import test from 'node:test';
import assert from 'node:assert/strict';
import { validateFeedback, readFeedback, saveFeedback } from '../src/feedback.mjs';

const valid = () => ({ requestId: 'd982c634-a464-4a62-a266-35a1ee8c3821', category: 'stock', name: 'テスト', email: 'test@example.com', message: '銘柄表示を確認します' });
test('accepts Japanese text and optional identity', () => {
  assert.equal(validateFeedback(valid()).message, '銘柄表示を確認します');
  assert.equal(validateFeedback({ ...valid(), name: '', email: '' }).email, '');
});
test('rejects missing, huge and invalid input', () => {
  for (const value of [null, [], {}, { ...valid(), requestId: '../data' }, { ...valid(), category: 'unknown' },
      { ...valid(), email: 'a@invalid' }, { ...valid(), message: ' ' }, { ...valid(), message: 'x'.repeat(20_001) },
      { ...valid(), name: 'x'.repeat(201) }, { ...valid(), name: 'a\nb' }, { ...valid(), message: 'a\0b' }]) assert.equal(validateFeedback(value), null);
});
test('diagnostics only contain consented version fields', () => {
  const result = validateFeedback({ ...valid(), diagnostics: { androidSdk: 35, appVersion: '1.0', holdings: 'secret', serial: 'device-id' } });
  assert.deepEqual(result.diagnostics, { androidSdk: 35, appVersion: '1.0' });
  assert.equal(validateFeedback(valid()).diagnostics, null);
});
test('body reader enforces size, JSON and content type', async () => {
  assert.ok(await readFeedback(new Request('https://example.com', { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify(valid()) })));
  assert.equal(await readFeedback(new Request('https://example.com', { method: 'POST', headers: { 'content-type': 'application/json' }, body: 'x'.repeat(100_001) })), null);
  assert.equal(await readFeedback(new Request('https://example.com', { method: 'POST', body: JSON.stringify(valid()) })), null);
  assert.equal(await readFeedback(new Request('https://example.com', { method: 'POST', headers: { 'content-type': 'application/json' }, body: '{broken' })), null);
});
test('retries produce one stored receipt and conflicting content is rejected', async () => {
  const rows = new Map();
  const db = { prepare: sql => ({ bind: (...values) => ({
    run: async () => { if (!rows.has(values[0])) rows.set(values[0], { request_hash: values[2] }); },
    first: async () => rows.get(values[0])
  }) }) };
  const input = validateFeedback(valid());
  assert.equal(await saveFeedback(db, input), true);
  assert.equal(await saveFeedback(db, input), true);
  assert.equal(rows.size, 1);
  assert.equal(await saveFeedback(db, { ...input, message: 'different' }), false);
});
test('storage errors cannot be reported as accepted', async () => {
  await assert.rejects(saveFeedback({ prepare: () => { throw Error('unavailable'); } }, validateFeedback(valid())));
});
