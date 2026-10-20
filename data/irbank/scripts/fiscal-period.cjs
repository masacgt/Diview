// Preserve the precision provided by the source; never invent a fiscal end day.
function fiscalPeriod(row) {
  const value = String(row.period_end || row.period || row.fiscal_year || '').replaceAll('/', '-');
  if (/^\d{4}$/.test(value)) return value;
  if (/^\d{4}-(0[1-9]|1[0-2])$/.test(value)) return value;
  if (/^\d{4}-\d{2}-\d{2}$/.test(value)) {
    const parsed = new Date(`${value}T00:00:00Z`);
    if (Number.isFinite(parsed.getTime()) && parsed.toISOString().slice(0, 10) === value) return value;
  }
  throw new Error(`Invalid fiscal period: ${value}`);
}

function matchingDividend(rows, row) {
  const period = fiscalPeriod(row);
  const candidates = rows.filter(item => item.event_type === 'annual_reported'
    && fiscalPeriod(item).slice(0, 4) === period.slice(0, 4));
  return candidates.find(item => fiscalPeriod(item) === period)
    || (period.length === 4 && candidates.length === 1 ? candidates[0] : undefined);
}
module.exports = { fiscalPeriod, matchingDividend };
