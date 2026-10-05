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
