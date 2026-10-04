import io
import json
import unittest
from unittest.mock import patch

from fetch_jquants_universe import display_code, fetch_codes


class _Response(io.BytesIO):
    def __enter__(self):
        return self

    def __exit__(self, *_args):
        self.close()


class JQuantsUniverseTests(unittest.TestCase):
    def test_code_conversion(self):
        self.assertEqual(display_code("130A0"), "130A")
        self.assertEqual(display_code("13010"), "1301")
        with self.assertRaises(ValueError):
            display_code("1301.T")

    def test_all_pages_are_required(self):
        first = {"data": [{"Code": f"{n:04d}0"} for n in range(1000, 2000)], "pagination_key": "next"}
        second = {"data": [{"Code": "20000"}]}
        responses = [_Response(json.dumps(page).encode()) for page in (first, second)]
        with patch("fetch_jquants_universe.urlopen", side_effect=responses) as request:
            codes = fetch_codes("test-key")
        self.assertEqual(len(codes), 1001)
        self.assertEqual(codes[-1], "2000")
        self.assertIn("pagination_key=next", request.call_args_list[1].args[0].full_url)

    def test_partial_response_is_rejected(self):
        response = _Response(b'{"data":[{"Code":"13010"}]}')
        with patch("fetch_jquants_universe.urlopen", return_value=response):
            with self.assertRaisesRegex(RuntimeError, "only 1 issues"):
                fetch_codes("test-key")


if __name__ == "__main__":
    unittest.main()
