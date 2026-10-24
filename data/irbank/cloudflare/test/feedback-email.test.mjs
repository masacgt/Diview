import test from 'node:test';
import assert from 'node:assert/strict';
import { DatabaseSync } from 'node:sqlite';
import { readFileSync } from 'node:fs';
import { saveFeedback } from '../src/feedback.mjs';
import { deliverFeedbackEmail, retryFeedbackEmails } from '../src/feedback-email.mjs';

const input = () => ({ requestId: crypto.randomUUID(), category: 'bug', name: 'テスト', email: 'reply@example.com', message: '日本語の本文\n次の行', diagnostics: null });
function fixture(t) {
  const sql = new DatabaseSync(':memory:');
  t.after(() => sql.close());
  sql.exec(readFileSync(new URL('../migrations/0001_support_feedback.sql', import.meta.url), 'utf8'));
  const migration = readFileSync(new URL('../migrations/0006_feedback_email.sql', import.meta.url), 'utf8');
  sql.exec(migration);
  sql.exec(migration); // Safe to rerun.
  const DB = { prepare(query) {
    const statement = sql.prepare(query);
    return { bind(...args) { return {
      first: async () => statement.get(...args),
      run: async () => statement.run(...args),
      all: async () => ({ results: statement.all(...args) }),
    }; } };
  } };
  const sent = [];
  const env = { DB, FEEDBACK_EMAIL_FROM: 'noreply@example.org', FEEDBACK_EMAIL_TO: 'admin@example.com', FEEDBACK_EMAIL: { send: async mail => sent.push(mail) } };
  return { sql, env, sent, migration };
}

test('new receipts notify the fixed admin once; duplicate requests and collisions do not enqueue', async t => {
  const { sql, env, sent } = fixture(t);
  const data = input();
  assert.equal(await saveFeedback(env.DB, data), true);
  assert.equal(await saveFeedback(env.DB, data), true);
  assert.equal(await saveFeedback(env.DB, { ...data, message: 'changed' }), false);
  await Promise.all([deliverFeedbackEmail(env, data.requestId), deliverFeedbackEmail(env, data.requestId)]);
  await deliverFeedbackEmail(env, data.requestId);
  assert.equal(sent.length, 1);
  assert.equal(sent[0].to, 'admin@example.com');
  assert.equal(sent[0].replyTo, data.email);
  assert.ok(sent[0].text.includes(data.message));
  assert.ok(sql.prepare('SELECT sent_at FROM support_feedback_email').get().sent_at);
});

test('failed mail preserves receipt and retries after the lease expires', async t => {
  const { sql, env, sent } = fixture(t);
  const data = input();
  await saveFeedback(env.DB, data);
  const send = env.FEEDBACK_EMAIL.send;
  env.FEEDBACK_EMAIL.send = async () => { throw Error('provider rejected'); };
  await deliverFeedbackEmail(env, data.requestId);
  assert.equal(sql.prepare('SELECT count(*) AS n FROM support_feedback').get().n, 1);
  assert.equal(sql.prepare('SELECT sent_at FROM support_feedback_email').get().sent_at, null);
  env.FEEDBACK_EMAIL.send = send;
  await retryFeedbackEmails(env);
  assert.equal(sent.length, 0);
  sql.exec("UPDATE support_feedback_email SET next_attempt_at = '2000-01-01T00:00:00Z'");
  await retryFeedbackEmails(env);
  assert.equal(sent.length, 1);
});

test('migration does not notify old receipts; deletes remove pending notification data', async t => {
  const { sql, env, sent, migration } = fixture(t);
  sql.exec('DROP TRIGGER support_feedback_email_insert');
  const old = input();
  await saveFeedback(env.DB, old);
  sql.exec(migration);
  await retryFeedbackEmails(env);
  assert.equal(sent.length, 0);
  const current = input();
  await saveFeedback(env.DB, current);
  sql.prepare('DELETE FROM support_feedback WHERE receipt_id = ?').run(current.requestId);
  assert.equal(sql.prepare('SELECT count(*) AS n FROM support_feedback_email').get().n, 0);
});

test('missing binding preserves pending mail; expired crash lease recovers and optional reply is omitted', async t => {
  const { sql, env, sent } = fixture(t);
  const data = { ...input(), email: '' };
  await saveFeedback(env.DB, data);
  await deliverFeedbackEmail({ ...env, FEEDBACK_EMAIL: undefined }, data.requestId);
  assert.equal(sql.prepare('SELECT attempts FROM support_feedback_email').get().attempts, 0);
  sql.exec("UPDATE support_feedback_email SET lease_token = 'crashed', next_attempt_at = '2000-01-01T00:00:00Z'");
  await retryFeedbackEmails(env);
  assert.equal(sent.length, 1);
  assert.equal(sent[0].replyTo, undefined);
});
