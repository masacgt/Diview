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
// Read actual SQL from every Worker source file, including fallback query maps.
const queries = fs.readdirSync(path.join(root, 'src')).filter(f => /\.(mjs|js)$/.test(f))
  .flatMap(f => [...fs.readFileSync(path.join(root, 'src', f), 'utf8')
    .matchAll(/(['"`])((?:SELECT|INSERT|UPDATE|DELETE)\s[\s\S]*?)\1/g)].map(m => m[2]));
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
  assert.ok(queries.length >= 60, `Unexpected SQL inventory size: ${queries.length}`);
  for (const sql of queries) {
    assert.ok(!sql.includes('${'), `Dynamic SQL needs explicit test expansion: ${sql}`);
    assert.doesNotThrow(() => db.prepare(sql), sql);
  }
  console.log(`Validated ${queries.length} Worker SQL literals`);
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

test('real rate-limit UPSERT increments and resets at the window boundary', t => {
  const db = database(); t.after(() => db.close());
  const stmt = db.prepare(query('INSERT INTO api_request_log'));
  const hit = now => stmt.get('bucket',now,now,60,now,60).request_count;
  assert.equal(hit('2026-10-20T00:00:00Z'), 1);
  assert.equal(hit('2026-10-20T00:00:59Z'), 2);
  assert.equal(hit('2026-10-20T00:01:00Z'), 1);
});

test('notification retries preserve delivery and terminal status; event IDs are never reused', t => {
  const db = database(); t.after(() => db.close());
  const insert = db.prepare(query('INSERT INTO notification_events'));
  const event = insert.run('13010','GRADE_CHANGED',null,'A','now','{}').lastInsertRowid;
  const delivery = db.prepare(query('INSERT INTO notification_deliveries'));
  delivery.run(event,'token',null,null,'retry','t1');
  delivery.run(event,'token','t2',null,null,'t2');
  delivery.run(event,'token',null,'t3','terminal','t3');
  const saved = db.prepare('SELECT * FROM notification_deliveries').get();
  assert.equal(saved.attempts, 3); assert.equal(saved.delivered_at,'t2'); assert.equal(saved.terminal_at,'t3');
  db.prepare('DELETE FROM notification_events WHERE id=?').run(event);
  assert.ok(insert.run('13010','GRADE_CHANGED','A','B','later','{}').lastInsertRowid > event);
});

test('snapshot UPSERT, nullable metrics, audit defaults, and raw backend source tags', t => {
  const db = database(); t.after(() => db.close());
  const state = db.prepare(query('INSERT INTO stock_analysis_state'));
  state.run('13010','t1',null,1,null,null,null,null,'[]','{}');
  state.run('13010','t2',null,2,80,'A','NEAR',0.95,'[]','{"updated":true}');
  assert.equal(db.prepare('SELECT count(*) n FROM stock_analysis_state').get().n,1);
  assert.equal(db.prepare('SELECT last_observed_at FROM stock_analysis_state').get().last_observed_at,'t2');
  const audit = db.prepare(query('INSERT OR IGNORE INTO app_definition_audit'));
  audit.run('key',1,'hash','{}'); audit.run('key',1,'hash','{}');
  assert.ok(db.prepare('SELECT updated_at FROM app_definition_audit').get().updated_at);
  assert.equal(db.prepare('SELECT count(*) n FROM app_definition_audit').get().n,1);
  db.prepare(query('INSERT INTO backend_state')).run('raw-source-tag','now');
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
  fs.writeFileSync(path.join(options.inputDir,'financial_periods.csv'),'wrong header');
  await assert.rejects(main(options),/Unexpected header/);
  fs.unlinkSync(path.join(options.inputDir,'financial_periods.csv'));
  await assert.rejects(main(options),/ENOENT/);
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
