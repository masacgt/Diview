import test from 'node:test';
import assert from 'node:assert/strict';
import { withMarketReadCache, sharedMarketJson, sharedMarketDocuments } from '../src/market-read-cache.mjs';

test('concurrent and sequential readers share I/O but cannot mutate each other', async () => {
  const env = withMarketReadCache({});
  let reads = 0;
  const load = async () => { reads++; return { rows: [{ price: 100 }] }; };
  const [first, second] = await Promise.all([
    sharedMarketJson(env, 'current', load), sharedMarketJson(env, 'current', load),
  ]);
  first.rows[0].price = 200;
  assert.equal(second.rows[0].price, 100);
  assert.equal((await sharedMarketJson(env, 'current', load)).rows[0].price, 100);
  assert.equal(reads, 1);
});

test('request contexts do not mutate or cache on the reusable environment', async () => {
  const original = Object.freeze({ MARKET_DATA: {} });
  const first = withMarketReadCache(original);
  assert.equal(withMarketReadCache(first), first);
  assert.equal(await sharedMarketJson(first, 'current', async () => 1), 1);
  assert.equal(await sharedMarketJson(withMarketReadCache(original), 'current', async () => 2), 2);
});

test('missing objects share one lookup and failed reads remain retryable', async () => {
  const env = withMarketReadCache({});
  let reads = 0;
  const absent = async () => { reads++; return null; };
  assert.equal(await sharedMarketJson(env, 'missing', absent), null);
  assert.equal(await sharedMarketJson(env, 'missing', absent), null);
  assert.equal(reads, 1);
  await assert.rejects(sharedMarketJson(env, 'broken', async () => { throw Error('offline'); }));
  assert.equal(await sharedMarketJson(env, 'broken', async () => 3), 3);
});

test('document lists share pagination work without sharing mutable arrays', async () => {
  const env = withMarketReadCache({});
  let reads = 0;
  const load = async () => { reads++; return [{ year: 2025 }, { year: 2024 }]; };
  const first = await sharedMarketDocuments(env, 'financials/', load);
  first.sort((a, b) => a.year - b.year);
  assert.equal((await sharedMarketDocuments(env, 'financials/', load))[0].year, 2025);
  assert.equal(reads, 1);
});
