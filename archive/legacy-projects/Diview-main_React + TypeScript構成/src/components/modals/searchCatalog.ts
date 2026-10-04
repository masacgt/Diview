export interface SearchCatalogItem {
  code: string;
  name: string;
  category: 'jp' | 'us' | 'fund';
  estimated_average_trading_value_20d: number | null;
  price: number | null;
  yield_rate: number | null;
}

interface SearchCatalogDocument {
  schema_version: number;
  generated_at: string;
  items: SearchCatalogItem[];
}

const CACHE_KEY = 'diview-search-catalog-v1';
const CACHE_AGE_MS = 24 * 60 * 60 * 1000;
let inMemory: SearchCatalogDocument | null = null;
let inMemorySavedAt = 0;
let pending: Promise<SearchCatalogDocument> | null = null;

export async function getSearchCatalog(): Promise<SearchCatalogDocument> {
  if (inMemory && Date.now() - inMemorySavedAt < CACHE_AGE_MS) return inMemory;
  let stale: SearchCatalogDocument | null = inMemory;
  try {
    const saved = JSON.parse(localStorage.getItem(CACHE_KEY) || 'null');
    if (saved?.data?.schema_version === 1 && Array.isArray(saved.data.items)) {
      const cached = saved.data as SearchCatalogDocument;
      stale = cached;
      if (Date.now() - saved.savedAt < CACHE_AGE_MS) {
        inMemory = cached;
        inMemorySavedAt = saved.savedAt;
        return cached;
      }
    }
  } catch { /* Storage may be disabled. */ }

  if (!pending) {
    const apiBaseUrl = import.meta.env.VITE_MARKET_API_BASE_URL || 'https://diview-irbank.omasa.workers.dev';
    pending = fetch(`${apiBaseUrl}/api/v1/search-catalog`)
      .then(async response => {
        if (!response.ok) throw new Error(`HTTP ${response.status}`);
        const data: SearchCatalogDocument = await response.json();
        if (data.schema_version !== 1 || !Array.isArray(data.items)) throw new Error('Invalid search catalog');
        inMemory = data;
        inMemorySavedAt = Date.now();
        try { localStorage.setItem(CACHE_KEY, JSON.stringify({ savedAt: inMemorySavedAt, data })); } catch { /* Storage may be full. */ }
        return data;
      })
      .catch(error => {
        if (stale) return (inMemory = stale);
        throw error;
      })
      .finally(() => { pending = null; });
  }
  return pending;
}

export function searchCatalog(items: SearchCatalogItem[], rawQuery: string, category: 'all' | 'jp' | 'us' | 'fund' | 'high-div'): SearchCatalogItem[] {
  const query = rawQuery.trim().toLocaleLowerCase();
  const length = Array.from(query).length;
  if (length < 2) return [];
  const relevance = (item: SearchCatalogItem): number => {
    const code = item.code.toLocaleLowerCase();
    const name = item.name.toLocaleLowerCase();
    if (length >= 3 && (code === query || name === query)) return 0;
    if (name === query) return 0;
    if ((length >= 3 && code.startsWith(query)) || name.startsWith(query)) return 1;
    return 2;
  };
  return items.filter(item => {
    const matchesCategory = category === 'all' ||
      (category === 'high-div' ? (item.yield_rate ?? 0) >= 3 : item.category === category);
    return matchesCategory &&
      ((length >= 3 && item.code.toLocaleLowerCase().includes(query)) || item.name.toLocaleLowerCase().includes(query));
  }).sort((a, b) =>
    relevance(a) - relevance(b) ||
    (b.estimated_average_trading_value_20d ?? -1) - (a.estimated_average_trading_value_20d ?? -1) ||
    a.code.localeCompare(b.code)
  ).slice(0, 20);
}
