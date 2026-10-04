const API_BASE_URL = import.meta.env.VITE_MARKET_API_BASE_URL || 'https://diview-irbank.omasa.workers.dev';

export interface IrbankOverview {
  schema_version: number;
  security: {
    code: string;
    canonical_code: string;
    edinet_code: string;
    name: string | null;
    metrics: unknown[];
  };
  organization: Record<string, unknown> | null;
  financials: {
    annual: unknown[];
    quarterly: unknown[];
    semiannual: unknown[];
  };
  dividends: { records: unknown[] } | null;
  valuation: { history: unknown[]; per_5y_avg: unknown } | null;
  segments: { records: unknown[] } | null;
  shareholders: { records: unknown[] } | null;
  sources: Record<string, unknown>;
}

export interface IrbankSeries {
  security_code?: string;
  period?: string;
  points?: unknown[];
  weeks?: unknown[];
  [key: string]: unknown;
}

async function getJson<T>(path: string, signal?: AbortSignal): Promise<T> {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    headers: { accept: 'application/json' },
    signal,
  });
  if (!response.ok) {
    throw new Error(`IRBANK data request failed (${response.status})`);
  }
  return response.json() as Promise<T>;
}

function encodeCode(code: string): string {
  return encodeURIComponent(code.trim().toUpperCase());
}

export function fetchIrbankOverview(code: string, signal?: AbortSignal): Promise<IrbankOverview> {
  return getJson(`/api/v1/irbank/securities/${encodeCode(code)}`, signal);
}

export function fetchIrbankDailyPrices(code: string, signal?: AbortSignal): Promise<IrbankSeries> {
  return getJson(`/api/v1/irbank/securities/${encodeCode(code)}/prices`, signal);
}

export function fetchIrbankWeeklyMargin(code: string, signal?: AbortSignal): Promise<IrbankSeries> {
  return getJson(`/api/v1/irbank/securities/${encodeCode(code)}/weekly-margin`, signal);
}

export function fetchIrbankCatalog(signal?: AbortSignal): Promise<{ securities: Array<{ code: string; name: string | null }> }> {
  return getJson('/api/v1/irbank/catalog', signal);
}
