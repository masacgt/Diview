const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');
const d1 = path.join(root, 'organized', 'd1');
const out = path.join(root, 'cloudflare', 'import');
fs.mkdirSync(out, { recursive: true });

const configs = [
  {
    file: 'securities.csv',
    table: 'securities',
    columns: ['security_code','security_code_display','edinet_code','name','metrics_json'],
    chunkRows: 500,
  },
  {
    file: 'financial_periods.csv',
    table: 'financial_periods',
    columns: ['security_code','period_type','fiscal_year','accounting_standard','is_consolidated','data_json'],
    chunkRows: 250,
  },
  {
    file: 'dividend_events.csv',
    table: 'dividend_events',
    columns: ['security_code','fiscal_year','event_type','annual_dps','annual_dps_adjusted','record_date','final_rights_date','date_status','data_json'],
    chunkRows: 250,
  },
];

function parseCsvLine(line) {
  const out = [];
  let cur = '';
  let quoted = false;
  for (let i = 0; i < line.length; i++) {
    const ch = line[i];
    if (quoted) {
      if (ch === '"' && line[i + 1] === '"') { cur += '"'; i++; }
      else if (ch === '"') quoted = false;
      else cur += ch;
    } else if (ch === '"') quoted = true;
    else if (ch === ',') { out.push(cur); cur = ''; }
    else cur += ch;
  }
  out.push(cur);
  return out;
}

const sql = (value) => value === '' || value == null ? 'NULL' : `'${String(value).replaceAll("'", "''")}'`;

for (const config of configs) {
  const input = path.join(d1, config.file);
  const lines = fs.readFileSync(input, 'utf8').split(/\r?\n/).filter(Boolean);
  const header = parseCsvLine(lines.shift());
  if (header.join('|') !== config.columns.join('|')) throw new Error(`Unexpected header in ${config.file}`);

  let part = 0;
  for (let i = 0; i < lines.length; i += config.chunkRows) {
    const rows = lines.slice(i, i + config.chunkRows).map(parseCsvLine);
    const statements = rows.map((row) => {
      const values = row.map((v, idx) => {
        const col = config.columns[idx];
        if (['fiscal_year','is_consolidated','annual_dps','annual_dps_adjusted'].includes(col) && v !== '') return v;
        return sql(v);
      }).join(',');
      return `INSERT OR REPLACE INTO ${config.table} (${config.columns.join(',')}) VALUES (${values});`;
    });
    const body = `${statements.join('\n')}\n`;
    const name = `${config.table}_${String(part).padStart(5, '0')}.sql`;
    fs.writeFileSync(path.join(out, name), body);
    part++;
  }
  console.log(`${config.table}: ${lines.length} rows -> ${part} files`);
}
