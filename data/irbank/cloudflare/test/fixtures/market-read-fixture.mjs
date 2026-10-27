export function marketReadFixture(code = '7203', status = 'available', archive = false) {
  const root = 'market/yfinance/v2';
  const base = `${root}/securities/${code}.T`;
  const documents = new Map([
    [`${base}/profile.json`, { code, symbol: `${code}.T`, display_name: '検証銘柄' }],
    [`${base}/current.json`, {
      price: { close: 3200, date: '2026-10-26', currency: 'JPY' },
      statistics: { trailingEps: 200, bookValue: 1000, dividendYield: 0.03 },
      dividend: {}, field_status: { dividend_history: 'available' },
    }],
    [`${base}/sync.json`, { status: 'success' }],
    [`${root}/catalog/current.json`, {
      generation: '2026-10-26', market_data_date: '2026-10-26',
      items: [{ code, name: '検証銘柄', price: 3000, quote_date: '2026-10-26' }],
    }],
    [`${base}/financials/2025.json`, {
      annual: { fiscal_year: '2025/03', period_end: '2025-03-31',
        source_values: { seed: { source: 'IRBANK',
          values: { sales: 1000, operating_profit: 100, equity_ratio_computed: 40 },
          field_units: { sales: 'million_jpy', operating_profit: 'million_jpy', equity_ratio_computed: 'percent' } } } },
    }],
    [`${base}/dividends/2025.json`, { records: [{ date: '2025-03-31', amount: 40 }, { date: '2025-09-30', amount: 60 }] }],
    [`${base}/prices/monthly/2025.json`, { records: [{ month: '2025-12', date: '2025-12-30', close: 2800 }] }],
    [`${root}/issuer_dividends/current.json`, { items: { [code]: {
      code, actual_status: status === 'unknown' ? 'empty' : 'available', forecast_status: 'not_requested', forecast: null,
      actual: status === 'unknown' ? null : { fiscal_year: 2025, period_end: '2025-03-31', annual_dps: status === 'no_dividend' ? 0 : 100 },
    } } }],
    [`${root}/issuer_dividends/shards/current.json`, { schema_version: 1, prefix_length: 2, slot: 'a', prefixes: [code.slice(0, 2)] }],
  ]);
  documents.set(`${root}/issuer_dividends/shards/a/${code.slice(0, 2)}.json`, {
    items: documents.get(`${root}/issuer_dividends/current.json`).items,
  });
  if (archive) documents.set(`${root}/archive/events/${code}.T/dividends/1990.json`, {
    records: [{ date: '1990-06-29', amount: 1 }],
  });
  const reads = [], lists = [];
  const env = {
    APP_DEFINITIONS: { get: async () => null },
    MARKET_DATA: {
      async get(key) {
        reads.push(key);
        return documents.has(key) ? { json: async () => structuredClone(documents.get(key)) } : null;
      },
      async list({ prefix }) {
        lists.push(prefix);
        return { objects: [...documents.keys()].filter(key => key.startsWith(prefix)).map(key => ({ key })), truncated: false };
      },
    },
    DB: { prepare(sql) { return {
      bind() { return this; },
      async first() {
        return sql.includes('FROM securities')
          ? { security_code: code.length === 4 ? `${code}0` : code, security_code_display: code, name: '検証銘柄', metrics_json: '[]' }
          : null;
      },
      async run() { return {}; }, async all() { return { results: [] }; },
    }; } },
  };
  return { env, documents, reads, lists, base };
}
