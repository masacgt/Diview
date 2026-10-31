import hashlib
import io
import json
from pathlib import Path
from tempfile import TemporaryDirectory
import unittest
from unittest.mock import patch
from apply_cross_issuer_repair import apply_plan


class FakeR2:
    def __init__(self, contents, fail_put=False): self.contents=contents; self.fail_put=fail_put; self.puts=[]
    def get_object(self, *, Bucket, Key):
        body,etag=self.contents[Key]
        return {'Body':io.BytesIO(body),'ETag':etag}
    def put_object(self, *, Bucket, Key, Body, IfMatch, **metadata):
        self.puts.append((Key,IfMatch))
        if self.fail_put: raise RuntimeError('simulated write failure')
        if self.contents[Key][1]!=IfMatch: raise RuntimeError('precondition failed')
        self.contents[Key]=(Body,'new-etag')


class ApplyRepairTest(unittest.TestCase):
    def test_preflight_detects_changes_before_any_put(self):
        with TemporaryDirectory() as tmp:
            root=Path(tmp); client=FakeR2({'key':(b'changed','new')}); revisions=[]
            plan=[{'key':'key','etag':'old','sha256':hashlib.sha256(b'original').hexdigest()}]
            with patch('apply_cross_issuer_repair.validate_plan'), self.assertRaisesRegex(ValueError,'changed'):
                apply_plan(client,'diview-market-data',plan,root,root,root/'candidates',root/'log',lambda *_: revisions.append(1))
            self.assertEqual(client.puts,[]); self.assertEqual(revisions,[])

    def test_failed_write_still_bumps_revision_and_records_attempt(self):
        with TemporaryDirectory() as tmp:
            root=Path(tmp); (root/'replacement').write_bytes(b'new'); revisions=[]
            client=FakeR2({'key':(b'original','old')},fail_put=True)
            plan=[{'key':'key','etag':'old','sha256':hashlib.sha256(b'original').hexdigest(),
                   'replacement_file':'replacement','metadata':{}}]
            with patch('apply_cross_issuer_repair.validate_plan'), self.assertRaisesRegex(RuntimeError,'simulated'):
                apply_plan(client,'diview-market-data',plan,root,root,root/'candidates',root/'log',lambda *_: revisions.append(1) or 'new-token')
            self.assertEqual(client.puts,[('key','old')]); self.assertEqual(revisions,[1])
            events=[json.loads(line)['status'] for line in (root/'log').read_text().splitlines()]
            self.assertEqual(events,['put_attempted','financial_revision_verified'])


if __name__=='__main__': unittest.main()
