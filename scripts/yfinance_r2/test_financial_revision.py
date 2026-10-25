import json
from io import BytesIO
from unittest import TestCase
from unittest.mock import Mock, patch

import financial_revision


class FinancialRevisionTests(TestCase):
    def test_returns_the_written_revision_when_read_back_matches(self):
        client = Mock()
        client.get_object.return_value = {
            "Body": BytesIO(json.dumps({
                "schema_version": 1,
                "revision": "a" * 32,
                "updated_at": "2026-10-26T00:00:00Z",
            }).encode())
        }
        with patch.object(financial_revision, "uuid4") as uuid:
            uuid.return_value.hex = "a" * 32
            revision = financial_revision.bump_financial_revision(client, "test")

        self.assertEqual(revision, "a" * 32)
        body = json.loads(client.put_object.call_args.kwargs["Body"])
        self.assertEqual(body["revision"], revision)

    def test_accepts_a_valid_revision_written_by_a_concurrent_refresh(self):
        client = Mock()
        client.get_object.return_value = {
            "Body": BytesIO(json.dumps({
                "schema_version": 1,
                "revision": "b" * 32,
                "updated_at": "2026-10-26T00:00:01Z",
            }).encode())
        }
        with patch.object(financial_revision, "uuid4") as uuid:
            uuid.return_value.hex = "a" * 32
            revision = financial_revision.bump_financial_revision(client, "test")

        self.assertEqual(revision, "b" * 32)

    def test_rejects_invalid_read_back_without_publishing_a_valid_token(self):
        client = Mock()
        client.get_object.return_value = {
            "Body": BytesIO(b'{"schema_version":1,"revision":"broken"}')
        }
        with patch.object(financial_revision, "uuid4") as uuid:
            uuid.return_value.hex = "a" * 32
            with self.assertRaisesRegex(RuntimeError, "read-back is invalid"):
                financial_revision.bump_financial_revision(client, "test")
