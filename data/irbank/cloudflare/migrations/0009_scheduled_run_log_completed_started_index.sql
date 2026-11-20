CREATE INDEX IF NOT EXISTS idx_scheduled_run_log_completed_started_id
ON scheduled_run_log(datetime(started_at), id)
WHERE finished_at IS NOT NULL
  AND status IN ('success', 'failed', 'partial', 'superseded');
