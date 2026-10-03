const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');
const output = path.join(root, 'organized', 'by_security');
const organized = path.join(root, 'organized');
const types = ['annual', 'quarterly', 'semiannual'];
const securities = JSON.parse(fs.readFileSync(path.join(root, 'catalog', 'securities.json'), 'utf8'));
const byEdinet = new Map(securities.map((s) => [s.edinet_code, s]));
const companies = new Map(securities.map((s) => [s.security_code, {
  ...s,
  financials: { annual: [], quarterly: [], semiannual: [] },
  dividends: [],
  valuation: null,
}]));
const sourceMeta = { financials: {}, attribution: {}, valuation: {}, dividends: {
  fields: [
    { key: 'annual_dps', label: '1株当たり年間配当(報告時実額)', unit: 'jpy_per_share' },
    { key: 'annual_dps_adjusted', label: '1株当たり年間配当(分割調整後)', unit: 'jpy_per_share' },
    { key: 'record_date', label: '配当基準日', unit: 'date' },
    { key: 'final_rights_date', label: '権利付最終日', unit: 'date' },
  ],
  date_note: '基準日・権利付最終日は現在の保存データに含まれないため null。推定値は登録しない。',
} };
const seenPeriods = new Map();

for (const type of types) {
  const dir = path.join(root, 'raw', 'financials', type);
  const files = fs.readdirSync(dir).filter((name) => name.endsWith('.json')).sort();
  const seen = new Set();
  let rows = 0;
  for (const file of files) {
    const batch = JSON.parse(fs.readFileSync(path.join(dir, file), 'utf8'));
    sourceMeta.financials[type] ??= batch.fields;
    sourceMeta.attribution.financials ??= batch.attribution;
    for (const result of batch.results ?? []) {
      seen.add(result.edinet_code);
      const security = byEdinet.get(result.edinet_code);
      if (!security) continue;
      const company = companies.get(security.security_code);
      const periods = result.periods ?? [];
      const existing = new Map(company.financials[type].map((period) => [`${period.fiscal_year}\0${period.quarter ?? ''}\0${period.accounting_standard ?? ''}\0${period.is_consolidated ?? ''}`, period]));
      for (const period of periods) {
        const key = `${period.fiscal_year}\0${period.quarter ?? ''}\0${period.accounting_standard ?? ''}\0${period.is_consolidated ?? ''}`;
        if (existing.has(key)) {
          if (JSON.stringify(existing.get(key)) !== JSON.stringify(period)) throw new Error(`Conflicting period: ${result.edinet_code} ${type} ${period.fiscal_year} (${file})`);
          continue;
        }
        existing.set(key, period);
        company.financials[type].push(period);
      }
      rows += company.financials[type].length;
    }
  }
  for (const company of companies.values()) company.financials[type].sort((a, b) => a.fiscal_year.localeCompare(b.fiscal_year));
  seenPeriods.set(type, { source_files: files.length, edinet_codes_with_response: seen.size, periods: rows });
}

const valuationDir = path.join(root, 'raw', 'valuation');
let valuationFiles = 0;
for (const file of fs.readdirSync(valuationDir).filter((name) => name.endsWith('.json')).sort()) {
  const code = path.basename(file, '.json');
  const valuation = JSON.parse(fs.readFileSync(path.join(valuationDir, file), 'utf8'));
  sourceMeta.valuation.fields ??= valuation.fields;
  sourceMeta.attribution.valuation ??= valuation.attribution;
  const security = byEdinet.get(code);
  if (!security) continue;
  companies.get(security.security_code).valuation = {
    history: valuation.history ?? [],
    per_5y_avg: valuation.per_5y_avg ?? null,
  };
  for (const row of valuation.history ?? []) {
    if (row.dps_adjusted == null) continue;
    const fiscalYear = String(row.fiscal_year ?? '').slice(0, 4);
    if (!/^\d{4}$/.test(fiscalYear)) continue;
    companies.get(security.security_code).dividends.push({
      fiscal_year: Number(fiscalYear), period: row.fiscal_year, event_type: 'annual_reported',
      annual_dps: null, annual_dps_adjusted: row.dps_adjusted,
      record_date: null, final_rights_date: null,
      date_status: 'unavailable_in_current_snapshot', source: 'IRBANK valuation history',
    });
  }
  valuationFiles++;
}

const dividendSamplePath = path.join(root, 'raw', 'samples', 'dividend_13010.json');
if (fs.existsSync(dividendSamplePath)) {
  const sample = JSON.parse(fs.readFileSync(dividendSamplePath, 'utf8'));
  const company = companies.get(sample.security_code);
  if (company) {
    sourceMeta.attribution.dividends = sample.attribution;
    for (const row of sample.records ?? []) {
      const year = Number(row.fiscal_year);
      const existing = company.dividends.find((item) => item.fiscal_year === year && item.event_type === 'annual_reported');
      const enriched = {
        fiscal_year: year, period: existing?.period ?? String(year), event_type: 'annual_reported',
        annual_dps: row.annual_dps ?? null,
        annual_dps_adjusted: row.annual_dps_adjusted ?? existing?.annual_dps_adjusted ?? null,
        record_date: null, final_rights_date: null, date_status: 'unavailable_in_current_snapshot',
        payout_ratio: row.payout_ratio ?? null, change_type: row.change_type ?? null,
        source_refs: row.source_refs ?? [], source: 'IRBANK dividend history sample',
      };
      if (existing) Object.assign(existing, enriched);
      else company.dividends.push(enriched);
    }
    company.dividends.sort((a, b) => a.fiscal_year - b.fiscal_year);
  }
}

const dividendsDir = path.join(root, 'raw', 'dividends');
if (fs.existsSync(dividendsDir)) {
  const dividendFiles = fs.readdirSync(dividendsDir).filter((name) => name.endsWith('.json')).sort();
  for (const file of dividendFiles) {
    const payload = JSON.parse(fs.readFileSync(path.join(dividendsDir, file), 'utf8'));
    const company = companies.get(payload.security_code);
    if (!company) continue;
    sourceMeta.attribution.dividends ??= payload.attribution;
    for (const row of payload.records ?? []) {
      const year = Number(row.fiscal_year);
      if (!Number.isFinite(year)) continue;
      const existing = company.dividends.find((item) => item.fiscal_year === year && item.event_type === 'annual_reported');
      const enriched = {
        fiscal_year: year,
        period: existing?.period ?? String(year),
        event_type: 'annual_reported',
        annual_dps: row.annual_dps ?? existing?.annual_dps ?? null,
        annual_dps_adjusted: row.annual_dps_adjusted ?? existing?.annual_dps_adjusted ?? null,
        record_date: existing?.record_date ?? null,
        final_rights_date: existing?.final_rights_date ?? null,
        date_status: existing?.date_status ?? 'unavailable_in_current_snapshot',
        payout_ratio: row.payout_ratio ?? null,
        doe: row.doe ?? null,
        net_asset_dividend_rate: row.net_asset_dividend_rate ?? null,
        consecutive_dividend_increase_years: row.consecutive_dividend_increase_years ?? null,
        change_type: row.change_type ?? null,
        source_refs: row.source_refs ?? [],
        source: 'IRBANK dividend history',
      };
      if (existing) Object.assign(existing, enriched);
      else company.dividends.push(enriched);
    }
    company.dividends.sort((a, b) => a.fiscal_year - b.fiscal_year);
  }
}

fs.mkdirSync(output, { recursive: true });
const db = path.join(organized, 'd1');
fs.mkdirSync(db, { recursive: true });
const csvCell = (value) => {
  const text = value == null ? '' : typeof value === 'object' ? JSON.stringify(value) : String(value);
  return `"${text.replaceAll('"', '""')}"`;
};
const securitiesCsv = ['security_code,security_code_display,edinet_code,name,metrics_json', ...[...companies.values()].map((s) => [s.security_code, s.security_code_display, s.edinet_code, s.name, JSON.stringify(s.metrics ?? [])].map(csvCell).join(','))].join('\n') + '\n';
const periodRows = [];
const dividendRows = [];
for (const company of companies.values()) {
  for (const type of types) {
    for (const period of company.financials[type]) {
      periodRows.push([company.security_code, type, period.fiscal_year, period.accounting_standard ?? null, period.is_consolidated ?? null, period]);
    }
  }
  for (const dividend of company.dividends) dividendRows.push([company.security_code, dividend.fiscal_year, dividend.event_type, dividend.annual_dps, dividend.annual_dps_adjusted, dividend.record_date, dividend.final_rights_date, dividend.date_status, dividend]);
}
const periodCsv = ['security_code,period_type,fiscal_year,accounting_standard,is_consolidated,data_json', ...periodRows.map((row) => row.map(csvCell).join(','))].join('\n') + '\n';
const dividendCsv = ['security_code,fiscal_year,event_type,annual_dps,annual_dps_adjusted,record_date,final_rights_date,date_status,data_json', ...dividendRows.map((row) => row.map(csvCell).join(','))].join('\n') + '\n';
fs.writeFileSync(path.join(db, 'securities.csv'), securitiesCsv);
fs.writeFileSync(path.join(db, 'financial_periods.csv'), periodCsv);
fs.writeFileSync(path.join(db, 'dividend_events.csv'), dividendCsv);
fs.writeFileSync(path.join(db, 'metadata.json'), JSON.stringify({ source: 'IRBANK Connect', financial_fields: sourceMeta.financials, valuation_fields: sourceMeta.valuation.fields, dividend_fields: sourceMeta.dividends.fields, dividend_date_note: sourceMeta.dividends.date_note, attribution: sourceMeta.attribution }, null, 2) + '\n');

const sql = `PRAGMA foreign_keys = ON;\n\nCREATE TABLE IF NOT EXISTS securities (\n  security_code TEXT PRIMARY KEY,\n  security_code_display TEXT NOT NULL,\n  edinet_code TEXT NOT NULL,\n  name TEXT NOT NULL,\n  metrics_json TEXT NOT NULL DEFAULT '[]' CHECK (json_valid(metrics_json))\n);\nCREATE INDEX IF NOT EXISTS idx_securities_edinet_code ON securities(edinet_code);\n\nCREATE TABLE IF NOT EXISTS financial_periods (\n  security_code TEXT NOT NULL REFERENCES securities(security_code),\n  period_type TEXT NOT NULL CHECK (period_type IN ('annual','quarterly','semiannual')),\n  fiscal_year TEXT NOT NULL,\n  accounting_standard TEXT,\n  is_consolidated INTEGER,\n  data_json TEXT NOT NULL CHECK (json_valid(data_json)),\n  PRIMARY KEY (security_code, period_type, fiscal_year, accounting_standard, is_consolidated)\n);\nCREATE INDEX IF NOT EXISTS idx_financial_periods_lookup ON financial_periods(security_code, period_type, fiscal_year);\n`;
fs.writeFileSync(path.join(db, 'schema.sql'), sql);
fs.appendFileSync(path.join(db, 'schema.sql'), `\nCREATE TABLE IF NOT EXISTS dividend_events (\n  security_code TEXT NOT NULL REFERENCES securities(security_code),\n  fiscal_year INTEGER NOT NULL,\n  event_type TEXT NOT NULL,\n  annual_dps REAL,\n  annual_dps_adjusted REAL,\n  record_date TEXT,\n  final_rights_date TEXT,\n  date_status TEXT NOT NULL,\n  data_json TEXT NOT NULL CHECK (json_valid(data_json)),\n  PRIMARY KEY (security_code, fiscal_year, event_type)\n);\nCREATE INDEX IF NOT EXISTS idx_dividend_events_rights_date ON dividend_events(final_rights_date, security_code);\n`);

for (const company of companies.values()) {
  const json = JSON.stringify({ ...company, source: 'IRBANK Connect', attribution: sourceMeta.attribution });
  const target = path.join(output, `${company.security_code}.json`);
  fs.writeFileSync(target, json + '\n');
}

const report = {
  generated_at: new Date().toISOString(),
  securities: companies.size,
  edinet_codes_matched_to_securities: new Set([...companies.values()].map((s) => s.edinet_code)).size,
  valuation_files: valuationFiles,
  financials: Object.fromEntries([...seenPeriods].map(([type, info]) => [type, info])),
  financial_period_rows: periodRows.length,
  dividend_event_rows: dividendRows.length,
  companies_with_dividend_data: new Set(dividendRows.filter((row) => row[4] != null).map((row) => row[0])).size,
  dividend_events_with_unadjusted_dps: dividendRows.filter((row) => row[3] != null).length,
  dividend_events_with_adjusted_dps: dividendRows.filter((row) => row[4] != null).length,
  dividend_events_with_rights_date: dividendRows.filter((row) => row[6] != null).length,
  companies_with_financial_periods: Object.fromEntries(types.map((type) => [type, [...companies.values()].filter((s) => s.financials[type].length).length])),
  companies_without_any_annual_period: [...companies.values()].filter((s) => !s.financials.annual.length).length,
  securities_without_annual_period: [...companies.values()].filter((s) => !s.financials.annual.length).map((s) => s.security_code),
  edinet_codes_without_annual_period: [...new Set([...companies.values()].filter((s) => !s.financials.annual.length).map((s) => s.edinet_code))],
  files_created: companies.size + 5,
};
fs.writeFileSync(path.join(organized, 'manifest.json'), JSON.stringify(report, null, 2) + '\n');
console.log(JSON.stringify(report, null, 2));
