"""Fetch IRBANK annual dividend records and selected company forecasts for v2."""

from __future__ import annotations

import argparse
from concurrent.futures import ThreadPoolExecutor, as_completed
from datetime import date, datetime, timedelta, timezone
from decimal import Decimal, InvalidOperation, ROUND_HALF_UP
import hashlib
import json
import os
from pathlib import Path
import re
import sys
import tempfile
import threading
import time
from typing import Any
from urllib.error import HTTPError, URLError
from urllib.parse import urlencode
from urllib.request import Request, urlopen


API_BASE = "https://api.irbank.net/v1"
DEFAULT_CATALOG_URL = "https://diview-irbank.omasa.workers.dev/api/v1/irbank/catalog"
DEFAULT_R2_KEY = "market/yfinance/v2/issuer_dividends/current.json"
DEFAULT_OUTPUT = Path(tempfile.gettempdir()) / "diview-irbank-v2-dividends-current.json"
EDINET_CODE_RE = re.compile(r"^[EG]\d{5}$")
SECURITY_CODE_RE = re.compile(r"^[A-Z0-9]{4}$")
FORECAST_TITLE_RE = re.compile(r"配当|決算|業績|dividend|earnings|results|forecast", re.IGNORECASE)
# IRBANK currently allows 60 requests/minute per account. Keep a small margin
# for requests made outside this process while reducing idle time between calls.
API_REQUEST_INTERVAL_SECONDS = 1.02
_api_rate_lock = threading.Lock()
_api_next_request_at = 0.0


class ApiFailure(RuntimeError):
    def __init__(self, status: int, url: str):
        super().__init__(f"IRBANK API returned HTTP {status}: {url.split('?')[0]}")
        self.status = status


class RequestBudgetExceeded(RuntimeError):
    """Raised before an IRBANK request would exceed the configured daily cap."""


class RequestBudget:
    def __init__(self, limit: int, *, used: int = 0):
        self.limit = max(0, limit)
        self.used = max(0, used)
        self._lock = threading.Lock()

    @property
    def remaining(self) -> int:
        with self._lock:
            return max(0, self.limit - self.used)

    def consume(self, url: str) -> None:
        with self._lock:
            if self.used >= self.limit:
                raise RequestBudgetExceeded(
                    f"IRBANK daily request limit reached before {url.split('?')[0]}"
                )
            self.used += 1


def iso_now() -> str:
    return datetime.now(timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z")


def wait_for_api_slot(token: str | None) -> None:
    global _api_next_request_at
    if not token:
        return
    with _api_rate_lock:
        now = time.monotonic()
        wait_seconds = max(0.0, _api_next_request_at - now)
        _api_next_request_at = max(now, _api_next_request_at) + API_REQUEST_INTERVAL_SECONDS
    if wait_seconds:
        time.sleep(wait_seconds)


def request_json(url: str, token: str | None = None, *, attempts: int = 5,
                 budget: RequestBudget | None = None) -> dict[str, Any]:
    headers = {"Accept": "application/json", "User-Agent": "Diview/IRBANK-v2-dividends"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    for attempt in range(attempts):
        if token and budget:
            budget.consume(url)
        if not url.endswith("/usage"):
            wait_for_api_slot(token)
        request = Request(url, headers=headers)
        try:
            with urlopen(request, timeout=30) as response:
                parsed = json.loads(response.read())
            if not isinstance(parsed, dict):
                raise ValueError(f"Expected a JSON object from {url.split('?')[0]}")
            return parsed
        except HTTPError as error:
            if error.code == 404:
                raise ApiFailure(404, url) from error
            if error.code not in {408, 425, 429, 500, 502, 503, 504} or attempt + 1 >= attempts:
                raise ApiFailure(error.code, url) from error
        except (TimeoutError, URLError, OSError) as error:
            if attempt + 1 >= attempts:
                raise RuntimeError(f"IRBANK API transport failed: {url.split('?')[0]}") from error
        time.sleep(min(2 ** attempt, 16))
    raise RuntimeError(f"IRBANK API failed after retries: {url.split('?')[0]}")


def decimal_value(value: Any) -> Decimal | None:
    if value is None or isinstance(value, bool):
        return None
    try:
        result = Decimal(str(value))
    except (InvalidOperation, ValueError):
        return None
    return result if result.is_finite() else None


def json_number(value: Decimal) -> int | float:
    return int(value) if value == value.to_integral_value() else float(value)


def round_yen(value: Decimal) -> Decimal:
    return value.quantize(Decimal("0.01"), rounding=ROUND_HALF_UP)


def latest_actual(records: Any) -> dict[str, Any] | None:
    rows = [row for row in records if isinstance(row, dict)] if isinstance(records, list) else []
    rows.sort(key=lambda row: int(row.get("fiscal_year") or 0))
    return dict(rows[-1]) if rows else None


def _fact_text(facts: list[dict[str, Any]], *element_names: str) -> str:
    accepted = set(element_names)
    return " ".join(str(row.get("value") or "") for row in facts if row.get("element_name") in accepted)


def _forecast_dps_facts(document: dict[str, Any]) -> list[dict[str, Any]]:
    facts = document.get("xbrl_facts")
    if not isinstance(facts, list):
        return []
    result = []
    for row in facts:
        if not isinstance(row, dict) or row.get("element_name") != "DividendPerShare":
            continue
        context = str(row.get("context") or "")
        if "CurrentYearDuration" not in context or "CurrentMember" not in context or "ForecastMember" not in context:
            continue
        if "PreviousMember" in context or row.get("unit") not in {None, "JPYPerShares"}:
            continue
        value = decimal_value(row.get("value"))
        if value is not None and value >= 0:
            result.append({"context": context, "value": value})
    return result


def parse_forecast(document: dict[str, Any], latest_actual_row: dict[str, Any] | None) -> dict[str, Any] | None:
    """Normalize explicit current-year DividendPerShare XBRL facts to one annual DPS."""
    facts = document.get("xbrl_facts")
    if not isinstance(facts, list):
        return None
    facts = [row for row in facts if isinstance(row, dict)]
    dps_facts = _forecast_dps_facts({"xbrl_facts": facts})
    if not dps_facts:
        return None

    fiscal_end_raw = next((str(row.get("value")) for row in facts
                           if row.get("element_name") == "FiscalYearEnd" and row.get("value")), "")
    period_end = fiscal_end_raw[:10] if re.fullmatch(r"\d{4}-\d{2}-\d{2}.*", fiscal_end_raw) else None
    fiscal_year = int(period_end[:4]) if period_end else None

    annual = next((row for row in dps_facts if "AnnualMember" in row["context"]), None)
    components: list[dict[str, Any]] = []
    normalization: dict[str, Any] = {"method": "reported_current_forecast"}
    if annual:
        annual_dps = annual["value"]
        components.append({"period": "annual", "reported_dps": json_number(annual["value"]), "current_share_basis_dps": json_number(annual["value"])})
    else:
        second_quarter = next((row for row in dps_facts if "SecondQuarterMember" in row["context"]), None)
        year_end = next((row for row in dps_facts if "YearEndMember" in row["context"]), None)
        if second_quarter is None or year_end is None:
            return None
        first_half = second_quarter["value"]
        split_notice = "株式分割" in str(document.get("meta", {}).get("title") or "")
        split_notice = split_notice or "株式分割" in _fact_text(facts, "NoticeOfForecastCorrection", "ReasonForDividendForecastCorrection")
        actual_reported = decimal_value((latest_actual_row or {}).get("annual_dps"))
        actual_adjusted = decimal_value((latest_actual_row or {}).get("annual_dps_adjusted"))
        adjustment_factor = actual_reported / actual_adjusted if actual_reported and actual_adjusted and actual_adjusted > 0 else None
        adjusted_first_half = first_half
        if split_notice and adjustment_factor is not None and adjustment_factor > Decimal("1.001"):
            candidate = first_half / adjustment_factor
            # Require the post-split half-year forecasts to agree within one sen.
            # Otherwise the document does not provide enough basis information.
            if abs(round_yen(candidate) - round_yen(year_end["value"])) > Decimal("0.01"):
                return None
            adjusted_first_half = candidate
            normalization = {
                "method": "interim_dps_divided_by_irbank_adjustment_factor",
                "factor": json_number(adjustment_factor),
                "reason": "split announcement and matching post-adjustment half-year DPS",
            }
        annual_dps = adjusted_first_half + year_end["value"]
        components.extend([
            {"period": "second_quarter", "reported_dps": json_number(first_half),
             "current_share_basis_dps": json_number(round_yen(adjusted_first_half))},
            {"period": "year_end", "reported_dps": json_number(year_end["value"]),
             "current_share_basis_dps": json_number(year_end["value"])},
        ])

    if annual_dps < 0:
        return None
    annual_dps = round_yen(annual_dps)
    meta = document.get("meta") if isinstance(document.get("meta"), dict) else {}
    doc_id = str(meta.get("doc_id") or "")
    source = str(meta.get("source") or "tdnet")
    return {
        "status": "available",
        "fiscal_year": fiscal_year,
        "period_end": period_end,
        "annual_dps_current_share_basis": json_number(annual_dps),
        "amount_basis": "irbank_forecast_current_share_basis",
        "disclosed_at": meta.get("disclosed_at"),
        "source_refs": ([{"doc_id": doc_id, "source": source}] if doc_id else []),
        "components": components,
        "normalization": normalization,
    }


def fetch_latest_forecast(code: str, actual: dict[str, Any] | None, token: str,
                          *, as_of: date | None = None,
                          budget: RequestBudget | None = None) -> tuple[dict[str, Any] | None, dict[str, Any] | None]:
    as_of = as_of or date.today()
    from_date = (as_of - timedelta(days=1095)).isoformat()
    url = f"{API_BASE}/securities/{code}/disclosures?{urlencode({'from': from_date, 'limit': 100})}"
    listing = request_json(url, token, budget=budget)
    disclosures = listing.get("disclosures")
    if not isinstance(disclosures, list):
        raise ValueError("IRBANK disclosure list does not contain disclosures[]")
    candidates = [row for row in disclosures if isinstance(row, dict)
                  and FORECAST_TITLE_RE.search(str(row.get("title") or ""))]
    candidates.sort(key=lambda row: str(row.get("disclosed_at") or ""), reverse=True)
    for row in candidates[:8]:
        doc_id = str(row.get("doc_id") or "")
        if not doc_id:
            continue
        try:
            document = request_json(
                f"{API_BASE}/disclosures/{doc_id}?include=meta,xbrl_facts", token, budget=budget
            )
        except ApiFailure as error:
            if error.status == 404:
                continue
            raise
        forecast = parse_forecast(document, actual)
        if forecast:
            return forecast, document.get("attribution")
    return None, listing.get("attribution")


def load_catalog(url: str) -> list[dict[str, Any]]:
    catalog = request_json(url)
    rows = catalog.get("securities")
    if not isinstance(rows, list):
        raise ValueError("IRBANK catalog response does not contain securities[]")
    return [row for row in rows if isinstance(row, dict)
            and row.get("has_overview") is not False
            and EDINET_CODE_RE.fullmatch(str(row.get("edinet_code") or ""))
            and SECURITY_CODE_RE.fullmatch(str(row.get("code") or "").upper())]


def parse_codes(raw: str | None, codes_file: Path | None, catalog: list[dict[str, Any]]) -> list[dict[str, Any]]:
    selected = {code.upper() for code in (raw or "").split(",") if code.strip()}
    if codes_file:
        selected.update(line.strip().upper() for line in codes_file.read_text(encoding="utf-8").splitlines()
                        if line.strip() and not line.lstrip().startswith("#"))
    if not selected:
        return catalog
    unknown = selected - {str(row["code"]).upper() for row in catalog}
    if unknown:
        raise ValueError(f"Codes missing from IRBANK catalog: {', '.join(sorted(unknown))}")
    return [row for row in catalog if str(row["code"]).upper() in selected]


def fetch_dividend_record(security: dict[str, Any], token: str, from_fiscal_year: int,
                          *, budget: RequestBudget | None = None) -> tuple[str, dict[str, Any]]:
    code = str(security["code"]).upper()
    query = urlencode({"edinet_code": security["edinet_code"], "from_fiscal_year": from_fiscal_year})
    document = request_json(f"{API_BASE}/dividends?{query}", token, budget=budget)
    actual = latest_actual(document.get("records"))
    result = {
        "code": code,
        "name": str(security.get("name") or code),
        "edinet_code": security["edinet_code"],
        "actual_status": "available" if actual else "empty",
        "actual": actual,
        "forecast_status": "not_requested",
        "forecast": None,
        "attribution": document.get("attribution"),
    }
    return code, result


def required_env(name: str) -> str:
    value = os.environ.get(name, "").strip()
    if not value:
        raise RuntimeError(f"Required environment variable is missing: {name}")
    return value


def make_r2_client(workers: int):
    import boto3
    from botocore.config import Config

    account_id = required_env("CLOUDFLARE_ACCOUNT_ID")
    return boto3.client(
        "s3",
        endpoint_url=os.environ.get("R2_ENDPOINT_URL", f"https://{account_id}.r2.cloudflarestorage.com"),
        aws_access_key_id=required_env("R2_ACCESS_KEY_ID"),
        aws_secret_access_key=required_env("R2_SECRET_ACCESS_KEY"),
        region_name="auto",
        config=Config(max_pool_connections=max(10, workers * 2), retries={"max_attempts": 4, "mode": "standard"}),
    )


def read_r2_document(client: Any, bucket: str, key: str) -> dict[str, Any] | None:
    try:
        raw = client.get_object(Bucket=bucket, Key=key)["Body"].read()
    except Exception as error:
        code = str(getattr(error, "response", {}).get("Error", {}).get("Code", ""))
        if code in {"NoSuchKey", "404", "NotFound"}:
            return None
        raise
    document = json.loads(raw)
    return document if isinstance(document, dict) else None


def json_bytes(document: dict[str, Any]) -> bytes:
    return json.dumps(document, ensure_ascii=False, separators=(",", ":"), allow_nan=False).encode("utf-8")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--catalog-url", default=DEFAULT_CATALOG_URL)
    parser.add_argument("--codes", help="Comma-separated listed codes; omit to refresh the full IRBANK catalog")
    parser.add_argument("--codes-file", type=Path)
    parser.add_argument("--forecast-codes", default="", help="Codes whose recent TDnet/EDINET disclosures should also be parsed")
    parser.add_argument("--from-fiscal-year", type=int, default=2024)
    parser.add_argument("--workers", type=int, default=4)
    parser.add_argument("--reserve-calls", type=int, default=8)
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    parser.add_argument("--publish", action="store_true")
    parser.add_argument("--bucket", default="diview-market-data")
    parser.add_argument("--key", default=DEFAULT_R2_KEY)
    args = parser.parse_args()

    token = required_env("IRBANK_API_KEY")
    catalog = load_catalog(args.catalog_url)
    securities = parse_codes(args.codes, args.codes_file, catalog)
    if not securities:
        raise ValueError("No IRBANK securities selected")
    forecast_codes = {code.strip().upper() for code in args.forecast_codes.split(",") if code.strip()}
    known_codes = {str(row["code"]).upper() for row in securities}
    if forecast_codes - known_codes:
        raise ValueError("Every forecast code must also be selected for dividend refresh")

    usage = request_json(f"{API_BASE}/usage", token)
    remaining = int(usage.get("remaining") or 0)
    estimated_calls = len(securities) + args.reserve_calls + 9 * len(forecast_codes)
    if remaining < estimated_calls:
        raise RuntimeError(f"IRBANK quota preflight: need about {estimated_calls} requests, {remaining} remain")

    items: dict[str, dict[str, Any]] = {}
    failures: list[dict[str, Any]] = []
    attribution: dict[str, Any] | None = None
    with ThreadPoolExecutor(max_workers=max(1, min(args.workers, 12))) as executor:
        futures = {executor.submit(fetch_dividend_record, row, token, args.from_fiscal_year): row for row in securities}
        for completed, future in enumerate(as_completed(futures), start=1):
            row = futures[future]
            code = str(row.get("code") or "")
            try:
                result_code, result = future.result()
                items[result_code] = result
                attribution = attribution or result.get("attribution")
            except ApiFailure as error:
                if error.status == 404:
                    items[code] = {
                        "code": code, "name": row.get("name"), "edinet_code": row.get("edinet_code"),
                        "actual_status": "empty", "actual": None, "forecast_status": "not_requested",
                        "forecast": None,
                    }
                else:
                    failures.append({"code": code, "status": error.status})
            except Exception as error:
                failures.append({"code": code, "error": type(error).__name__})
            if completed % 100 == 0 or completed == len(securities):
                print(f"dividend actuals {completed}/{len(securities)}; failures={len(failures)}", flush=True)

    if failures:
        raise RuntimeError(f"Dividend fetch incomplete for {len(failures)} securities; refusing to publish a partial index")

    for code in sorted(forecast_codes):
        security = next(row for row in securities if str(row["code"]).upper() == code)
        latest = items[code].get("actual")
        try:
            forecast, forecast_attribution = fetch_latest_forecast(code, latest, token)
        except ApiFailure as error:
            if error.status == 404:
                forecast, forecast_attribution = None, None
            else:
                raise
        items[code]["forecast"] = forecast
        items[code]["forecast_status"] = "available" if forecast else "empty"
        items[code]["name"] = str(security.get("name") or code)
        attribution = attribution or forecast_attribution

    r2_client = make_r2_client(args.workers) if args.publish else None
    if r2_client:
        existing = read_r2_document(r2_client, args.bucket, args.key)
        if existing is None and len(securities) != len(catalog):
            raise RuntimeError("Refusing to publish a partial index before a full index exists in R2")
        if existing and isinstance(existing.get("items"), dict):
            old_items = existing["items"]
            for code, item in items.items():
                previous = old_items.get(code)
                if (item.get("forecast_status") == "not_requested" and isinstance(previous, dict)
                    and previous.get("forecast") is not None):
                    item["forecast"] = previous["forecast"]
                    item["forecast_status"] = previous.get("forecast_status", "available")
            items = {**old_items, **items}

    document = {
        "schema_version": 1,
        "generated_at": iso_now(),
        "source": "IRBANK Connect REST API",
        "attribution": attribution,
        "count": len(items),
        "items": {code: items[code] for code in sorted(items)},
    }
    payload = json_bytes(document)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_bytes(payload)
    digest = hashlib.sha256(payload).hexdigest()
    print(json.dumps({"output": str(args.output), "count": len(items), "forecast_count": sum(bool(row.get("forecast")) for row in items.values()),
                      "sha256": digest, "bytes": len(payload), "api_remaining_before_fetch": remaining}, ensure_ascii=False))

    if args.publish:
        response = r2_client.put_object(Bucket=args.bucket, Key=args.key, Body=payload,
                                        ContentType="application/json; charset=utf-8",
                                        CacheControl="public, max-age=300")
        saved = r2_client.get_object(Bucket=args.bucket, Key=args.key)["Body"].read()
        if hashlib.sha256(saved).hexdigest() != digest:
            raise RuntimeError(f"IRBANK v2 dividend read-back mismatch: {args.key}")
        print(json.dumps({"published_key": args.key, "etag": str(response.get("ETag") or "").strip('"'),
                          "read_back_sha256": digest}, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as error:
        print(f"fatal: {error}", file=sys.stderr)
        raise
