import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import worker from '../src/index.js';

function fixture() {
  const root = 'market/yfinance/v2';
  const heads = new Map([
    [`${root}/catalog/current.json`, { httpEtag: '"catalog-a"' }],
    [`${root}/financial_revision.json`, { httpEtag: '"financial-a"' }],
  ]);
  const documents = new Map([
    [`${root}/catalog/current.json`, { generation: '2026-10-20', items: [{ code: '7203', name: 'Toyota' }] }],
    [`${root}/securities/7203.T/current.json`, {
      updated_at: new Date().toISOString(), price: { close: 100, date: '2026-10-20' },
      statistics: { trailingEps: 10 }, statistics_field_units: { trailingEps: 'jpy_per_share', payoutRatio: 'ratio' }, dividend: {},
    }],
    [`${root}/securities/7203.T/financials/2026-03-31.json`, {
      year: '2026', annual: {
        fiscal_year: '2026', period_end: '2026-03-31', equity_ratio_computed: 20,
        source_values: { seed: { source: 'IRBANK', values: { equity_ratio_computed: 20 }, field_units: { equity_ratio_computed: 'percent' } } },
      },
    }],
  ]);
  for (const [key, definition] of [
    ['stock-analysis-definitions:v1', { version: 1, metric_rules: [], overall_bands: [] }],
    ['watchlist-analysis-definitions:v1', { version: 1 }],
  ]) {
    const prefix = `definitions/v1/${encodeURIComponent(key)}/`;
    documents.set(`${prefix}current.json`, { version: definition.version });
    documents.set(`${prefix}${definition.version}.json`, definition);
  }
  const state = { source: null, snapshots: [], headKeys: [], listCalls: 0, d1Reads: 0, failPersistence: false, beforePut: null };
  const etags = new Map();
  let nextEtag = 0;
  const getEtag = key => {
    if (!etags.has(key)) etags.set(key, `seed-${key}`);
    return etags.get(key);
  };
  const rateLimiter = {
    idFromName: name => name,
    get: () => ({ fetch: async () => Response.json({ allowed: true, count: 1 }) }),
  };
  const env = {
    ADMIN_SECRET: 'test',
    APP_DEFINITIONS: { get: async () => null },
    RATE_LIMITER: rateLimiter,
    MARKET_DATA: {
      head: async key => {
        state.headKeys.push(key);
        return heads.get(key) || (documents.has(key) ? { uploaded: new Date() } : null);
      },
      get: async key => documents.has(key) ? {
        etag: getEtag(key),
        json: async () => documents.get(key),
        text: async () => JSON.stringify(documents.get(key)),
      } : null,
      put: async (key, body, options = {}) => {
        state.beforePut?.(key, options, { documents, etags });
        if (state.failPersistence && key.startsWith('analysis/v1/current/')) throw new Error('persistence failed');
        if (options.onlyIf?.etagDoesNotMatch === '*' && documents.has(key)) return null;
        if (options.onlyIf?.etagMatches !== undefined
          && (!documents.has(key) || getEtag(key) !== options.onlyIf.etagMatches)) return null;
        const value = JSON.parse(body);
        documents.set(key, value);
        const etag = `test-${++nextEtag}`;
        etags.set(key, etag);
        if (key === 'analysis/v1/state/source.json') state.source = value;
        if (key.startsWith('analysis/v1/current/')) state.snapshots.push(value);
        return { etag };
      },
      delete: async keys => { for (const key of Array.isArray(keys) ? keys : [keys]) documents.delete(key); },
      list: async ({ prefix, cursor, limit = 1000 }) => {
        state.listCalls += 1;
        const keys = [...documents.keys()].filter(key => key.startsWith(prefix)).sort();
        const offset = cursor ? Number(cursor) : 0;
        const listed = keys.slice(offset, offset + limit);
        return {
          objects: listed.map(key => ({ key, uploaded: new Date() })),
          truncated: offset + listed.length < keys.length,
          cursor: offset + listed.length < keys.length ? String(offset + listed.length) : undefined,
        };
      },
    },
    DB: {
      prepare(sql) {
        if (sql.startsWith('DELETE FROM support_feedback WHERE created_at <')) {
          return { run: async () => ({ success: true }) };
        }
        state.d1Reads += 1;
        throw new Error('scheduled analysis must not access D1');
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
  return { root, heads, documents, state, env, run };
}

test('Cron configuration separates minute analysis batches from hourly maintenance', () => {
  const activeConfig = readFileSync(new URL('../wrangler.toml', import.meta.url), 'utf8');
  const pausedConfig = readFileSync(new URL('../wrangler.cutover-paused.toml', import.meta.url), 'utf8');
  assert.match(activeConfig, /crons\s*=\s*\["\* \* \* \* \*", "\*\/5 \* \* \* \*", "17 \* \* \* \*"\]/);
  assert.match(pausedConfig, /crons\s*=\s*\[\]/);
  assert.doesNotMatch(activeConfig, /^\[limits\]/m);
  const source = readFileSync(new URL('../src/index.js', import.meta.url), 'utf8');
  assert.match(source, /const ANALYSIS_BATCH_SIZE = 8;/);
  assert.match(source, /const ANALYSIS_RETENTION_BATCH_SIZE = 200;/);
  assert.match(source, /const ANALYSIS_JOB_LEASE_MS = 16 \* 60 \* 1000;/);
  assert.match(source, /const ANALYSIS_RETENTION_LEASE_MS = 16 \* 60 \* 1000;/);
});

test('health reports the latest migrated D1 run from R2 before a current R2 run exists', async () => {
  const f = fixture();
  const runKey = 'operations/v1/runs/analysis/legacy-d1-42/summary.json';
  f.documents.set('operations/v1/runs/legacy-d1/current.json', {
    run_key: runKey,
    run_id: '42',
    run_type: 'analysis',
    started_at: '2026-11-03T01:00:00Z',
  });
  f.documents.set(runKey, {
    run_id: '42',
    run_type: 'analysis',
    started_at: '2026-11-03T01:00:00Z',
    finished_at: '2026-11-03T01:01:00Z',
    status: 'success',
    processed: 12,
    failed: 0,
  });

  const response = await worker.fetch(new Request('https://example.com/api/v1/health'), f.env);
  const body = await response.json();

  assert.equal(response.status, 200);
  assert.deepEqual(body.latest_scheduled_run, {
    started_at: '2026-11-03T01:00:00Z',
    finished_at: '2026-11-03T01:01:00Z',
    status: 'success',
    processed: 12,
    failed: 0,
  });
  assert.equal(f.state.d1Reads, 0);
});

test('health reports the active v2 catalog separately from a stale legacy snapshot', async () => {
  const f = fixture();
  const generation = '2026-11-02-daa434e899b0493a9fdf346067419041';
  f.documents.set(`${f.root}/catalog/current.json`, {
    generation,
    market_data_date: '2026-11-02',
    generated_at: '2026-11-03T05:54:18.165Z',
    items: [
      { code: '7203', price: 3051, quote_date: '2026-11-02' },
      { code: '8306', price: null, quote_date: null },
    ],
  });
  f.heads.set('market/yfinance/latest_market_data.json', {
    uploaded: new Date('2026-10-10T08:31:40.154Z'),
  });

  const response = await worker.fetch(new Request('https://example.com/api/v1/health'), f.env);
  const body = await response.json();

  assert.equal(response.status, 200);
  assert.equal(body.market.available, true);
  assert.equal(body.market.uploaded, '2026-11-03T05:54:18.165Z');
  assert.ok(body.market.age_days < body.market.legacy_snapshot_age_days);
  assert.equal(body.market.v2_catalog.available, true);
  assert.equal(body.market.v2_catalog.generation, generation);
  assert.equal(body.market.v2_catalog.market_data_date, '2026-11-02');
  assert.equal(body.market.v2_catalog.item_count, 2);
  assert.equal(body.market.v2_catalog.quoted_security_count, 1);
  assert.equal(f.state.d1Reads, 0);
});

test('health prefers a newer R2 analysis run over the migrated D1 run pointer', async () => {
  const f = fixture();
  const legacyRunKey = 'operations/v1/runs/analysis/legacy-d1-42/summary.json';
  const currentRunKey = 'operations/v1/runs/analysis/20261104T010000000Z-current/summary.json';
  f.documents.set('operations/v1/runs/legacy-d1/current.json', {
    run_key: legacyRunKey,
    run_id: '42',
    run_type: 'analysis',
    started_at: '2026-11-03T01:00:00Z',
  });
  f.documents.set(legacyRunKey, { started_at: '2026-11-03T01:00:00Z', status: 'success', processed: 12, failed: 0 });
  f.documents.set('operations/v1/runs/analysis/current.json', {
    run_key: currentRunKey,
    run_id: '20261104T010000000Z-current',
    started_at: '2026-11-04T01:00:00Z',
  });
  f.documents.set(currentRunKey, {
    started_at: '2026-11-04T01:00:00Z',
    finished_at: null,
    status: 'running',
    processed: 0,
    failed: 0,
  });

  const response = await worker.fetch(new Request('https://example.com/api/v1/health'), f.env);
  const body = await response.json();

  assert.equal(response.status, 200);
  assert.deepEqual(body.latest_scheduled_run, {
    started_at: '2026-11-04T01:00:00Z',
    finished_at: null,
    status: 'running',
    processed: 0,
    failed: 0,
  });
  assert.equal(f.state.d1Reads, 0);
});

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
  const annual = f.documents.get(`${f.root}/securities/7203.T/financials/2026-03-31.json`).annual;
  annual.equity_ratio_computed = 40;
  annual.source_values.seed.values.equity_ratio_computed = 40;
  assert.equal((await f.run()).source_tag_advanced, true);
  assert.equal(f.state.snapshots.length, 3);
  assert.equal(f.state.snapshots.at(-1).values.equity_ratio, 40);
  assert.equal((await f.run()).source_changed, false);
  assert.deepEqual(new Set(f.state.headKeys.filter(key => key.startsWith(f.root))), new Set([
    `${f.root}/catalog/current.json`, `${f.root}/financial_revision.json`,
  ]));
  assert.equal(f.state.d1Reads, 0);
});

test('scheduled analysis resumes the next catalog batch and advances the source only after completion', async () => {
  const f = fixture();
  const codes = ['7203', ...Array.from({ length: 12 }, (_, index) => String(1000 + index))];
  const catalogKey = `${f.root}/catalog/current.json`;
  f.documents.set(catalogKey, {
    generation: '2026-11-06-multi-batch',
    items: codes.map(code => ({ code, name: `Security ${code}` })),
  });
  f.heads.set(catalogKey, { httpEtag: '"catalog-multi-batch"' });
  for (const code of codes.slice(1)) {
    f.documents.set(`${f.root}/securities/${code}.T/current.json`, {
      updated_at: new Date().toISOString(),
      price: { close: 100, date: '2026-10-20' },
      statistics: { trailingEps: 10 },
      statistics_field_units: { trailingEps: 'jpy_per_share', payoutRatio: 'ratio' },
      dividend: {},
    });
  }

  const first = await f.run();
  assert.equal(first.processed, 8);
  assert.equal(first.source_tag_advanced, false);
  assert.equal(first.next_index, 8);
  assert.equal(f.state.snapshots.length, 8);
  assert.equal(f.documents.get('analysis/v1/state/scheduled-job.json').status, 'running');

  const second = await f.run();
  assert.equal(second.processed, 5);
  assert.equal(second.source_tag_advanced, true);
  assert.equal(second.complete, true);
  assert.equal(f.state.snapshots.length, 13);
  assert.equal(f.documents.get('analysis/v1/state/scheduled-job.json').status, 'complete');
  assert.equal(f.state.d1Reads, 0);
});

test('scheduled analysis detects definition content changes even when its version is unchanged', async () => {
  const f = fixture();
  await f.run();
  const stockKey = `definitions/v1/${encodeURIComponent('stock-analysis-definitions:v1')}/1.json`;
  f.documents.get(stockKey).description = 'Updated content without a version bump';

  const report = await f.run();

  assert.equal(report.source_changed, true);
  assert.equal(report.source_tag_advanced, true);
  assert.equal(f.state.snapshots.length, 2);
});

test('scheduled analysis lease prevents overlapping Cron batches', async () => {
  const f = fixture();
  const originalPut = f.env.MARKET_DATA.put;
  let releaseWrite;
  let signalWrite;
  const writeGate = new Promise(resolve => { releaseWrite = resolve; });
  const writeStarted = new Promise(resolve => { signalWrite = resolve; });
  f.env.MARKET_DATA.put = async (key, body, options) => {
    if (key === 'analysis/v1/current/72030.json') {
      signalWrite();
      await writeGate;
    }
    return originalPut(key, body, options);
  };

  const firstInvocation = f.run();
  try {
    await writeStarted;
    const overlapping = await f.run();
    assert.equal(overlapping.skipped, 'lease_active');
  } finally {
    releaseWrite();
  }
  await firstInvocation;
  assert.equal(f.state.snapshots.length, 1);
});

test('analysis Cron awaits its R2 batch instead of leaving it in waitUntil', async () => {
  const f = fixture();
  const originalPut = f.env.MARKET_DATA.put;
  let releaseRunLog;
  let runLogStarted;
  const runLogGate = new Promise(resolve => { releaseRunLog = resolve; });
  const runLogEntered = new Promise(resolve => { runLogStarted = resolve; });
  f.env.MARKET_DATA.put = async (key, body, options) => {
    if (key.startsWith('operations/v1/runs/analysis/') && key.endsWith('/summary.json')) {
      runLogStarted();
      await runLogGate;
    }
    return originalPut(key, body, options);
  };
  const deferred = [];
  const invocation = worker.scheduled({ cron: '* * * * *', scheduledTime: Date.now() }, f.env, {
    waitUntil: promise => deferred.push(promise),
  });
  let invocationSettled = false;
  const finished = invocation.then(() => { invocationSettled = true; });

  try {
    await runLogEntered;
    await Promise.resolve();
    assert.equal(invocationSettled, false);
    assert.equal(deferred.length, 0);
  } finally {
    releaseRunLog();
  }

  await finished;
  await Promise.allSettled(deferred);
  assert.equal(invocationSettled, true);
  assert.equal(f.state.d1Reads, 0);
});

test('scheduled analysis does not overwrite a newer concurrent R2 snapshot', async () => {
  const f = fixture();
  const key = 'analysis/v1/current/72030.json';
  const newer = { observed_at: '9999-01-01T00:00:00.000Z', code: '7203', values: { per: 99 } };
  f.state.beforePut = (putKey, options, { documents, etags }) => {
    if (putKey !== key || options.onlyIf?.etagDoesNotMatch !== '*') return;
    documents.set(key, newer);
    etags.set(key, 'concurrent-newer-snapshot');
    f.state.beforePut = null;
  };

  const report = await f.run();

  assert.equal(report.failed, 0);
  assert.deepEqual(f.documents.get(key), newer);
});

test('R2 retention keeps recent history, one daily row through 365 days, one monthly row before that, and expires old run logs', async () => {
  const f = fixture();
  const utcNoon = new Date();
  utcNoon.setUTCHours(12, 0, 0, 0);
  const now = utcNoon.getTime();
  const historyKey = (at, suffix) => {
    const date = new Date(at);
    const yyyy = String(date.getUTCFullYear());
    const mm = String(date.getUTCMonth() + 1).padStart(2, '0');
    const stamp = date.toISOString().slice(0, 10).replaceAll('-', '');
    return `analysis/v1/history/72030/${yyyy}/${mm}/${stamp}-${suffix}.json`;
  };
  const putHistory = (key, at) => f.documents.set(key, { observed_at: new Date(at).toISOString() });

  const recent = now - 5 * 86400000;
  const dailyOlder = now - 45 * 86400000;
  const monthlyOlder = now - 400 * 86400000;
  const monthlyOlderLater = monthlyOlder + 2 * 3600000;
  const recentKey = historyKey(recent, 'recent');
  const dailyOldKey = historyKey(dailyOlder, 'daily-old');
  const dailyNewKey = historyKey(dailyOlder + 3600000, 'daily-new');
  const monthlyOldKey = historyKey(monthlyOlder, 'monthly-old');
  const monthlyNewKey = historyKey(monthlyOlderLater, 'monthly-new');
  putHistory(recentKey, recent);
  putHistory(dailyOldKey, dailyOlder);
  putHistory(dailyNewKey, dailyOlder + 3600000);
  putHistory(monthlyOldKey, monthlyOlder);
  putHistory(monthlyNewKey, monthlyOlderLater);

  const expiredRunKey = 'operations/v1/runs/analysis/legacy-d1-11/summary.json';
  const retainedRunKey = 'operations/v1/runs/analysis/legacy-d1-12/summary.json';
  const unfinishedRunKey = 'operations/v1/runs/analysis/legacy-d1-13/summary.json';
  f.documents.set(expiredRunKey, { run_id: '11', started_at: new Date(now - 400 * 86400000).toISOString(), finished_at: new Date(now - 400 * 86400000).toISOString(), status: 'success' });
  f.documents.set(retainedRunKey, { run_id: '12', started_at: new Date(now - 10 * 86400000).toISOString(), finished_at: new Date(now - 10 * 86400000).toISOString(), status: 'success' });
  f.documents.set(unfinishedRunKey, { run_id: '13', started_at: new Date(now - 400 * 86400000).toISOString(), finished_at: null, status: 'running' });

  await f.run();
  await worker.scheduled({ cron: '*/5 * * * *', scheduledTime: Date.now() }, f.env, { waitUntil() {} });
  assert.equal(f.documents.get('operations/v1/retention/analysis.json').phase, 'runs');
  await worker.scheduled({ cron: '*/5 * * * *', scheduledTime: Date.now() + 5 * 60 * 1000 }, f.env, { waitUntil() {} });

  assert.equal(f.documents.has(recentKey), true);
  assert.equal(f.documents.has(dailyOldKey), false);
  assert.equal(f.documents.has(dailyNewKey), true);
  assert.equal(f.documents.has(monthlyOldKey), false);
  assert.equal(f.documents.has(monthlyNewKey), true);
  assert.equal(f.documents.has(expiredRunKey), false);
  assert.equal(f.documents.has(retainedRunKey), true);
  assert.equal(f.documents.has(unfinishedRunKey), true);
  const listCallsAfterCompaction = f.state.listCalls;
  assert.ok(listCallsAfterCompaction >= 2);

  await worker.scheduled({ cron: '*/5 * * * *', scheduledTime: Date.now() + 10 * 60 * 1000 }, f.env, { waitUntil() {} });
  assert.equal(f.state.listCalls, listCallsAfterCompaction, 'the R2 compaction pass is limited to once per day');
});

test('R2 retention advances one bounded history page per invocation and resumes the cursor', async () => {
  const f = fixture();
  const observedAt = Date.now() - 400 * 86400000;
  const date = new Date(observedAt);
  const year = String(date.getUTCFullYear());
  const month = String(date.getUTCMonth() + 1).padStart(2, '0');
  const stamp = date.toISOString().slice(0, 10).replaceAll('-', '');
  for (let index = 0; index < 201; index += 1) {
    const code = 'A' + String(index).padStart(3, '0');
    const key = `analysis/v1/history/${code}/${year}/${month}/${stamp}-row.json`;
    f.documents.set(key, { observed_at: new Date(observedAt).toISOString() });
  }

  await worker.scheduled({ cron: '*/5 * * * *', scheduledTime: Date.now() }, f.env, { waitUntil() {} });
  const firstState = f.documents.get('operations/v1/retention/analysis.json');
  assert.equal(firstState.phase, 'history');
  assert.equal(firstState.history_cursor, '200');
  assert.equal(f.state.listCalls, 1);

  await worker.scheduled({ cron: '*/5 * * * *', scheduledTime: Date.now() + 5 * 60 * 1000 }, f.env, { waitUntil() {} });
  assert.equal(f.documents.get('operations/v1/retention/analysis.json').phase, 'runs');
  assert.equal(f.state.listCalls, 2);

  await worker.scheduled({ cron: '*/5 * * * *', scheduledTime: Date.now() + 10 * 60 * 1000 }, f.env, { waitUntil() {} });
  assert.equal(f.documents.get('operations/v1/retention/analysis.json').phase, 'complete');
  assert.equal(f.state.listCalls, 3);
});

test('scheduled analysis leaves the source tag pending when R2 inputs change mid-run', async () => {
  const f = fixture();
  f.state.beforePut = key => {
    if (key !== 'analysis/v1/current/72030.json') return;
    f.heads.set(`${f.root}/catalog/current.json`, { httpEtag: '"catalog-b"' });
    f.state.beforePut = null;
  };

  const report = await f.run();

  assert.equal(report.failed, 0);
  assert.equal(report.source_tag_advanced, false);
  assert.equal(f.state.source, null);
  const pointer = f.documents.get('operations/v1/runs/analysis/current.json');
  assert.equal(f.documents.get(pointer.run_key).status, 'partial');
});

test('scheduled analysis does not move the current run pointer backward', async () => {
  const f = fixture();
  const key = 'operations/v1/runs/analysis/current.json';
  const newest = { run_id: '9999T010101000Z-newest', run_key: 'operations/v1/runs/analysis/newest/summary.json' };
  f.documents.set(key, newest);

  await f.run();

  assert.deepEqual(f.documents.get(key), newest);
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
  assert.equal((await f.run()).skipped, 'retry_backoff');
  f.state.failPersistence = false;
  const jobKey = 'analysis/v1/state/scheduled-job.json';
  const retryJob = f.documents.get(jobKey);
  retryJob.retry_at = new Date(0).toISOString();
  f.documents.set(jobKey, retryJob);
  assert.equal((await f.run()).source_tag_advanced, true);
  assert.notEqual(f.state.source, previous);
});

test('analysis uses percent payout units and quote date, not financial update time', async () => {
  const f = fixture();
  const current = f.documents.get(`${f.root}/securities/7203.T/current.json`);
  current.price.date = '2020-03-31';
  current.statistics.payoutRatio = 1.5;
  current.updated_at = new Date().toISOString();
  await f.run();
  const snapshot = f.state.snapshots.at(-1);
  assert.equal(snapshot.values.payout_ratio, 150);
  assert.ok(snapshot.anomalies.some(a => a.code === 'PAYOUT_EXTREME_HIGH'));
  assert.equal(snapshot.freshness.market_stale, true);
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
  f.state.source = { source_tag: JSON.stringify({ etag: '"legacy"', stock_version: 1, watch_version: 1 }) };
  f.documents.set('analysis/v1/state/source.json', f.state.source);
  f.heads.delete(`${f.root}/financial_revision.json`);
  assert.equal((await f.run()).source_tag_advanced, true);
  assert.equal((await f.run()).source_changed, false);
  f.heads.set(`${f.root}/financial_revision.json`, { httpEtag: '"first-revision"' });
  assert.equal((await f.run()).source_tag_advanced, true);
  assert.equal(f.state.snapshots.length, 2);
});

test('analysis computes from current R2 inputs on demand and analysis history reads R2 without D1', async () => {
  const f = fixture();
  const analysis = await worker.fetch(new Request('https://example.com/api/v1/analysis/7203'), f.env);
  assert.equal(analysis.status, 200);
  const analysisData = await analysis.json();
  assert.equal(analysisData.canonical_code, '72030');
  assert.equal(analysisData.market_date, '2026-10-20');

  await f.run();
  const history = await worker.fetch(new Request('https://example.com/api/v1/analysis-history/7203'), f.env);
  assert.equal(history.status, 200);
  const data = await history.json();
  assert.equal(data.items.length, 1);
  assert.equal(data.items[0].analysis.canonical_code, '72030');
  assert.equal(f.state.d1Reads, 0);
});

test('versioned R2 definitions take precedence and definition audit is listed from R2', async () => {
  const f = fixture();
  const encoded = encodeURIComponent('stock-analysis-definitions:v1');
  const definition = { version: 3, metric_rules: [], overall_bands: [] };
  f.documents.set(`definitions/v1/${encoded}/current.json`, { version: 3 });
  f.documents.set(`definitions/v1/${encoded}/3.json`, definition);
  const response = await worker.fetch(new Request('https://example.com/api/v1/stock-analysis-definitions'), f.env);
  assert.equal(response.status, 200);
  assert.deepEqual(await response.json(), definition);

  await f.run();
  const audit = await worker.fetch(new Request('https://example.com/api/v1/definition-audit'), f.env);
  assert.equal(audit.status, 200);
  const records = (await audit.json()).items;
  const stockAudit = records.find(record => record.definition_key === 'stock-analysis-definitions:v1' && record.version === 3);
  assert.ok(stockAudit);
  assert.equal(stockAudit.metadata.source, 'r2');
  assert.equal(f.state.d1Reads, 0);
});

test('missing R2 definitions fail closed without falling back to KV or recording empty definitions', async () => {
  const f = fixture();
  let kvReads = 0;
  f.env.APP_DEFINITIONS = { get: async () => { kvReads++; return JSON.stringify([{ title: 'legacy' }]); } };
  const stockPrefix = `definitions/v1/${encodeURIComponent('stock-analysis-definitions:v1')}/`;
  f.documents.delete(`${stockPrefix}current.json`);
  f.documents.delete(`${stockPrefix}1.json`);

  const stock = await worker.fetch(new Request('https://example.com/api/v1/stock-analysis-definitions'), f.env);
  assert.equal(stock.status, 503);
  assert.deepEqual(await stock.json(), { error: 'definitions_unavailable' });
  const diagnosis = await worker.fetch(new Request('https://example.com/api/v1/diagnosis-definitions'), f.env);
  assert.equal(diagnosis.status, 503);
  assert.deepEqual(await diagnosis.json(), { error: 'definitions_unavailable' });
  const analysis = await worker.fetch(new Request('https://example.com/api/v1/analysis/7203'), f.env);
  assert.equal(analysis.status, 503);
  assert.deepEqual(await analysis.json(), { error: 'definitions_unavailable' });
  assert.equal(kvReads, 0);
  assert.equal([...f.documents.keys()].some(key => key.startsWith('audit/v1/definitions/')), false);
  assert.equal(f.state.d1Reads, 0);
});

test('health reports missing analysis definitions instead of treating them as empty', async () => {
  const f = fixture();
  let kvReads = 0;
  f.env.APP_DEFINITIONS = { get: async () => { kvReads++; return JSON.stringify({ version: 'legacy' }); } };
  const stockPrefix = `definitions/v1/${encodeURIComponent('stock-analysis-definitions:v1')}/`;
  f.documents.delete(`${stockPrefix}current.json`);
  f.documents.delete(`${stockPrefix}1.json`);

  const response = await worker.fetch(new Request('https://example.com/api/v1/health'), f.env);

  assert.equal(response.status, 503);
  assert.deepEqual(await response.json(), { error: 'definitions_unavailable' });
  assert.equal(kvReads, 0);
  assert.equal(f.state.d1Reads, 0);
});
