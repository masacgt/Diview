const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { spawnSync } = require('node:child_process');
const { DatabaseSync } = require('node:sqlite');
const { main } = require('../../scripts/build-d1-import.cjs');
const root = path.resolve(__dirname, '..');
const base = fs.readFileSync(path.join(root, '../organized/d1/schema.sql'), 'utf8');
const migrations = fs.readdirSync(path.join(root, 'migrations')).filter(f => f.endsWith('.sql')).sort()
  .map(f => fs.readFileSync(path.join(root, 'migrations', f), 'utf8'));
function database() {
  const db = new DatabaseSync(':memory:');
  db.exec(base);
  migrations.forEach(s => db.exec(s));
  return db;
}
// Read D1 SQL from Worker sources; Durable Object SQLite statements use a separate database.
const queries = fs.readdirSync(path.join(root, 'src')).filter(f => /\.(mjs|js)$/.test(f))
  .flatMap(f => [...fs.readFileSync(path.join(root, 'src', f), 'utf8')
    .matchAll(/(['"`])((?:SELECT|INSERT|UPDATE|DELETE)\s[\s\S]*?)\1/g)].map(m => m[2]))
  .filter(sql => !/\brate_limit_(?:state|legacy_seed)\b/.test(sql));
const query = prefix => {
  const result = queries.find(s => s.startsWith(prefix));
  assert.ok(result, `SQL not found: ${prefix}`);
  return result;
};
const headers = {
  securities: 'security_code,security_code_display,edinet_code,name,metrics_json',
  financial_periods: 'security_code,period_type,fiscal_year,accounting_standard,is_consolidated,data_json',
  dividend_events: 'security_code,fiscal_year,event_type,annual_dps,annual_dps_adjusted,record_date,final_rights_date,date_status,data_json',
};
const csv = values => values.map(v => '"' + String(v).replaceAll('"', '""') + '"').join(',');
function fixtures(t) {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'diview-d1-test-'));
  t.after(() => fs.rmSync(dir, { recursive: true, force: true }));
  for (const [table, header] of Object.entries(headers)) fs.writeFileSync(path.join(dir, table + '.csv'), '\uFEFF' + header + '\r\n');
  fs.appendFileSync(path.join(dir, 'securities.csv'), csv(['13010','1301','E0001',"O'Brien,株式会社",'[]']) + '\r\n');
  return { inputDir: dir, outputDir: path.join(dir, 'out'), log: () => {} };
}

test('all actual Worker SQL compiles against base plus migrations', t => {
  const db = database(); t.after(() => db.close());
  assert.ok(queries.length >= 10, `Unexpected SQL inventory size after retiring D1 market-data paths: ${queries.length}`);
  for (const sql of queries) {
    assert.ok(!sql.includes('${'), `Dynamic SQL needs explicit test expansion: ${sql}`);
    assert.doesNotThrow(() => db.prepare(sql), sql);
  }
  console.log(`Validated ${queries.length} Worker SQL literals`);
});

test('securities code lookup uses both indexed branches of the OR predicate', t => {
  const db = database(); t.after(() => db.close());
  const query = `SELECT security_code, security_code_display, name, metrics_json FROM securities
    WHERE security_code = ? OR security_code_display = ? LIMIT ?`;
  const plan = db.prepare(`EXPLAIN QUERY PLAN ${query}`).all('72030', '7203', 1)
    .map(row => row.detail).join(' ');
  assert.match(plan, /sqlite_autoindex_securities_1/);
  assert.match(plan, /idx_securities_code_display/);
});

test('migration replay preserves existing rows and operator definitions; defaults are complete', t => {
  const db = database(); t.after(() => db.close());
  for (const name of ['backend-config','stock-analysis-definitions','watchlist-analysis-definitions','portfolio-analysis-definitions']) {
    const row = db.prepare('SELECT value_json FROM app_definition_documents WHERE definition_key=?').get(name + ':v1');
    assert.deepEqual(JSON.parse(row.value_json), JSON.parse(fs.readFileSync(path.join(root, 'kv', name + '.json'), 'utf8')));
  }
  for (const [name, table] of [['diagnosis-definitions','diagnosis_bands'],['valuation-scenarios','valuation_scenarios']]) {
    assert.equal(db.prepare(`SELECT count(*) n FROM ${table}`).get().n, JSON.parse(fs.readFileSync(path.join(root,'kv',name+'.json'),'utf8')).length);
  }
  db.exec("UPDATE app_definition_documents SET value_json='{\"custom\":true}'; UPDATE diagnosis_bands SET title='custom'; INSERT INTO clients VALUES ('c','hash','now','now',NULL)");
  const snapshot = () => db.prepare("SELECT name FROM sqlite_master WHERE type='table' ORDER BY name").all()
    .map(({name}) => [name, db.prepare(`SELECT * FROM ${name}`).all()]);
  const before = snapshot();
  migrations.forEach(s => db.exec(s));
  assert.deepEqual(snapshot(), before);
});

test('the Worker keeps a read-only legacy rate-limit lookup for first-use Durable Object handoff', t => {
  const db = database(); t.after(() => db.close());
  db.prepare('INSERT INTO api_request_log(bucket_key, request_count, window_started_at) VALUES (?, ?, ?)')
    .run('bucket', 4, '2026-10-20T00:00:00Z');
  const statement = query('SELECT request_count, window_started_at FROM api_request_log');
  assert.match(statement, /WHERE bucket_key = \?/);
  assert.deepEqual({ ...db.prepare(statement).get('bucket') }, {
    request_count: 4,
    window_started_at: '2026-10-20T00:00:00Z',
  });
});

test('notification retries preserve delivery and terminal status; event IDs are never reused', t => {
  const db = database(); t.after(() => db.close());
  const insert = db.prepare(`INSERT INTO notification_events(
    security_code,event_type,from_status,to_status,occurred_at,payload_json
  ) VALUES (?,?,?,?,?,?)`);
  const event = insert.run('13010','GRADE_CHANGED',null,'A','now','{}').lastInsertRowid;
  const delivery = db.prepare(`INSERT INTO notification_deliveries(event_id,device_token,attempts,delivered_at,terminal_at,last_error,updated_at)
    VALUES (?, ?, 1, ?, ?, ?, ?)
    ON CONFLICT(event_id,device_token) DO UPDATE SET
      attempts=notification_deliveries.attempts+1,
      delivered_at=COALESCE(excluded.delivered_at,notification_deliveries.delivered_at),
      terminal_at=COALESCE(excluded.terminal_at,notification_deliveries.terminal_at),
      last_error=excluded.last_error,updated_at=excluded.updated_at`);
  delivery.run(event,'token',null,null,'retry','t1');
  delivery.run(event,'token','t2',null,null,'t2');
  delivery.run(event,'token',null,'t3','terminal','t3');
  const saved = db.prepare('SELECT * FROM notification_deliveries').get();
  assert.equal(saved.attempts, 3); assert.equal(saved.delivered_at,'t2'); assert.equal(saved.terminal_at,'t3');
  db.prepare('DELETE FROM notification_events WHERE id=?').run(event);
  assert.ok(insert.run('13010','GRADE_CHANGED','A','B','later','{}').lastInsertRowid > event);
});

test('legacy analysis snapshots and checkpoints remain readable until an explicit retention decision', t => {
  const db = database(); t.after(() => db.close());
  db.prepare(`INSERT INTO stock_analysis_state(
    security_code,last_observed_at,market_date,definition_version,overall_score,overall_grade,
    buy_zone_status,buy_zone_ratio,anomaly_flags_json,analysis_json
  ) VALUES (?,?,?,?,?,?,?,?,?,?)`).run('13010','t2',null,2,80,'A','NEAR',0.95,'[]','{"updated":true}');
  db.prepare(`INSERT INTO stock_analysis_history(
    security_code,observed_at,market_date,definition_version,overall_score,overall_grade,
    buy_zone_status,buy_zone_ratio,anomaly_flags_json,metrics_json,analysis_json
  ) VALUES (?,?,?,?,?,?,?,?,?,?,?)`).run('13010','t2',null,2,80,'A','NEAR',0.95,'[]','{}','{"updated":true}');
  assert.equal(db.prepare('SELECT count(*) n FROM stock_analysis_state').get().n,1);
  assert.equal(db.prepare('SELECT last_observed_at FROM stock_analysis_state').get().last_observed_at,'t2');
  db.prepare(`INSERT INTO backend_state(state_key,value_json,updated_at) VALUES (?,?,?)`).run('analysis_source','legacy','t2');
  const before = db.prepare('SELECT analysis_json FROM stock_analysis_history').get().analysis_json;
  migrations.forEach(s => db.exec(s));
  assert.equal(db.prepare('SELECT analysis_json FROM stock_analysis_history').get().analysis_json,before);
  assert.equal(db.prepare('SELECT value_json FROM backend_state WHERE state_key=?').get('analysis_source').value_json,'legacy');
});

test('CSV round trip keeps full annual periods, numeric dividend years, escaping and NULLs', async t => {
  const options = fixtures(t);
  fs.appendFileSync(path.join(options.inputDir,'financial_periods.csv'), [
    ['13010','annual','2025/03','J-GAAP','true','{"fiscal_year":"2025/03"}'],
    ['13010','annual','2025/12','J-GAAP','false','{}'],
    ['13010','quarterly','2025/06','J-GAAP','true','{}'],
  ].map(csv).join('\r\n'));
  fs.appendFileSync(path.join(options.inputDir,'dividend_events.csv'), csv(['13010','2025','annual','12.5','','','','unknown','{}']));
  await main(options);
  const db = database(); t.after(() => db.close());
  for (const prefix of ['securities','financial_periods_annual','dividend_events']) {
    db.exec(fs.readFileSync(path.join(options.outputDir, prefix+'_00000.sql'),'utf8'));
  }
  assert.deepEqual(db.prepare('SELECT fiscal_year, typeof(fiscal_year) type, is_consolidated FROM financial_periods ORDER BY fiscal_year').all().map(r => ({...r})), [
    {fiscal_year:'2025/03',type:'text',is_consolidated:1}, {fiscal_year:'2025/12',type:'text',is_consolidated:0},
  ]);
  assert.equal(db.prepare('SELECT name FROM securities').get().name,"O'Brien,株式会社");
  assert.deepEqual({...db.prepare('SELECT fiscal_year,typeof(fiscal_year) type,annual_dps,annual_dps_adjusted FROM dividend_events').get()}, {fiscal_year:2025,type:'integer',annual_dps:12.5,annual_dps_adjusted:null});
});

test('malformed numeric/period values, bad CSV, headers and input errors reject cleanly', async t => {
  for (const row of [
    ['13010','annual','2025/13','J-GAAP','true','{}'],
    ['13010','annual','2025/03','J-GAAP','bogus','{}'],
    ['13010','annual','2025/03','','true','{}'],
    ['13010','annual','2025/03','J-GAAP','','{}'],
  ]) {
    const options=fixtures(t);
    fs.appendFileSync(path.join(options.inputDir,'financial_periods.csv'),csv(row));
    await assert.rejects(main(options),/Invalid/);
  }
  for (const value of ['2025/03','2025; DELETE FROM clients']) {
    const options=fixtures(t);
    fs.appendFileSync(path.join(options.inputDir,'dividend_events.csv'),csv(['13010',value,'annual','1','','','','unknown','{}']));
    await assert.rejects(main(options),/Invalid/);
  }
  const options=fixtures(t);
  fs.writeFileSync(path.join(options.inputDir,'financial_periods.csv'),headers.financial_periods+'\n"unterminated');
  await assert.rejects(main(options),/Unterminated/);
  fs.rmSync(options.outputDir, { recursive: true, force: true });
  fs.writeFileSync(path.join(options.inputDir,'financial_periods.csv'),'wrong header');
  await assert.rejects(main(options),/Unexpected header/);
  fs.rmSync(options.outputDir, { recursive: true, force: true });
  fs.unlinkSync(path.join(options.inputDir,'financial_periods.csv'));
  await assert.rejects(main(options),/ENOENT/);
});

test('financial key migration preserves existing rows and rejects nullable new keys', t => {
  const db = new DatabaseSync(':memory:'); t.after(() => db.close());
  db.exec(base);
  db.exec("INSERT INTO securities VALUES ('13010','1301','E0001','fixture','[]')");
  db.exec("INSERT INTO financial_periods VALUES ('13010','annual','2025/03',NULL,1,'{}')");
  migrations.forEach(s => db.exec(s));
  const put = db.prepare('INSERT OR REPLACE INTO financial_periods VALUES (?,?,?,?,?,?)');
  for (const [standard, consolidated] of [[null, 1], ['', 1], ['J-GAAP', null]]) {
    assert.throws(() => put.run('13010','annual','2025/03',standard,consolidated,'{}'), /requires/);
  }
  assert.equal(db.prepare('SELECT count(*) n FROM financial_periods').get().n, 1);
  db.exec("UPDATE financial_periods SET accounting_standard='J-GAAP'");
  put.run('13010','annual','2025/03','J-GAAP',1,'{"sales":200}');
  assert.equal(db.prepare('SELECT count(*) n FROM financial_periods').get().n, 1);
  assert.throws(() => db.exec('UPDATE financial_periods SET is_consolidated=NULL'), /requires/);
});

test('validated import refuses directory reuse and preserves both issuer periods', async t => {
  const options = fixtures(t);
  const rows = ['2026/03', '2026/12'].map(period =>
    csv(['13010','2026','annual_reported','10','','','','unknown',JSON.stringify({period})]));
  fs.appendFileSync(path.join(options.inputDir, 'dividend_events.csv'), rows.join('\n'));
  await main(options);
  await assert.rejects(main(options), /empty import directory/);
  const manifest = JSON.parse(fs.readFileSync(path.join(options.outputDir, 'manifest.json'), 'utf8'));
  assert.equal(manifest.format_version, 2);
  const db = database(); t.after(() => db.close());
  for (const file of manifest.files) db.exec(fs.readFileSync(path.join(options.outputDir, file.name), 'utf8'));
  assert.deepEqual(db.prepare('SELECT fiscal_period FROM issuer_dividend_periods ORDER BY fiscal_period').all()
    .map(r => r.fiscal_period), ['2026-03', '2026-12']);
});

test('large nonsecurity CSV streams under a 32 MB heap and chunks at 250 rows', t => {
  const options=fixtures(t);
  const row=csv(['13010','annual','2025/03','J-GAAP','true',JSON.stringify({pad:'x'.repeat(16384)})])+'\n';
  const fd=fs.openSync(path.join(options.inputDir,'financial_periods.csv'),'a');
  try { for (let i=0;i<3001;i++) fs.writeSync(fd,row); } finally { fs.closeSync(fd); }
  const script=`const fs=require('node:fs'); const original=fs.readFileSync; fs.readFileSync=function(p,...args){if(String(p).endsWith('.csv')) throw Error('CSV preload'); return original.call(this,p,...args)}; require(${JSON.stringify(path.join(root,'../scripts/build-d1-import.cjs'))}).main(${JSON.stringify({inputDir:options.inputDir,outputDir:options.outputDir})}).catch(e=>{console.error(e);process.exitCode=1})`;
  const result=spawnSync(process.execPath,['--max-old-space-size=32','-e',script],{encoding:'utf8',timeout:60000});
  assert.equal(result.status,0,result.stderr);
  const files=fs.readdirSync(options.outputDir).filter(f=>f.startsWith('financial_periods_annual'));
  assert.equal(files.length,13);
  assert.equal(fs.readFileSync(path.join(options.outputDir,files.at(-1)),'utf8').trim().split('\n').length,1);
});

test('local initializer applies every migration before starting Worker and stops on failure', () => {
  const init=path.join(root,'dev-local.ps1').replaceAll("'","''");
  for (const fail of [false,true]) {
    const script=`$global:calls = [System.Collections.Generic.List[string]]::new()
function global:npx {
  $global:calls.Add(($args -join ' '))
  $global:LASTEXITCODE = 0
  if ($args -contains 'apply' -and $${fail}) { $global:LASTEXITCODE = 1 }
}
try { . '${init}' } catch { if (-not $${fail}) { throw } }
$global:calls | ConvertTo-Json -Compress`;
    const result=spawnSync('pwsh',['-NoProfile','-Command',script],{encoding:'utf8',timeout:10000});
    assert.equal(result.status,0,result.stderr);
    const calls=JSON.parse(result.stdout.trim());
    assert.match(calls[0],/d1 execute .* --local --config .* --file /);
    assert.match(calls[1],/d1 migrations apply .* --local --config /);
    assert.equal(calls.length,fail ? 2 : 3);
    if (!fail) assert.match(calls[2],/^wrangler dev --config /);
    assert.ok(calls.every(c=>!c.includes('--remote')));
  }
});

test('unquoted periods can destroy history: corrected imports must come from trusted source', t => {
  const db=database(); t.after(()=>db.close());
  db.exec("INSERT INTO securities VALUES ('13010','1301','E1','name','[]')");
  // Both periods evaluate to 675; the second REPLACE destroys the first row.
  db.exec(`INSERT OR REPLACE INTO financial_periods VALUES ('13010','annual',2025/03,'J-GAAP',1,'{"original":"2025/03"}');
    INSERT OR REPLACE INTO financial_periods VALUES ('13010','annual',2026/03,'J-GAAP',1,'{"original":"2026/03"}');`);
  const surviving=db.prepare('SELECT fiscal_year,data_json FROM financial_periods').all();
  assert.equal(surviving.length,1);
  assert.equal(surviving[0].fiscal_year,'675');
  assert.equal(JSON.parse(surviving[0].data_json).original,'2026/03');
});
