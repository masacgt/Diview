import test from 'node:test';
import assert from 'node:assert/strict';
import worker from '../src/index.js';

function environment(limit = 1, storageFails = false) {
  const rows = new Map();
  return {
    APP_DEFINITIONS: { get: async () => null },
    MARKET_DATA: { get: async () => ({ json: async () => ({ security: { code: '7203' }, financials: { annual: [{ fiscal_year: '2026/03' }] }, dividends: { records: [] }, valuation: { history: [] }, officers: ['private-not-needed'] }) }) },
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
      get: async key => documents.has(key) ? { json: async () => documents.get(key) } : null
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

test('dividend-months returns sorted recent observed months from R2 for each requested symbol', async () => {
  const today = japanToday();
  const recentDate = dateOffset(today, -20);
  let oldDate = dateOffset(today, -400);
  while ([today.slice(5, 7), recentDate.slice(5, 7)].includes(oldDate.slice(5, 7))) {
    oldDate = dateOffset(oldDate, -20);
  }
  const key = 'market/yfinance/securities/7203.T.json';
  const env = dividendEnvironment(new Map([[key, {
    actions: { dividends: [
      { date: today, amount: 25 },
      { date: recentDate, amount: 30 },
      { date: oldDate, amount: 40 },
      { date: dateOffset(today, 1), amount: 50 },
      { date: recentDate, amount: 0 },
      { date: 'invalid', amount: 100 }
    ] }
  }]]));
  const response = await worker.fetch(new Request('https://example.com/api/v1/dividend-months?symbols=7203.T,7203.T,MSFT'), env);
  assert.equal(response.status, 200);
  const data = await response.json();
  assert.equal(data.as_of, today);
  assert.equal(data.lookback_days, 365);
  assert.deepEqual(data.items, [
    {
      symbol: '7203.T',
      months: [...new Set([Number(today.slice(5, 7)), Number(recentDate.slice(5, 7))])].sort((a, b) => a - b),
      events: [
        { date: recentDate, amount: 30, final_rights_date: null },
        { date: today, amount: 25, final_rights_date: null },
      ],
    },
    { symbol: 'MSFT', months: [] }
  ]);
});

test('dividend-months validates batches and reports missing R2 storage', async () => {
  assert.equal((await worker.fetch(new Request('https://example.com/api/v1/dividend-months?symbols=7203.T,../secret'), dividendEnvironment(new Map()))).status, 400);
  assert.equal((await worker.fetch(new Request(`https://example.com/api/v1/dividend-months?symbols=${Array.from({ length: 51 }, (_, i) => `T${i}`).join(',')}`), dividendEnvironment(new Map()))).status, 400);
  assert.equal((await worker.fetch(new Request('https://example.com/api/v1/dividend-months?symbols=7203.T'), dividendEnvironment(new Map(), false))).status, 503);
});

test('live-quotes pages only the immutable completed run and validates cursors', async () => {
  const env = dividendEnvironment(new Map([['market/yfinance/live_quotes_completion.json', {
    last_completed_run_id: '2026-10-09', last_completed_date: '2026-10-09'
  }]]));
  const listCalls = [];
  env.MARKET_DATA.list = async options => {
    listCalls.push(options);
    return {
      objects: [{ key: 'market/yfinance/live_quote_runs/2026-10-09/7203.json', customMetadata: { close: '2100', quoteDate: '2026-10-09' } }],
      truncated: true,
      cursor: 'opaque-next-token'
    };
  };
  const first = await worker.fetch(new Request('https://example.com/api/v1/live-quotes?limit=1'), env);
  assert.equal(first.status, 200);
  const firstPage = await first.json();
  assert.equal(firstPage.run_id, '2026-10-09');
  assert.equal(firstPage.market_data_date, '2026-10-09');
  assert.deepEqual(firstPage.items, [{ code: '7203', price: 2100, quote_date: '2026-10-09' }]);
  assert.equal(firstPage.next_cursor, 'opaque-next-token');
  assert.equal(listCalls[0].prefix, 'market/yfinance/live_quote_runs/2026-10-09/');
  assert.equal(listCalls[0].limit, 1);

  const second = await worker.fetch(new Request('https://example.com/api/v1/live-quotes?run_id=2026-10-09&market_data_date=2026-10-09&cursor=opaque-next-token'), env);
  assert.equal(second.status, 200);
  assert.equal(listCalls[1].cursor, 'opaque-next-token');
  assert.equal((await worker.fetch(new Request('https://example.com/api/v1/live-quotes?run_id=../bad'), env)).status, 400);
});

test('legacy completion metadata is not exposed as a published quote snapshot', async () => {
  const env = dividendEnvironment(new Map([['market/yfinance/live_quotes_completion.json', {
    last_completed_date: '2026-10-10', live_quote_count: 3960
  }]]));
  env.MARKET_DATA.list = async () => assert.fail('no versioned run should be listed');
  const response = await worker.fetch(new Request('https://example.com/api/v1/live-quotes?limit=2'), env);
  assert.equal(response.status, 200);
  assert.deepEqual(await response.json(), {
    run_id: null,
    market_data_date: null,
    complete: false,
    legacy_unpublished: true,
    items: [],
    next_cursor: null,
  });
});

test('legacy 12-symbol queue deliveries are forwarded as six two-symbol jobs', async () => {
  const messages = [];
  let acknowledged = false;
  const env = {
    MARKET_DATA: {
      get: async key => key.endsWith('live_quotes_state.json')
        ? { json: async () => ({ universe_size: 3960, queue_run_date: '2026-10-10' }) }
        : null
    },
    MARKET_PRICE_QUEUE: { sendBatch: async batch => messages.push(...batch) },
    DB: { prepare: () => ({ bind: () => ({ run: async () => ({ success: true }) }) }) }
  };
  await worker.queue({ queue: 'diview-market-prices', messages: [{
    body: { run_date: '2026-10-10', batch_index: 7, total_batches: 330, symbols: Array.from({ length: 12 }, (_, index) => String(7200 + index)) },
    ack: () => { acknowledged = true; },
    retry: () => assert.fail('legacy forwarding should not retry')
  }] }, env);

  assert.equal(acknowledged, true);
  assert.equal(messages.length, 6);
  assert.ok(messages.every(message => message.body.symbols.length === 2));
  assert.deepEqual(messages.map(message => message.body.batch_index), [42, 43, 44, 45, 46, 47]);
  assert.ok(messages.every(message => message.body.total_batches === 1980));
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
