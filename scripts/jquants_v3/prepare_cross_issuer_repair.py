#!/usr/bin/env python3
"""Prepare an immutable local R2 repair plan from a previously saved snapshot.

No R2 access or writes. Every replacement is based on exact, reviewed
fingerprints and preserves independently sourced sections.
"""
from __future__ import annotations
import argparse
from copy import deepcopy
from decimal import Decimal
import hashlib
import json
from pathlib import Path
from prepare_v2_financial_repair import prepare as replace_foreign_seed

BAD = {'1301': {'2017/03': (6639,5594), '2023/03': (7070,6073),
                 '2024/03': (7392,7050), '2025/03': (7957,7126), '2026/03': (8283,6973)},
       '2708': {'2011/10': (74430,30631), '2012/10': (78971,33345), '2013/10': (88145,36479),
                '2014/10': (96938,38140), '2015/10': (105307,42058), '2016/10': (113626,46527),
                '2017/10': (122766,52745), '2018/10': (132499,59070), '2019/10': (136134,68216),
                '2020/10': (135835,85102), '2021/10': (147592,98989), '2022/10': (183053,109621),
                '2023/10': (211405,130119), '2024/10': (234950,139446), '2025/10': (245109,156015)}}


def quarantine(row: dict, code: str, expected: tuple[int, int]) -> dict:
    result = deepcopy(row)
    sources = result.get('source_values') or {}
    seed = sources.get('seed') or {}
    values = seed.get('values') or {}
    if seed.get('source_key') != f'market/irbank/v1/securities/{code}/overview.json' or (values.get('sales'), values.get('total_assets')) != expected:
        raise ValueError('foreign seed fingerprint or origin changed')
    yahoo = (sources.get('yfinance') or {}).get('values') or {}
    status = result.setdefault('field_status', {})
    reasons = result.setdefault('field_status_reason', {})
    for field in values:
        if field not in yahoo:
            result.pop(field, None)
            (result.get('field_units') or {}).pop(field, None)
            status[field] = 'unsupported'
            reasons[field] = ['cross_issuer_seed_quarantined']
    calculated = (sources.get('calculated') or {}).get('values') or {}
    for field, entry in list(calculated.items()):
        if 'seed' in (entry.get('input_sources') or {}).values():
            del calculated[field]
            if field not in yahoo:
                result.pop(field, None)
                status[field] = 'unsupported'
                reasons[field] = ['cross_issuer_seed_quarantined']
    sources['seed'] = {**seed, 'values': {}, 'field_units': {}, 'validation_status': 'unsupported',
                       'reason': 'cross_issuer_seed_quarantined'}
    result['source_values'] = sources
    return result


def inject_jquants(document: dict, candidate: dict) -> dict:
    result = deepcopy(document)
    row = result.get('annual') or {}
    if result.get('symbol') != candidate['code']+'.T' or row.get('period_end') != candidate['period_end']:
        raise ValueError('J-Quants period or symbol mismatch')
    if candidate.get('source') != 'J-Quants API /fins/summary' or candidate.get('document_type') != 'FYFinancialStatements_Consolidated_JP':
        raise ValueError('unexpected J-Quants provenance')
    sources = row.setdefault('source_values', {})
    if (sources.get('seed') or {}).get('values'):
        raise ValueError('existing seed is not empty')
    values = {}
    for field, raw in candidate['values'].items():
        value=Decimal(raw)
        if not value.is_finite(): raise ValueError('non-finite value')
        values[field] = int(value) if value == value.to_integral_value() else float(value)
    if not values: raise ValueError('empty candidate')
    sources['seed'] = {'source': candidate['source'], 'source_key': 'jquants:/v2/fins/summary',
        'values': values, 'field_units': candidate['field_units'],
        'disclosure_number': candidate.get('disclosure_number'),
        'disclosed_date': candidate.get('disclosed_date'), 'document_type': candidate['document_type']}
    result['annual'] = row
    return result


def modify(key: str, document: dict, candidates: dict) -> dict | None:
    code = next((c for c in BAD if f'/{c}/overview.json' in key or f'/{c}.T/financials/' in key), None)
    if code is None: raise ValueError('unexpected snapshot key')
    result = deepcopy(document)
    if key.endswith('/overview.json'):
        rows = result.get('financials',{}).get('annual')
        if not isinstance(rows,list): raise ValueError('overview annual rows missing')
        keep=[]; removed=set()
        for row in rows:
            period=row.get('fiscal_year'); expected=BAD[code].get(period)
            if expected:
                if (row.get('sales'),row.get('total_assets')) != expected: raise ValueError('overview fingerprint mismatch')
                removed.add(period)
            else: keep.append(row)
        if removed != set(BAD[code]): raise ValueError('overview missing expected contaminated periods')
        result['financials']['annual']=keep
        return result
    annual=result.get('annual')
    if not annual: return None
    period=str(annual.get('period_end') or '')
    fiscal=period[:7].replace('-','/')
    expected=BAD[code].get(fiscal)
    candidate=candidates.get((code,period))
    if expected:
        if candidate: return replace_foreign_seed(result,candidate,expected)
        safe=quarantine(annual,code,expected)
        yahoo=(safe.get('source_values',{}).get('yfinance') or {}).get('values') or {}
        if not yahoo: result.pop('annual')
        else: result['annual']=safe
        return result
    if code == '2708' and period in ('2025-03-31','2026-03-31'):
        if not candidate: raise ValueError('required J-Quants candidate missing')
        return inject_jquants(result,candidate)
    return None


def main() -> None:
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--snapshot',type=Path,required=True)
    parser.add_argument('--candidates',type=Path,required=True)
    parser.add_argument('--output',type=Path,required=True)
    args=parser.parse_args()
    manifest=json.loads((args.snapshot/'manifest.json').read_text(encoding='utf-8'))
    cs=json.loads(args.candidates.read_text(encoding='utf-8'))['candidates']
    candidates={(c['code'],c['period_end']):c for c in cs}
    if len(candidates)!=len(cs): raise ValueError('duplicate J-Quants candidates require review')
    changes=[]
    args.output.mkdir(exist_ok=False)
    for entry in manifest:
        data=(args.snapshot/entry['file']).read_bytes()
        if hashlib.sha256(data).hexdigest()!=entry['sha256']: raise ValueError('backup hash mismatch')
        updated=modify(entry['key'],json.loads(data),candidates)
        if updated is None: continue
        payload=json.dumps(updated,ensure_ascii=False,separators=(',',':'),allow_nan=False).encode('utf-8')
        name=entry['file']
        (args.output/name).write_bytes(payload)
        changes.append({**entry,'replacement_file':name,'replacement_sha256':hashlib.sha256(payload).hexdigest()})
    if len(changes)!=19: raise ValueError(f'unexpected change count: {len(changes)}')
    (args.output/'plan.json').write_text(json.dumps(changes,ensure_ascii=False,indent=2),encoding='utf-8')
    print(f'Prepared {len(changes)} local replacements; no production writes.')


if __name__=='__main__': main()
