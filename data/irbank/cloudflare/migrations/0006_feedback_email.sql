CREATE TABLE IF NOT EXISTS support_feedback_email (
    receipt_id TEXT PRIMARY KEY NOT NULL REFERENCES support_feedback(receipt_id) ON DELETE CASCADE,
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TEXT NOT NULL,
    lease_token TEXT,
    sent_at TEXT,
    last_error TEXT
);
CREATE INDEX IF NOT EXISTS support_feedback_email_pending
    ON support_feedback_email(sent_at, next_attempt_at);

-- Only new receipts create notifications; migration does not email historical feedback.
CREATE TRIGGER IF NOT EXISTS support_feedback_email_insert
AFTER INSERT ON support_feedback
BEGIN
    INSERT INTO support_feedback_email(receipt_id, next_attempt_at)
    VALUES (NEW.receipt_id, NEW.created_at);
END;

CREATE TRIGGER IF NOT EXISTS support_feedback_email_delete
AFTER DELETE ON support_feedback
BEGIN
    DELETE FROM support_feedback_email WHERE receipt_id = OLD.receipt_id;
END;
