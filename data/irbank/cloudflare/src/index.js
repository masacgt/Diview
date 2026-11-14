import { readFeedback, saveFeedback } from './feedback.mjs';
import { safelyDeliverFeedbackEmail, retryFeedbackEmails } from './feedback-email.mjs';
import financialUnitContract from './financial_unit_contract.json' with { type: 'json' };
import jpxReitMarketMaster from './jpx_reit_market_master.json' with { type: 'json' };
import { withMarketReadCache, sharedMarketJson, sharedMarketDocuments } from './market-read-cache.mjs';

// Synthetic 4,391-code replay measured at most 307 R2 calls per eight-security Cron batch; production skew and Cloudflare CPU remain unmeasured.
const ANALYSIS_BATCH_SIZE = 8;
const ANALYSIS_CONCURRENCY = 4;
const ANALYSIS_JOB_STATE_KEY = 'analysis/v1/state/scheduled-job.json';
// Cron invocations can remain alive for 15 minutes; keep the lease beyond that wall-time limit.
const ANALYSIS_JOB_LEASE_MS = 16 * 60 * 1000;
const ANALYSIS_CURRENT_PREFIX = 'analysis/v1/current/';
const ANALYSIS_HISTORY_PREFIX = 'analysis/v1/history/';
const ANALYSIS_SOURCE_KEY = 'analysis/v1/state/source.json';
const ANALYSIS_RUN_PREFIX = 'operations/v1/runs/analysis/';
const ANALYSIS_RETENTION_STATE_KEY = 'operations/v1/retention/analysis.json';
const ANALYSIS_RETENTION_INTERVAL_MS = 24 * 60 * 60 * 1000;
const ANALYSIS_RETENTION_BATCH_SIZE = 200;
const ANALYSIS_RETENTION_LEASE_MS = 16 * 60 * 1000;
const SCHEDULED_RUN_PREFIX = 'operations/v1/runs/';
const DEFINITIONS_PREFIX = 'definitions/v1/';
const DEFINITION_AUDIT_PREFIX = 'audit/v1/definitions/';

function json(data, status = 200) {
  return new Response(JSON.stringify(data), {
    status,
    headers: {
      'content-type': 'application/json; charset=utf-8',
      'cache-control': 'private, no-store',
      'access-control-allow-origin': '*',
      'access-control-allow-methods': 'GET, HEAD, POST, PUT, DELETE, OPTIONS',
      'access-control-allow-headers': 'Content-Type, Range, X-Admin-Secret',
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

function normalizeCode(code) {
  if (/^\d{4}$/.test(code)) return `${code}0`;
  return code;
}

function isoNow() {
  return new Date().toISOString();
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
  const key = await requestBucketKey(request, route, clientId);
  if (!env.RATE_LIMITER) throw new Error('RATE_LIMITER binding is missing');
  const id = env.RATE_LIMITER.idFromName(key);
  const response = await env.RATE_LIMITER.get(id).fetch('https://rate-limit.internal/counter', {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify({
      bucket_key: key,
      window_seconds: windowSeconds,
      limit: Number(limit),
      now_seconds: Math.floor(Date.now() / 1000),
    }),
  });
  if (!response.ok) throw new Error(`rate limiter returned HTTP ${response.status}`);
  const result = await response.json();
  return result.allowed === true;
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

async function readR2JsonWithEtag(env, key) {
  const object = await env.MARKET_DATA.get(key);
  if (!object) return null;
  const value = await object.json();
  const etag = object.etag;
  if (!etag) throw new Error(`R2 object has no ETag: ${key}`);
  return { value, etag };
}

function r2OnlyIfUnchanged(existing) {
  return existing ? { etagMatches: existing.etag } : { etagDoesNotMatch: '*' };
}

async function analysisSourceTag(env, stockDefs, watchDefs, config) {
  const [catalogObject, financialRevisionObject] = await Promise.all([
    env.MARKET_DATA.head(`${YFINANCE_V2_ROOT}/catalog/current.json`),
    env.MARKET_DATA.head(`${YFINANCE_V2_ROOT}/financial_revision.json`),
  ]);
  return JSON.stringify({
    analysis_contract: 3,
    catalog_etag: catalogObject?.httpEtag || catalogObject?.etag || null,
    financial_revision_etag: financialRevisionObject?.httpEtag || financialRevisionObject?.etag || null,
    stock_definition_sha256: await sha256Hex(JSON.stringify(stockDefs)),
    watch_definition_sha256: await sha256Hex(JSON.stringify(watchDefs)),
    market_stale_days: Number(config?.market_stale_days || 3),
  });
}

async function advanceAnalysisSource(env, sourceTag, previousSourceTag) {
  const [{ stockDefs, watchDefs }, config] = await Promise.all([
    loadAnalysisDefinitions(env),
    backendConfig(env),
  ]);
  if (await analysisSourceTag(env, stockDefs, watchDefs, config) !== sourceTag) return false;
  const current = await readR2JsonWithEtag(env, ANALYSIS_SOURCE_KEY);
  if (current?.value?.source_tag === sourceTag) return true;
  if ((current?.value?.source_tag ?? null) !== previousSourceTag) return false;
  const [{ stockDefs: latestStockDefs, watchDefs: latestWatchDefs }, latestConfig] = await Promise.all([
    loadAnalysisDefinitions(env),
    backendConfig(env),
  ]);
  if (await analysisSourceTag(env, latestStockDefs, latestWatchDefs, latestConfig) !== sourceTag) return false;
  const result = await env.MARKET_DATA.put(
    ANALYSIS_SOURCE_KEY,
    JSON.stringify({ source_tag: sourceTag, updated_at: isoNow() }),
    {
      onlyIf: r2OnlyIfUnchanged(current),
      httpMetadata: { contentType: 'application/json; charset=utf-8' },
    },
  );
  return result !== null;
}

async function auditDefinition(env, key, definition) {
  if (!definition || !env.MARKET_DATA) return;
  const canonical = JSON.stringify(definition);
  const hash = await sha256Hex(canonical);
  const version = Number(definition.version || 1);
  const objectKey = `${DEFINITION_AUDIT_PREFIX}${encodeURIComponent(key)}/${version}/${hash}.json`;
  await env.MARKET_DATA.put(objectKey, JSON.stringify({
    definition_key: key,
    version,
    content_hash: hash,
    metadata: { source: 'r2', observed_at: isoNow() },
  }), {
    onlyIf: { etagDoesNotMatch: '*' },
    httpMetadata: { contentType: 'application/json; charset=utf-8' },
  });
}

async function readDefinitionFromR2(env, key) {
  const keyRoot = `${DEFINITIONS_PREFIX}${encodeURIComponent(key)}/`;
  const pointer = await readV2Json(env, `${keyRoot}current.json`);
  if (pointer?.version === undefined || pointer?.version === null) return null;
  return readV2Json(env, `${keyRoot}${encodeURIComponent(String(pointer.version))}.json`);
}

class AnalysisDefinitionsUnavailableError extends Error {
  constructor() {
    super('Required R2 analysis definitions are unavailable');
    this.name = 'AnalysisDefinitionsUnavailableError';
  }
}

async function loadAnalysisDefinitions(env) {
  const [stockDefs, watchDefs] = await Promise.all([
    appDefinitionDocument(env, 'stock-analysis-definitions:v1'),
    appDefinitionDocument(env, 'watchlist-analysis-definitions:v1'),
  ]);
  if (stockDefs === null || watchDefs === null) throw new AnalysisDefinitionsUnavailableError();
  return { stockDefs, watchDefs };
}

async function appDefinitionItems(env, key) {
  return readDefinitionFromR2(env, key);
}

async function appDefinitionDocument(env, key) {
  return readDefinitionFromR2(env, key);
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
const IRBANK_SHARE_SPLITS_ROOT = 'market/irbank/v1/events/share-splits';

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

function tokyoDate(timestamp) {
  return new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Tokyo', year: 'numeric', month: '2-digit', day: '2-digit',
  }).format(new Date(timestamp * 1000));
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

async function readIrbankDividendIndexesForCodes(env, codes) {
  const manifest = await readV2Json(env, IRBANK_V2_DIVIDEND_SHARD_MANIFEST_KEY);
  if (manifest?.schema_version !== 1 || manifest.prefix_length !== DIVIDEND_SHARD_PREFIX_LENGTH
    || !['a', 'b'].includes(manifest.slot)
    || !Array.isArray(manifest.prefixes)) {
    return new Map([['*', await readIrbankDividendIndex(env)]]);
  }

  const prefixes = [...new Set(codes.map(yfinanceV2Code).filter(Boolean)
    .map(code => code.slice(0, DIVIDEND_SHARD_PREFIX_LENGTH)))];
  const listedPrefixes = new Set(manifest.prefixes);
  const loaded = await Promise.all(prefixes.filter(prefix => listedPrefixes.has(prefix)).map(async prefix => {
    const shard = await readV2Json(env, `${IRBANK_V2_DIVIDEND_SHARDS_ROOT}/${manifest.slot}/${prefix}.json`);
    if (!shard || !shard.items || typeof shard.items !== 'object') return [prefix, null];
    return [prefix, { source: manifest.source, attribution: manifest.attribution, items: shard.items }];
  }));

  // A manifest that points to a missing shard is incomplete. Keep the older
  // full index as a compatibility fallback instead of treating rows as absent.
  if (loaded.some(([, index]) => index === null)) {
    return new Map([['*', await readIrbankDividendIndex(env)]]);
  }

  const indexes = new Map(loaded);
  for (const prefix of prefixes) {
    if (!indexes.has(prefix)) {
      indexes.set(prefix, { source: manifest.source, attribution: manifest.attribution, items: {} });
    }
  }
  return indexes;
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
function japanDate() {
  const parts = Object.fromEntries(new Intl.DateTimeFormat('en', {
    timeZone: 'Asia/Tokyo', year: 'numeric', month: '2-digit', day: '2-digit',
  }).formatToParts(new Date()).map(part => [part.type, part.value]));
  return `${parts.year}-${parts.month}-${parts.day}`;
}

function yahooForwardEligible(row) {
  if (row.security_type === 'equity') return true;
  if (row.security_type !== 'reit') return false;
  const listed = jpxReitMarketMaster[yfinanceV2Code(row.code)];
  return typeof listed === 'string' && listed <= japanDate();
}

// A per-security statistics read must not replace a newer published catalog price.
function reitForwardQuote(row, current) {
  const catalogCandidate = finiteNumber(row?.price);
  const currentCandidate = finiteNumber(current?.price?.close);
  const catalogPrice = catalogCandidate > 0 ? catalogCandidate : null;
  const currentPrice = currentCandidate > 0 ? currentCandidate : null;
  const catalogDate = /^\d{4}-\d{2}-\d{2}$/.test(String(row?.quote_date || '')) ? row.quote_date : null;
  const currentDate = /^\d{4}-\d{2}-\d{2}$/.test(String(current?.price?.date || '')) ? current.price.date : null;
  if (catalogPrice !== null && catalogDate && (!currentDate || catalogDate > currentDate)) {
    return { price: catalogPrice, date: catalogDate };
  }
  return { price: currentPrice ?? catalogPrice, date: currentPrice !== null ? currentDate : catalogDate };
}

function searchDividend(row, issuer, current) {
  if (issuer.forecast != null) return issuer;
  const fetchedAt = Date.parse(String(current?.statistics_last_fetched_at || ''));
  const age = Date.now() - fetchedAt;
  // Android's J-REIT fallback uses whole Japan calendar dates, including day 45.
  let recent = Number.isFinite(fetchedAt) && age >= 0 && age <= 45 * 86400000;
  if (row.security_type === 'reit' && Number.isFinite(fetchedAt)) {
    const fetchedDate = String(current?.statistics_last_fetched_at || '').slice(0, 10);
    const calendarAge = (Date.parse(`${japanDate()}T00:00:00Z`) - Date.parse(`${fetchedDate}T00:00:00Z`)) / 86400000;
    recent = Number.isInteger(calendarAge) && calendarAge >= 0 && calendarAge <= 45;
  }
  const forward = finiteNumber(current?.statistics?.dividendRate);
  const reitNoDividend = row.security_type === 'reit' && (current?.dividend?.dividend_status ?? row?.dividend_status) === 'no_dividend';
  if (yahooForwardEligible(row) && !reitNoDividend && recent
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

function yfinanceCalendarYearDividends(events, amountBasis = 'yfinance_split_adjusted_assumption') {
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
      amount_basis: amountBasis,
    }));
  return {
    records,
    status: records.length ? 'available' : 'empty',
    amount_basis: amountBasis,
  };
}

function calendarYearDividendTrend(records) {
  const observed = (Array.isArray(records) ? records : [])
    .map(row => ({
      year: finiteNumber(row?.calendar_year),
      amount: finiteNumber(row?.calendar_year_distribution_per_share),
    }))
    .filter(row => Number.isInteger(row.year) && row.amount !== null && row.amount >= 0)
    .sort((a, b) => a.year - b.year);
  const latest = observed.at(-1);
  const prior = observed.at(-2);
  const currentYear = Number(new Intl.DateTimeFormat('en', {
    timeZone: 'Asia/Tokyo', year: 'numeric'
  }).format(new Date()));
  if (!latest || !prior || latest.year !== currentYear - 1 || prior.year !== latest.year - 1) {
    return { latest_completed_year: latest?.year ?? null, consecutive_increase_years: null, no_cut_years: null,
      status: 'insufficient_history' };
  }
  let increases = 0;
  let noCuts = 0;
  for (let index = observed.length - 1; index > 0; index -= 1) {
    const current = observed[index];
    const previous = observed[index - 1];
    if (previous.year !== current.year - 1 || current.amount <= previous.amount) break;
    increases += 1;
  }
  for (let index = observed.length - 1; index > 0; index -= 1) {
    const current = observed[index];
    const previous = observed[index - 1];
    if (previous.year !== current.year - 1 || current.amount < previous.amount) break;
    noCuts += 1;
  }
  return { latest_completed_year: latest.year, consecutive_increase_years: increases, no_cut_years: noCuts,
    status: 'available' };
}

async function readIssuerShareActions(env, code) {
  const normalized = yfinanceV2Code(code);
  return normalized
    ? readV2Json(env, `${IRBANK_SHARE_SPLITS_ROOT}/${normalized}.json`)
    : null;
}

async function readNormalizedDividendBasis(env, symbol) {
  const normalized = yfinanceV2Symbol(symbol);
  return normalized
    ? readV2Json(env, `${YFINANCE_V2_ROOT}/securities/${normalized}/dividend_basis/current.json`)
    : null;
}

function isFreshDividendBasis(basis, current) {
  if (!basis || !Array.isArray(basis.records)) return false;
  const basisTime = Date.parse(String(basis.generated_at || ''));
  const currentTime = Date.parse(String(current?.updated_at || ''));
  return Number.isFinite(basisTime) && (!Number.isFinite(currentTime) || basisTime >= currentTime);
}

function isCurrentDividendBasis(basis, current) {
  return basis?.status === 'available' && isFreshDividendBasis(basis, current);
}

function currentShareBasisDps(record, issuerFinancialPeriods, actionDocument, asOfDate) {
  const sourceDps = finiteNumber(record?.annual_dps);
  if (sourceDps === null || actionDocument?.status !== 'available'
    || (actionDocument?.errors || []).length > 0
    || (actionDocument?.coverage?.conflicting_dates || []).length > 0) return null;
  const fiscalYear = String(record?.fiscal_year ?? '').slice(0, 4);
  const periods = (Array.isArray(issuerFinancialPeriods) ? issuerFinancialPeriods : []).filter(row =>
    String(row?.fiscal_year || '').slice(0, 4) === fiscalYear
      && /^\d{4}-\d{2}-\d{2}$/.test(String(row?.period_end || '')));
  if (periods.length !== 1) return null;
  const periodEnd = String(periods[0].period_end);
  const coverageFrom = String(actionDocument?.coverage?.disclosures_from || '');
  const coverageTo = String(actionDocument?.coverage?.disclosures_to || '');
  if (!/^\d{4}-\d{2}-\d{2}$/.test(coverageFrom) || coverageFrom > periodEnd
    || !/^\d{4}-\d{2}-\d{2}$/.test(coverageTo) || coverageTo < asOfDate) return null;
  let factor = 1;
  for (const action of actionDocument.events || []) {
    const actionDate = String(action?.effective_date || '');
    const ratio = finiteNumber(action?.ratio);
    if (actionDate > periodEnd && actionDate <= asOfDate && ratio !== null && ratio > 0) factor *= ratio;
  }
  if (!Number.isFinite(factor) || factor <= 0) return null;
  return {
    value: sourceDps / factor,
    source_dps: sourceDps,
    share_adjustment_factor: factor,
    period_end: periodEnd,
    as_of: asOfDate,
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
  const shareActions = await readIssuerShareActions(env, code);
  const normalizedBasis = await readNormalizedDividendBasis(env, `${code}.T`);
  const normalizedBasisFresh = isFreshDividendBasis(normalizedBasis, current);
  const normalizedBasisCurrent = isCurrentDividendBasis(normalizedBasis, current);
  const effectiveDividendHistory = normalizedBasisCurrent
    ? yfinanceCalendarYearDividends(normalizedBasis.records, normalizedBasis.amount_basis)
    : normalizedBasisFresh
      ? { records: [], status: normalizedBasis.status || 'review_required', amount_basis: 'unverified' }
      : dividendHistory;
  const calendarYearMetrics = current?.dividend?.calendar_year_metrics || null;
  const effectiveDividendMetrics = normalizedBasisFresh
    ? calendarYearDividendTrend(effectiveDividendHistory.records)
    : calendarYearMetrics;
  const dividendRows = normalizedBasisFresh
    ? effectiveDividendHistory.records
    : Array.isArray(calendarYearMetrics?.annual_records)
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
  const issuerFinancialPeriods = annual.length ? annual : irbankOverview?.financials?.annual || [];
  const asOfDate = new Date().toISOString().slice(0, 10);
  const issuerDividendRecords = [...issuerDividendRecordsByPeriod.entries()]
    .sort(([a], [b]) => a.localeCompare(b))
    .map(([, record]) => {
      const adjusted = currentShareBasisDps(record, issuerFinancialPeriods, shareActions, asOfDate);
      return ({
      ...record,
      ...(adjusted ? {
        annual_dps_current_share_basis: adjusted.value,
        share_adjustment_factor: adjusted.share_adjustment_factor,
        share_basis_period_end: adjusted.period_end,
        share_basis_as_of: adjusted.as_of,
        share_basis_status: 'verified_from_issuer_actions',
      } : {}),
      field_units: {
        ...(record.field_units && typeof record.field_units === 'object' ? record.field_units : {}),
        ...(finiteNumber(record.annual_dps) !== null ? { annual_dps: financialUnitContract.api_units.per_share } : {}),
        ...(finiteNumber(record.annual_dps_adjusted) !== null ? { annual_dps_adjusted: financialUnitContract.api_units.per_share } : {}),
        ...(adjusted ? { annual_dps_current_share_basis: financialUnitContract.api_units.per_share } : {}),
        ...(finiteNumber(record.payout_ratio) !== null ? { payout_ratio: financialUnitContract.api_units.ratio } : {}),
      },
      });
    });
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
  const shareBasisStatus = shareActions?.status === 'available' && (shareActions.events || []).length > 0
    && !(shareActions.errors || []).length
    ? 'verified_from_issuer_actions'
    : shareActions?.status === 'review_required' || (shareActions?.errors || []).length
      ? 'unverified_incomplete_corporate_action_history'
      : null;
  const dividendHistoryStatus = current?.field_status?.dividend_history;
  const rawPayoutRatio = finiteNumber(current?.statistics?.payoutRatio);
  const rawPayoutUnit = yfinanceStatisticsFieldUnit(current, 'payoutRatio');
  const payoutRatio = ratioAsPercent(rawPayoutRatio, rawPayoutUnit);
  return {
    financial_contract_version: 7,
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
      amount_basis: effectiveDividendHistory.amount_basis,
      consecutive_dividend_increase_years: finiteNumber(effectiveDividendMetrics?.consecutive_increase_years),
      no_cut_years: finiteNumber(effectiveDividendMetrics?.no_cut_years),
      metrics_status: effectiveDividendMetrics?.status || 'not_fetched',
      metrics_as_of_year: finiteNumber(effectiveDividendMetrics?.latest_completed_year),
      source: 'Yahoo Finance via yfinance',
      current: current?.dividend || null,
      ...(shareBasisStatus ? { share_basis_status: shareBasisStatus } : {}),
      payout_ratio: payoutRatio,
      field_units: { payout_ratio: financialUnitContract.api_units.ratio },
      field_status: {
        ...(current?.field_status || {}),
        dividend_history: normalizedBasisFresh ? effectiveDividendHistory.status : dividendHistoryStatus || dividendHistory.status,
        consecutive_dividend_increase_years: effectiveDividendMetrics?.status || 'not_fetched',
        no_cut_years: effectiveDividendMetrics?.status || 'not_fetched',
        payout_ratio: payoutRatio != null ? 'available' : 'empty',
      },
    },
    issuer_dividends: {
      records: issuerDividendRecords,
      edinet_code: irbankDividendItem?.edinet_code || null,
      latest_actual: latestIssuerActual || issuerDividendRecords.at(-1) || null,
      forecast: issuerForecast,
      history_basis: 'issuer_reported_fiscal_year',
      amount_basis: 'irbank_reported_and_current_share_basis_when_verified',
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

const LIVE_QUOTE_RUN_PREFIX = 'market/yfinance/live_quote_runs/';

function liveQuoteRunObjectKey(runId, code) {
  return `${LIVE_QUOTE_RUN_PREFIX}${runId}/${code}.json`;
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
  const definitions = preloaded?.stockDefs != null && preloaded?.watchDefs != null
    ? { stockDefs: preloaded.stockDefs, watchDefs: preloaded.watchDefs }
    : await loadAnalysisDefinitions(env);
  const { stockDefs, watchDefs } = definitions;
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
  if (!env.MARKET_DATA) throw new Error('MARKET_DATA binding is missing');
  const currentKey = `${ANALYSIS_CURRENT_PREFIX}${security.security_code}.json`;
  const payload = JSON.stringify(snapshot);
  const comparable = value => {
    const copy = JSON.parse(JSON.stringify(value));
    delete copy.observed_at;
    if (copy.freshness) delete copy.freshness.market_generated_at;
    return JSON.stringify(copy);
  };
  const incomingAt = Date.parse(snapshot.observed_at);
  if (!Number.isFinite(incomingAt)) throw new Error(`Invalid analysis observed_at for ${security.security_code}`);

  for (let attempt = 0; attempt < 3; attempt += 1) {
    const current = await readR2JsonWithEtag(env, currentKey);
    const previous = current?.value || null;
    if (previous) {
      const previousAt = Date.parse(previous.observed_at);
      if (!Number.isFinite(previousAt)) throw new Error(`Invalid current analysis observed_at for ${security.security_code}`);
      if (previousAt > incomingAt) return;
      if (previousAt === incomingAt) {
        if (comparable(previous) === comparable(snapshot)) return;
        throw new Error(`Conflicting analysis snapshots share observed_at: ${security.security_code}`);
      }
    }

    const historyChanged = !previous || comparable(previous) !== comparable(snapshot);
    if (historyChanged) {
      const observedAt = new Date(snapshot.observed_at);
      const yyyy = String(observedAt.getUTCFullYear());
      const mm = String(observedAt.getUTCMonth() + 1).padStart(2, '0');
      const stamp = observedAt.toISOString().slice(0, 10).replaceAll('-', '');
      const transitionHash = await sha256Hex(`${previous?.observed_at || 'initial'}\n${comparable(snapshot)}`);
      const historyKey = `${ANALYSIS_HISTORY_PREFIX}${security.security_code}/${yyyy}/${mm}/${stamp}-${transitionHash.slice(0, 16)}.json`;
      const historyBody = JSON.stringify({
        observed_at: snapshot.observed_at,
        market_date: snapshot.market_date,
        definition_version: snapshot.definition_version,
        overall_score: snapshot.analysis.overall_score,
        overall_grade: snapshot.analysis.overall_grade,
        buy_zone_status: snapshot.buy_zone?.status ?? null,
        buy_zone_ratio: snapshot.buy_zone_ratio,
        anomaly_flags_json: JSON.stringify(snapshot.anomalies),
        metrics_json: JSON.stringify(snapshot.values),
        analysis_json: payload,
      });
      const written = await env.MARKET_DATA.put(historyKey, historyBody, {
        onlyIf: { etagDoesNotMatch: '*' },
        httpMetadata: { contentType: 'application/json; charset=utf-8' },
      });
      if (written === null) {
        const existingHistory = await readR2JsonWithEtag(env, historyKey);
        let existingSnapshot = null;
        try {
          existingSnapshot = typeof existingHistory?.value?.analysis_json === 'string'
            ? JSON.parse(existingHistory.value.analysis_json)
            : existingHistory?.value?.analysis_json;
        } catch {
          // A malformed object at a deterministic immutable key is a conflict.
        }
        if (!existingSnapshot || comparable(existingSnapshot) !== comparable(snapshot)) {
          throw new Error(`Conflicting analysis history object: ${historyKey}`);
        }
      }
    }

    const written = await env.MARKET_DATA.put(currentKey, payload, {
      onlyIf: r2OnlyIfUnchanged(current),
      httpMetadata: { contentType: 'application/json; charset=utf-8' },
    });
    if (written !== null) return;
  }
  throw new Error(`R2 current analysis changed concurrently: ${currentKey}`);
}

async function compactAnalysisHistory(env, nowMs = Date.now(), cursor = null, previousCandidate = null) {
  if (!env.MARKET_DATA) throw new Error('MARKET_DATA binding is missing');
  const dailyCutoff = nowMs - 30 * 86400000;
  const monthlyCutoff = nowMs - 365 * 86400000;
  const deleteKeys = [];
  let candidate = previousCandidate || null;
  const page = await env.MARKET_DATA.list({
    prefix: ANALYSIS_HISTORY_PREFIX,
    limit: ANALYSIS_RETENTION_BATCH_SIZE,
    cursor: cursor || undefined,
  });

  for (const object of page.objects || []) {
    const match = object.key.match(/^analysis\/v1\/history\/([A-Z0-9]{4,5})\/(\d{4})\/(\d{2})\/(\d{8})-[^/]+\.json$/);
    if (!match) continue;
    const keyDate = match[2] + '-' + match[3] + '-' + match[4].slice(6, 8);
    const keyDateMs = Date.parse(keyDate + 'T00:00:00.000Z');
    if (!Number.isFinite(keyDateMs) || new Date(keyDateMs).toISOString().slice(0, 10) !== keyDate || keyDateMs > dailyCutoff) continue;

    const history = await readV2Json(env, object.key);
    const observedMs = Date.parse(history?.observed_at || '');
    if (!Number.isFinite(observedMs) || observedMs >= dailyCutoff) continue;
    const observedAt = new Date(observedMs).toISOString();
    const date = observedAt.slice(0, 10);
    const month = observedAt.slice(0, 7);
    const period = observedMs < monthlyCutoff ? match[1] + '/' + month : match[1] + '/' + date;
    const uploadedAt = object.uploaded?.getTime?.() || 0;
    const current = { key: object.key, period, observed_ms: observedMs, uploaded_at_ms: uploadedAt };

    if (candidate?.period === period) {
      const isLater = observedMs > candidate.observed_ms
        || (observedMs === candidate.observed_ms && (uploadedAt > candidate.uploaded_at_ms
          || (uploadedAt === candidate.uploaded_at_ms && object.key.localeCompare(candidate.key) > 0)));
      if (isLater) {
        deleteKeys.push(candidate.key);
        candidate = current;
      } else {
        deleteKeys.push(object.key);
      }
    } else {
      candidate = current;
    }
  }

  if (deleteKeys.length > 0) await env.MARKET_DATA.delete(deleteKeys);
  return {
    deleted: deleteKeys.length,
    done: !page.truncated,
    cursor: page.truncated ? page.cursor || null : null,
    candidate: page.truncated ? candidate : null,
  };
}

function runStartedAtFromKey(key) {
  const match = key.match(/^operations\/v1\/runs\/[^/]+\/([^/]+)\/summary\.json$/);
  if (!match || match[1].startsWith('legacy-d1-')) return null;
  const timestamp = match[1].match(/^(\d{4})(\d{2})(\d{2})T(\d{2})(\d{2})(\d{2})(\d{3})Z-/);
  if (!timestamp) return null;
  return Date.UTC(Number(timestamp[1]), Number(timestamp[2]) - 1, Number(timestamp[3]),
    Number(timestamp[4]), Number(timestamp[5]), Number(timestamp[6]), Number(timestamp[7]));
}

async function compactScheduledRunLogs(env, nowMs = Date.now(), cursor = null) {
  if (!env.MARKET_DATA) throw new Error('MARKET_DATA binding is missing');
  const cutoff = nowMs - 365 * 86400000;
  const deleteKeys = [];
  const page = await env.MARKET_DATA.list({
    prefix: SCHEDULED_RUN_PREFIX,
    limit: ANALYSIS_RETENTION_BATCH_SIZE,
    cursor: cursor || undefined,
  });
  const terminalStatuses = new Set(['success', 'failed', 'partial', 'superseded']);

  for (const object of page.objects || []) {
    if (!object.key.endsWith('/summary.json')) continue;
    let startedAt = runStartedAtFromKey(object.key);
    let summary = null;
    if (startedAt === null && /\/legacy-d1-\d+\/summary\.json$/.test(object.key)) {
      summary = await readV2Json(env, object.key);
      const parsed = Date.parse(summary?.started_at || '');
      if (Number.isFinite(parsed)) startedAt = parsed;
    }
    if (startedAt === null || startedAt >= cutoff) continue;
    if (!summary) summary = await readV2Json(env, object.key);
    if (!summary?.finished_at || !terminalStatuses.has(String(summary.status || '').toLowerCase())) continue;
    deleteKeys.push(object.key);
  }

  if (deleteKeys.length > 0) await env.MARKET_DATA.delete(deleteKeys);
  return {
    deleted: deleteKeys.length,
    done: !page.truncated,
    cursor: page.truncated ? page.cursor || null : null,
  };
}

async function compactAnalysisRetentionIfDue(env, nowMs = Date.now()) {
  if (!env.MARKET_DATA) throw new Error('MARKET_DATA binding is missing');
  const state = await readR2JsonWithEtag(env, ANALYSIS_RETENTION_STATE_KEY);
  const value = state?.value || {};
  const completedAt = Date.parse(value.completed_at || '');
  const leaseUntil = Date.parse(value.lease_until || '');
  if (Number.isFinite(leaseUntil) && leaseUntil > nowMs) return { skipped: 'locked' };

  const inProgress = value.phase === 'history' || value.phase === 'runs';
  if (!inProgress && Number.isFinite(completedAt) && nowMs - completedAt < ANALYSIS_RETENTION_INTERVAL_MS) {
    return { skipped: 'not_due' };
  }
  const base = inProgress ? value : {
    completed_at: value.completed_at || null,
    phase: 'history',
    history_cursor: null,
    history_candidate: null,
    run_cursor: null,
    history_deleted: 0,
    run_logs_deleted: 0,
  };
  const leaseId = crypto.randomUUID();
  const claimed = await env.MARKET_DATA.put(ANALYSIS_RETENTION_STATE_KEY, JSON.stringify({
    ...base,
    lease_id: leaseId,
    lease_until: new Date(nowMs + ANALYSIS_RETENTION_LEASE_MS).toISOString(),
  }), {
    onlyIf: r2OnlyIfUnchanged(state),
    httpMetadata: { contentType: 'application/json; charset=utf-8' },
  });
  if (!claimed?.etag) return { skipped: 'claim_conflict' };

  try {
    let next = { ...base };
    if (base.phase === 'history') {
      const page = await compactAnalysisHistory(env, nowMs, base.history_cursor, base.history_candidate);
      next.history_deleted = Number(base.history_deleted || 0) + page.deleted;
      next.history_cursor = page.done ? null : page.cursor;
      next.history_candidate = page.done ? null : page.candidate;
      next.phase = page.done ? 'runs' : 'history';
    } else {
      const page = await compactScheduledRunLogs(env, nowMs, base.run_cursor);
      next.run_logs_deleted = Number(base.run_logs_deleted || 0) + page.deleted;
      next.run_cursor = page.done ? null : page.cursor;
      next.phase = page.done ? 'complete' : 'runs';
    }

    if (next.phase === 'complete') next.completed_at = new Date(nowMs).toISOString();
    next.lease_id = null;
    next.lease_until = null;
    next.last_error = null;
    const written = await env.MARKET_DATA.put(ANALYSIS_RETENTION_STATE_KEY, JSON.stringify(next), {
      onlyIf: { etagMatches: claimed.etag },
      httpMetadata: { contentType: 'application/json; charset=utf-8' },
    });
    if (!written?.etag) throw new Error('R2 analysis-retention state changed during compaction');
    return {
      phase: next.phase,
      done: next.phase === 'complete',
      history_deleted: next.history_deleted,
      run_logs_deleted: next.run_logs_deleted,
    };
  } catch (error) {
    await env.MARKET_DATA.put(ANALYSIS_RETENTION_STATE_KEY, JSON.stringify({
      ...base,
      last_error_at: new Date(nowMs).toISOString(),
      last_error: String(error?.message || error).slice(0, 200),
      lease_id: null,
      lease_until: null,
    }), {
      onlyIf: { etagMatches: claimed.etag },
      httpMetadata: { contentType: 'application/json; charset=utf-8' },
    }).catch(() => {});
    throw error;
  }
}

async function updateCurrentAnalysisRun(env, runKey, runId, startedAt) {
  const pointerKey = `${ANALYSIS_RUN_PREFIX}current.json`;
  for (let attempt = 0; attempt < 3; attempt += 1) {
    const current = await readR2JsonWithEtag(env, pointerKey);
    if (String(current?.value?.run_id || '') >= runId) return;
    const written = await env.MARKET_DATA.put(pointerKey, JSON.stringify({
      run_key: runKey,
      run_id: runId,
      started_at: startedAt,
    }), {
      onlyIf: r2OnlyIfUnchanged(current),
      httpMetadata: { contentType: 'application/json; charset=utf-8' },
    });
    if (written !== null) return;
  }
  throw new Error('R2 current analysis run pointer changed concurrently');
}

async function updateAnalysisRunSummary(env, runKey, summary, etag) {
  const written = await env.MARKET_DATA.put(runKey, JSON.stringify(summary), {
    onlyIf: { etagMatches: etag },
    httpMetadata: { contentType: 'application/json; charset=utf-8' },
  });
  if (!written?.etag) throw new Error(`R2 analysis run summary changed concurrently: ${runKey}`);
  return written.etag;
}

async function runScheduledAnalysis(env) {
  if (!env.MARKET_DATA) throw new Error('MARKET_DATA binding is missing');
  const runStartedMs = Date.now();
  const startedAt = isoNow();
  const [{ stockDefs, watchDefs }, config] = await Promise.all([
    loadAnalysisDefinitions(env),
    backendConfig(env),
  ]);
  const [latestState, catalog] = await Promise.all([
    readV2Json(env, ANALYSIS_SOURCE_KEY),
    readV2Catalog(env),
  ]);
  if (!Array.isArray(catalog?.items) || catalog.items.length === 0) {
    throw new Error('R2 v2 security catalog is missing or empty');
  }
  const securities = catalog.items.map(row => {
    const displayCode = yfinanceV2Code(row?.code || row?.security_code_display || row?.symbol);
    if (!displayCode) return null;
    return {
      security_code: displayCode.length === 4 ? displayCode + '0' : displayCode,
      security_code_display: displayCode,
      name: String(row?.name || row?.display_name || displayCode),
    };
  }).filter(Boolean);
  if (securities.length === 0) throw new Error('R2 v2 security catalog has no valid security codes');

  const sourceTag = await analysisSourceTag(env, stockDefs, watchDefs, config);
  const previousSourceTag = latestState?.source_tag ?? null;
  const sourceChanged = previousSourceTag !== sourceTag;
  const generation = String(catalog.generation || 'unknown');
  const total = securities.length;
  const jobRead = await readR2JsonWithEtag(env, ANALYSIS_JOB_STATE_KEY);
  const existingJob = jobRead?.value;
  const resumableStatuses = new Set(['running', 'retrying', 'finalizing']);
  const jobMatches = Boolean(existingJob
    && existingJob.source_tag === sourceTag
    && existingJob.catalog_generation === generation
    && Number(existingJob.total) === total
    && resumableStatuses.has(existingJob.status));

  if (!sourceChanged && !(jobMatches && existingJob.status === 'finalizing')) {
    return {
      processed: 0,
      failed: 0,
      source_changed: false,
      source_tag_advanced: false,
      skipped: 'source_unchanged',
      observed_at: isoNow(),
    };
  }

  const nowMs = Date.now();
  if (existingJob && resumableStatuses.has(existingJob.status)) {
    const leaseUntil = Date.parse(existingJob.lease_until || '');
    if (Number.isFinite(leaseUntil) && leaseUntil > nowMs) {
      return { processed: 0, failed: 0, source_changed: sourceChanged, source_tag_advanced: false, skipped: 'lease_active' };
    }
  }
  if (jobMatches) {
    const retryAt = Date.parse(existingJob.retry_at || '');
    if (existingJob.status === 'retrying' && Number.isFinite(retryAt) && retryAt > nowMs) {
      return {
        processed: 0,
        failed: (existingJob.failed_codes || []).length,
        source_changed: sourceChanged,
        source_tag_advanced: false,
        skipped: 'retry_backoff',
        retry_at: existingJob.retry_at,
      };
    }
  }

  const job = jobMatches ? { ...existingJob } : (() => {
    const runId = startedAt.replaceAll('-', '').replaceAll(':', '').replace('.', '') + '-' + crypto.randomUUID();
    return {
      source_tag: sourceTag,
      previous_source_tag: previousSourceTag,
      catalog_generation: generation,
      total,
      next_index: 0,
      run_id: runId,
      run_key: ANALYSIS_RUN_PREFIX + runId + '/summary.json',
      started_at: startedAt,
      status: 'running',
      attempts: 0,
      retry_at: null,
      failed_codes: [],
    };
  })();
  const leaseId = crypto.randomUUID();
  const claimedJob = {
    ...job,
    status: job.status === 'finalizing' ? 'finalizing' : 'running',
    lease_id: leaseId,
    lease_until: new Date(nowMs + ANALYSIS_JOB_LEASE_MS).toISOString(),
  };
  const claimed = await env.MARKET_DATA.put(ANALYSIS_JOB_STATE_KEY, JSON.stringify(claimedJob), {
    onlyIf: r2OnlyIfUnchanged(jobRead),
    httpMetadata: { contentType: 'application/json; charset=utf-8' },
  });
  if (!claimed?.etag) {
    return { processed: 0, failed: 0, source_changed: sourceChanged, source_tag_advanced: false, skipped: 'claim_conflict' };
  }
  let jobEtag = claimed.etag;

  if (existingJob && resumableStatuses.has(existingJob.status) && !jobMatches) {
    const staleSummary = await readR2JsonWithEtag(env, existingJob.run_key);
    if (staleSummary?.etag) {
      await updateAnalysisRunSummary(env, existingJob.run_key, {
        ...staleSummary.value,
        finished_at: isoNow(),
        status: 'partial',
        metadata: {
          ...(staleSummary.value.metadata || {}),
          superseded_by_source_tag: sourceTag,
          superseded_at: isoNow(),
        },
      }, staleSummary.etag);
    }
  }

  if (!jobMatches) {
    await Promise.all([
      auditDefinition(env, 'stock-analysis-definitions:v1', stockDefs),
      auditDefinition(env, 'watchlist-analysis-definitions:v1', watchDefs),
    ]);
  }

  const saveJob = async nextJob => {
    const written = await env.MARKET_DATA.put(ANALYSIS_JOB_STATE_KEY, JSON.stringify(nextJob), {
      onlyIf: { etagMatches: jobEtag },
      httpMetadata: { contentType: 'application/json; charset=utf-8' },
    });
    if (!written?.etag) throw new Error('R2 scheduled-analysis job state changed concurrently');
    jobEtag = written.etag;
  };

  let runSummaryRead = await readR2JsonWithEtag(env, job.run_key);
  if (!runSummaryRead) {
    const initialSummary = {
      run_id: job.run_id,
      run_type: 'analysis',
      started_at: job.started_at,
      finished_at: null,
      status: 'running',
      processed: Number(job.next_index || 0),
      failed: (job.failed_codes || []).length,
      metadata: {
        source_changed: true,
        catalog_generation: generation,
        total,
        batch_size: ANALYSIS_BATCH_SIZE,
        next_index: Number(job.next_index || 0),
      },
    };
    const created = await env.MARKET_DATA.put(job.run_key, JSON.stringify(initialSummary), {
      onlyIf: { etagDoesNotMatch: '*' },
      httpMetadata: { contentType: 'application/json; charset=utf-8' },
    });
    runSummaryRead = created?.etag ? { value: initialSummary, etag: created.etag } : await readR2JsonWithEtag(env, job.run_key);
  }
  if (!runSummaryRead?.etag) throw new Error('R2 scheduled-analysis run summary is unavailable');
  await updateCurrentAnalysisRun(env, job.run_key, job.run_id, job.started_at);
  let runSummary = { ...runSummaryRead.value };
  let runSummaryEtag = runSummaryRead.etag;

  const persistRunSummary = async () => {
    runSummaryEtag = await updateAnalysisRunSummary(env, job.run_key, runSummary, runSummaryEtag);
  };
  const finishJob = async finalizingJob => {
    const advanced = await advanceAnalysisSource(env, sourceTag, job.previous_source_tag ?? null);
    const finishedAt = isoNow();
    runSummary = {
      ...runSummary,
      finished_at: finishedAt,
      status: advanced ? 'success' : 'partial',
      processed: advanced ? total : Number(finalizingJob.next_index || 0),
      failed: (finalizingJob.failed_codes || []).length,
      metadata: {
        ...(runSummary.metadata || {}),
        source_changed: true,
        source_tag_advanced: advanced,
        catalog_generation: generation,
        total,
        batch_size: ANALYSIS_BATCH_SIZE,
        next_index: Number(finalizingJob.next_index || 0),
        failure_samples: finalizingJob.last_failure_samples || [],
        durations_ms: { total_ms: Date.now() - runStartedMs },
      },
    };
    await persistRunSummary();
    await saveJob({
      ...finalizingJob,
      status: advanced ? 'complete' : 'superseded',
      retry_at: null,
      lease_id: null,
      lease_until: null,
      finished_at: finishedAt,
    });
    return advanced;
  };

  if (job.status === 'finalizing') {
    const sourceTagAdvanced = await finishJob(job);
    return {
      processed: sourceTagAdvanced ? total : Number(job.next_index || 0),
      failed: (job.failed_codes || []).length,
      source_changed: true,
      source_tag_advanced: sourceTagAdvanced,
      complete: sourceTagAdvanced,
      next_index: Number(job.next_index || 0),
      total,
      observed_at: isoNow(),
    };
  }

  const batchStart = Number(job.next_index || 0);
  const batchEnd = Math.min(total, batchStart + ANALYSIS_BATCH_SIZE);
  const batch = securities.slice(batchStart, batchEnd);
  const batchFailures = [];
  for (let offset = 0; offset < batch.length; offset += ANALYSIS_CONCURRENCY) {
    const group = batch.slice(offset, offset + ANALYSIS_CONCURRENCY);
    await Promise.all(group.map(async security => {
      try {
        const input = await loadSecurityAnalysisInput(env, security);
        const snapshot = await buildAnalysisSnapshot(env, security, null, {
          stockDefs, watchDefs, config, input, skipDefinitionAudit: true,
        });
        await persistSnapshot(env, security, snapshot);
      } catch (error) {
        if (batchFailures.length < 20) {
          batchFailures.push({
            security_code: security.security_code_display,
            error: String(error?.name || 'Error').slice(0, 80),
          });
        }
      }
    }));
  }

  const batchCodes = new Set(batch.map(row => row.security_code_display));
  const failedCodes = new Set((job.failed_codes || []).filter(code => !batchCodes.has(code)));
  for (const failure of batchFailures) failedCodes.add(failure.security_code);
  const commonJobFields = {
    ...job,
    failed_codes: [...failedCodes],
    last_failure_samples: batchFailures,
    retry_at: null,
    lease_id: leaseId,
    lease_until: claimedJob.lease_until,
  };

  if (batchFailures.length > 0) {
    const attempts = Number(job.attempts || 0) + 1;
    const delayMs = Math.min(60 * 60 * 1000, 60 * 1000 * (2 ** Math.min(attempts - 1, 6)));
    const retryAt = new Date(Date.now() + delayMs).toISOString();
    const retryingJob = {
      ...commonJobFields,
      status: 'retrying',
      attempts,
      retry_at: retryAt,
      lease_id: null,
      lease_until: null,
    };
    await saveJob(retryingJob);
    runSummary = {
      ...runSummary,
      finished_at: null,
      status: 'running',
      processed: batchStart,
      failed: failedCodes.size,
      metadata: {
        ...(runSummary.metadata || {}),
        source_changed: true,
        catalog_generation: generation,
        total,
        batch_size: ANALYSIS_BATCH_SIZE,
        next_index: batchStart,
        retry_at: retryAt,
        failure_samples: batchFailures,
      },
    };
    await persistRunSummary();
    return {
      processed: 0,
      failed: batchFailures.length,
      source_changed: true,
      source_tag_advanced: false,
      retry_at: retryAt,
      next_index: batchStart,
      total,
      observed_at: isoNow(),
    };
  }

  const nextIndex = batchEnd;
  const finalizing = nextIndex >= total;
  const completedBatchJob = {
    ...commonJobFields,
    next_index: nextIndex,
    attempts: 0,
    status: finalizing ? 'finalizing' : 'running',
    lease_id: finalizing ? leaseId : null,
    lease_until: finalizing ? claimedJob.lease_until : null,
  };
  await saveJob(completedBatchJob);
  runSummary = {
    ...runSummary,
    finished_at: null,
    status: 'running',
    processed: nextIndex,
    failed: failedCodes.size,
    metadata: {
      ...(runSummary.metadata || {}),
      source_changed: true,
      source_tag_advanced: false,
      catalog_generation: generation,
      total,
      batch_size: ANALYSIS_BATCH_SIZE,
      next_index: nextIndex,
      failure_samples: [],
      durations_ms: { last_batch_ms: Date.now() - runStartedMs },
    },
  };
  await persistRunSummary();

  let sourceTagAdvanced = false;
  if (finalizing) sourceTagAdvanced = await finishJob(completedBatchJob);
  return {
    processed: batch.length,
    failed: 0,
    source_changed: true,
    source_tag_advanced: sourceTagAdvanced,
    complete: finalizing && sourceTagAdvanced,
    next_index: nextIndex,
    total,
    observed_at: isoNow(),
  };
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

function rateLimitLegacyImportUntil(env, nowSeconds, windowSeconds) {
  if (!env.DB) return 0;
  const rawFenceAt = env.RATE_LIMITER_D1_FENCE_AT;
  const rawImportUntil = env.RATE_LIMITER_D1_IMPORT_UNTIL;
  if (typeof rawFenceAt !== 'string' || !/^\d+$/.test(rawFenceAt) ||
      typeof rawImportUntil !== 'string' || !/^\d+$/.test(rawImportUntil)) {
    throw new Error('RATE_LIMITER_D1_FENCE_AT and RATE_LIMITER_D1_IMPORT_UNTIL must be Unix epoch timestamps in seconds');
  }
  const fenceAt = Number(rawFenceAt);
  const importUntil = Number(rawImportUntil);
  const requiredImportUntil = fenceAt + Math.ceil(windowSeconds);
  if (!Number.isSafeInteger(fenceAt) || !Number.isSafeInteger(importUntil) ||
      !Number.isSafeInteger(requiredImportUntil) || importUntil < requiredImportUntil) {
    throw new Error('rate-limit D1 import deadline must cover a full window after the D1 fence');
  }
  if (fenceAt > nowSeconds) throw new Error('RATE_LIMITER_D1_FENCE_AT is in the future');
  return importUntil;
}

export class RateLimitCounter {
  constructor(ctx, env) {
    this.ctx = ctx;
    this.env = env;
    this.legacySeed = null;
    this.cleanupAlarmTarget = null;
    this.alarmSetup = Promise.resolve();
    this.ctx.storage.sql.exec(`CREATE TABLE IF NOT EXISTS rate_limit_state (
      id INTEGER PRIMARY KEY CHECK (id = 1),
      window_started_at INTEGER NOT NULL,
      request_count INTEGER NOT NULL,
      window_seconds REAL NOT NULL
    )`);
    this.ctx.storage.sql.exec(`CREATE TABLE IF NOT EXISTS rate_limit_legacy_seed (
      id INTEGER PRIMARY KEY CHECK (id = 1)
    )`);
  }

  async fetch(request) {
    if (request.method !== 'POST') return new Response('method not allowed', { status: 405 });
    let input;
    try { input = await request.json(); } catch { return new Response('invalid json', { status: 400 }); }
    const bucketKey = String(input.bucket_key || '');
    const windowSeconds = Math.max(1, Number(input.window_seconds) || 60);
    const limit = Number(input.limit);
    const nowSeconds = Math.floor(Number(input.now_seconds) || Date.now() / 1000);
    if (!bucketKey || !Number.isFinite(limit) || !Number.isFinite(nowSeconds)) {
      return new Response('invalid rate-limit request', { status: 400 });
    }
    const importUntil = rateLimitLegacyImportUntil(this.env, nowSeconds, windowSeconds);

    const readRow = () => Array.from(this.ctx.storage.sql.exec(
      'SELECT window_started_at, request_count, window_seconds FROM rate_limit_state WHERE id = 1'
    ))[0] || null;
    let row = readRow();
    let initial = null;
    if (!row) {
      if (!this.legacySeed) {
        const alreadyImported = Array.from(this.ctx.storage.sql.exec(
          'SELECT id FROM rate_limit_legacy_seed WHERE id = 1'
        )).length > 0;
        const canImportLegacy = Boolean(this.env.DB) && nowSeconds < importUntil;
        this.legacySeed = alreadyImported || !canImportLegacy
          ? Promise.resolve({ startedAt: nowSeconds, count: 0 })
          : (async () => {
              const legacy = await this.env.DB.prepare(
                'SELECT request_count, window_started_at FROM api_request_log WHERE bucket_key = ?'
              ).bind(bucketKey).first();
              const legacyStart = legacy?.window_started_at ? Math.floor(Date.parse(legacy.window_started_at) / 1000) : NaN;
              const legacyCount = Number(legacy?.request_count);
              return Number.isFinite(legacyStart) && Number.isFinite(legacyCount) && legacyStart <= nowSeconds && nowSeconds - legacyStart < windowSeconds
                ? { startedAt: legacyStart, count: Math.max(0, legacyCount) }
                : { startedAt: nowSeconds, count: 0 };
            })();
      }
      try { initial = await this.legacySeed; } catch (error) { this.legacySeed = null; throw error; }
    }

    let result;
    this.ctx.storage.transactionSync(() => {
      const markLegacySeeded = () => this.ctx.storage.sql.exec(
        'INSERT OR IGNORE INTO rate_limit_legacy_seed(id) VALUES (1)'
      );
      if (!row) {
        const legacyStartedAt = Number(initial?.startedAt);
        const legacyCount = Number(initial?.count);
        const legacyWindowActive = Number.isFinite(legacyStartedAt) && Number.isFinite(legacyCount) &&
          legacyStartedAt <= nowSeconds && nowSeconds - legacyStartedAt < windowSeconds;
        const startedAt = legacyWindowActive ? legacyStartedAt : nowSeconds;
        const count = legacyWindowActive ? Math.max(0, legacyCount) + 1 : 1;
        const inserted = Array.from(this.ctx.storage.sql.exec(
          'INSERT OR IGNORE INTO rate_limit_state(id, window_started_at, request_count, window_seconds) VALUES (1, ?, ?, ?) RETURNING id',
          startedAt, count, windowSeconds,
        )).length > 0;
        if (inserted) {
          markLegacySeeded();
          result = { allowed: count <= limit, count, window_started_at: startedAt };
          return;
        }
        // A concurrent first request inserted the seed while this request waited on D1.
        row = readRow();
      }
      markLegacySeeded();
      const expired = nowSeconds - Number(row.window_started_at) >= windowSeconds;
      const startedAt = expired ? nowSeconds : Number(row.window_started_at);
      const count = expired ? 1 : Number(row.request_count) + 1;
      this.ctx.storage.sql.exec(
        'UPDATE rate_limit_state SET window_started_at = ?, request_count = ?, window_seconds = ? WHERE id = 1',
        startedAt, count, windowSeconds,
      );
      result = { allowed: count <= limit, count, window_started_at: startedAt };
    });

    const cleanupAt = (Number(result.window_started_at) + windowSeconds) * 1000;
    await this.scheduleCleanup(cleanupAt);

    return Response.json(result);
  }

  async scheduleCleanup(cleanupAt) {
    const operation = this.alarmSetup.then(async () => {
      if (this.cleanupAlarmTarget === cleanupAt) return;
      if (this.cleanupAlarmTarget === null) {
        const existingAlarm = await this.ctx.storage.getAlarm();
        if (existingAlarm === cleanupAt) {
          this.cleanupAlarmTarget = cleanupAt;
          return;
        }
      }
      await this.ctx.storage.setAlarm(cleanupAt);
      this.cleanupAlarmTarget = cleanupAt;
    });
    this.alarmSetup = operation.catch(() => {});
    await operation;
  }

  async alarm() {
    this.cleanupAlarmTarget = null;
    const row = Array.from(this.ctx.storage.sql.exec(
      'SELECT window_started_at, window_seconds FROM rate_limit_state WHERE id = 1'
    ))[0] || null;
    if (!row) return;

    const windowStartedAt = Number(row.window_started_at);
    const windowSeconds = Number(row.window_seconds);
    const cleanupAt = (windowStartedAt + windowSeconds) * 1000;
    if (!Number.isFinite(windowStartedAt) || !Number.isFinite(windowSeconds) || windowSeconds < 1 || !Number.isFinite(cleanupAt)) {
      throw new Error('rate limiter stored window is invalid');
    }
    if (Date.now() < cleanupAt) {
      await this.scheduleCleanup(cleanupAt);
      return;
    }

    this.ctx.storage.sql.exec('INSERT OR IGNORE INTO rate_limit_legacy_seed(id) VALUES (1)');
    this.ctx.storage.sql.exec('DELETE FROM rate_limit_state WHERE id = 1');
  }
}

export default {
  async fetch(request, env, ctx) {
    env = withMarketReadCache(env);
    const url = new URL(request.url);

    if (request.method === 'OPTIONS') return new Response(null, { status: 204, headers: json(null).headers });

    const deviceManagedRoutes = new Set([
      '/api/v1/clients/register',
      '/api/v1/push-devices',
      '/api/v1/watchlist-targets',
      '/api/v1/notification-events',
    ]);
    if (deviceManagedRoutes.has(url.pathname)) return json({ error: 'device_managed' }, 410);

    if (url.pathname === '/api/v1/income-advice-candidates' && request.method === 'GET') {
      const config = await backendConfig(env);
      if (!await enforceRateLimit(
        request,
        env,
        `public:${publicRateLimitRoute(url.pathname)}`,
        config.rate_limit_public,
        '',
        config,
      )) return rateLimitedResponse(config);
      try {
        const object = await env.MARKET_DATA?.get(`${YFINANCE_V2_ROOT}/derived/income_advice/current.json`);
        if (!object) return json({ error: 'income_advice_unavailable' }, 503);
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

    const config = await backendConfig(env);

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

    const rateLimitRoute = request.method === 'GET' ? publicRateLimitRoute(url.pathname) : null;
    if (rateLimitRoute && !await enforceRateLimit(request, env, `public:${rateLimitRoute}`, config.rate_limit_public, '', config)) {
      return rateLimitedResponse(config);
    }

    if (url.pathname === '/api/v1/admin/run-analysis' && request.method === 'POST') {
      const provided = String(request.headers.get('X-Admin-Secret') || '');
      if (!env.ADMIN_SECRET || provided !== env.ADMIN_SECRET) return json({ error: 'unauthorized' }, 401);
      try {
        return json(await runScheduledAnalysis(env));
      } catch (error) {
        if (error instanceof AnalysisDefinitionsUnavailableError) return json({ error: 'definitions_unavailable' }, 503);
        throw error;
      }
    }

    if (url.pathname === '/api/v1/health' && request.method === 'GET') {
      const [currentRunPointer, migratedRunPointer, v2Catalog] = await Promise.all([
        readV2Json(env, `${ANALYSIS_RUN_PREFIX}current.json`),
        readV2Json(env, `${SCHEDULED_RUN_PREFIX}legacy-d1/current.json`),
        readV2Catalog(env),
      ]);
      const currentRunAt = Date.parse(currentRunPointer?.started_at || '');
      const migratedRunAt = Date.parse(migratedRunPointer?.started_at || '');
      const runPointer = Number.isFinite(migratedRunAt)
        && (!Number.isFinite(currentRunAt) || migratedRunAt > currentRunAt)
        ? migratedRunPointer
        : currentRunPointer;
      const runDocument = runPointer?.run_key ? await readV2Json(env, runPointer.run_key) : null;
      const latestRun = runDocument ? {
        started_at: runDocument.started_at,
        finished_at: runDocument.finished_at,
        status: runDocument.status,
        processed: runDocument.processed,
        failed: runDocument.failed,
      } : null;
      const quoteRun = null;
      const marketRefreshObject = await env.MARKET_DATA?.get('market/yfinance/live_quotes_state.json');
      const marketRefresh = marketRefreshObject ? await marketRefreshObject.json() : {};
      const marketCompletionObject = await env.MARKET_DATA?.get('market/yfinance/live_quotes_completion.json');
      const marketCompletion = marketCompletionObject ? await marketCompletionObject.json() : {};
      const hasPublishedRun = !!marketCompletion.last_completed_run_id;
      const liveQuoteCount = Number((hasPublishedRun ? marketCompletion.live_quote_count : null) ?? quoteRun?.processed_symbols ?? marketRefresh.live_quote_count ?? 0);
      const marketObject = await env.MARKET_DATA?.head('market/yfinance/latest_market_data.json');
      const v2CatalogItems = Array.isArray(v2Catalog?.items) ? v2Catalog.items : [];
      const v2CatalogAvailable = Array.isArray(v2Catalog?.items);
      const v2MarketDataDate = /^\d{4}-\d{2}-\d{2}$/.test(String(v2Catalog?.market_data_date || ''))
        ? v2Catalog.market_data_date : null;
      const v2CatalogGeneratedAt = v2Catalog?.generated_at || null;
      const v2CatalogAgeAnchor = v2CatalogGeneratedAt || (v2MarketDataDate ? `${v2MarketDataDate}T00:00:00.000Z` : null);
      const legacySnapshotUploadedAt = marketObject?.uploaded?.toISOString?.() || null;
      const marketUploadedAt = v2CatalogAgeAnchor
        || (hasPublishedRun ? marketCompletion.last_completed_at : null)
        || legacySnapshotUploadedAt;
      const v2QuotedSecurityCount = v2CatalogItems.filter(row => Number(row?.price) > 0
        && /^\d{4}-\d{2}-\d{2}$/.test(String(row?.quote_date || ''))).length;
      let analysisDefinitions;
      try {
        analysisDefinitions = await loadAnalysisDefinitions(env);
      } catch (error) {
        if (error instanceof AnalysisDefinitionsUnavailableError) {
          return json({ error: 'definitions_unavailable' }, 503);
        }
        throw error;
      }
      const { stockDefs, watchDefs } = analysisDefinitions;
      return json({ ok: true, checked_at: isoNow(), d1: !!env.DB, market: {
        available: !!marketObject || v2CatalogAvailable,
        uploaded: marketUploadedAt,
        snapshot_uploaded: legacySnapshotUploadedAt,
        age_days: marketUploadedAt ? daysOld(marketUploadedAt) : null,
        legacy_snapshot_age_days: legacySnapshotUploadedAt ? daysOld(legacySnapshotUploadedAt) : null,
        stale_after_days: Number(config.market_stale_days || 3),
        v2_catalog: {
          available: v2CatalogAvailable,
          generation: v2Catalog?.generation || null,
          market_data_date: v2MarketDataDate,
          generated_at: v2CatalogGeneratedAt,
          item_count: v2CatalogItems.length,
          quoted_security_count: v2QuotedSecurityCount,
        },
        live_quote_count: liveQuoteCount,
        live_refresh_date: quoteRun?.run_date || marketRefresh.queue_run_date || null,
        live_refresh_batches_total: Number(quoteRun?.total_batches ?? marketRefresh.queue_batches_total ?? 0),
        live_refresh_batches_enqueued: Number(quoteRun?.enqueued_batches ?? marketRefresh.queue_batches_enqueued ?? 0),
        live_refresh_batches_completed: Number(marketRefresh.queue_batches_completed || 0),
        live_refresh_last_batch_at: quoteRun?.last_batch_at || marketRefresh.last_batch_at || null,
        live_refresh_last_completed_date: hasPublishedRun ? marketCompletion.last_completed_date || null : null,
        live_refresh_failed_batches: Number(marketRefresh.queue_failed_batches ?? (hasPublishedRun ? marketCompletion.last_failed_batches : null) ?? 0),
        live_refresh_status: marketRefresh.queue_status || (hasPublishedRun ? marketCompletion.last_run_status : null) || null,
        live_refresh_market_data_date: marketRefresh.market_data_date || (hasPublishedRun ? marketCompletion.last_completed_date : null) || null,
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
          const [v2Events, current, dividendBasis] = await Promise.all([
            readV2DividendEvents(env, symbol, years),
            v2Symbol ? readV2Json(env, `${YFINANCE_V2_ROOT}/securities/${v2Symbol}/current.json`) : Promise.resolve(null),
            readNormalizedDividendBasis(env, symbol),
          ]);
          const basisFresh = isFreshDividendBasis(dividendBasis, current);
          const basisCurrent = isCurrentDividendBasis(dividendBasis, current);
          const sourceEvents = basisFresh
            ? dividendBasis.records.filter(event => years.includes(Number(String(event?.date || '').slice(0, 4))))
            : v2Events;
          const storedHistoryStatus = current?.field_status?.dividend_history
            || (sourceEvents ? (sourceEvents.length > 0 ? 'available' : 'empty') : 'not_fetched');
          const estimatedHistoryEvent = estimatedEventsBySymbol.get(symbol);
          const historyStatus = basisFresh && dividendBasis.status !== 'available'
            ? dividendBasis.status || 'review_required'
            : estimatedHistoryEvent && ['empty', 'not_fetched'].includes(storedHistoryStatus)
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
          if (sourceEvents) {
            const selectedByDate = new Map();
            let conflictingDates = false;
            for (const event of sourceEvents) {
              const eventDate = String(event?.date || '');
              const amount = finiteNumber(event?.amount);
              if (!/^\d{4}-\d{2}-\d{2}$/.test(eventDate) || amount === null || amount <= 0) continue;
              const previous = selectedByDate.get(eventDate);
              if (previous && previous.amount !== amount) conflictingDates = true;
              else selectedByDate.set(eventDate, event);
            }
            const events = [...selectedByDate.values()].filter(event => {
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
                amount_basis: basisCurrent ? dividendBasis.amount_basis
                  : current?.dividend?.event_amount_basis || 'yfinance_split_adjusted_assumption',
                ...(basisCurrent ? {
                  source: 'Yahoo raw dividends normalized with issuer-filed share actions',
                  raw_amount: finiteNumber(event.raw_amount),
                  share_adjustment_factor: finiteNumber(event.share_adjustment_factor),
                } : {}),
                row_repaired_by_yfinance: event.row_repaired_by_yfinance === true,
                final_rights_date: rightsDates.finalRightsDate || null, record_date: rightsDates.recordDate || null,
                ...(/^\d{4}-\d{2}-\d{2}$/.test(paymentStartDate)
                  ? { payment_start_date: paymentStartDate }
                  : {}) };
            }).sort((a, b) => a.date.localeCompare(b.date));
            const resolvedEvents = conflictingDates ? [] : events;
            return {
              symbol,
              history_status: conflictingDates ? 'review_required_conflicting_same_date_values' : historyStatus,
              ...(rawEventYears == null ? {} : { requested_event_years: requestedYears }),
              amount_basis: basisCurrent ? dividendBasis.amount_basis
                : current?.dividend?.event_amount_basis || 'yfinance_split_adjusted_assumption',
              months: [...new Set(resolvedEvents
                .filter(event => rawEventYears == null || event.date >= cutoff)
                .map(event => Number(event.date.slice(5, 7))))].sort((a, b) => a - b),
              events: resolvedEvents,
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
        const [events, current, issuerActions] = await Promise.all([
          readV2SplitEvents(env, code, years),
          symbol ? readV2Json(env, `${YFINANCE_V2_ROOT}/securities/${symbol}/current.json`) : Promise.resolve(null),
          readIssuerShareActions(env, code),
        ]);
        const records = (events || []).map(event => {
          const date = String(event?.date || '');
          const ratio = finiteNumber(event?.ratio);
          return /^\d{4}-\d{2}-\d{2}$/.test(date) && date >= from && date <= to
            && ratio != null && ratio > 0 && Math.abs(ratio - 1) > 1e-9
            ? { date, ratio, _source: 'Yahoo Finance' }
            : null;
        }).filter(Boolean);
        for (const action of issuerActions?.events || []) {
          const date = String(action?.effective_date || '');
          const ratio = finiteNumber(action?.ratio);
          if (/^\d{4}-\d{2}-\d{2}$/.test(date) && date >= from && date <= to
            && ratio != null && ratio > 0 && Math.abs(ratio - 1) > 1e-9) {
            records.push({ date, ratio, _source: 'IRBANK issuer disclosure', action_type: action.action_type,
              source_documents: action.sources || [] });
          }
        }
        const issuerEvents = records.filter(event => event._source === 'IRBANK issuer disclosure');
        const yahooEvents = records.filter(event => event._source !== 'IRBANK issuer disclosure');
        const matchedYahooEvents = new Set();
        for (const issuerEvent of issuerEvents) {
          for (const yahooEvent of yahooEvents) {
            const dayDistance = Math.abs(Date.parse(`${issuerEvent.date}T00:00:00Z`)
              - Date.parse(`${yahooEvent.date}T00:00:00Z`)) / 86400000;
            if (dayDistance <= 5 && Math.abs(issuerEvent.ratio - yahooEvent.ratio)
              <= Math.max(1e-12, Math.abs(issuerEvent.ratio) * 1e-8)) {
              matchedYahooEvents.add(yahooEvent);
            }
          }
        }
        const mergedByDate = new Map();
        let actionConflicts = false;
        for (const event of [...issuerEvents, ...yahooEvents.filter(row => !matchedYahooEvents.has(row))]
          .sort((a, b) => a.date.localeCompare(b.date))) {
          const existing = mergedByDate.get(event.date);
          if (existing && Math.abs(existing.ratio - event.ratio) > Math.max(1e-12, Math.abs(existing.ratio) * 1e-8)) {
            actionConflicts = true;
            continue;
          }
          if (!existing || event._source === 'IRBANK issuer disclosure') mergedByDate.set(event.date, event);
        }
        const uniqueRecords = (actionConflicts ? [] : [...mergedByDate.values()])
          .sort((a, b) => a.date.localeCompare(b.date))
          .map(({ _source, ...event }) => _source === 'IRBANK issuer disclosure'
            ? { ...event, source: _source, source_documents: event.source_documents }
            : { date: event.date, ratio: event.ratio });
        const storedHistoryStatus = current?.field_status?.split_history;
        const historyStatus = actionConflicts ? 'review_required_conflicting_ratios'
          : issuerActions?.status === 'review_required' || (issuerActions?.errors || []).length
            ? 'review_required_incomplete_issuer_coverage'
          : uniqueRecords.length > 0
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
        const irbankDividendIndexes = requestedCodes
          ? await readIrbankDividendIndexesForCodes(env, requestedCodes)
          : null;
        const irbankDividendIndex = requestedCodes ? null : await readIrbankDividendIndex(env);
        const catalogItems = catalog.items || [];
        const offset = Math.min(catalogItems.length, Number.parseInt(cursor || '0', 10));
        const requestedItems = requestedCodes
          ? requestedCodes.map(code => catalogItems.find(row => String(row.code || '').toUpperCase() === code)).filter(Boolean)
          : catalogItems.slice(offset, offset + limit);
        const items = await Promise.all(requestedItems.map(async row => {
          const code = String(row.code || '').toUpperCase();
          const issuerIndex = requestedCodes
            ? irbankDividendIndexes.get(code.slice(0, DIVIDEND_SHARD_PREFIX_LENGTH)) ?? irbankDividendIndexes.get('*')
            : irbankDividendIndex;
          const issuerDividend = irbankDividendForCode(issuerIndex, code);
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
          const quote = row.security_type === 'reit' && selected.dividend_amount_basis === 'yfinance_forward_dividend_rate'
            ? reitForwardQuote(row, current) : null;
          const close = quote ? quote.price : finiteNumber(current?.price?.close) ?? finiteNumber(row.price);
          const selectedYield = searchYield(close, selected);
          return {
            ...item,
            price: close ?? item.price,
            quote_date: quote ? quote.date : current?.price?.date ?? item.quote_date,
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
      const [v2Catalog, irbankCatalog] = await Promise.all([
        readV2Catalog(env),
        readV2Json(env, 'market/irbank/v1/catalog.json'),
      ]);
      if (!v2Catalog) return json({ error: 'security_catalog_unavailable' }, 503);
      const irbankDividendIndex = await readIrbankDividendIndex(env);
      const normalizeSearchText = value => String(value || '').normalize('NFKC').toLowerCase();
      const needle = normalizeSearchText(query);
      const irbankNamesByCode = new Map();
      for (const row of irbankCatalog?.securities || []) {
        const code = irbankR2Code(String(row?.code || ''));
        const name = String(row?.name || '').trim();
        if (code && name && !irbankNamesByCode.has(code)) irbankNamesByCode.set(code, name);
      }
      const itemsByCode = new Map();
      for (const row of v2Catalog.items || []) {
        const code = yfinanceV2Code(row?.code || row?.security_code_display || row?.symbol);
        if (code && !itemsByCode.has(code)) {
          itemsByCode.set(code, {
            ...row,
            code,
            name: irbankNamesByCode.get(code) || row.name,
          });
        }
      }
      for (const row of irbankCatalog?.securities || []) {
        const code = irbankR2Code(String(row?.code || ''));
        if (code && !itemsByCode.has(code)) {
          itemsByCode.set(code, { code, name: irbankNamesByCode.get(code) || code });
        }
      }
      const items = [...itemsByCode.values()]
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
          const current = issuer.forecast == null && yahooForwardEligible(row) && symbol
            ? await readV2Json(env, `${YFINANCE_V2_ROOT}/securities/${symbol}/current.json`) : null;
          const dividend = searchDividend(row, issuer, current);
          const quote = row.security_type === 'reit' && dividend.dividend_amount_basis === 'yfinance_forward_dividend_rate'
            ? reitForwardQuote(row, current) : null;
          const price = quote ? quote.price : finiteNumber(current?.price?.close) ?? finiteNumber(row.price);
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
      const [catalog, irbankCatalog] = await Promise.all([
        readV2Catalog(env),
        readV2Json(env, 'market/irbank/v1/catalog.json'),
      ]);
      if (!catalog) return json({ error: 'security_catalog_unavailable' }, 503);
      const item = (catalog.items || []).find(row => String(row.code || '').toUpperCase() === code);
      const irbankMaster = (irbankCatalog?.securities || []).find(row => irbankR2Code(String(row?.code || '')) === code);
      if (!item) {
        const name = String(irbankMaster?.name || '').trim();
        if (!name) return json({ error: 'not_found' }, 404);
        return json({
          code,
          name,
          dividend: {
            annual_dps: null,
            annual_dps_adjusted: null,
            trailing_12_month_per_share: null,
            fiscal_year: null,
            yield_basis: null,
            dividend_period: 'unknown',
            amount_basis: null,
            source: null,
            field_status: { annual_dps: 'empty', forecast_dividend: 'empty' },
          },
          market_price: null,
          market_yield_rate: null,
          market_yield_source: null,
          market_yield_as_of: null,
          market_yield_attribution: null,
          dividend_status: 'unknown',
          valuation: {
            per: null,
            pbr: null,
            source: null,
            price_date: null,
            statistics_last_fetched_session_date: null,
            formulas: {},
          },
          data_retrieval: {
            yfinance_ticker: null,
            source: 'IRBANK R2 catalog',
            currency: null,
            last_attempt_at: null,
            processing_status: 'master_only',
            failure_reason: null,
            company_info: {
              status: 'master_only',
              last_fetched_session_date: null,
              last_fetched_at: null,
              fetch_date_basis: null,
              last_attempt_session_date: null,
              last_attempt_at: null,
              last_attempt_status: null,
              refresh_pending: false,
              failure_class: null,
              failure_reason: null,
            },
            dividend_history: {
              status: 'not_available',
              last_attempt_status: null,
              source: null,
              yfinance_version: null,
              stored_history_status: 'not_available',
              stored_history_start: null,
              stored_history_end: null,
              last_coverage_start: null,
              last_coverage_end: null,
              full_history_coverage_end: null,
              last_coverage_event_count: null,
              period: null,
            },
            ex_dividend_calendar: {
              date: null,
              status: 'not_available',
              last_attempt_status: null,
              fetched_at: null,
              source: null,
            },
          },
        });
      }
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
      if (items === null) return json({ error: 'definitions_unavailable' }, 503);
      return json({ items });
    }

    if (url.pathname === '/api/v1/valuation-scenarios') {
      const items = await appDefinitionItems(env, 'valuation-scenarios:v1');
      if (items === null) return json({ error: 'definitions_unavailable' }, 503);
      return json({ items });
    }

    if (url.pathname === '/api/v1/stock-analysis-definitions') {
      const definition = await appDefinitionDocument(env, 'stock-analysis-definitions:v1');
      if (definition === null) return json({ error: 'definitions_unavailable' }, 503);
      return json(definition);
    }

    if (url.pathname === '/api/v1/watchlist-analysis-definitions') {
      const definition = await appDefinitionDocument(env, 'watchlist-analysis-definitions:v1');
      if (definition === null) return json({ error: 'definitions_unavailable' }, 503);
      return json(definition);
    }

    if (url.pathname === '/api/v1/portfolio-analysis-definitions') {
      const definition = await appDefinitionDocument(env, 'portfolio-analysis-definitions:v1');
      if (definition === null) return json({ error: 'definitions_unavailable' }, 503);
      return json(definition);
    }

    if (url.pathname === '/api/v1/definition-audit') {
      if (!env.MARKET_DATA) return json({ error: 'market_data_unavailable' }, 503);
      const objects = [];
      let cursor;
      do {
        const page = await env.MARKET_DATA.list({ prefix: DEFINITION_AUDIT_PREFIX, limit: 1000, cursor });
        objects.push(...(page.objects || []));
        cursor = page.truncated ? page.cursor : undefined;
      } while (cursor && objects.length < 5000);
      const recent = objects.sort((a, b) => (b.uploaded?.getTime?.() || 0) - (a.uploaded?.getTime?.() || 0)).slice(0, 100);
      const items = await Promise.all(recent.map(async item => {
        const document = await readV2Json(env, item.key);
        return document ? { ...document, updated_at: item.uploaded?.toISOString?.() || null } : null;
      }));
      return json({ items: items.filter(Boolean) });
    }

    const historyMatch = url.pathname.match(/^\/api\/v1\/analysis-history\/([^/]+)$/);
    if (historyMatch) {
      const requested = decodeURIComponent(historyMatch[1]);
      const code = normalizeCode(requested);
      if (!env.MARKET_DATA) return json({ error: 'market_data_unavailable' }, 503);
      const objects = [];
      let cursor;
      do {
        const page = await env.MARKET_DATA.list({ prefix: `${ANALYSIS_HISTORY_PREFIX}${code}/`, limit: 1000, cursor });
        objects.push(...(page.objects || []));
        cursor = page.truncated ? page.cursor : undefined;
      } while (cursor);
      objects.sort((a, b) => b.key.localeCompare(a.key));
      const rows = await Promise.all(objects.slice(0, 500).map(async item => readV2Json(env, item.key)));
      const items = rows.filter(Boolean).sort((a, b) => String(b.observed_at || '').localeCompare(String(a.observed_at || ''))).slice(0, 90)
        .map(row => ({
          ...row,
          anomaly_flags: JSON.parse(row.anomaly_flags_json || '[]'),
          metrics: JSON.parse(row.metrics_json || '{}'),
          analysis: JSON.parse(row.analysis_json || '{}'),
        }));
      return json({ items });
    }

    const analysisMatch = url.pathname.match(/^\/api\/v1\/analysis\/([^/]+)$/);
    if (analysisMatch) {
      const requested = decodeURIComponent(analysisMatch[1]);
      const displayCode = yfinanceV2Code(requested);
      if (!displayCode) return json({ error: 'invalid_security_code' }, 400);
      const [currentProfile, catalog, irbankCatalog] = await Promise.all([displayCode
        ? await readV2Json(env, `${YFINANCE_V2_ROOT}/securities/${yfinanceV2Symbol(displayCode)}/profile.json`)
        : null,
      readV2Catalog(env),
      readV2Json(env, 'market/irbank/v1/catalog.json')]);
      const catalogItem = catalog?.items?.find(row => yfinanceV2Code(row?.code || row?.security_code_display || row?.symbol) === displayCode);
      const irbankMaster = (irbankCatalog?.securities || []).find(row => irbankR2Code(String(row?.code || '')) === displayCode);
      if (!currentProfile && !catalogItem && !irbankMaster) return json({ error: 'not_found' }, 404);
      const security = {
        security_code: displayCode.length === 4 ? `${displayCode}0` : displayCode,
        security_code_display: displayCode,
        name: currentProfile?.display_name || currentProfile?.short_name || currentProfile?.long_name
          || irbankMaster?.name || catalogItem?.name || displayCode,
      };
      const targetYield = url.searchParams.get('target_yield');
      try {
        return json(await buildAnalysisSnapshot(env, security, targetYield === null ? null : Number(targetYield)));
      } catch (error) {
        if (error instanceof AnalysisDefinitionsUnavailableError) return json({ error: 'definitions_unavailable' }, 503);
        throw error;
      }
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

  async scheduled(controller, env, ctx) {
    if (controller?.cron === '*/5 * * * *') {
      return compactAnalysisRetentionIfDue(env);
    }
    if (controller?.cron === '17 * * * *') {
      ctx.waitUntil(retryFeedbackEmails(env));
      ctx.waitUntil(env.DB.prepare("DELETE FROM support_feedback WHERE created_at < strftime('%Y-%m-%dT%H:%M:%fZ', 'now', '-90 days')").run());
      return { maintenance: 'scheduled' };
    }
    return runScheduledAnalysis(env);
  },

  async queue(batch) {
    // Retire any queued requests from the former live Yahoo quote collector.
    for (const message of batch.messages) message.ack();
  }
};
