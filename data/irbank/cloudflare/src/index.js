const ANALYSIS_CONCURRENCY = 8;

function json(data, status = 200) {
  return new Response(JSON.stringify(data), {
    status,
    headers: {
      'content-type': 'application/json; charset=utf-8',
      'cache-control': 'private, no-store',
      'access-control-allow-origin': '*',
      'access-control-allow-methods': 'GET, POST, PUT, DELETE, OPTIONS',
      'access-control-allow-headers': 'Content-Type, X-Client-Id, X-Client-Secret, X-Admin-Secret',
      'access-control-max-age': '86400'
    }
  });
}

function rateLimitedResponse(config) {
  const response = json({ error: 'rate_limited' }, 429);
  response.headers.set('retry-after', String(Number(config.rate_limit_window_seconds || 60)));
  return response;
}

async function readJson(request) {
  try {
    return await request.json();
  } catch {
    return null;
  }
}

function normalizeCode(code) {
  if (/^\d{4}$/.test(code)) return `${code}0`;
  return code;
}

function isoNow() {
  return new Date().toISOString();
}

function randomToken(bytes = 24) {
  const data = new Uint8Array(bytes);
  crypto.getRandomValues(data);
  return [...data].map(b => b.toString(16).padStart(2, '0')).join('');
}

async function authenticateClient(request, env) {
  const clientId = String(request.headers.get('X-Client-Id') || '').trim();
  const secret = String(request.headers.get('X-Client-Secret') || '').trim();
  if (!clientId || !secret) return null;
  const row = await env.DB.prepare('SELECT client_secret_hash, disabled_at FROM clients WHERE client_id = ?').bind(clientId).first();
  if (!row || row.disabled_at) return null;
  const actual = await sha256Hex(secret);
  if (actual !== row.client_secret_hash) return null;
  await env.DB.prepare('UPDATE clients SET last_seen_at = ? WHERE client_id = ?').bind(isoNow(), clientId).run();
  return clientId;
}

function daysOld(value) {
  if (!value) return null;
  const ms = Date.now() - Date.parse(value);
  return Number.isFinite(ms) ? ms / 86400000 : null;
}

async function backendConfig(env) {
  const defaults = {
    market_stale_days: 3,
    rate_limit_window_seconds: 60,
    rate_limit_register: 10,
    rate_limit_authenticated: 120,
    rate_limit_public: 300,
  };
  const configured = await appDefinitionDocument(env, 'backend-config:v1') || {};
  return { ...defaults, ...configured };
}

async function requestBucketKey(request, route, clientId = '') {
  const ip = request.headers.get('CF-Connecting-IP') || 'unknown';
  return `rl:${route}:${await sha256Hex(clientId || ip)}`;
}

async function enforceRateLimit(request, env, route, limit, clientId = '', config = null) {
  const activeConfig = config || await backendConfig(env);
  const windowSeconds = Number(activeConfig.rate_limit_window_seconds || 60);
  const now = isoNow();
  const key = await requestBucketKey(request, route, clientId);
  const row = await env.DB.prepare(`INSERT INTO api_request_log(bucket_key, request_count, window_started_at)
    VALUES (?, 1, ?)
    ON CONFLICT(bucket_key) DO UPDATE SET
      request_count=CASE WHEN unixepoch(?) - unixepoch(api_request_log.window_started_at) >= ? THEN 1 ELSE api_request_log.request_count + 1 END,
      window_started_at=CASE WHEN unixepoch(?) - unixepoch(api_request_log.window_started_at) >= ? THEN excluded.window_started_at ELSE api_request_log.window_started_at END
    RETURNING request_count`).bind(key, now, now, windowSeconds, now, windowSeconds).first();
  return Number(row?.request_count || 0) <= Number(limit);
}

function publicRateLimitRoute(pathname) {
  if (pathname === '/api/v1/health') return 'health';
  if (pathname.startsWith('/api/v1/irbank/securities/')) return 'irbank-security';
  if (pathname.startsWith('/api/v1/analysis-history/')) return 'analysis-history';
  if (pathname.startsWith('/api/v1/analysis/')) return 'analysis';
  if (pathname.startsWith('/api/v1/securities/')) return 'security';
  if (pathname === '/api/v1/security-search') return 'security-search';
  if (pathname === '/api/v1/search-catalog') return 'search-catalog';
  if (pathname.startsWith('/api/v1/')) return pathname.slice('/api/v1/'.length);
  return null;
}

function finiteNumber(value) {
  return typeof value === 'number' && Number.isFinite(value) ? value : null;
}

function firstMetricValue(metrics, keys) {
  for (const key of keys) {
    const row = (metrics || []).find(item => item && String(item.key || item.name || '').toLowerCase() === key.toLowerCase());
    const value = finiteNumber(row?.value ?? row?.current ?? row?.latest);
    if (value !== null) return value;
  }
  return null;
}

function anomalyFlags(values) {
  const flags = [];
  const add = (code, severity, message, metric, value) => flags.push({ code, severity, message, metric, value });
  if (values.per === null) add('PER_MISSING', 'info', 'PERを取得できません。', 'per', null);
  else if (values.per <= 0) add('PER_NON_POSITIVE', 'warning', 'PERが0以下のため通常の割安判定には適しません。赤字または特殊要因を確認してください。', 'per', values.per);
  else if (values.per >= 80) add('PER_EXTREME_HIGH', 'warning', 'PERが非常に高く、成長期待や一時的な利益低下の影響を受けている可能性があります。', 'per', values.per);
  if (values.pbr === null) add('PBR_MISSING', 'info', 'PBRを取得できません。', 'pbr', null);
  else if (values.pbr < 0) add('PBR_NEGATIVE', 'warning', 'PBRが負値です。債務超過などで通常評価が難しい可能性があります。', 'pbr', values.pbr);
  else if (values.pbr >= 10) add('PBR_EXTREME_HIGH', 'warning', 'PBRが非常に高く、純資産に対する評価倍率が極端です。', 'pbr', values.pbr);
  if (values.payout_ratio !== null) {
    if (values.payout_ratio < 0) add('PAYOUT_NEGATIVE', 'warning', '配当性向が負値です。赤字または特殊要因により通常比較が難しい状態です。', 'payout_ratio', values.payout_ratio);
    else if (values.payout_ratio > 120) add('PAYOUT_EXTREME_HIGH', 'critical', '配当性向が120%を超えており、利益だけでは配当を賄えていない可能性があります。', 'payout_ratio', values.payout_ratio);
  }
  if (values.equity_ratio !== null && values.equity_ratio < 0) add('NEGATIVE_EQUITY_RATIO', 'critical', '自己資本比率が負値です。財務状態を重点確認してください。', 'equity_ratio', values.equity_ratio);
  if (values.operating_margin !== null && Math.abs(values.operating_margin) > 100) add('OPERATING_MARGIN_EXTREME', 'warning', '営業利益率が極端な値です。一時要因やデータ定義を確認してください。', 'operating_margin', values.operating_margin);
  if (values.fcf_margin !== null && Math.abs(values.fcf_margin) > 100) add('FCF_MARGIN_EXTREME', 'warning', 'FCF比率が極端な値です。大型投資や資産売却など一時要因を確認してください。', 'fcf_margin', values.fcf_margin);
  return flags;
}

function matchMetricBand(value, bands) {
  if (value === null || !Array.isArray(bands)) return null;
  return bands.find(band => {
    if (band.min !== undefined && value < Number(band.min)) return false;
    if (band.max !== undefined && value > Number(band.max)) return false;
    return true;
  }) || null;
}

function evaluateStockAnalysis(values, definitions) {
  const results = [];
  let weighted = 0;
  let weights = 0;
  for (const rule of definitions?.metric_rules || []) {
    const value = finiteNumber(values[rule.key]);
    const band = matchMetricBand(value, rule.bands);
    if (value === null || !band) continue;
    const weight = Number(rule.weight || 0);
    const score = Number(band.score || 0);
    weighted += score * weight;
    weights += weight;
    results.push({ key: rule.key, label: rule.label, value, score, weight, band: band.label });
  }
  const overallScore = weights > 0 ? Math.round(weighted / weights) : null;
  const overall = overallScore === null ? null : (definitions?.overall_bands || []).find(b => overallScore >= Number(b.min_score) && overallScore <= Number(b.max_score));
  return { overall_score: overallScore, overall_grade: overall?.grade ?? null, overall_title: overall?.title ?? null, metrics: results };
}

function buyZoneForRatio(ratio, definitions) {
  if (ratio === null) return null;
  return (definitions?.buy_zone || []).find(item => ratio >= Number(item.min_ratio || 0)) || null;
}

async function sha256Hex(value) {
  const bytes = new TextEncoder().encode(value);
  const digest = await crypto.subtle.digest('SHA-256', bytes);
  return [...new Uint8Array(digest)].map(b => b.toString(16).padStart(2, '0')).join('');
}

async function auditDefinition(env, key, definition) {
  if (!definition || !env.DB) return;
  const canonical = JSON.stringify(definition);
  const hash = await sha256Hex(canonical);
  const version = Number(definition.version || 1);
  await env.DB.prepare(
    `INSERT OR IGNORE INTO app_definition_audit(definition_key, version, content_hash, metadata_json)
     VALUES (?, ?, ?, ?)`
  ).bind(key, version, hash, JSON.stringify({ source: env.APP_DEFINITIONS ? 'kv_or_d1' : 'd1', observed_at: isoNow() })).run();
}

async function appDefinitionItems(env, key) {
  if (env.APP_DEFINITIONS) {
    const raw = await env.APP_DEFINITIONS.get(key);
    if (raw) {
      const parsed = JSON.parse(raw);
      return Array.isArray(parsed) ? parsed : parsed;
    }
  }
  const queries = {
    'diagnosis-definitions:v1': `SELECT min_score, max_score, grade, title, comment
      FROM diagnosis_bands ORDER BY min_score DESC`,
    'valuation-scenarios:v1': `SELECT id, per_max, pbr_max, title, badge, comment
      FROM valuation_scenarios ORDER BY id`
  };
  const query = queries[key];
  if (!query) return [];
  const result = await env.DB.prepare(query).all();
  return result.results || [];
}

async function appDefinitionDocument(env, key) {
  if (env.APP_DEFINITIONS) {
    const raw = await env.APP_DEFINITIONS.get(key);
    if (raw) return JSON.parse(raw);
  }
  const row = await env.DB.prepare(
    'SELECT value_json FROM app_definition_documents WHERE definition_key = ?'
  ).bind(key).first();
  return row ? JSON.parse(row.value_json) : null;
}

async function loadMarketIndex(env) {
  if (!env.MARKET_DATA) return new Map();
  const object = await env.MARKET_DATA.get('market/yfinance/latest_market_data.json');
  if (!object) return new Map();
  const snapshot = JSON.parse(await object.text());
  return new Map((snapshot.securities || [])
    .filter(row => row?.symbol)
    .map(row => [String(row.symbol).toUpperCase(), { row, snapshot }]));
}

async function latestMarketForSecurity(env, displayCode, marketIndex = null) {
  if (!env.MARKET_DATA) return null;
  const symbol = `${displayCode}.T`.toUpperCase();
  let indexed = marketIndex?.get(symbol);
  if (!marketIndex) {
    const object = await env.MARKET_DATA.get('market/yfinance/latest_market_data.json');
    if (!object) return null;
    const snapshot = JSON.parse(await object.text());
    const row = (snapshot.securities || []).find(item => String(item.symbol || '').toUpperCase() === symbol);
    if (row) indexed = { row, snapshot };
  }
  const row = indexed?.row;
  if (!row) return null;
  const snapshot = indexed.snapshot;
  const quote = row.latest_daily_price || {};
  return { summary: row, price: {
    symbol: row.symbol,
    date: quote.date ?? null,
    open: quote.open ?? null,
    high: quote.high ?? null,
    low: quote.low ?? null,
    close: quote.close ?? null,
    adjusted_close: quote.adjusted_close ?? null,
    volume: quote.volume ?? null,
    change: row.regularMarketChange ?? null,
    change_percent: row.regularMarketChangePercent ?? null,
    source: snapshot.source ?? null,
    generated_at: snapshot.generated_at ?? null
  }};
}

async function loadSecurityAnalysisInput(env, security, marketIndex = null) {
  const [dividend, financial, marketData] = await Promise.all([
    env.DB.prepare(
      `SELECT fiscal_year, annual_dps, annual_dps_adjusted, record_date, final_rights_date, date_status, data_json
       FROM dividend_events WHERE security_code = ? ORDER BY fiscal_year DESC LIMIT 1`
    ).bind(security.security_code).first(),
    env.DB.prepare(
      `SELECT fiscal_year, accounting_standard, is_consolidated, data_json
       FROM financial_periods WHERE security_code = ? AND period_type = 'annual' ORDER BY fiscal_year DESC LIMIT 1`
    ).bind(security.security_code).first(),
    latestMarketForSecurity(env, security.security_code_display, marketIndex),
  ]);
  const metrics = JSON.parse(security.metrics_json || '[]');
  const dividendData = dividend ? JSON.parse(dividend.data_json || '{}') : null;
  const financialData = financial ? JSON.parse(financial.data_json || '{}') : null;
  const summary = marketData?.summary || null;
  const price = finiteNumber(marketData?.price?.close);
  const annualDps = finiteNumber(dividend?.annual_dps_adjusted ?? dividend?.annual_dps);
  const values = {
    per: finiteNumber(summary?.trailingPE),
    pbr: finiteNumber(summary?.priceToBook),
    payout_ratio: finiteNumber(dividendData?.payout_ratio),
    equity_ratio: finiteNumber(financialData?.equity_ratio) ?? firstMetricValue(metrics, ['equity_ratio', '自己資本比率']),
    operating_margin: finiteNumber(financialData?.operating_margin) ?? firstMetricValue(metrics, ['operating_margin', '営業利益率']),
    fcf_margin: finiteNumber(financialData?.fcf_margin) ?? firstMetricValue(metrics, ['fcf_margin', 'fcf余力']),
    dividend_growth_years: finiteNumber(dividendData?.consecutive_dividend_increase_years),
  };
  const currentYield = price && annualDps ? annualDps / price * 100 : null;
  return { dividend, financial, dividendData, financialData, marketData, values, currentYield };
}

async function buildAnalysisSnapshot(env, security, targetYield = null, preloaded = null) {
  const stockDefs = preloaded?.stockDefs || await appDefinitionDocument(env, 'stock-analysis-definitions:v1') || {};
  const watchDefs = preloaded?.watchDefs || await appDefinitionDocument(env, 'watchlist-analysis-definitions:v1') || {};
  if (!preloaded?.skipDefinitionAudit) {
    await Promise.all([
      auditDefinition(env, 'stock-analysis-definitions:v1', stockDefs),
      auditDefinition(env, 'watchlist-analysis-definitions:v1', watchDefs),
    ]);
  }
  const input = preloaded?.input || await loadSecurityAnalysisInput(env, security, preloaded?.marketIndex);
  const analysis = evaluateStockAnalysis(input.values, stockDefs);
  const flags = anomalyFlags(input.values);
  const target = finiteNumber(targetYield);
  const ratio = target && target > 0 && input.currentYield !== null ? input.currentYield / target : null;
  const buyZone = buyZoneForRatio(ratio, watchDefs);
  const config = preloaded?.config || await backendConfig(env);
  return {
    code: security.security_code_display,
    canonical_code: security.security_code,
    name: security.name,
    observed_at: isoNow(),
    market_date: input.marketData?.price?.date ?? null,
    definition_version: Number(stockDefs.version || 1),
    watchlist_definition_version: Number(watchDefs.version || 1),
    values: input.values,
    anomalies: flags,
    analysis,
    current_yield: input.currentYield,
    target_yield: target,
    buy_zone_ratio: ratio,
    buy_zone: buyZone ? { status: buyZone.status, label: buyZone.label, comment: buyZone.comment, detail: buyZone.detail } : null,
    freshness: {
      market_generated_at: input.marketData?.price?.generated_at ?? null,
      market_age_days: daysOld(input.marketData?.price?.generated_at),
      market_stale: (daysOld(input.marketData?.price?.generated_at) ?? 999) > Number(config.market_stale_days || 3),
      financial_fiscal_year: input.financial?.fiscal_year ?? null,
      dividend_fiscal_year: input.dividend?.fiscal_year ?? null
    }
  };
}

async function persistSnapshot(env, security, snapshot) {
  const previous = await env.DB.prepare(
    'SELECT overall_grade, buy_zone_status, analysis_json FROM stock_analysis_state WHERE security_code = ?'
  ).bind(security.security_code).first();
  const payload = JSON.stringify(snapshot);
  let historyChanged = true;
  if (previous?.analysis_json) {
    try {
      const oldSnapshot = JSON.parse(previous.analysis_json);
      const newSnapshot = JSON.parse(payload);
      delete oldSnapshot.observed_at;
      delete newSnapshot.observed_at;
      if (oldSnapshot.freshness) delete oldSnapshot.freshness.market_generated_at;
      if (newSnapshot.freshness) delete newSnapshot.freshness.market_generated_at;
      historyChanged = JSON.stringify(oldSnapshot) !== JSON.stringify(newSnapshot);
    } catch {
      // Preserve history if an older or malformed snapshot cannot be compared safely.
    }
  }
  const statements = [];
  if (historyChanged) {
    statements.push(env.DB.prepare(
      `INSERT INTO stock_analysis_history(
        security_code, observed_at, market_date, definition_version, overall_score, overall_grade,
        buy_zone_status, buy_zone_ratio, anomaly_flags_json, metrics_json, analysis_json
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`
    ).bind(
      security.security_code,
      snapshot.observed_at,
      snapshot.market_date,
      snapshot.definition_version,
      snapshot.analysis.overall_score,
      snapshot.analysis.overall_grade,
      snapshot.buy_zone?.status ?? null,
      snapshot.buy_zone_ratio,
      JSON.stringify(snapshot.anomalies),
      JSON.stringify(snapshot.values),
      payload,
    ));
  }
  statements.push(env.DB.prepare(
      `INSERT INTO stock_analysis_state(
        security_code, last_observed_at, market_date, definition_version, overall_score, overall_grade,
        buy_zone_status, buy_zone_ratio, anomaly_flags_json, analysis_json
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
      ON CONFLICT(security_code) DO UPDATE SET
        last_observed_at=excluded.last_observed_at,
        market_date=excluded.market_date,
        definition_version=excluded.definition_version,
        overall_score=excluded.overall_score,
        overall_grade=excluded.overall_grade,
        buy_zone_status=excluded.buy_zone_status,
        buy_zone_ratio=excluded.buy_zone_ratio,
        anomaly_flags_json=excluded.anomaly_flags_json,
        analysis_json=excluded.analysis_json`
    ).bind(
      security.security_code,
      snapshot.observed_at,
      snapshot.market_date,
      snapshot.definition_version,
      snapshot.analysis.overall_score,
      snapshot.analysis.overall_grade,
      snapshot.buy_zone?.status ?? null,
      snapshot.buy_zone_ratio,
      JSON.stringify(snapshot.anomalies),
      payload,
  ));
  await env.DB.batch(statements);
  const events = [];
  if (previous?.overall_grade && previous.overall_grade !== snapshot.analysis.overall_grade) {
    events.push({ type: 'GRADE_CHANGED', from: previous.overall_grade, to: snapshot.analysis.overall_grade });
  }
  if (previous?.buy_zone_status && previous.buy_zone_status !== snapshot.buy_zone?.status) {
    events.push({ type: 'BUY_ZONE_CHANGED', from: previous.buy_zone_status, to: snapshot.buy_zone?.status ?? null });
  }
  for (const event of events) {
    await env.DB.prepare(
      `INSERT INTO notification_events(security_code, event_type, from_status, to_status, occurred_at, payload_json)
       VALUES (?, ?, ?, ?, ?, ?)`
    ).bind(security.security_code, event.type, event.from, event.to, snapshot.observed_at, payload).run();
  }
}

async function compactAnalysisHistory(env) {
  await env.DB.prepare(`DELETE FROM stock_analysis_history
    WHERE observed_at < datetime('now','-30 days')
      AND id NOT IN (
        SELECT MAX(id) FROM stock_analysis_history
        WHERE observed_at < datetime('now','-30 days')
        GROUP BY security_code, date(observed_at)
      )`).run();
  await env.DB.prepare(`DELETE FROM stock_analysis_history
    WHERE observed_at < datetime('now','-365 days')
      AND id NOT IN (
        SELECT MAX(id) FROM stock_analysis_history
        WHERE observed_at < datetime('now','-365 days')
        GROUP BY security_code, strftime('%Y-%m', observed_at)
      )`).run();
  await env.DB.prepare(`DELETE FROM api_request_log WHERE window_started_at < datetime('now','-1 day')`).run();
  await env.DB.prepare(`DELETE FROM scheduled_run_log WHERE started_at < datetime('now','-365 days')`).run();
  await env.DB.prepare(`DELETE FROM notification_events
    WHERE occurred_at < datetime('now','-365 days')
      AND (delivered_at IS NOT NULL OR json_extract(payload_json, '$.client_id') IS NULL)`).run();
}

function base64Url(bytes) {
  let binary = '';
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary).replace(/=/g, '').replace(/\+/g, '-').replace(/\//g, '_');
}

function utf8Base64Url(value) {
  return base64Url(new TextEncoder().encode(value));
}

async function fcmAccessToken(env) {
  if (!env.FCM_SERVICE_ACCOUNT_JSON) return null;
  const serviceAccount = JSON.parse(env.FCM_SERVICE_ACCOUNT_JSON);
  if (!serviceAccount.client_email || !serviceAccount.private_key) throw new Error('invalid_fcm_service_account');
  const tokenUri = serviceAccount.token_uri || 'https://oauth2.googleapis.com/token';
  const issuedAt = Math.floor(Date.now() / 1000);
  const header = utf8Base64Url(JSON.stringify({ alg: 'RS256', typ: 'JWT' }));
  const claims = utf8Base64Url(JSON.stringify({
    iss: serviceAccount.client_email,
    scope: 'https://www.googleapis.com/auth/firebase.messaging',
    aud: tokenUri,
    iat: issuedAt,
    exp: issuedAt + 3600,
  }));
  const unsigned = `${header}.${claims}`;
  const pem = String(serviceAccount.private_key).replace(/-----BEGIN PRIVATE KEY-----|-----END PRIVATE KEY-----|\s/g, '');
  const der = Uint8Array.from(atob(pem), character => character.charCodeAt(0));
  const signingKey = await crypto.subtle.importKey('pkcs8', der, { name: 'RSASSA-PKCS1-v1_5', hash: 'SHA-256' }, false, ['sign']);
  const signature = new Uint8Array(await crypto.subtle.sign('RSASSA-PKCS1-v1_5', signingKey, new TextEncoder().encode(unsigned)));
  const assertion = `${unsigned}.${base64Url(signature)}`;
  const response = await fetch(tokenUri, {
    method: 'POST',
    headers: { 'content-type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({ grant_type: 'urn:ietf:params:oauth:grant-type:jwt-bearer', assertion }),
  });
  if (!response.ok) throw new Error(`fcm_oauth_failed_${response.status}`);
  const token = await response.json();
  return token.access_token || null;
}

async function deliverPendingNotifications(env) {
  if (!env.FCM_PROJECT_ID || !env.FCM_SERVICE_ACCOUNT_JSON) return { sent: 0, skipped: true, reason: 'fcm_not_configured' };
  const accessToken = await fcmAccessToken(env);
  if (!accessToken) return { sent: 0, skipped: true, reason: 'fcm_token_unavailable' };
  const result = await env.DB.prepare(`SELECT id, payload_json FROM notification_events
    WHERE delivered_at IS NULL AND json_extract(payload_json, '$.client_id') IS NOT NULL
    ORDER BY occurred_at LIMIT 100`).all();
  let sent = 0;
  let failed = 0;
  let noDevices = 0;
  for (const row of result.results || []) {
    const payload = JSON.parse(row.payload_json || '{}');
    const clientId = payload.client_id;
    const devices = await env.DB.prepare('SELECT device_token FROM push_devices WHERE client_id = ? AND enabled = 1').bind(clientId).all();
    if (!(devices.results || []).length) { noDevices += 1; continue; }
    for (const device of devices.results || []) {
      const prior = await env.DB.prepare(`SELECT delivered_at, terminal_at FROM notification_deliveries WHERE event_id = ? AND device_token = ?`).bind(row.id, device.device_token).first();
      if (prior?.delivered_at || prior?.terminal_at) continue;
      const response = await fetch(`https://fcm.googleapis.com/v1/projects/${env.FCM_PROJECT_ID}/messages:send`, {
        method: 'POST',
        headers: { 'authorization': `Bearer ${accessToken}`, 'content-type': 'application/json' },
        body: JSON.stringify({ message: { token: device.device_token, notification: { title: 'Diview 買場通知', body: `${payload.name || payload.code || '銘柄'}の買場判定が更新されました。` }, data: { security_code: String(payload.code || ''), event_type: 'WATCHLIST_BUY_ZONE_CHANGED' } } })
      });
      const responseBody = await response.json().catch(() => ({}));
      const errorDetails = responseBody?.error?.details || [];
      const unregistered = errorDetails.some(detail => detail?.errorCode === 'UNREGISTERED');
      const now = isoNow();
      await env.DB.prepare(`INSERT INTO notification_deliveries(event_id, device_token, attempts, delivered_at, terminal_at, last_error, updated_at)
        VALUES (?, ?, 1, ?, ?, ?, ?)
        ON CONFLICT(event_id, device_token) DO UPDATE SET
          attempts=notification_deliveries.attempts+1,
          delivered_at=COALESCE(notification_deliveries.delivered_at, excluded.delivered_at),
          terminal_at=COALESCE(notification_deliveries.terminal_at, excluded.terminal_at),
          last_error=excluded.last_error,
          updated_at=excluded.updated_at`)
        .bind(row.id, device.device_token, response.ok ? now : null, unregistered ? now : null, response.ok ? null : String(responseBody?.error?.status || `http_${response.status}`).slice(0, 120), now).run();
      if (response.ok) sent += 1;
      else {
        failed += 1;
        if (unregistered) await env.DB.prepare('UPDATE push_devices SET enabled=0, updated_at=? WHERE client_id=? AND device_token=?').bind(now, clientId, device.device_token).run();
      }
    }
    const unresolved = await env.DB.prepare(`SELECT COUNT(*) AS count FROM push_devices d
      LEFT JOIN notification_deliveries nd ON nd.event_id = ? AND nd.device_token = d.device_token
      WHERE d.client_id = ? AND d.enabled = 1 AND nd.delivered_at IS NULL AND nd.terminal_at IS NULL`).bind(row.id, clientId).first();
    if (Number(unresolved?.count || 0) === 0) await env.DB.prepare('UPDATE notification_events SET delivered_at=? WHERE id=?').bind(isoNow(), row.id).run();
  }
  return { sent, failed, no_devices: noDevices, skipped: false };
}

async function runScheduledAnalysis(env) {
  const runStartedMs = Date.now();
  const startedAt = isoNow();
  const log = await env.DB.prepare(`INSERT INTO scheduled_run_log(run_type, started_at, status) VALUES ('analysis', ?, 'running')`).bind(startedAt).run();
  const runId = log.meta?.last_row_id;
  let processed = 0;
  let failed = 0;
  const failureSamples = [];
  try {
    const stockDefs = await appDefinitionDocument(env, 'stock-analysis-definitions:v1') || {};
    const watchDefs = await appDefinitionDocument(env, 'watchlist-analysis-definitions:v1') || {};
    const config = await backendConfig(env);
    await Promise.all([auditDefinition(env, 'stock-analysis-definitions:v1', stockDefs), auditDefinition(env, 'watchlist-analysis-definitions:v1', watchDefs)]);
    const activeTargets = await env.DB.prepare(`SELECT s.security_code, s.security_code_display, s.name, s.metrics_json, w.client_id, w.target_yield
      FROM securities s JOIN watchlist_targets w ON w.security_code = s.security_code
      WHERE w.alert_enabled = 1 ORDER BY s.security_code, w.client_id`).all();
    const targetsBySecurity = new Map();
    const watchedBySecurity = new Map();
    for (const row of activeTargets.results || []) {
      watchedBySecurity.set(row.security_code, {
        security_code: row.security_code,
        security_code_display: row.security_code_display,
        name: row.name,
        metrics_json: row.metrics_json,
      });
      const targets = targetsBySecurity.get(row.security_code) || [];
      targets.push({ client_id: row.client_id, target_yield: row.target_yield, alert_enabled: 1 });
      targetsBySecurity.set(row.security_code, targets);
    }
    const latestState = await env.DB.prepare(`SELECT value_json FROM backend_state WHERE state_key = 'analysis_source'`).first();
    const marketObject = await env.MARKET_DATA?.head('market/yfinance/latest_market_data.json');
    const sourceTag = JSON.stringify({ etag: marketObject?.httpEtag || null, stock_version: stockDefs.version || 1, watch_version: watchDefs.version || 1 });
    const sourceChanged = latestState?.value_json !== sourceTag;
    const securities = sourceChanged
      ? (await env.DB.prepare('SELECT security_code, security_code_display, name, metrics_json FROM securities ORDER BY security_code').all()).results || []
      : [...watchedBySecurity.values()];
    const marketIndex = securities.length ? await loadMarketIndex(env) : new Map();
    const analysisStartedMs = Date.now();
    for (let offset = 0; offset < securities.length; offset += ANALYSIS_CONCURRENCY) {
      const group = securities.slice(offset, offset + ANALYSIS_CONCURRENCY);
      await Promise.all(group.map(async security => {
        try {
          const targetRows = targetsBySecurity.get(security.security_code) || [];
          const input = await loadSecurityAnalysisInput(env, security, marketIndex);
          const preloaded = { stockDefs, watchDefs, config, input, skipDefinitionAudit: true };
          const base = await buildAnalysisSnapshot(env, security, null, preloaded);
          if (sourceChanged) await persistSnapshot(env, security, base);
          if (!base.freshness.market_stale) {
            for (const targetRow of targetRows) {
              const targeted = await buildAnalysisSnapshot(env, security, Number(targetRow.target_yield), preloaded);
              const previous = await env.DB.prepare(`SELECT to_status FROM notification_events WHERE security_code = ? AND event_type = ? AND json_extract(payload_json, '$.client_id') = ? ORDER BY occurred_at DESC LIMIT 1`).bind(security.security_code, 'WATCHLIST_BUY_ZONE_CHANGED', targetRow.client_id).first();
              const currentStatus = targeted.buy_zone?.status ?? null;
              if (currentStatus && previous?.to_status !== currentStatus) await env.DB.prepare(`INSERT INTO notification_events(security_code, event_type, from_status, to_status, occurred_at, payload_json) VALUES (?, ?, ?, ?, ?, ?)`).bind(security.security_code, 'WATCHLIST_BUY_ZONE_CHANGED', previous?.to_status ?? null, currentStatus, targeted.observed_at, JSON.stringify({ ...targeted, client_id: targetRow.client_id })).run();
            }
          }
          processed += 1;
        } catch (error) {
          failed += 1;
          if (failureSamples.length < 20) {
            failureSamples.push({
              security_code: security.security_code_display,
              error: String(error?.name || 'Error').slice(0, 80),
            });
          }
        }
      }));
    }
    const analysisDurationMs = Date.now() - analysisStartedMs;
    const sourceTagAdvanced = sourceChanged && failed === 0;
    if (sourceTagAdvanced) await env.DB.prepare(`INSERT INTO backend_state(state_key, value_json, updated_at) VALUES ('analysis_source', ?, ?) ON CONFLICT(state_key) DO UPDATE SET value_json=excluded.value_json, updated_at=excluded.updated_at`).bind(sourceTag, isoNow()).run();
    const cleanupStartedMs = Date.now();
    await compactAnalysisHistory(env);
    const cleanupDurationMs = Date.now() - cleanupStartedMs;
    const notificationsStartedMs = Date.now();
    const delivery = await deliverPendingNotifications(env);
    const notificationsDurationMs = Date.now() - notificationsStartedMs;
    const durations = {
      analysis_ms: analysisDurationMs,
      cleanup_ms: cleanupDurationMs,
      notifications_ms: notificationsDurationMs,
      total_ms: Date.now() - runStartedMs,
    };
    if (runId) await env.DB.prepare(`UPDATE scheduled_run_log SET finished_at=?, status=?, processed=?, failed=?, metadata_json=? WHERE id=?`).bind(isoNow(), failed ? 'partial' : 'success', processed, failed, JSON.stringify({ source_changed: sourceChanged, source_tag_advanced: sourceTagAdvanced, failure_samples: failureSamples, delivery, durations_ms: durations }), runId).run();
    return { processed, failed, source_changed: sourceChanged, source_tag_advanced: sourceTagAdvanced, failure_samples: failureSamples, delivery, durations_ms: durations, observed_at: isoNow() };
  } catch (error) {
    if (runId) await env.DB.prepare(`UPDATE scheduled_run_log SET finished_at=?, status='failed', processed=?, failed=?, metadata_json=? WHERE id=?`).bind(isoNow(), processed, failed + 1, JSON.stringify({ error: String(error?.message || error), durations_ms: { total_ms: Date.now() - runStartedMs } }), runId).run();
    throw error;
  }
}

async function irbankR2Object(env, key) {
  if (!env.MARKET_DATA) return json({ error: 'r2_unavailable' }, 503);
  const object = await env.MARKET_DATA.get(key);
  if (!object) return json({ error: 'not_found' }, 404);
  return new Response(object.body, {
    headers: {
      'content-type': 'application/json; charset=utf-8',
      'cache-control': 'public, max-age=300',
      'access-control-allow-origin': '*',
      'etag': object.httpEtag,
    }
  });
}

function irbankR2Code(value) {
  const code = value.toUpperCase();
  if (/^[A-Z0-9]{4}$/.test(code)) return code;
  if (/^[A-Z0-9]{4}0$/.test(code)) return code.slice(0, 4);
  return null;
}

export default {
  async fetch(request, env) {
    const url = new URL(request.url);

    const config = await backendConfig(env);

    if (request.method === 'OPTIONS') return new Response(null, { status: 204, headers: json(null).headers });

    const isClientScopedRead = url.pathname === '/api/v1/watchlist-targets' || url.pathname === '/api/v1/notification-events';
    const rateLimitRoute = request.method === 'GET' && !isClientScopedRead ? publicRateLimitRoute(url.pathname) : null;
    if (rateLimitRoute && !await enforceRateLimit(request, env, `public:${rateLimitRoute}`, config.rate_limit_public, '', config)) {
      return rateLimitedResponse(config);
    }

    if (url.pathname === '/api/v1/admin/run-analysis' && request.method === 'POST') {
      const provided = String(request.headers.get('X-Admin-Secret') || '');
      if (!env.ADMIN_SECRET || provided !== env.ADMIN_SECRET) return json({ error: 'unauthorized' }, 401);
      return json(await runScheduledAnalysis(env));
    }

    if (url.pathname === '/api/v1/clients/register' && request.method === 'POST') {
      if (!await enforceRateLimit(request, env, 'clients-register', config.rate_limit_register, '', config)) return rateLimitedResponse(config);
      const clientId = crypto.randomUUID();
      const secret = randomToken(32);
      await env.DB.prepare('INSERT INTO clients(client_id, client_secret_hash, created_at, last_seen_at) VALUES (?, ?, ?, ?)').bind(clientId, await sha256Hex(secret), isoNow(), isoNow()).run();
      return json({ client_id: clientId, client_secret: secret }, 201);
    }

    if (url.pathname === '/api/v1/push-devices' && request.method === 'PUT') {
      const clientId = await authenticateClient(request, env);
      if (!clientId) return json({ error: 'unauthorized' }, 401);
      if (!await enforceRateLimit(request, env, 'push-devices-put', config.rate_limit_authenticated, clientId, config)) return rateLimitedResponse(config);
      const body = await readJson(request);
      const token = String(body?.device_token || '').trim();
      const platform = String(body?.platform || 'android').trim();
      if (!token || token.length > 4096 || !['android','ios','web'].includes(platform)) return json({ error: 'invalid_request' }, 400);
      await env.DB.prepare(`INSERT INTO push_devices(client_id, device_token, platform, enabled, created_at, updated_at) VALUES (?, ?, ?, 1, ?, ?) ON CONFLICT(client_id, device_token) DO UPDATE SET platform=excluded.platform, enabled=1, updated_at=excluded.updated_at`).bind(clientId, token, platform, isoNow(), isoNow()).run();
      return json({ ok: true });
    }

    if (url.pathname === '/api/v1/push-devices' && request.method === 'DELETE') {
      const clientId = await authenticateClient(request, env);
      if (!clientId) return json({ error: 'unauthorized' }, 401);
      if (!await enforceRateLimit(request, env, 'push-devices-delete', config.rate_limit_authenticated, clientId, config)) return rateLimitedResponse(config);
      const body = await readJson(request);
      const token = String(body?.device_token || '').trim();
      if (!token) return json({ error: 'invalid_request' }, 400);
      await env.DB.prepare('UPDATE push_devices SET enabled=0, updated_at=? WHERE client_id=? AND device_token=?').bind(isoNow(), clientId, token).run();
      return json({ ok: true });
    }

    if (url.pathname === '/api/v1/watchlist-targets' && request.method === 'PUT') {
      const clientId = await authenticateClient(request, env);
      if (!clientId) return json({ error: 'unauthorized' }, 401);
      if (!await enforceRateLimit(request, env, 'watchlist-put', config.rate_limit_authenticated, clientId, config)) return rateLimitedResponse(config);
      const body = await readJson(request);
      const code = normalizeCode(String(body?.code || '').trim());
      const targetYield = finiteNumber(body?.target_yield);
      const alertEnabled = body?.alert_enabled === false ? 0 : 1;
      if (!clientId || !code || targetYield === null || targetYield <= 0) {
        return json({ error: 'invalid_request' }, 400);
      }
      const security = await env.DB.prepare(
        'SELECT security_code FROM securities WHERE security_code = ? OR security_code_display = ? LIMIT 1'
      ).bind(code, String(body?.code || '').trim()).first();
      if (!security) return json({ error: 'not_found' }, 404);
      await env.DB.prepare(
        `INSERT INTO watchlist_targets(client_id, security_code, target_yield, alert_enabled, updated_at)
         VALUES (?, ?, ?, ?, ?)
         ON CONFLICT(client_id, security_code) DO UPDATE SET
           target_yield=excluded.target_yield,
           alert_enabled=excluded.alert_enabled,
           updated_at=excluded.updated_at`
      ).bind(clientId, security.security_code, targetYield, alertEnabled, isoNow()).run();
      return json({ ok: true });
    }

    if (url.pathname === '/api/v1/watchlist-targets' && request.method === 'GET') {
      const clientId = await authenticateClient(request, env);
      if (!clientId) return json({ error: 'unauthorized' }, 401);
      if (!await enforceRateLimit(request, env, 'watchlist-get', config.rate_limit_authenticated, clientId, config)) return rateLimitedResponse(config);
      const result = await env.DB.prepare(`SELECT s.security_code_display AS code, s.name, w.target_yield, w.alert_enabled, w.updated_at
        FROM watchlist_targets w JOIN securities s ON s.security_code = w.security_code
        WHERE w.client_id = ? ORDER BY s.security_code`).bind(clientId).all();
      return json({ items: result.results || [] });
    }

    if (url.pathname === '/api/v1/watchlist-targets' && request.method === 'DELETE') {
      const clientId = await authenticateClient(request, env);
      if (!clientId) return json({ error: 'unauthorized' }, 401);
      if (!await enforceRateLimit(request, env, 'watchlist-delete', config.rate_limit_authenticated, clientId, config)) return rateLimitedResponse(config);
      const body = await readJson(request);
      const code = normalizeCode(String(body?.code || '').trim());
      if (!clientId || !code) return json({ error: 'invalid_request' }, 400);
      await env.DB.prepare(
        'DELETE FROM watchlist_targets WHERE client_id = ? AND security_code = ?'
      ).bind(clientId, code).run();
      return json({ ok: true });
    }

    if (url.pathname === '/api/v1/health' && request.method === 'GET') {
      const latestRun = await env.DB.prepare(`SELECT started_at, finished_at, status, processed, failed FROM scheduled_run_log ORDER BY id DESC LIMIT 1`).first();
      const marketObject = await env.MARKET_DATA?.head('market/yfinance/latest_market_data.json');
      const stockDefs = await appDefinitionDocument(env, 'stock-analysis-definitions:v1') || {};
      const watchDefs = await appDefinitionDocument(env, 'watchlist-analysis-definitions:v1') || {};
      return json({ ok: true, checked_at: isoNow(), d1: true, market: { available: !!marketObject, uploaded: marketObject?.uploaded?.toISOString?.() || null, age_days: marketObject?.uploaded ? daysOld(marketObject.uploaded.toISOString()) : null, stale_after_days: Number(config.market_stale_days || 3) }, definitions: { stock_version: stockDefs.version || null, watchlist_version: watchDefs.version || null }, latest_scheduled_run: latestRun || null });
    }

    if (request.method !== 'GET') return json({ error: 'method_not_allowed' }, 405);

    if (url.pathname === '/api/v1/popular-holdings') {
      if (!env.MARKET_DATA) return json({ error: 'market_data_unavailable' }, 503);
      const requestedDate = String(url.searchParams.get('date') || '');
      if (!/^\d{4}-\d{2}-\d{2}$/.test(requestedDate)) return json({ error: 'invalid_request' }, 400);
      const offset = Math.max(0, Math.min(100000, Number.parseInt(url.searchParams.get('offset') || '0', 10) || 0));
      const limit = Math.max(1, Math.min(20, Number.parseInt(url.searchParams.get('limit') || '20', 10) || 20));
      const catalogObject = await env.MARKET_DATA.get('market/yfinance/search_catalog.json');
      if (!catalogObject) return json({ error: 'search_catalog_unavailable' }, 503);
      const catalog = JSON.parse(await catalogObject.text());
      const candidates = (catalog.items || [])
        .filter(row => irbankR2Code(String(row?.code || ''))
          && finiteNumber(row?.price) > 0
          && finiteNumber(row?.estimated_average_trading_value_20d) > 0)
        .sort((a, b) => finiteNumber(b.estimated_average_trading_value_20d) - finiteNumber(a.estimated_average_trading_value_20d)
          || String(a.code).localeCompare(String(b.code)));
      const batch = candidates.slice(offset, offset + limit);
      const items = (await Promise.all(batch.map(async row => {
        const code = irbankR2Code(String(row.code));
        if (!code) return null;
        const object = await env.MARKET_DATA.get(`market/yfinance/securities/${code}.T.json`);
        if (!object) return null;
        const document = JSON.parse(await object.text());
        const prices = document.price_history?.['1d'];
        if (!Array.isArray(prices)) return null;
        const point = prices.reduce((best, item) => {
          const date = String(item?.date || '');
          const close = finiteNumber(item?.close);
          return date && date <= requestedDate && close > 0 && (!best || date > best.date)
            ? { date, close }
            : best;
        }, null);
        if (!point) return null;
        const currentPrice = finiteNumber(row.price);
        if (!(currentPrice > 0)) return null;
        const statistics = document.statistics_current || {};
        const dps = finiteNumber(statistics.dividendRate);
        const yieldRate = dps !== null && dps >= 0
          ? (dps / currentPrice) * 100
          : finiteNumber(row.yield_rate);
        const industry = String(statistics.industry || statistics.sector || '').trim();
        return {
          code,
          name: String(row.name || code),
          sector: industry || null,
          current_price: currentPrice,
          acquisition_price: point.close,
          acquisition_date: point.date,
          annual_dividend_per_share: dps !== null && dps >= 0 ? dps : null,
          yield_rate: yieldRate,
          estimated_average_trading_value_20d: finiteNumber(row.estimated_average_trading_value_20d),
        };
      }))).filter(Boolean);
      const response = json({
        items,
        next_offset: offset + batch.length,
        total_count: candidates.length,
        acquisition_date: requestedDate,
        ranking: 'estimated_average_trading_value_20d',
      });
      response.headers.set('cache-control', 'public, max-age=300');
      return response;
    }

    if (url.pathname === '/api/v1/monthly-price-history') {
      if (!env.MARKET_DATA) return json({ error: 'market_data_unavailable' }, 503);
      const codes = [...new Set(String(url.searchParams.get('codes') || '')
        .split(',')
        .map(value => irbankR2Code(value.trim()))
        .filter(Boolean))];
      const months = [...new Set(String(url.searchParams.get('months') || '')
        .split(',')
        .map(value => value.trim())
        .filter(value => /^\d{4}-(0[1-9]|1[0-2])$/.test(value)))];
      if (codes.length === 0 || codes.length > 20 || months.length === 0 || months.length > 12) {
        return json({ error: 'invalid_request' }, 400);
      }

      const todayParts = Object.fromEntries(new Intl.DateTimeFormat('en', {
        timeZone: 'Asia/Tokyo', year: 'numeric', month: '2-digit', day: '2-digit'
      }).formatToParts(new Date()).map(part => [part.type, part.value]));
      const today = `${todayParts.year}-${todayParts.month}-${todayParts.day}`;
      const currentMonth = today.slice(0, 7);
      const cutoffs = months.map(month => {
        if (month >= currentMonth) return [month, today];
        const nextMonth = new Date(`${month}-01T00:00:00.000Z`);
        nextMonth.setUTCMonth(nextMonth.getUTCMonth() + 1);
        nextMonth.setUTCDate(0);
        return [month, nextMonth.toISOString().slice(0, 10)];
      });
      const items = (await Promise.all(codes.map(async code => {
        const object = await env.MARKET_DATA.get(`market/yfinance/securities/${code}.T.json`);
        if (!object) return null;
        const document = JSON.parse(await object.text());
        const prices = document.price_history?.['1d'];
        if (!Array.isArray(prices)) return null;
        const splits = (document.actions?.stock_splits || [])
          .map(item => ({ date: String(item?.date || ''), ratio: finiteNumber(item?.ratio) }))
          .filter(item => item.date && item.ratio > 0);
        const monthlyPrices = cutoffs.map(([month, cutoff]) => {
          const point = prices.reduce((best, item) => {
            const date = String(item?.date || '');
            const close = finiteNumber(item?.close);
            return date && date <= cutoff && close > 0 && (!best || date > best.date)
              ? { date, close }
              : best;
          }, null);
          if (!point) return null;
          const splitFactor = splits
            .filter(item => item.date > point.date && item.date <= today)
            .reduce((factor, item) => factor * item.ratio, 1);
          const close = point.close / splitFactor;
          if (!(close > 0) || !Number.isFinite(close)) return null;
          return { month, date: point.date, close };
        }).filter(Boolean);
        return { code, prices: monthlyPrices };
      }))).filter(Boolean);
      const response = json({ items, requested_months: months });
      response.headers.set('cache-control', 'public, max-age=300');
      return response;
    }

    if (url.pathname === '/api/v1/search-catalog') {
      const object = await env.MARKET_DATA?.get('market/yfinance/search_catalog.json');
      if (!object) return json({ error: 'search_catalog_unavailable' }, 503);
      return new Response(object.body, {
        headers: {
          'content-type': 'application/json; charset=utf-8',
          'cache-control': 'public, max-age=300',
          'access-control-allow-origin': '*',
          'etag': object.httpEtag,
        }
      });
    }

    if (url.pathname === '/api/v1/security-search') {
      const query = String(url.searchParams.get('q') || '').trim().slice(0, 100);
      if (!query) return json({ items: [] });
      const like = `%${query}%`;
      const prefix = `${query}%`;
      const exact = query.toUpperCase();
      const result = await env.DB.prepare(
        `SELECT security_code_display AS code, name
         FROM securities
         WHERE security_code_display LIKE ? OR name LIKE ?
         ORDER BY CASE WHEN upper(security_code_display) = ? THEN 0
                       WHEN upper(security_code_display) LIKE ? THEN 1 ELSE 2 END,
                  name COLLATE NOCASE
         LIMIT 30`
      ).bind(like, like, exact, prefix.toUpperCase()).all();
      return json({ items: result.results || [] });
    }

    if (url.pathname === '/api/v1/irbank/manifest') {
      return irbankR2Object(env, 'market/irbank/v1/manifest.json');
    }

    if (url.pathname === '/api/v1/irbank/catalog') {
      return irbankR2Object(env, 'market/irbank/v1/catalog.json');
    }

    const irbankMatch = url.pathname.match(/^\/api\/v1\/irbank\/securities\/([^/]+)(?:\/(prices|weekly-margin))?$/);
    if (irbankMatch) {
      const code = irbankR2Code(decodeURIComponent(irbankMatch[1]));
      if (!code) return json({ error: 'invalid_security_code' }, 400);
      const familyFile = irbankMatch[2] === 'prices'
        ? 'daily_prices.json'
        : irbankMatch[2] === 'weekly-margin'
          ? 'weekly_margin.json'
          : 'overview.json';
      return irbankR2Object(env, `market/irbank/v1/securities/${code}/${familyFile}`);
    }

    if (url.pathname === '/api/v1/diagnosis-definitions') {
      const items = await appDefinitionItems(env, 'diagnosis-definitions:v1');
      return json({ items });
    }

    if (url.pathname === '/api/v1/valuation-scenarios') {
      const items = await appDefinitionItems(env, 'valuation-scenarios:v1');
      return json({ items });
    }

    if (url.pathname === '/api/v1/stock-analysis-definitions') {
      return json(await appDefinitionDocument(env, 'stock-analysis-definitions:v1') || {});
    }

    if (url.pathname === '/api/v1/watchlist-analysis-definitions') {
      return json(await appDefinitionDocument(env, 'watchlist-analysis-definitions:v1') || {});
    }

    if (url.pathname === '/api/v1/portfolio-analysis-definitions') {
      return json(await appDefinitionDocument(env, 'portfolio-analysis-definitions:v1') || {});
    }

    if (url.pathname === '/api/v1/definition-audit') {
      const result = await env.DB.prepare(
        `SELECT definition_key, version, content_hash, updated_at, metadata_json
         FROM app_definition_audit ORDER BY updated_at DESC LIMIT 100`
      ).all();
      return json({ items: (result.results || []).map(row => ({
        ...row,
        metadata: JSON.parse(row.metadata_json || '{}'),
      })) });
    }

    if (url.pathname === '/api/v1/notification-events') {
      const clientId = await authenticateClient(request, env);
      if (!clientId) return json({ error: 'unauthorized' }, 401);
      if (!await enforceRateLimit(request, env, 'notification-events', config.rate_limit_authenticated, clientId, config)) return rateLimitedResponse(config);
      const result = await env.DB.prepare(
          `SELECT id, security_code, event_type, from_status, to_status, occurred_at, payload_json, delivered_at
           FROM notification_events
           WHERE json_extract(payload_json, '$.client_id') = ?
           ORDER BY occurred_at DESC LIMIT 100`
        ).bind(clientId).all();
      return json({ items: (result.results || []).map(row => ({
        ...row,
        payload: JSON.parse(row.payload_json || '{}'),
      })) });
    }

    const historyMatch = url.pathname.match(/^\/api\/v1\/analysis-history\/([^/]+)$/);
    if (historyMatch) {
      const requested = decodeURIComponent(historyMatch[1]);
      const code = normalizeCode(requested);
      const result = await env.DB.prepare(
        `SELECT observed_at, market_date, definition_version, overall_score, overall_grade,
                buy_zone_status, buy_zone_ratio, anomaly_flags_json, metrics_json, analysis_json
         FROM stock_analysis_history
         WHERE security_code = ?
         ORDER BY observed_at DESC LIMIT 90`
      ).bind(code).all();
      return json({ items: (result.results || []).map(row => ({
        ...row,
        anomaly_flags: JSON.parse(row.anomaly_flags_json || '[]'),
        metrics: JSON.parse(row.metrics_json || '{}'),
        analysis: JSON.parse(row.analysis_json || '{}'),
      })) });
    }

    const analysisMatch = url.pathname.match(/^\/api\/v1\/analysis\/([^/]+)$/);
    if (analysisMatch) {
      const requested = decodeURIComponent(analysisMatch[1]);
      const code = normalizeCode(requested);
      const security = await env.DB.prepare(
        'SELECT security_code, security_code_display, name, metrics_json FROM securities WHERE security_code = ? OR security_code_display = ? LIMIT 1'
      ).bind(code, requested).first();
      if (!security) return json({ error: 'not_found' }, 404);
      const targetYield = url.searchParams.get('target_yield');
      return json(await buildAnalysisSnapshot(env, security, targetYield === null ? null : Number(targetYield)));
    }

    const match = url.pathname.match(/^\/api\/v1\/securities\/([^/]+)$/);
    if (match) {
      const requested = decodeURIComponent(match[1]);
      const code = normalizeCode(requested);
      const security = await env.DB.prepare(
        'SELECT security_code, security_code_display, name, metrics_json FROM securities WHERE security_code = ? OR security_code_display = ? LIMIT 1'
      ).bind(code, requested).first();
      if (!security) return json({ error: 'not_found' }, 404);

      const dividend = await env.DB.prepare(
        `SELECT fiscal_year, annual_dps, annual_dps_adjusted, record_date, final_rights_date, date_status, data_json
         FROM dividend_events WHERE security_code = ? ORDER BY fiscal_year DESC LIMIT 1`
      ).bind(security.security_code).first();
      const financial = await env.DB.prepare(
        `SELECT fiscal_year, accounting_standard, is_consolidated, data_json
         FROM financial_periods WHERE security_code = ? AND period_type = 'annual' ORDER BY fiscal_year DESC LIMIT 1`
      ).bind(security.security_code).first();

      const metrics = JSON.parse(security.metrics_json || '[]');
      const dividendData = dividend ? JSON.parse(dividend.data_json || '{}') : null;
      const financialData = financial ? JSON.parse(financial.data_json || '{}') : null;
      const marketData = url.searchParams.get('include_market') === 'false'
        ? null
        : await latestMarketForSecurity(env, security.security_code_display);
      const marketPrice = marketData?.price ?? null;
      const marketSummary = marketData?.summary;
      return json({
        code: security.security_code_display,
        canonical_code: security.security_code,
        name: security.name,
        metrics,
        dividend: dividend ? {
          fiscal_year: dividend.fiscal_year,
          annual_dps: dividend.annual_dps,
          annual_dps_adjusted: dividend.annual_dps_adjusted,
          payout_ratio: dividendData?.payout_ratio ?? null,
          consecutive_dividend_increase_years: dividendData?.consecutive_dividend_increase_years ?? null,
          record_date: dividend.record_date,
          final_rights_date: dividend.final_rights_date,
          date_status: dividend.date_status
        } : null,
        financial: financial ? {
          fiscal_year: financial.fiscal_year,
          accounting_standard: financial.accounting_standard,
          is_consolidated: financial.is_consolidated,
          data: financialData
        } : null,
        market_price: marketPrice,
        valuation: marketSummary ? {
          per: marketSummary.trailingPE ?? null,
          pbr: marketSummary.priceToBook ?? null
        } : null
      });
    }

    return json({ error: 'not_found' }, 404);
  },

  async scheduled(_controller, env, ctx) {
    ctx.waitUntil(runScheduledAnalysis(env));
  }
};
