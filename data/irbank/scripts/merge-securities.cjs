const fs = require('node:fs');
const path = require('node:path');
const root = path.resolve(__dirname, '..');
const rawDir = path.join(root, 'raw', 'securities_pages');
const catalogDir = path.join(root, 'catalog');
fs.mkdirSync(catalogDir, { recursive: true });
const pages = [];
for (const filename of fs.readdirSync(rawDir)) {
  if (!filename.startsWith('securities_') || !filename.endsWith('.json')) continue;
  const source = JSON.parse(fs.readFileSync(path.join(rawDir, filename), 'utf8'));
  for (const item of Array.isArray(source) ? source : [source]) {
    const data = item.data || item.result || item;
    const offset = item.offset ?? Number(filename.match(/page_(\d+)/)?.[1]);
    if (!Array.isArray(data.securities)) throw new Error(`Invalid page ${filename} offset ${offset}`);
    pages.push({ filename, offset, count: data.securities.length, securities: data.securities });
  }
}
pages.sort((a, b) => a.offset - b.offset);
const unique = new Map();
for (const page of pages) for (const security of page.securities) unique.set(security.security_code, security);
const offsets = pages.map(({ offset, count }) => ({ offset, count }));
const manifest = {
  source: 'IRBANK Connect screen_securities',
  retrieved_date: '2026-09-26',
  parameters: { as_of: 'now', listed_status: 'listed', sort_order: 'desc' },
  reported_total_count: 4211,
  returned_rows: pages.reduce((n, page) => n + page.count, 0),
  unique_security_codes: unique.size,
  pages: offsets,
  note: 'The API returned fewer than requested on some pages, and the directory contains overlapping pages. Counts do not prove full coverage.'
};
fs.writeFileSync(path.join(catalogDir, 'securities.json'), JSON.stringify([...unique.values()], null, 2) + '\n');
fs.writeFileSync(path.join(catalogDir, 'manifest.json'), JSON.stringify(manifest, null, 2) + '\n');
console.log(JSON.stringify({ returned_rows: manifest.returned_rows, unique_security_codes: unique.size, pages: offsets.length, short_pages: offsets.filter(p => p.offset >= 1380 && p.offset + p.count < Math.min(p.offset + 100, 4211)) }));
