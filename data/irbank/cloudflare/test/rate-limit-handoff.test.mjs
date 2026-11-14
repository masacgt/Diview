import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { DatabaseSync } from 'node:sqlite';

const fenceSql = readFileSync(new URL('../operations/rate-limit-handoff-fence.sql', import.meta.url), 'utf8');
const abortSql = readFileSync(new URL('../operations/rate-limit-handoff-abort-before-do-cutover.sql', import.meta.url), 'utf8');

function createDatabase() {
  const db = new DatabaseSync(':memory:');
  db.exec(`
    CREATE TABLE api_request_log (
      bucket_key TEXT PRIMARY KEY NOT NULL,
      request_count INTEGER NOT NULL DEFAULT 0,
      window_started_at TEXT NOT NULL
    );
    INSERT INTO api_request_log(bucket_key, request_count, window_started_at)
    VALUES ('rl:analysis:hash', 4, '2026-11-04T00:00:00Z');
  `);
  return db;
}

const legacyUpsert = db => db.prepare(`
  INSERT INTO api_request_log(bucket_key, request_count, window_started_at)
  VALUES (?, 1, ?)
  ON CONFLICT(bucket_key) DO UPDATE SET request_count = api_request_log.request_count + 1
`).run('rl:analysis:hash', '2026-11-04T00:00:30Z');

test('handoff fence keeps reads available and blocks all legacy counter mutations', t => {
  const db = createDatabase();
  t.after(() => db.close());

  db.exec(fenceSql);

  const triggers = db.prepare(`
    SELECT name FROM sqlite_schema
    WHERE type = 'trigger' AND name LIKE 'api_request_log_handoff_block_%'
    ORDER BY name
  `).all().map(row => row.name);
  assert.deepEqual(triggers, [
    'api_request_log_handoff_block_delete',
    'api_request_log_handoff_block_insert',
    'api_request_log_handoff_block_update',
  ]);
  assert.deepEqual(
    { ...db.prepare('SELECT request_count, window_started_at FROM api_request_log WHERE bucket_key = ?')
      .get('rl:analysis:hash') },
    { request_count: 4, window_started_at: '2026-11-04T00:00:00Z' },
  );
  assert.throws(() => legacyUpsert(db), /rate_limit_handoff_fenced/);
  assert.throws(() => db.prepare('UPDATE api_request_log SET request_count = 99').run(), /rate_limit_handoff_fenced/);
  assert.throws(() => db.prepare('DELETE FROM api_request_log').run(), /rate_limit_handoff_fenced/);
  assert.equal(db.prepare('SELECT request_count FROM api_request_log').get().request_count, 4);
});

test('pre-DO abort script re-enables D1 writes before DO traffic begins', t => {
  const db = createDatabase();
  t.after(() => db.close());

  db.exec(fenceSql);
  db.exec(abortSql);

  assert.doesNotThrow(() => legacyUpsert(db));
  assert.equal(db.prepare('SELECT request_count FROM api_request_log').get().request_count, 5);
});
