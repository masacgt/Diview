# IRBANK → Cloudflare D1

このディレクトリは、`data/irbank/organized/d1` に生成済みのIRBANKデータをCloudflare D1へ登録するための設定です。

## 1. D1データベースを作成

```powershell
npx wrangler d1 create diview-irbank
```

表示された `database_id` を `wrangler.toml` の `REPLACE_WITH_D1_DATABASE_ID` に設定します。

## 2. スキーマを作成

```powershell
npx wrangler d1 execute diview-irbank --remote --file ..\organized\d1\schema.sql
```

## 3. インポート用SQLを生成

```powershell
node ..\scripts\build-d1-import.cjs
```

`import` フォルダに分割SQLが生成されます。

## 4. D1へ投入

```powershell
powershell -ExecutionPolicy Bypass -File .\import-d1.ps1
```

途中で失敗しても、成功済みのファイルは `import-state.json` に記録され、再実行時に続きから処理します。

