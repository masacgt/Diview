import { readFeedback, saveFeedback } from './feedback.mjs';
import { safelyDeliverFeedbackEmail, retryFeedbackEmails } from './feedback-email.mjs';
import financialUnitContract from './financial_unit_contract.json' with { type: 'json' };
import { withMarketReadCache, sharedMarketJson, sharedMarketDocuments } from './market-read-cache.mjs';

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
    const [current, profileDocument] = await Promise.all([
      readV2Json(env, `${YFINANCE_V2_ROOT}/securities/${symbol}/current.json`),
      readV2Json(env, `${YFINANCE_V2_ROOT}/securities/${symbol}/profile.json`),
    ]);
    if (current) {
      const profile = profileDocument || {};
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
        // Updating financials/current metadata does not refresh the quote.
        generated_at: /^\d{4}-\d{2}-\d{2}$/.test(price.date || '') ? `${price.date}T06:00:00.000Z` : null,
        status: current.field_status?.price ?? null,
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
const IRBANK_V2_DIVIDENDS_KEY = `${YFINANCE_V2_ROOT}/issuer_dividends/current.json`;
const IRBANK_V2_DIVIDEND_SHARDS_ROOT = `${YFINANCE_V2_ROOT}/issuer_dividends/shards`;
const IRBANK_V2_DIVIDEND_SHARD_MANIFEST_KEY = `${IRBANK_V2_DIVIDEND_SHARDS_ROOT}/current.json`;
const DIVIDEND_SHARD_PREFIX_LENGTH = 2;

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
  const reportedPer = finiteNumber(statistics.trailingPE);
  const reportedPbr = finiteNumber(statistics.priceToBook);
  const sources = current?.valuation_calculation?.sources || {};
  return {
    per: reportedPer ?? (price > 0 && eps > 0 ? Math.round(price / eps * 1e6) / 1e6 : null),
    pbr: reportedPbr ?? (price > 0 && bookValue > 0 ? Math.round(price / bookValue * 1e6) / 1e6 : null),
    source: 'yfinance_reported_with_daily_calculation_fallback',
    per_source: sources.trailingPE || (reportedPer !== null ? 'stored_statistic' : 'calculated_fallback'),
    pbr_source: sources.priceToBook || (reportedPbr !== null ? 'stored_statistic' : 'calculated_fallback'),
    price_date: current?.price?.date || null,
    statistics_last_fetched_session_date: current?.statistics_last_fetched_session_date || null,
    formulas: {
      per: 'price.close / statistics.trailingEps',
      pbr: 'price.close / statistics.bookValue',
    },
  };
}

function yfinanceV2Symbol(code) {
  const normalized = yfinanceV2Code(code);
  return normalized ? `${normalized}.T` : null;
}

function selectedIrbankDividend(item) {
  const reportedYield = finiteNumber(item?.yield?.rate);
  const yieldRate = reportedYield !== null && reportedYield >= 0 ? reportedYield : null;
  const yieldAsOf = /^\d{4}-\d{2}-\d{2}$/.test(String(item?.yield?.as_of || ''))
    ? String(item.yield.as_of)
    : null;
  const yieldSource = yieldRate !== null ? String(item?.yield?.source || 'IRBANK screening dividendYield') : null;
  const yieldAttribution = item?.yield?.attribution || null;
  const forecast = item?.forecast;
  const forecastDps = finiteNumber(forecast?.annual_dps_current_share_basis);
  const forecastPeriodEnd = String(forecast?.period_end || '');
  const firstPeriodEnd = String(forecast?.first_period_end || '');
  const reitTwoPeriodForecast = forecast?.amount_basis === 'reit_two_period_forecast_including_excess_distributions';
  if (forecast?.status === 'available' && forecastDps !== null && forecastDps >= 0
    && /^\d{4}-\d{2}-\d{2}$/.test(forecastPeriodEnd) && forecastPeriodEnd > tokyoDate(Date.now() / 1000)
    && (!reitTwoPeriodForecast || (/^\d{4}-\d{2}-\d{2}$/.test(firstPeriodEnd)
      && firstPeriodEnd > tokyoDate(Date.now() / 1000) && firstPeriodEnd < forecastPeriodEnd))) {
    return {
      annual_dps: forecastDps,
      dividend_period: reitTwoPeriodForecast ? 'forward_two_fiscal_periods_forecast' : 'next_fiscal_year_forecast',
      yield_basis: reitTwoPeriodForecast ? 'forward_two_fiscal_periods_forecast' : 'next_fiscal_year_forecast',
      dividend_amount_basis: forecast.amount_basis || 'irbank_forecast_current_share_basis',
      dividend_status: forecastDps > 0 ? 'has_dividend' : 'no_dividend',
      fiscal_year: finiteNumber(forecast.fiscal_year),
      forecast,
      yield_rate: yieldRate,
      yield_as_of: yieldAsOf,
      yield_source: yieldSource,
      yield_attribution: yieldAttribution,
    };
  }

  const actual = item?.actual;
  const actualAdjusted = finiteNumber(actual?.annual_dps_adjusted);
  const actualReported = finiteNumber(actual?.annual_dps);
  const actualDps = actualAdjusted ?? actualReported;
  if (item?.actual_status === 'available' && actualDps !== null && actualDps >= 0) {
    return {
      annual_dps: actualDps,
      dividend_period: 'issuer_reported_fiscal_year',
      yield_basis: 'issuer_reported_fiscal_year',
      dividend_amount_basis: actualAdjusted !== null ? 'irbank_adjusted_fiscal_dps' : 'irbank_reported_fiscal_dps',
      dividend_status: actualDps > 0 ? 'has_dividend' : 'no_dividend',
      fiscal_year: finiteNumber(actual.fiscal_year),
      forecast: null,
      yield_rate: yieldRate,
      yield_as_of: yieldAsOf,
      yield_source: yieldSource,
      yield_attribution: yieldAttribution,
    };
  }
  // An empty issuer feed is not evidence that the issuer pays no dividend.
  // In particular, REITs and ETFs can have distribution events outside this feed.
  return {
    annual_dps: null,
    dividend_period: 'unknown',
    yield_basis: null,
    dividend_amount_basis: null,
    dividend_status: 'unknown',
    fiscal_year: null,
    forecast: null,
    yield_rate: yieldRate,
    yield_as_of: yieldAsOf,
    yield_source: yieldSource,
    yield_attribution: yieldAttribution,
  };
}

async function readIrbankDividendIndex(env, code = null) {
  if (code === null || code === undefined) {
    const document = await readV2Json(env, IRBANK_V2_DIVIDENDS_KEY);
    return document && document.items && typeof document.items === 'object' ? document : null;
  }
  const normalized = yfinanceV2Code(code);
  if (!normalized) return null;
  const manifest = await readV2Json(env, IRBANK_V2_DIVIDEND_SHARD_MANIFEST_KEY);
  if (manifest?.schema_version !== 1 || manifest.prefix_length !== DIVIDEND_SHARD_PREFIX_LENGTH
    || !['a', 'b'].includes(manifest.slot)
    || !Array.isArray(manifest.prefixes)) return null;
  const prefix = normalized.slice(0, DIVIDEND_SHARD_PREFIX_LENGTH);
  if (!manifest.prefixes.includes(prefix)) {
    return { source: manifest.source, attribution: manifest.attribution, items: {} };
  }
  const shard = await readV2Json(env, `${IRBANK_V2_DIVIDEND_SHARDS_ROOT}/${manifest.slot}/${prefix}.json`);
  return shard && shard.items && typeof shard.items === 'object'
    ? { source: manifest.source, attribution: manifest.attribution, items: shard.items }
    : { source: manifest.source, attribution: manifest.attribution, items: {} };
}

function irbankDividendForCode(index, code) {
  const normalized = yfinanceV2Code(code);
  return normalized ? selectedIrbankDividend(index?.items?.[normalized]) : selectedIrbankDividend(null);
}

function yfinanceDividendYieldPercent(value) {
  const fraction = finiteNumber(value);
  return fraction !== null && fraction >= 0 && fraction <= 1
    ? Math.round(fraction * 100 * 10000) / 10000
    : null;
}

function yieldFromDividend(price, annualDps, irbankYield = null, yfinanceYield = null, catalogYfinanceYield = null) {
  const yfinanceRate = yfinanceDividendYieldPercent(yfinanceYield)
    ?? finiteNumber(catalogYfinanceYield);
  if (yfinanceRate !== null && yfinanceRate >= 0) return yfinanceRate;
  const reportedYield = finiteNumber(irbankYield);
  if (reportedYield !== null && reportedYield >= 0) return reportedYield;
  const close = finiteNumber(price);
  return close !== null && close > 0 && annualDps !== null
    ? Math.round((annualDps / close * 100) * 10000) / 10000
    : null;
}

function yieldSourceFromDividend(yieldRate, irbankYield, irbankSource, yfinanceYield = null, catalogYfinanceYield = null) {
  if (yfinanceDividendYieldPercent(yfinanceYield) !== null
    || (finiteNumber(catalogYfinanceYield) !== null && finiteNumber(catalogYfinanceYield) >= 0)) {
    return 'yfinance_reported_dividendYield';
  }
  if (finiteNumber(irbankYield) !== null && finiteNumber(irbankYield) >= 0) {
    return irbankSource || 'IRBANK screening dividendYield';
  }
  return yieldRate !== null ? 'calculated_from_irbank_dividend_and_quote' : null;
}

// Search and quote screens must compare the same forecast and price, rather
// than mixing a screening yield from a different date with an annual DPS.
function searchDividend(row, issuer, current) {
  if (issuer.forecast != null) return issuer;
  const fetchedAt = Date.parse(String(current?.statistics_last_fetched_at || ''));
  const age = Date.now() - fetchedAt;
  const forward = finiteNumber(current?.statistics?.dividendRate);
  if (row.security_type === 'equity' && Number.isFinite(fetchedAt) && age >= 0 && age <= 45 * 86400000
      && forward !== null && forward > 0 && current?.field_status?.statistics_dividendRate !== 'failed') {
    return { ...issuer, annual_dps: forward, dividend_period: 'forward_12_month_estimate', fiscal_year: null,
      dividend_amount_basis: 'yfinance_forward_dividend_rate', dividend_status: forward > 0 ? 'has_dividend' : 'no_dividend',
      yield_basis: 'forward_12_month_estimate', yield_as_of: String(current.statistics_last_fetched_at).slice(0, 10),
      yield_source: 'calculated_from_yfinance_dividend_and_quote', forecast: null };
  }
  // Historical issuer actuals must not override an explicit current status.
  // A valid issuer forecast (or fresh forward estimate above) remains authoritative.
  const status = current?.dividend?.dividend_status ?? row?.dividend_status;
  if (status === 'no_dividend' || status === 'unknown') {
    return { ...issuer, annual_dps: status === 'no_dividend' ? 0 : null,
      dividend_period: status === 'no_dividend' ? 'yfinance_reported_no_dividend' : 'unknown',
      dividend_amount_basis: null, dividend_status: status, fiscal_year: null, forecast: null,
      yield_basis: status === 'no_dividend' ? 'yfinance_reported_no_dividend' : null,
      yield_rate: null, yield_as_of: null, yield_source: null, yield_attribution: null };
  }
  return issuer;
}

function searchYield(price, dividend) {
  const close = finiteNumber(price);
  const dps = finiteNumber(dividend.annual_dps);
  return close !== null && close > 0 && dps !== null && dps >= 0
    ? Math.round(dps / close * 1000000) / 10000 : null;
}

function selectedDividendSource(dividend) {
  if (dividend.dividend_status === 'unknown') return null;
  if (dividend.dividend_period === 'yfinance_reported_no_dividend'
      || dividend.dividend_amount_basis === 'yfinance_forward_dividend_rate') return 'Yahoo Finance via yfinance';
  return 'IRBANK';
}

async function readV2Json(env, key) {
  return sharedMarketJson(env, key, async () => {
    const object = await env.MARKET_DATA?.get(key);
    return object ? object.json() : null;
  });
}

async function listV2Documents(env, prefix) {
  return sharedMarketDocuments(env, prefix, () => loadV2Documents(env, prefix));
}

async function loadV2Documents(env, prefix) {
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

const IRBANK_ANNUAL_MONEY_FIELDS = new Set(financialUnitContract.irbank.annual_money_fields);
const IRBANK_VALUATION_MONEY_FIELDS = new Set(financialUnitContract.irbank.valuation_money_fields);
const IRBANK_ANNUAL_FIELD_UNITS = {
  ...Object.fromEntries([...IRBANK_ANNUAL_MONEY_FIELDS].map(field => [field, 'million_jpy'])),
  ...financialUnitContract.irbank.annual_field_units,
};
const IRBANK_VALUATION_FIELD_UNITS = {
  ...Object.fromEntries([...IRBANK_VALUATION_MONEY_FIELDS].map(field => [field, 'hundred_million_jpy'])),
  ...financialUnitContract.irbank.valuation_field_units,
};
const FINANCIAL_ANNUAL_MONEY_FIELDS = IRBANK_ANNUAL_MONEY_FIELDS;
const YFINANCE_NATIVE_FINANCIAL_UNIT = financialUnitContract.api_units.yfinance_native_financial_money;
const YFINANCE_STATISTICS_FIELD_UNITS = financialUnitContract.yfinance_statistics_field_units;

function isReasonableEquityRatio(value) {
  const ratio = finiteNumber(value);
  return ratio !== null && Math.abs(ratio) <= 1000;
}

function normalizedFinancialUnit(value) {
  return typeof value === 'string' && value.trim()
    ? value.trim().toLowerCase().replaceAll('-', '_')
    : null;
}

function normalizedFinancialCurrency(value) {
  if (typeof value !== 'string') return null;
  const currency = value.trim().toUpperCase();
  return /^[A-Z]{3}$/.test(currency) && currency !== 'XXX' ? currency : null;
}

function isVerifiedYfinanceDerivedPercent(calculation, inputFields, yfinanceValues, yfinanceFieldUnits) {
  if (calculation?.unit !== 'percent' || !isReasonableEquityRatio(calculation?.value)) return false;
  const sources = calculation?.input_sources;
  const units = calculation?.input_units;
  return inputFields.every(field => sources?.[field] === 'yfinance'
    && normalizedFinancialUnit(units?.[field]) === YFINANCE_NATIVE_FINANCIAL_UNIT
    && normalizedFinancialUnit(yfinanceFieldUnits[field]) === YFINANCE_NATIVE_FINANCIAL_UNIT
    && finiteNumber(yfinanceValues[field]) !== null);
}

function yfinanceStatisticsFieldUnit(current, field) {
  const declared = current?.statistics_field_units?.[field];
  return normalizedFinancialUnit(declared)
    || normalizedFinancialUnit(YFINANCE_STATISTICS_FIELD_UNITS[field]);
}

function yfinanceStatisticsFieldUnits(current) {
  const declared = current?.statistics_field_units && typeof current.statistics_field_units === 'object'
    ? { ...current.statistics_field_units }
    : {};
  for (const field of Object.keys(YFINANCE_STATISTICS_FIELD_UNITS)) {
    if (Object.hasOwn(current?.statistics || {}, field) && !declared[field]) {
      declared[field] = YFINANCE_STATISTICS_FIELD_UNITS[field];
    }
  }
  const rawDividendYield = finiteNumber(current?.statistics?.dividendYield);
  if (rawDividendYield !== null && rawDividendYield > 1
    && normalizedFinancialUnit(declared.dividendYield) === 'ratio') {
    const calculatedYield = finiteNumber(current?.dividend?.yield_rate);
    const tolerance = Math.max(0.05, Math.abs(rawDividendYield) * 0.03);
    if (current?.dividend?.yield_source === 'calculated_from_yfinance_dividend_and_quote'
      && calculatedYield !== null
      && Math.abs(rawDividendYield - calculatedYield) <= tolerance) {
      declared.dividendYield = 'percent';
    } else {
      delete declared.dividendYield;
    }
  }
  return declared;
}

function ratioAsPercent(value, unit) {
  const number = finiteNumber(value);
  const normalizedUnit = normalizedFinancialUnit(unit);
  if (number === null) return null;
  if (normalizedUnit === 'percent') return number;
  if (normalizedUnit === 'ratio') return number * 100;
  return null;
}

function financialValueWithUnit(row, field, expectedUnit) {
  return normalizedFinancialUnit(row?.field_units?.[field]) === expectedUnit
    ? finiteNumber(row?.[field])
    : null;
}

function seedFieldUnits(section, seedValues, seedRecord, row) {
  const units = {
    ...(row?.field_units && typeof row.field_units === 'object' ? row.field_units : {}),
    ...(seedRecord?.field_units && typeof seedRecord.field_units === 'object' ? seedRecord.field_units : {}),
  };
  const sourceHint = [seedRecord?.source, seedRecord?.source_key, row?.migration_source]
    .filter(Boolean).join(' ').toLowerCase();
  if (sourceHint.includes('irbank')) {
    const knownUnits = section === 'annual' ? IRBANK_ANNUAL_FIELD_UNITS : IRBANK_VALUATION_FIELD_UNITS;
    for (const [field, unit] of Object.entries(knownUnits)) {
      if (Object.hasOwn(seedValues, field) && !units[field]) units[field] = unit;
    }
  }
  return units;
}

// Confirmed cross-issuer legacy records. Match both amounts and source so a
// later independently repaired record is not suppressed merely by its code.
const CONTAMINATED_ANNUAL_SEEDS = {
  '1301': { '2017/03': [6639, 5594], '2023/03': [7070, 6073],
    '2024/03': [7392, 7050], '2025/03': [7957, 7126], '2026/03': [8283, 6973] },
  '2708': { '2014/03': [62268, 27566], '2011/10': [74430, 30631], '2012/10': [78971, 33345],
    '2013/10': [88145, 36479], '2014/10': [96938, 38140], '2015/10': [105307, 42058],
    '2016/10': [113626, 46527], '2017/10': [122766, 52745], '2018/10': [132499, 59070],
    '2019/10': [136134, 68216], '2020/10': [135835, 85102], '2021/10': [147592, 98989],
    '2022/10': [183053, 109621], '2023/10': [211405, 130119], '2024/10': [234950, 139446],
    '2025/10': [245109, 156015] },
};

function quarantineContaminatedAnnualSeed(code, input) {
  const explicit = input?.source_values;
  const seed = explicit?.seed;
  const values = seed?.values;
  const period = String(input?.period_end || input?.fiscal_year || '').replace('-', '/').slice(0, 7);
  const expected = CONTAMINATED_ANNUAL_SEEDS[yfinanceV2Code(code)]?.[period];
  const sourceKey = seed?.source_key || input?.migration_source;
  if (!expected || sourceKey !== `market/irbank/v1/securities/${yfinanceV2Code(code)}/overview.json`
    || finiteNumber(values?.sales) !== expected[0] || finiteNumber(values?.total_assets) !== expected[1]) return input;
  const row = { ...input, field_status: { ...input.field_status },
    field_status_reason: { ...input.field_status_reason }, field_units: { ...input.field_units } };
  const yahoo = explicit?.yfinance?.values || {};
  for (const field of Object.keys(values)) {
    delete row[field];
    delete row.field_units[field];
    if (!Object.hasOwn(yahoo, field)) {
      row.field_status[field] = 'unsupported';
      row.field_status_reason[field] = ['cross_issuer_seed_quarantined'];
    }
  }
  const calculations = explicit?.calculated?.values || {};
  for (const [field, calculation] of Object.entries(calculations)) {
    if (!Object.values(calculation?.input_sources || {}).includes('seed')) continue;
    delete row[field];
    delete row.field_units[field];
    if (!Object.hasOwn(yahoo, field)) {
      row.field_status[field] = 'unsupported';
      row.field_status_reason[field] = ['cross_issuer_seed_quarantined'];
    }
  }
  const safeCalculations = Object.fromEntries(Object.entries(calculations).filter(([, value]) =>
    !Object.values(value?.input_sources || {}).includes('seed')));
  row.source_values = { ...explicit, seed: { ...seed, values: {}, field_units: {},
    validation_status: 'unsupported', reason: 'cross_issuer_seed_quarantined' },
    ...(explicit.calculated ? { calculated: { ...explicit.calculated, values: safeCalculations } } : {}) };
  return row;
}

function quarantineIrbankOverview(code, overview) {
  if (!Array.isArray(overview?.financials?.annual)) return overview;
  const annual = overview.financials.annual.map(row => {
    const period = String(row?.fiscal_year || '').replace('-', '/').slice(0, 7);
    const expected = CONTAMINATED_ANNUAL_SEEDS[code]?.[period];
    if (!expected || finiteNumber(row.sales) !== expected[0] || finiteNumber(row.total_assets) !== expected[1]) return row;
    return { fiscal_year: row.fiscal_year, validation_status: 'unsupported',
      reason: 'cross_issuer_seed_quarantined' };
  });
  return { ...overview, financials: { ...overview.financials, annual } };
}

async function readV2FinancialPayload(env, code) {
  const symbol = yfinanceV2Symbol(code);
  if (!symbol || !env.MARKET_DATA) return null;
  const base = `${YFINANCE_V2_ROOT}/securities/${symbol}`;
  const currentPromise = readV2Json(env, `${base}/current.json`);
  const dividendDocumentsPromise = currentPromise.then(current =>
    Array.isArray(current?.dividend?.calendar_year_metrics?.annual_records)
      ? []
      : listV2Documents(env, `${base}/dividends/`),
  );
  const [profile, current, financialDocuments, dividendDocuments, irbankDividendIndex, irbankOverview] = await Promise.all([
    readV2Json(env, `${base}/profile.json`),
    currentPromise,
    listV2Documents(env, `${base}/financials/`),
    dividendDocumentsPromise,
    readIrbankDividendIndex(env, code),
    irbankR2Code(code)
      ? readV2Json(env, `market/irbank/v1/securities/${irbankR2Code(code)}/overview.json`)
      : Promise.resolve(null),
  ]);
  if (!profile && !current && financialDocuments.length === 0 && dividendDocuments.length === 0
    && !irbankOverview && !irbankDividendIndex?.items?.[yfinanceV2Code(code)]) return null;
  const documentPeriodKey = document => financialPeriodKey(
    document.annual?.period_end ? document.annual
      : document.valuation?.period_end ? document.valuation : document,
  );
  financialDocuments.sort((a, b) => documentPeriodKey(a).localeCompare(documentPeriodKey(b)));
  const sourceRows = (section) => financialDocuments.map(document => {
    let row = document[section];
    if (!row || typeof row !== 'object') return null;
    if (section === 'annual') row = quarantineContaminatedAnnualSeed(code, row);
    const explicit = row.source_values && typeof row.source_values === 'object' ? row.source_values : null;
    const seedRecord = explicit?.seed && typeof explicit.seed === 'object' ? explicit.seed : {};
    const seedValues = { ...(seedRecord.values && typeof seedRecord.values === 'object' ? seedRecord.values : {}) };
    const yfinanceValues = { ...(explicit?.yfinance?.values && typeof explicit.yfinance.values === 'object' ? explicit.yfinance.values : {}) };
    const yfinanceSourceRecord = explicit?.yfinance && typeof explicit.yfinance === 'object' ? explicit.yfinance : {};
    const rawYfinanceUnits = yfinanceSourceRecord.field_units && typeof yfinanceSourceRecord.field_units === 'object'
      ? yfinanceSourceRecord.field_units : {};
    const rawYfinanceCurrencies = yfinanceSourceRecord.field_reported_currencies
      && typeof yfinanceSourceRecord.field_reported_currencies === 'object'
      ? yfinanceSourceRecord.field_reported_currencies : {};
    const reportedYfinanceCurrencies = {};
    const unverifiedYfinanceFields = new Set();
    const yfinanceFieldUnits = {};
    for (const [field, rawUnit] of Object.entries(rawYfinanceUnits)) {
      if (!FINANCIAL_ANNUAL_MONEY_FIELDS.has(field)) {
        yfinanceFieldUnits[field] = rawUnit;
        continue;
      }
      const currency = normalizedFinancialCurrency(rawYfinanceCurrencies[field]);
      const unit = normalizedFinancialUnit(rawUnit);
      if (unit === YFINANCE_NATIVE_FINANCIAL_UNIT) {
        yfinanceFieldUnits[field] = unit;
        if (currency) reportedYfinanceCurrencies[field] = currency;
      }
    }
    if (section === 'annual') {
      for (const field of FINANCIAL_ANNUAL_MONEY_FIELDS) {
        if (!Object.hasOwn(yfinanceValues, field)) continue;
        const unit = normalizedFinancialUnit(rawYfinanceUnits[field]);
        if (unit !== YFINANCE_NATIVE_FINANCIAL_UNIT) {
          unverifiedYfinanceFields.add(field);
          delete yfinanceValues[field];
        }
      }
    }
    const calculatedValues = explicit?.calculated?.values && typeof explicit.calculated.values === 'object' ? explicit.calculated.values : {};
    for (const [key, value] of Object.entries(row)) {
      if (['fiscal_year', 'period_end', 'field_status', 'field_status_reason', 'field_units', 'migration_source', 'source_values'].includes(key)
        || Object.hasOwn(seedValues, key) || Object.hasOwn(yfinanceValues, key)
        || Object.hasOwn(calculatedValues, key)) continue;
      if (row.field_status?.[key] === 'available' && !row.migration_source) yfinanceValues[key] = value;
      else seedValues[key] = value;
    }
    if (section === 'annual') {
      for (const field of FINANCIAL_ANNUAL_MONEY_FIELDS) {
        if (!Object.hasOwn(yfinanceValues, field)) continue;
        if (normalizedFinancialUnit(rawYfinanceUnits[field]) !== YFINANCE_NATIVE_FINANCIAL_UNIT) {
          unverifiedYfinanceFields.add(field);
          delete yfinanceValues[field];
        }
      }
    }
    const sanitizedYfinanceMetadata = explicit?.yfinance && typeof explicit.yfinance === 'object'
      ? Object.fromEntries(Object.entries(explicit.yfinance).filter(([key]) => key !== 'field_currencies'))
      : null;
    const sanitizedYfinanceRecord = sanitizedYfinanceMetadata ? {
      ...sanitizedYfinanceMetadata,
      values: yfinanceValues,
      field_units: yfinanceFieldUnits,
      field_reported_currencies: reportedYfinanceCurrencies,
      ...(unverifiedYfinanceFields.size ? { unverified_fields: [...unverifiedYfinanceFields].sort() } : {}),
    } : null;
    const seedUnits = seedFieldUnits(section, seedValues, seedRecord, row);
    const seedRatio = seedUnits.equity_ratio_computed === 'percent'
      ? finiteNumber(seedValues.equity_ratio_computed) : null;
    const ratioCalculation = calculatedValues.equity_ratio_computed;
    const ratioSources = ratioCalculation?.input_sources;
    const ratioUnits = ratioCalculation?.input_units;
    const ratioSource = ratioSources?.equity;
    const ratioInputUnit = normalizedFinancialUnit(ratioUnits?.equity);
    const compatibleRatioUnit = ratioInputUnit === 'jpy' || ratioInputUnit === 'million_jpy'
      || ratioInputUnit === YFINANCE_NATIVE_FINANCIAL_UNIT
      || Boolean(ratioInputUnit && /^[a-z]{3}$/.test(ratioInputUnit));
    const ratioEquityField = finiteNumber(yfinanceValues.stockholders_equity) !== null
      ? 'stockholders_equity' : 'total_equity_gross_minority_interest';
    const ratioEquityCurrency = reportedYfinanceCurrencies[ratioEquityField];
    const ratioAssetsCurrency = reportedYfinanceCurrencies.total_assets;
    const compatibleYfinanceCurrencies = !ratioEquityCurrency || !ratioAssetsCurrency
      || ratioEquityCurrency === ratioAssetsCurrency;
    const sameSourceCalculation = Boolean(
      typeof ratioSource === 'string' && ratioSource.trim()
      && ratioSource === ratioSources?.total_assets
      && ratioInputUnit
      && ratioInputUnit === normalizedFinancialUnit(ratioUnits?.total_assets)
      && compatibleRatioUnit
      && (ratioSource !== 'yfinance' || compatibleYfinanceCurrencies)
      && ratioCalculation?.unit === 'percent'
      && !['unknown', 'mixed', 'unavailable'].includes(String(ratioSource).toLowerCase())
      && isReasonableEquityRatio(ratioCalculation?.value),
    );
    const compatibleCalculatedValues = Object.fromEntries(Object.entries(calculatedValues).filter(([field, calculation]) => {
      if (field === 'equity_ratio_computed') return sameSourceCalculation;
      if (field === 'operating_margin') {
        return isVerifiedYfinanceDerivedPercent(
          calculation, ['operating_profit', 'sales'], yfinanceValues, yfinanceFieldUnits,
        );
      }
      if (field === 'fcf_margin') {
        return isVerifiedYfinanceDerivedPercent(
          calculation, ['operating_cf', 'investing_cf', 'sales'], yfinanceValues, yfinanceFieldUnits,
        );
      }
      return true;
    }));
    const sourceValues = explicit ? {
      ...explicit,
      ...(explicit.seed && typeof explicit.seed === 'object' ? {
        seed: { ...explicit.seed, values: seedValues, field_units: seedUnits },
      } : {}),
      ...(explicit.yfinance && typeof explicit.yfinance === 'object' ? {
        yfinance: sanitizedYfinanceRecord,
      } : {}),
      ...(explicit.calculated && typeof explicit.calculated === 'object' ? {
        calculated: { ...explicit.calculated, values: compatibleCalculatedValues },
      } : {}),
    } : null;
    const flatSeedValues = { ...seedValues };
    const effectiveFieldUnits = {};
    const knownFlatUnits = section === 'annual' ? IRBANK_ANNUAL_FIELD_UNITS : IRBANK_VALUATION_FIELD_UNITS;
    for (const field of new Set([...Object.keys(seedValues), ...Object.keys(yfinanceValues)])) {
      if (Object.hasOwn(yfinanceValues, field)) {
        const unit = yfinanceFieldUnits[field]
          || (FINANCIAL_ANNUAL_MONEY_FIELDS.has(field) ? null : knownFlatUnits[field]);
        if (unit) effectiveFieldUnits[field] = unit;
        continue;
      }
      if (!Object.hasOwn(seedValues, field)) continue;
      const sourceUnit = seedUnits[field];
      const normalizedSourceUnit = normalizedFinancialUnit(sourceUnit);
      const unitScale = normalizedSourceUnit === 'million_jpy' ? 1_000_000
        : normalizedSourceUnit === 'hundred_million_jpy' ? 100_000_000 : null;
      if (unitScale !== null) {
        const amount = finiteNumber(seedValues[field]);
        if (amount !== null) flatSeedValues[field] = amount * unitScale;
        effectiveFieldUnits[field] = 'jpy';
      } else if (sourceUnit) {
        effectiveFieldUnits[field] = sourceUnit;
      }
    }
    const mergedRow = { ...row, ...flatSeedValues, ...yfinanceValues };
    const currencyStatuses = { ...(mergedRow.field_status || {}) };
    const currencyReasons = { ...(mergedRow.field_status_reason || {}) };
    for (const field of unverifiedYfinanceFields) {
      if (Object.hasOwn(flatSeedValues, field)) {
        mergedRow[field] = flatSeedValues[field];
        currencyStatuses[field] = 'legacy_migrated';
        delete currencyReasons[field];
      } else {
        delete mergedRow[field];
        currencyStatuses[field] = 'unsupported';
        currencyReasons[field] = ['financial_currency_unverified'];
      }
    }
    if (unverifiedYfinanceFields.size) {
      mergedRow.field_status = currencyStatuses;
      if (Object.keys(currencyReasons).length) mergedRow.field_status_reason = currencyReasons;
    }
    // A flat legacy ratio is trusted only when its source calculation is proven,
    // or when IRBANK supplied a valid ratio directly. Otherwise the API omits it.
    for (const field of ['calculated_doe', 'net_de_ratio']) {
      if (Object.hasOwn(row, field)) mergedRow[field] = row[field];
    }
    if (section === 'annual') {
      const yfinanceEquityField = finiteNumber(yfinanceValues.stockholders_equity) !== null
        ? 'stockholders_equity'
        : finiteNumber(yfinanceValues.total_equity_gross_minority_interest) !== null
          ? 'total_equity_gross_minority_interest'
          : null;
      const yfinanceEquity = yfinanceEquityField ? finiteNumber(yfinanceValues[yfinanceEquityField]) : null;
      const yfinanceAssets = finiteNumber(yfinanceValues.total_assets);
      const yfinanceEquityUnit = normalizedFinancialUnit(yfinanceFieldUnits[yfinanceEquityField]);
      const yfinanceAssetsUnit = normalizedFinancialUnit(yfinanceFieldUnits.total_assets);
      const recalculatedYfinanceRatio = yfinanceEquity !== null && yfinanceAssets !== null && yfinanceAssets > 0
        && yfinanceEquityUnit === YFINANCE_NATIVE_FINANCIAL_UNIT
        && yfinanceAssetsUnit === yfinanceEquityUnit
        && compatibleYfinanceCurrencies
        ? yfinanceEquity / yfinanceAssets * 100
        : null;
      const safeRatio = sameSourceCalculation
        ? finiteNumber(ratioCalculation.value)
        : isReasonableEquityRatio(recalculatedYfinanceRatio)
          ? recalculatedYfinanceRatio
          : isReasonableEquityRatio(seedRatio)
            ? finiteNumber(seedRatio)
            : null;
      delete mergedRow.equity_ratio;
      delete mergedRow.equity_ratio_computed;
      if (safeRatio !== null) {
        mergedRow.equity_ratio_computed = safeRatio;
        effectiveFieldUnits.equity_ratio_computed = 'percent';
        if (sameSourceCalculation || isReasonableEquityRatio(recalculatedYfinanceRatio)) {
          const priorStatus = mergedRow.field_status?.equity_ratio_computed;
          mergedRow.field_status = {
            ...(mergedRow.field_status || {}),
            equity_ratio_computed: priorStatus === 'stale' ? 'stale' : 'available',
          };
        }
      } else {
        mergedRow.field_status = { ...(mergedRow.field_status || {}), equity_ratio_computed: 'empty' };
      }
    }
    for (const field of ['operating_margin', 'fcf_margin']) {
      const calculation = compatibleCalculatedValues[field];
      const value = finiteNumber(calculation?.value);
      if (value !== null && isVerifiedYfinanceDerivedPercent(
        calculation,
        field === 'operating_margin' ? ['operating_profit', 'sales'] : ['operating_cf', 'investing_cf', 'sales'],
        yfinanceValues,
        yfinanceFieldUnits,
      )) {
        mergedRow[field] = value;
        effectiveFieldUnits[field] = 'percent';
      } else {
        const seedMargin = normalizedFinancialUnit(seedUnits[field]) === 'percent'
          ? finiteNumber(seedValues[field]) : null;
        if (isReasonableEquityRatio(seedMargin)) {
          mergedRow[field] = seedMargin;
          effectiveFieldUnits[field] = 'percent';
        } else {
          delete mergedRow[field];
        }
      }
    }
    for (const field of ['sales', 'operating_profit', 'operating_cf', 'investing_cf']) {
      if (Array.isArray(row.field_status_reason?.[field])
        && row.field_status_reason[field].includes('mixed_source_units_not_comparable')) {
        mergedRow[field] = null;
      }
    }
    if (section === 'annual') {
      const marginFields = ['sales', 'operating_profit', 'operating_cf', 'investing_cf'];
      if (marginFields.some(field => Object.hasOwn(yfinanceValues, field))) {
        const blockedFields = marginFields.filter(field =>
          Object.hasOwn(seedValues, field) && !Object.hasOwn(yfinanceValues, field));
        for (const field of blockedFields) mergedRow[field] = null;
        if (blockedFields.length) {
          mergedRow.field_status = { ...(mergedRow.field_status || {}) };
          mergedRow.field_status_reason = { ...(mergedRow.field_status_reason || {}) };
          for (const field of blockedFields) {
            mergedRow.field_status[field] = 'unsupported';
            mergedRow.field_status_reason[field] = ['mixed_source_units_not_comparable'];
          }
        }
      }
    }
    if (Object.keys(effectiveFieldUnits).length) mergedRow.field_units = effectiveFieldUnits;
    return {
      row: { ...mergedRow, ...(sourceValues ? { source_values: sourceValues } : {}) },
      seed: Object.keys(seedValues).length ? {
        fiscal_year: row.fiscal_year,
        period_end: row.period_end,
        source: seedRecord.source || (row.migration_source ? 'IRBANK' : 'existing v2 snapshot'),
        source_key: seedRecord.source_key || row.migration_source || null,
        values: seedValues,
        field_units: seedUnits,
        validation_status: 'available',
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
  const dividendRows = Array.isArray(calendarYearMetrics?.annual_records)
    ? calendarYearMetrics.annual_records
    : dividendHistory.records;
  const dividends = dividendRows.map(record => ({
    ...record,
    field_units: {
      ...(record.field_units && typeof record.field_units === 'object' ? record.field_units : {}),
      ...(finiteNumber(record.calendar_year_distribution_per_share) !== null
        ? { calendar_year_distribution_per_share: financialUnitContract.api_units.per_share } : {}),
    },
  }));
  const issuerDividendRecordsByPeriod = new Map();
  const irbankIssuerDividendRecords = Array.isArray(irbankOverview?.dividends?.records)
    ? irbankOverview.dividends.records
    : [];
  for (const record of irbankIssuerDividendRecords) {
    const fiscalYearMatch = String(record?.fiscal_year || '').match(/^(\d{4})(?:\/\d{2})?$/);
    if (!fiscalYearMatch) continue;
    const reportedDps = finiteNumber(record.annual_dps);
    const adjustedDps = finiteNumber(record.annual_dps_adjusted);
    if (reportedDps === null && adjustedDps === null) continue;
    const period = String(record.period_end || record.fiscal_period || record.period || fiscalYearMatch[1]).replaceAll('/', '-');
    if (!/^\d{4}(?:-(?:0[1-9]|1[0-2])(?:-\d{2})?)?$/.test(period)) continue;
    issuerDividendRecordsByPeriod.set(period, {
      ...record,
      fiscal_year: Number(fiscalYearMatch[1]),
      fiscal_period: period,
      source: 'IRBANK',
    });
  }
  const irbankDividendItem = irbankDividendIndex?.items?.[yfinanceV2Code(code)] || null;
  const latestIssuerActual = irbankDividendItem?.actual;
  if (irbankDividendItem?.actual_status === 'available' && latestIssuerActual && finiteNumber(latestIssuerActual.fiscal_year) !== null) {
    const fiscalYear = Number(latestIssuerActual.fiscal_year);
    let period = String(latestIssuerActual.period_end || latestIssuerActual.fiscal_period || latestIssuerActual.period || fiscalYear).replaceAll('/', '-');
    const candidates = [...issuerDividendRecordsByPeriod.keys()].filter(key =>
      key === period || (key.startsWith(period + '-') || period.startsWith(key + '-')));
    // Coarser year/month keys identify a period only when the match is unambiguous.
    if (candidates.length === 1) {
      const matched = candidates[0];
      issuerDividendRecordsByPeriod.delete(matched);
      if (matched.length > period.length) period = matched;
    }
    if (candidates.length <= 1 || issuerDividendRecordsByPeriod.has(period)) issuerDividendRecordsByPeriod.set(period, {
      ...latestIssuerActual,
      fiscal_year: fiscalYear,
      fiscal_period: period,
      source: 'IRBANK',
    });
  }
  const issuerDividendRecords = [...issuerDividendRecordsByPeriod.entries()]
    .sort(([a], [b]) => a.localeCompare(b))
    .map(([, record]) => ({
      ...record,
      field_units: {
        ...(record.field_units && typeof record.field_units === 'object' ? record.field_units : {}),
        ...(finiteNumber(record.annual_dps) !== null ? { annual_dps: financialUnitContract.api_units.per_share } : {}),
        ...(finiteNumber(record.annual_dps_adjusted) !== null ? { annual_dps_adjusted: financialUnitContract.api_units.per_share } : {}),
        ...(finiteNumber(record.payout_ratio) !== null ? { payout_ratio: financialUnitContract.api_units.ratio } : {}),
      },
    }));
  const issuerForecast = irbankDividendItem?.forecast && typeof irbankDividendItem.forecast === 'object'
    ? {
      ...irbankDividendItem.forecast,
      field_units: {
        ...(irbankDividendItem.forecast.field_units && typeof irbankDividendItem.forecast.field_units === 'object'
          ? irbankDividendItem.forecast.field_units : {}),
        ...(finiteNumber(irbankDividendItem.forecast.annual_dps_current_share_basis) !== null
          ? { annual_dps_current_share_basis: financialUnitContract.api_units.per_share } : {}),
      },
    }
    : null;
  const shareBasisStatus = dividendShareBasisStatus(code);
  const dividendHistoryStatus = current?.field_status?.dividend_history;
  const rawPayoutRatio = finiteNumber(current?.statistics?.payoutRatio);
  const rawPayoutUnit = yfinanceStatisticsFieldUnit(current, 'payoutRatio');
  const payoutRatio = ratioAsPercent(rawPayoutRatio, rawPayoutUnit);
  return {
    financial_contract_version: 6,
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
      field_units: { payout_ratio: financialUnitContract.api_units.ratio },
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
      edinet_code: irbankDividendItem?.edinet_code || null,
      latest_actual: latestIssuerActual || issuerDividendRecords.at(-1) || null,
      forecast: issuerForecast,
      history_basis: 'issuer_reported_fiscal_year',
      amount_basis: 'irbank_adjusted_fiscal_dps',
      source: irbankDividendIndex?.source || 'IRBANK Connect',
      attribution: irbankDividendIndex?.attribution
        || irbankOverview?.sources?.dividends || null,
      status: issuerDividendRecords.length || irbankDividendItem?.actual_status === 'available' ? 'available' : 'empty',
      forecast_status: irbankDividendItem?.forecast_status || 'not_fetched',
      ...(shareBasisStatus ? { share_basis_status: shareBasisStatus } : {}),
    },
    valuation: { history: valuation, seed_history: seedValuation, field_status: valuationStatus },
    market: current ? {
      price: current.price || null,
      summary: current.statistics || {},
      summary_field_units: yfinanceStatisticsFieldUnits(current),
      statistics_last_fetched_at: current.statistics_last_fetched_at || null,
      statistics_dividend_rate_status: current.field_status?.statistics_dividendRate || null,
    } : null,
    sources: { market: 'Yahoo Finance via yfinance' },
  };
}

async function readV2FinancialRevision(env) {
  if (!env.MARKET_DATA) return null;
  const object = await env.MARKET_DATA.get(`${YFINANCE_V2_ROOT}/financial_revision.json`);
  if (!object) return null;
  const value = await object.json();
  const revision = typeof value?.revision === 'string' ? value.revision.trim() : '';
  if (!revision || !/^[a-f0-9-]{16,64}$/i.test(revision)) return null;
  return {
    schema_version: 1,
    revision,
    updated_at: typeof value.updated_at === 'string' ? value.updated_at : null,
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
  env = withMarketReadCache(env);
  const symbol = yfinanceV2Symbol(security.security_code_display);
  const [marketData, v2Payload, yfinanceCurrent] = await Promise.all([
    latestMarketForSecurity(env, security.security_code_display, marketIndex),
    readV2FinancialPayload(env, security.security_code_display),
    symbol ? readV2Json(env, `${YFINANCE_V2_ROOT}/securities/${symbol}/current.json`) : null,
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
  const sales = financialValueWithUnit(financialData, 'sales', 'jpy');
  const operatingProfit = financialValueWithUnit(financialData, 'operating_profit', 'jpy');
  const reportedOperatingMargin = ratioAsPercent(summary?.operatingMargins, marketData?.summary_field_units?.operatingMargins);
  const operatingMargin = reportedOperatingMargin
    ?? financialValueWithUnit(financialData, 'operating_margin', 'percent')
    ?? (sales > 0 && operatingProfit != null ? operatingProfit / sales * 100 : null);
  const operatingCf = financialValueWithUnit(financialData, 'operating_cf', 'jpy');
  const investingCf = financialValueWithUnit(financialData, 'investing_cf', 'jpy');
  const fcf = operatingCf != null && investingCf != null
    ? operatingCf + investingCf
    : null;
  const equityRatio = financialValueWithUnit(financialData, 'equity_ratio_computed', 'percent')
    ?? financialValueWithUnit(financialData, 'equity_ratio', 'percent');
  const values = {
    per: finiteNumber(summary?.trailingPE),
    pbr: finiteNumber(summary?.priceToBook),
    payout_ratio: finiteNumber(dividendData?.payout_ratio),
    equity_ratio: equityRatio,
    operating_margin: operatingMargin,
    fcf_margin: financialValueWithUnit(financialData, 'fcf_margin', 'percent')
      ?? (fcf != null && sales > 0 ? fcf / sales * 100 : null),
    dividend_growth_years: finiteNumber(dividendData?.consecutive_dividend_increase_years),
  };
  const currentYield = yfinanceDividendYieldPercent(summary?.dividendYield)
    ?? finiteNumber(marketData?.dividend?.yield_rate);
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
      market_stale: ['stale', 'failed', 'empty'].includes(input.marketData?.price?.status)
        || (daysOld(input.marketData?.price?.generated_at) ?? 999) > Number(config.market_stale_days || 3),
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
      analysis_contract: 2,
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
  async fetch(request, env, ctx) {
    env = withMarketReadCache(env);
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
        const notification = safelyDeliverFeedbackEmail(env, input.requestId);
        if (ctx?.waitUntil) ctx.waitUntil(notification);
        else await notification;
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
      url.pathname === '/api/v1/security-categories' ||
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
      const rawEventYears = url.searchParams.get('event_years');
      const requestedEventYears = rawEventYears == null ? null : rawEventYears.split(',').map(value => Number(value.trim()));
      if (symbols.length === 0 || symbols.length > 50 || symbols.some(symbol => !/^[A-Z0-9.^_=-]{1,32}$/.test(symbol))) {
        return json({ error: 'invalid_request' }, 400);
      }

      const todayParts = Object.fromEntries(new Intl.DateTimeFormat('en', {
        timeZone: 'Asia/Tokyo', year: 'numeric', month: '2-digit', day: '2-digit'
      }).formatToParts(new Date()).map(part => [part.type, part.value]));
      const asOf = `${todayParts.year}-${todayParts.month}-${todayParts.day}`;
      const requestedYears = requestedEventYear != null ? [requestedEventYear] : requestedEventYears;
      const hasInvalidRequestedYears = requestedYears != null && (
        requestedYears.length === 0 || requestedYears.length > 10
        || new Set(requestedYears).size !== requestedYears.length
        || requestedYears.some(year => !Number.isInteger(year) || year < 1900 || year > Number(asOf.slice(0, 4)))
      );
      if ((rawEventYear != null && rawEventYears != null) || hasInvalidRequestedYears) {
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
            paymentStartDate: event.payment_start_date || null,
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
          const years = requestedYears != null
            ? requestedYears
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
          const calendarPaymentStartDate = String(
            calendarExDate === upcomingExDate ? current?.calendar?.payment_start_date || '' : '');
          const usesHistoryEstimate = Boolean(estimatedHistoryDate && upcomingExDate === estimatedHistoryDate);
          const usesCalendarDate = Boolean(!usesHistoryEstimate && calendarUpcoming && upcomingExDate === calendarUpcoming);
          const upcomingEvent = upcomingExDate && upcomingExDate > asOf ? {
            ex_dividend_date: upcomingExDate,
            final_rights_date: upcomingRightsDates?.finalRightsDate || null,
            record_date: upcomingRightsDates?.recordDate || null,
            ...(/^\d{4}-\d{2}-\d{2}$/.test(calendarPaymentStartDate)
              ? { payment_start_date: calendarPaymentStartDate }
              : {}),
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
              return /^\d{4}-\d{2}-\d{2}$/.test(date) &&
                (requestedYears != null
                  ? requestedYears.includes(Number(date.slice(0, 4))) && date <= asOf
                  : date >= cutoff && date <= asOf) && amount > 0;
            }).map(event => {
              const eventDate = String(event.date);
              const rightsDates = rightsDatesByEvent.get(symbol + '|' + eventDate) || {};
              const paymentStartDate = String(event.payment_start_date || rightsDates.paymentStartDate || '');
              return { date: eventDate, amount: finiteNumber(event.amount),
                amount_basis: current?.dividend?.event_amount_basis || 'yfinance_split_adjusted_assumption',
                row_repaired_by_yfinance: event.row_repaired_by_yfinance === true,
                final_rights_date: rightsDates.finalRightsDate || null, record_date: rightsDates.recordDate || null,
                ...(/^\d{4}-\d{2}-\d{2}$/.test(paymentStartDate)
                  ? { payment_start_date: paymentStartDate }
                  : {}) };
            }).sort((a, b) => a.date.localeCompare(b.date));
            return {
              symbol,
              history_status: historyStatus,
              ...(rawEventYears == null ? {} : { requested_event_years: requestedYears }),
              amount_basis: current?.dividend?.event_amount_basis || 'yfinance_split_adjusted_assumption',
              months: [...new Set(events
                .filter(event => rawEventYears == null || event.date >= cutoff)
                .map(event => Number(event.date.slice(5, 7))))].sort((a, b) => a - b),
              events,
              upcoming_event: upcomingEvent,
            };
          }
          return {
            symbol,
            history_status: historyStatus,
            ...(rawEventYears == null ? {} : { requested_event_years: requestedYears }),
            months: [], events: [], upcoming_event: upcomingEvent,
          };
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
      const irbankDividendIndex = await readIrbankDividendIndex(env);
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
        const dividend = irbankDividendForCode(irbankDividendIndex, code);
        const dps = dividend.annual_dps;
        const symbol = yfinanceV2Symbol(code);
        const current = symbol
          ? await readV2Json(env, `${YFINANCE_V2_ROOT}/securities/${symbol}/current.json`)
          : null;
        const catalogYield = row.yield_source === 'yfinance_reported_dividendYield' ? row.yield_rate : null;
        const yieldRate = yieldFromDividend(
          currentPrice, dps, dividend.yield_rate, current?.statistics?.dividendYield, catalogYield,
        );
        return {
          code,
          name: String(row.name || code),
          sector: String(row.sector || '').trim() || null,
          current_price: currentPrice,
          acquisition_price: point.close,
          acquisition_date: point.date,
          annual_dividend_per_share: dps !== null && dps >= 0 ? dps : null,
          yield_rate: yieldRate,
          yield_source: yieldSourceFromDividend(
            yieldRate, dividend.yield_rate, dividend.yield_source,
            current?.statistics?.dividendYield, catalogYield,
          ),
          yield_as_of: dividend.yield_as_of,
          yield_attribution: dividend.yield_attribution,
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
        const irbankDividendIndex = await readIrbankDividendIndex(env);
        const catalogItems = catalog.items || [];
        const offset = Math.min(catalogItems.length, Number.parseInt(cursor || '0', 10));
        const requestedItems = requestedCodes
          ? requestedCodes.map(code => catalogItems.find(row => String(row.code || '').toUpperCase() === code)).filter(Boolean)
          : catalogItems.slice(offset, offset + limit);
        const items = await Promise.all(requestedItems.map(async row => {
          const code = String(row.code || '').toUpperCase();
          const issuerDividend = irbankDividendForCode(irbankDividendIndex, code);
          const irbankDividend = searchDividend(row, issuerDividend, null);
          const catalogYield = row.yield_source === 'yfinance_reported_dividendYield' ? row.yield_rate : null;
          const itemYield = irbankDividend.dividend_status === 'unknown' ? null : irbankDividend.dividend_status === 'no_dividend' ? 0 : yieldFromDividend(
            row.price, irbankDividend.annual_dps, irbankDividend.yield_rate, null, catalogYield,
          );
          const item = {
            code: row.code,
            price: row.price,
            quote_date: row.quote_date,
            annual_dividend_per_share: irbankDividend.annual_dps,
            trailing_12_month_distribution_per_share: row.trailing_12_month_distribution_per_share,
            dividend_period: irbankDividend.dividend_period,
            dividend_amount_basis: irbankDividend.dividend_amount_basis,
            yield_rate: itemYield,
            yield_source: yieldSourceFromDividend(
              itemYield, irbankDividend.yield_rate, irbankDividend.yield_source, null, catalogYield,
            ),
            yield_as_of: irbankDividend.yield_as_of,
            yield_attribution: irbankDividend.yield_attribution,
            yield_basis: irbankDividend.yield_basis,
            dividend_status: irbankDividend.dividend_status,
            dividend_fiscal_year: irbankDividend.fiscal_year,
            dividend_source: selectedDividendSource(irbankDividend),
          };
          if (!requestedCodes) return item;

          // The full catalog may lag the per-security current document. For a
          // portfolio refresh, read the fiscal forecast directly so a trailing
          // 12-month distribution total cannot be mistaken for annual DPS.
          const symbol = yfinanceV2Symbol(code);
          const current = symbol
            ? await readV2Json(env, `${YFINANCE_V2_ROOT}/securities/${symbol}/current.json`)
            : null;
          const selected = searchDividend(row, issuerDividend, current);
          const close = finiteNumber(current?.price?.close) ?? finiteNumber(row.price);
          const selectedYield = searchYield(close, selected);
          return {
            ...item,
            price: close ?? item.price,
            quote_date: current?.price?.date ?? item.quote_date,
            annual_dividend_per_share: selected.annual_dps,
            dividend_period: selected.dividend_period,
            dividend_amount_basis: selected.dividend_amount_basis,
            dividend_status: selected.dividend_status,
            dividend_fiscal_year: selected.fiscal_year,
            dividend_source: selectedDividendSource(selected),
            yield_basis: selected.yield_basis,
            yield_as_of: selected.yield_as_of,
            yield_rate: selectedYield,
            yield_source: selectedYield === null ? null : 'calculated_from_selected_dividend_and_quote',
            trailing_12_month_distribution_per_share: finiteNumber(current?.dividend?.trailing_12_month_per_share) ?? item.trailing_12_month_distribution_per_share,
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

    if (url.pathname === '/api/v1/security-categories') {
      const rawCodes = url.searchParams.get('codes');
      const requestedCodes = rawCodes === null ? null : [...new Set(rawCodes.split(',').map(yfinanceV2Code))];
      if (requestedCodes && (!requestedCodes.length || requestedCodes.includes(null) || requestedCodes.length > 200)) {
        return json({ error: 'invalid_codes' }, 400);
      }
      try {
        // The full universe includes securities without quotes or a search-catalog entry.
        const universe = await readV2Json(env, `${YFINANCE_V2_ROOT}/universe/current.json`);
        if (!Array.isArray(universe?.securities) || universe.securities.length === 0 || !universe.updated_at) {
          return json({ error: 'security_categories_unavailable' }, 503);
        }
        const categories = new Map();
        for (const row of universe.securities) {
          const code = yfinanceV2Code(row?.code);
          if (!code) continue;
          const category = row.jquants_product_category;
          // Never silently turn corrupt upstream data into a 45-day negative cache.
          if (category != null && !/^\d{3}$/.test(category)) {
            return json({ error: 'security_categories_invalid' }, 503);
          }
          categories.set(code, category == null ? null : String(category));
        }
        if (!categories.size) return json({ error: 'security_categories_unavailable' }, 503);
        const codes = requestedCodes || [...categories.keys()];
        const response = json({
          schema_version: 1,
          source: 'J-Quants API v2',
          updated_at: universe.updated_at,
          session_date: universe.session_date || null,
          categories: Object.fromEntries(codes.filter(code => categories.has(code)).map(code => [code, categories.get(code)])),
          not_found: codes.filter(code => !categories.has(code)),
        });
        response.headers.set('cache-control', 'public, max-age=300');
        return response;
      } catch {
        return json({ error: 'security_categories_unavailable' }, 503);
      }
    }

    if (url.pathname === '/api/v1/search-catalog') {
      const v2Catalog = await readV2Catalog(env);
      if (!v2Catalog) return json({ error: 'search_catalog_unavailable' }, 503);
      const irbankDividendIndex = await readIrbankDividendIndex(env);
      const response = json({
        ...v2Catalog,
        items: v2Catalog.items.map(({ income_advice, ...row }) => {
          const dividend = searchDividend(row, irbankDividendForCode(irbankDividendIndex, row.code), null);
          const catalogYield = row.yield_source === 'yfinance_reported_dividendYield' ? row.yield_rate : null;
          const yieldRate = dividend.dividend_status === 'unknown' ? null : dividend.dividend_status === 'no_dividend' ? 0 : yieldFromDividend(
            row.price, dividend.annual_dps, dividend.yield_rate, null, catalogYield,
          );
          return {
            ...row,
            annual_dividend_per_share: dividend.annual_dps,
            dividend_period: dividend.dividend_period,
            dividend_amount_basis: dividend.dividend_amount_basis,
            yield_rate: yieldRate,
            yield_source: yieldRate === null ? null : yieldSourceFromDividend(
              yieldRate,
              dividend.yield_rate, dividend.yield_source, null,
              row.yield_source === 'yfinance_reported_dividendYield' ? row.yield_rate : null,
            ),
            yield_as_of: dividend.yield_as_of,
            yield_attribution: dividend.yield_attribution,
            yield_basis: dividend.yield_basis,
            dividend_status: dividend.dividend_status,
            dividend_source: selectedDividendSource(dividend),
          };
        }),
      });
      response.headers.set('cache-control', 'public, max-age=300');
      return response;
    }

    if (url.pathname === '/api/v1/security-search') {
      const query = String(url.searchParams.get('q') || '').trim().slice(0, 100);
      if (!query) return json({ items: [] });
      const v2Catalog = await readV2Catalog(env);
      if (!v2Catalog) return json({ error: 'security_catalog_unavailable' }, 503);
      const irbankDividendIndex = await readIrbankDividendIndex(env);
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
        .slice(0, 30);
      const resultItems = await Promise.all(items.map(async row => {
          const issuer = irbankDividendForCode(irbankDividendIndex, row.code);
          const symbol = yfinanceV2Symbol(row.code);
          const current = issuer.forecast == null && row.security_type === 'equity' && symbol
            ? await readV2Json(env, `${YFINANCE_V2_ROOT}/securities/${symbol}/current.json`) : null;
          const dividend = searchDividend(row, issuer, current);
          const price = finiteNumber(current?.price?.close) ?? finiteNumber(row.price);
          const yieldRate = searchYield(price, dividend);
          return {
            code: row.code,
            name: row.name,
            price,
            annual_dividend_per_share: dividend.annual_dps,
            trailing_12_month_distribution_per_share: row.trailing_12_month_distribution_per_share,
            dividend_period: dividend.dividend_period,
            dividend_amount_basis: dividend.dividend_amount_basis,
            yield_rate: yieldRate,
            yield_source: yieldRate === null ? null : 'calculated_from_selected_dividend_and_quote',
            yield_as_of: dividend.yield_as_of,
            yield_attribution: dividend.yield_attribution,
            yield_basis: dividend.yield_basis,
            security_type: row.security_type,
            dividend_status: dividend.dividend_status,
            dividend_source: selectedDividendSource(dividend),
          };
        }));
      return json({ items: resultItems, generation: v2Catalog.generation || null });
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
      // Older clients retain the complete retrieval metadata. Current screens
      // can explicitly omit the unrendered, potentially unbounded history scan.
      const includeHistoryBounds = url.searchParams.get('include_history_bounds') !== 'false';
      const [profile, current, sync, dividendHistoryBounds, irbankDividendIndex] = await Promise.all([
        readV2Json(env, `${base}/profile.json`),
        readV2Json(env, `${base}/current.json`),
        readV2Json(env, `${base}/sync.json`),
        includeHistoryBounds ? readV2DividendHistoryBounds(env, symbol)
          : Promise.resolve({ status: 'not_requested', start: null, end: null }),
        readIrbankDividendIndex(env, code),
      ]);
      if (!profile || !current) return json({ error: 'security_data_unavailable' }, 503);
      const dividend = current.dividend || {};
      const actionHistory = current.action_history || {};
      const syncFieldStatus = sync?.field_status || {};
      const dividendHistoryStatus = current.field_status?.dividend_history
        || syncFieldStatus.dividend_history
        || null;
      const selectedDividend = searchDividend(item, irbankDividendForCode(irbankDividendIndex, code), current);
      const selectedDps = selectedDividend.annual_dps;
      const valuation = calculateDailyValuation(current);
      const reportedYield = current.statistics?.dividendYield;
      const marketYieldRate = selectedDividend.dividend_amount_basis === 'yfinance_forward_dividend_rate'
        ? searchYield(current.price?.close, selectedDividend)
        : selectedDividend.dividend_status === 'unknown' ? null : selectedDividend.dividend_status === 'no_dividend' ? 0 : yieldFromDividend(
        current.price?.close, selectedDps, selectedDividend.yield_rate, reportedYield,
      );
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
          fiscal_year: selectedDividend.fiscal_year,
          yield_basis: selectedDividend.yield_basis,
          dividend_period: selectedDividend.dividend_period,
          amount_basis: selectedDividend.dividend_amount_basis,
          source: selectedDividendSource(selectedDividend),
          field_status: {
            ...(current.field_status || {}),
            annual_dps: selectedDps !== null ? 'available' : 'empty',
            forecast_dividend: selectedDividend.forecast != null ? 'available' : 'empty',
          },
        },
        market_price: current.price || null,
        market_yield_rate: marketYieldRate,
        market_yield_source: marketYieldRate === null ? null : selectedDividend.dividend_amount_basis === 'yfinance_forward_dividend_rate'
          ? 'calculated_from_yfinance_dividend_and_quote' : yieldSourceFromDividend(
          marketYieldRate, selectedDividend.yield_rate, selectedDividend.yield_source, reportedYield,
        ),
        market_yield_as_of: selectedDividend.yield_as_of,
        market_yield_attribution: selectedDividend.yield_attribution,
        dividend_status: selectedDividend.dividend_status,
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

    if (url.pathname === '/api/v1/financial-data/revision') {
      if (request.method !== 'GET') return json({ error: 'method_not_allowed' }, 405);
      const revision = await readV2FinancialRevision(env);
      return revision ? json(revision) : json({ error: 'revision_unavailable' }, 503);
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
      if (familyFile === 'overview.json' && CONTAMINATED_ANNUAL_SEEDS[code]) {
        if (!env.MARKET_DATA) return json({ error: 'r2_unavailable' }, 503);
        const object = await env.MARKET_DATA.get(`market/irbank/v1/securities/${code}/${familyFile}`);
        if (!object) return json({ error: 'not_found' }, 404);
        return json(quarantineIrbankOverview(code, await object.json()));
      }
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
      const [v2Profile, v2Current, irbankDividendIndex] = await Promise.all([
        readV2Json(env, `${YFINANCE_V2_ROOT}/securities/${symbol}/profile.json`),
        readV2Json(env, `${YFINANCE_V2_ROOT}/securities/${symbol}/current.json`),
        readIrbankDividendIndex(env, code),
      ]);
      if (!v2Profile || !v2Current) return json({ error: 'not_found' }, 404);
      const includeMarket = url.searchParams.get('include_market') !== 'false';
      const stats = v2Current.statistics || {};
      const rawPayoutRatio = finiteNumber(stats.payoutRatio);
      const payoutRatio = ratioAsPercent(rawPayoutRatio, yfinanceStatisticsFieldUnit(v2Current, 'payoutRatio'));
      const valuation = calculateDailyValuation(v2Current);
      const selectedDividend = irbankDividendForCode(irbankDividendIndex, code);
      const marketYieldRate = yieldFromDividend(
        v2Current.price?.close, selectedDividend.annual_dps, selectedDividend.yield_rate, stats.dividendYield,
      );
      return json({
        code,
        canonical_code: `${code}0`,
        name: v2Profile.display_name || v2Profile.short_name || v2Profile.long_name || v2Profile.english_name || code,
        metrics: [],
        dividend: v2Current.dividend ? {
          fiscal_year: selectedDividend.fiscal_year,
          annual_dps: selectedDividend.annual_dps,
          annual_dps_adjusted: selectedDividend.annual_dps,
          yield_basis: selectedDividend.yield_basis,
          dividend_period: selectedDividend.dividend_period,
          amount_basis: selectedDividend.dividend_amount_basis,
          source: 'IRBANK API',
          payout_ratio: payoutRatio,
          field_units: { payout_ratio: financialUnitContract.api_units.ratio },
        } : null,
        market_yield_rate: yieldFromDividend(
          v2Current.price?.close, selectedDividend.annual_dps, selectedDividend.yield_rate,
          stats.dividendYield,
        ),
        market_yield_source: yieldSourceFromDividend(
          yieldFromDividend(
            v2Current.price?.close, selectedDividend.annual_dps, selectedDividend.yield_rate,
            stats.dividendYield,
          ),
          selectedDividend.yield_rate, selectedDividend.yield_source, stats.dividendYield,
        ),
        market_yield_as_of: selectedDividend.yield_as_of,
        market_yield_attribution: selectedDividend.yield_attribution,
        dividend_status: selectedDividend.dividend_status,
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
    ctx.waitUntil(retryFeedbackEmails(env));
    ctx.waitUntil(env.DB.prepare("DELETE FROM support_feedback WHERE created_at < strftime('%Y-%m-%dT%H:%M:%fZ', 'now', '-90 days')").run());
    ctx.waitUntil(runScheduledAnalysis(env));
  },

  async queue(batch) {
    // Retire any queued requests from the former live Yahoo quote collector.
    for (const message of batch.messages) message.ack();
  }
};
