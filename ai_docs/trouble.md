# Trouble

- Wrangler can fail with `Invalid access token [code: 9109]` when a stale `CLOUDFLARE_API_TOKEN` environment variable overrides an existing Wrangler OAuth session. Remove that variable only from the current PowerShell process before invoking Wrangler; `wrangler whoami` then confirms whether the saved browser OAuth session works. Do not print or paste token values.
