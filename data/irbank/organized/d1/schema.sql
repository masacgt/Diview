PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS securities (
  security_code TEXT PRIMARY KEY,
  security_code_display TEXT NOT NULL,
  edinet_code TEXT NOT NULL,
  name TEXT NOT NULL,
  metrics_json TEXT NOT NULL DEFAULT '[]' CHECK (json_valid(metrics_json))
);
CREATE INDEX IF NOT EXISTS idx_securities_edinet_code ON securities(edinet_code);

CREATE TABLE IF NOT EXISTS financial_periods (
  security_code TEXT NOT NULL REFERENCES securities(security_code),
  period_type TEXT NOT NULL CHECK (period_type IN ('annual','quarterly','semiannual')),
  fiscal_year TEXT NOT NULL,
  accounting_standard TEXT,
  is_consolidated INTEGER,
  data_json TEXT NOT NULL CHECK (json_valid(data_json)),
  PRIMARY KEY (security_code, period_type, fiscal_year, accounting_standard, is_consolidated)
);
CREATE INDEX IF NOT EXISTS idx_financial_periods_lookup ON financial_periods(security_code, period_type, fiscal_year);

CREATE TABLE IF NOT EXISTS dividend_events (
  security_code TEXT NOT NULL REFERENCES securities(security_code),
  fiscal_year INTEGER NOT NULL,
  event_type TEXT NOT NULL,
  annual_dps REAL,
  annual_dps_adjusted REAL,
  record_date TEXT,
  final_rights_date TEXT,
  date_status TEXT NOT NULL,
  data_json TEXT NOT NULL CHECK (json_valid(data_json)),
  PRIMARY KEY (security_code, fiscal_year, event_type)
);
CREATE INDEX IF NOT EXISTS idx_dividend_events_rights_date ON dividend_events(final_rights_date, security_code);

CREATE TABLE IF NOT EXISTS diagnosis_bands (
  min_score INTEGER NOT NULL,
  max_score INTEGER NOT NULL,
  grade TEXT NOT NULL,
  title TEXT NOT NULL,
  comment TEXT NOT NULL,
  PRIMARY KEY (min_score, max_score)
);

CREATE TABLE IF NOT EXISTS valuation_scenarios (
  id INTEGER PRIMARY KEY,
  per_max REAL,
  pbr_max REAL,
  title TEXT NOT NULL,
  badge TEXT NOT NULL,
  comment TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS app_definition_documents (
  definition_key TEXT PRIMARY KEY,
  value_json TEXT NOT NULL CHECK (json_valid(value_json))
);

CREATE TABLE IF NOT EXISTS app_definition_audit (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  definition_key TEXT NOT NULL,
  version INTEGER NOT NULL,
  content_hash TEXT NOT NULL,
  updated_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now')),
  metadata_json TEXT NOT NULL DEFAULT '{}' CHECK (json_valid(metadata_json))
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_definition_audit_unique
  ON app_definition_audit(definition_key, version, content_hash);

CREATE TABLE IF NOT EXISTS stock_analysis_history (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  security_code TEXT NOT NULL REFERENCES securities(security_code),
  observed_at TEXT NOT NULL,
  market_date TEXT,
  definition_version INTEGER NOT NULL,
  overall_score INTEGER,
  overall_grade TEXT,
  buy_zone_status TEXT,
  buy_zone_ratio REAL,
  anomaly_flags_json TEXT NOT NULL DEFAULT '[]' CHECK (json_valid(anomaly_flags_json)),
  metrics_json TEXT NOT NULL DEFAULT '{}' CHECK (json_valid(metrics_json)),
  analysis_json TEXT NOT NULL CHECK (json_valid(analysis_json))
);
CREATE INDEX IF NOT EXISTS idx_stock_analysis_history_lookup
  ON stock_analysis_history(security_code, observed_at DESC);

CREATE TABLE IF NOT EXISTS stock_analysis_state (
  security_code TEXT PRIMARY KEY REFERENCES securities(security_code),
  last_observed_at TEXT NOT NULL,
  market_date TEXT,
  definition_version INTEGER NOT NULL,
  overall_score INTEGER,
  overall_grade TEXT,
  buy_zone_status TEXT,
  buy_zone_ratio REAL,
  anomaly_flags_json TEXT NOT NULL DEFAULT '[]' CHECK (json_valid(anomaly_flags_json)),
  analysis_json TEXT NOT NULL CHECK (json_valid(analysis_json))
);

CREATE TABLE IF NOT EXISTS notification_events (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  security_code TEXT NOT NULL REFERENCES securities(security_code),
  event_type TEXT NOT NULL,
  from_status TEXT,
  to_status TEXT,
  occurred_at TEXT NOT NULL,
  payload_json TEXT NOT NULL DEFAULT '{}' CHECK (json_valid(payload_json)),
  delivered_at TEXT
);
CREATE INDEX IF NOT EXISTS idx_notification_events_pending
  ON notification_events(delivered_at, occurred_at DESC);

CREATE TABLE IF NOT EXISTS watchlist_targets (
  client_id TEXT NOT NULL,
  security_code TEXT NOT NULL REFERENCES securities(security_code),
  target_yield REAL NOT NULL,
  alert_enabled INTEGER NOT NULL DEFAULT 1,
  updated_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now')),
  PRIMARY KEY (client_id, security_code)
);
CREATE INDEX IF NOT EXISTS idx_watchlist_targets_security
  ON watchlist_targets(security_code, alert_enabled);

CREATE TABLE IF NOT EXISTS clients (
  client_id TEXT PRIMARY KEY,
  client_secret_hash TEXT NOT NULL,
  created_at TEXT NOT NULL,
  last_seen_at TEXT NOT NULL,
  disabled_at TEXT
);

CREATE TABLE IF NOT EXISTS push_devices (
  client_id TEXT NOT NULL REFERENCES clients(client_id) ON DELETE CASCADE,
  device_token TEXT NOT NULL,
  platform TEXT NOT NULL CHECK (platform IN ('android','ios','web')),
  enabled INTEGER NOT NULL DEFAULT 1,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  PRIMARY KEY (client_id, device_token)
);
CREATE INDEX IF NOT EXISTS idx_push_devices_enabled ON push_devices(client_id, enabled);

CREATE TABLE IF NOT EXISTS scheduled_run_log (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  run_type TEXT NOT NULL,
  started_at TEXT NOT NULL,
  finished_at TEXT,
  status TEXT NOT NULL,
  processed INTEGER NOT NULL DEFAULT 0,
  failed INTEGER NOT NULL DEFAULT 0,
  metadata_json TEXT NOT NULL DEFAULT '{}' CHECK (json_valid(metadata_json))
);
CREATE INDEX IF NOT EXISTS idx_scheduled_run_log_started ON scheduled_run_log(started_at DESC);

CREATE TABLE IF NOT EXISTS backend_state (
  state_key TEXT PRIMARY KEY,
  value_json TEXT NOT NULL CHECK (json_valid(value_json)),
  updated_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS api_request_log (
  bucket_key TEXT PRIMARY KEY,
  request_count INTEGER NOT NULL DEFAULT 0,
  window_started_at TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_notification_events_client_pending
  ON notification_events(json_extract(payload_json, '$.client_id'), delivered_at, occurred_at DESC);
