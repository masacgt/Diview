#!/usr/bin/env python3
"""Prepare one reviewed V2 annual repair locally; never writes to R2."""
from __future__ import annotations

import argparse
from copy import deepcopy
from decimal import Decimal
import json
from pathlib import Path


def prepare(document: dict, candidate: dict, expected: tuple[int, int]) -> dict:
    """Replace a verified foreign seed with the same security's J-Quants FY.

    Independent Yahoo values and valuations are retained. This intentionally
    refuses candidates for another security or fiscal period.
    """
    result = deepcopy(document)
    row = result.get('annual') or {}
    code = candidate.get('code')
    period = candidate.get('period_end')
    if result.get('symbol') != f'{code}.T' or row.get('period_end') != period:
        raise ValueError('symbol or fiscal period does not match candidate')
    sources = row.get('source_values') or {}
    seed = sources.get('seed') or {}
    old = seed.get('values') or {}
    if seed.get('source_key') != f'market/irbank/v1/securities/{code}/overview.json':
        raise ValueError('unexpected seed provenance')
    if (old.get('sales'), old.get('total_assets')) != expected:
        raise ValueError('foreign seed fingerprint does not match')
    if candidate.get('source') != 'J-Quants API /fins/summary' or candidate.get('document_type') != 'FYFinancialStatements_Consolidated_JP':
        raise ValueError('unexpected candidate provenance')
    yahoo = (sources.get('yfinance') or {}).get('values') or {}
    statuses = row.setdefault('field_status', {})
    reasons = row.setdefault('field_status_reason', {})
    units = row.setdefault('field_units', {})
    for field in old:
        if field not in yahoo:
            row.pop(field, None)
            units.pop(field, None)
            statuses[field] = 'unsupported'
            reasons[field] = ['cross_issuer_seed_replaced']
    calculations = (sources.get('calculated') or {}).get('values') or {}
    for field, entry in list(calculations.items()):
        if 'seed' in (entry.get('input_sources') or {}).values():
            del calculations[field]
            if field not in yahoo:
                row.pop(field, None)
                units.pop(field, None)
                statuses[field] = 'unsupported'
                reasons[field] = ['cross_issuer_seed_replaced']
    values = {}
    for field, raw in (candidate.get('values') or {}).items():
        value = Decimal(raw)
        if not value.is_finite():
            raise ValueError('non-finite candidate value')
        values[field] = int(value) if value == value.to_integral_value() else float(value)
        if field not in yahoo and field not in calculations:
            row[field] = values[field]
            units[field] = candidate['field_units'][field]
            statuses[field] = 'available'
            reasons.pop(field, None)
    if not values:
        raise ValueError('candidate contains no values')
    sources['seed'] = {'source': candidate['source'], 'source_key': 'jquants:/v2/fins/summary',
        'values': values, 'field_units': candidate['field_units'],
        'disclosure_number': candidate.get('disclosure_number'),
        'disclosed_date': candidate.get('disclosed_date'), 'document_type': candidate['document_type']}
    row['source_values'] = sources
    if row.get('migration_source') == seed.get('source_key'):
        row.pop('migration_source')
    result['annual'] = row
    return result


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--original', type=Path, required=True)
    parser.add_argument('--candidates', type=Path, required=True)
    parser.add_argument('--code', required=True)
    parser.add_argument('--period', required=True)
    parser.add_argument('--expected-sales', type=int, required=True)
    parser.add_argument('--expected-assets', type=int, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    document = json.loads(args.original.read_text(encoding='utf-8'))
    candidates = json.loads(args.candidates.read_text(encoding='utf-8'))['candidates']
    matches = [c for c in candidates if c.get('code') == args.code and c.get('period_end') == args.period]
    if len(matches) != 1:
        raise ValueError('expected exactly one candidate')
    result = prepare(document, matches[0], (args.expected_sales, args.expected_assets))
    with args.output.open('x', encoding='utf-8', newline='\n') as output:
        json.dump(result, output, ensure_ascii=False, indent=2, allow_nan=False)
        output.write('\n')
    print('Prepared local review file; no production data changed.')


if __name__ == '__main__':
    main()
