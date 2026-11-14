-- Temporary cutover fence. Apply only during the reviewed DO cutover.
-- Reads remain available so each Durable Object can import its active counter.
-- Do not add this file to the automatic D1 migration sequence.
CREATE TRIGGER IF NOT EXISTS api_request_log_handoff_block_insert
BEFORE INSERT ON api_request_log
BEGIN
  SELECT RAISE(ABORT, 'rate_limit_handoff_fenced');
END;

CREATE TRIGGER IF NOT EXISTS api_request_log_handoff_block_update
BEFORE UPDATE ON api_request_log
BEGIN
  SELECT RAISE(ABORT, 'rate_limit_handoff_fenced');
END;

CREATE TRIGGER IF NOT EXISTS api_request_log_handoff_block_delete
BEFORE DELETE ON api_request_log
BEGIN
  SELECT RAISE(ABORT, 'rate_limit_handoff_fenced');
END;
