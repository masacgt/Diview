const fs = require('node:fs');
const path = require('node:path');
const readline = require('node:readline');

const root = path.resolve(__dirname, '..');
const d1 = path.join(root, 'organized', 'd1');
const out = path.join(root, 'cloudflare', 'import');

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
  if (quoted) throw new Error('Unterminated CSV quote');
  out.push(cur);
  return out;
}

const sql = (value) => value === '' || value == null ? 'NULL' : `'${String(value).replaceAll("'", "''")}'`;

// Types are table-specific: annual periods are YYYY/MM TEXT, dividend years INTEGER.
function sqlValue(config, column, value) {
  if (value === '' || value == null) return 'NULL';
  if (column === 'fiscal_year') {
    const pattern = config.table === 'financial_periods' ? /^\d{4}\/(0[1-9]|1[0-2])$/ : /^\d{4}$/;
    if (!pattern.test(value)) throw new Error(`Invalid ${config.table}.${column}: ${value}`);
    return config.table === 'financial_periods' ? sql(value) : value;
  }
  if (column === 'is_consolidated') {
    if (value === 'true' || value === '1') return '1';
    if (value === 'false' || value === '0') return '0';
    throw new Error(`Invalid ${column}: ${value}`);
  }
  if (column === 'annual_dps' || column === 'annual_dps_adjusted') {
    if (!/^[+-]?(?:\d+(?:\.\d*)?|\.\d+)(?:[eE][+-]?\d+)?$/.test(value) || !Number.isFinite(Number(value))) {
      throw new Error(`Invalid ${column}: ${value}`);
    }
    return value;
  }
  return sql(value);
}

async function main({ inputDir = d1, outputDir = out, log = console.log } = {}) {
  fs.mkdirSync(outputDir, { recursive: true });
  for (const config of configs) {
    const input = fs.createReadStream(path.join(inputDir, config.file), { encoding: 'utf8' });
    const lines = readline.createInterface({ input, crlfDelay: Infinity });
    let firstLine = true;
    let part = 0;
    let count = 0;
    let chunk = [];
    const flush = () => {
      if (!chunk.length) return;
      const prefix = config.table === 'financial_periods' ? 'financial_periods_annual' : config.table;
      const name = `${prefix}_${String(part++).padStart(5, '0')}.sql`;
      fs.writeFileSync(path.join(outputDir, name), `${chunk.join('\n')}\n`);
      chunk = [];
    };
    try {
      for await (const line of lines) {
        if (firstLine) {
          firstLine = false;
          const header = parseCsvLine(line.replace(/^\uFEFF/, ''));
          if (header.join('|') !== config.columns.join('|')) throw new Error(`Unexpected header in ${config.file}`);
          continue;
        }
        if (!line) continue;
        const row = parseCsvLine(line);
        if (row.length !== config.columns.length) throw new Error(`Unexpected column count in ${config.file}`);
        if (config.table === 'financial_periods' && row[1] !== 'annual') continue;
        const values = row.map((value, index) => sqlValue(config, config.columns[index], value));
        chunk.push(`INSERT OR REPLACE INTO ${config.table} (${config.columns.join(',')}) VALUES (${values.join(',')});`);
        count++;
        if (chunk.length >= config.chunkRows) flush();
      }
      if (firstLine) throw new Error(`Missing header in ${config.file}`);
      flush();
    } finally {
      lines.close();
      input.destroy();
    }
    log(`${config.table}: ${count} rows -> ${part} files`);
  }
}

module.exports = { main };
if (require.main === module) main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
