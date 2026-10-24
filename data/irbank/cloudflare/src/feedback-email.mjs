const categories = { feature: '機能のご要望', bug: '不具合報告', stock: '銘柄について', other: 'その他', question: 'お問い合わせ' };

export function feedbackEmail(input, createdAt, env) {
  return {
    from: env.FEEDBACK_EMAIL_FROM,
    to: env.FEEDBACK_EMAIL_TO,
    ...(input.email ? { replyTo: input.email } : {}),
    subject: `[Diview] ${categories[input.category] || 'お問い合わせ'} / ${input.requestId}`,
    text: [
      'Diviewに新しいお問い合わせが届きました。',
      `受付番号: ${input.requestId}`, `受付日時 (UTC): ${createdAt}`,
      `カテゴリ: ${categories[input.category] || input.category}`,
      `名前: ${input.name || '未入力'}`, `返信先: ${input.email || '未入力'}`,
      '', 'お問い合わせ内容:', input.message,
      ...(input.diagnostics ? ['', `アプリ版: ${input.diagnostics.appVersion}`, `Android SDK: ${input.diagnostics.androidSdk}`] : []),
    ].join('\n'),
  };
}

// A lease prevents concurrent requests/cron runs from sending the same receipt.
// Delivery is at-least-once: a crash after send but before recording sent_at can duplicate mail.
export async function deliverFeedbackEmail(env, receiptId, now = new Date()) {
  if (!env.FEEDBACK_EMAIL || !env.FEEDBACK_EMAIL_FROM || !env.FEEDBACK_EMAIL_TO) return;
  const token = crypto.randomUUID();
  const retryAt = new Date(now.getTime() + 10 * 60_000).toISOString();
  const claimed = await env.DB.prepare(`UPDATE support_feedback_email
    SET lease_token = ?, next_attempt_at = ?, attempts = attempts + 1
    WHERE receipt_id = ? AND sent_at IS NULL AND next_attempt_at <= ?
    RETURNING receipt_id`).bind(token, retryAt, receiptId, now.toISOString()).first();
  if (!claimed) return;
  try {
    const row = await env.DB.prepare('SELECT payload_json, created_at FROM support_feedback WHERE receipt_id = ?')
      .bind(receiptId).first();
    if (!row) return;
    await env.FEEDBACK_EMAIL.send(feedbackEmail(JSON.parse(row.payload_json), row.created_at, env));
    await env.DB.prepare(`UPDATE support_feedback_email SET sent_at = ?, lease_token = NULL, last_error = NULL
      WHERE receipt_id = ? AND lease_token = ?`).bind(new Date().toISOString(), receiptId, token).run();
  } catch {
    // Never log the message, reply address, or provider errors that might contain them.
    await env.DB.prepare(`UPDATE support_feedback_email SET lease_token = NULL, last_error = 'send_or_record_failed'
      WHERE receipt_id = ? AND lease_token = ?`).bind(receiptId, token).run();
    console.error('feedback_email_delivery_failed');
  }
}

export async function retryFeedbackEmails(env) {
  if (!env.FEEDBACK_EMAIL || !env.FEEDBACK_EMAIL_FROM || !env.FEEDBACK_EMAIL_TO) return;
  const pending = await env.DB.prepare(`SELECT receipt_id FROM support_feedback_email
    WHERE sent_at IS NULL AND next_attempt_at <= ? ORDER BY next_attempt_at LIMIT 20`)
    .bind(new Date().toISOString()).all();
  for (const row of pending.results || []) await deliverFeedbackEmail(env, row.receipt_id);
}

export async function safelyDeliverFeedbackEmail(env, receiptId) {
  try { await deliverFeedbackEmail(env, receiptId); }
  catch { console.error('feedback_email_storage_failed'); }
}
