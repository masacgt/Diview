CREATE TABLE IF NOT EXISTS market_price_refresh_runs (
    run_id TEXT PRIMARY KEY NOT NULL,
    run_date TEXT NOT NULL,
    total_batches INTEGER NOT NULL CHECK (total_batches > 0),
    universe_size INTEGER NOT NULL CHECK (universe_size >= 0),
    enqueued_batches INTEGER NOT NULL DEFAULT 0,
    completed_batches INTEGER NOT NULL DEFAULT 0,
    failed_batches INTEGER NOT NULL DEFAULT 0,
    processed_symbols INTEGER NOT NULL DEFAULT 0,
    market_data_date TEXT,
    status TEXT NOT NULL DEFAULT 'queued',
    started_at TEXT NOT NULL,
    last_batch_at TEXT,
    completed_at TEXT,
    updated_at TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS market_price_refresh_runs_date
    ON market_price_refresh_runs(run_date DESC);

CREATE TABLE IF NOT EXISTS market_price_refresh_batches (
    run_id TEXT NOT NULL,
    batch_index INTEGER NOT NULL CHECK (batch_index >= 0),
    status TEXT NOT NULL CHECK (status IN ('complete', 'failed')),
    processed_symbols INTEGER NOT NULL DEFAULT 0,
    market_data_date TEXT,
    completed_at TEXT NOT NULL,
    PRIMARY KEY (run_id, batch_index)
);
