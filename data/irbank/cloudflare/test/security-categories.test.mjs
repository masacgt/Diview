import test from 'node:test';
import assert from 'node:assert/strict';
import worker from '../src/index.js';

const universeKey = 'market/yfinance/v2/universe/current.json';
const universe = {
  updated_at: '2026-10-26T00:00:00Z', session_date: '2026-10-26',
  securities: [
    { code: '1306', jquants_product_category: '014' },
    { code: '8951', jquants_product_category: '013' },
    { code: '25935', jquants_product_category: '011' },
    { code: '285A', jquants_product_category: null },
  ],
};

function environment(document = universe) {
  return {
    APP_DEFINITIONS: { get: async () => '{}' },
    DB: { prepare: () => { throw Error('Category lookup must not use D1'); } },
    MARKET_DATA: { get: async key => {
      assert.equal(key, universeKey, 'Categories must not depend on price/catalog coverage');
      return document ? { json: async () => document } : null;
    } },
  };
}

test('full universe and batches expose categories even without any priced catalog entry', async () => {
  const full = await worker.fetch(new Request('https://example.com/api/v1/security-categories'), environment());
  assert.equal(full.status, 200);
  assert.deepEqual((await full.json()).categories, { '1306': '014', '8951': '013', '25935': '011', '285A': null });
  const batch = await worker.fetch(new Request('https://example.com/api/v1/security-categories?codes=13060,8951.T,25935,285a,9999'), environment());
  assert.equal(batch.status, 200);
  const body = await batch.json();
  assert.equal(body.updated_at, universe.updated_at);
  assert.deepEqual(body.categories, { '1306': '014', '8951': '013', '25935': '011', '285A': null });
  assert.deepEqual(body.not_found, ['9999']);
  assert.equal(batch.headers.get('cache-control'), 'public, max-age=300');
});

test('invalid codes and unavailable or corrupt universes never become successful negative results', async () => {
  for (const codes of ['', '1306,', '../', Array.from({ length: 201 }, (_, i) => String(1000 + i)).join(',')]) {
    const response = await worker.fetch(new Request(`https://example.com/api/v1/security-categories?codes=${codes}`), environment());
    assert.equal(response.status, 400);
  }
  for (const document of [null, {}, { ...universe, securities: [] }, {
    ...universe, securities: [{ code: '1306', jquants_product_category: 'ETF' }],
  }]) {
    const response = await worker.fetch(new Request('https://example.com/api/v1/security-categories'), environment(document));
    assert.equal(response.status, 503);
    assert.equal(response.headers.get('cache-control'), 'private, no-store');
  }
  const post = await worker.fetch(new Request('https://example.com/api/v1/security-categories', { method: 'POST' }), environment());
  assert.equal(post.status, 405);
});
