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
    { symbol: '7203.T', months: [...new Set([Number(today.slice(5, 7)), Number(recentDate.slice(5, 7))])].sort((a, b) => a - b) },
    { symbol: 'MSFT', months: [] }
  ]);
});

test('dividend-months validates batches and reports missing R2 storage', async () => {
  assert.equal((await worker.fetch(new Request('https://example.com/api/v1/dividend-months?symbols=7203.T,../secret'), dividendEnvironment(new Map()))).status, 400);
  assert.equal((await worker.fetch(new Request(`https://example.com/api/v1/dividend-months?symbols=${Array.from({ length: 51 }, (_, i) => `T${i}`).join(',')}`), dividendEnvironment(new Map()))).status, 400);
  assert.equal((await worker.fetch(new Request('https://example.com/api/v1/dividend-months?symbols=7203.T'), dividendEnvironment(new Map(), false))).status, 503);
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
