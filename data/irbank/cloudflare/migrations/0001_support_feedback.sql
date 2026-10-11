CREATE TABLE IF NOT EXISTS support_feedback (
    receipt_id TEXT PRIMARY KEY NOT NULL,
    created_at TEXT NOT NULL,
    request_hash TEXT NOT NULL,
    payload_json TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS support_feedback_created_at ON support_feedback(created_at);
