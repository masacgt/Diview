"""Fetch IRBANK annual dividend records and selected company forecasts for v2."""

from __future__ import annotations

import argparse
from concurrent.futures import ThreadPoolExecutor, as_completed
from datetime import date, datetime, timedelta, timezone
import calendar
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

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "yfinance_r2"))
from financial_revision import bump_financial_revision


API_BASE = "https://api.irbank.net/v1"
DEFAULT_CATALOG_URL = "https://diview-irbank.omasa.workers.dev/api/v1/irbank/catalog"
DEFAULT_R2_KEY = "market/yfinance/v2/issuer_dividends/current.json"
DIVIDEND_SHARD_MANIFEST_KEY = "market/yfinance/v2/issuer_dividends/shards/current.json"
DIVIDEND_SHARD_ROOT = "market/yfinance/v2/issuer_dividends/shards"
DIVIDEND_SHARD_PREFIX_LENGTH = 2
DEFAULT_OUTPUT = Path(tempfile.gettempdir()) / "diview-irbank-v2-dividends-current.json"
EDINET_CODE_RE = re.compile(r"^[EG]\d{5}$")
SECURITY_CODE_RE = re.compile(r"^[A-Z0-9]{4,5}$")
FORECAST_TITLE_RE = re.compile(r"配当|分配|決算|業績|運用状況|dividend|distribution|earnings|results|forecast", re.IGNORECASE)
FORECAST_CORRECTION_TITLE_RE = re.compile(r"訂正|修正|変更|correction|revision|amend", re.IGNORECASE)
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
        # Both forms are emitted by IRBANK: ordinary earnings reports omit
        # CurrentMember, while forecast revisions may include it explicitly.
        # Match complete tokens so previous, next-year or unrelated dimensions
        # cannot be mistaken for the current annual forecast.
        tokens = context.split("_")
        allowed = {"CurrentYearDuration", "AnnualMember", "SecondQuarterMember", "YearEndMember",
                   "NonConsolidatedMember", "CurrentMember", "ForecastMember"}
        if (not tokens or tokens[0] != "CurrentYearDuration" or set(tokens) - allowed
                or "NonConsolidatedMember" not in tokens or "ForecastMember" not in tokens
                or sum(token in {"AnnualMember", "SecondQuarterMember", "YearEndMember"} for token in tokens) != 1
                or len(tokens) != len(set(tokens)) or row.get("unit") not in {None, "JPYPerShares"}):
            continue
        value = decimal_value(row.get("value"))
        if value is not None and value >= 0:
            result.append({"context": context, "value": value})
    return result


def _has_forecast_distribution_fact(document: dict[str, Any]) -> bool:
    facts = document.get("xbrl_facts")
    if not isinstance(facts, list):
        return False
    for row in facts:
        if not isinstance(row, dict):
            continue
        element = str(row.get("element_name") or "")
        context = str(row.get("context") or "")
        if (element == "DividendPerShare" or element.startswith("DistributionsPerUnit")) and (
            "ForecastMember" in context and "PreviousMember" not in context
        ):
            return True
    return False


def _has_distribution_element(document: dict[str, Any]) -> bool:
    facts = document.get("xbrl_facts")
    return isinstance(facts, list) and any(
        isinstance(row, dict)
        and (row.get("element_name") == "DividendPerShare"
             or str(row.get("element_name") or "").startswith("DistributionsPerUnit"))
        for row in facts
    )


def _reit_forecast(facts: list[dict[str, Any]], meta: dict[str, Any], as_of: date) -> dict[str, Any] | None:
    """Combine two explicitly reported six-month REIT distributions on a 12-month basis."""
    def fact_date(name: str) -> date | None:
        for row in facts:
            if row.get("element_name") == name:
                try:
                    return date.fromisoformat(str(row.get("value"))[:10])
                except ValueError:
                    return None
        return None

    start = fact_date("CurrentFiscalYearStartDateDEI")
    end = fact_date("CurrentFiscalYearEndDateDEI")
    if start is None or end is None or (end.year - start.year) * 12 + end.month - start.month != 5:
        return None
    if start.day != 1 or end.day != calendar.monthrange(end.year, end.month)[1]:
        return None

    def advance_six_months(value: date) -> date:
        month_index = value.year * 12 + value.month - 1 + 6
        year, month_zero = divmod(month_index, 12)
        return date(year, month_zero + 1, calendar.monthrange(year, month_zero + 1)[1])

    first_end = advance_six_months(end)
    second_end = advance_six_months(first_end)
    # Never keep including a completed distribution in a forward 12-month estimate.
    if first_end <= as_of:
        return None
    components = []
    for context_prefix, period_end in (("NextYearDuration", first_end), ("Next2YearDuration", second_end)):
        matches = []
        for row in facts:
            context = str(row.get("context") or "")
            if (row.get("element_name") != "DistributionsPerUnitIncludingDistributionsInExcessOfProfitREIT"
                    or not context.startswith(context_prefix + "_") or "ForecastMember" not in context
                    or "PreviousMember" in context or row.get("unit") != "JPYPerUnits"):
                continue
            value = decimal_value(row.get("value"))
            if value is not None and value >= 0:
                matches.append(value)
        if not matches or len(set(matches)) != 1:
            return None
        components.append({"period_end": period_end.isoformat(), "reported_dps": json_number(matches[0]),
                           "current_share_basis_dps": json_number(matches[0])})
    doc_id = str(meta.get("doc_id") or "")
    return {
        "status": "available", "fiscal_year": second_end.year, "period_end": second_end.isoformat(),
        "first_period_end": first_end.isoformat(),
        "annual_dps_current_share_basis": json_number(sum(Decimal(str(c["reported_dps"])) for c in components)),
        "amount_basis": "reit_two_period_forecast_including_excess_distributions",
        "disclosed_at": meta.get("disclosed_at"),
        "source_refs": ([{"doc_id": doc_id, "source": meta.get("source") or "tdnet"}] if doc_id else []),
        "components": components,
        "normalization": {"method": "sum_two_reported_six_month_distributions"},
    }


def parse_forecast(document: dict[str, Any], latest_actual_row: dict[str, Any] | None,
                   *, as_of: date | None = None) -> dict[str, Any] | None:
    """Normalize explicit current-year DividendPerShare XBRL facts to one annual DPS."""
    facts = document.get("xbrl_facts")
    if not isinstance(facts, list):
        return None
    facts = [row for row in facts if isinstance(row, dict)]
    reit_forecast = _reit_forecast(facts, document.get("meta") or {},
                                   as_of or datetime.now(timezone(timedelta(hours=9))).date())
    if reit_forecast is not None:
        return reit_forecast
    dps_facts = _forecast_dps_facts({"xbrl_facts": facts})
    if not dps_facts:
        return None

    # Do not select an arbitrary value when equivalent reported contexts differ.
    for member in ("AnnualMember", "SecondQuarterMember", "YearEndMember"):
        if len({row["value"] for row in dps_facts if member in row["context"].split("_")}) > 1:
            return None

    fiscal_end_raw = next((str(row.get("value")) for row in facts
                           if row.get("element_name") == "FiscalYearEnd" and row.get("value")), "")
    period_end = fiscal_end_raw[:10] if re.fullmatch(r"\d{4}-\d{2}-\d{2}.*", fiscal_end_raw) else None
    try:
        end_date = date.fromisoformat(period_end) if period_end else None
    except ValueError:
        return None
    if end_date is None or (as_of is not None and end_date <= as_of):
        return None
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
                          budget: RequestBudget | None = None,
                          audit: dict[str, Any] | None = None,
                          cached_forecast: dict[str, Any] | None = None) -> tuple[dict[str, Any] | None, dict[str, Any] | None]:
    as_of = as_of or datetime.now(timezone(timedelta(hours=9))).date()
    cached_doc_id = None
    cached_period_end = None
    cached_value = decimal_value(
        cached_forecast.get("annual_dps_current_share_basis")
        if isinstance(cached_forecast, dict) else None
    )
    if isinstance(cached_forecast, dict) and cached_forecast.get("status") == "available":
        period_raw = str(cached_forecast.get("period_end") or "")
        try:
            cached_period_end = date.fromisoformat(period_raw[:10])
        except ValueError:
            cached_period_end = None
        refs = cached_forecast.get("source_refs")
        if isinstance(refs, list):
            cached_doc_id = next((str(ref.get("doc_id") or "") for ref in refs
                                  if isinstance(ref, dict) and ref.get("doc_id")), None)
        normalization = cached_forecast.get("normalization")
        normalization_method = (normalization.get("method")
                                if isinstance(normalization, dict) else None)
        if (not cached_doc_id or cached_value is None or cached_value < 0
                or cached_period_end is None or cached_period_end <= as_of
                or normalization_method not in (None, "reported_current_forecast")):
            cached_doc_id = None
    from_date = (as_of - timedelta(days=1095)).isoformat()
    cursor: str | None = None
    seen_cursors: set[str] = set()
    pages_read = 0
    candidate_count = 0
    inspected_doc_ids: list[str] = []
    xbrl_docs = 0
    distribution_element_docs = 0
    forecast_fact_docs = 0
    missing_xbrl_docs = 0
    listing_attribution: dict[str, Any] | None = None

    def finish(forecast: dict[str, Any] | None, attribution: dict[str, Any] | None,
               resolution: str, reason: str | None = None,
               *, cached_document_reused: bool = False) -> tuple[dict[str, Any] | None, dict[str, Any] | None]:
        if audit is not None:
            evidence: dict[str, Any] = {
                "disclosure_pages_read": pages_read,
                "candidate_disclosure_count": candidate_count,
                "documents_inspected": len(inspected_doc_ids),
                "inspected_doc_ids": inspected_doc_ids,
                "xbrl_documents": xbrl_docs,
                "missing_xbrl_documents": missing_xbrl_docs,
                "documents_with_distribution_elements": distribution_element_docs,
                "documents_with_forecast_facts": forecast_fact_docs,
            }
            if reason:
                evidence["reason"] = reason
            if cached_document_reused:
                evidence["cached_document_reused"] = True
            if forecast:
                refs = forecast.get("source_refs")
                if isinstance(refs, list) and refs:
                    evidence["selected_doc_id"] = refs[0].get("doc_id")
            audit.update({"resolution": resolution, "evidence": evidence})
        return forecast, attribution or listing_attribution

    while pages_read < MAX_DISCLOSURE_LIST_PAGES:
        params = {"from": from_date, "limit": 100}
        if cursor:
            params["cursor"] = cursor
        try:
            listing = request_json(
                f"{API_BASE}/securities/{code}/disclosures?{urlencode(params)}", token, budget=budget
            )
        except ApiFailure as error:
            if error.status == 404 and pages_read == 0:
                return finish(None, None, "not_supported_by_disclosure_api", "disclosure_list_404")
            raise
        pages_read += 1
        disclosures = listing.get("disclosures")
        if not isinstance(disclosures, list):
            raise ValueError("IRBANK disclosure list does not contain disclosures[]")
        listing_attribution = listing.get("attribution") or listing_attribution
        candidates = [row for row in disclosures if isinstance(row, dict)
                      and FORECAST_TITLE_RE.search(str(row.get("title") or ""))]
        candidates.sort(key=lambda row: str(row.get("disclosed_at") or ""), reverse=True)
        candidate_count += len(candidates)

        for row in candidates:
            doc_id = str(row.get("doc_id") or "")
            if not doc_id:
                continue
            if cached_doc_id and doc_id == cached_doc_id:
                # Newer candidates in the ordered listing have already been
                # inspected. Reuse the old XBRL result only if its period is
                # still current and it did not depend on a split adjustment.
                value = decimal_value(cached_forecast.get("annual_dps_current_share_basis"))
                resolution = "explicit_zero_forecast" if value == 0 else "forecast_available"
                return finish(dict(cached_forecast), listing_attribution, resolution,
                              cached_document_reused=True)
            if len(inspected_doc_ids) >= MAX_FORECAST_DOCUMENTS:
                return finish(None, None, "review_required", "candidate_document_limit")
            inspected_doc_ids.append(doc_id)
            try:
                document = request_json(
                    f"{API_BASE}/disclosures/{doc_id}?include=meta,xbrl_facts", token, budget=budget
                )
            except ApiFailure as error:
                if error.status == 404:
                    if cached_doc_id:
                        return finish(None, None, "review_required",
                                      "newer_candidate_document_missing_before_cached_forecast")
                    continue
                raise

            facts = document.get("xbrl_facts")
            if isinstance(facts, list):
                xbrl_docs += 1
            else:
                missing_xbrl_docs += 1
                if cached_doc_id:
                    return finish(None, document.get("attribution"), "review_required",
                                  "newer_candidate_xbrl_missing_before_cached_forecast")
            has_distribution_element = _has_distribution_element(document)
            has_forecast_fact = _has_forecast_distribution_fact(document)
            distribution_element_docs += int(has_distribution_element)
            forecast_fact_docs += int(has_forecast_fact)

            forecast = parse_forecast(document, actual, as_of=as_of)
            if forecast:
                value = decimal_value(forecast.get("annual_dps_current_share_basis"))
                resolution = "explicit_zero_forecast" if value == 0 else "forecast_available"
                return finish(forecast, document.get("attribution"), resolution)

            title = str(row.get("title") or "")
            is_dividend_correction = (
                FORECAST_CORRECTION_TITLE_RE.search(title) is not None
                and re.search(r"配当|分配|dividend|distribution", title, re.IGNORECASE) is not None
            )
            is_dividend_document = re.search(r"配当|分配|dividend|distribution", title, re.IGNORECASE) is not None
            if has_forecast_fact or is_dividend_correction or (has_distribution_element and is_dividend_document):
                return finish(None, document.get("attribution"), "review_required",
                              "unparseable_forecast_or_correction_document")

        next_cursor = listing.get("next_cursor")
        if not next_cursor:
            if candidate_count == 0:
                return finish(None, None, "no_matching_disclosure")
            if missing_xbrl_docs and not xbrl_docs:
                return finish(None, None, "xbrl_missing")
            if distribution_element_docs:
                return finish(None, None, "review_required", "distribution_element_without_current_forecast")
            if xbrl_docs:
                return finish(None, None, "xbrl_item_missing")
            return finish(None, None, "review_required", "candidate_documents_unavailable")
        next_cursor = str(next_cursor)
        if next_cursor in seen_cursors:
            return finish(None, None, "review_required", "repeated_disclosure_cursor")
        seen_cursors.add(next_cursor)
        cursor = next_cursor

    return finish(None, None, "review_required", "disclosure_page_limit")


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


def build_dividend_index_shards(document: dict[str, Any], slot: str) -> tuple[dict[str, bytes], dict[str, Any]]:
    if slot not in {"a", "b"}:
        raise ValueError(f"Invalid issuer-dividend shard slot: {slot}")
    items = document.get("items")
    if not isinstance(items, dict) or not items:
        raise ValueError("Issuer dividend index does not contain a non-empty items{}")

    grouped: dict[str, dict[str, Any]] = {}
    for code, item in items.items():
        normalized = str(code).strip().upper()
        if not SECURITY_CODE_RE.fullmatch(normalized) or not isinstance(item, dict):
            raise ValueError(f"Invalid issuer-dividend index item: {code}")
        prefix = normalized[:DIVIDEND_SHARD_PREFIX_LENGTH]
        if normalized in grouped.get(prefix, {}):
            raise ValueError(f"Duplicate normalized issuer-dividend code: {normalized}")
        grouped.setdefault(prefix, {})[normalized] = item

    shard_payloads = {
        f"{DIVIDEND_SHARD_ROOT}/{slot}/{prefix}.json": json_bytes({
            "schema_version": 1,
            "prefix": prefix,
            "items": {code: prefix_items[code] for code in sorted(prefix_items)},
        })
        for prefix, prefix_items in sorted(grouped.items())
    }
    manifest = {
        "schema_version": 1,
        "slot": slot,
        "prefix_length": DIVIDEND_SHARD_PREFIX_LENGTH,
        "prefixes": sorted(grouped),
        "item_count": sum(len(prefix_items) for prefix_items in grouped.values()),
        "generated_at": document.get("generated_at"),
        "source": document.get("source"),
        "attribution": document.get("attribution"),
    }
    return shard_payloads, manifest


def _put_verified_r2_object(client: Any, bucket: str, key: str, payload: bytes) -> str:
    digest = hashlib.sha256(payload).hexdigest()
    client.put_object(
        Bucket=bucket,
        Key=key,
        Body=payload,
        ContentType="application/json; charset=utf-8",
        CacheControl="public, max-age=300",
    )
    saved = client.get_object(Bucket=bucket, Key=key)["Body"].read()
    if hashlib.sha256(saved).hexdigest() != digest:
        raise RuntimeError(f"IRBANK v2 dividend shard read-back mismatch: {key}")
    return digest


def prepare_dividend_index_shards(client: Any, bucket: str, document: dict[str, Any]) -> dict[str, Any]:
    current = read_r2_document(client, bucket, DIVIDEND_SHARD_MANIFEST_KEY)
    active_slot = current.get("slot") if isinstance(current, dict) else None
    next_slot = "b" if active_slot == "a" else "a"
    payloads, manifest = build_dividend_index_shards(document, next_slot)
    digests = {
        key: _put_verified_r2_object(client, bucket, key, payload)
        for key, payload in payloads.items()
    }
    manifest["shard_count"] = len(payloads)
    manifest["shards_sha256"] = hashlib.sha256(
        "\n".join(f"{key}:{digests[key]}" for key in sorted(digests)).encode("utf-8")
    ).hexdigest()
    return manifest


def activate_dividend_index_shards(client: Any, bucket: str, manifest: dict[str, Any]) -> str:
    if manifest.get("schema_version") != 1 or manifest.get("slot") not in {"a", "b"}:
        raise ValueError("Invalid issuer-dividend shard manifest")
    payload = json_bytes(manifest)
    return _put_verified_r2_object(client, bucket, DIVIDEND_SHARD_MANIFEST_KEY, payload)


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
        index_write_attempted = False
        try:
            shard_manifest = (
                prepare_dividend_index_shards(r2_client, args.bucket, document)
                if args.key == DEFAULT_R2_KEY else None
            )
            # A timed-out PUT may have succeeded remotely even without a response.
            index_write_attempted = True
            response = r2_client.put_object(Bucket=args.bucket, Key=args.key, Body=payload,
                                            ContentType="application/json; charset=utf-8",
                                            CacheControl="public, max-age=300")
            saved = r2_client.get_object(Bucket=args.bucket, Key=args.key)["Body"].read()
            if hashlib.sha256(saved).hexdigest() != digest:
                raise RuntimeError(f"IRBANK v2 dividend read-back mismatch: {args.key}")
            if shard_manifest is not None:
                activate_dividend_index_shards(r2_client, args.bucket, shard_manifest)
            print(json.dumps({"published_key": args.key, "etag": str(response.get("ETag") or "").strip('"'),
                              "read_back_sha256": digest,
                              "shard_count": shard_manifest.get("shard_count") if shard_manifest else None,
                              "shard_manifest_sha256": (
                                  hashlib.sha256(json_bytes(shard_manifest)).hexdigest() if shard_manifest else None
                              )}, ensure_ascii=False))
        finally:
            if index_write_attempted and args.key == DEFAULT_R2_KEY:
                bump_financial_revision(r2_client, args.bucket)
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as error:
        print(f"fatal: {error}", file=sys.stderr)
        raise
