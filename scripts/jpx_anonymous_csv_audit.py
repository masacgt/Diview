"""Read-only JPX portal download and monthly master comparison."""
import collections
import csv
import io
import json
import pathlib
import urllib.request
from playwright.sync_api import sync_playwright
import openpyxl

out = pathlib.Path('jpx-audit-output')
out.mkdir(exist_ok=True)
portal = 'https://clientportal.jpx.co.jp/ClientPortal/s/Issue?language=ja'
monthly = 'https://www.jpx.co.jp/markets/statistics-equities/misc/tvdivq0000001vg2-att/data_j.xlsx'
with sync_playwright() as p:
    browser = p.chromium.launch(headless=True)
    context = browser.new_context(accept_downloads=True)
    page = context.new_page()
    try:
        response = page.goto(portal, wait_until='domcontentloaded', timeout=90000)
        print('PORTAL_STATUS', response.status if response else None, flush=True)
        page.get_by_role('button', name='全件CSV取得').wait_for(timeout=90000)
        print('LOGIN_LINK_VISIBLE', page.get_by_role('link', name='ログイン', exact=True).is_visible(), flush=True)
        with page.expect_download(timeout=120000) as pending:
            page.get_by_role('button', name='全件CSV取得').click(timeout=30000)
        download = pending.value
        print('DOWNLOAD_NAME', download.suggested_filename, flush=True)
        download.save_as(str(out / 'portal.csv'))
    except Exception:
        page.screenshot(path=str(out / 'failure.png'), full_page=True)
        (out / 'failure-page.txt').write_text(page.locator('body').inner_text(), encoding='utf-8')
        raise
    finally:
        browser.close()

data = (out / 'portal.csv').read_bytes()
for encoding in ('utf-8-sig', 'cp932'):
    try:
        rows = list(csv.DictReader(io.StringIO(data.decode(encoding))))
        break
    except UnicodeDecodeError:
        pass
else:
    raise ValueError('Unrecognized CSV encoding')
if not rows or not {'銘柄コード', '商品分類', '市場区分'}.issubset(rows[0]):
    raise ValueError('Missing expected CSV columns')
def normalize(s):
    return s[:-1] if len(s) == 5 and s.endswith('0') else s
active = {normalize(row['銘柄コード']): row for row in rows if row['市場区分'] != '上場廃止'}
request = urllib.request.Request(monthly, headers={'User-Agent':'Mozilla/5.0'})
with urllib.request.urlopen(request, timeout=60) as response:
    book = openpyxl.load_workbook(io.BytesIO(response.read()), read_only=True, data_only=True)
    month_rows = list(book.active.values)
    monthly_codes = {str(r[1]).strip() for r in month_rows[1:] if r[1] is not None}
result = {'rows':len(rows), 'active_count':len(active), 'categories':dict(collections.Counter(r['商品分類'] for r in rows)), 'markets':dict(collections.Counter(r['市場区分'] for r in rows)), 'monthly_count':len(monthly_codes), 'monthly_missing_active_portal':sorted(monthly_codes - active.keys()), 'portal_missing_monthly':sorted(active.keys() - monthly_codes)}
(out / 'summary.json').write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding='utf-8')
print(json.dumps(result, ensure_ascii=False), flush=True)
