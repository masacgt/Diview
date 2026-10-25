-- Preserve historical rows; prevent nullable composite keys from creating duplicates on retry.
CREATE TRIGGER IF NOT EXISTS financial_periods_key_insert
BEFORE INSERT ON financial_periods
WHEN NEW.accounting_standard IS NULL OR trim(NEW.accounting_standard) = ''
  OR NEW.is_consolidated IS NULL
BEGIN
  SELECT RAISE(ABORT, 'financial_periods requires accounting_standard and is_consolidated');
END;
CREATE TRIGGER IF NOT EXISTS financial_periods_key_update
BEFORE UPDATE ON financial_periods
WHEN NEW.accounting_standard IS NULL OR trim(NEW.accounting_standard) = ''
  OR NEW.is_consolidated IS NULL
BEGIN
  SELECT RAISE(ABORT, 'financial_periods requires accounting_standard and is_consolidated');
END;
