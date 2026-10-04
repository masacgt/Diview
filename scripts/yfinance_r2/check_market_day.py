"""Report whether today's Japan exchange session is open."""  from __future__ import annotations  from datetime import datetime import os from pathlib import Path from zoneinfo import ZoneInfo  import exchange_calendars as xcals import pandas as pd   def main() -> int:     today_jst = datetime.now(ZoneInfo("Asia/Tokyo")).date()     calendar = xcals.get_calendar("XTKS")     is_open = bool(calendar.is_session(pd.Timestamp(today_jst)))
    if os.environ.get("ALLOW_CLOSED_MARKET", "false").lower() in {"1", "true", "yes"}:
        print("Manual run requested; allowing collection outside a Tokyo trading session.")
        is_open = True
    if os.environ.get("ALLOW_CLOSED_MARKET", "false").lower() in {"1", "true", "yes"}:
        print("Manual run requested; allowing collection outside a Tokyo trading session.")
        is_open = True     state = "OPEN" if is_open else "CLOSED"     print(f"Tokyo Stock Exchange session for {today_jst}: {state}")      github_output = Path(os.environ["GITHUB_OUTPUT"]) if "GITHUB_OUTPUT" in os.environ else None     if github_output:         with github_output.open("a", encoding="utf-8") as output:             output.write(f"is_open={'true' if is_open else 'false'}\n")             output.write(f"session_date={today_jst.isoformat()}\n")     return 0   if __name__ == "__main__":     raise SystemExit(main())
