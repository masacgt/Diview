"""XTKS date helpers for December month-end close records."""

from __future__ import annotations

import re
from datetime import date
from functools import lru_cache
from typing import Any


@lru_cache(maxsize=32)
def last_xtks_session_in_december(year: int) -> date:
    import exchange_calendars as xcals
    import pandas as pd

    calendar = xcals.get_calendar("XTKS")
    sessions = calendar.sessions_in_range(pd.Timestamp(date(year, 12, 1)), pd.Timestamp(date(year, 12, 31)))
    if len(sessions) == 0:
        raise RuntimeError(f"XTKS calendar has no December sessions for {year}")
    return sessions[-1].date()


def normalize_december_price_point(point: dict[str, Any]) -> tuple[dict[str, Any], bool]:
    month = str(point.get("month") or "")
    if not re.fullmatch(r"\d{4}-12", month):
        return point, False
    expected_date = last_xtks_session_in_december(int(month[:4])).isoformat()
    if str(point.get("date") or "")[:10] == expected_date:
        return point, False
    return {**point, "date": expected_date}, True
