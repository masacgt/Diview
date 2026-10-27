import test from 'node:test';
import assert from 'node:assert/strict';
import worker from '../src/index.js';
import { marketReadFixture } from './fixtures/market-read-fixture.mjs';

test('empty issuer records remain unknown across security, quotes, and search; explicit zero remains no dividend', async () => {
  for (const [fixtureStatus, expected] of [['unknown', 'unknown'], ['no_dividend', 'no_dividend']]) {
    const f = marketReadFixture('8984', fixtureStatus);
    const current = f.documents.get(`${f.base}/current.json`);
    current.dividend = { trailing_12_month_per_share: 6571 };
    const paths = ['/api/v1/securities/8984', '/api/v1/live-quotes?codes=8984', '/api/v1/security-search?q=8984', '/api/v1/search-catalog'];
    for (const path of paths) {
      const response = await worker.fetch(new Request(`https://example.com${path}`), f.env);
      assert.equal(response.status, 200, path);
      const body = await response.json();
      const item = body.items?.[0] ?? body;
      assert.equal(item.dividend_status, expected, path);
      const dps = item.annual_dividend_per_share ?? item.dividend?.annual_dps ?? null;
      assert.equal(dps, fixtureStatus === 'unknown' ? null : 0, path);
    }
  }
});

test('search and live quote use the same recent forward DPS and current price', async () => {
  const f = marketReadFixture('4502');
  const row = f.documents.get('market/yfinance/v2/catalog/current.json').items[0];
  row.security_type = 'equity';
  const current = f.documents.get(`${f.base}/current.json`);
  current.price.close = 6452;
  current.statistics.dividendRate = 204;
  current.statistics_last_fetched_at = new Date().toISOString();
  current.field_status.statistics_dividendRate = 'available';
  for (const path of ['/api/v1/security-search?q=4502', '/api/v1/live-quotes?codes=4502']) {
    const body = await (await worker.fetch(new Request(`https://example.com${path}`), f.env)).json();
    assert.equal(body.items[0].annual_dividend_per_share, 204, path);
    assert.equal(body.items[0].yield_rate, 3.1618, path);
    assert.equal(body.items[0].dividend_source, 'Yahoo Finance via yfinance', path);
    assert.equal(body.items[0].dividend_period, 'forward_12_month_estimate', path);
  }
  current.statistics_last_fetched_at = new Date(Date.now() - 46 * 86400000).toISOString();
  const stale = await (await worker.fetch(new Request('https://example.com/api/v1/security-search?q=4502'), f.env)).json();
  assert.equal(stale.items[0].annual_dividend_per_share, 100);
  assert.equal(stale.items[0].dividend_period, 'issuer_reported_fiscal_year');
});

test('a zero Yahoo estimate does not turn missing issuer data into confirmed no dividend', async () => {
  const f = marketReadFixture('4502', 'unknown');
  f.documents.get('market/yfinance/v2/catalog/current.json').items[0].security_type = 'equity';
  const current = f.documents.get(`${f.base}/current.json`);
  current.statistics.dividendRate = 0;
  current.statistics_last_fetched_at = new Date().toISOString();
  const body = await (await worker.fetch(new Request('https://example.com/api/v1/security-search?q=4502'), f.env)).json();
  assert.equal(body.items[0].dividend_status, 'unknown');
  assert.equal(body.items[0].annual_dividend_per_share, null);
  assert.equal(body.items[0].yield_rate, null);
});

test('REIT search and quotes use disclosed two-period forecasts, not Yahoo estimates', async () => {
  const f = marketReadFixture('8984', 'unknown');
  const row = f.documents.get('market/yfinance/v2/catalog/current.json').items[0];
  row.security_type = 'reit';
  row.price = 110200;
  const current = f.documents.get(`${f.base}/current.json`);
  current.price.close = 110200;
  current.statistics.dividendRate = 6040;
  current.statistics_last_fetched_at = new Date().toISOString();
  const index = f.documents.get('market/yfinance/v2/issuer_dividends/current.json');
  index.items['8984'].forecast = { status: 'available', annual_dps_current_share_basis: 7110,
    period_end: '2099-08-31', first_period_end: '2099-02-28', fiscal_year: 2099,
    amount_basis: 'reit_two_period_forecast_including_excess_distributions' };
  index.items['8984'].forecast_status = 'available';
  for (const path of ['/api/v1/security-search?q=8984', '/api/v1/live-quotes?codes=8984']) {
    const body = await (await worker.fetch(new Request(`https://example.com${path}`), f.env)).json();
    assert.equal(body.items[0].annual_dividend_per_share, 7110, path);
    assert.equal(body.items[0].yield_rate, 6.4519, path);
    assert.equal(body.items[0].dividend_period, 'forward_two_fiscal_periods_forecast', path);
  }
  const security = await (await worker.fetch(new Request('https://example.com/api/v1/securities/8984'), f.env)).json();
  assert.equal(security.dividend.dividend_period, 'forward_two_fiscal_periods_forecast');
  index.items['8984'].forecast.first_period_end = '2000-02-28';
  const body = await (await worker.fetch(new Request('https://example.com/api/v1/security-search?q=8984'), f.env)).json();
  assert.equal(body.items[0].annual_dividend_per_share, null);
  assert.equal(body.items[0].yield_rate, null);
});

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
