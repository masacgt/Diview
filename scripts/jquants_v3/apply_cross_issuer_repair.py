#!/usr/bin/env python3
"""Apply a verified local repair plan with R2 ETag preconditions and read-back."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import sys
import boto3
from botocore.config import Config
from prepare_cross_issuer_repair import modify

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'yfinance_r2'))
from financial_revision import bump_financial_revision


def validate_plan(plan, snapshot, plan_dir, candidates):
    original=json.loads((snapshot/'manifest.json').read_text(encoding='utf-8'))
    by_key={x['key']:x for x in original}
    cs=json.loads(candidates.read_text(encoding='utf-8'))['candidates']
    lookup={(c['code'],c['period_end']):c for c in cs}
    if len(lookup)!=len(cs): raise ValueError('duplicate candidates')
    if len(plan)!=19 or len({x['key'] for x in plan})!=19: raise ValueError('unexpected repair plan size')
    expected=[]
    for entry in original:
        raw=(snapshot/entry['file']).read_bytes()
        if hashlib.sha256(raw).hexdigest()!=entry['sha256']: raise ValueError('snapshot checksum mismatch')
        candidate=modify(entry['key'],json.loads(raw),lookup)
        if candidate is not None: expected.append(entry['key'])
    if {x['key'] for x in plan}!=set(expected): raise ValueError('repair targets differ from snapshot')
    for item in plan:
        entry=by_key[item['key']]
        for field in ('file','etag','sha256','metadata'):
            if item[field]!=entry[field]: raise ValueError('repair plan differs from backup')
        if item['replacement_file'] != item['file'] or Path(item['file']).name != item['file']:
            raise ValueError('unexpected repair file')
        original_doc=json.loads((snapshot/item['file']).read_bytes())
        computed=modify(item['key'],original_doc,lookup)
        replacement=(plan_dir/item['replacement_file']).read_bytes()
        expected_bytes=json.dumps(computed,ensure_ascii=False,separators=(',',':'),allow_nan=False).encode('utf-8')
        if replacement!=expected_bytes or hashlib.sha256(replacement).hexdigest()!=item['replacement_sha256']:
            raise ValueError('replacement differs from reviewed calculation')


def apply_plan(client, bucket, plan, snapshot, plan_dir, candidates, log_path, publish_revision=bump_financial_revision):
    if bucket != 'diview-market-data': raise ValueError('unexpected bucket')
    validate_plan(plan,snapshot,plan_dir,candidates)
    # All objects must still be identical before the first write. If-Match
    # closes the race between this check and each individual PUT.
    for item in plan:
        response=client.get_object(Bucket=bucket,Key=item['key'])
        raw=response['Body'].read()
        if response['ETag']!=item['etag'] or hashlib.sha256(raw).hexdigest()!=item['sha256']:
            raise ValueError('remote object changed since snapshot: '+item['key'])
    attempted=False
    with log_path.open('x',encoding='utf-8',newline='\n') as log:
        try:
            for item in plan:
                body=(plan_dir/item['replacement_file']).read_bytes()
                log.write(json.dumps({'key':item['key'],'status':'put_attempted'})+'\n'); log.flush()
                attempted=True
                client.put_object(Bucket=bucket,Key=item['key'],Body=body,IfMatch=item['etag'],**item['metadata'])
                saved=client.get_object(Bucket=bucket,Key=item['key'])['Body'].read()
                if hashlib.sha256(saved).hexdigest()!=item['replacement_sha256']:
                    raise ValueError('read-back mismatch: '+item['key'])
                log.write(json.dumps({'key':item['key'],'status':'verified'})+'\n'); log.flush()
        finally:
            if attempted:
                token=publish_revision(client,bucket)
                log.write(json.dumps({'status':'financial_revision_verified','revision':token})+'\n'); log.flush()


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--snapshot',type=Path,required=True)
    parser.add_argument('--plan-dir',type=Path,required=True)
    parser.add_argument('--candidates',type=Path,required=True)
    parser.add_argument('--log',type=Path,required=True)
    parser.add_argument('--apply',action='store_true',required=True)
    args=parser.parse_args()
    account=os.environ['CLOUDFLARE_ACCOUNT_ID']
    if len(account)!=32 or any(c not in '0123456789abcdef' for c in account): raise ValueError('invalid account id')
    client=boto3.client('s3',endpoint_url='https://'+account+'.r2.cloudflarestorage.com',
        aws_access_key_id=os.environ['R2_ACCESS_KEY_ID'],aws_secret_access_key=os.environ['R2_SECRET_ACCESS_KEY'],
        region_name='auto',config=Config(retries={'max_attempts':1}))
    plan=json.loads((args.plan_dir/'plan.json').read_text(encoding='utf-8'))
    apply_plan(client,os.environ['R2_BUCKET'],plan,args.snapshot,args.plan_dir,args.candidates,args.log)
    print('Repair completed and all objects read back; revision updated.')


if __name__=='__main__': main()
