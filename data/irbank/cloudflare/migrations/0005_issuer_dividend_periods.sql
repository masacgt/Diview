-- Add a lossless fiscal-period store without replacing the legacy yearly table.
CREATE TABLE IF NOT EXISTS issuer_dividend_periods (
    security_code TEXT NOT NULL,
    fiscal_period TEXT NOT NULL,
    event_type TEXT NOT NULL,
    data_json TEXT NOT NULL CHECK (json_valid(data_json)),
    PRIMARY KEY (security_code, fiscal_period, event_type)
);
INSERT OR IGNORE INTO issuer_dividend_periods(security_code, fiscal_period, event_type, data_json)
SELECT security_code,
       replace(COALESCE(NULLIF(json_extract(data_json, '$.period_end'), ''),
                        NULLIF(json_extract(data_json, '$.period'), ''),
                        CAST(fiscal_year AS TEXT)), '/', '-'),
       event_type, data_json
FROM dividend_events;
