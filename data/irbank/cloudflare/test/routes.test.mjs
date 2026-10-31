import test from 'node:test';
import assert from 'node:assert/strict';
import worker from '../src/index.js';

function environment(limit = 1, storageFails = false) {
  const rows = new Map();
  return {
    APP_DEFINITIONS: { get: async () => null },
    MARKET_DATA: {
      get: async () => ({ json: async () => ({ security: { code: '7203' }, financials: { annual: [{ fiscal_year: '2026/03' }] }, dividends: { records: [{ fiscal_year: 2025, annual_dps_adjusted: 10 }] }, valuation: { history: [] }, officers: ['private-not-needed'] }) }),
      list: async () => ({ objects: [], truncated: false }),
    },
    DB: { prepare: sql => ({ bind: (...values) => ({
      first: async () => sql.includes('api_request_log') ? { request_count: limit } : rows.get(values[0]),
      run: async () => { if (storageFails) throw Error('storage unavailable'); if (sql.includes('INSERT INTO support_feedback') && !rows.has(values[0])) rows.set(values[0], { request_hash: values[2] }); }
    }), run: async () => { if (storageFails) throw Error('storage unavailable'); } }) }
  };
}

function dividendEnvironment(documents, marketAvailable = true, reads = null) {
  const indexKey = 'market/yfinance/v2/issuer_dividends/current.json';
  const manifestKey = 'market/yfinance/v2/issuer_dividends/shards/current.json';
  if (documents.has(indexKey) && !documents.has(manifestKey)) {
    const index = documents.get(indexKey);
    const grouped = new Map();
    for (const [code, item] of Object.entries(index?.items || {})) {
      const prefix = code.slice(0, 2).toUpperCase();
      if (!grouped.has(prefix)) grouped.set(prefix, {});
      grouped.get(prefix)[code.toUpperCase()] = item;
    }
    const slot = 'a';
    documents.set(manifestKey, {
      schema_version: 1,
      slot,
      prefix_length: 2,
      prefixes: [...grouped.keys()].sort(),
      item_count: Object.keys(index?.items || {}).length,
      source: index.source,
      attribution: index.attribution,
    });
    for (const [prefix, items] of grouped) {
      documents.set(`market/yfinance/v2/issuer_dividends/shards/${slot}/${prefix}.json`, {
        schema_version: 1, prefix, items,
      });
    }
  }
  return {
    APP_DEFINITIONS: { get: async () => null },
    MARKET_DATA: marketAvailable ? {
      get: async key => {
        reads?.push(key);
        return documents.has(key) ? { json: async () => documents.get(key) } : null;
      },
      list: async ({ prefix }) => ({
        objects: [...documents.keys()].filter(key => key.startsWith(prefix)).map(key => ({ key })),
        truncated: false,
      }),
    } : undefined,
    DB: { prepare: sql => ({ bind: () => ({ first: async () =>
      sql.includes('api_request_log') ? { request_count: 1 } : null
    }) }) }
  };
}

function japanToday() {
  const parts = Object.fromEntries(new Intl.DateTimeFormat('en', {
    timeZone: 'Asia/Tokyo', year: 'numeric', month: '2-digit', day: '2-digit'
  }).formatToParts(new Date()).map(part => [part.type, part.value]));
  return `${parts.year}-${parts.month}-${parts.day}`;
}

function dateOffset(date, offsetDays) {
  return new Date(Date.parse(`${date}T00:00:00.000Z`) + offsetDays * 86400000).toISOString().slice(0, 10);
}
const payload = () => ({ requestId: 'd982c634-a464-4a62-a266-35a1ee8c3821', category: 'feature', name: '', email: '', message: 'test' });
const request = body => new Request('https://example.com/api/v1/feedback', { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify(body) });

test('financial API only returns the necessary public document fields', async () => {
  const result = await worker.fetch(new Request('https://example.com/api/v1/financial-data/7203'), environment());
  assert.equal(result.status, 200);
  const data = await result.json();
  assert.equal(data.security.code, '7203');
  assert.equal(data.officers, undefined);
  assert.equal((await worker.fetch(new Request('https://example.com/api/v1/financial-data/7203', { method: 'POST' }), environment())).status, 405);
});

test('financial API reads only the active issuer-dividend shard for one security', async () => {
  const base = 'market/yfinance/v2/securities/7203.T';
  const indexKey = 'market/yfinance/v2/issuer_dividends/current.json';
  const documents = new Map([
    [`${base}/profile.json`, { display_name: 'トヨタ自動車', symbol: '7203.T' }],
    [`${base}/current.json`, { dividend: { calendar_year_metrics: { annual_records: [] } } }],
    [`market/irbank/v1/securities/7203/overview.json`, { dividends: { records: [] } }],
    [`${base}/financials/2025-03-31.json`, {
      annual: { fiscal_year: '2025/03', period_end: '2025-03-31', field_status: {} },
    }],
    ['market/yfinance/v2/issuer_dividends/shards/current.json', {
      schema_version: 1, slot: 'b', prefix_length: 2, prefixes: ['72'], source: 'IRBANK Connect',
      attribution: { source_label: 'IRBANK' },
    }],
    ['market/yfinance/v2/issuer_dividends/shards/b/72.json', {
      schema_version: 1, prefix: '72', items: {
        '7203': {
          code: '7203', actual_status: 'available',
          actual: { fiscal_year: 2025, period_end: '2025-03-31', annual_dps: 100 },
          forecast_status: 'not_requested', forecast: null,
        },
      },
    }],
  ]);
  const reads = [];
  const response = await worker.fetch(
    new Request('https://example.com/api/v1/financial-data/7203'),
    dividendEnvironment(documents, true, reads),
  );

  assert.equal(response.status, 200);
  const data = await response.json();
  assert.equal(data.issuer_dividends.latest_actual.annual_dps, 100);
  assert.equal(data.issuer_dividends.source, 'IRBANK Connect');
  assert.ok(reads.includes('market/yfinance/v2/issuer_dividends/shards/b/72.json'));
  assert.ok(!reads.includes(indexKey));
});

test('security API exposes yfinance retrieval metadata and stored dividend history bounds', async () => {
  const base = 'market/yfinance/v2/securities/7203.T';
  const documents = new Map([
    ['market/yfinance/v2/catalog/current.json', { items: [{ code: '7203' }] }],
    [`${base}/profile.json`, {
      code: '7203', symbol: '7203.T', display_name: 'トヨタ自動車', source: 'Yahoo Finance via yfinance',
    }],
    [`${base}/current.json`, {
      code: '7203', symbol: '7203.T', source: 'Yahoo Finance via yfinance',
      price: { close: 3000, currency: 'JPY' },
      statistics: { trailingEps: 200, bookValue: 1000 },
      statistics_last_fetched_session_date: '2026-10-15',
      dividend: { dividend_status: 'has_dividend' },
      field_status: { dividend_history: 'available', statistics_refresh: 'failed' },
      action_history: {
        status: 'success', source: 'yfinance_history', yfinance_version: '1.7.0',
        coverage_start: '2025-01-01', coverage_end: '2025-12-31',
        full_history_coverage_end: '2025-12-31', dividend_event_count: 2, period: 'max',
      },
    }],
    [`${base}/sync.json`, {
      last_attempt_at: '2026-10-17T00:00:00Z', status: 'partial',
      failure_reason: 'Field fetch failed: price', yfinance_version: '1.7.0',
      info_last_fetched_session_date: '2026-10-15',
      info_last_fetched_at: '2026-10-15T12:00:00Z',
      info_last_attempt_session_date: '2026-10-17',
      info_last_attempt_at: '2026-10-17T12:00:00Z',
      info_last_attempt_status: 'failed',
      info_refresh_pending: true,
      info_refresh_failure: {
        failure_class: 'transient', exception_type: 'TimeoutError', reason: 'timed out',
      },
    }],
    [`${base}/dividends/2025.json`, { records: [
      { date: '2025-03-31', amount: 20 }, { date: '2025-09-30', amount: 30 },
    ] }],
    [`market/yfinance/v2/archive/events/7203.T/dividends/1990.json`, { records: [
      { date: '1990-06-29', amount: 1 },
    ] }],
  ]);
  const response = await worker.fetch(
    new Request('https://example.com/api/v1/securities/7203'), dividendEnvironment(documents),
  );
  assert.equal(response.status, 200);
  const data = await response.json();
  assert.equal(data.code, '7203');
  assert.deepEqual(data.valuation, {
    per: 15,
    pbr: 3,
    source: 'yfinance_reported_with_daily_calculation_fallback',
    price_date: null,
    statistics_last_fetched_session_date: '2026-10-15',
    formulas: {
      per: 'price.close / statistics.trailingEps',
      pbr: 'price.close / statistics.bookValue',
    },
  });
  assert.deepEqual(data.data_retrieval, {
    yfinance_ticker: '7203.T',
    source: 'Yahoo Finance via yfinance',
    currency: 'JPY',
    last_attempt_at: '2026-10-17T00:00:00Z',
    processing_status: 'partial',
    failure_reason: 'Field fetch failed: price',
    company_info: {
      status: 'failed',
      last_fetched_session_date: '2026-10-15',
      last_fetched_at: '2026-10-15T12:00:00Z',
      fetch_date_basis: 'company_info_fetch',
      last_attempt_session_date: '2026-10-17',
      last_attempt_at: '2026-10-17T12:00:00Z',
      last_attempt_status: 'failed',
      refresh_pending: true,
      failure_class: 'transient',
      failure_reason: 'timed out',
    },
    ex_dividend_calendar: {
      date: null,
      status: 'not_fetched',
      last_attempt_status: null,
      fetched_at: null,
      source: null,
    },
    dividend_history: {
      status: 'available',
      last_attempt_status: 'success',
      source: 'yfinance_history',
      yfinance_version: '1.7.0',
      stored_history_status: 'available',
      stored_history_start: '1990-06-29',
      stored_history_end: '2025-09-30',
      last_coverage_start: '2025-01-01',
      last_coverage_end: '2025-12-31',
      full_history_coverage_end: '2025-12-31',
      last_coverage_event_count: 2,
      period: 'max',
    },
  });
});

test('security API labels legacy company-information dates as sync-date fallbacks', async () => {
  const base = 'market/yfinance/v2/securities/7203.T';
  const documents = new Map([
    ['market/yfinance/v2/catalog/current.json', { items: [{ code: '7203' }] }],
    [`${base}/profile.json`, { code: '7203', display_name: 'トヨタ自動車' }],
    [`${base}/current.json`, {
      code: '7203',
      price: { close: 3000, date: '2026-10-16', currency: 'JPY' },
      statistics: { trailingEps: 200, bookValue: 1000 },
      field_status: { dividend_history: 'available' },
      dividend: {},
    }],
    [`${base}/sync.json`, {
      last_success_session_date: '2026-10-16',
      last_success_at: '2026-10-16T12:00:00Z',
    }],
  ]);
  const response = await worker.fetch(
    new Request('https://example.com/api/v1/securities/7203'), dividendEnvironment(documents),
  );
  assert.equal(response.status, 200);
  const data = await response.json();
  assert.equal(data.data_retrieval.company_info.status, 'stale');
  assert.equal(data.data_retrieval.company_info.last_fetched_session_date, '2026-10-16');
  assert.equal(data.data_retrieval.company_info.last_fetched_at, null);
  assert.equal(data.data_retrieval.company_info.fetch_date_basis, 'last_successful_sync_fallback');
});

test('financial API aggregates only published v2 yfinance calendar-year events', async () => {
  const code = '1306';
  const year = new Date().getUTCFullYear();
  const base = `market/yfinance/v2/securities/${code}.T`;
  const env = dividendEnvironment(new Map([
    [`${base}/profile.json`, { display_name: 'ETF' }],
    [`${base}/current.json`, { field_status: { dividend_history: 'available' } }],
    [`${base}/dividends/${year - 2}.json`, { records: [
      { date: `${year - 2}-03-01`, amount: 10 }, { date: `${year - 2}-09-01`, amount: 15 },
      { date: `${year - 2}-09-01`, amount: 15 }, { date: `${year - 2}-02-30`, amount: 99 },
    ] }],
    [`${base}/dividends/${year - 1}.json`, { records: [
      { date: `${year - 1}-03-01`, amount: 12 }, { date: `${year - 1}-09-01`, amount: 18 },
    ] }],
    [`${base}/dividends/${year}.json`, { records: [{ date: `${year}-03-01`, amount: 99 }] }],
  ]));
  const response = await worker.fetch(new Request(`https://example.com/api/v1/financial-data/${code}`), env);
  assert.equal(response.status, 200);
  const data = await response.json();
  assert.equal(data.dividends.history_basis, 'calendar_year_observed_distributions');
  assert.deepEqual(data.dividends.records, [
    { calendar_year: year - 2, calendar_year_distribution_per_share: 25, amount_basis: 'yfinance_split_adjusted_assumption',
      field_units: { calendar_year_distribution_per_share: 'jpy_per_share' } },
    { calendar_year: year - 1, calendar_year_distribution_per_share: 30, amount_basis: 'yfinance_split_adjusted_assumption',
      field_units: { calendar_year_distribution_per_share: 'jpy_per_share' } },
  ]);
});

test('financial API avoids rereading dividend partitions when current annual aggregates exist', async () => {
  const code = '8058';
  const base = `market/yfinance/v2/securities/${code}.T`;
  const env = dividendEnvironment(new Map([
    [`${base}/profile.json`, { display_name: '三菱商事' }],
    [`${base}/current.json`, {
      dividend: {
        calendar_year_metrics: {
          status: 'available', latest_completed_year: 2025,
          annual_records: [{ calendar_year: 2025, calendar_year_distribution_per_share: 50 }],
        },
      },
      field_status: { dividend_history: 'available' },
    }],
    [`${base}/dividends/2025.json`, { records: [{ date: '2025-03-31', amount: 999 }] }],
  ]));
  let dividendListCalls = 0;
  const list = env.MARKET_DATA.list;
  env.MARKET_DATA.list = async options => {
    if (options.prefix === `${base}/dividends/`) dividendListCalls += 1;
    return list(options);
  };

  const response = await worker.fetch(new Request(`https://example.com/api/v1/financial-data/${code}`), env);
  assert.equal(response.status, 200);
  const data = await response.json();
  assert.equal(data.dividends.records[0].calendar_year_distribution_per_share, 50);
  assert.equal(dividendListCalls, 0);
});

test('financial API labels dividend yield as percent only when the stored calculated yield corroborates it', async () => {
  const code = '8058';
  const base = `market/yfinance/v2/securities/${code}.T`;
  const current = {
    statistics: { dividendYield: 2.67 },
    statistics_field_units: { dividendYield: 'ratio' },
    dividend: {
      yield_rate: 2.6511,
      yield_source: 'calculated_from_yfinance_dividend_and_quote',
      calendar_year_metrics: { annual_records: [] },
    },
  };
  const documents = new Map([[`${base}/current.json`, current]]);
  const response = await worker.fetch(
    new Request(`https://example.com/api/v1/financial-data/${code}`), dividendEnvironment(documents),
  );
  assert.equal(response.status, 200);
  const data = await response.json();
  assert.equal(data.market.summary.dividendYield, 2.67);
  assert.equal(data.market.summary_field_units.dividendYield, 'percent');

  current.dividend = { calendar_year_metrics: { annual_records: [] } };
  const uncorroborated = await worker.fetch(
    new Request(`https://example.com/api/v1/financial-data/${code}`), dividendEnvironment(documents),
  );
  assert.equal(uncorroborated.status, 200);
  const uncorroboratedData = await uncorroborated.json();
  assert.equal(Object.hasOwn(uncorroboratedData.market.summary_field_units, 'dividendYield'), false);
});

test('financial API normalizes payout ratio only from an explicit ratio or percent unit', async () => {
  const base = 'market/yfinance/v2/securities/8058.T';
  const documents = new Map([[`${base}/current.json`, {
    statistics: { payoutRatio: 0.25 },
    statistics_field_units: { payoutRatio: 'ratio' },
    field_status: { dividend_history: 'empty' },
  }]]);
  const fetchPayload = async () => {
    const response = await worker.fetch(new Request('https://example.com/api/v1/financial-data/8058'),
      dividendEnvironment(documents));
    assert.equal(response.status, 200);
    return response.json();
  };

  let data = await fetchPayload();
  assert.equal(data.dividends.payout_ratio, 25);
  assert.equal(data.dividends.field_units.payout_ratio, 'percent');

  documents.set(`${base}/current.json`, {
    statistics: { payoutRatio: 25 },
    statistics_field_units: { payoutRatio: 'percent' },
    field_status: { dividend_history: 'empty' },
  });
  data = await fetchPayload();
  assert.equal(data.dividends.payout_ratio, 25);

  documents.set(`${base}/current.json`, {
    statistics: { payoutRatio: 25 },
    statistics_field_units: { payoutRatio: 'unknown' },
    field_status: { dividend_history: 'empty' },
  });
  data = await fetchPayload();
  assert.equal(data.dividends.payout_ratio, null);
});

test('financial API marks 8303 share basis unverified without changing stored dividend amounts', async () => {
  const base = 'market/yfinance/v2/securities/8303.T';
  const env = dividendEnvironment(new Map([
    [`${base}/profile.json`, { display_name: 'ＳＢＩ新生銀行' }],
    [`${base}/current.json`, { field_status: { dividend_history: 'available' } }],
    [`${base}/dividends/2025.json`, { records: [{ date: '2025-03-31', amount: 40000000 }] }],
    ['market/irbank/v1/securities/8303/overview.json', {
      security: { code: '8303', name: 'ＳＢＩ新生銀行' },
      dividends: { records: [{ fiscal_year: 2025, annual_dps: 40000000, annual_dps_adjusted: 40000000 }] },
      sources: { dividends: { source_label: '金融庁 EDINET / TDnet' } },
    }],
  ]));

  const response = await worker.fetch(new Request('https://example.com/api/v1/financial-data/8303'), env);
  assert.equal(response.status, 200);
  const data = await response.json();
  assert.equal(data.dividends.share_basis_status, 'unverified_incomplete_corporate_action_history');
  assert.equal(data.issuer_dividends.share_basis_status, 'unverified_incomplete_corporate_action_history');
  assert.equal(data.issuer_dividends.records[0].annual_dps, 40000000);
  assert.equal(data.issuer_dividends.records[0].annual_dps_adjusted, 40000000);

  const ordinaryBase = 'market/yfinance/v2/securities/7203.T';
  const ordinary = dividendEnvironment(new Map([[`${ordinaryBase}/profile.json`, { display_name: 'トヨタ自動車' }]]));
  const ordinaryResponse = await worker.fetch(new Request('https://example.com/api/v1/financial-data/7203'), ordinary);
  const ordinaryData = await ordinaryResponse.json();
  assert.equal(Object.hasOwn(ordinaryData.dividends, 'share_basis_status'), false);
  assert.equal(Object.hasOwn(ordinaryData.issuer_dividends, 'share_basis_status'), false);
});

test('financial API returns calculated DOE and net D/E from the stored annual row', async () => {
  const code = '7203';
  const base = `market/yfinance/v2/securities/${code}.T`;
  const annual = {
    fiscal_year: '2026/03',
    period_end: '2026-03-31',
    calculated_doe: 3.25,
    net_de_ratio: -0.22,
    field_status: { calculated_doe: 'available', net_de_ratio: 'available' },
  };
  const env = dividendEnvironment(new Map([
    [`${base}/profile.json`, { display_name: 'トヨタ自動車' }],
    [`${base}/current.json`, { field_status: { dividend_history: 'empty' } }],
    [`${base}/financials/2026-03-31.json`, { year: '2026', annual }],
  ]));

  const response = await worker.fetch(new Request(`https://example.com/api/v1/financial-data/${code}`), env);
  assert.equal(response.status, 200);
  const data = await response.json();
  assert.deepEqual(data.financials.annual, [{
    ...annual,
    field_status: { ...annual.field_status, equity_ratio_computed: 'empty' },
    field_units: { calculated_doe: 'percent', net_de_ratio: 'times' },
  }]);
});

test('financial API exposes retained seed values separately from newer yfinance values', async () => {
  const code = '7203';
  const base = `market/yfinance/v2/securities/${code}.T`;
  const annual = {
    fiscal_year: '2025/03',
    period_end: '2025-03-31',
    sales: 120,
    operating_margin: 12,
    equity_ratio_computed: 24,
    field_status: { sales: 'available' },
    source_values: {
      seed: { source: 'IRBANK', source_key: 'market/irbank/v1/securities/7203/overview.json',
        values: { sales: 100, operating_margin: 12 },
        field_units: { sales: 'million_jpy', operating_margin: 'percent', equity_ratio_computed: 'percent' } },
      yfinance: { source: 'Yahoo Finance via yfinance', values: { sales: 120 }, field_status: { sales: 'available' } },
      calculated: { values: { equity_ratio_computed: { value: 24, input_sources: { equity: 'seed', total_assets: 'yfinance' } } } },
    },
  };
  const env = dividendEnvironment(new Map([
    [`${base}/profile.json`, { display_name: 'トヨタ自動車' }],
    [`${base}/current.json`, { field_status: { dividend_history: 'empty' } }],
    [`${base}/financials/2025-03-31.json`, { year: '2025', annual }],
  ]));

  const response = await worker.fetch(new Request(`https://example.com/api/v1/financial-data/${code}`), env);
  assert.equal(response.status, 200);
  const data = await response.json();
  assert.equal(data.financials.annual[0].sales, 100_000_000);
  assert.equal(data.financials.annual[0].field_units.sales, 'jpy');
  assert.equal(data.financials.annual[0].operating_margin, 12);
  assert.equal(data.financials.annual[0].field_units.operating_margin, 'percent');
  assert.equal(Object.hasOwn(data.financials.annual[0], 'equity_ratio_computed'), false);
  assert.deepEqual(data.financials.annual[0].source_values.calculated.values, {});
  assert.equal(data.financials.seed_annual[0].values.sales, 100);
  assert.deepEqual(data.financials.annual[0].source_values.yfinance.unverified_fields, ['sales']);
  assert.equal(Object.hasOwn(data.financials.seed_annual[0].values, 'equity_ratio_computed'), false);
  assert.equal(data.financials.seed_annual[0].source, 'IRBANK');
});

test('financial API quarantines confirmed cross-issuer annual seeds while retaining independent values', async () => {
  const makeRow = (code, period, sales, assets, yahoo = null) => ({
    fiscal_year: period.slice(0, 7).replace('-', '/'), period_end: period,
    sales: sales * 1_000_000, total_assets: assets * 1_000_000, equity_ratio_computed: 31.2,
    field_status: { sales: 'legacy_migrated', total_assets: 'legacy_migrated', equity_ratio_computed: 'legacy_migrated' },
    source_values: {
      seed: { source: 'IRBANK', source_key: `market/irbank/v1/securities/${code}/overview.json`,
        values: { sales, total_assets: assets, equity_ratio_computed: 31.2 },
        field_units: { sales: 'million_jpy', total_assets: 'million_jpy', equity_ratio_computed: 'percent' } },
      ...(yahoo ? { yfinance: { values: yahoo,
        field_units: { sales: 'yfinance_native', stockholders_equity: 'yfinance_native', total_assets: 'yfinance_native' },
        field_reported_currencies: { sales: 'JPY', stockholders_equity: 'JPY', total_assets: 'JPY' } } } : {}),
    },
  });
  const rows = [
    ['1301', '2017-03-31', makeRow('1301', '2017-03-31', 6639, 5594)],
    ['1301', '2026-03-31', makeRow('1301', '2026-03-31', 8283, 6973,
      { sales: 334612000000, stockholders_equity: 77335000000, total_assets: 214119000000 })],
    ['2708', '2025-10-31', makeRow('2708', '2025-10-31', 245109, 156015)],
    ['2708', '2014-03-31', makeRow('2708', '2014-03-31', 62268, 27566)],
    ['1380', '2026-03-31', makeRow('1380', '2026-03-31', 8283, 6973)],
    ['1301', '2025-03-31', makeRow('1301', '2025-03-31', 302681, 182125)],
  ];
  const docs = new Map(rows.map(([code, period, annual]) =>
    [`market/yfinance/v2/securities/${code}.T/financials/${period}.json`, { annual }]));
  const env = dividendEnvironment(docs);
  const read = async code => (await (await worker.fetch(
    new Request(`https://example.com/api/v1/financial-data/${code}`), env)).json()).financials;
  const kyokuyo = await read('1301');
  assert.equal(kyokuyo.annual[0].period_end, '2017-03-31');
  assert.equal(Object.hasOwn(kyokuyo.annual[0], 'sales'), false);
  assert.equal(Object.hasOwn(kyokuyo.annual[0], 'equity_ratio_computed'), false);
  assert.deepEqual(kyokuyo.annual[0].field_status_reason.sales, ['cross_issuer_seed_quarantined']);
  const current = kyokuyo.annual.find(row => row.period_end === '2026-03-31');
  assert.equal(current.sales, 334612000000);
  assert.equal(current.total_assets, 214119000000);
  assert.ok(Math.abs(current.equity_ratio_computed - 77335000000 / 214119000000 * 100) < 1e-10);
  assert.equal(kyokuyo.seed_annual.some(row => row.period_end === '2017-03-31' || row.period_end === '2026-03-31'), false);
  assert.equal(kyokuyo.seed_annual.some(row => row.period_end === '2025-03-31'), true);
  const kuze = await read('2708');
  assert.equal(kuze.annual.length, 2);
  assert.ok(kuze.annual.every(row => !Object.hasOwn(row, 'sales')));
  assert.equal(kuze.seed_annual.length, 0);
  const akigawa = await read('1380');
  assert.equal(akigawa.annual[0].sales, 8283000000);
  assert.equal(akigawa.seed_annual.length, 1);
});

test('raw IRBANK overview quarantines only confirmed foreign annual rows', async () => {
  const overview = { financials: { annual: [
    { fiscal_year: '2026/03', sales: 8283, total_assets: 6973, eps: 5.23 },
    { fiscal_year: '2025/03', sales: 302681, total_assets: 182125 },
  ], quarterly: [{ sales: 123 }] }, valuation: { history: [{ eps: 575.97 }] } };
  const env = dividendEnvironment(new Map([['market/irbank/v1/securities/1301/overview.json', overview]]));
  const result = await worker.fetch(new Request('https://example.com/api/v1/irbank/securities/1301'), env);
  assert.equal(result.status, 200);
  const body = await result.json();
  assert.deepEqual(body.financials.annual[0], { fiscal_year: '2026/03', validation_status: 'unsupported', reason: 'cross_issuer_seed_quarantined' });
  assert.equal(body.financials.annual[1].sales, 302681);
  assert.equal(body.financials.quarterly[0].sales, 123);
  assert.equal(body.valuation.history[0].eps, 575.97);
  assert.equal(overview.financials.annual[0].sales, 8283);
});

test('raw IRBANK overview quarantines known mixed 2014 row but accepts corrected data', async () => {
  const key = 'market/irbank/v1/securities/2708/overview.json';
  const docs = new Map([[key, {financials: {annual: [
    {fiscal_year:'2014/03', sales:62268, total_assets:27566, operating_cf:5179},
  ]}}]]);
  const env = dividendEnvironment(docs);
  const read = async () => (await (await worker.fetch(new Request('https://example.com/api/v1/irbank/securities/2708'), env)).json()).financials.annual[0];
  assert.equal((await read()).reason, 'cross_issuer_seed_quarantined');
  docs.set(key, {financials: {annual: [{fiscal_year:'2014/03', sales:62268445000, total_assets:19002585000, operating_cf:-1082741000}]}});
  assert.equal((await read()).total_assets, 19002585000);
});

test('financial API retains J-Quants repair provenance and independent Yahoo values', async () => {
  const annual = { fiscal_year: '2026/03', period_end: '2026-03-31',
    field_status: { sales: 'available', total_assets: 'available', equity_ratio_computed: 'available' },
    source_values: {
      seed: { source: 'J-Quants API /fins/summary', source_key: 'jquants:/v2/fins/summary',
        values: { sales: 334612000000, total_assets: 214128000000, equity_ratio_computed: 36.1 },
        field_units: { sales: 'jpy', total_assets: 'jpy', equity_ratio_computed: 'percent' } },
      yfinance: { values: { sales: 334612000000, total_assets: 214119000000, stockholders_equity: 77335000000 },
        field_units: { sales: 'yfinance_native', total_assets: 'yfinance_native', stockholders_equity: 'yfinance_native' },
        field_reported_currencies: { sales: 'JPY', total_assets: 'JPY', stockholders_equity: 'JPY' } },
    } };
  const env = dividendEnvironment(new Map([['market/yfinance/v2/securities/1301.T/financials/2026.json', { annual }]]));
  const body = await (await worker.fetch(new Request('https://example.com/api/v1/financial-data/1301'), env)).json();
  assert.equal(body.financials.annual[0].total_assets, 214119000000);
  assert.equal(body.financials.seed_annual[0].source, 'J-Quants API /fins/summary');
  assert.equal(body.financials.seed_annual[0].values.total_assets, 214128000000);
  assert.equal(body.financials.seed_annual[0].field_units.total_assets, 'jpy');
});

test('financial API keeps same-source ratios and marks Yahoo statement amounts as native units', async () => {
  const code = '9984';
  const base = `market/yfinance/v2/securities/${code}.T`;
  const annual = {
    fiscal_year: '2025/03',
    period_end: '2025-03-31',
    equity_ratio_computed: 20,
    field_status: { equity_ratio_computed: 'available' },
    source_values: {
      calculated: {
        values: {
          equity_ratio_computed: {
            value: 20,
            formula: 'equity / total_assets * 100',
            input_sources: { equity: 'yfinance', total_assets: 'yfinance' },
            input_units: { equity: 'yfinance_native', total_assets: 'yfinance_native' },
            unit: 'percent',
          },
          operating_margin: {
            value: 10, formula: 'operating_profit / sales * 100',
            input_sources: { operating_profit: 'yfinance', sales: 'yfinance' },
            input_units: { operating_profit: 'yfinance_native', sales: 'yfinance_native' }, unit: 'percent',
          },
          fcf_margin: {
            value: 5, formula: '(operating_cf + investing_cf) / sales * 100',
            input_sources: { operating_cf: 'yfinance', investing_cf: 'yfinance', sales: 'yfinance' },
            input_units: { operating_cf: 'yfinance_native', investing_cf: 'yfinance_native', sales: 'yfinance_native' }, unit: 'percent',
          },
        },
      },
      yfinance: {
        values: {
          stockholders_equity: 40, total_assets: 200, sales: 100,
          operating_profit: 10, operating_cf: 8, investing_cf: -3,
        },
        field_units: {
          stockholders_equity: 'yfinance_native', total_assets: 'yfinance_native', sales: 'yfinance_native',
          operating_profit: 'yfinance_native', operating_cf: 'yfinance_native', investing_cf: 'yfinance_native',
        },
        field_reported_currencies: {
          stockholders_equity: 'JPY', total_assets: 'JPY', sales: 'JPY',
          operating_profit: 'JPY', operating_cf: 'JPY', investing_cf: 'JPY',
        },
      },
    },
  };
  const env = dividendEnvironment(new Map([
    [`${base}/profile.json`, { display_name: 'ソフトバンクグループ' }],
    [`${base}/current.json`, { field_status: { dividend_history: 'empty' } }],
    [`${base}/financials/2025-03-31.json`, { year: '2025', annual }],
  ]));

  const response = await worker.fetch(new Request(`https://example.com/api/v1/financial-data/${code}`), env);
  assert.equal(response.status, 200);
  const data = await response.json();
  assert.equal(data.financial_contract_version, 6);
  const row = data.financials.annual[0];
  assert.equal(row.source_values.calculated.values.equity_ratio_computed.value, 20);
  assert.equal(row.field_units.stockholders_equity, 'yfinance_native');
  assert.equal(row.source_values.yfinance.field_reported_currencies.stockholders_equity, 'JPY');
  assert.equal(row.operating_margin, 10);
  assert.equal(row.field_units.operating_margin, 'percent');
  assert.equal(row.fcf_margin, 5);
});

test('financial API omits legacy Yahoo money fields whose statement currency was never verified', async () => {
  const annual = {
    period_end: '2025-03-31',
    sales: 7_070_000_000,
    equity_ratio_computed: 35.15,
    field_status: { sales: 'available', equity_ratio_computed: 'available' },
    field_units: { sales: 'jpy', equity_ratio_computed: 'percent' },
    source_values: {
      yfinance: {
        values: { sales: 7_070_000_000 },
        field_units: { sales: 'jpy' },
        field_currencies: { sales: 'JPY' },
      },
      calculated: {
        values: { equity_ratio_computed: {
          value: 35.15, unit: 'percent',
          input_sources: { equity: 'yfinance', total_assets: 'yfinance' },
          input_units: { equity: 'jpy', total_assets: 'jpy' },
        } },
      },
    },
  };
  const env = dividendEnvironment(new Map([
    ['market/yfinance/v2/securities/1301.T/financials/2025-03-31.json', { annual }],
  ]));
  const response = await worker.fetch(new Request('https://example.com/api/v1/financial-data/1301'), env);
  const body = await response.json();
  const row = body.financials.annual[0];
  assert.equal(Object.hasOwn(row, 'sales'), false);
  assert.equal(row.equity_ratio_computed, 35.15);
  assert.equal(row.field_status.sales, 'unsupported');
  assert.deepEqual(row.field_status_reason.sales, ['financial_currency_unverified']);
  assert.deepEqual(row.source_values.yfinance.field_units, {});
  assert.equal(Object.hasOwn(row.source_values.yfinance, 'field_currencies'), false);
  assert.deepEqual(row.source_values.yfinance.unverified_fields, ['sales']);
  assert.equal(row.source_values.calculated.values.equity_ratio_computed.value, 35.15);
});

test('financial API retains seed values and same-source ratio when Yahoo native scale differs greatly', async () => {
  const annual = {
    fiscal_year: '2022/12',
    period_end: '2022-12-31',
    sales: 363_593_000_000,
    total_assets: 416_206_000_000,
    equity_ratio_computed: 25.8795,
    field_status: { sales: 'legacy_migrated', total_assets: 'legacy_migrated', equity_ratio_computed: 'available' },
    source_values: {
      seed: {
        source: 'IRBANK',
        values: { sales: 363593, total_assets: 416206, equity_ratio_computed: 25.8795 },
        field_units: { sales: 'million_jpy', total_assets: 'million_jpy', equity_ratio_computed: 'percent' },
      },
      yfinance: {
        values: { stockholders_equity: 811_640_000, total_assets: 3_136_213_000 },
        field_units: { stockholders_equity: 'yfinance_native', total_assets: 'yfinance_native' },
        field_reported_currencies: { stockholders_equity: 'JPY', total_assets: 'JPY' },
      },
      calculated: {
        values: { equity_ratio_computed: {
          value: 25.8795, unit: 'percent',
          input_sources: { equity: 'yfinance', total_assets: 'yfinance' },
          input_units: { equity: 'yfinance_native', total_assets: 'yfinance_native' },
        } },
      },
    },
  };
  const env = dividendEnvironment(new Map([
    ['market/yfinance/v2/securities/6269.T/financials/2022-12-31.json', { annual }],
  ]));
  const response = await worker.fetch(new Request('https://example.com/api/v1/financial-data/6269'), env);
  const body = await response.json();
  const row = body.financials.annual[0];
  assert.equal(row.equity_ratio_computed, 25.8795);
  assert.equal(row.total_assets, 3_136_213_000);
  assert.equal(row.field_units.total_assets, 'yfinance_native');
  assert.equal(body.financials.seed_annual[0].values.total_assets, 416206);
  assert.equal(body.financials.seed_annual[0].validation_status, 'available');
});

test('financial API prefers a same-period Yahoo calculation over a retained seed ratio', async () => {
  const annual = {
    fiscal_year: '2023/03', period_end: '2023-03-31',
    equity_ratio_computed: 35.15439778,
    field_status: { equity_ratio_computed: 'legacy_migrated' },
    source_values: {
      seed: { source: 'IRBANK', values: { total_assets: 6073, equity_ratio_computed: 35.15439778 },
        field_units: { total_assets: 'million_jpy', equity_ratio_computed: 'percent' } },
      yfinance: {
        values: { stockholders_equity: 47_541_000_000, total_assets: 146_294_000_000 },
        field_units: { stockholders_equity: 'yfinance_native', total_assets: 'yfinance_native' },
        field_reported_currencies: { stockholders_equity: 'JPY', total_assets: 'JPY' },
      },
      calculated: { values: { equity_ratio_computed: {
        value: 32.4953, unit: 'percent',
        input_sources: { equity: 'yfinance', total_assets: 'yfinance' },
        input_units: { equity: 'yfinance_native', total_assets: 'yfinance_native' },
      } } },
    },
  };
  const env = dividendEnvironment(new Map([
    ['market/yfinance/v2/securities/1301.T/financials/2023-03-31.json', { annual }],
  ]));
  const response = await worker.fetch(new Request('https://example.com/api/v1/financial-data/1301'), env);
  const body = await response.json();
  const row = body.financials.annual[0];
  assert.equal(row.equity_ratio_computed, 32.4953);
  assert.equal(row.field_units.equity_ratio_computed, 'percent');
  assert.equal(body.financials.seed_annual[0].values.equity_ratio_computed, 35.15439778);
});

test('financial API does not replace the calculated ratio with a retained seed ratio', async () => {
  const annual = { period_end: '2026-03-31', equity_ratio_computed: 60,
    field_status: { equity_ratio_computed: 'available' },
    source_values: {
      seed: { source: 'IRBANK', values: { equity_ratio_computed: 24 }, field_units: { equity_ratio_computed: 'percent' } },
      yfinance: { values: { stockholders_equity: 60, total_assets: 100 },
        field_units: { stockholders_equity: 'yfinance_native', total_assets: 'yfinance_native' },
        field_reported_currencies: { stockholders_equity: 'JPY', total_assets: 'JPY' } },
      calculated: { values: { equity_ratio_computed: {
        value: 60, unit: 'percent', input_sources: { equity: 'yfinance', total_assets: 'yfinance' },
        input_units: { equity: 'jpy', total_assets: 'jpy' },
      } } },
    } };
  const env = dividendEnvironment(new Map([['market/yfinance/v2/securities/4502.T/financials/2026.json', { annual }]]));
  const response = await worker.fetch(new Request('https://example.com/api/v1/financial-data/4502'), env);
  const body = await response.json();
  assert.equal(body.financials.annual[0].equity_ratio_computed, 60);
  assert.equal(body.financials.seed_annual[0].values.equity_ratio_computed, 24);
});

test('equity ratio does not mix conflicting reported currencies', async () => {
  const annual = { period_end: '2026-03-31', source_values: {
    seed: { source: 'IRBANK', values: { equity_ratio_computed: 24 }, field_units: { equity_ratio_computed: 'percent' } },
    yfinance: { values: { stockholders_equity: 60, total_assets: 100 },
      field_units: { stockholders_equity: 'yfinance_native', total_assets: 'yfinance_native' },
      field_reported_currencies: { stockholders_equity: 'USD', total_assets: 'JPY' } },
    calculated: { values: { equity_ratio_computed: {
      value: 60, unit: 'percent', input_sources: { equity: 'yfinance', total_assets: 'yfinance' },
      input_units: { equity: 'yfinance_native', total_assets: 'yfinance_native' },
    } } },
  } };
  const env = dividendEnvironment(new Map([['market/yfinance/v2/securities/4502.T/financials/2026.json', { annual }]]));
  const read = async () => (await (await worker.fetch(new Request('https://example.com/api/v1/financial-data/4502'), env)).json()).financials.annual[0];
  const conflicting = await read();
  assert.equal(conflicting.equity_ratio_computed, 24);
  assert.equal(Object.hasOwn(conflicting.source_values.calculated.values, 'equity_ratio_computed'), false);
  delete annual.source_values.seed.values.equity_ratio_computed;
  assert.equal(Object.hasOwn(await read(), 'equity_ratio_computed'), false);
  annual.source_values.yfinance.field_reported_currencies.stockholders_equity = 'JPY';
  assert.equal((await read()).equity_ratio_computed, 60);
});

test('financial API uses IRBANK canonical fiscal DPS instead of yfinance issuer rows', async () => {
  const records = ['2026/03', '2026/12'].map((period, index) => ({ fiscal_year: 2026, period, annual_dps: 10 + index }));
  const documents = new Map([
    ['market/yfinance/v2/securities/4502.T/profile.json', { display_name: 'アステラス製薬' }],
    ['market/yfinance/v2/securities/4502.T/issuer_dividends/2026.json', { records }],
    ['market/irbank/v1/securities/4502/overview.json', {
      security: { code: '4502', name: 'アステラス製薬' },
      dividends: { records: [{ fiscal_year: 2026, annual_dps: 74, annual_dps_adjusted: 74 }] },
      sources: { dividends: { source_label: '金融庁 EDINET / TDnet' } },
    }],
  ]);
  const env = dividendEnvironment(documents);
  const response = await worker.fetch(new Request('https://example.com/api/v1/financial-data/4502'), env);
  const body = await response.json();
  assert.deepEqual(body.issuer_dividends.records.map(r => r.fiscal_period), ['2026']);
  assert.equal(body.issuer_dividends.records[0].annual_dps_adjusted, 74);
  assert.equal(body.issuer_dividends.records[0].source, 'IRBANK');
});

test('issuer corrections replace only the matching fiscal period and retain ambiguous years', async () => {
  const index = { items: { '7203': { actual_status: 'available', actual: {
    fiscal_year: 2026, period_end: '2026-12-31', annual_dps: 25,
  } } } };
  const documents = new Map([
    ['market/irbank/v1/securities/7203/overview.json', { dividends: { records: [
      { fiscal_year: 2026, period: '2026/03', annual_dps: 10 },
      { fiscal_year: 2026, period: '2026/12', annual_dps: 20 },
    ] } }],
    ['market/yfinance/v2/issuer_dividends/current.json', index],
  ]);
  const read = async () => (await (await worker.fetch(new Request('https://example.com/api/v1/financial-data/7203'),
    dividendEnvironment(documents))).json()).issuer_dividends.records;
  assert.deepEqual((await read()).map(r => [r.fiscal_period, r.annual_dps]), [['2026-03', 10], ['2026-12-31', 25]]);
  index.items['7203'].actual = { fiscal_year: 2026, period: '2026/03', annual_dps: 12 };
  assert.deepEqual((await read()).map(r => [r.fiscal_period, r.annual_dps]), [['2026-03', 12], ['2026-12', 20]]);
  index.items['7203'].actual = { fiscal_year: 2026, annual_dps: 99 };
  assert.deepEqual((await read()).map(r => r.annual_dps), [10, 20]);
});

test('financial API preserves mixed-unit null guards and raw provenance without changing other merges', async () => {
  const base = 'market/yfinance/v2/securities/7203.T';
  const fields = ['sales', 'operating_profit', 'operating_cf', 'investing_cf'];
  const annual = {
    fiscal_year: '2026/03', period_end: '2026-03-31',
    ...Object.fromEntries(fields.map(field => [field, null])),
    field_status: Object.fromEntries(fields.map(field => [field, 'unsupported'])),
    field_status_reason: Object.fromEntries(fields.map(field => [field, ['mixed_source_units_not_comparable']])),
    source_values: {
      seed: { source: 'IRBANK', values: { sales: 100, operating_profit: 20, operating_cf: 30, investing_cf: -10, total_assets: 500 } },
      yfinance: {
        values: { total_assets: 500000000 },
        field_units: { total_assets: 'yfinance_native' },
        field_reported_currencies: { total_assets: 'JPY' },
      },
    },
  };
  const documents = new Map([[`${base}/financials/2026-03-31.json`, { annual }]]);
  const fetchData = async () => {
    const response = await worker.fetch(new Request('https://example.com/api/v1/financial-data/7203'),
      dividendEnvironment(documents));
    assert.equal(response.status, 200);
    return response.json();
  };
  const data = await fetchData();
  const row = data.financials.annual[0];
  for (const field of fields) assert.equal(row[field], null, field);
  for (const [field, value] of Object.entries(annual.source_values.seed.values)) {
    assert.equal(data.financials.seed_annual[0].values[field], value);
  }
  assert.deepEqual(row.source_values.seed.values, annual.source_values.seed.values);
  assert.deepEqual(row.source_values.yfinance.values, annual.source_values.yfinance.values);
  assert.deepEqual(row.source_values.seed.field_units, {
    sales: 'million_jpy', operating_profit: 'million_jpy', operating_cf: 'million_jpy',
    investing_cf: 'million_jpy', total_assets: 'million_jpy',
  });
  assert.equal(row.total_assets, 500000000);
  // With one fresh cash-flow input, the seed-only other side must stay null.
  annual.operating_cf = 30000000;
  annual.source_values.yfinance.values.operating_cf = 30000000;
  annual.source_values.yfinance.field_units.operating_cf = 'yfinance_native';
  annual.source_values.yfinance.field_reported_currencies.operating_cf = 'JPY';
  delete annual.field_status_reason.operating_cf;
  annual.field_status_reason.sales = ['different_reason'];
  const mixed = (await fetchData()).financials.annual[0];
  assert.equal(mixed.operating_cf, 30000000);
  assert.equal(mixed.investing_cf, null);
  assert.equal(mixed.sales, null);
  assert.equal(mixed.field_status.sales, 'unsupported');
});

test('financial API keeps observed event history separate from legacy fiscal-year DPS', async () => {
  const code = '1306';
  const currentYear = Number(new Intl.DateTimeFormat('en', { timeZone: 'Asia/Tokyo', year: 'numeric' }).format(new Date()));
  const base = `market/yfinance/v2/securities/${code}.T`;
  const documents = new Map([
    [`market/irbank/v1/securities/${code}/overview.json`, {
      security: { code, name: 'ETF' }, financials: { annual: [] },
      dividends: { records: [{ fiscal_year: '2025/03', annual_dps_adjusted: 999 }] }, valuation: { history: [] }
    }],
    [`${base}/profile.json`, { display_name: 'ETF' }],
    [`${base}/current.json`, { field_status: { dividend_history: 'available' } }],
    [`${base}/dividends/${currentYear - 1}.json`, { records: [
        { date: `${currentYear - 1}-03-01`, amount: 10 },
        { date: `${currentYear - 1}-03-01`, amount: 10 },
        { date: `${currentYear - 1}-02-30`, amount: 100 },
        { date: `${currentYear - 1}-09-01`, amount: 15 }
      ] }],
  ]);
  const response = await worker.fetch(new Request(`https://example.com/api/v1/financial-data/${code}`), dividendEnvironment(documents));
  assert.equal(response.status, 200);
  const data = await response.json();
  assert.equal(data.security.name, 'ETF');
  assert.deepEqual(data.dividends.records, [{
    calendar_year: currentYear - 1,
    calendar_year_distribution_per_share: 25,
    amount_basis: 'yfinance_split_adjusted_assumption',
    field_units: { calendar_year_distribution_per_share: 'jpy_per_share' },
  }]);
});

test('financial API does not substitute legacy DPS when v2 event history is unavailable', async () => {
  const code = '1306';
  const base = `market/yfinance/v2/securities/${code}.T`;
  const env = dividendEnvironment(new Map([
    [`${base}/profile.json`, { display_name: 'ETF', symbol: `${code}.T` }],
    [`${base}/current.json`, { field_status: { dividend_history: 'not_fetched' } }],
    [`market/irbank/v1/securities/${code}/overview.json`, {
    security: { code, name: 'ETF' },
    financials: { annual: [] },
    dividends: { records: [{ fiscal_year: '2025/03', annual_dps_adjusted: 999 }] },
    valuation: { history: [] },
    }],
  ]));
  const response = await worker.fetch(new Request(`https://example.com/api/v1/financial-data/${code}`), env);
  assert.equal(response.status, 200);
  const data = await response.json();
  assert.deepEqual(data.dividends.records, []);
  assert.equal(data.dividends.field_status.dividend_history, 'not_fetched');
});

test('dividend-months returns sorted recent observed months from R2 for each requested symbol', async () => {
  const today = japanToday();
  const recentDate = dateOffset(today, -20);
  let oldDate = dateOffset(today, -400);
  while ([today.slice(5, 7), recentDate.slice(5, 7)].includes(oldDate.slice(5, 7))) {
    oldDate = dateOffset(oldDate, -20);
  }
  const base = 'market/yfinance/v2/securities/7203.T';
  const record = (date, amount) => ({ date, amount });
  const env = dividendEnvironment(new Map([
    [`${base}/current.json`, { field_status: { dividend_history: 'available' } }],
    [`${base}/dividends/${today.slice(0, 4)}.json`, { records: [
      record(today, 25), record(dateOffset(today, 1), 50), record(today, 0), record('invalid', 100),
    ] }],
    [`${base}/dividends/${Number(today.slice(0, 4)) - 1}.json`, { records: [
      record(recentDate, 30), record(oldDate, 40),
    ] }],
  ]));
  const response = await worker.fetch(new Request('https://example.com/api/v1/dividend-months?symbols=7203.T,7203.T,MSFT'), env);
  assert.equal(response.status, 200);
  const data = await response.json();
  assert.equal(data.as_of, today);
  assert.equal(data.lookback_days, 365);
  assert.deepEqual(data.items, [
    {
      symbol: '7203.T',
      history_status: 'available',
      amount_basis: 'yfinance_split_adjusted_assumption',
      months: [...new Set([Number(today.slice(5, 7)), Number(recentDate.slice(5, 7))])].sort((a, b) => a - b),
      events: [
        { date: recentDate, amount: 30, amount_basis: 'yfinance_split_adjusted_assumption', row_repaired_by_yfinance: false, final_rights_date: null, record_date: null },
        { date: today, amount: 25, amount_basis: 'yfinance_split_adjusted_assumption', row_repaired_by_yfinance: false, final_rights_date: null, record_date: null },
      ],
      upcoming_event: null,
    },
    { symbol: 'MSFT', history_status: 'not_fetched', months: [], events: [], upcoming_event: null }
  ]);
});

test('yfinance v2 routes preserve exact five-digit class-share security codes', async () => {
  const code = '92025';
  const symbol = `${code}.T`;
  const base = `market/yfinance/v2/securities/${symbol}`;
  const generation = '2026-10-19';
  const dividendEvents = [
    { date: '2026-03-30', amount: 52.73 },
    { date: '2026-09-29', amount: 87.5 },
  ];
  const splitEvent = { date: '2025-12-01', ratio: 1.5 };
  const paddedCodeBase = 'market/yfinance/v2/securities/7203.T';
  const catalog = {
    generation,
    market_data_date: generation,
    items: [
      {
        code, name: 'ANA HOLDINGS INC NPV PRF STOCK', price: 4720,
        quote_date: generation, annual_dividend_per_share: 140.23,
        yield_rate: 2.971, dividend_status: 'has_dividend',
      },
      { code: '7203', name: 'トヨタ自動車', price: 3000, quote_date: generation },
    ],
  };
  const current = {
    code, symbol, updated_at: `${generation}T12:00:00Z`, source: 'Yahoo Finance via yfinance',
    price: { close: 4720, date: generation, currency: 'JPY' },
    dividend: {
      dividend_status: 'has_dividend',
      yield_basis: 'next_fiscal_year_forecast',
      next_fiscal_year_forecast_per_share: 140.23,
      trailing_12_month_per_share: 140.23,
      event_amount_basis: 'yfinance_split_adjusted_assumption',
    },
    field_status: { dividend_history: 'available', split_history: 'available' },
    action_history: { status: 'success', source: 'yfinance_history', yfinance_version: '1.7.0' },
  };
  const documents = new Map([
    ['market/yfinance/v2/catalog/current.json', catalog],
    [`market/yfinance/v2/catalog/generations/${generation}.json`, catalog],
    [`${base}/profile.json`, { code, symbol, display_name: 'ANA社債型種類株式' }],
    [`${base}/current.json`, current],
    [`${base}/sync.json`, { status: 'ok' }],
    [`${base}/dividends/2026.json`, { records: dividendEvents }],
    [`${base}/splits/2025.json`, { records: [splitEvent] }],
    [`${base}/prices/monthly/2025.json`, {
      points: [{ month: '2025-12', date: '2025-12-30', close: 5000 }],
    }],
    [`${paddedCodeBase}/profile.json`, { code: '7203', symbol: '7203.T', display_name: 'トヨタ自動車' }],
    [`${paddedCodeBase}/current.json`, {
      code: '7203', symbol: '7203.T', price: { close: 3000, currency: 'JPY' },
    }],
  ]);
  const env = dividendEnvironment(documents);

  const securityResponse = await worker.fetch(new Request(`https://example.com/api/v1/securities/${code}`), env);
  assert.equal(securityResponse.status, 200);
  assert.equal((await securityResponse.json()).code, code);

  const financialResponse = await worker.fetch(new Request(`https://example.com/api/v1/financial-data/${code}`), env);
  assert.equal(financialResponse.status, 200);
  assert.equal((await financialResponse.json()).security.code, code);

  const dividendsResponse = await worker.fetch(new Request(
    `https://example.com/api/v1/dividend-months?symbols=${symbol}&event_year=2026`,
  ), env);
  assert.deepEqual((await dividendsResponse.json()).items[0].events.map(({ date, amount }) => ({ date, amount })), dividendEvents);

  const splitResponse = await worker.fetch(new Request(
    `https://example.com/api/v1/share-split-history?code=${code}&from=2025-01-01&to=2025-12-31`,
  ), env);
  assert.deepEqual((await splitResponse.json()).events, [splitEvent]);

  const monthlyPriceResponse = await worker.fetch(new Request(
    `https://example.com/api/v1/monthly-price-history?codes=${code}&months=2025-12`,
  ), env);
  assert.deepEqual((await monthlyPriceResponse.json()).items[0].prices, [
    { month: '2025-12', date: '2025-12-30', close: 5000 },
  ]);

  const liveQuoteResponse = await worker.fetch(new Request(
    `https://example.com/api/v1/live-quotes?codes=${code}`,
  ), env);
  const liveQuote = (await liveQuoteResponse.json()).items[0];
  assert.equal(liveQuote.code, code);
  assert.equal(liveQuote.price, 4720);
  assert.equal(liveQuote.annual_dividend_per_share, null);
  assert.equal(liveQuote.yield_rate, null);

  const paddedCodeResponse = await worker.fetch(new Request('https://example.com/api/v1/securities/72030'), env);
  assert.equal(paddedCodeResponse.status, 200);
  assert.equal((await paddedCodeResponse.json()).code, '7203');
});

test('dividend-months returns the history-based next ex-date before the legacy statistics fallback', async () => {
  const today = japanToday();
  const estimatedDate = dateOffset(today, 120);
  const statisticsDate = dateOffset(today, 60);
  const symbol = '7203.T';
  const base = `market/yfinance/v2/securities/${symbol}`;
  const env = dividendEnvironment(new Map([
    ['market/yfinance/v2/derived/rights_dates/current.json', {
      generated_at: '2026-10-17T00:00:00Z',
      records: [{
        symbol,
        ex_dividend_date: estimatedDate,
        final_rights_date: dateOffset(estimatedDate, -1),
        record_date: dateOffset(estimatedDate, 1),
        event_type: 'history_estimated',
        estimated_from_history: true,
        source: 'Yahoo Finance historical dividend event dates',
      }],
    }],
    [`${base}/current.json`, {
      statistics: { exDividendDate: Date.parse(`${statisticsDate}T00:00:00Z`) / 1000 },
      field_status: { dividend_history: 'empty', statistics_exDividendDate: 'available' },
    }],
    [`${base}/dividends/${today.slice(0, 4)}.json`, { records: [] }],
    [`${base}/dividends/${Number(today.slice(0, 4)) - 1}.json`, { records: [] }],
  ]));

  const response = await worker.fetch(
    new Request(`https://example.com/api/v1/dividend-months?symbols=${symbol}`), env);
  assert.equal(response.status, 200);
  const data = await response.json();
  assert.equal(data.items[0].history_status, 'available');
  assert.deepEqual(data.items[0].upcoming_event, {
    ex_dividend_date: estimatedDate,
    final_rights_date: dateOffset(estimatedDate, -1),
    record_date: dateOffset(estimatedDate, 1),
    estimated_from_history: true,
    ex_dividend_date_status: 'estimated_from_dividend_history',
    source: 'Yahoo Finance historical dividend event dates',
  });
});

test('dividend-months prefers a recurring history estimate over an off-month Ticker.calendar date', async () => {
  const today = japanToday();
  const calendarDate = dateOffset(today, 30);
  const estimatedDate = dateOffset(today, 150);
  const symbol = '7203.T';
  const base = `market/yfinance/v2/securities/${symbol}`;
  const env = dividendEnvironment(new Map([
    ['market/yfinance/v2/derived/rights_dates/current.json', {
      records: [{
        symbol,
        ex_dividend_date: estimatedDate,
        final_rights_date: dateOffset(estimatedDate, -1),
        record_date: dateOffset(estimatedDate, 1),
        event_type: 'history_estimated',
        estimated_from_history: true,
      }],
    }],
    [`${base}/current.json`, {
      calendar: { ex_dividend_date: calendarDate, source: 'Yahoo Finance via yfinance Ticker.calendar' },
      field_status: { dividend_history: 'empty', upcoming_ex_dividend_date: 'available' },
    }],
    [`${base}/dividends/${today.slice(0, 4)}.json`, { records: [] }],
    [`${base}/dividends/${Number(today.slice(0, 4)) - 1}.json`, { records: [] }],
  ]));

  const response = await worker.fetch(
    new Request(`https://example.com/api/v1/dividend-months?symbols=${symbol}`), env);
  assert.equal(response.status, 200);
  const data = await response.json();
  assert.equal(data.items[0].upcoming_event.ex_dividend_date, estimatedDate);
  assert.equal(data.items[0].upcoming_event.estimated_from_history, true);
  assert.equal(data.items[0].upcoming_event.source, 'Yahoo Finance historical dividend event dates');
});

test('dividend-months preserves history provenance when its date matches Ticker.calendar', async () => {
  const today = japanToday();
  const estimatedDate = dateOffset(today, 120);
  const symbol = '7203.T';
  const base = `market/yfinance/v2/securities/${symbol}`;
  const env = dividendEnvironment(new Map([
    ['market/yfinance/v2/derived/rights_dates/current.json', {
      records: [{
        symbol,
        ex_dividend_date: estimatedDate,
        final_rights_date: dateOffset(estimatedDate, -1),
        record_date: dateOffset(estimatedDate, 1),
        event_type: 'history_estimated',
        estimated_from_history: true,
        source: 'Yahoo Finance historical dividend event dates',
      }],
    }],
    [`${base}/current.json`, {
      calendar: { ex_dividend_date: estimatedDate, source: 'Yahoo Finance via yfinance Ticker.calendar' },
      field_status: { dividend_history: 'empty', upcoming_ex_dividend_date: 'available' },
    }],
    [`${base}/dividends/${today.slice(0, 4)}.json`, { records: [] }],
    [`${base}/dividends/${Number(today.slice(0, 4)) - 1}.json`, { records: [] }],
  ]));

  const response = await worker.fetch(
    new Request(`https://example.com/api/v1/dividend-months?symbols=${symbol}`), env);
  assert.equal(response.status, 200);
  const data = await response.json();
  assert.equal(data.items[0].upcoming_event.ex_dividend_date, estimatedDate);
  assert.equal(data.items[0].upcoming_event.estimated_from_history, true);
  assert.equal(data.items[0].upcoming_event.ex_dividend_date_status, 'estimated_from_dividend_history');
  assert.equal(data.items[0].upcoming_event.source, 'Yahoo Finance historical dividend event dates');
});

test('dividend-months uses Ticker.calendar when no recurring history estimate exists', async () => {
  const today = japanToday();
  const calendarDate = dateOffset(today, 30);
  const symbol = '7203.T';
  const base = `market/yfinance/v2/securities/${symbol}`;
  const env = dividendEnvironment(new Map([
    [`${base}/current.json`, {
      calendar: { ex_dividend_date: calendarDate, source: 'Yahoo Finance via yfinance Ticker.calendar' },
      field_status: { dividend_history: 'empty', upcoming_ex_dividend_date: 'available' },
    }],
    [`${base}/dividends/${today.slice(0, 4)}.json`, { records: [] }],
    [`${base}/dividends/${Number(today.slice(0, 4)) - 1}.json`, { records: [] }],
  ]));

  const response = await worker.fetch(
    new Request(`https://example.com/api/v1/dividend-months?symbols=${symbol}`), env);
  assert.equal(response.status, 200);
  const data = await response.json();
  assert.equal(data.items[0].upcoming_event.ex_dividend_date, calendarDate);
  assert.equal(data.items[0].upcoming_event.estimated_from_history, false);
  assert.equal(data.items[0].upcoming_event.source, 'Yahoo Finance via yfinance Ticker.calendar');
});

test('dividend-months validates batches and reports missing R2 storage', async () => {
  assert.equal((await worker.fetch(new Request('https://example.com/api/v1/dividend-months?symbols=7203.T,../secret'), dividendEnvironment(new Map()))).status, 400);
  assert.equal((await worker.fetch(new Request(`https://example.com/api/v1/dividend-months?symbols=${Array.from({ length: 51 }, (_, i) => `T${i}`).join(',')}`), dividendEnvironment(new Map()))).status, 400);
  assert.equal((await worker.fetch(new Request('https://example.com/api/v1/dividend-months?symbols=7203.T'), dividendEnvironment(new Map(), false))).status, 503);
});

test('live-quotes pages an immutable v2 catalog generation and validates cursors', async () => {
  const generation = '2026-10-09';
  const catalog = {
    generation,
    market_data_date: generation,
    items: [
      { code: '7203', price: 2100, quote_date: generation, annual_dividend_per_share: 100, yield_rate: 4.76, dividend_status: 'has_dividend' },
      { code: '8306', price: 1000, quote_date: generation, annual_dividend_per_share: 0, yield_rate: 0, dividend_status: 'no_dividend' },
    ],
  };
  const env = dividendEnvironment(new Map([
    ['market/yfinance/v2/catalog/current.json', catalog],
    [`market/yfinance/v2/catalog/generations/${generation}.json`, catalog],
  ]));
  const first = await worker.fetch(new Request('https://example.com/api/v1/live-quotes?limit=1'), env);
  assert.equal(first.status, 200);
  const firstPage = await first.json();
  assert.equal(firstPage.run_id, generation);
  assert.equal(firstPage.market_data_date, generation);
  assert.equal(firstPage.complete, true);
  assert.equal(firstPage.items[0].code, '7203');
  assert.equal(firstPage.items[0].price, 2100);
  assert.equal(firstPage.next_cursor, '1');

  const second = await worker.fetch(new Request(`https://example.com/api/v1/live-quotes?run_id=${generation}&market_data_date=${generation}&cursor=1`), env);
  assert.equal(second.status, 200);
  assert.deepEqual((await second.json()).items.map(item => item.code), ['8306']);
  assert.equal((await worker.fetch(new Request('https://example.com/api/v1/live-quotes?run_id=../bad'), env)).status, 400);
  assert.equal((await worker.fetch(new Request(`https://example.com/api/v1/live-quotes?run_id=${generation}&market_data_date=2026-10-10`), env)).status, 409);
});

test('legacy trailing-distribution catalog rows are not exposed as annual DPS or yield', async () => {
  const catalog = {
    generation: '2026-10-16',
    market_data_date: '2026-10-16',
    items: [{
      code: '8214', name: 'AOKI', price: 1800, quote_date: '2026-10-16',
      annual_dividend_per_share: 630,
      trailing_12_month_distribution_per_share: 630,
      dividend_period: 'trailing_12_months',
      yield_rate: 35,
      yield_basis: 'trailing_12_months',
      dividend_status: 'has_dividend',
    }, {
      code: '6721', name: 'Wintest', price: 200, quote_date: '2026-10-16',
      annual_dividend_per_share: 0,
      trailing_12_month_distribution_per_share: 0,
      dividend_period: 'trailing_12_months',
      yield_rate: 0,
      dividend_status: 'no_dividend',
    }],
  };
  const env = dividendEnvironment(new Map([['market/yfinance/v2/catalog/current.json', catalog]]));

  const catalogResponse = await worker.fetch(new Request('https://example.com/api/v1/search-catalog'), env);
  const catalogPayload = await catalogResponse.json();
  const catalogItem = catalogPayload.items[0];
  assert.equal(catalogItem.annual_dividend_per_share, null);
  assert.equal(catalogItem.yield_rate, null);
  assert.equal(catalogItem.trailing_12_month_distribution_per_share, 630);
  const noDividendItem = catalogPayload.items.find(item => item.code === '6721');
  assert.equal(noDividendItem.annual_dividend_per_share, 0);
  assert.equal(noDividendItem.dividend_period, 'yfinance_reported_no_dividend');
  assert.equal(noDividendItem.yield_rate, 0);

  const quotesResponse = await worker.fetch(new Request('https://example.com/api/v1/live-quotes?limit=1'), env);
  const quoteItem = (await quotesResponse.json()).items[0];
  assert.equal(quoteItem.annual_dividend_per_share, null);
  assert.equal(quoteItem.yield_rate, null);
});

test('current dividend states override old issuer actuals across search, quotes and detail', async () => {
  const code = '8783';
  const generation = '2026-10-28';
  const catalog = { generation, market_data_date: generation, items: [{
    code, name: 'Example', security_type: 'equity', price: 37,
    quote_date: generation, dividend_status: 'no_dividend',
  }] };
  const current = { price: { close: 37, date: generation },
    dividend: { dividend_status: 'no_dividend' }, statistics: { dividendYield: 0 } };
  const documents = new Map([
    ['market/yfinance/v2/catalog/current.json', catalog],
    ['market/yfinance/v2/issuer_dividends/current.json', { items: { [code]: {
      actual_status: 'available', actual: { fiscal_year: 2010, annual_dps_adjusted: 16.6667 },
    } } }],
    [`market/yfinance/v2/securities/${code}.T/profile.json`, { display_name: 'Example' }],
    [`market/yfinance/v2/securities/${code}.T/current.json`, current],
  ]);
  const env = dividendEnvironment(documents);
  const read = async path => (await (await worker.fetch(new Request(`https://example.com${path}`), env)).json());
  for (const status of ['no_dividend', 'unknown']) {
    current.dividend.dividend_status = status;
    catalog.items[0].dividend_status = status;
    const expected = status === 'no_dividend' ? 0 : null;
    const detail = await read(`/api/v1/securities/${code}`);
    const search = (await read(`/api/v1/security-search?q=${code}`)).items[0];
    const quote = (await read(`/api/v1/live-quotes?codes=${code}`)).items[0];
    const catalogRow = (await read('/api/v1/search-catalog')).items[0];
    assert.equal(detail.dividend_status, status);
    assert.equal(detail.dividend.annual_dps, expected);
    assert.equal(detail.market_yield_rate, expected);
    for (const row of [search, quote, catalogRow]) {
      assert.equal(row.dividend_status, status);
      assert.equal(row.annual_dividend_per_share, expected);
      assert.equal(row.yield_rate, expected);
    }
  }
  const issuer = documents.get('market/yfinance/v2/issuer_dividends/current.json').items[code];
  const nextYear = Number(japanToday().slice(0, 4)) + 1;
  issuer.forecast = { status: 'available', fiscal_year: nextYear,
    period_end: `${nextYear}-12-31`, annual_dps_current_share_basis: 40 };
  current.dividend.dividend_status = 'no_dividend';
  const forecastDetail = await read(`/api/v1/securities/${code}`);
  const forecastQuote = (await read(`/api/v1/live-quotes?codes=${code}`)).items[0];
  assert.equal(forecastDetail.dividend_status, 'has_dividend');
  assert.equal(forecastDetail.dividend.annual_dps, 40);
  assert.equal(forecastQuote.annual_dividend_per_share, 40);
});

test('live-quotes keeps UUID generations pinned across same-day catalog publication', async () => {
  const date = '2026-10-20';
  const generation = `${date}-${'a'.repeat(32)}`;
  const catalog = { generation, market_data_date: date, items: [
    { code: '7203', price: 100 }, { code: '8306', price: 200 },
  ] };
  const documents = new Map([
    ['market/yfinance/v2/catalog/current.json', catalog],
    [`market/yfinance/v2/catalog/generations/${generation}.json`, catalog],
  ]);
  const env = dividendEnvironment(documents);
  const first = await worker.fetch(new Request('https://example.com/api/v1/live-quotes?limit=1'), env);
  assert.equal(first.status, 200);
  const page = await first.json();
  assert.equal(page.run_id, generation);
  documents.set('market/yfinance/v2/catalog/current.json', {
    generation: `${date}-${'b'.repeat(32)}`, market_data_date: date, items: [],
  });
  const next = await worker.fetch(new Request(
    `https://example.com/api/v1/live-quotes?run_id=${page.run_id}&cursor=${page.next_cursor}&market_data_date=${date}`,
  ), env);
  assert.equal(next.status, 200);
  assert.equal((await next.json()).items[0].price, 200);
  for (const invalid of [`${date}-abc`, `${date}-${'g'.repeat(32)}`, `${generation}/bad`]) {
    assert.equal((await worker.fetch(new Request(
      `https://example.com/api/v1/live-quotes?run_id=${invalid}`,
    ), env)).status, 400);
  }
  documents.set(`market/yfinance/v2/catalog/generations/${generation}.json`, {
    ...catalog, generation: `${date}-${'c'.repeat(32)}`,
  });
  assert.equal((await worker.fetch(new Request(
    `https://example.com/api/v1/live-quotes?run_id=${generation}`,
  ), env)).status, 409);
});

test('financial aggregation orders full periods and retains fiscal-year-only legacy rows', async () => {
  const base = 'market/yfinance/v2/securities/1605.T/financials';
  const documents = new Map();
  // Reverse same-year insertion order, as R2 listing order is not a period contract.
  for (const period of ['2019-12-31', '2019-03-31']) {
    documents.set(`${base}/${period}.json`, {
      year: '2019',
      annual: { fiscal_year: '2019', period_end: period, sales: period.endsWith('12-31') ? 200 : 100 },
      valuation: { fiscal_year: '2019', period_end: period, per: 10 },
      retrieval_status: { valuation: period.endsWith('12-31') ? 'available' : 'empty' },
    });
  }
  documents.set(`${base}/2018.json`, {
    year: '2018', annual: { fiscal_year: '2018/03', sales: 50 },
    valuation: { fiscal_year: '2018/03', per: 5 },
  });
  const response = await worker.fetch(
    new Request('https://example.com/api/v1/financial-data/1605'), dividendEnvironment(documents),
  );
  assert.equal(response.status, 200);
  const data = await response.json();
  for (const rows of [data.financials.annual, data.financials.seed_annual,
    data.valuation.history, data.valuation.seed_history]) {
    assert.deepEqual(rows.map(row => row.period_end || row.fiscal_year),
      ['2018/03', '2019-03-31', '2019-12-31']);
  }
  assert.equal(data.financials.annual.at(-1).sales, 200);
  assert.equal(data.valuation.field_status, 'available');
});

test('Tokio Marine IRBANK forecast replaces the yfinance DPS in quotes and financial data', async () => {
  const code = '8766';
  const generation = '2026-10-23';
  const base = 'market/yfinance/v2/securities/8766.T';
  const actual = {
    fiscal_year: 2026,
    annual_dps: 218,
    annual_dps_adjusted: 14.533333333333333,
    period_end: '2026-03-31',
    source_refs: [{ doc_id: 'S100YLS8', source: 'edinet' }],
  };
  const forecast = {
    status: 'available',
    fiscal_year: 2027,
    period_end: '2027-03-31',
    annual_dps_current_share_basis: 16.34,
    amount_basis: 'irbank_forecast_current_share_basis',
    source_refs: [{ doc_id: '20260825525826', source: 'tdnet' }],
    components: [
      { period: 'second_quarter', reported_dps: 122.5, current_share_basis_dps: 8.17 },
      { period: 'year_end', reported_dps: 8.17, current_share_basis_dps: 8.17 },
    ],
    normalization: { method: 'interim_dps_divided_by_irbank_adjustment_factor', factor: 15 },
  };
  const catalog = {
    generation,
    market_data_date: generation,
    items: [{ code, name: '東京海上ホールディングス', price: 544.3, quote_date: generation,
      annual_dividend_per_share: 130, yield_rate: 23.8839, yield_basis: 'next_fiscal_year_forecast',
      dividend_status: 'has_dividend' }],
  };
  const documents = new Map([
    ['market/yfinance/v2/catalog/current.json', catalog],
    [`market/yfinance/v2/catalog/generations/${generation}.json`, catalog],
    ['market/irbank/v1/securities/8766/overview.json', {
      security: { code, name: '東京海上ホールディングス' },
      dividends: { records: [
        { fiscal_year: 2025, annual_dps: 172, annual_dps_adjusted: 11.47 },
        { fiscal_year: 2026, annual_dps: 218, annual_dps_adjusted: 14.53 },
      ] },
      sources: { dividends: { source_label: '金融庁 EDINET / TDnet', processed_by: 'IRBANK' } },
    }],
    ['market/yfinance/v2/issuer_dividends/current.json', {
      source: 'IRBANK Connect REST API',
      attribution: { source_label: '金融庁 EDINET / TDnet', processed_by: 'IRBANK' },
      items: { [code]: { code, name: '東京海上ホールディングス', actual_status: 'available', actual,
        forecast_status: 'available', forecast } },
    }],
    [`${base}/profile.json`, { code, symbol: '8766.T', display_name: '東京海上ホールディングス' }],
    [`${base}/current.json`, {
      code, symbol: '8766.T', updated_at: `${generation}T12:00:00Z`,
      price: { close: 544.3, date: generation, currency: 'JPY' },
      dividend: { dividend_status: 'has_dividend', yield_basis: 'next_fiscal_year_forecast',
        next_fiscal_year_forecast_per_share: 130, trailing_12_month_per_share: 218,
        event_amount_basis: 'yfinance_split_adjusted_assumption' },
      statistics: { dividendRate: 130 },
    }],
    [`${base}/issuer_dividends/2024.json`, { records: [{
      fiscal_year: 2024, period_end: '2024-03-31', annual_dps: 123, annual_dps_adjusted: 8.2,
    }] }],
  ]);
  const env = dividendEnvironment(documents);

  const quoteResponse = await worker.fetch(new Request(
    'https://example.com/api/v1/live-quotes?codes=8766',
  ), env);
  const quote = (await quoteResponse.json()).items[0];
  assert.equal(quote.annual_dividend_per_share, 16.34);
  assert.equal(quote.dividend_source, 'IRBANK');
  assert.equal(quote.dividend_period, 'next_fiscal_year_forecast');
  assert.equal(quote.yield_rate, 3.002);

  const financialResponse = await worker.fetch(new Request(
    'https://example.com/api/v1/financial-data/8766',
  ), env);
  const financial = await financialResponse.json();
  assert.equal(financial.issuer_dividends.latest_actual.annual_dps_adjusted, 14.533333333333333);
  assert.deepEqual(financial.issuer_dividends.records.map(row => row.fiscal_year), [2025, 2026]);
  assert.equal(financial.issuer_dividends.records[0].source, 'IRBANK');
  assert.equal(financial.issuer_dividends.records.at(-1).annual_dps_adjusted, 14.533333333333333);
  assert.equal(financial.issuer_dividends.forecast.annual_dps_current_share_basis, 16.34);
});

test('requested live quotes prefer IRBANK forecasts and keep trailing distributions separate', async () => {
  const generation = '2026-10-16';
  const codes = ['8214', '8058'];
  const catalog = {
    generation,
    market_data_date: generation,
    items: codes.map((code, index) => ({
      code,
      price: 1800 + index * 1000,
      quote_date: generation,
      annual_dividend_per_share: 630,
      trailing_12_month_distribution_per_share: 630,
      dividend_period: 'trailing_12_months',
      yield_rate: 35,
      dividend_status: 'has_dividend',
    })),
  };
  const documents = new Map([
    ['market/yfinance/v2/catalog/current.json', catalog],
    [`market/yfinance/v2/catalog/generations/${generation}.json`, catalog],
    ['market/yfinance/v2/issuer_dividends/current.json', {
      source: 'IRBANK Connect REST API',
      items: {
        '8214': {
          actual_status: 'available', actual: { fiscal_year: 2026, annual_dps_adjusted: 90 },
          forecast_status: 'available', forecast: { status: 'available', fiscal_year: 2027, period_end: '2027-03-31', annual_dps_current_share_basis: 90 },
        },
        '8058': {
          actual_status: 'available', actual: { fiscal_year: 2026, annual_dps_adjusted: 125 },
          forecast_status: 'available', forecast: { status: 'available', fiscal_year: 2027, period_end: '2027-03-31', annual_dps_current_share_basis: 125 },
        },
      },
    }],
    ['market/yfinance/v2/securities/8214.T/current.json', {
      updated_at: '2026-10-16T00:00:00Z',
      price: { close: 1800, date: generation },
      dividend: {
        dividend_status: 'has_dividend',
        yield_basis: 'next_fiscal_year_forecast',
        next_fiscal_year_forecast_per_share: 120,
        trailing_12_month_per_share: 630,
      },
    }],
    ['market/yfinance/v2/securities/8058.T/current.json', {
      updated_at: '2026-10-16T00:00:00Z',
      price: { close: 2800, date: generation },
      dividend: {
        dividend_status: 'has_dividend',
        yield_basis: 'next_fiscal_year_forecast',
        next_fiscal_year_forecast_per_share: 126,
        trailing_12_month_per_share: 819,
      },
    }],
  ]);
  const response = await worker.fetch(new Request(
    'https://example.com/api/v1/live-quotes?codes=8214%2C8058',
  ), dividendEnvironment(documents));

  assert.equal(response.status, 200);
  const items = (await response.json()).items;
  assert.deepEqual(items.map(item => item.annual_dividend_per_share), [90, 125]);
  assert.deepEqual(items.map(item => item.trailing_12_month_distribution_per_share), [630, 819]);
  assert.deepEqual(items.map(item => item.dividend_period), [
    'next_fiscal_year_forecast', 'next_fiscal_year_forecast',
  ]);
  assert.deepEqual(items.map(item => item.yield_rate), [5, 4.4643]);

  const irbankIndex = documents.get('market/yfinance/v2/issuer_dividends/current.json');
  irbankIndex.items['8214'].forecast.annual_dps_current_share_basis = 100;
  documents.set('market/yfinance/v2/issuer_dividends/current.json', irbankIndex);
  const revisedResponse = await worker.fetch(new Request(
    'https://example.com/api/v1/live-quotes?codes=8214',
  ), dividendEnvironment(documents));
  assert.equal((await revisedResponse.json()).items[0].annual_dividend_per_share, 100);
});

test('legacy completion metadata cannot substitute for the published v2 catalog', async () => {
  const env = dividendEnvironment(new Map([['market/yfinance/live_quotes_completion.json', {
    last_completed_date: '2026-10-10', live_quote_count: 3960,
  }]]));
  const response = await worker.fetch(new Request('https://example.com/api/v1/live-quotes?limit=2'), env);
  assert.equal(response.status, 503);
  assert.deepEqual(await response.json(), { error: 'catalog_unavailable' });
});

test('share-split-history reports stored events as available and removes duplicate partition rows', async () => {
  const code = '7203';
  const base = 'market/yfinance/v2/securities/7203.T';
  const split = { date: '2021-09-29', ratio: 5 };
  const documents = new Map([
    [`${base}/current.json`, { field_status: { split_history: 'empty' } }],
    [`${base}/splits/2021.json`, { records: [split] }],
    [`${base}/splits/2022.json`, { records: [split] }],
    [`${base}/splits/2023.json`, { records: [split] }],
  ]);
  const response = await worker.fetch(new Request(
    'https://example.com/api/v1/share-split-history?code=7203&from=2017-01-01&to=2026-10-16',
  ), dividendEnvironment(documents));

  assert.equal(response.status, 200);
  assert.deepEqual(await response.json(), {
    code,
    history_status: 'available',
    events: [split],
  });
});

test('share-split-history preserves empty and failed states when no events are stored', async () => {
  const base = 'market/yfinance/v2/securities/7203.T';
  const documents = new Map([
    [`${base}/current.json`, { field_status: { split_history: 'failed' } }],
    [`${base}/splits/2025.json`, { records: [] }],
  ]);
  const response = await worker.fetch(new Request(
    'https://example.com/api/v1/share-split-history?code=7203&from=2025-01-01&to=2025-12-31',
  ), dividendEnvironment(documents));

  assert.equal(response.status, 200);
  assert.deepEqual(await response.json(), { code: '7203', history_status: 'failed', events: [] });
});

test('retired Yahoo quote queue deliveries are acknowledged without a fetch or requeue', async () => {
  const messages = [];
  let acknowledged = false;
  let retried = false;
  await worker.queue({ queue: 'diview-market-prices', messages: [{
    body: { run_date: '2026-10-10', batch_index: 7, total_batches: 330, symbols: Array.from({ length: 12 }, (_, index) => String(7200 + index)) },
    ack: () => { acknowledged = true; },
    retry: () => { retried = true; }
  }] }, {});

  assert.equal(acknowledged, true);
  assert.equal(retried, false);
  assert.equal(messages.length, 0);
});

test('feedback acceptance, retries, collisions and storage failure are distinct', async () => {
  const env = environment();
  const accepted = await worker.fetch(request(payload()), env);
  assert.equal(accepted.status, 201);
  assert.equal((await accepted.json()).accepted, true);
  assert.equal((await worker.fetch(request(payload()), env)).status, 201);
  assert.equal((await worker.fetch(request({ ...payload(), message: 'changed' }), env)).status, 409);
  assert.equal((await worker.fetch(request(payload()), environment(1, true))).status, 503);
});
test('invalid input, rate limiting and anonymous administration are rejected', async () => {
  assert.equal((await worker.fetch(request({}), environment())).status, 400);
  const limited = await worker.fetch(request(payload()), environment(6));
  assert.equal(limited.status, 429);
  assert.ok(limited.headers.get('retry-after'));
  assert.equal((await worker.fetch(new Request('https://example.com/api/v1/admin/feedback'), environment())).status, 401);
});

test('notification storage failure never turns a saved feedback receipt into failure', async () => {
  const env = environment();
  env.FEEDBACK_EMAIL_FROM = 'noreply@example.org';
  env.FEEDBACK_EMAIL_TO = 'admin@example.com';
  env.FEEDBACK_EMAIL = { send: async () => { throw Error('mail unavailable'); } };
  const prepare = env.DB.prepare;
  env.DB.prepare = sql => {
    if (sql.includes('support_feedback_email')) throw Error('outbox unavailable');
    return prepare(sql);
  };
  const response = await worker.fetch(request(payload()), env);
  assert.equal(response.status, 201);
  assert.equal((await response.json()).accepted, true);
});
