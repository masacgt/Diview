// Each HTTP request (or one scheduled security) owns its reads. Never retain
// market objects across requests: current.json and publication markers can change.
const contextKey = Symbol('marketReadContext');

export function withMarketReadCache(env) {
  return env[contextKey] ? env : {
    ...env,
    [contextKey]: { json: new Map() },
  };
}

async function sharedRead(cache, key, load) {
  if (!cache) return load();
  let pending = cache.get(key);
  if (!pending) {
    pending = Promise.resolve().then(load);
    cache.set(key, pending);
  }
  try {
    // Existing consumers sort arrays and enrich rows. Give each consumer its own
    // objects, just as independent R2 body reads did before sharing the I/O.
    return structuredClone(await pending);
  } catch (error) {
    if (cache.get(key) === pending) cache.delete(key);
    throw error;
  }
}

export function sharedMarketJson(env, key, load) {
  // Only profile/current are repeatedly read by the analysis path. Catalogs,
  // issuer indexes and history partitions can be large and are normally read
  // once; retaining and cloning those would add CPU without saving R2 I/O.
  if (!/^market\/yfinance\/v2\/securities\/[A-Z0-9]{4,5}\.T\/(?:profile|current)\.json$/.test(key)) {
    return load();
  }
  return sharedRead(env[contextKey]?.json, key, load);
}

export function sharedMarketDocuments(env, prefix, load) {
  // Keep independently loaded list arrays, preserving the existing sort and
  // enrichment behavior without cloning a complete financial/history series.
  return load();
}
