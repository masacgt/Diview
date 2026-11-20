-- Use only to abort before any request has been handled by the DO-only Worker.
-- Once DO counters have accepted traffic, keep this fence and roll back only
-- to a reviewed Worker version that still uses the Durable Object limiter.
DROP TRIGGER IF EXISTS api_request_log_handoff_block_insert;
DROP TRIGGER IF EXISTS api_request_log_handoff_block_update;
DROP TRIGGER IF EXISTS api_request_log_handoff_block_delete;
