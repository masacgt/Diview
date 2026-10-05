const categories = new Set(['feature', 'bug', 'stock', 'other', 'question']);

export function validateFeedback(input) {
  if (!input || typeof input !== 'object' || Array.isArray(input)) return null;
  const requestId = typeof input.requestId === 'string' ? input.requestId : '';
  const category = input.category;
  const name = typeof input.name === 'string' ? input.name.trim() : '';
  const email = typeof input.email === 'string' ? input.email.trim() : '';
  const message = typeof input.message === 'string' ? input.message.trim() : '';
  if (!/^[a-f0-9]{8}-[a-f0-9]{4}-4[a-f0-9]{3}-[89ab][a-f0-9]{3}-[a-f0-9]{12}$/.test(requestId) ||
      !categories.has(category) || name.length > 200 || email.length > 254 ||
      (email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) || message.length < 1 || message.length > 20_000 ||
      /[\r\n\x00]/.test(name + email) || message.includes('\x00')) return null;
  const diagnostics = input.diagnostics;
  const safeDiagnostics = diagnostics && typeof diagnostics === 'object' &&
      Number.isInteger(diagnostics.androidSdk) && diagnostics.androidSdk >= 26 && diagnostics.androidSdk <= 100
    ? { androidSdk: diagnostics.androidSdk, appVersion: String(diagnostics.appVersion || '').slice(0, 30) } : null;
  return { requestId, category, name, email, message, diagnostics: safeDiagnostics };
}

export async function readFeedback(request) {
  if (!/^application\/json(?:;|$)/i.test(request.headers.get('content-type') || '')) return null;
  const reader = request.body?.getReader();
  if (!reader) return null;
  const chunks = [];
  let length = 0;
  try {
    for (;;) {
      const { value, done } = await reader.read();
      if (done) break;
      length += value.length;
      if (length > 100_000) { await reader.cancel(); return null; }
      chunks.push(value);
    }
    const bytes = new Uint8Array(length);
    let offset = 0;
    for (const chunk of chunks) { bytes.set(chunk, offset); offset += chunk.length; }
    return validateFeedback(JSON.parse(new TextDecoder('utf-8', { fatal: true }).decode(bytes)));
  } catch { return null; } finally { reader.releaseLock(); }
}

export async function saveFeedback(db, input) {
  const payload = JSON.stringify(input);
  const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(payload));
  const hash = Array.from(new Uint8Array(digest), b => b.toString(16).padStart(2, '0')).join('');
  await db.prepare(`INSERT INTO support_feedback(receipt_id, created_at, request_hash, payload_json)
    VALUES (?, ?, ?, ?) ON CONFLICT(receipt_id) DO NOTHING`)
    .bind(input.requestId, new Date().toISOString(), hash, payload).run();
  const saved = await db.prepare('SELECT request_hash FROM support_feedback WHERE receipt_id = ?').bind(input.requestId).first();
  if (!saved) throw new Error('feedback_not_saved');
  return saved.request_hash === hash;
}
