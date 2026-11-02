"""Compare six IRBANK forecast-document retrieval strategies on matched issuers.

Runs three rounds across six modes (18 trials total). Results are written to a
local JSON report; this script never publishes trial data to R2. The doc_cache
arm uses the production guarded cache path, which inspects newer candidates
before reusing a cached forecast. Earlier pilot numbers used an unguarded cache
shortcut and must not be treated as the expected production call count.
"""
from __future__ import annotations

from datetime import datetime, timedelta, timezone
import json
from pathlib import Path
import sys
from urllib.parse import urlencode

import refresh_v2_dividends as api

MODES = ("latest_one", "progressive", "recent_window", "doc_cache", "title_priority", "latest_then_corrections")
SAMPLES = ("7203", "4502", "8984")
OUT = Path.home() / ".cache" / "irbank-forecast-strategy-measurement.json"


def title_rank(row):
    title = str(row.get("title") or "")
    correction = any(k in title for k in ("訂正", "修正", "変更"))
    forecast = any(k in title for k in ("配当予想", "分配金予想", "業績予想"))
    results = any(k in title for k in ("決算短信", "決算", "運用状況"))
    return (not correction, not forecast, not results, str(row.get("disclosed_at") or ""))


def run_trial(mode, code, token, cached_forecasts):
    calls = {"listing": 0, "detail": 0}
    original = api.request_json
    def counted(url, *args, **kwargs):
        api.wait_for_api_slot(token)
        if "/disclosures/" in url:
            calls["detail"] += 1
        elif "/securities/" in url and "/disclosures" in url:
            calls["listing"] += 1
        return original(url, *args, **kwargs)
    api.request_json = counted
    try:
        today = datetime.now(timezone(timedelta(hours=9))).date()
        if mode == "doc_cache":
            audit = {}
            forecast, _ = api.fetch_latest_forecast(
                code, None, token, as_of=today, audit=audit,
                cached_forecast=cached_forecasts.get(code),
            )
            evidence = audit.get("evidence") or {}
            return {
                "mode": mode, "code": code,
                "listing_calls": calls["listing"], "detail_calls": calls["detail"],
                "total_calls": calls["listing"] + calls["detail"],
                "candidate_count": int(evidence.get("candidate_disclosure_count") or 0),
                "documents_examined": int(evidence.get("documents_inspected") or 0),
                "forecast_found": forecast is not None,
                "cached_document_reused": bool(evidence.get("cached_document_reused")),
                "resolution": audit.get("resolution"),
            }
        days = 180 if mode == "recent_window" else 1095
        query = {"from": (today - timedelta(days=days)).isoformat(), "limit": 100}
        listing = original(f"{api.API_BASE}/securities/{code}/disclosures?{urlencode(query)}", token)
        rows = [r for r in listing.get("disclosures", []) if isinstance(r, dict) and api.FORECAST_TITLE_RE.search(str(r.get("title") or ""))]
        if mode == "title_priority":
            rows.sort(key=title_rank)
        else:
            rows.sort(key=lambda r: str(r.get("disclosed_at") or ""), reverse=True)
        limit = 1 if mode == "latest_one" else 8
        if mode == "latest_then_corrections":
            rows = rows[:1] + [r for r in rows[1:] if any(k in str(r.get("title") or "") for k in ("訂正", "修正", "変更"))]
        found = False
        examined = 0
        for row in rows[:limit]:
            doc_id = str(row.get("doc_id") or "")
            if not doc_id:
                continue
            # A cached source document is a hit only if the listing still
            # contains it; otherwise the new filing must be inspected.
            try:
                doc = counted(f"{api.API_BASE}/disclosures/{doc_id}?include=meta,xbrl_facts", token)
            except api.ApiFailure as e:
                if e.status == 404:
                    continue
                raise
            examined += 1
            if api.parse_forecast(doc, None, as_of=today):
                found = True
                break
        return {"mode": mode, "code": code, "listing_calls": calls["listing"] + 1, "detail_calls": calls["detail"],
                "total_calls": calls["listing"] + calls["detail"] + 1, "candidate_count": len(rows),
                "documents_examined": examined, "forecast_found": found}
    finally:
        api.request_json = original


def main():
    token = api.required_env("IRBANK_API_KEY")
    # matched sample reused for every mode; known source doc IDs form the cache arm
    import boto3
    client = api.make_r2_client(1)
    index = api.read_r2_document(client, "diview-market-data", "market/yfinance/v2/issuer_dividends/current.json") or {}
    cached = {}
    for code, item in index.get("items", {}).items():
        forecast = item.get("forecast") if isinstance(item, dict) else None
        if isinstance(forecast, dict):
            cached[str(code).upper()] = forecast
    results = []
    for code in SAMPLES:
        for mode in MODES:
            try:
                results.append(run_trial(mode, code, token, cached))
            except api.ApiFailure as error:
                results.append({"mode": mode, "code": code, "listing_calls": 1,
                                "detail_calls": 0, "total_calls": 1,
                                "candidate_count": 0, "documents_examined": 0,
                                "forecast_found": False, "error": f"http_{error.status}"})
    summary = {}
    for mode in MODES:
        rows = [r for r in results if r["mode"] == mode]
        summary[mode] = {
            "trials": len(rows), "mean_api_calls": sum(x["total_calls"] for x in rows) / len(rows),
            "mean_detail_calls": sum(x["detail_calls"] for x in rows) / len(rows),
            "mean_documents_examined": sum(x["documents_examined"] for x in rows) / len(rows),
            "forecasts_found": sum(x["forecast_found"] for x in rows),
        }
    report = {"created_at": datetime.now(timezone.utc).isoformat(), "sample_codes": list(SAMPLES),
              "modes": list(MODES), "trials": results, "summary": summary,
              "note": "Matched 3-issuer pilot; not a full-universe estimate. Trials do not publish to R2."}
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps({"report": str(OUT), "summary": summary}, ensure_ascii=False))


if __name__ == "__main__":
    main()
