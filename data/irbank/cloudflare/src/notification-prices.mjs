const MAX_CODES = 10;
const MAX_BODY_BYTES = 4096;
const CATALOG_GENERATION_PATTERN = /^\d{4}-\d{2}-\d{2}(?:-[0-9a-f]{32})?$/;

function validDate(value) {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(String(value || ''))) return false;
  const date = new Date(`${value}T00:00:00.000Z`);
  return Number.isFinite(date.getTime()) && date.toISOString().slice(0, 10) === value;
}

function jstDate(nowMs) {
  const parts = Object.fromEntries(new Intl.DateTimeFormat('en', {
    timeZone: 'Asia/Tokyo', year: 'numeric', month: '2-digit', day: '2-digit',
  }).formatToParts(new Date(nowMs)).map(part => [part.type, part.value]));
  return `${parts.year}-${parts.month}-${parts.day}`;
}

function positivePrice(value) {
  return typeof value === 'number' && Number.isFinite(value) && value > 0 ? value : null;
}

function validGeneration(value) {
  return CATALOG_GENERATION_PATTERN.test(value)
    && validDate(value.slice(0, 10));
}

function unavailable(helpers) {
  return helpers.json({ error: 'notification_prices_unavailable' }, 503);
}

function generationMismatch(helpers) {
  return helpers.json({ error: 'catalog_generation_mismatch' }, 409);
}

class CatalogGenerationMismatchError extends Error {}

async function readV2Price(env, code, publication, requestedDate, helpers) {
  const current = await helpers.readSecurity(env, code);
  const price = positivePrice(current?.price?.close);
  const quoteDate = String(current?.price?.date || '');
  if (price === null || !validDate(quoteDate)
    || quoteDate > publication.market_data_date || quoteDate > requestedDate) return null;
  const currentUpdatedAt = Date.parse(String(current?.updated_at || ''));
  const publicationCompletedAt = Date.parse(String(publication.last_completed_at || ''));
  if (!Number.isFinite(currentUpdatedAt) || !Number.isFinite(publicationCompletedAt)
    || currentUpdatedAt > publicationCompletedAt) {
    throw new CatalogGenerationMismatchError('security current record is newer than the published catalog generation');
  }
  const sourceLabel = typeof current.source === 'string' ? current.source.trim() : '';
  return {
    code,
    price,
    quote_date: quoteDate,
    ...(sourceLabel ? { attribution: { source_label: sourceLabel } } : {}),
  };
}

export async function notificationPricesResponse(url, env, helpers, { cacheControl = 'public, max-age=300' } = {}) {
  const rawCodes = String(url.searchParams.get('codes') || '');
  const codes = [...new Set(rawCodes.split(',').map(helpers.normalizeCode))];
  const requestedDate = String(url.searchParams.get('market_data_date') || '');
  const requestedRunId = String(url.searchParams.get('run_id') || '');
  if (!rawCodes || !codes.length || codes.length > MAX_CODES || codes.some(code => !code)
    || !validDate(requestedDate) || requestedDate > jstDate(Date.now())
    || (requestedRunId && !validGeneration(requestedRunId))) {
    return helpers.json({ error: 'invalid_request' }, 400);
  }
  if (!env.MARKET_DATA) return helpers.json({ error: 'market_data_unavailable' }, 503);

  try {
    const publication = await helpers.readPublication(env);
    const generation = String(publication?.generation || '');
    const marketDataDate = String(publication?.market_data_date || '');
    const lastCompletedAt = String(publication?.last_completed_at || '');
    if (!validGeneration(generation) || !validDate(marketDataDate)
      || generation.slice(0, 10) !== marketDataDate
      || !Number.isFinite(Date.parse(lastCompletedAt))) {
      return unavailable(helpers);
    }
    if (marketDataDate > requestedDate) return generationMismatch(helpers);
    if (requestedRunId && requestedRunId !== generation) return generationMismatch(helpers);

    const items = [];
    for (const code of codes) {
      const item = await readV2Price(env, code, publication, requestedDate, helpers);
      if (item) items.push(item);
    }

    const latestPublication = await helpers.readPublication(env);
    if (latestPublication?.generation !== generation
      || latestPublication?.market_data_date !== marketDataDate
      || latestPublication?.last_completed_at !== lastCompletedAt) {
      return generationMismatch(helpers);
    }

    const response = helpers.json({
      run_id: generation,
      market_data_date: marketDataDate,
      complete: true,
      items,
    });
    response.headers.set('cache-control', cacheControl);
    return response;
  } catch (error) {
    if (error instanceof CatalogGenerationMismatchError) return generationMismatch(helpers);
    return unavailable(helpers);
  }
}

async function readBoundedJsonBody(request, maxBytes) {
  const contentLengthHeader = request.headers.get('content-length');
  const contentLength = Number(contentLengthHeader);
  if (contentLengthHeader && Number.isFinite(contentLength) && contentLength > maxBytes) return null;
  if (!request.headers.get('content-type')?.toLowerCase().startsWith('application/json') || !request.body) return null;

  const reader = request.body.getReader();
  const chunks = [];
  let length = 0;
  try {
    while (true) {
      const { value, done } = await reader.read();
      if (done) break;
      length += value.byteLength;
      if (length > maxBytes) {
        await reader.cancel();
        return null;
      }
      chunks.push(value);
    }
  } finally {
    reader.releaseLock();
  }
  const bytes = new Uint8Array(length);
  let offset = 0;
  for (const chunk of chunks) {
    bytes.set(chunk, offset);
    offset += chunk.byteLength;
  }
  try { return JSON.parse(new TextDecoder().decode(bytes)); } catch { return null; }
}

export async function notificationPricesPostResponse(request, env, helpers) {
  const body = await readBoundedJsonBody(request, MAX_BODY_BYTES);
  if (!body || typeof body !== 'object' || Array.isArray(body)
    || !Array.isArray(body.codes) || body.codes.length < 1 || body.codes.length > MAX_CODES
    || typeof body.market_data_date !== 'string') {
    return helpers.json({ error: 'invalid_request' }, 400);
  }
  const codes = [...new Set(body.codes.map(value => typeof value === 'string' ? helpers.normalizeCode(value) : null))];
  if (codes.some(code => !code) || codes.length > MAX_CODES) return helpers.json({ error: 'invalid_request' }, 400);
  if (body.run_id !== undefined && typeof body.run_id !== 'string') return helpers.json({ error: 'invalid_request' }, 400);

  const query = new URL('https://internal.invalid/api/v1/notification-prices');
  query.searchParams.set('codes', codes.join(','));
  query.searchParams.set('market_data_date', body.market_data_date);
  if (body.run_id) query.searchParams.set('run_id', body.run_id);
  return notificationPricesResponse(query, env, helpers, { cacheControl: 'private, no-store' });
}
