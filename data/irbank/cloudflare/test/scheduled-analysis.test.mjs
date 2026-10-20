import test from 'node:test';
import assert from 'node:assert/strict';
import worker from '../src/index.js';

function fixture() {
  const root = 'market/yfinance/v2';
  const heads = new Map([
    [`${root}/catalog/current.json`, { httpEtag: '"catalog-a"' }],
    [`${root}/financial_revision.json`, { httpEtag: '"financial-a"' }],
  ]);
  const documents = new Map([
    [`${root}/securities/7203.T/current.json`, {
      updated_at: new Date().toISOString(), price: { close: 100, date: '2026-10-20' },
      statistics: { trailingEps: 10 }, dividend: {},
    }],
    [`${root}/securities/7203.T/financials/2026-03-31.json`, {
      year: '2026', annual: { fiscal_year: '2026', period_end: '2026-03-31', equity_ratio: 20 },
    }],
  ]);
  const state = { source: null, snapshots: [], headKeys: [], failPersistence: false };
  const security = { security_code: '72030', security_code_display: '7203', name: 'Toyota' };
  const env = {
    ADMIN_SECRET: 'test',
    APP_DEFINITIONS: { get: async () => null },
    MARKET_DATA: {
      head: async key => { state.headKeys.push(key); return heads.get(key) || null; },
      get: async key => documents.has(key) ? { json: async () => documents.get(key) } : null,
      list: async ({ prefix }) => ({
        objects: [...documents.keys()].filter(key => key.startsWith(prefix)).map(key => ({ key })),
        truncated: false,
      }),
    },
    DB: {
      prepare(sql) {
        let values = [];
        return {
          sql,
          get values() { return values; },
          bind(...args) { values = args; return this; },
          async first() {
            if (sql.includes("state_key = 'analysis_source'")) return state.source ? { value_json: state.source } : null;
            return null;
          },
          async all() {
            if (sql.includes('JOIN watchlist_targets')) return { results: [] };
            if (sql.includes('FROM securities ORDER BY')) return { results: [security] };
            return { results: [] };
          },
          async run() {
            if (sql.includes("VALUES ('analysis_source'")) state.source = values[0];
            return { meta: { last_row_id: 1, changes: 1 } };
          },
        };
      },
      async batch(statements) {
        if (state.failPersistence) throw new Error('persistence failed');
        for (const statement of statements) {
          if (statement.sql.includes('INSERT INTO stock_analysis_state')) {
            state.snapshots.push(JSON.parse(statement.values.at(-1)));
          }
        }
        return statements.map(() => ({ success: true }));
      },
    },
  };
  const run = async () => {
    const response = await worker.fetch(new Request('https://example.com/api/v1/admin/run-analysis', {
      method: 'POST', headers: { 'X-Admin-Secret': 'test' },
    }), env);
    assert.equal(response.status, 200);
    return response.json();
  };
  return { root, heads, documents, state, run };
}

test('scheduled analysis persists unwatched securities on either v2 source change, skips unchanged sources', async () => {
  const f = fixture();
  assert.equal((await f.run()).source_tag_advanced, true);
  assert.equal(f.state.snapshots.length, 1);
  assert.equal((await f.run()).source_changed, false);
  assert.equal(f.state.snapshots.length, 1);

  f.heads.set(`${f.root}/catalog/current.json`, { httpEtag: '"catalog-b"' });
  f.documents.get(`${f.root}/securities/7203.T/current.json`).price.close = 200;
  assert.equal((await f.run()).processed, 1);
  assert.equal(f.state.snapshots.at(-1).values.per, 20);

  f.heads.set(`${f.root}/financial_revision.json`, { etag: 'financial-b' });
  f.documents.get(`${f.root}/securities/7203.T/financials/2026-03-31.json`).annual.equity_ratio = 40;
  assert.equal((await f.run()).source_tag_advanced, true);
  assert.equal(f.state.snapshots.length, 3);
  assert.equal(f.state.snapshots.at(-1).values.equity_ratio, 40);
  assert.equal((await f.run()).source_changed, false);
  assert.deepEqual(new Set(f.state.headKeys), new Set([
    `${f.root}/catalog/current.json`, `${f.root}/financial_revision.json`,
  ]));
});

test('failed persistence leaves source tag pending for retry', async () => {
  const f = fixture();
  await f.run();
  const previous = f.state.source;
  f.heads.set(`${f.root}/financial_revision.json`, { httpEtag: '"financial-b"' });
  f.state.failPersistence = true;
  const failed = await f.run();
  assert.equal(failed.failed, 1);
  assert.equal(failed.source_tag_advanced, false);
  assert.equal(f.state.source, previous);
  f.state.failPersistence = false;
  assert.equal((await f.run()).source_tag_advanced, true);
  assert.notEqual(f.state.source, previous);
});

test('scheduled analysis does not compute FCF from guarded seed and yen cash flows', async () => {
  const f = fixture();
  f.documents.get(`${f.root}/securities/7203.T/financials/2026-03-31.json`).annual = {
    fiscal_year: '2026/03', period_end: '2026-03-31',
    sales: 100000000, operating_cf: 30000000, investing_cf: null,
    field_status: { investing_cf: 'unsupported' },
    field_status_reason: { investing_cf: ['mixed_source_units_not_comparable'] },
    source_values: {
      seed: { source: 'IRBANK', values: { investing_cf: -10 } },
      yfinance: { values: { sales: 100000000, operating_cf: 30000000 } },
    },
  };
  assert.equal((await f.run()).failed, 0);
  assert.equal(f.state.snapshots.at(-1).values.fcf_margin, null);
});

test('legacy source state and absent financial marker migrate when the marker first appears', async () => {
  const f = fixture();
  f.state.source = JSON.stringify({ etag: '"legacy"', stock_version: 1, watch_version: 1 });
  f.heads.delete(`${f.root}/financial_revision.json`);
  assert.equal((await f.run()).source_tag_advanced, true);
  assert.equal((await f.run()).source_changed, false);
  f.heads.set(`${f.root}/financial_revision.json`, { httpEtag: '"first-revision"' });
  assert.equal((await f.run()).source_tag_advanced, true);
  assert.equal(f.state.snapshots.length, 2);
});
