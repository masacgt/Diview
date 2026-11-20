import test from 'node:test';
import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { DatabaseSync } from 'node:sqlite';
import worker, { RateLimitCounter } from '../src/index.js';

test('analysis route preserves its shared public bucket, configured limits, and rejection response', async () => {
  let bucketName = null;
  let limiterRequest = null;
  let d1Calls = 0;
  const env = {
    APP_DEFINITIONS: { get: async () => null },
    MARKET_DATA: { get: async () => null },
    DB: { prepare: () => { d1Calls += 1; throw new Error('Analysis must not query D1'); } },
    RATE_LIMITER: {
      idFromName: name => { bucketName = name; return name; },
      get: id => ({ fetch: async (_url, init) => {
        assert.equal(id, bucketName);
        limiterRequest = JSON.parse(init.body);
        return Response.json({ allowed: false, count: limiterRequest.limit + 1 });
      } }),
    },
  };
  const request = new Request('https://example.com/api/v1/analysis/7203', {
    headers: { 'CF-Connecting-IP': '203.0.113.7' },
  });

  const response = await worker.fetch(request, env);

  const ipHash = createHash('sha256').update('203.0.113.7').digest('hex');
  assert.equal(bucketName, `rl:public:analysis:${ipHash}`);
  assert.equal(limiterRequest.limit, 300);
  assert.equal(limiterRequest.window_seconds, 60);
  assert.ok(Number.isInteger(limiterRequest.now_seconds));
  assert.equal(response.status, 429);
  assert.equal(response.headers.get('retry-after'), '60');
  assert.deepEqual(await response.json(), { error: 'rate_limited' });
  assert.equal(d1Calls, 0);
});

test('feedback and J-Quants routes preserve their dedicated shared buckets and limits', async () => {
  const ip = '203.0.113.8';
  const ipHash = createHash('sha256').update(ip).digest('hex');
  const cases = [
    {
      request: new Request('https://example.com/api/v1/feedback', {
        method: 'POST',
        headers: { 'CF-Connecting-IP': ip },
      }),
      route: 'support-feedback',
      limit: 5,
    },
    {
      request: new Request('https://example.com/api/v1/jquants/daily-quotes', {
        headers: { 'CF-Connecting-IP': ip },
      }),
      route: 'public:jquants-daily-quotes',
      limit: 5,
    },
  ];

  for (const item of cases) {
    let bucketName = null;
    let limiterRequest = null;
    let d1Calls = 0;
    const env = {
      APP_DEFINITIONS: { get: async () => null },
      MARKET_DATA: { get: async () => null },
      DB: { prepare: () => { d1Calls += 1; throw new Error('Rate-limited routes must not query D1'); } },
      RATE_LIMITER: {
        idFromName: name => { bucketName = name; return name; },
        get: id => ({ fetch: async (_url, init) => {
          assert.equal(id, bucketName);
          limiterRequest = JSON.parse(init.body);
          return Response.json({ allowed: false, count: item.limit + 1 });
        } }),
      },
    };

    const response = await worker.fetch(item.request, env);

    assert.equal(bucketName, `rl:${item.route}:${ipHash}`);
    assert.equal(limiterRequest.limit, item.limit);
    assert.equal(limiterRequest.window_seconds, 60);
    assert.equal(response.status, 429);
    assert.equal(response.headers.get('retry-after'), '60');
    assert.deepEqual(await response.json(), { error: 'rate_limited' });
    assert.equal(d1Calls, 0);
  }
});

function counterFixture(legacy = null, importUntil = '9999999999') {
  const db = new DatabaseSync(':memory:');
  let legacyReads = 0;
  let alarmCalls = 0;
  let scheduledAlarm = null;
  let rowsRead = 0;
  let rowsWritten = 0;
  const sql = {
    exec(statement, ...params) {
      if (/^\s*CREATE\s/i.test(statement)) {
        db.exec(statement);
        return [];
      }
      if (/^\s*SELECT\s/i.test(statement)) {
        const rows = db.prepare(statement).all(...params);
        rowsRead += rows.length;
        return rows;
      }
      if (/\bRETURNING\b/i.test(statement)) {
        const rows = db.prepare(statement).all(...params);
        rowsWritten += rows.length;
        return rows;
      }
      const result = db.prepare(statement).run(...params);
      if (/^\s*(?:INSERT|UPDATE|DELETE)\s/i.test(statement)) rowsWritten += result.changes;
      return result;
    },
  };
  const storage = {
    sql,
    transactionSync(callback) {
      db.exec('BEGIN');
      try {
        const result = callback();
        db.exec('COMMIT');
        return result;
      } catch (error) {
        db.exec('ROLLBACK');
        throw error;
      }
    },
    async getAlarm() { return scheduledAlarm; },
    async setAlarm(timestamp) {
      alarmCalls += 1;
      scheduledAlarm = timestamp;
    },
  };
  const env = {
    RATE_LIMITER_D1_FENCE_AT: '1000',
    RATE_LIMITER_D1_IMPORT_UNTIL: String(importUntil),
    DB: {
      prepare(statement) {
        assert.match(statement, /SELECT request_count, window_started_at FROM api_request_log/);
        return { bind: key => ({ first: async () => {
          legacyReads += 1;
          return legacy?.bucket_key === key ? legacy : null;
        } }) };
      },
    },
  };
  const counter = new RateLimitCounter({ storage }, env);
  return {
    counter,
    db,
    env,
    storage,
    get legacyReads() { return legacyReads; },
    get alarmCalls() { return alarmCalls; },
    get scheduledAlarm() { return scheduledAlarm; },
    get rowsRead() { return rowsRead; },
    get rowsWritten() { return rowsWritten; },
    async fireAlarm() {
      scheduledAlarm = null;
      await counter.alarm();
    },
  };
}

function hit(counter, nowSeconds, limit = 5, windowSeconds = 60) {
  return counter.fetch(new Request('https://rate-limit.internal/counter', {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ bucket_key: 'rl:analysis:hash', now_seconds: nowSeconds, limit, window_seconds: windowSeconds }),
  })).then(response => response.json());
}

test('first Durable Object request carries forward an active D1 counter without a D1 write', async t => {
  const f = counterFixture({ bucket_key: 'rl:analysis:hash', request_count: 4, window_started_at: '2026-11-03T12:00:00Z' });
  t.after(() => f.db.close());
  const now = Math.floor(Date.parse('2026-11-03T12:00:30Z') / 1000);
  assert.deepEqual(await hit(f.counter, now), { allowed: true, count: 5, window_started_at: now - 30 });
  assert.deepEqual(await hit(f.counter, now), { allowed: false, count: 6, window_started_at: now - 30 });
  assert.equal(f.legacyReads, 1);
  assert.equal(f.alarmCalls, 1);
  assert.equal(f.scheduledAlarm, (now + 30) * 1000);
  assert.equal(f.rowsRead, 1);
  assert.equal(f.rowsWritten, 3);
});

test('a transient failure during first-use D1 import does not poison the Durable Object', async t => {
  const f = counterFixture();
  t.after(() => f.db.close());
  const legacy = { bucket_key: 'rl:analysis:hash', request_count: 4, window_started_at: '2026-11-04T00:00:00Z' };
  let legacyReads = 0;
  f.env.DB = {
    prepare(statement) {
      assert.match(statement, /SELECT request_count, window_started_at FROM api_request_log/);
      return { bind: key => ({ first: async () => {
        legacyReads += 1;
        if (legacyReads === 1) throw new Error('temporary D1 read failure');
        assert.equal(key, legacy.bucket_key);
        return legacy;
      } }) };
    },
  };
  const now = Math.floor(Date.parse('2026-11-04T00:00:30Z') / 1000);

  await assert.rejects(hit(f.counter, now), /temporary D1 read failure/);
  assert.equal(legacyReads, 1);
  assert.equal(f.db.prepare('SELECT COUNT(*) AS total FROM rate_limit_state').get().total, 0);

  assert.deepEqual(await hit(f.counter, now), { allowed: true, count: 5, window_started_at: now - 30 });
  assert.equal(legacyReads, 2);
  assert.equal(f.alarmCalls, 1);
  assert.equal(f.scheduledAlarm, (now + 30) * 1000);
  assert.equal(f.rowsWritten, 2);
});

test('a Durable Object skips legacy D1 reads after the configured handoff deadline', async t => {
  const now = 1800000000;
  const f = counterFixture({
    bucket_key: 'rl:analysis:hash',
    request_count: 4,
    window_started_at: new Date((now - 120) * 1000).toISOString(),
  }, String(now - 1));
  f.env.RATE_LIMITER_D1_FENCE_AT = String(now - 120);
  t.after(() => f.db.close());

  assert.deepEqual(await hit(f.counter, now), { allowed: true, count: 1, window_started_at: now });
  assert.equal(f.legacyReads, 0);
  assert.equal(f.db.prepare('SELECT COUNT(*) AS total FROM rate_limit_legacy_seed').get().total, 1);
});

test('a D1-backed Durable Object fails closed when the handoff deadline is missing', async t => {
  const f = counterFixture();
  t.after(() => f.db.close());
  delete f.env.RATE_LIMITER_D1_IMPORT_UNTIL;

  await assert.rejects(hit(f.counter, 1800000000), /RATE_LIMITER_D1_IMPORT_UNTIL/);
  assert.equal(f.legacyReads, 0);
  assert.equal(f.db.prepare('SELECT COUNT(*) AS total FROM rate_limit_state').get().total, 0);
  assert.equal(f.db.prepare('SELECT COUNT(*) AS total FROM rate_limit_legacy_seed').get().total, 0);
});

test('a handoff deadline shorter than the configured window fails closed', async t => {
  const now = 1800000000;
  const f = counterFixture(null, String(now + 30));
  f.env.RATE_LIMITER_D1_FENCE_AT = String(now - 29);
  t.after(() => f.db.close());

  await assert.rejects(hit(f.counter, now), /must cover a full window/);
  assert.equal(f.legacyReads, 0);
  assert.equal(f.db.prepare('SELECT COUNT(*) AS total FROM rate_limit_state').get().total, 0);
});

test('a future D1 fence timestamp fails closed', async t => {
  const now = 1800000000;
  const f = counterFixture(null, String(now + 180));
  f.env.RATE_LIMITER_D1_FENCE_AT = String(now + 1);
  t.after(() => f.db.close());

  await assert.rejects(hit(f.counter, now), /FENCE_AT is in the future/);
  assert.equal(f.legacyReads, 0);
  assert.equal(f.db.prepare('SELECT COUNT(*) AS total FROM rate_limit_state').get().total, 0);
});

test('a future D1 fence also fails closed for an initialized bucket', async t => {
  const now = 1800000000;
  const f = counterFixture();
  t.after(() => f.db.close());

  assert.deepEqual(await hit(f.counter, now), { allowed: true, count: 1, window_started_at: now });
  f.env.RATE_LIMITER_D1_FENCE_AT = String(now + 1);
  await assert.rejects(hit(f.counter, now), /FENCE_AT is in the future/);
  assert.equal(f.legacyReads, 1);
  assert.equal(f.db.prepare('SELECT request_count FROM rate_limit_state WHERE id = 1').get().request_count, 1);
});

test('a future D1 fence also fails closed after an imported counter row was cleaned up', async t => {
  const now = 1800000000;
  const f = counterFixture();
  t.after(() => f.db.close());

  await hit(f.counter, now);
  f.db.prepare('DELETE FROM rate_limit_state WHERE id = 1').run();
  f.env.RATE_LIMITER_D1_FENCE_AT = String(now + 1);
  await assert.rejects(hit(f.counter, now), /FENCE_AT is in the future/);
  assert.equal(f.legacyReads, 1);
  assert.equal(f.db.prepare('SELECT COUNT(*) AS total FROM rate_limit_legacy_seed').get().total, 1);
  assert.equal(f.db.prepare('SELECT COUNT(*) AS total FROM rate_limit_state').get().total, 0);
});

test('Durable Object counters are serialized and reset exactly at the window boundary', async t => {
  const f = counterFixture();
  t.after(() => f.db.close());
  const results = await Promise.all(Array.from({ length: 10 }, () => hit(f.counter, 1000, 20, 60)));
  assert.deepEqual(results.map(result => result.count).sort((a, b) => a - b), Array.from({ length: 10 }, (_, i) => i + 1));
  assert.equal(f.legacyReads, 1);
  assert.equal(f.rowsRead, 9); // Only competing first-use requests reread after losing the insert race.
  assert.equal(f.rowsWritten, 11);
  assert.equal(f.alarmCalls, 1);
  assert.equal(f.scheduledAlarm, (1060) * 1000);
  assert.equal((await hit(f.counter, 1059, 20, 60)).count, 11);
  assert.deepEqual(await hit(f.counter, 1060, 20, 60), { allowed: true, count: 1, window_started_at: 1060 });
  assert.equal(f.rowsRead, 11);
  assert.equal(f.rowsWritten, 13);
  assert.equal(f.alarmCalls, 2);
  assert.equal(f.scheduledAlarm, (1120) * 1000);
});

test('expired counter lazily resets after object restart without another D1 lookup', async t => {
  const f = counterFixture();
  t.after(() => f.db.close());
  const now = Math.floor(Date.now() / 1000);
  await hit(f.counter, now - 120, 5, 60);
  // Reconstruct the actor as Cloudflare can between requests; it resets on the next hit.
  const restarted = new RateLimitCounter({ storage: f.storage }, f.env);
  assert.deepEqual(await hit(restarted, now, 5, 60), { allowed: true, count: 1, window_started_at: now });
  assert.equal(f.legacyReads, 1);
  assert.equal(f.rowsRead, 1);
  assert.equal(f.rowsWritten, 3);
  assert.equal(f.alarmCalls, 2);
  assert.equal(f.scheduledAlarm, (now + 60) * 1000);
});

test('counter alarm preserves active windows, deletes expired rows, and keeps the one-time D1 seed marker', async t => {
  const now = Math.floor(Date.now() / 1000);
  const f = counterFixture({
    bucket_key: 'rl:analysis:hash',
    request_count: 2,
    window_started_at: new Date((now - 10) * 1000).toISOString(),
  });
  t.after(() => f.db.close());

  assert.deepEqual(await hit(f.counter, now, 5, 60), { allowed: true, count: 3, window_started_at: now - 10 });
  assert.equal(f.legacyReads, 1);
  assert.equal(f.scheduledAlarm, (now + 50) * 1000);

  // A delayed or early alarm must keep a counter whose window is still active.
  await f.fireAlarm();
  assert.equal(f.db.prepare('SELECT COUNT(*) AS total FROM rate_limit_state').get().total, 1);
  assert.equal(f.scheduledAlarm, (now + 50) * 1000);

  f.db.prepare('UPDATE rate_limit_state SET window_started_at = ?').run(now - 120);
  f.db.prepare('DELETE FROM rate_limit_legacy_seed').run(); // Simulate an actor created before the persistent marker existed.
  await f.fireAlarm();
  assert.equal(f.db.prepare('SELECT COUNT(*) AS total FROM rate_limit_state').get().total, 0);
  assert.equal(f.db.prepare('SELECT COUNT(*) AS total FROM rate_limit_legacy_seed').get().total, 1);
  assert.equal(f.scheduledAlarm, null);

  const restarted = new RateLimitCounter({ storage: f.storage }, f.env);
  assert.deepEqual(await hit(restarted, now + 61, 5, 60), { allowed: true, count: 1, window_started_at: now + 61 });
  assert.equal(f.legacyReads, 1);
  assert.equal(f.db.prepare('SELECT COUNT(*) AS total FROM rate_limit_state').get().total, 1);
  assert.equal(f.db.prepare('SELECT COUNT(*) AS total FROM rate_limit_legacy_seed').get().total, 1);
  assert.equal(f.rowsWritten, 5);
});
