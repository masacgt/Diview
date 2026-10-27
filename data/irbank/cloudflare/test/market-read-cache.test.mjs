import test from 'node:test';
import assert from 'node:assert/strict';
import { withMarketReadCache, sharedMarketJson, sharedMarketDocuments } from '../src/market-read-cache.mjs';

const currentKey = 'market/yfinance/v2/securities/7203.T/current.json';

test('concurrent and sequential readers share I/O but cannot mutate each other', async () => {
  const env = withMarketReadCache({});
  let reads = 0;
  const load = async () => { reads++; return { rows: [{ price: 100 }] }; };
  const [first, second] = await Promise.all([
    sharedMarketJson(env, currentKey, load), sharedMarketJson(env, currentKey, load),
  ]);
  first.rows[0].price = 200;
  assert.equal(second.rows[0].price, 100);
  assert.equal((await sharedMarketJson(env, currentKey, load)).rows[0].price, 100);
  assert.equal(reads, 1);
});

test('request contexts do not mutate or cache on the reusable environment', async () => {
  const original = Object.freeze({ MARKET_DATA: {} });
  const first = withMarketReadCache(original);
  assert.equal(withMarketReadCache(first), first);
  assert.equal(await sharedMarketJson(first, currentKey, async () => 1), 1);
  assert.equal(await sharedMarketJson(withMarketReadCache(original), currentKey, async () => 2), 2);
});

test('missing objects share one lookup and failed reads remain retryable', async () => {
  const env = withMarketReadCache({});
  let reads = 0;
  const absent = async () => { reads++; return null; };
  assert.equal(await sharedMarketJson(env, currentKey, absent), null);
  assert.equal(await sharedMarketJson(env, currentKey, absent), null);
  assert.equal(reads, 1);
  const profileKey = currentKey.replace('current.json', 'profile.json');
  await assert.rejects(sharedMarketJson(env, profileKey, async () => { throw Error('offline'); }));
  assert.equal(await sharedMarketJson(env, profileKey, async () => 3), 3);
});

test('single-use document lists keep independent arrays without retaining whole histories', async () => {
  const env = withMarketReadCache({});
  let reads = 0;
  const load = async () => { reads++; return [{ year: 2025 }, { year: 2024 }]; };
  const first = await sharedMarketDocuments(env, 'financials/', load);
  first.sort((a, b) => a.year - b.year);
  assert.equal((await sharedMarketDocuments(env, 'financials/', load))[0].year, 2025);
  assert.equal(reads, 2);
});

test('full issuer indexes are neither cloned nor retained by the request cache', async () => {
  const env = withMarketReadCache({});
  const document = { items: Object.fromEntries(Array.from({ length: 4451 }, (_, index) => [String(index), {
    actual: { fiscal_year: 2026, annual_dps: 100 }, forecast: { fiscal_year: 2027, annual_dps: 110 },
  }])) };
  let reads = 0;
  const load = async () => { reads++; return document; };
  const key = 'market/yfinance/v2/issuer_dividends/current.json';
  assert.equal(await sharedMarketJson(env, key, load), document);
  assert.equal(await sharedMarketJson(env, key, load), document);
  assert.equal(reads, 2);
});
