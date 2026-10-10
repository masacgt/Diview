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
