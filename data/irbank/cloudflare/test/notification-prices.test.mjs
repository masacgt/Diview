import test from 'node:test';
import assert from 'node:assert/strict';
import worker from '../src/index.js';

const date = '2026-11-18';
const generation = `${date}-e5051793bf404937a602f71bdb940825`;
const publicationKey = 'market/yfinance/v2/publications/current.json';
const currentKey = 'market/yfinance/v2/securities/7203.T/current.json';

function publicationFor(id = generation) {
  return {
    generation: id,
    market_data_date: date,
    last_completed_at: '2026-11-18T21:03:04.898443Z',
  };
}

function envFor({ documents = new Map(), publication = publicationFor(), publicationSequence = null, rateAllowed = true } = {}) {
  const reads = [];
  const writes = [];
  const rateRequests = [];
  let d1Calls = 0;
  let publicationReads = 0;
  const storedDocuments = new Map(documents);
  if (publication) storedDocuments.set(publicationKey, publication);
  const env = {
    IRBANK_API_KEY: 'must-not-be-used',
    APP_DEFINITIONS: { get: async () => null },
    MARKET_DATA: {
      get: async key => {
        reads.push(key);
        if (key === publicationKey && publicationSequence?.length) {
          const value = publicationSequence[Math.min(publicationReads, publicationSequence.length - 1)];
          publicationReads += 1;
          return { json: async () => structuredClone(value) };
        }
        return storedDocuments.has(key)
          ? { json: async () => structuredClone(storedDocuments.get(key)) }
          : null;
      },
      put: async (key, value) => {
        writes.push({ key, value });
      },
    },
    DB: { prepare: () => { d1Calls += 1; throw new Error('notification prices must not query D1'); } },
    RATE_LIMITER: {
      idFromName: name => name,
      get: id => ({ fetch: async (_url, init) => {
        rateRequests.push({ id, ...JSON.parse(init.body) });
        return Response.json({ allowed: rateAllowed, count: rateAllowed ? 1 : 1000 });
      } }),
    },
  };
  return { env, reads, writes, rateRequests, d1Calls: () => d1Calls };
}

function quoteReads(fixture) {
  return fixture.reads.filter(key => key.startsWith('market/yfinance/v2/securities/'));
}

function post(codes = ['7203'], targetDate = date, runId = null) {
  const body = { codes, market_data_date: targetDate };
  if (runId) body.run_id = runId;
  return new Request('https://example.com/api/v1/notification-prices', {
    method: 'POST',
    headers: { 'content-type': 'application/json', 'CF-Connecting-IP': '203.0.113.25' },
    body: JSON.stringify(body),
  });
}

async function withFetch(fetcher, action) {
  const originalFetch = globalThis.fetch;
  globalThis.fetch = fetcher;
  try { return await action(); } finally { globalThis.fetch = originalFetch; }
}

test('notification price refresh uses the published generation and existing YFinance v2 quote', async () => {
  const fixture = envFor({ documents: new Map([[currentKey, {
    source: 'Yahoo Finance via yfinance',
    updated_at: '2026-11-18T20:52:36.629240Z',
    price: { close: 3391, date },
  }]]) });
  let upstreamCalls = 0;
  const response = await withFetch(async () => {
    upstreamCalls += 1;
    throw new Error('notification refresh must not fetch an external provider');
  }, () => worker.fetch(post(['7203'], '2026-11-19'), fixture.env));

  assert.equal(response.status, 200);
  assert.equal(response.headers.get('cache-control'), 'private, no-store');
  assert.deepEqual(await response.json(), {
    run_id: generation,
    market_data_date: date,
    complete: true,
    items: [{
      code: '7203', price: 3391, quote_date: date,
      attribution: { source_label: 'Yahoo Finance via yfinance' },
    }],
  });
  assert.deepEqual(quoteReads(fixture), [currentKey]);
  assert.equal(fixture.reads.filter(key => key === publicationKey).length, 2);
  assert.deepEqual(fixture.reads.filter(key => key.startsWith('market/irbank/')), []);
  assert.equal(upstreamCalls, 0);
  assert.equal(fixture.writes.length, 0);
  assert.equal(fixture.d1Calls(), 0);
});

test('missing, invalid, and future-dated v2 prices are omitted for local stale-price handling', async () => {
  const fixture = envFor({ documents: new Map([
    [currentKey, { updated_at: '2026-11-18T20:52:36.629240Z', price: { close: 3391, date: '2026-11-19' } }],
    ['market/yfinance/v2/securities/8306.T/current.json', { updated_at: '2026-11-18T20:52:36.629240Z', price: { close: 0, date } }],
  ]) });
  let upstreamCalls = 0;
  const response = await withFetch(async () => {
    upstreamCalls += 1;
    throw new Error('missing v2 prices must not fall back to an external provider');
  }, () => worker.fetch(post(['7203', '8306', '9432']), fixture.env));

  assert.equal(response.status, 200);
  assert.deepEqual((await response.json()).items, []);
  assert.deepEqual(quoteReads(fixture), [
    currentKey,
    'market/yfinance/v2/securities/8306.T/current.json',
    'market/yfinance/v2/securities/9432.T/current.json',
  ]);
  assert.deepEqual(fixture.reads.filter(key => key.startsWith('market/irbank/')), []);
  assert.equal(upstreamCalls, 0);
  assert.equal(fixture.writes.length, 0);
  assert.equal(fixture.d1Calls(), 0);
});

test('notification price requests reject invalid dates, codes, and batches over ten', async () => {
  const badRequests = [
    post(['7203'], '2026-02-30'),
    post(['7203'], date, 'invalid-generation'),
    post(['not-a-code']),
    post(Array.from({ length: 11 }, (_, index) => String(1000 + index))),
    new Request('https://example.com/api/v1/notification-prices', {
      method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify({ codes: ['7203'] }),
    }),
  ];
  for (const request of badRequests) {
    const response = await worker.fetch(request, envFor().env);
    assert.equal(response.status, 400);
    assert.deepEqual(await response.json(), { error: 'invalid_request' });
  }
});

test('a requested run id must match the currently published generation', async () => {
  const fixture = envFor({ documents: new Map([[currentKey, {
    updated_at: '2026-11-18T20:52:36.629240Z', price: { close: 3391, date },
  }]]) });
  const otherGeneration = `${date}-ffeeddccbbaa00998877665544332211`;
  const response = await worker.fetch(post(['7203'], date, otherGeneration), fixture.env);

  assert.equal(response.status, 409);
  assert.deepEqual(await response.json(), { error: 'catalog_generation_mismatch' });
  assert.deepEqual(quoteReads(fixture), []);
  assert.equal(fixture.writes.length, 0);
  assert.equal(fixture.d1Calls(), 0);
});

test('notification price refresh rejects a current record from an unpublished generation', async () => {
  const fixture = envFor({ documents: new Map([[currentKey, {
    updated_at: '2026-11-18T21:04:00.000Z', price: { close: 3391, date },
  }]]) });
  const response = await worker.fetch(post(), fixture.env);

  assert.equal(response.status, 409);
  assert.deepEqual(await response.json(), { error: 'catalog_generation_mismatch' });
  assert.deepEqual(fixture.reads.filter(key => key.startsWith('market/irbank/')), []);
  assert.equal(fixture.writes.length, 0);
  assert.equal(fixture.d1Calls(), 0);
});

test('notification price refresh rejects a publication change while reading one batch', async () => {
  const nextGeneration = `${date}-ffeeddccbbaa00998877665544332211`;
  const fixture = envFor({
    documents: new Map([[currentKey, {
      updated_at: '2026-11-18T20:52:36.629240Z', price: { close: 3391, date },
    }]]),
    publicationSequence: [publicationFor(), publicationFor(nextGeneration)],
  });
  const response = await worker.fetch(post(), fixture.env);

  assert.equal(response.status, 409);
  assert.deepEqual(await response.json(), { error: 'catalog_generation_mismatch' });
  assert.deepEqual(quoteReads(fixture), [currentKey]);
  assert.equal(fixture.writes.length, 0);
  assert.equal(fixture.d1Calls(), 0);
});

test('the public rate limiter rejects before the R2 quote read', async () => {
  const fixture = envFor({ rateAllowed: false });
  const response = await worker.fetch(post(), fixture.env);

  assert.equal(response.status, 429);
  assert.equal(response.headers.get('retry-after'), '60');
  assert.deepEqual(await response.json(), { error: 'rate_limited' });
  assert.deepEqual(quoteReads(fixture), []);
  assert.equal(fixture.writes.length, 0);
  assert.equal(fixture.d1Calls(), 0);
});
