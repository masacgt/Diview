# 問い合わせメール通知

新規問い合わせのD1保存時に、SQLトリガーで `support_feedback_email` に通知待ちを同じトランザクションで記録する。受付後のバックグラウンド処理でCloudflareの `send_email` バインディングから管理者へ通知する。AndroidのAPI契約と受付番号は変更しない。

- 送信元・通知先は `wrangler.toml` の `FEEDBACK_EMAIL_FROM` / `FEEDBACK_EMAIL_TO`。バインディングでも通知先を1件に制限する。
- 本文はプレーンテキスト。受付番号、受付日時、カテゴリ、名前、返信先、問い合わせ本文、同意された診断情報を含む。入力された返信先は `replyTo` とし、宛先や送信元に使わない。
- 失敗後は10分の再試行待ち時間を設ける。既存の毎時17分の定期処理で最大20件ずつ再試行する。同じ受付番号の再送でも通知待ちを重複作成しない。
- 送信処理には10分のリースを使用する。送信成功直後に処理が停止した場合などは再送による重複があり得るため、受付番号で識別する。`sent_at` はCloudflareへの送信成功の記録であり、受信箱への到達を保証しない。
- 過去の問い合わせは移行時に通知しない。問い合わせ削除時には通知記録も削除し、既存の90日保持に従う。
- メール障害を問い合わせ受付エラーにしない。本文やメールアドレスをエラーログに出さない。

## 初期設定・公開

Cloudflareで送信元ドメインのEmail Routingを有効化し、通知先アドレスが確認済みであることを確認する。既存メール用DNSがある場合は変更前に影響を確認する。

Worker公開前に `migrations/0006_feedback_email.sql` をD1へ適用する。再実行可能で、既存問い合わせは変更しない。

```powershell
Set-Location C:\Codex\Diview\data\irbank\cloudflare
node --test test/*.test.mjs
npx.cmd wrangler d1 execute diview-irbank --remote --file migrations/0006_feedback_email.sql
npx.cmd wrangler versions upload
# 上の出力で得たバージョンIDを指定する。既存の定期トリガーを保持する。
npx.cmd wrangler versions deploy <バージョンID>@100% --yes
```

監視は `support_feedback_email` の `sent_at`、`attempts`、`next_attempt_at`、`last_error` を確認する。未送信が続く場合はドメイン・通知先の認証とCloudflare Email Serviceの稼働状況を確認する。

サーバーのみの変更なのでAndroidの再ビルド・実機／エミュレータへの再インストールは不要。

## Gitへの記録

並行作業がある場合、下記の対話的選択ではメール通知関連の差分だけを選ぶ。特に `src/index.js` の財務期間処理や `test/routes.test.mjs` の財務テスト、`ai_docs/todo.md` の他タスクは含めない。

```powershell
Set-Location C:\Codex\Diview
git diff --cached --quiet
if ($LASTEXITCODE -ne 0) { throw '既にステージされた変更があります。先に内容を確認してください。' }
git add -p -- ai_docs/architecture.md ai_docs/todo.md data/irbank/cloudflare/README.md data/irbank/cloudflare/src/index.js data/irbank/cloudflare/test/routes.test.mjs data/irbank/cloudflare/wrangler.toml
git add -- data/irbank/cloudflare/docs/feedback-email.md data/irbank/cloudflare/migrations/0006_feedback_email.sql data/irbank/cloudflare/src/feedback-email.mjs data/irbank/cloudflare/test/feedback-email.test.mjs
git diff --cached --check
git diff --cached
# 上の差分が今回の通知変更だけであることを確認してから実行する。
git commit -m "Add support feedback email notifications with durable retries"
git push origin HEAD
```

公式仕様: https://developers.cloudflare.com/email-service/api/send-emails/workers-api/
