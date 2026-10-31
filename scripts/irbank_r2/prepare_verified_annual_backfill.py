#!/usr/bin/env python3
"""Prepare a local, reviewable 1301/2708 IRBANK annual backfill; no R2 writes."""
import argparse
from copy import deepcopy
from datetime import date
import hashlib
import json
import math
from pathlib import Path

EXPECTED={'1301':'E00012','2708':'E02938'}
MONEY=('sales','operating_profit','operating_cf','investing_cf','total_assets')
OVERVIEW_MONEY=('sales','operating_profit','ordinary_income','net_income','operating_cf','investing_cf',
                'extraordinary_income','extraordinary_loss','total_assets','net_assets','total_liabilities')


def periods(code, response):
    if response.get('edinet_code') != EXPECTED[code] or not response.get('attribution'):
        raise ValueError('issuer or attribution mismatch')
    units={field['key']:field.get('unit') for field in response.get('fields',[]) if 'key' in field}
    if any(units.get(k)!='jpy' for k in (*MONEY,'owners_equity')): raise ValueError('unexpected money unit')
    result={}
    for item in response.get('periods',[]):
        fiscal=item.get('fiscal_year','')
        if fiscal not in {f'{y}/03' for y in range(2008,2025)}: continue
        if fiscal in result or item.get('is_consolidated') is not True: raise ValueError('duplicate or nonconsolidated period')
        exceptions=set(item.get('scope_exceptions') or [])
        if exceptions.intersection((*MONEY,'owners_equity')): raise ValueError('mixed scope input')
        standard_exceptions=item.get('standard_exceptions') or {}
        if not isinstance(standard_exceptions,dict) or any(set(fields or []).intersection((*MONEY,'owners_equity')) for fields in standard_exceptions.values()):
            raise ValueError('mixed accounting-standard input')
        for key in (*MONEY,'owners_equity'):
            val=item.get(key)
            if isinstance(val,bool) or not isinstance(val,(int,float)) or not math.isfinite(val): raise ValueError('missing or invalid financial value')
        if item['total_assets']<=0: raise ValueError('invalid assets')
        result[fiscal]=item
    if set(result)!={f'{y}/03' for y in range(2008,2025)}: raise ValueError('incomplete historical series')
    return result,units


def seed_values(row):
    values={k:row[k] for k in MONEY}
    values['equity_ratio_computed']=round(row['owners_equity']/row['total_assets']*100,9)
    return values


def overview_row(row, units):
    output={k:row.get(k) for k in ('fiscal_year','accounting_standard','is_consolidated')}
    field_units={}
    for field in OVERVIEW_MONEY:
        value=row.get(field)
        if value is None: continue
        if units.get(field)!='jpy' or field in (row.get('scope_exceptions') or []): raise ValueError('unsupported overview field unit or scope')
        output[field]=value; field_units[field]='jpy'
    for field in ('eps',):
        if row.get(field) is not None and field not in (row.get('scope_exceptions') or []):
            if units.get(field)!='jpy_per_share': raise ValueError('unexpected EPS unit')
            output[field]=row[field];field_units[field]='jpy_per_share'
    output['equity_ratio_computed']=seed_values(row)['equity_ratio_computed']
    field_units['equity_ratio_computed']='percent';output['field_units']=field_units
    return output


def merge_annual(doc, code, row, attribution, fetched_at):
    result=deepcopy(doc); period=row['fiscal_year'].replace('/','-')+'-31'
    if result.get('symbol')!=code+'.T': raise ValueError('symbol mismatch')
    annual=result.get('annual') or {'fiscal_year':row['fiscal_year'],'period_end':period}
    if annual.get('period_end')!=period or annual.get('fiscal_year')!=row['fiscal_year']:
        raise ValueError('different annual period')
    sources=annual.setdefault('source_values',{}); seed=sources.get('seed') or {}
    if seed.get('values'): raise ValueError('will not replace existing seed values')
    values=seed_values(row); units={k:('percent' if k=='equity_ratio_computed' else 'jpy') for k in values}
    sources['seed']={'source':'IRBANK Connect','source_key':f'market/irbank/v1/securities/{code}/overview.json',
        'values':values,'field_units':units,'attribution':attribution,'fetched_at':fetched_at,
        'equity_ratio_formula':'owners_equity / total_assets * 100',
        'period_basis':'IRBANK annual; may include trailing-twelve-month estimates'}
    statuses=annual.setdefault('field_status',{}); reasons=annual.setdefault('field_status_reason',{})
    yahoo=(sources.get('yfinance') or {}).get('values') or {}
    calculations=(sources.get('calculated') or {}).get('values') or {}
    for k,v in values.items():
        if k not in yahoo and k not in calculations:
            annual[k]=v;statuses[k]='available';reasons.pop(k,None)
    result['annual']=annual
    return result


def prepare(snapshot, responses, output, fetched_at):
    date.fromisoformat(fetched_at[:10])
    manifest=json.loads((snapshot/'manifest.json').read_text(encoding='utf8')); mapping={e['key']:e for e in manifest}
    docs={}
    for key,e in mapping.items():
        raw=(snapshot/e['file']).read_bytes()
        if hashlib.sha256(raw).hexdigest()!=e['sha256']: raise ValueError('snapshot hash mismatch')
        docs[key]=json.loads(raw)
    input_docs={c:json.loads((responses/(c+'.json')).read_text(encoding='utf8')) for c in EXPECTED}
    normalized={c:periods(c,input_docs[c]) for c in EXPECTED}
    replacements={}
    for code in EXPECTED:
        rows,units=normalized[code]; attr=input_docs[code]['attribution']; overview_key=f'market/irbank/v1/securities/{code}/overview.json'
        overview=deepcopy(docs[overview_key]); annual=overview['financials']['annual']; old={r['fiscal_year']:r for r in annual}
        if len(old)!=len(annual): raise ValueError('duplicate overview periods')
        if code=='1301' and any(k in old for k in rows): raise ValueError('1301 overview already has historical rows')
        if code=='2708':
            target=old.get('2014/03') or {}
            if (target.get('sales'),target.get('total_assets'),target.get('operating_cf'))!=(62268,27566,5179):
                raise ValueError('2708 2014 fingerprint changed')
        for fiscal,row in rows.items():
            if fiscal not in old or (code=='2708' and fiscal=='2014/03'): old[fiscal]=overview_row(row,units)
        overview['financials']['annual']=[old[k] for k in sorted(old)]
        overview['sources'].setdefault('financials',{})['annual']=attr
        replacements[overview_key]=overview
        prefix=f'market/yfinance/v2/securities/{code}.T/financials/'
        for fiscal,row in sorted(rows.items()):
            end=fiscal.replace('/','-')+'-31'; matches=[]
            for key,d in docs.items():
                if not key.startswith(prefix): continue
                a=d.get('annual') or {}; v=d.get('valuation') or {}
                if a.get('period_end')==end or (not a and v.get('period_end')==end): matches.append(key)
            if len(matches)>1: raise ValueError('ambiguous V2 period')
            key=matches[0] if matches else prefix+end+'.json'
            if key in docs: document=docs[key]
            else: document={'schema_version':1,'symbol':code+'.T','year':int(end[:4]),'source':'IRBANK Connect historical annual backfill'}
            replacements[key]=merge_annual(document,code,row,attr,fetched_at)
    if len(replacements)!=36: raise ValueError('unexpected target count')
    output.mkdir(exist_ok=False); plan=[]
    for index,(key,document) in enumerate(sorted(replacements.items())):
        body=json.dumps(document,ensure_ascii=False,separators=(',',':'),allow_nan=False).encode('utf8'); filename=f'{index:03}.json';(output/filename).write_bytes(body)
        entry=mapping.get(key)
        plan.append({'key':key,'replacement_file':filename,'replacement_sha256':hashlib.sha256(body).hexdigest(),
                     'original':entry})
    (output/'plan.json').write_text(json.dumps(plan,ensure_ascii=False,indent=2),encoding='utf8')
    print('Prepared',len(plan),'replacements; existing',sum(bool(x['original']) for x in plan),'new',sum(not x['original'] for x in plan))


if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--snapshot',type=Path,required=True);p.add_argument('--responses',type=Path,required=True);p.add_argument('--output',type=Path,required=True);p.add_argument('--fetched-at',required=True)
    a=p.parse_args();prepare(a.snapshot,a.responses,a.output,a.fetched_at)
