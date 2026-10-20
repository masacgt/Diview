-- Additive operational schema; no seeds, updates, or repairs of existing data.
-- Market-data tables remain in organized/d1/schema.sql.
-- No FK to securities: the importer uses INSERT OR REPLACE on that table.
CREATE TABLE IF NOT EXISTS clients (
    client_id TEXT PRIMARY KEY NOT NULL,
    client_secret_hash TEXT NOT NULL,
    created_at TEXT NOT NULL,
    last_seen_at TEXT NOT NULL,
    disabled_at TEXT
);
CREATE TABLE IF NOT EXISTS push_devices (
    client_id TEXT NOT NULL,
    device_token TEXT NOT NULL,
    platform TEXT NOT NULL,
    enabled INTEGER NOT NULL DEFAULT 1,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    PRIMARY KEY (client_id, device_token)
);
CREATE INDEX IF NOT EXISTS idx_push_devices_enabled ON push_devices(client_id, enabled);
CREATE TABLE IF NOT EXISTS watchlist_targets (
    client_id TEXT NOT NULL,
    security_code TEXT NOT NULL,
    target_yield REAL,
    alert_enabled INTEGER NOT NULL DEFAULT 1,
    updated_at TEXT NOT NULL,
    PRIMARY KEY (client_id, security_code)
);
CREATE INDEX IF NOT EXISTS idx_watchlist_targets_security ON watchlist_targets(security_code, alert_enabled);
CREATE TABLE IF NOT EXISTS api_request_log (
    bucket_key TEXT PRIMARY KEY NOT NULL,
    request_count INTEGER NOT NULL DEFAULT 0,
    window_started_at TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_api_request_log_window ON api_request_log(window_started_at);
CREATE TABLE IF NOT EXISTS stock_analysis_state (
    security_code TEXT PRIMARY KEY NOT NULL,
    last_observed_at TEXT NOT NULL,
    market_date TEXT,
    definition_version INTEGER,
    overall_score REAL,
    overall_grade TEXT,
    buy_zone_status TEXT,
    buy_zone_ratio REAL,
    anomaly_flags_json TEXT NOT NULL DEFAULT '[]',
    analysis_json TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS stock_analysis_history (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    security_code TEXT NOT NULL,
    observed_at TEXT NOT NULL,
    market_date TEXT,
    definition_version INTEGER,
    overall_score REAL,
    overall_grade TEXT,
    buy_zone_status TEXT,
    buy_zone_ratio REAL,
    anomaly_flags_json TEXT NOT NULL DEFAULT '[]',
    metrics_json TEXT NOT NULL DEFAULT '{}',
    analysis_json TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_analysis_history_security_time ON stock_analysis_history(security_code, observed_at DESC);
CREATE INDEX IF NOT EXISTS idx_analysis_history_time ON stock_analysis_history(observed_at);
-- IDs must not be reused after retention deletes: delivery records reference them.
CREATE TABLE IF NOT EXISTS notification_events (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    security_code TEXT NOT NULL,
    event_type TEXT NOT NULL,
    from_status TEXT,
    to_status TEXT,
    occurred_at TEXT NOT NULL,
    payload_json TEXT NOT NULL,
    delivered_at TEXT
);
CREATE INDEX IF NOT EXISTS idx_notification_events_pending ON notification_events(delivered_at, occurred_at);
CREATE INDEX IF NOT EXISTS idx_notification_events_security ON notification_events(security_code, event_type, occurred_at DESC);
-- Deliberately no cascading deletion: migration must preserve existing deliveries.
CREATE TABLE IF NOT EXISTS notification_deliveries (
    event_id INTEGER NOT NULL,
    device_token TEXT NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    delivered_at TEXT,
    terminal_at TEXT,
    last_error TEXT,
    updated_at TEXT NOT NULL,
    PRIMARY KEY (event_id, device_token)
);
CREATE TABLE IF NOT EXISTS scheduled_run_log (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    run_type TEXT NOT NULL,
    started_at TEXT NOT NULL,
    finished_at TEXT,
    status TEXT NOT NULL,
    processed INTEGER NOT NULL DEFAULT 0,
    failed INTEGER NOT NULL DEFAULT 0,
    metadata_json TEXT NOT NULL DEFAULT '{}'
);
CREATE INDEX IF NOT EXISTS idx_scheduled_run_log_started ON scheduled_run_log(started_at);
-- value_json may contain a raw source tag; the Worker does not JSON-encode it.
CREATE TABLE IF NOT EXISTS backend_state (
    state_key TEXT PRIMARY KEY NOT NULL,
    value_json TEXT NOT NULL,
    updated_at TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS app_definition_documents (
    definition_key TEXT PRIMARY KEY NOT NULL,
    value_json TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS app_definition_audit (
    definition_key TEXT NOT NULL,
    version INTEGER NOT NULL,
    content_hash TEXT NOT NULL,
    updated_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
    metadata_json TEXT NOT NULL DEFAULT '{}',
    PRIMARY KEY (definition_key, version, content_hash)
);
CREATE INDEX IF NOT EXISTS idx_app_definition_audit_updated ON app_definition_audit(updated_at DESC);
CREATE TABLE IF NOT EXISTS diagnosis_bands (
    min_score REAL PRIMARY KEY NOT NULL,
    max_score REAL NOT NULL,
    grade TEXT NOT NULL,
    title TEXT NOT NULL,
    comment TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS valuation_scenarios (
    id INTEGER PRIMARY KEY,
    per_max REAL,
    pbr_max REAL,
    title TEXT NOT NULL,
    badge TEXT,
    comment TEXT NOT NULL
);
