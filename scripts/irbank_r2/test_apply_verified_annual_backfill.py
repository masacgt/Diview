import hashlib
import io
from pathlib import Path
from tempfile import TemporaryDirectory
import unittest
from unittest.mock import patch
from botocore.exceptions import ClientError
from apply_verified_annual_backfill import apply


class FakeClient:
    def __init__(self): self.objects={};self.puts=[]
    def get_object(self,*,Bucket,Key):
        body,etag=self.objects[Key];return {'Body':io.BytesIO(body),'ETag':etag}
    def head_object(self,*,Bucket,Key):
        if Key in self.objects:return {'ETag':self.objects[Key][1]}
        raise ClientError({'Error':{'Code':'404','Message':'missing'}},'HeadObject')
    def put_object(self,*,Bucket,Key,Body,**kwargs):
        self.puts.append((Key,kwargs)); self.objects[Key]=(Body,'new-etag')


class ApplyBackfillTest(unittest.TestCase):
    def test_new_and_existing_targets_use_distinct_conditions(self):
        with TemporaryDirectory() as tmp:
            p=Path(tmp);(p/'body').write_bytes(b'new');client=FakeClient();client.objects['old']=(b'old','etag')
            old={'etag':'etag','sha256':hashlib.sha256(b'old').hexdigest(),'metadata':{'ContentType':'application/json'}}
            plan=[{'key':'old','original':old,'replacement_file':'body','replacement_sha256':hashlib.sha256(b'new').hexdigest()}]
            plan += [{'key':f'new-{i}','original':None,'replacement_file':'body','replacement_sha256':hashlib.sha256(b'new').hexdigest()} for i in range(35)]
            calls=[];apply(client,'diview-market-data',plan,p,p/'log',lambda *_:calls.append(1) or 'token')
            self.assertEqual(client.puts[0][1]['IfMatch'],'etag')
            self.assertTrue(all(x[1].get('IfNoneMatch')=='*' for x in client.puts[1:]))
            self.assertEqual(calls,[1])

    def test_preflight_failure_prevents_every_write(self):
        with TemporaryDirectory() as tmp:
            p=Path(tmp);client=FakeClient();client.objects['new-0']=(b'already exists','etag')
            plan=[{'key':f'new-{i}','original':None} for i in range(36)]
            with self.assertRaisesRegex(ValueError,'already exists'):
                apply(client,'diview-market-data',plan,p,p/'log',lambda *_:'token')
            self.assertEqual(client.puts,[])


if __name__=='__main__':unittest.main()
