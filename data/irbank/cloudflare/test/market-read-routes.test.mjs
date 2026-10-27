import test from 'node:test';
import assert from 'node:assert/strict';
import worker from '../src/index.js';
import { marketReadFixture } from './fixtures/market-read-fixture.mjs';

for (const code of ['7203', '25935', '285A']) {
  test(`analysis reads each market object once and sees updates on the next request: ${code}`, async () => {
    const f = marketReadFixture(code);
    const request = () => new Request(`https://example.com/api/v1/analysis/${code}`);
    const response = await worker.fetch(request(), f.env);
    assert.equal(response.status, 200);
    const data = await response.json();
    assert.equal(data.values.per, 16);
    assert.equal(data.values.pbr, 3.2);
    for (const file of ['profile.json', 'current.json']) {
      assert.equal(f.reads.filter(key => key === `${f.base}/${file}`).length, 1);
    }
    f.documents.get(`${f.base}/current.json`).price.close = 4000;
    const updated = await (await worker.fetch(request(), f.env)).json();
    assert.equal(updated.values.per, 20);
    assert.equal(f.reads.filter(key => key === `${f.base}/current.json`).length, 2);
  });
}

test('explicit history-scan opt-out preserves all security fields except the omitted bounds', async () => {
  const full = marketReadFixture('7203', 'available', true);
  const lean = marketReadFixture('7203', 'available', true);
  const before = await (await worker.fetch(new Request('https://example.com/api/v1/securities/7203'), full.env)).json();
  const after = await (await worker.fetch(new Request('https://example.com/api/v1/securities/7203?include_history_bounds=false'), lean.env)).json();
  assert.equal(before.data_retrieval.dividend_history.stored_history_start, '1990-06-29');
  assert.equal(after.data_retrieval.dividend_history.stored_history_status, 'not_requested');
  for (const data of [before, after]) {
    for (const key of ['stored_history_status', 'stored_history_start', 'stored_history_end']) {
      delete data.data_retrieval.dividend_history[key];
    }
  }
  assert.deepEqual(after, before);
  assert.equal(full.lists.length, 2);
  assert.equal(lean.lists.length, 0);
  assert.ok(lean.reads.length < full.reads.length);
});
