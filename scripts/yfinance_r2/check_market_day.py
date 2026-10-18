"""Select today's or a missed, unpublished XTKS session for scheduled sync."""

from __future__ import annotations

from datetime import date, datetime
import json
import os
from pathlib import Path
from zoneinfo import ZoneInfo

import boto3
from botocore.exceptions import ClientError
import exchange_calendars as xcals
import pandas as pd


JST = ZoneInfo("Asia/Tokyo")


def latest_completed_session(now: datetime) -> date:
    current = now.replace(tzinfo=JST) if now.tzinfo is None else now.astimezone(JST)
    calendar = xcals.get_calendar("XTKS")
    session = calendar.date_to_session(pd.Timestamp(current.date()), direction="previous")
    if session.date() == current.date():
        close = calendar.session_close(session).to_pydatetime().astimezone(JST)
        if current < close:
            session = calendar.previous_session(session)
    return session.date()


def active_generation() -> str | None:
    account = os.environ["CLOUDFLARE_ACCOUNT_ID"].strip()
    bucket = os.environ["R2_BUCKET"].strip()
    client = boto3.client(
        "s3",
        endpoint_url=f"https://{account}.r2.cloudflarestorage.com",
        aws_access_key_id=os.environ["R2_ACCESS_KEY_ID"].strip(),
        aws_secret_access_key=os.environ["R2_SECRET_ACCESS_KEY"].strip(),
        region_name="auto",
    )
    key = "market/yfinance/v2/schedules/current.json"
    try:
        response = client.get_object(Bucket=bucket, Key=key)
    except ClientError as exc:
        code = str((exc.response.get("Error") or {}).get("Code") or "")
        if code in {"NoSuchKey", "404", "NotFound"}:
            return None
        raise
    body = response["Body"]
    try:
        state = json.loads(body.read().decode("utf-8"))
    finally:
        body.close()
    value = state.get("active_session_date") if isinstance(state, dict) else None
    if not value:
        return None
    normalized = date.fromisoformat(str(value)).isoformat()
    if normalized != str(value):
        raise ValueError(f"Invalid active XTKS session date in R2: {value!r}")
    return normalized


def main() -> int:
    now = datetime.now(JST)
    today_jst = now.date()
    calendar = xcals.get_calendar("XTKS")
    is_session = bool(calendar.is_session(pd.Timestamp(today_jst)))
    manual_override = os.environ.get("ALLOW_CLOSED_MARKET", "false").lower() in {"1", "true", "yes"}
    session_date = latest_completed_session(now)

    if manual_override:
        is_open = True
        print("Manual run requested; allowing collection outside a Tokyo trading session.")
    elif is_session:
        # A scheduled run can start after midnight. Use the latest close available
        # when the collector starts, and pass that exact date into the sync job.
        is_open = True
        print(f"Tokyo Stock Exchange session for {today_jst}: OPEN; selected completed session {session_date}.")
    else:
        active_date = active_generation()
        is_open = active_date != session_date.isoformat()
        if is_open:
            print(
                f"Tokyo Stock Exchange is closed on {today_jst}; recovering unpublished session "
                f"{session_date} (active generation: {active_date or 'none'})."
            )
        else:
            print(
                f"Tokyo Stock Exchange is closed on {today_jst}; latest completed session "
                f"{session_date} is already published. Skipping collection."
            )

    github_output = Path(os.environ["GITHUB_OUTPUT"]) if "GITHUB_OUTPUT" in os.environ else None
    if github_output:
        with github_output.open("a", encoding="utf-8") as output:
            output.write(f"is_open={'true' if is_open else 'false'}\n")
            output.write(f"session_date={session_date.isoformat()}\n")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
