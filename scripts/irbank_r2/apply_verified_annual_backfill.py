#!/usr/bin/env python3
"""Apply a locally verified 1301/2708 backfill with conditional R2 writes."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import sys
import tempfile
import boto3
from botocore.config import Config
from botocore.exceptions import ClientError
from prepare_verified_annual_backfill import prepare

sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'yfinance_r2'))
from financial_revision import bump_financial_revision


def verify_plan(snapshot, responses, plan_dir, fetched_at):
    plan=json.loads((plan_dir/'plan.json').read_text(encoding='utf8'))
    with tempfile.TemporaryDirectory(prefix='diview-irbank-review-') as name:
        regenerated=Path(name)/'plan';prepare(snapshot,responses,regenerated,fetched_at)
        expected=json.loads((regenerated/'plan.json').read_text(encoding='utf8'))
        if plan!=expected: raise ValueError('plan does not match verified snapshot and IRBANK response')
        for item in plan:
            body=(plan_dir/item['replacement_file']).read_bytes()
            if body!=(regenerated/item['replacement_file']).read_bytes(): raise ValueError('replacement mismatch')
            if hashlib.sha256(body).hexdigest()!=item['replacement_sha256']: raise ValueError('replacement checksum mismatch')
    return plan


def apply(client,bucket,plan,plan_dir,log_path,publish_revision=bump_financial_revision):
    if bucket!='diview-market-data': raise ValueError('unexpected bucket')
    if len(plan)!=36 or len({p['key'] for p in plan})!=36: raise ValueError('unexpected plan size')
    for item in plan:
        key=item['key']; old=item['original']
        if old:
            result=client.get_object(Bucket=bucket,Key=key); raw=result['Body'].read()
            if result['ETag']!=old['etag'] or hashlib.sha256(raw).hexdigest()!=old['sha256']:
                raise ValueError('remote changed since backup: '+key)
        else:
            try: client.head_object(Bucket=bucket,Key=key)
            except ClientError as exc:
                if str(exc.response.get('Error',{}).get('Code')) not in ('404','NoSuchKey','NotFound'): raise
            else: raise ValueError('new target already exists: '+key)
    attempted=False
    with log_path.open('x',encoding='utf8',newline='\n') as log:
        try:
            for item in plan:
                body=(plan_dir/item['replacement_file']).read_bytes(); old=item['original']
                condition={'IfMatch':old['etag']} if old else {'IfNoneMatch':'*'}
                metadata=old['metadata'] if old else {'ContentType':'application/json; charset=utf-8'}
                log.write(json.dumps({'key':item['key'],'status':'put_attempted'})+'\n');log.flush();attempted=True
                client.put_object(Bucket=bucket,Key=item['key'],Body=body,**condition,**metadata)
                saved=client.get_object(Bucket=bucket,Key=item['key'])['Body'].read()
                if hashlib.sha256(saved).hexdigest()!=item['replacement_sha256']: raise ValueError('read-back mismatch: '+item['key'])
                log.write(json.dumps({'key':item['key'],'status':'verified'})+'\n');log.flush()
        finally:
            if attempted:
                revision=publish_revision(client,bucket)
                log.write(json.dumps({'status':'financial_revision_verified','revision':revision})+'\n');log.flush()


if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--snapshot',type=Path,required=True);p.add_argument('--responses',type=Path,required=True);p.add_argument('--plan-dir',type=Path,required=True);p.add_argument('--fetched-at',required=True);p.add_argument('--log',type=Path,required=True);p.add_argument('--apply',action='store_true',required=True)
    a=p.parse_args();plan=verify_plan(a.snapshot,a.responses,a.plan_dir,a.fetched_at)
    account=os.environ['CLOUDFLARE_ACCOUNT_ID']
    if len(account)!=32 or any(c not in '0123456789abcdef' for c in account): raise ValueError('invalid account id')
    client=boto3.client('s3',endpoint_url='https://'+account+'.r2.cloudflarestorage.com',aws_access_key_id=os.environ['R2_ACCESS_KEY_ID'],aws_secret_access_key=os.environ['R2_SECRET_ACCESS_KEY'],region_name='auto',config=Config(retries={'max_attempts':1}))
    apply(client,os.environ['R2_BUCKET'],plan,a.plan_dir,a.log)
    print('Backfill complete; all R2 objects verified and revision updated.')
