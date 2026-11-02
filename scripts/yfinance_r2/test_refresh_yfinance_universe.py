import io
import json
from pathlib import Path
import sys
import tempfile
import unittest
from types import SimpleNamespace
from unittest.mock import patch

from irbank_universe import IRBANK_UNIVERSE_R2_KEY, JPX_ETF_SOURCE, validate_snapshot

IRBANK_SCRIPT_DIR = Path(__file__).resolve().parents[1] / "irbank_r2"
sys.path.insert(0, str(IRBANK_SCRIPT_DIR))
import refresh_yfinance_universe as refresh  # noqa: E402


class _R2Client:
    def __init__(self) -> None:
        self.objects: dict[str, bytes] = {}
        self.put_count = 0

    def put_object(self, *, Bucket: str, Key: str, Body: bytes, **_kwargs) -> None:
        self.objects[Key] = Body
        self.put_count += 1

    def get_object(self, *, Bucket: str, Key: str):
        return {"Body": io.BytesIO(self.objects[Key])}


class RefreshYfinanceUniverseTests(unittest.TestCase):
    def test_refresh_publishes_integrated_snapshot_and_verifies_readback(self):
        client = _R2Client()
        irbank_rows = [
            {"security_code_display": f"{code:04d}", "irbank_source_code": f"{code:04d}0", "sources": ["IRBANK Connect API v1 /securities"]}
            for code in range(1000, 4500)
        ]
        jpx_rows = [
            {"security_code_display": f"{code:04d}", "listing_date": "2020-01-02"}
            for code in range(5000, 5350)
        ]
        budget = SimpleNamespace(used=12)
        with tempfile.TemporaryDirectory() as directory:
            artifact = Path(directory) / "snapshot.json"
            with patch.object(refresh.irbank_api, "make_r2_client", return_value=client), \
                 patch.object(refresh.irbank_api, "read_r2_document", return_value=None), \
                 patch.object(refresh.irbank_api, "required_env", return_value="test-key"), \
                 patch.object(refresh, "fetch_complete_universe", return_value=(irbank_rows, budget, "updated")), \
                 patch.object(refresh, "fetch_jpx_etfs", return_value=jpx_rows), \
                 patch.object(refresh, "write_github_outputs"), \
                 patch.object(sys, "argv", ["refresh_yfinance_universe.py", "--output", str(artifact)]):
                self.assertEqual(refresh.main(), 0)

            self.assertEqual(client.put_count, 1)
            snapshot = json.loads(client.objects[IRBANK_UNIVERSE_R2_KEY])
            validated = validate_snapshot(snapshot)
            self.assertEqual(len(validated), 3850)
            self.assertEqual(snapshot["supplemental_sources"][0]["source"], JPX_ETF_SOURCE)
            self.assertEqual(snapshot["supplemental_sources"][0]["listed_etf_count"], 350)
            self.assertEqual(json.loads(artifact.read_text(encoding="utf-8"))["security_code_sha256"], snapshot["security_code_sha256"])


if __name__ == "__main__":
    unittest.main()
