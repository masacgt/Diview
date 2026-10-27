// Each HTTP request (or one scheduled security) owns its reads. Never retain
// market objects across requests: current.json and publication markers can change.
const contextKey = Symbol('marketReadContext');

export function withMarketReadCache(env) {
  return env[contextKey] ? env : {
    ...env,
    [contextKey]: { json: new Map(), documents: new Map() },
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
  return sharedRead(env[contextKey]?.json, key, load);
}

export function sharedMarketDocuments(env, prefix, load) {
  return sharedRead(env[contextKey]?.documents, prefix, load);
}
