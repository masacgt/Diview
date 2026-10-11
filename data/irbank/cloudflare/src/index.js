import { readFeedback, saveFeedback } from './feedback.mjs';

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
  if (pathname === '/api/v1/live-quotes') return 'live-quotes';
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
      if (symbols.length === 0 || symbols.length > 50 || symbols.some(symbol => !/^[A-Z0-9.^_=-]{1,32}$/.test(symbol))) {
        return json({ error: 'invalid_request' }, 400);
      }

      const todayParts = Object.fromEntries(new Intl.DateTimeFormat('en', {
        timeZone: 'Asia/Tokyo', year: 'numeric', month: '2-digit', day: '2-digit'
      }).formatToParts(new Date()).map(part => [part.type, part.value]));
      const asOf = `${todayParts.year}-${todayParts.month}-${todayParts.day}`;
      const cutoff = new Date(Date.parse(`${asOf}T00:00:00.000Z`) - 365 * 24 * 60 * 60 * 1000)
        .toISOString().slice(0, 10);
      try {
        const rightsDatesObject = await env.MARKET_DATA.get('market/yfinance/rights_dates.json');
        const rightsDatesDocument = rightsDatesObject ? await rightsDatesObject.json() : null;
        const rightsDatesByEvent = new Map((rightsDatesDocument?.records || [])
          .map(event => [`${String(event.symbol || '').toUpperCase()}|${String(event.ex_dividend_date || '')}`, event.final_rights_date]));
        const items = await Promise.all(symbols.map(async symbol => {
          const safeSymbol = symbol.replaceAll('^', '_').replaceAll('=', '_').replaceAll('/', '_');
          const object = await env.MARKET_DATA.get(`market/yfinance/securities/${safeSymbol}.json`);
          if (!object) return { symbol, months: [] };
          const document = await object.json();
          const dividendEvents = Array.isArray(document.actions?.dividends) ? document.actions.dividends : [];
          const events = dividendEvents
            .filter(event => {
              const date = String(event?.date || '');
              const amount = finiteNumber(event?.amount);
              if (!/^\d{4}-\d{2}-\d{2}$/.test(date) || date < cutoff || date > asOf || !(amount > 0)) return false;
              const parsedDate = new Date(`${date}T00:00:00.000Z`);
              return Number.isFinite(parsedDate.getTime()) && parsedDate.toISOString().slice(0, 10) === date;
            })
            .map(event => ({
              date: String(event.date),
              amount: finiteNumber(event.amount),
              final_rights_date: rightsDatesByEvent.get(`${symbol}|${String(event.date)}`) || null,
            }))
            .sort((a, b) => a.date.localeCompare(b.date));
          const months = [...new Set(events
            .map(event => Number(event.date.slice(5, 7))))]
            .filter(month => month >= 1 && month <= 12)
            .sort((a, b) => a - b);
          return { symbol, months, events };
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
        const dps = finiteNumber(statistics.dividendRate);
        const yieldRate = dps !== null && dps >= 0
          ? (dps / currentPrice) * 100
          : finiteNumber(row.yield_rate);
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
      const [completionObject, catalogObject] = await Promise.all([
        env.MARKET_DATA.get('market/yfinance/live_quotes_completion.json'),
        env.MARKET_DATA.get('market/yfinance/search_catalog.json'),
      ]);
      const completion = completionObject ? await completionObject.json() : {};
      const catalog = catalogObject ? await catalogObject.json() : {};
      const stableMarketDate = completion.last_completed_run_id
        ? String(completion.last_completed_date || '')
        : completion.last_completed_date
          ? '0000-00-00'
          : String(catalog.market_data_date || today);
      const currentMonth = today.slice(0, 7);
      const cutoffs = months.map(month => {
        if (month >= currentMonth) return [month, stableMarketDate];
        const nextMonth = new Date(`${month}-01T00:00:00.000Z`);
        nextMonth.setUTCMonth(nextMonth.getUTCMonth() + 1);
        nextMonth.setUTCDate(0);
        return [month, nextMonth.toISOString().slice(0, 10)];
      });
      const items = (await Promise.all(codes.map(async code => {
        const [object, dailyObjects] = await Promise.all([
          env.MARKET_DATA.get(`market/yfinance/securities/${code}.T.json`),
          Promise.all(months.map(month => env.MARKET_DATA.get(`market/yfinance/daily_prices/${code}/${month}.json`))),
        ]);
        if (!object && dailyObjects.every(item => !item)) return null;
        const document = object ? JSON.parse(await object.text()) : {};
        const prices = new Map((Array.isArray(document.price_history?.['1d']) ? document.price_history['1d'] : [])
          .map(item => [String(item?.date || ''), item]));
        for (const dailyObject of dailyObjects.filter(Boolean)) {
          const dailyDocument = await dailyObject.json();
          for (const point of dailyDocument.prices || []) {
            const date = String(point?.date || '');
            if (date) prices.set(date, point);
          }
        }
        const splits = (document.actions?.stock_splits || [])
          .map(item => ({ date: String(item?.date || ''), ratio: finiteNumber(item?.ratio) }))
          .filter(item => item.date && item.ratio > 0);
        const monthlyPrices = cutoffs.map(([month, cutoff]) => {
          const point = [...prices.values()].reduce((best, item) => {
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

    if (url.pathname === '/api/v1/live-quotes') {
      if (!env.MARKET_DATA) return json({ error: 'market_data_unavailable' }, 503);
      const limit = Math.max(1, Math.min(250, Number.parseInt(url.searchParams.get('limit') || '200', 10) || 200));
      const requestedRunId = String(url.searchParams.get('run_id') || '');
      const requestedDate = String(url.searchParams.get('market_data_date') || '');
      const cursor = String(url.searchParams.get('cursor') || '');
      if ((requestedRunId && !/^\d{4}-\d{2}-\d{2}$/.test(requestedRunId))
        || (requestedDate && !/^\d{4}-\d{2}-\d{2}$/.test(requestedDate))
        || cursor.length > 2048) return json({ error: 'invalid_request' }, 400);

      try {
        const completionObject = await env.MARKET_DATA.get('market/yfinance/live_quotes_completion.json');
        const completion = completionObject ? await completionObject.json() : {};
        const runId = requestedRunId || String(completion.last_completed_run_id || '');
        const marketDataDate = requestedDate || (runId === completion.last_completed_run_id
          ? String(completion.last_completed_date || '')
          : '');
        if (!runId) return json({
          run_id: null,
          market_data_date: null,
          complete: false,
          legacy_unpublished: !!completion.last_completed_date,
          items: [],
          next_cursor: null,
        });
        if (!marketDataDate) return json({ error: 'market_data_date_required' }, 400);
        const result = await env.MARKET_DATA.list({
          prefix: `${LIVE_QUOTE_RUN_PREFIX}${runId}/`,
          limit,
          cursor: cursor || undefined,
          include: ['customMetadata'],
        });
        const items = (result.objects || []).map(object => {
          const code = object.key.slice(`${LIVE_QUOTE_RUN_PREFIX}${runId}/`.length).replace(/\.json$/, '').toUpperCase();
          const price = Number(object.customMetadata?.close);
          if (!/^[A-Z0-9]{4,5}$/.test(code) || !Number.isFinite(price) || price <= 0) return null;
          return { code, price, quote_date: String(object.customMetadata?.quoteDate || marketDataDate) };
        }).filter(Boolean);
        const response = json({
          run_id: runId,
          market_data_date: marketDataDate,
          complete: true,
          items,
          next_cursor: result.truncated ? result.cursor : null,
        });
        response.headers.set('cache-control', 'public, max-age=300');
        return response;
      } catch {
        return json({ error: 'live_quotes_unavailable' }, 503);
      }
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
      const object = await env.MARKET_DATA?.get('market/irbank/v1/catalog.json');
      if (!object) return json({ error: 'security_catalog_unavailable' }, 503);
      const catalog = await object.json();
      const needle = query.toLocaleLowerCase();
      const items = (catalog.securities || [])
        .filter(row => String(row.code || '').toLocaleLowerCase().includes(needle)
          || String(row.name || '').toLocaleLowerCase().includes(needle))
        .sort((a, b) => {
          const aCode = String(a.code || '').toLocaleLowerCase();
          const bCode = String(b.code || '').toLocaleLowerCase();
          const rank = value => value === needle ? 0 : value.startsWith(needle) ? 1 : 2;
          return rank(aCode) - rank(bCode) || String(a.name || '').localeCompare(String(b.name || ''), 'ja');
        })
        .slice(0, 30)
        .map(({ code, name }) => ({ code, name }));
      return json({ items });
    }

    if (url.pathname === '/api/v1/irbank/manifest') {
      return irbankR2Object(env, 'market/irbank/v1/manifest.json');
    }

    if (url.pathname === '/api/v1/irbank/catalog') {
      return irbankR2Object(env, 'market/irbank/v1/catalog.json');
    }

    const financialMatch = url.pathname.match(/^\/api\/v1\/financial-data\/([A-Z0-9]{4})$/);
    if (financialMatch) {
      if (request.method !== 'GET') return json({ error: 'method_not_allowed' }, 405);
      const code = financialMatch[1];
      const object = await env.MARKET_DATA.get(`market/irbank/v1/securities/${code}/overview.json`);
      let document = null;
      if (object) {
        document = await object.json();
        const issuerRecords = document.dividends?.records;
        const hasUsableIssuerDividend = Array.isArray(issuerRecords) && issuerRecords.some(record =>
          Number.isInteger(Number(record?.fiscal_year)) && Number(record.fiscal_year) >= 1900 &&
          Number(record.fiscal_year) <= 2100 && finiteNumber(record?.annual_dps_adjusted) != null &&
          Number(record.annual_dps_adjusted) >= 0);
        if (hasUsableIssuerDividend) {
          return json({ security: document.security, financials: { annual: document.financials?.annual || [] },
            dividends: document.dividends, valuation: document.valuation, sources: document.sources });
        }
      }

      // ETFs and funds have no issuer financial statements; use Yahoo's observed
      // distribution events, grouped by complete calendar year. Never label
      // these as fiscal-year/company-reported DPS.
      const unified = await env.MARKET_DATA.get(`market/securities/v1/${code}.json`);
      if (!unified) return json({ error: 'not_found' }, 404);
      const bundle = await unified.json();
      if (bundle.security?.code && String(bundle.security.code) !== code) return json({ error: 'not_found' }, 404);
      const events = bundle.dividend_history_by_source?.yfinance;
      if (!Array.isArray(events) || events.length === 0) return json({ error: 'not_found' }, 404);
      const currentYear = Number(new Intl.DateTimeFormat('en', { timeZone: 'Asia/Tokyo', year: 'numeric' }).format(new Date()));
      const yearly = new Map();
      const seenEvents = new Set();
      for (const event of events) {
        const date = typeof event?.date === 'string' ? event.date : '';
        const year = Number(date.slice(0, 4));
        const amount = finiteNumber(event?.amount);
        const parsedDate = /^\d{4}-\d{2}-\d{2}$/.test(date) ? new Date(`${date}T00:00:00.000Z`) : null;
        if (!parsedDate || parsedDate.toISOString().slice(0, 10) !== date || !Number.isInteger(year) || year < 1900 || year >= currentYear || amount == null || amount <= 0) continue;
        const eventKey = `${date}:${amount}`;
        if (seenEvents.has(eventKey)) continue;
        seenEvents.add(eventKey);
        yearly.set(year, (yearly.get(year) || 0) + amount);
      }
      const records = [...yearly.entries()].sort(([a], [b]) => a - b)
        .map(([year, amount]) => ({ fiscal_year: year, annual_dps_adjusted: amount }));
      if (!records.length) return json({ error: 'not_found' }, 404);
      return json({ security: document?.security || { code }, financials: { annual: document?.financials?.annual || [] },
        dividends: { records, history_basis: 'calendar_year_observed_distributions', source: 'Yahoo Finance via yfinance' },
        valuation: document?.valuation || { history: [] }, sources: { ...(bundle.sources || {}), ...(document?.sources || {}) } });
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
      const code = irbankR2Code(requested);
      if (!code) return json({ error: 'invalid_security_code' }, 400);
      const object = await env.MARKET_DATA?.get(`market/irbank/v1/securities/${code}/overview.json`);
      if (!object) return json({ error: 'not_found' }, 404);
      const overview = await object.json();
      const security = overview.security;
      if (!security) return json({ error: 'not_found' }, 404);
      const dividend = [...(overview.dividends?.records || [])]
        .sort((a, b) => Number(b.fiscal_year || 0) - Number(a.fiscal_year || 0))[0] || null;
      const financial = [...(overview.financials?.annual || [])]
        .sort((a, b) => String(b.fiscal_year || '').localeCompare(String(a.fiscal_year || '')))[0] || null;
      const marketData = url.searchParams.get('include_market') === 'false'
        ? null
        : await latestMarketForSecurity(env, security.code);
      const marketPrice = marketData?.price ?? null;
      const marketSummary = marketData?.summary;
      return json({
        code: security.code,
        canonical_code: security.canonical_code,
        name: security.name,
        metrics: security.metrics || [],
        dividend,
        financial: financial ? {
          fiscal_year: financial.fiscal_year,
          accounting_standard: financial.accounting_standard,
          is_consolidated: financial.is_consolidated,
          data: financial,
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
    ctx.waitUntil(enqueueDailyMarketQuotes(env, _controller.scheduledTime).catch(error => console.error('Live market queue enqueue failed', error)));
    ctx.waitUntil(env.DB.prepare("DELETE FROM support_feedback WHERE created_at < strftime('%Y-%m-%dT%H:%M:%fZ', 'now', '-90 days')").run());
    ctx.waitUntil(runScheduledAnalysis(env));
  },

  async queue(batch, env) {
    const deadLetter = batch.queue === MARKET_PRICE_DLQ;
    for (const message of batch.messages) {
      const body = message.body || {};
      try {
        const symbols = Array.isArray(body.symbols) ? body.symbols : [];
        if (!body.run_id && symbols.length > LIVE_MARKET_BATCH_SIZE) {
          await splitLegacyMarketQueueMessage(env, body);
          message.ack();
          continue;
        }
        const runId = String(body.run_id || body.run_date || '');
        const runDate = String(body.run_date || runId);
        const batchIndex = Number(body.batch_index);
        const previousResult = await env.DB.prepare(`SELECT status FROM market_price_refresh_batches
          WHERE run_id = ? AND batch_index = ?`).bind(runId, batchIndex).first();
        if (previousResult) {
          const completedRun = await env.DB.prepare(`SELECT run_id, run_date, total_batches, completed_batches,
            failed_batches, processed_symbols, market_data_date, status, completed_at
            FROM market_price_refresh_runs WHERE run_id = ?`).bind(runId).first();
          if (completedRun?.status === 'complete') await publishCompletedQuoteRun(env, completedRun);
          message.ack();
          continue;
        }
        if (deadLetter) {
          await recordMarketBatchResult(env, { ...body, run_id: runId, run_date: runDate }, 'failed');
          message.ack();
          continue;
        }
        const result = await refreshLiveMarketQuotes(env, symbols, runId);
        if (result.failed > 0) throw new Error(`Yahoo quote fetch failed for ${result.failed} symbols`);
        await recordMarketBatchResult(env, { ...body, run_id: runId, run_date: runDate }, 'complete', result.processed, result.marketDataDate);
        message.ack();
      } catch (error) {
        if (deadLetter) {
          console.error('Unable to record failed market-price batch', error);
          message.retry({ delaySeconds: 300 });
        } else {
          console.error('Market-price batch failed', error);
          message.retry({ delaySeconds: 60 });
        }
      }
    }
  }
};
