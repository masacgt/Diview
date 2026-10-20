import { readFeedback, saveFeedback } from './feedback.mjs';

const ANALYSIS_CONCURRENCY = 8;

function json(data, status = 200) {
  return new Response(JSON.stringify(data), {
    status,
    headers: {
      'content-type': 'application/json; charset=utf-8',
      'cache-control': 'private, no-store',
      'access-control-allow-origin': '*',
      'access-control-allow-methods': 'GET, HEAD, POST, PUT, DELETE, OPTIONS',
      'access-control-allow-headers': 'Content-Type, Range, X-Client-Id, X-Client-Secret, X-Admin-Secret',
      'access-control-expose-headers': 'Accept-Ranges, Content-Length, Content-Range, ETag, Last-Modified',
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
  if (pathname === '/api/v1/live-quotes') return 'live-quotes';
  if (pathname.startsWith('/api/v1/')) return pathname.slice('/api/v1/'.length);
  return null;
}

function finiteNumber(value) {
  return typeof value === 'number' && Number.isFinite(value) ? value : null;
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
  const normalizedCode = yfinanceV2Code(displayCode);
  if (normalizedCode) {
    const symbol = `${normalizedCode}.T`;
    const [currentObject, profileObject] = await Promise.all([
      env.MARKET_DATA.get(`${YFINANCE_V2_ROOT}/securities/${symbol}/current.json`),
      env.MARKET_DATA.get(`${YFINANCE_V2_ROOT}/securities/${symbol}/profile.json`),
    ]);
    if (currentObject) {
      const current = await currentObject.json();
      const profile = profileObject ? await profileObject.json() : {};
      const price = current.price || {};
      const valuation = calculateDailyValuation(current);
      const summary = {
        ...(current.statistics || {}),
        trailingPE: valuation.per,
        priceToBook: valuation.pbr,
        symbol,
        shortName: profile.display_name || profile.short_name || null,
      };
      return { summary, dividend: current.dividend || null, price: {
        symbol,
        date: price.date ?? null,
        open: null,
        high: null,
        low: null,
        close: price.close ?? null,
        adjusted_close: price.close ?? null,
        volume: null,
        change: price.change ?? null,
        change_percent: price.change_percent ?? null,
        source: current.source || 'Yahoo Finance via yfinance',
        generated_at: current.updated_at ?? null,
      }};
    }
  }
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
  const completionObject = await env.MARKET_DATA.get('market/yfinance/live_quotes_completion.json');
  const completion = completionObject ? await completionObject.json() : {};
  const publishedRunId = String(completion.last_completed_run_id || '');
  const publishedDate = String(completion.last_completed_date || '');
  const publishedQuoteObject = publishedRunId
    ? await env.MARKET_DATA.get(liveQuoteRunObjectKey(publishedRunId, String(displayCode).toUpperCase()))
    : null;
  const candidateObject = publishedQuoteObject;
  const candidateQuote = candidateObject ? await candidateObject.json() : null;
  const liveQuote = candidateQuote && (!publishedDate || String(candidateQuote.date || '') <= publishedDate) ? candidateQuote : null;
  const quote = liveQuote || row.latest_daily_price || {};
  return { summary: row, price: {
    symbol: row.symbol,
    date: quote.date ?? null,
    open: quote.open ?? null,
    high: quote.high ?? null,
    low: quote.low ?? null,
    close: quote.close ?? null,
    adjusted_close: quote.adjusted_close ?? null,
    volume: quote.volume ?? null,
    change: liveQuote?.change ?? row.regularMarketChange ?? null,
    change_percent: liveQuote?.change_percent ?? row.regularMarketChangePercent ?? null,
    source: snapshot.source ?? null,
    generated_at: quote.generated_at ?? snapshot.generated_at ?? null
  }};
}

const YFINANCE_V2_ROOT = 'market/yfinance/v2';

// SBI Shinsei's FY2026 securities report documents historical share actions
// that are not all represented in its stored yfinance split history.
const UNVERIFIED_DIVIDEND_SHARE_BASIS_CODES = new Set(['8303']);

function dividendShareBasisStatus(code) {
  const normalized = String(code || '').trim().toUpperCase().replace(/\.T$/, '');
  return UNVERIFIED_DIVIDEND_SHARE_BASIS_CODES.has(normalized)
    ? 'unverified_incomplete_corporate_action_history'
    : null;
}

function calculateDailyValuation(current) {
  const price = finiteNumber(current?.price?.close);
  const statistics = current?.statistics || {};
  const eps = finiteNumber(statistics.trailingEps);
  const bookValue = finiteNumber(statistics.bookValue);
  return {
    per: price > 0 && eps > 0 ? Math.round(price / eps * 1e6) / 1e6 : null,
    pbr: price > 0 && bookValue > 0 ? Math.round(price / bookValue * 1e6) / 1e6 : null,
    source: 'daily_close_divided_by_latest_yfinance_eps_bps',
    price_date: current?.price?.date || null,
    statistics_last_fetched_session_date: current?.statistics_last_fetched_session_date || null,
    formulas: {
      per: 'price.close / statistics.trailingEps',
      pbr: 'price.close / statistics.bookValue',
    },
  };
}

// Company forecasts checked against issuer disclosures on 2026-10-17.
// AOKI: https://ir.aoki-hd.co.jp/ja/news/news/auto_20260508520915/pdfFile.pdf
// Mitsubishi: https://www.mitsubishicorp.com/jp/ja/ir/shareholder/allot/
// Refresh these when the issuers publish their next fiscal-year forecasts.
const OFFICIAL_NEXT_FY_DPS = { '8214': { stale: 120, official: 90 }, '8058': { stale: 126, official: 125 } };

function yfinanceV2Symbol(code) {
  const normalized = yfinanceV2Code(code);
  return normalized ? `${normalized}.T` : null;
}

function selectedAnnualDividendPerShare(code, dividend, updatedAt = '') {
  if (dividend?.dividend_status === 'no_dividend'
    || dividend?.yield_basis === 'yfinance_reported_no_dividend') return 0;
  if (dividend?.yield_basis === 'next_fiscal_year_forecast') {
    const updatedDate = String(updatedAt || '').slice(0, 10);
    const officialOverride = OFFICIAL_NEXT_FY_DPS[code];
    const reported = finiteNumber(dividend.next_fiscal_year_forecast_per_share);
    if (officialOverride && (!updatedDate || updatedDate <= '2027-03-31')
      && (reported === null || Math.abs(reported - officialOverride.stale) < 0.001)) {
      return officialOverride.official;
    }
    return reported;
  }
  if (dividend?.yield_basis === 'current_fiscal_year') {
    return finiteNumber(dividend.current_fiscal_year_per_share);
  }
  return null;
}

function yieldFromDividend(price, annualDps) {
  const close = finiteNumber(price);
  return close !== null && close > 0 && annualDps !== null
    ? Math.round((annualDps / close * 100) * 10000) / 10000
    : null;
}

async function readV2Json(env, key) {
  const object = await env.MARKET_DATA?.get(key);
  return object ? object.json() : null;
}

async function listV2Documents(env, prefix) {
  const documents = [];
  let cursor;
  do {
    const page = await env.MARKET_DATA.list({ prefix, limit: 1000, cursor });
    for (const item of page.objects || []) {
      const document = await readV2Json(env, item.key);
      if (document) documents.push(document);
    }
    cursor = page.truncated ? page.cursor : undefined;
  } while (cursor);
  return documents;
}

async function readV2DividendHistoryBounds(env, symbol) {
  try {
    const [currentDocuments, archivedDocuments] = await Promise.all([
      listV2Documents(env, `${YFINANCE_V2_ROOT}/securities/${symbol}/dividends/`),
      listV2Documents(env, `${YFINANCE_V2_ROOT}/archive/events/${symbol}/dividends/`),
    ]);
    const dates = [...currentDocuments, ...archivedDocuments]
      .flatMap(document => Array.isArray(document.records) ? document.records : [])
      .map(record => String(record?.date || '').slice(0, 10))
      .filter(value => /^\d{4}-\d{2}-\d{2}$/.test(value))
      .sort();
    return {
      status: dates.length ? 'available' : 'empty',
      start: dates[0] || null,
      end: dates.length ? dates[dates.length - 1] : null,
    };
  } catch {
    return { status: 'failed', start: null, end: null };
  }
}

const CATALOG_GENERATION_PATTERN = /^\d{4}-\d{2}-\d{2}(?:-[0-9a-f]{32})?$/;

async function readV2Catalog(env, generation = '') {
  const key = generation
    ? `${YFINANCE_V2_ROOT}/catalog/generations/${generation}.json`
    : `${YFINANCE_V2_ROOT}/catalog/current.json`;
  const catalog = await readV2Json(env, key);
  if (!catalog || !Array.isArray(catalog.items)) return catalog;
  return {
    ...catalog,
    items: catalog.items.map(row => row?.dividend_period !== 'trailing_12_months'
      ? row
      : row?.dividend_status === 'no_dividend'
        ? {
            ...row,
            annual_dividend_per_share: 0,
            dividend_period: 'yfinance_reported_no_dividend',
            yield_rate: 0,
            yield_basis: 'yfinance_reported_no_dividend',
          }
        : {
          ...row,
          annual_dividend_per_share: null,
          dividend_period: 'unknown',
          yield_rate: null,
          yield_basis: null,
        }),
  };
}

function yfinanceCalendarYearDividends(events) {
  if (!Array.isArray(events)) return { records: [], status: 'not_fetched' };
  const currentYear = Number(new Intl.DateTimeFormat('en', {
    timeZone: 'Asia/Tokyo', year: 'numeric'
  }).format(new Date()));
  const yearly = new Map();
  const seenEvents = new Set();
  for (const event of events) {
    const date = typeof event?.date === 'string' ? event.date : '';
    const year = Number(date.slice(0, 4));
    const amount = finiteNumber(event?.amount);
    const parsedDate = /^\d{4}-\d{2}-\d{2}$/.test(date) ? new Date(`${date}T00:00:00.000Z`) : null;
    if (!parsedDate || !Number.isFinite(parsedDate.getTime()) || parsedDate.toISOString().slice(0, 10) !== date || !Number.isInteger(year)
      || year < 1900 || year >= currentYear || amount == null || amount <= 0) continue;
    const eventKey = `${date}:${amount}`;
    if (seenEvents.has(eventKey)) continue;
    seenEvents.add(eventKey);
    yearly.set(year, (yearly.get(year) || 0) + amount);
  }
  const records = [...yearly.entries()].sort(([a], [b]) => a - b)
    .map(([calendar_year, amount]) => ({
      calendar_year,
      calendar_year_distribution_per_share: amount,
      amount_basis: 'yfinance_split_adjusted_assumption',
    }));
  return {
    records,
    status: records.length ? 'available' : 'empty',
    amount_basis: 'yfinance_split_adjusted_assumption',
  };
}

function financialPeriodKey(row) {
  return String(row?.period_end || row?.fiscal_year || row?.year || '').replaceAll('/', '-');
}

async function readV2FinancialPayload(env, code) {
  const symbol = yfinanceV2Symbol(code);
  if (!symbol || !env.MARKET_DATA) return null;
  const base = `${YFINANCE_V2_ROOT}/securities/${symbol}`;
  const [profile, current, financialDocuments, dividendDocuments, issuerDividendDocuments] = await Promise.all([
    readV2Json(env, `${base}/profile.json`),
    readV2Json(env, `${base}/current.json`),
    listV2Documents(env, `${base}/financials/`),
    listV2Documents(env, `${base}/dividends/`),
    listV2Documents(env, `${base}/issuer_dividends/`),
  ]);
  if (!profile && !current && financialDocuments.length === 0 && dividendDocuments.length === 0
    && issuerDividendDocuments.length === 0) return null;
  const documentPeriodKey = document => financialPeriodKey(
    document.annual?.period_end ? document.annual
      : document.valuation?.period_end ? document.valuation : document,
  );
  financialDocuments.sort((a, b) => documentPeriodKey(a).localeCompare(documentPeriodKey(b)));
  const sourceRows = (section) => financialDocuments.map(document => {
    const row = document[section];
    if (!row || typeof row !== 'object') return null;
    const explicit = row.source_values && typeof row.source_values === 'object' ? row.source_values : null;
    const seedValues = { ...(explicit?.seed?.values && typeof explicit.seed.values === 'object' ? explicit.seed.values : {}) };
    const yfinanceValues = { ...(explicit?.yfinance?.values && typeof explicit.yfinance.values === 'object' ? explicit.yfinance.values : {}) };
    const calculatedValues = explicit?.calculated?.values && typeof explicit.calculated.values === 'object' ? explicit.calculated.values : {};
    const compatibleCalculatedValues = Object.fromEntries(Object.entries(calculatedValues).filter(([field, calculation]) => {
      if (field !== 'equity_ratio_computed') return true;
      const sources = calculation?.input_sources;
      return Boolean(sources?.equity && sources?.total_assets && sources.equity === sources.total_assets);
    }));
    const sourceValues = explicit ? {
      ...explicit,
      ...(explicit.calculated && typeof explicit.calculated === 'object' ? {
        calculated: { ...explicit.calculated, values: compatibleCalculatedValues },
      } : {}),
    } : null;
    for (const [key, value] of Object.entries(row)) {
      if (['fiscal_year', 'period_end', 'field_status', 'migration_source', 'source_values'].includes(key)
        || Object.hasOwn(seedValues, key) || Object.hasOwn(yfinanceValues, key)
        || Object.hasOwn(calculatedValues, key)) continue;
      if (row.field_status?.[key] === 'available' && !row.migration_source) yfinanceValues[key] = value;
      else seedValues[key] = value;
    }
    const mergedRow = { ...row, ...seedValues, ...yfinanceValues };
    for (const field of ['sales', 'operating_profit', 'operating_cf', 'investing_cf']) {
      if (Array.isArray(row.field_status_reason?.[field])
        && row.field_status_reason[field].includes('mixed_source_units_not_comparable')) {
        mergedRow[field] = null;
      }
    }
    return {
      row: { ...mergedRow, ...(sourceValues ? { source_values: sourceValues } : {}) },
      seed: Object.keys(seedValues).length ? {
        fiscal_year: row.fiscal_year,
        period_end: row.period_end,
        source: explicit?.seed?.source || (row.migration_source ? 'IRBANK' : 'existing v2 snapshot'),
        source_key: explicit?.seed?.source_key || row.migration_source || null,
        values: seedValues,
      } : null,
    };
  }).filter(Boolean);
  const annualSources = sourceRows('annual').sort((a, b) => financialPeriodKey(a.row).localeCompare(financialPeriodKey(b.row)));
  const valuationSources = sourceRows('valuation').sort((a, b) => financialPeriodKey(a.row).localeCompare(financialPeriodKey(b.row)));
  const annual = annualSources.map(item => item.row);
  const seedAnnual = annualSources.map(item => item.seed).filter(Boolean);
  const valuation = valuationSources.map(item => item.row);
  const seedValuation = valuationSources.map(item => item.seed).filter(Boolean);
  const retrievalStatus = financialDocuments.at(-1)?.retrieval_status || {};
  const financialFieldStatus = { ...(retrievalStatus.fields || {}) };
  for (const row of annual) {
    for (const [field, state] of Object.entries(row.field_status || {})) {
      if (!financialFieldStatus[field] || ['empty', 'not_scheduled'].includes(financialFieldStatus[field])) {
        financialFieldStatus[field] = state;
      }
    }
  }
  const financialRetrievalStatus = { ...retrievalStatus, fields: financialFieldStatus };
  const hasMigratedValuation = valuation.some(row => Object.values(row.field_status || {}).includes('legacy_migrated'));
  const storedValuationStatus = retrievalStatus.valuation || current?.field_status?.financial_valuation;
  const valuationStatus = storedValuationStatus === 'empty' && hasMigratedValuation
    ? 'legacy_migrated'
    : storedValuationStatus || (hasMigratedValuation ? 'legacy_migrated' : valuation.length ? 'available' : 'empty');
  const dividendHistory = yfinanceCalendarYearDividends(
    dividendDocuments.flatMap(document => Array.isArray(document.records) ? document.records : []),
  );
  const calendarYearMetrics = current?.dividend?.calendar_year_metrics || null;
  const dividends = Array.isArray(calendarYearMetrics?.annual_records)
    ? calendarYearMetrics.annual_records
    : dividendHistory.records;
  const issuerDividendRecordsByYear = new Map();
  for (const record of issuerDividendDocuments.flatMap(document => Array.isArray(document.records) ? document.records : [])) {
    const fiscalYearMatch = String(record?.fiscal_year || '').match(/^(\d{4})(?:\/\d{2})?$/);
    if (!fiscalYearMatch) continue;
    issuerDividendRecordsByYear.set(Number(fiscalYearMatch[1]), record);
  }
  const issuerDividendRecords = [...issuerDividendRecordsByYear.entries()]
    .sort(([a], [b]) => a - b)
    .map(([, record]) => record);
  const shareBasisStatus = dividendShareBasisStatus(code);
  const dividendHistoryStatus = current?.field_status?.dividend_history;
  const payoutRatio = finiteNumber(current?.statistics?.payoutRatio);
  return {
    security: {
      code: yfinanceV2Code(code),
      canonical_code: String(code || '').length === 4 ? `${code}0` : String(code || ''),
      name: profile?.display_name || profile?.short_name || profile?.long_name || profile?.english_name || String(code || ''),
      metrics: [],
    },
    financials: { annual, seed_annual: seedAnnual, field_status: financialRetrievalStatus },
    dividends: {
      records: dividends,
      history_basis: 'calendar_year_observed_distributions',
      amount_basis: dividendHistory.amount_basis,
      consecutive_dividend_increase_years: finiteNumber(calendarYearMetrics?.consecutive_increase_years),
      no_cut_years: finiteNumber(calendarYearMetrics?.no_cut_years),
      metrics_status: calendarYearMetrics?.status || 'not_fetched',
      metrics_as_of_year: finiteNumber(calendarYearMetrics?.latest_completed_year),
      source: 'Yahoo Finance via yfinance',
      current: current?.dividend || null,
      ...(shareBasisStatus ? { share_basis_status: shareBasisStatus } : {}),
      payout_ratio: payoutRatio,
      field_status: {
        ...(current?.field_status || {}),
        dividend_history: dividendHistoryStatus || dividendHistory.status,
        consecutive_dividend_increase_years: calendarYearMetrics?.status || 'not_fetched',
        no_cut_years: calendarYearMetrics?.status || 'not_fetched',
        payout_ratio: payoutRatio != null ? 'available' : 'empty',
      },
    },
    issuer_dividends: {
      records: issuerDividendRecords,
      history_basis: 'issuer_reported_fiscal_year',
      amount_basis: 'irbank_adjusted_fiscal_dps',
      source: issuerDividendDocuments[0]?.source || 'IRBANK Connect',
      attribution: issuerDividendDocuments.find(document => document.attribution)?.attribution || null,
      status: issuerDividendRecords.length ? 'available' : 'empty',
      ...(shareBasisStatus ? { share_basis_status: shareBasisStatus } : {}),
    },
    valuation: { history: valuation, seed_history: seedValuation, field_status: valuationStatus },
    market: current ? { price: current.price || null, summary: current.statistics || {} } : null,
    sources: { market: 'Yahoo Finance via yfinance' },
  };
}

async function readV2DividendEvents(env, code, years) {
  const symbol = yfinanceV2Symbol(code);
  if (!symbol || !env.MARKET_DATA) return null;
  const documents = await Promise.all(years.map(async year => {
    const key = `${YFINANCE_V2_ROOT}/securities/${symbol}/dividends/${year}.json`;
    return readV2Json(env, key);
  }));
  if (documents.every(document => !document)) return null;
  return documents.flatMap(document => Array.isArray(document?.records) ? document.records : []);
}

async function readV2SplitEvents(env, code, years) {
  const symbol = yfinanceV2Symbol(code);
  if (!symbol || !env.MARKET_DATA) return null;
  const documents = await Promise.all(years.map(async year => {
    const key = `${YFINANCE_V2_ROOT}/securities/${symbol}/splits/${year}.json`;
    return readV2Json(env, key);
  }));
  if (documents.every(document => !document)) return null;
  return documents.flatMap(document => Array.isArray(document?.records) ? document.records : []);
}

async function readV2MonthlyPrices(env, code, months) {
  const symbol = yfinanceV2Symbol(code);
  if (!symbol || !env.MARKET_DATA) return null;
  const years = [...new Set(months.map(month => month.slice(0, 4)))];
  const documents = await Promise.all(years.flatMap(year => [
    readV2Json(env, `${YFINANCE_V2_ROOT}/archive/prices/monthly/${symbol}/${year}.json`),
    readV2Json(env, `${YFINANCE_V2_ROOT}/securities/${symbol}/prices/monthly/${year}.json`),
  ]));
  if (documents.every(document => !document)) return null;
  const points = new Map(documents.flatMap(document => Array.isArray(document?.points) ? document.points : [])
    .map(point => [String(point.month || ''), point]));
  return months.map(month => points.get(month)).filter(point => point && finiteNumber(point.close) > 0)
    .map(point => ({ month: point.month, date: point.date, close: point.close }));
}

const LIVE_MARKET_BATCH_SIZE = 2;
const LIVE_MARKET_CONCURRENCY = 2;
const LIVE_MARKET_ENQUEUE_LIMIT = 100;
const LIVE_MARKET_LEGACY_BATCH_SIZE = 12;
const LIVE_MARKET_LEGACY_GROUPS_PER_CRON = 100;
const MARKET_PRICE_QUEUE = 'diview-market-prices';
const MARKET_PRICE_DLQ = 'diview-market-prices-dlq';
const LIVE_QUOTE_RUN_PREFIX = 'market/yfinance/live_quote_runs/';

function tokyoDate(timestamp) {
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Tokyo', year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date(timestamp * 1000));
}

async function saveDailyPricePoint(env, code, quote) {
  const month = quote.date.slice(0, 7);
  const key = `market/yfinance/daily_prices/${code}/${month}.json`;
  const existing = await env.MARKET_DATA.get(key);
  const document = existing ? await existing.json() : { code, month, prices: [] };
  const points = new Map((document.prices || []).map(point => [String(point.date), point]));
  points.set(quote.date, { date: quote.date, close: quote.close, volume: quote.volume });
  await env.MARKET_DATA.put(key, JSON.stringify({
    code,
    month,
    updated_at: quote.updated_at,
    prices: [...points.values()].sort((a, b) => String(a.date).localeCompare(String(b.date))),
  }), { httpMetadata: { contentType: 'application/json; charset=utf-8' } });
}

function liveQuoteRunObjectKey(runId, code) {
  return `${LIVE_QUOTE_RUN_PREFIX}${runId}/${code}.json`;
}

async function ensureLiveQuoteRun(env, runId, runDate, totalBatches, universeSize, enqueuedBatches = 0) {
  const now = isoNow();
  await env.DB.prepare(`INSERT OR IGNORE INTO market_price_refresh_runs
    (run_id, run_date, total_batches, universe_size, enqueued_batches, status, started_at, updated_at)
    VALUES (?, ?, ?, ?, ?, 'queued', ?, ?)`)
    .bind(runId, runDate, totalBatches, universeSize, enqueuedBatches, now, now).run();
}

async function publishCompletedQuoteRun(env, run) {
  if (!run || run.status !== 'complete' || !run.market_data_date) return;
  const key = 'market/yfinance/live_quotes_completion.json';
  const existingObject = await env.MARKET_DATA.get(key);
  const existing = existingObject ? await existingObject.json() : {};
  if (existing.last_completed_run_id === run.run_id && existing.last_completed_date === run.market_data_date) return;
  if (String(existing.last_completed_run_id || '') > String(run.run_id || '')) return;
  const completedAt = run.completed_at || isoNow();
  await env.MARKET_DATA.put(key, JSON.stringify({
    ...existing,
    last_completed_date: run.market_data_date,
    last_completed_run_id: run.run_id,
    last_completed_at: completedAt,
    last_failed_batches: 0,
    live_quote_count: Number(run.processed_symbols || 0),
    last_run_status: 'complete',
    last_run_date: run.run_date,
    last_run_batches_total: Number(run.total_batches || 0),
    last_run_batches_completed: Number(run.completed_batches || 0),
  }), { httpMetadata: { contentType: 'application/json; charset=utf-8' } });
}

async function refreshLiveMarketQuotes(env, requestedCodes, runId) {
  const codes = [...new Set((requestedCodes || []).map(value => String(value || '').toUpperCase()).filter(code => /^[A-Z0-9]{4,5}$/.test(code)))];
  if (!codes.length) return { processed: 0, failed: 0, marketDataDate: null, skipped: 'empty_batch' };
  const now = isoNow();
  let processed = 0;
  let failed = 0;
  let marketDataDate = null;
  for (let start = 0; start < codes.length; start += LIVE_MARKET_CONCURRENCY) {
    const group = codes.slice(start, start + LIVE_MARKET_CONCURRENCY);
    const results = await Promise.all(group.map(async code => {
      try {
        const response = await fetch(`https://query1.finance.yahoo.com/v8/finance/chart/${encodeURIComponent(code)}.T?range=5d&interval=1d`, {
          headers: { 'user-agent': 'Mozilla/5.0 Diview/1.0' },
          signal: AbortSignal.timeout(8000),
        });
        if (!response.ok) return null;
        const payload = await response.json();
        const result = payload?.chart?.result?.[0];
        const timestamps = result?.timestamp || [];
        const quote = result?.indicators?.quote?.[0] || {};
        let closeIndex = timestamps.length - 1;
        while (closeIndex >= 0 && !(finiteNumber(quote.close?.[closeIndex]) > 0)) closeIndex -= 1;
        if (closeIndex < 0) return null;
        const price = finiteNumber(quote.close[closeIndex]);
        const previousIndex = closeIndex - 1;
        const previousClose = previousIndex >= 0 ? finiteNumber(quote.close?.[previousIndex]) : finiteNumber(result?.meta?.chartPreviousClose);
        return {
          code,
          quote: {
            symbol: result.meta?.symbol || `${code}.T`,
            date: tokyoDate(timestamps[closeIndex]),
            open: finiteNumber(quote.open?.[closeIndex]),
            high: finiteNumber(quote.high?.[closeIndex]),
            low: finiteNumber(quote.low?.[closeIndex]),
            close: price,
            adjusted_close: finiteNumber(result?.indicators?.adjclose?.[0]?.adjclose?.[closeIndex]) || price,
            volume: finiteNumber(quote.volume?.[closeIndex]),
            change: previousClose === null ? null : price - previousClose,
            change_percent: previousClose === null || previousClose === 0 ? null : (price - previousClose) / previousClose * 100,
            source: 'Yahoo Finance chart API',
            generated_at: now,
            updated_at: now,
          }
        };
      } catch {
        return null;
      }
    }));

    await Promise.all(results.filter(Boolean).map(async ({ code, quote }) => {
      const key = liveQuoteRunObjectKey(runId, code);
      const existing = await env.MARKET_DATA.get(key);
      const previous = existing ? await existing.json() : null;
      if (!previous?.date || previous.date <= quote.date) {
        await env.MARKET_DATA.put(key, JSON.stringify(quote), {
          httpMetadata: { contentType: 'application/json; charset=utf-8' },
          customMetadata: {
            source: 'Yahoo Finance chart API',
            quoteDate: quote.date,
            close: String(quote.close),
            adjustedClose: String(quote.adjusted_close),
            volume: String(quote.volume ?? ''),
          },
        });
        await saveDailyPricePoint(env, code, quote);
      }
      processed += 1;
      if (!marketDataDate || quote.date > marketDataDate) marketDataDate = quote.date;
    }));
    failed += group.length - results.filter(Boolean).length;
  }
  return { processed, failed, marketDataDate };
}

async function enqueueDailyMarketQuotes(env, scheduledTime) {
  if (!env.MARKET_PRICE_QUEUE) throw new Error('MARKET_PRICE_QUEUE binding is missing');
  const date = new Date(scheduledTime || Date.now());
  const parts = Object.fromEntries(new Intl.DateTimeFormat('en', {
    timeZone: 'Asia/Tokyo', weekday: 'short', hour: '2-digit', hourCycle: 'h23', year: 'numeric', month: '2-digit', day: '2-digit'
  }).formatToParts(date).map(part => [part.type, part.value]));
  const runDate = `${parts.year}-${parts.month}-${parts.day}`;
  const stateKey = 'market/yfinance/live_quotes_state.json';
  const stateObject = await env.MARKET_DATA.get(stateKey);
  let state = stateObject ? await stateObject.json() : {};
  const completionObject = await env.MARKET_DATA.get('market/yfinance/live_quotes_completion.json');
  const completion = completionObject ? await completionObject.json() : {};
  const firstRunPending = !completion.last_completed_run_id && !completion.last_completed_date;
  const legacyCurrentRunPending = !completion.last_completed_run_id && state.queue_run_date === runDate;
  if (!firstRunPending && !legacyCurrentRunPending && (['Sat', 'Sun'].includes(parts.weekday) || Number(parts.hour) < 17)) return;
  const latestActiveRun = await env.DB.prepare(`SELECT run_id FROM market_price_refresh_runs
    WHERE status IN ('queued', 'running') ORDER BY run_date DESC LIMIT 1`).first();
  if (latestActiveRun && latestActiveRun.run_id !== runDate) return;

  const stateRunDate = String(state.queue_run_date || '');
  const oldBatchSize = Number(state.queue_batch_size || LIVE_MARKET_LEGACY_BATCH_SIZE);
  const oldTotalBatches = Number(state.queue_batches_total || 0);
  const oldEnqueuedBatches = Number(state.queue_batches_enqueued || 0);
  const universeRow = await env.DB.prepare(`SELECT COUNT(*) AS count FROM securities
    WHERE length(security_code_display) BETWEEN 4 AND 5
      AND security_code_display NOT GLOB '*[^A-Za-z0-9]*'`).first();
  const universeSize = Number(universeRow?.count || 0);
  if (universeSize <= 0) return;
  const totalBatches = Math.ceil(universeSize / LIVE_MARKET_BATCH_SIZE);

  if (stateRunDate === runDate && oldBatchSize !== LIVE_MARKET_BATCH_SIZE) {
    const legacyPrefix = `market/yfinance/live_quote_batches/${runDate}/`;
    const legacyResults = await env.MARKET_DATA.list({ prefix: legacyPrefix, limit: 1000, include: ['customMetadata'] });
    const completedParents = (legacyResults.objects || []).map(object => {
      const index = Number(object.key.slice(legacyPrefix.length).replace(/\.json$/, ''));
      return Number.isInteger(index) && index >= 0 && ['complete', 'failed'].includes(object.customMetadata?.status) ? index : null;
    }).filter(index => index !== null);
    const neverEnqueuedParents = Array.from({ length: Math.max(0, oldTotalBatches - oldEnqueuedBatches) }, (_, index) => oldEnqueuedBatches + index);
    const legacyBackfillIndexes = [...new Set([...completedParents, ...neverEnqueuedParents])].sort((a, b) => a - b);
    state = {
      ...state,
      queue_run_date: runDate,
      queue_batch_size: LIVE_MARKET_BATCH_SIZE,
      queue_batches_total: totalBatches,
      queue_batches_enqueued: totalBatches,
      universe_size: universeSize,
      legacy_batch_size: oldBatchSize,
      legacy_total_batches: oldTotalBatches,
      legacy_backfill_indexes: legacyBackfillIndexes,
      legacy_backfill_cursor: 0,
      legacy_backfilled_indexes: [],
    };
    await ensureLiveQuoteRun(env, runDate, runDate, totalBatches, universeSize, totalBatches);
    await env.MARKET_DATA.put(stateKey, JSON.stringify(state), { httpMetadata: { contentType: 'application/json; charset=utf-8' } });
  }

  if (state.queue_run_date !== runDate) {
    state = {
      ...state,
      queue_run_date: runDate,
      queue_batch_size: LIVE_MARKET_BATCH_SIZE,
      queue_batches_total: totalBatches,
      queue_batches_enqueued: 0,
      universe_size: universeSize,
      last_enqueue_at: isoNow(),
    };
    await ensureLiveQuoteRun(env, runDate, runDate, totalBatches, universeSize, 0);
    await env.MARKET_DATA.put(stateKey, JSON.stringify(state), { httpMetadata: { contentType: 'application/json; charset=utf-8' } });
  }

  if (Number(state.legacy_total_batches || 0) > 0 && !(stateRunDate === runDate && oldBatchSize !== LIVE_MARKET_BATCH_SIZE)) {
    const legacyPrefix = `market/yfinance/live_quote_batches/${runDate}/`;
    const legacyResults = await env.MARKET_DATA.list({ prefix: legacyPrefix, limit: 1000, include: ['customMetadata'] });
    const newlyCompleted = (legacyResults.objects || []).map(object => {
      const index = Number(object.key.slice(legacyPrefix.length).replace(/\.json$/, ''));
      return Number.isInteger(index) && index >= 0 && ['complete', 'failed'].includes(object.customMetadata?.status) ? index : null;
    }).filter(index => index !== null);
    const known = new Set(state.legacy_backfill_indexes || []);
    const additions = newlyCompleted.filter(index => !known.has(index));
    if (additions.length) {
      state = { ...state, legacy_backfill_indexes: [...known, ...additions].sort((a, b) => a - b) };
      await env.MARKET_DATA.put(stateKey, JSON.stringify(state), { httpMetadata: { contentType: 'application/json; charset=utf-8' } });
    }
  }

  if (state.legacy_backfill_indexes?.length) {
    const backfilled = new Set(state.legacy_backfilled_indexes || []);
    const remaining = state.legacy_backfill_indexes.filter(index => !backfilled.has(index)).slice(0, LIVE_MARKET_LEGACY_GROUPS_PER_CRON);
    if (remaining.length) {
      const statements = remaining.map(index => env.DB.prepare(`SELECT security_code_display AS code
        FROM securities ORDER BY security_code LIMIT ? OFFSET ?`).bind(
        Number(state.legacy_batch_size || LIVE_MARKET_LEGACY_BATCH_SIZE),
        index * Number(state.legacy_batch_size || LIVE_MARKET_LEGACY_BATCH_SIZE),
      ));
      const legacyRows = await env.DB.batch(statements);
      const messages = [];
      const childrenPerParent = Math.ceil(Number(state.legacy_batch_size || LIVE_MARKET_LEGACY_BATCH_SIZE) / LIVE_MARKET_BATCH_SIZE);
      legacyRows.forEach((result, parentOffset) => {
        const symbols = (result.results || []).map(row => String(row.code || '').toUpperCase()).filter(code => /^[A-Z0-9]{4,5}$/.test(code));
        for (let offset = 0; offset < symbols.length; offset += LIVE_MARKET_BATCH_SIZE) {
          messages.push({ body: {
            run_id: runDate,
            run_date: runDate,
            batch_index: remaining[parentOffset] * childrenPerParent + Math.floor(offset / LIVE_MARKET_BATCH_SIZE),
            total_batches: totalBatches,
            symbols: symbols.slice(offset, offset + LIVE_MARKET_BATCH_SIZE),
          } });
        }
      });
      for (let offset = 0; offset < messages.length; offset += LIVE_MARKET_ENQUEUE_LIMIT) {
        await env.MARKET_PRICE_QUEUE.sendBatch(messages.slice(offset, offset + LIVE_MARKET_ENQUEUE_LIMIT));
      }
      remaining.forEach(index => backfilled.add(index));
      state = {
        ...state,
        legacy_backfilled_indexes: [...backfilled].sort((a, b) => a - b),
        legacy_backfill_cursor: Number(state.legacy_backfill_cursor || 0) + remaining.length,
      };
      await env.MARKET_DATA.put(stateKey, JSON.stringify(state), { httpMetadata: { contentType: 'application/json; charset=utf-8' } });
    }
  }

  if (state.queue_batches_enqueued >= state.queue_batches_total) return;
  const nextBatch = Number(state.queue_batches_enqueued || 0);
  const batchCount = Math.min(LIVE_MARKET_ENQUEUE_LIMIT, state.queue_batches_total - nextBatch);
  const result = await env.DB.prepare(`SELECT security_code_display AS code FROM securities
    WHERE length(security_code_display) BETWEEN 4 AND 5
      AND security_code_display NOT GLOB '*[^A-Za-z0-9]*'
    ORDER BY security_code LIMIT ? OFFSET ?`).bind(batchCount * LIVE_MARKET_BATCH_SIZE, nextBatch * LIVE_MARKET_BATCH_SIZE).all();
  const codes = (result.results || []).map(row => String(row.code || '').toUpperCase());
  const messages = [];
  for (let offset = 0; offset < codes.length; offset += LIVE_MARKET_BATCH_SIZE) {
    messages.push({ body: {
      run_id: runDate,
      run_date: runDate,
      batch_index: nextBatch + messages.length,
      total_batches: state.queue_batches_total,
      symbols: codes.slice(offset, offset + LIVE_MARKET_BATCH_SIZE),
    } });
  }
  if (!messages.length) return;
  await env.MARKET_PRICE_QUEUE.sendBatch(messages);
  state = { ...state, queue_batches_enqueued: nextBatch + messages.length, last_enqueue_at: isoNow() };
  await env.DB.prepare('UPDATE market_price_refresh_runs SET enqueued_batches = ?, updated_at = ? WHERE run_id = ?')
    .bind(state.queue_batches_enqueued, isoNow(), runDate).run();
  await env.MARKET_DATA.put(stateKey, JSON.stringify(state), { httpMetadata: { contentType: 'application/json; charset=utf-8' } });
}

async function recordMarketBatchResult(env, message, status, processedSymbols = 0, marketDataDate = null) {
  const runId = String(message?.run_id || message?.run_date || '');
  const runDate = String(message?.run_date || runId);
  const batchIndex = Number(message?.batch_index);
  const totalBatches = Number(message?.total_batches);
  if (!/^\d{4}-\d{2}-\d{2}$/.test(runId) || !/^\d{4}-\d{2}-\d{2}$/.test(runDate) || !Number.isInteger(batchIndex) || batchIndex < 0 || !Number.isInteger(totalBatches) || totalBatches < 1) {
    throw new Error('Invalid market-price queue message');
  }
  await ensureLiveQuoteRun(env, runId, runDate, totalBatches, totalBatches * LIVE_MARKET_BATCH_SIZE, totalBatches);
  const completedAt = isoNow();
  const results = await env.DB.batch([
    env.DB.prepare(`INSERT OR IGNORE INTO market_price_refresh_batches
      (run_id, batch_index, status, processed_symbols, market_data_date, completed_at)
      VALUES (?, ?, ?, ?, ?, ?)`)
      .bind(runId, batchIndex, status, processedSymbols, marketDataDate, completedAt),
    env.DB.prepare(`UPDATE market_price_refresh_runs SET
        completed_batches = completed_batches + 1,
        failed_batches = failed_batches + CASE WHEN ? = 'failed' THEN 1 ELSE 0 END,
        processed_symbols = processed_symbols + ?,
        market_data_date = CASE WHEN ? IS NULL THEN market_data_date
          WHEN market_data_date IS NULL OR market_data_date < ? THEN ? ELSE market_data_date END,
        status = CASE WHEN completed_batches + 1 >= total_batches
          THEN CASE WHEN failed_batches + CASE WHEN ? = 'failed' THEN 1 ELSE 0 END > 0 THEN 'partial' ELSE 'complete' END
          ELSE 'running' END,
        last_batch_at = ?,
        completed_at = CASE WHEN completed_batches + 1 >= total_batches THEN ? ELSE NULL END,
        updated_at = ?
      WHERE run_id = ? AND changes() = 1
      RETURNING run_id, run_date, total_batches, completed_batches, failed_batches,
        processed_symbols, market_data_date, status, completed_at`)
      .bind(status, processedSymbols, marketDataDate, marketDataDate, marketDataDate, status,
        completedAt, completedAt, completedAt, runId),
  ]);
  let run = results?.[1]?.results?.[0] || null;
  if (!run) run = await env.DB.prepare(`SELECT run_id, run_date, total_batches, completed_batches,
    failed_batches, processed_symbols, market_data_date, status, completed_at
    FROM market_price_refresh_runs WHERE run_id = ?`).bind(runId).first();
  if (run?.status === 'complete') await publishCompletedQuoteRun(env, run);
}

async function splitLegacyMarketQueueMessage(env, body) {
  const runDate = String(body?.run_date || '');
  const symbols = [...new Set((Array.isArray(body?.symbols) ? body.symbols : [])
    .map(value => String(value || '').toUpperCase())
    .filter(code => /^[A-Z0-9]{4,5}$/.test(code)))];
  const parentIndex = Number(body?.batch_index);
  if (!/^\d{4}-\d{2}-\d{2}$/.test(runDate) || !Number.isInteger(parentIndex) || parentIndex < 0 || !symbols.length) {
    throw new Error('Invalid legacy market-price queue message');
  }
  const stateObject = await env.MARKET_DATA.get('market/yfinance/live_quotes_state.json');
  const state = stateObject ? await stateObject.json() : {};
  const legacyBatchSize = Number(state.legacy_batch_size || LIVE_MARKET_LEGACY_BATCH_SIZE);
  const universeSize = Number(state.universe_size || Number(body.total_batches || 0) * legacyBatchSize);
  const totalBatches = Math.ceil(universeSize / LIVE_MARKET_BATCH_SIZE);
  if (!totalBatches) throw new Error('Market-price universe is empty');
  await ensureLiveQuoteRun(env, runDate, runDate, totalBatches, universeSize, totalBatches);
  const childrenPerParent = Math.ceil(legacyBatchSize / LIVE_MARKET_BATCH_SIZE);
  const messages = [];
  for (let offset = 0; offset < symbols.length; offset += LIVE_MARKET_BATCH_SIZE) {
    messages.push({ body: {
      run_id: runDate,
      run_date: runDate,
      batch_index: parentIndex * childrenPerParent + Math.floor(offset / LIVE_MARKET_BATCH_SIZE),
      total_batches: totalBatches,
      symbols: symbols.slice(offset, offset + LIVE_MARKET_BATCH_SIZE),
    } });
  }
  for (let offset = 0; offset < messages.length; offset += LIVE_MARKET_ENQUEUE_LIMIT) {
    await env.MARKET_PRICE_QUEUE.sendBatch(messages.slice(offset, offset + LIVE_MARKET_ENQUEUE_LIMIT));
  }
}

async function loadSecurityAnalysisInput(env, security, marketIndex = null) {
  const [marketData, v2Payload] = await Promise.all([
    latestMarketForSecurity(env, security.security_code_display, marketIndex),
    readV2FinancialPayload(env, security.security_code_display),
  ]);
  const latestV2Dividend = v2Payload?.dividends?.records?.at(-1) || null;
  const dividendYear = latestV2Dividend?.calendar_year ?? null;
  const dividendData = v2Payload ? {
    payout_ratio: v2Payload.dividends.payout_ratio,
    consecutive_dividend_increase_years: v2Payload.dividends.consecutive_dividend_increase_years,
  } : null;
  const financial = v2Payload?.financials?.annual?.at(-1) || null;
  const financialData = financial
    ? (typeof financial.data_json === 'string' ? JSON.parse(financial.data_json || '{}') : financial)
    : null;
  const summary = marketData?.summary || null;
  const sales = finiteNumber(financialData?.sales);
  const operatingProfit = finiteNumber(financialData?.operating_profit);
  const operatingMargin = finiteNumber(financialData?.operating_margin)
    ?? (sales > 0 && operatingProfit != null ? operatingProfit / sales * 100 : null);
  const fcf = finiteNumber(financialData?.operating_cf) != null && finiteNumber(financialData?.investing_cf) != null
    ? finiteNumber(financialData.operating_cf) + finiteNumber(financialData.investing_cf)
    : null;
  const values = {
    per: finiteNumber(summary?.trailingPE),
    pbr: finiteNumber(summary?.priceToBook),
    payout_ratio: finiteNumber(dividendData?.payout_ratio),
    equity_ratio: finiteNumber(financialData?.equity_ratio_computed ?? financialData?.equity_ratio),
    operating_margin: operatingMargin,
    fcf_margin: finiteNumber(financialData?.fcf_margin) ?? (fcf != null && sales > 0 ? fcf / sales * 100 : null),
    dividend_growth_years: finiteNumber(dividendData?.consecutive_dividend_increase_years),
  };
  const currentYield = finiteNumber(marketData?.dividend?.yield_rate);
  return {
    dividendYear,
    dividendHistoryBasis: v2Payload?.dividends?.history_basis || null,
    dividendData,
    financial,
    financialData,
    marketData,
    values,
    currentYield,
  };
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
      dividend_history_year: input.dividendYear,
      dividend_history_basis: input.dividendHistoryBasis,
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
    const [catalogObject, financialRevisionObject] = await Promise.all([
      env.MARKET_DATA?.head(`${YFINANCE_V2_ROOT}/catalog/current.json`),
      env.MARKET_DATA?.head(`${YFINANCE_V2_ROOT}/financial_revision.json`),
    ]);
    const sourceTag = JSON.stringify({
      catalog_etag: catalogObject?.httpEtag || catalogObject?.etag || null,
      financial_revision_etag: financialRevisionObject?.httpEtag || financialRevisionObject?.etag || null,
      stock_version: stockDefs.version || 1,
      watch_version: watchDefs.version || 1,
    });
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

function parseJQuantsByteRange(header, size) {
  const match = /^bytes=(\d*)-(\d*)$/i.exec(String(header || '').trim());
  if (!match || (!match[1] && !match[2]) || size <= 0) return null;

  let start;
  let end;
  if (!match[1]) {
    const suffixLength = Number(match[2]);
    if (!Number.isSafeInteger(suffixLength) || suffixLength <= 0) return null;
    start = Math.max(0, size - suffixLength);
    end = size - 1;
  } else {
    start = Number(match[1]);
    end = match[2] ? Number(match[2]) : size - 1;
    if (!Number.isSafeInteger(start) || !Number.isSafeInteger(end) || start >= size || end < start) return null;
    end = Math.min(end, size - 1);
  }

  return { start, end, length: end - start + 1 };
}

async function jquantsDailyQuotesResponse(request, env, config) {
  if (!['GET', 'HEAD'].includes(request.method)) {
    const response = json({ error: 'method_not_allowed' }, 405);
    response.headers.set('allow', 'GET, HEAD, OPTIONS');
    return response;
  }
  if (!await enforceRateLimit(request, env, 'public:jquants-daily-quotes', 5, '', config)) {
    return rateLimitedResponse(config);
  }
  if (!env.MARKET_DATA) return json({ error: 'r2_unavailable' }, 503);

  const key = 'debug/jquants/v1/adjusted_daily_quotes.csv';
  const rangeHeader = request.headers.get('range');
  let object;
  let byteRange = null;
  if (rangeHeader) {
    const metadata = await env.MARKET_DATA.head(key);
    if (!metadata) return json({ error: 'not_found' }, 404);
    byteRange = parseJQuantsByteRange(rangeHeader, metadata.size);
    if (!byteRange) {
      return new Response(null, {
        status: 416,
        headers: {
          'content-range': `bytes */${metadata.size}`,
          'accept-ranges': 'bytes',
          'access-control-allow-origin': '*',
          'access-control-allow-methods': 'GET, HEAD, OPTIONS',
          'access-control-allow-headers': 'Range',
          'access-control-expose-headers': 'Accept-Ranges, Content-Range, ETag, Last-Modified',
          'cache-control': 'private, no-store',
        },
      });
    }
    object = request.method === 'HEAD'
      ? metadata
      : await env.MARKET_DATA.get(key, { range: { offset: byteRange.start, length: byteRange.length } });
  } else {
    object = request.method === 'HEAD'
      ? await env.MARKET_DATA.head(key)
      : await env.MARKET_DATA.get(key);
  }
  if (!object) return json({ error: 'not_found' }, 404);

  const headers = new Headers({
    'content-type': 'text/csv; charset=utf-8',
    'content-disposition': 'attachment; filename="adjusted_daily_quotes.csv"',
    'cache-control': 'public, max-age=300, s-maxage=300',
    'access-control-allow-origin': '*',
    'access-control-allow-methods': 'GET, HEAD, OPTIONS',
    'access-control-allow-headers': 'Range',
    'access-control-expose-headers': 'Accept-Ranges, Content-Length, Content-Range, ETag, Last-Modified',
    'access-control-max-age': '86400',
    'accept-ranges': 'bytes',
    'content-length': String(byteRange ? byteRange.length : object.size),
    'etag': object.httpEtag,
    'x-content-type-options': 'nosniff',
  });
  if (object.uploaded) headers.set('last-modified', object.uploaded.toUTCString());
  if (byteRange) headers.set('content-range', `bytes ${byteRange.start}-${byteRange.end}/${object.size}`);

  return new Response(request.method === 'HEAD' ? null : object.body, {
    status: byteRange ? 206 : 200,
    headers,
  });
}

function irbankR2Code(value) {
  const code = value.toUpperCase();
  if (/^[A-Z0-9]{4}$/.test(code)) return code;
  if (/^[A-Z0-9]{4}0$/.test(code)) return code.slice(0, 4);
  return null;
}

function yfinanceV2Code(value) {
  const code = String(value || '').trim().toUpperCase().replace(/\.T$/, '');
  if (/^[A-Z0-9]{4}$/.test(code)) return code;
  // J-Quants pads ordinary four-character TSE codes with a trailing zero;
  // listed class shares use their distinct five-character code as the Yahoo symbol.
  if (/^[A-Z0-9]{5}$/.test(code)) return code.endsWith('0') ? code.slice(0, 4) : code;
  return null;
}

export default {
  async fetch(request, env) {
    const url = new URL(request.url);

    const config = await backendConfig(env);

    if (request.method === 'OPTIONS') return new Response(null, { status: 204, headers: json(null).headers });

    if (url.pathname === '/api/v1/feedback' && request.method === 'POST') {
      if (!await enforceRateLimit(request, env, 'support-feedback', 5, '', config)) return rateLimitedResponse(config);
      const input = await readFeedback(request);
      if (!input) return json({ error: 'invalid_feedback' }, 400);
      try {
        await env.DB.prepare("DELETE FROM support_feedback WHERE created_at < strftime('%Y-%m-%dT%H:%M:%fZ', 'now', '-90 days')").run();
        if (!await saveFeedback(env.DB, input)) return json({ error: 'request_id_conflict' }, 409);
        return json({ accepted: true, receiptId: input.requestId }, 201);
      } catch {
        return json({ error: 'feedback_storage_unavailable' }, 503);
      }
    }

    if (url.pathname === '/api/v1/jquants/daily-quotes') {
      return jquantsDailyQuotesResponse(request, env, config);
    }

    if (url.pathname === '/api/v1/admin/feedback') {
      const provided = String(request.headers.get('X-Admin-Secret') || '');
      if (!env.ADMIN_SECRET || provided !== env.ADMIN_SECRET) return json({ error: 'unauthorized' }, 401);
      if (request.method === 'GET') {
        await env.DB.prepare("DELETE FROM support_feedback WHERE created_at < strftime('%Y-%m-%dT%H:%M:%fZ', 'now', '-90 days')").run();
        const result = await env.DB.prepare('SELECT receipt_id, created_at, payload_json FROM support_feedback ORDER BY created_at DESC LIMIT 100').all();
        return json({ items: (result.results || []).map(row => ({ receiptId: row.receipt_id, createdAt: row.created_at, ...JSON.parse(row.payload_json) })) });
      }
      if (request.method === 'DELETE') {
        const id = url.searchParams.get('id') || '';
        if (!/^[a-f0-9-]{36}$/.test(id)) return json({ error: 'invalid_receipt_id' }, 400);
        await env.DB.prepare('DELETE FROM support_feedback WHERE receipt_id = ?').bind(id).run();
        return json({ deleted: true });
      }
      return json({ error: 'method_not_allowed' }, 405);
    }

    const isClientScopedRead = url.pathname === '/api/v1/watchlist-targets' || url.pathname === '/api/v1/notification-events';
    const d1IndependentRead = request.method === 'GET' && (
      url.pathname === '/api/v1/health' ||
      url.pathname === '/api/v1/security-search' ||
      url.pathname === '/api/v1/search-catalog' ||
      url.pathname === '/api/v1/live-quotes' ||
      url.pathname === '/api/v1/irbank/manifest' ||
      url.pathname === '/api/v1/irbank/catalog' ||
      url.pathname.startsWith('/api/v1/financial-data/') ||
      url.pathname.startsWith('/api/v1/irbank/securities/') ||
      url.pathname.startsWith('/api/v1/securities/')
    );
    const rateLimitRoute = request.method === 'GET' && !isClientScopedRead && !d1IndependentRead
      ? publicRateLimitRoute(url.pathname)
      : null;
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
      let latestRun = null;
      let quoteRun = null;
      let d1Available = true;
      try {
        latestRun = await env.DB.prepare(`SELECT started_at, finished_at, status, processed, failed FROM scheduled_run_log ORDER BY id DESC LIMIT 1`).first();
        quoteRun = await env.DB.prepare(`SELECT run_date, total_batches, enqueued_batches, completed_batches,
          processed_symbols, failed_batches, market_data_date, status, last_batch_at, completed_at
          FROM market_price_refresh_runs ORDER BY run_date DESC LIMIT 1`).first();
      } catch {
        d1Available = false;
      }
      const marketRefreshObject = await env.MARKET_DATA?.get('market/yfinance/live_quotes_state.json');
      const marketRefresh = marketRefreshObject ? await marketRefreshObject.json() : {};
      const marketCompletionObject = await env.MARKET_DATA?.get('market/yfinance/live_quotes_completion.json');
      const marketCompletion = marketCompletionObject ? await marketCompletionObject.json() : {};
      const hasPublishedRun = !!marketCompletion.last_completed_run_id;
      const liveQuoteCount = Number((hasPublishedRun ? marketCompletion.live_quote_count : null) ?? quoteRun?.processed_symbols ?? marketRefresh.live_quote_count ?? 0);
      const marketObject = await env.MARKET_DATA?.head('market/yfinance/latest_market_data.json');
      const marketUploadedAt = (hasPublishedRun ? marketCompletion.last_completed_at : null) || marketObject?.uploaded?.toISOString?.() || null;
      const stockDefs = await appDefinitionDocument(env, 'stock-analysis-definitions:v1') || {};
      const watchDefs = await appDefinitionDocument(env, 'watchlist-analysis-definitions:v1') || {};
      return json({ ok: true, checked_at: isoNow(), d1: d1Available, market: {
        available: !!marketObject,
        uploaded: marketUploadedAt,
        snapshot_uploaded: marketObject?.uploaded?.toISOString?.() || null,
        age_days: marketUploadedAt ? daysOld(marketUploadedAt) : null,
        stale_after_days: Number(config.market_stale_days || 3),
        live_quote_count: liveQuoteCount,
        live_refresh_date: quoteRun?.run_date || marketRefresh.queue_run_date || null,
        live_refresh_batches_total: Number(quoteRun?.total_batches ?? marketRefresh.queue_batches_total ?? 0),
        live_refresh_batches_enqueued: Number(quoteRun?.enqueued_batches ?? marketRefresh.queue_batches_enqueued ?? 0),
        live_refresh_batches_completed: Number(quoteRun?.completed_batches || 0),
        live_refresh_last_batch_at: quoteRun?.last_batch_at || marketRefresh.last_batch_at || null,
        live_refresh_last_completed_date: hasPublishedRun ? marketCompletion.last_completed_date || null : null,
        live_refresh_failed_batches: Number(quoteRun?.failed_batches ?? (hasPublishedRun ? marketCompletion.last_failed_batches : null) ?? 0),
        live_refresh_status: quoteRun?.status || (hasPublishedRun ? marketCompletion.last_run_status : null) || null,
        live_refresh_market_data_date: quoteRun?.market_data_date || (hasPublishedRun ? marketCompletion.last_completed_date : null) || null,
      }, definitions: { stock_version: stockDefs.version || null, watchlist_version: watchDefs.version || null }, latest_scheduled_run: latestRun || null });
    }

    if (request.method !== 'GET') return json({ error: 'method_not_allowed' }, 405);

    if (url.pathname === '/api/v1/dividend-months') {
      if (!env.MARKET_DATA) return json({ error: 'market_data_unavailable' }, 503);
      const rawSymbols = String(url.searchParams.get('symbols') || '').split(',').map(value => value.trim());
      const symbols = [...new Set(rawSymbols.map(value => value.toUpperCase()))];
      const rawEventYear = url.searchParams.get('event_year');
      const requestedEventYear = rawEventYear == null ? null : Number(rawEventYear);
      if (symbols.length === 0 || symbols.length > 50 || symbols.some(symbol => !/^[A-Z0-9.^_=-]{1,32}$/.test(symbol))) {
        return json({ error: 'invalid_request' }, 400);
      }

      const todayParts = Object.fromEntries(new Intl.DateTimeFormat('en', {
        timeZone: 'Asia/Tokyo', year: 'numeric', month: '2-digit', day: '2-digit'
      }).formatToParts(new Date()).map(part => [part.type, part.value]));
      const asOf = `${todayParts.year}-${todayParts.month}-${todayParts.day}`;
      if (rawEventYear != null && (!Number.isInteger(requestedEventYear) || requestedEventYear < 1900 || requestedEventYear > Number(asOf.slice(0, 4)))) {
        return json({ error: 'invalid_request' }, 400);
      }
      const cutoff = new Date(Date.parse(`${asOf}T00:00:00.000Z`) - 365 * 24 * 60 * 60 * 1000)
        .toISOString().slice(0, 10);
      try {
        const rightsDatesObject = await env.MARKET_DATA.get(`${YFINANCE_V2_ROOT}/derived/rights_dates/current.json`);
        const rightsDatesDocument = rightsDatesObject ? await rightsDatesObject.json() : null;
        const rightsRecords = Array.isArray(rightsDatesDocument?.records) ? rightsDatesDocument.records : [];
        const rightsDatesByEvent = new Map(rightsRecords
          .map(event => [`${String(event.symbol || '').toUpperCase()}|${String(event.ex_dividend_date || '')}`, {
            finalRightsDate: event.final_rights_date || null,
            recordDate: event.record_date || null,
          }]));
        const estimatedEventsBySymbol = new Map();
        for (const event of rightsRecords) {
          if (event.event_type !== 'history_estimated') continue;
          const symbol = String(event.symbol || '').toUpperCase();
          const eventDate = String(event.ex_dividend_date || '');
          if (!symbol || !/^\d{4}-\d{2}-\d{2}$/.test(eventDate) || eventDate <= asOf) continue;
          const current = estimatedEventsBySymbol.get(symbol);
          if (!current || eventDate < String(current.ex_dividend_date || '')) {
            estimatedEventsBySymbol.set(symbol, event);
          }
        }
        const items = await Promise.all(symbols.map(async symbol => {
          const years = requestedEventYear != null
            ? [requestedEventYear]
            : [Number(asOf.slice(0, 4)), Number(asOf.slice(0, 4)) - 1];
          const v2Symbol = yfinanceV2Symbol(symbol);
          const [v2Events, current] = await Promise.all([
            readV2DividendEvents(env, symbol, years),
            v2Symbol ? readV2Json(env, `${YFINANCE_V2_ROOT}/securities/${v2Symbol}/current.json`) : Promise.resolve(null),
          ]);
          const storedHistoryStatus = current?.field_status?.dividend_history
            || (v2Events ? (v2Events.length > 0 ? 'available' : 'empty') : 'not_fetched');
          const estimatedHistoryEvent = estimatedEventsBySymbol.get(symbol);
          const historyStatus = estimatedHistoryEvent && ['empty', 'not_fetched'].includes(storedHistoryStatus)
            ? 'available' : storedHistoryStatus;
          const calendarExDate = String(current?.calendar?.ex_dividend_date || '');
          const rawUpcoming = finiteNumber(current?.statistics?.exDividendDate);
          const statisticsExDate = rawUpcoming ? new Date(rawUpcoming * 1000).toISOString().slice(0, 10) : null;
          const calendarUpcoming = /^\d{4}-\d{2}-\d{2}$/.test(calendarExDate) && calendarExDate > asOf
            ? calendarExDate : null;
          const estimatedHistoryDate = estimatedHistoryEvent?.ex_dividend_date > asOf
            ? estimatedHistoryEvent.ex_dividend_date : null;
          const statisticsUpcoming = /^\d{4}-\d{2}-\d{2}$/.test(String(statisticsExDate || '')) && statisticsExDate > asOf
            ? statisticsExDate : null;
          const upcomingExDate = estimatedHistoryDate || calendarUpcoming || statisticsUpcoming;
          const upcomingRightsDates = upcomingExDate && upcomingExDate > asOf
            ? rightsDatesByEvent.get(`${symbol}|${upcomingExDate}`) || null
            : null;
          const usesHistoryEstimate = Boolean(estimatedHistoryDate && upcomingExDate === estimatedHistoryDate);
          const usesCalendarDate = Boolean(!usesHistoryEstimate && calendarUpcoming && upcomingExDate === calendarUpcoming);
          const upcomingEvent = upcomingExDate && upcomingExDate > asOf ? {
            ex_dividend_date: upcomingExDate,
            final_rights_date: upcomingRightsDates?.finalRightsDate || null,
            record_date: upcomingRightsDates?.recordDate || null,
            estimated_from_history: usesHistoryEstimate,
            ex_dividend_date_status: usesHistoryEstimate
              ? 'estimated_from_dividend_history'
              : usesCalendarDate
              ? current?.field_status?.upcoming_ex_dividend_date || 'available'
              : current?.field_status?.statistics_exDividendDate || 'legacy_statistics',
            source: usesHistoryEstimate
              ? estimatedHistoryEvent?.source || 'Yahoo Finance historical dividend event dates'
              : usesCalendarDate
              ? current?.calendar?.source || 'Yahoo Finance via yfinance Ticker.calendar'
              : 'Yahoo Finance via yfinance Ticker.get_info',
          } : null;
          if (v2Events) {
            const events = v2Events.filter(event => {
              const date = String(event?.date || '');
              const amount = finiteNumber(event?.amount);
              const matchesRequestedYear = years.length === 1 && date.startsWith(`${years[0]}-`);
              return /^\d{4}-\d{2}-\d{2}$/.test(date) &&
                (matchesRequestedYear ? date <= asOf : date >= cutoff && date <= asOf) && amount > 0;
            }).map(event => {
              const eventDate = String(event.date);
              const rightsDates = rightsDatesByEvent.get(`${symbol}|${eventDate}`) || {};
              return { date: eventDate, amount: finiteNumber(event.amount),
                amount_basis: current?.dividend?.event_amount_basis || 'yfinance_split_adjusted_assumption',
                row_repaired_by_yfinance: event.row_repaired_by_yfinance === true,
                final_rights_date: rightsDates.finalRightsDate || null, record_date: rightsDates.recordDate || null };
            }).sort((a, b) => a.date.localeCompare(b.date));
            return {
              symbol,
              history_status: historyStatus,
              amount_basis: current?.dividend?.event_amount_basis || 'yfinance_split_adjusted_assumption',
              months: [...new Set(events.map(event => Number(event.date.slice(5, 7))))].sort((a, b) => a - b),
              events,
              upcoming_event: upcomingEvent,
            };
          }
          return { symbol, history_status: historyStatus, months: [], events: [], upcoming_event: upcomingEvent };
        }));
        return json({ as_of: asOf, lookback_days: 365,
          rights_dates_generated_at: rightsDatesDocument?.generated_at || null, items });
      } catch {
        return json({ error: 'dividend_history_unavailable' }, 503);
      }
    }

    if (url.pathname === '/api/v1/popular-holdings') {
      if (!env.MARKET_DATA) return json({ error: 'market_data_unavailable' }, 503);
      const requestedDate = String(url.searchParams.get('date') || '');
      if (!/^\d{4}-\d{2}-\d{2}$/.test(requestedDate)) return json({ error: 'invalid_request' }, 400);
      const offset = Math.max(0, Math.min(100000, Number.parseInt(url.searchParams.get('offset') || '0', 10) || 0));
      const limit = Math.max(1, Math.min(20, Number.parseInt(url.searchParams.get('limit') || '20', 10) || 20));
      const catalogObject = await env.MARKET_DATA.get('market/yfinance/search_catalog.json');
      if (!catalogObject) return json({ error: 'search_catalog_unavailable' }, 503);
      const catalog = JSON.parse(await catalogObject.text());
      const completionObject = await env.MARKET_DATA.get('market/yfinance/live_quotes_completion.json');
      const completion = completionObject ? await completionObject.json() : {};
      const publishedRunId = String(completion.last_completed_run_id || '');
      const stableMarketDate = publishedRunId
        ? String(completion.last_completed_date || '')
        : completion.last_completed_date
          ? '0000-00-00'
          : String(catalog.market_data_date || '');
      const legacyUnpublished = !publishedRunId && !!completion.last_completed_date;
      const candidates = (catalog.items || [])
        .filter(row => irbankR2Code(String(row?.code || ''))
          && finiteNumber(row?.price) > 0
          && (!legacyUnpublished ? (!stableMarketDate || !row.quote_date || String(row.quote_date) <= stableMarketDate) : !row.quote_date)
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
        const liveObject = publishedRunId
          ? await env.MARKET_DATA.get(liveQuoteRunObjectKey(publishedRunId, code))
          : null;
        const liveQuote = liveObject ? await liveObject.json() : null;
        const catalogDate = String(row.quote_date || '');
        const catalogPrice = !catalogDate || !stableMarketDate || catalogDate <= stableMarketDate
          ? finiteNumber(row.price)
          : null;
        const currentPrice = finiteNumber(liveQuote?.close) || catalogPrice;
        if (!(currentPrice > 0)) return null;
        const statistics = document.statistics_current || {};
        const reportedDps = finiteNumber(statistics.dividendRate);
        const catalogDps = finiteNumber(row.annual_dividend_per_share);
        const dps = reportedDps !== null && reportedDps >= 0
          ? reportedDps
          : catalogDps !== null && catalogDps >= 0 ? catalogDps : null;
        const yieldRate = dps !== null && dps >= 0
          ? (dps / currentPrice) * 100
          : null;
        return {
          code,
          name: String(row.name || code),
          sector: String(row.sector || '').trim() || null,
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

    if (url.pathname === '/api/v1/share-split-history') {
      if (!env.MARKET_DATA) return json({ error: 'market_data_unavailable' }, 503);
      const code = yfinanceV2Code(url.searchParams.get('code'));
      const from = String(url.searchParams.get('from') || '');
      const to = String(url.searchParams.get('to') || '');
      const isValidDate = value => {
        if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) return false;
        const timestamp = Date.parse(`${value}T00:00:00.000Z`);
        return Number.isFinite(timestamp) && new Date(timestamp).toISOString().slice(0, 10) === value;
      };
      if (!code || !isValidDate(from) || !isValidDate(to)
        || from > to || Number(to.slice(0, 4)) - Number(from.slice(0, 4)) > 9) {
        return json({ error: 'invalid_request' }, 400);
      }
      const symbol = yfinanceV2Symbol(code);
      const years = Array.from(
        { length: Number(to.slice(0, 4)) - Number(from.slice(0, 4)) + 1 },
        (_, index) => Number(from.slice(0, 4)) + index,
      );
      try {
        const [events, current] = await Promise.all([
          readV2SplitEvents(env, code, years),
          symbol ? readV2Json(env, `${YFINANCE_V2_ROOT}/securities/${symbol}/current.json`) : Promise.resolve(null),
        ]);
        const records = (events || []).map(event => {
          const date = String(event?.date || '');
          const ratio = finiteNumber(event?.ratio);
          return /^\d{4}-\d{2}-\d{2}$/.test(date) && date >= from && date <= to
            && ratio != null && ratio > 0 && Math.abs(ratio - 1) > 1e-9
            ? { date, ratio }
            : null;
        }).filter(Boolean);
        const uniqueRecords = [...new Map(records.map(event => [
          `${event.date}:${event.ratio}`,
          event,
        ])).values()].sort((a, b) => a.date.localeCompare(b.date));
        const storedHistoryStatus = current?.field_status?.split_history;
        const historyStatus = uniqueRecords.length > 0
          ? 'available'
          : storedHistoryStatus || (events ? 'empty' : 'not_fetched');
        const response = json({ code, history_status: historyStatus, events: uniqueRecords });
        response.headers.set('cache-control', 'public, max-age=300');
        return response;
      } catch {
        return json({ error: 'share_split_history_unavailable' }, 503);
      }
    }

    if (url.pathname === '/api/v1/monthly-price-history') {
      if (!env.MARKET_DATA) return json({ error: 'market_data_unavailable' }, 503);
      const codes = [...new Set(String(url.searchParams.get('codes') || '')
        .split(',')
        .map(value => yfinanceV2Code(value))
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
      const catalog = await readV2Catalog(env);
      const stableMarketDate = String(catalog?.market_data_date || '');
      if (!/^\d{4}-\d{2}-\d{2}$/.test(stableMarketDate)) {
        return json({ error: 'market_data_unavailable' }, 503);
      }
      const currentMonth = today.slice(0, 7);
      const cutoffs = months.map(month => {
        if (month >= currentMonth) return [month, stableMarketDate];
        const nextMonth = new Date(`${month}-01T00:00:00.000Z`);
        nextMonth.setUTCMonth(nextMonth.getUTCMonth() + 1);
        nextMonth.setUTCDate(0);
        return [month, nextMonth.toISOString().slice(0, 10)];
      });
      const items = (await Promise.all(codes.map(async code => {
        const v2Prices = await readV2MonthlyPrices(env, code, months);
        return { code, prices: v2Prices || [] };
      }))).filter(Boolean);
      const response = json({ items, requested_months: months });
      response.headers.set('cache-control', 'public, max-age=300');
      return response;
    }

    if (url.pathname === '/api/v1/live-quotes') {
      if (!env.MARKET_DATA) return json({ error: 'market_data_unavailable' }, 503);
      const limit = Math.max(1, Math.min(250, Number.parseInt(url.searchParams.get('limit') || '200', 10) || 200));
      const requestedRunId = String(url.searchParams.get('run_id') || '');
      const requestedDate = String(url.searchParams.get('market_data_date') || '');
      const cursor = String(url.searchParams.get('cursor') || '');
      const codesParam = url.searchParams.get('codes');
      const requestedCodes = codesParam == null ? null : [...new Set(codesParam.split(',').map(value => yfinanceV2Code(value)))];
      if ((requestedRunId && !CATALOG_GENERATION_PATTERN.test(requestedRunId))
        || (requestedDate && !/^\d{4}-\d{2}-\d{2}$/.test(requestedDate))
        || (cursor && !/^\d{1,8}$/.test(cursor))
        || (codesParam != null && (!requestedCodes.length || requestedCodes.length > 50 || requestedCodes.some(code => !code)))) {
        return json({ error: 'invalid_request' }, 400);
      }

      try {
        const catalog = await readV2Catalog(env, requestedRunId);
        if (!catalog) return json({ error: 'catalog_unavailable' }, 503);
        const generation = String(catalog.generation || catalog.market_data_date || '');
        const marketDataDate = String(catalog.market_data_date || '');
        if (!CATALOG_GENERATION_PATTERN.test(generation) || !marketDataDate) return json({ error: 'catalog_invalid' }, 503);
        if (requestedRunId && requestedRunId !== generation) return json({ error: 'catalog_generation_mismatch' }, 409);
        if (requestedDate && requestedDate !== marketDataDate) return json({ error: 'catalog_generation_mismatch' }, 409);
        const catalogItems = catalog.items || [];
        const offset = Math.min(catalogItems.length, Number.parseInt(cursor || '0', 10));
        const requestedItems = requestedCodes
          ? requestedCodes.map(code => catalogItems.find(row => String(row.code || '').toUpperCase() === code)).filter(Boolean)
          : catalogItems.slice(offset, offset + limit);
        const items = await Promise.all(requestedItems.map(async row => {
          const item = {
            code: row.code,
            price: row.price,
            quote_date: row.quote_date,
            annual_dividend_per_share: row.annual_dividend_per_share,
            trailing_12_month_distribution_per_share: row.trailing_12_month_distribution_per_share,
            dividend_period: row.dividend_period,
            dividend_amount_basis: row.dividend_amount_basis,
            yield_rate: row.yield_rate,
            yield_basis: row.yield_basis,
            dividend_status: row.dividend_status,
          };
          if (!requestedCodes) return item;

          // The full catalog may lag the per-security current document. For a
          // portfolio refresh, read the fiscal forecast directly so a trailing
          // 12-month distribution total cannot be mistaken for annual DPS.
          const code = String(row.code || '').toUpperCase();
          const symbol = yfinanceV2Symbol(code);
          const current = symbol
            ? await readV2Json(env, `${YFINANCE_V2_ROOT}/securities/${symbol}/current.json`)
            : null;
          const dividend = current?.dividend;
          if (!dividend) {
            return {
              ...item,
              annual_dividend_per_share: null,
              dividend_period: 'unknown',
              yield_rate: null,
              yield_basis: null,
              dividend_status: 'unknown',
            };
          }
          const annualDps = selectedAnnualDividendPerShare(code, dividend, current.updated_at);
          const close = finiteNumber(current.price?.close) ?? finiteNumber(row.price);
          return {
            ...item,
            price: close ?? item.price,
            quote_date: current.price?.date ?? item.quote_date,
            annual_dividend_per_share: annualDps,
            trailing_12_month_distribution_per_share: finiteNumber(dividend.trailing_12_month_per_share),
            dividend_period: dividend.yield_basis || 'unknown',
            dividend_amount_basis: dividend.event_amount_basis || item.dividend_amount_basis,
            yield_rate: yieldFromDividend(close, annualDps),
            yield_basis: dividend.yield_basis || null,
            dividend_status: dividend.dividend_status || 'unknown',
          };
        }));
        const nextOffset = offset + items.length;
        const response = json({ run_id: generation, market_data_date: marketDataDate, complete: true,
          items, next_cursor: requestedCodes ? null : nextOffset < catalogItems.length ? String(nextOffset) : null });
        response.headers.set('cache-control', 'public, max-age=300');
        return response;
      } catch {
        return json({ error: 'live_quotes_unavailable' }, 503);
      }
    }

    if (url.pathname === '/api/v1/income-advice-candidates') {
      const object = await env.MARKET_DATA?.get(`${YFINANCE_V2_ROOT}/derived/income_advice/current.json`);
      if (!object) return json({ error: 'income_advice_unavailable' }, 503);
      try {
        const catalog = await object.json();
        if (!Array.isArray(catalog?.items) || !catalog?.generation || !catalog?.generated_at) {
          return json({ error: 'income_advice_invalid' }, 503);
        }
        const response = json(catalog);
        response.headers.set('cache-control', 'public, max-age=300');
        return response;
      } catch {
        return json({ error: 'income_advice_unavailable' }, 503);
      }
    }

    if (url.pathname === '/api/v1/search-catalog') {
      const v2Catalog = await readV2Catalog(env);
      if (!v2Catalog) return json({ error: 'search_catalog_unavailable' }, 503);
      const response = json({
        ...v2Catalog,
        items: v2Catalog.items.map(({ income_advice, ...row }) => row),
      });
      response.headers.set('cache-control', 'public, max-age=300');
      return response;
    }

    if (url.pathname === '/api/v1/security-search') {
      const query = String(url.searchParams.get('q') || '').trim().slice(0, 100);
      if (!query) return json({ items: [] });
      const v2Catalog = await readV2Catalog(env);
      if (!v2Catalog) return json({ error: 'security_catalog_unavailable' }, 503);
      const normalizeSearchText = value => String(value || '').normalize('NFKC').toLowerCase();
      const needle = normalizeSearchText(query);
      const items = (v2Catalog.items || [])
        .filter(row => normalizeSearchText(row.code).includes(needle)
          || normalizeSearchText(row.name).includes(needle))
        .sort((a, b) => {
          const aCode = normalizeSearchText(a.code);
          const bCode = normalizeSearchText(b.code);
          const rank = value => value === needle ? 0 : value.startsWith(needle) ? 1 : 2;
          return rank(aCode) - rank(bCode) || String(a.name || '').localeCompare(String(b.name || ''), 'ja');
        })
        .slice(0, 30)
        .map(({ code, name, price, annual_dividend_per_share, trailing_12_month_distribution_per_share,
          dividend_period, dividend_amount_basis, yield_rate, yield_basis, security_type, dividend_status }) => ({
          code, name, price, annual_dividend_per_share, trailing_12_month_distribution_per_share,
          dividend_period, dividend_amount_basis, yield_rate, yield_basis, security_type, dividend_status,
        }));
      return json({ items, generation: v2Catalog.generation || null });
    }

    const securityMatch = url.pathname.match(/^\/api\/v1\/securities\/([A-Z0-9]{4,5})$/);
    if (securityMatch) {
      if (request.method !== 'GET') return json({ error: 'method_not_allowed' }, 405);
      const code = yfinanceV2Code(securityMatch[1]);
      if (!code) return json({ error: 'invalid_security_code' }, 400);
      const catalog = await readV2Catalog(env);
      if (!catalog) return json({ error: 'security_catalog_unavailable' }, 503);
      const item = (catalog.items || []).find(row => String(row.code || '').toUpperCase() === code);
      if (!item) return json({ error: 'not_found' }, 404);
      const symbol = yfinanceV2Symbol(code);
      if (!symbol) return json({ error: 'invalid_security_code' }, 400);
      const base = `${YFINANCE_V2_ROOT}/securities/${symbol}`;
      const [profile, current, sync, dividendHistoryBounds] = await Promise.all([
        readV2Json(env, `${base}/profile.json`),
        readV2Json(env, `${base}/current.json`),
        readV2Json(env, `${base}/sync.json`),
        readV2DividendHistoryBounds(env, symbol),
      ]);
      if (!profile || !current) return json({ error: 'security_data_unavailable' }, 503);
      const dividend = current.dividend || {};
      const actionHistory = current.action_history || {};
      const syncFieldStatus = sync?.field_status || {};
      const dividendHistoryStatus = current.field_status?.dividend_history
        || syncFieldStatus.dividend_history
        || null;
      const selectedDps = selectedAnnualDividendPerShare(code, dividend, current.updated_at);
      const valuation = calculateDailyValuation(current);
      const infoFailure = sync?.info_refresh_failure || {};
      const companyInfoHasData = Boolean(
        profile.display_name || profile.short_name || profile.long_name
        || Object.keys(current.statistics || {}).length,
      );
      const companyInfoHasExactFetchDate = Boolean(
        sync?.info_last_fetched_session_date || current.statistics_last_fetched_session_date,
      );
      const companyInfoLastFetchedSessionDate = sync?.info_last_fetched_session_date
        || current.statistics_last_fetched_session_date
        || (companyInfoHasData ? sync?.last_success_session_date : null)
        || null;
      return json({
        code,
        name: profile.display_name || profile.short_name || profile.long_name || profile.english_name || code,
        dividend: {
          annual_dps: selectedDps,
          annual_dps_adjusted: selectedDps,
          trailing_12_month_per_share: finiteNumber(dividend.trailing_12_month_per_share),
          yield_basis: dividend.yield_basis || null,
          field_status: current.field_status || {},
        },
        market_price: current.price || null,
        market_yield_rate: yieldFromDividend(current.price?.close, selectedDps) ?? finiteNumber(dividend.yield_rate),
        dividend_status: dividend.dividend_status || 'unknown',
        valuation: {
          per: valuation.per,
          pbr: valuation.pbr,
          source: valuation.source,
          price_date: valuation.price_date,
          statistics_last_fetched_session_date: valuation.statistics_last_fetched_session_date,
          formulas: valuation.formulas,
        },
        data_retrieval: {
          yfinance_ticker: profile.symbol || current.symbol || symbol,
          source: current.source || profile.source || null,
          currency: current.price?.currency || null,
          last_attempt_at: sync?.last_attempt_at || null,
          processing_status: sync?.status || null,
          failure_reason: sync?.failure_reason || null,
          company_info: {
            status: current.field_status?.statistics_refresh
              || sync?.info_refresh_status
              || (companyInfoHasData ? 'stale' : 'not_fetched'),
            last_fetched_session_date: companyInfoLastFetchedSessionDate,
            last_fetched_at: companyInfoHasExactFetchDate
              ? sync?.info_last_fetched_at || current.statistics_last_fetched_at || null
              : null,
            fetch_date_basis: companyInfoHasExactFetchDate
              ? 'company_info_fetch'
              : companyInfoLastFetchedSessionDate ? 'last_successful_sync_fallback' : null,
            last_attempt_session_date: sync?.info_last_attempt_session_date || null,
            last_attempt_at: sync?.info_last_attempt_at || null,
            last_attempt_status: sync?.info_last_attempt_status || null,
            refresh_pending: sync?.info_refresh_pending === true,
            failure_class: infoFailure.failure_class || null,
            failure_reason: infoFailure.reason || null,
          },
          dividend_history: {
            status: dividendHistoryStatus,
            last_attempt_status: actionHistory.status || null,
            source: actionHistory.source || null,
            yfinance_version: actionHistory.yfinance_version || sync?.yfinance_version || null,
            stored_history_status: dividendHistoryBounds.status,
            stored_history_start: dividendHistoryBounds.start,
            stored_history_end: dividendHistoryBounds.end,
            last_coverage_start: actionHistory.coverage_start || null,
            last_coverage_end: actionHistory.coverage_end || null,
            full_history_coverage_end: actionHistory.full_history_coverage_end || null,
            last_coverage_event_count: finiteNumber(actionHistory.dividend_event_count),
            period: actionHistory.period || null,
          },
          ex_dividend_calendar: {
            date: current.calendar?.ex_dividend_date || null,
            status: current.field_status?.upcoming_ex_dividend_date || 'not_fetched',
            last_attempt_status: current.calendar?.last_attempt_status || null,
            fetched_at: current.calendar?.fetched_at || null,
            source: current.calendar?.source || null,
          },
        },
      });
    }

    if (url.pathname === '/api/v1/irbank/manifest') {
      return irbankR2Object(env, 'market/irbank/v1/manifest.json');
    }

    if (url.pathname === '/api/v1/irbank/catalog') {
      return irbankR2Object(env, 'market/irbank/v1/catalog.json');
    }

    const financialMatch = url.pathname.match(/^\/api\/v1\/financial-data\/([A-Z0-9]{4,5})$/);
    if (financialMatch) {
      if (request.method !== 'GET') return json({ error: 'method_not_allowed' }, 405);
      const code = yfinanceV2Code(financialMatch[1]);
      if (!code) return json({ error: 'invalid_security_code' }, 400);
      const v2Payload = await readV2FinancialPayload(env, code);
      if (v2Payload) return json(v2Payload);
      return json({ error: 'not_found' }, 404);
    }

    const irbankMatch = url.pathname.match(/^\/api\/v1\/irbank\/securities\/([^/]+)(?:\/(prices|weekly-margin|jsf-margin))?$/);
    if (irbankMatch) {
      const code = irbankR2Code(decodeURIComponent(irbankMatch[1]));
      if (!code) return json({ error: 'invalid_security_code' }, 400);
      const familyFile = irbankMatch[2] === 'prices'
        ? 'daily_prices.json'
        : irbankMatch[2] === 'weekly-margin'
          ? 'weekly_margin.json'
          : irbankMatch[2] === 'jsf-margin'
            ? 'jsf_margin.json'
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
      let security = await env.DB.prepare(
        'SELECT security_code, security_code_display, name, metrics_json FROM securities WHERE security_code = ? OR security_code_display = ? LIMIT 1'
      ).bind(code, requested).first();
      const displayCode = yfinanceV2Code(requested);
      const currentProfile = displayCode
        ? await readV2Json(env, `${YFINANCE_V2_ROOT}/securities/${yfinanceV2Symbol(displayCode)}/profile.json`)
        : null;
      if (security && currentProfile) {
        security = { ...security, name: currentProfile.display_name || currentProfile.short_name || currentProfile.long_name || security.name };
      }
      if (!security) {
        if (!currentProfile) return json({ error: 'not_found' }, 404);
        security = {
          security_code: displayCode.length === 4 ? `${displayCode}0` : displayCode,
          security_code_display: displayCode,
          name: currentProfile.display_name || currentProfile.short_name || currentProfile.long_name || displayCode,
          metrics_json: '[]',
        };
      }
      const targetYield = url.searchParams.get('target_yield');
      return json(await buildAnalysisSnapshot(env, security, targetYield === null ? null : Number(targetYield)));
    }

    const match = url.pathname.match(/^\/api\/v1\/securities\/([^/]+)$/);
    if (match) {
      const requested = decodeURIComponent(match[1]);
      const code = yfinanceV2Code(requested);
      if (!code) return json({ error: 'invalid_security_code' }, 400);
      const symbol = yfinanceV2Symbol(code);
      const publishedCatalog = await readV2Catalog(env);
      if (!publishedCatalog || !(publishedCatalog.items || []).some(row => String(row.code || '').toUpperCase() === code)) {
        return json({ error: 'not_found' }, 404);
      }
      const [v2Profile, v2Current] = await Promise.all([
        readV2Json(env, `${YFINANCE_V2_ROOT}/securities/${symbol}/profile.json`),
        readV2Json(env, `${YFINANCE_V2_ROOT}/securities/${symbol}/current.json`),
      ]);
      if (!v2Profile || !v2Current) return json({ error: 'not_found' }, 404);
      const includeMarket = url.searchParams.get('include_market') !== 'false';
      const stats = v2Current.statistics || {};
      const valuation = calculateDailyValuation(v2Current);
      return json({
        code,
        canonical_code: `${code}0`,
        name: v2Profile.display_name || v2Profile.short_name || v2Profile.long_name || v2Profile.english_name || code,
        metrics: [],
        dividend: v2Current.dividend ? {
          fiscal_year: null,
          annual_dps: selectedAnnualDividendPerShare(code, v2Current.dividend, v2Current.updated_at),
          annual_dps_adjusted: selectedAnnualDividendPerShare(code, v2Current.dividend, v2Current.updated_at),
          payout_ratio: stats.payoutRatio ?? null,
          source: 'Yahoo Finance via yfinance',
        } : null,
        market_yield_rate: v2Current.dividend?.yield_rate ?? null,
        dividend_status: v2Current.dividend?.dividend_status || 'unknown',
        financial: null,
        market_price: includeMarket ? v2Current.price || null : null,
        valuation: includeMarket ? {
          per: valuation.per,
          pbr: valuation.pbr,
          source: valuation.source,
          price_date: valuation.price_date,
          statistics_last_fetched_session_date: valuation.statistics_last_fetched_session_date,
          formulas: valuation.formulas,
        } : null,
        field_status: v2Current.field_status || {},
      });
    }

    return json({ error: 'not_found' }, 404);
  },

  async scheduled(_controller, env, ctx) {
    ctx.waitUntil(env.DB.prepare("DELETE FROM support_feedback WHERE created_at < strftime('%Y-%m-%dT%H:%M:%fZ', 'now', '-90 days')").run());
    ctx.waitUntil(runScheduledAnalysis(env));
  },

  async queue(batch) {
    // Retire any queued requests from the former live Yahoo quote collector.
    for (const message of batch.messages) message.ack();
  }
};
