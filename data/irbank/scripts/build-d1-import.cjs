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

async function main() {
for (const config of configs) {
  const input = path.join(d1, config.file);
  const lines = fs.readFileSync(input, 'utf8').split(/\r?\n/).filter(Boolean);
  const header = parseCsvLine(lines.shift());
  if (header.join('|') !== config.columns.join('|')) throw new Error(`Unexpected header in ${config.file}`);

  let sourceRows = lines.map(parseCsvLine);
  if (config.table === 'financial_periods') {
    sourceRows = sourceRows.filter(row => row[1] === 'annual');
  }

  // The dividend and financial CSVs can exceed the default 4 GB V8 heap.
  // Stream large inputs in bounded chunks while preserving the existing SQL format.
  if (config.table !== 'securities') {
    const inputStream = fs.createReadStream(input, { encoding: 'utf8' });
    let carry = '';
    let firstLine = true;
    let part = 0;
    let chunk = [];
    let count = 0;
    const flush = () => {
      if (!chunk.length) return;
      const statements = chunk.map((row) => {
        const values = row.map((v, idx) => {
          const col = config.columns[idx];
          if (['fiscal_year','is_consolidated','annual_dps','annual_dps_adjusted'].includes(col) && v !== '') return v;
          return sql(v);
        }).join(',');
        return `INSERT OR REPLACE INTO ${config.table} (${config.columns.join(',')}) VALUES (${values});`;
      });
      const filePrefix = config.table === 'financial_periods' ? 'financial_periods_annual' : 'dividend_events';
      const name = `${filePrefix}_${String(part++).padStart(5, '0')}.sql`;
      fs.writeFileSync(path.join(out, name), `${statements.join('\n')}\n`);
      chunk = [];
    };
    const consumeLine = (line) => {
      if (firstLine) {
        firstLine = false;
        if (line.replace(/^\uFEFF/, '').split(',').join('|') !== config.columns.join('|')) throw new Error(`Unexpected header in ${config.file}`);
        return;
      }
      if (!line) return;
      const row = parseCsvLine(line);
      if (config.table === 'financial_periods' && row[1] !== 'annual') return;
      chunk.push(row);
      count++;
      if (chunk.length >= config.chunkRows) flush();
    };
    await new Promise((resolve, reject) => {
      inputStream.on('data', (data) => {
        carry += data;
        const lines = carry.split(/\r?\n/);
        carry = lines.pop();
        for (const line of lines) consumeLine(line);
      });
      inputStream.on('end', () => {
        if (carry) consumeLine(carry);
        flush();
        resolve();
      });
      inputStream.on('error', reject);
    });
    console.log(`${config.table}: ${count} rows -> ${part} files`);
    continue;
  }

  let part = 0;
  for (let i = 0; i < sourceRows.length; i += config.chunkRows) {
    const rows = sourceRows.slice(i, i + config.chunkRows);
    const statements = rows.map((row) => {
      const values = row.map((v, idx) => {
        const col = config.columns[idx];
        if (['fiscal_year','is_consolidated','annual_dps','annual_dps_adjusted'].includes(col) && v !== '') return v;
        return sql(v);
      }).join(',');
      return `INSERT OR REPLACE INTO ${config.table} (${config.columns.join(',')}) VALUES (${values});`;
    });
    const body = `${statements.join('\n')}\n`;
    const filePrefix = config.table === 'financial_periods' ? 'financial_periods_annual' : config.table;
    const name = `${filePrefix}_${String(part).padStart(5, '0')}.sql`;
    fs.writeFileSync(path.join(out, name), body);
    part++;
  }
  console.log(`${config.table}: ${sourceRows.length} rows -> ${part} files`);
}
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
