# 東証33業種マスター

`tse33_industry_master.tsv` は、JPX公式の東証上場銘柄一覧にある証券コード・33業種コード・33業種区分から生成しています。

- 取得元: <https://www.jpx.co.jp/markets/statistics-equities/misc/tvdivq0000001vg2-att/data_j.xlsx>
- JPX案内: <https://www.jpx.co.jp/markets/statistics-equities/misc/01.html>
- 同梱データ基準日: 2026年8月31日
- 収録件数: 証券コード3,892件（英字入り4文字コードを含む）、33業種すべて

マスターを更新する場合は、プロジェクトルートで `python scripts/sync_tse33_industry_master.py` を実行します。アプリは数字または英字を含む4文字の証券コードで照合し、銘柄名や別データの業種文字列を分類根拠にしません。ETFなど33業種が付与されていない銘柄はマスターに含まれず、景気区分の比率集計から除外されます。
