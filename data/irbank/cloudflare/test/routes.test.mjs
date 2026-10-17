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

function dividendEnvironment(documents, marketAvailable = true) {
  return {
    APP_DEFINITIONS: { get: async () => null },
    MARKET_DATA: marketAvailable ? {
      get: async key => documents.has(key) ? { json: async () => documents.get(key) } : null,
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
      dividend: { dividend_status: 'has_dividend' },
      field_status: { dividend_history: 'available' },
      action_history: {
        status: 'success', source: 'yfinance_history', yfinance_version: '1.7.0',
        coverage_start: '2025-01-01', coverage_end: '2025-12-31',
        full_history_coverage_end: '2025-12-31', dividend_event_count: 2, period: 'max',
      },
    }],
    [`${base}/sync.json`, {
      last_attempt_at: '2026-10-17T00:00:00Z', status: 'partial',
      failure_reason: 'Field fetch failed: price', yfinance_version: '1.7.0',
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
  assert.deepEqual(data.data_retrieval, {
    yfinance_ticker: '7203.T',
    source: 'Yahoo Finance via yfinance',
    currency: 'JPY',
    last_attempt_at: '2026-10-17T00:00:00Z',
    processing_status: 'partial',
    failure_reason: 'Field fetch failed: price',
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
    { calendar_year: year - 2, calendar_year_distribution_per_share: 25, amount_basis: 'yfinance_split_adjusted_assumption' },
    { calendar_year: year - 1, calendar_year_distribution_per_share: 30, amount_basis: 'yfinance_split_adjusted_assumption' },
  ]);
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
  }]);
});

test('financial API does not substitute legacy DPS when v2 event history is unavailable', async () => {
  const code = '1306';
  const env = dividendEnvironment(new Map([[`market/irbank/v1/securities/${code}/overview.json`, {
    security: { code, name: 'ETF' },
    financials: { annual: [] },
    dividends: { records: [{ fiscal_year: '2025/03', annual_dps_adjusted: 999 }] },
    valuation: { history: [] },
  }]]));
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

test('requested live quotes use annual fiscal DPS and keep trailing distributions separate', async () => {
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

  documents.set('market/yfinance/v2/securities/8214.T/current.json', {
    updated_at: '2026-10-17T00:00:00Z',
    price: { close: 1800, date: generation },
    dividend: {
      dividend_status: 'has_dividend',
      yield_basis: 'next_fiscal_year_forecast',
      next_fiscal_year_forecast_per_share: 100,
      trailing_12_month_per_share: 630,
    },
  });
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
