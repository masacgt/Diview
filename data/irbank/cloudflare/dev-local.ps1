$ErrorActionPreference = "Stop"
$config = Join-Path $PSScriptRoot "wrangler.dev.toml"
$schema = Join-Path $PSScriptRoot "..\organized\d1\schema.sql"

# Use the saved Wrangler OAuth login instead of the stale API-token environment variable.
Remove-Item Env:CLOUDFLARE_API_TOKEN -ErrorAction SilentlyContinue

Push-Location $PSScriptRoot
try {
    npx wrangler d1 execute diview-irbank-dev-local --local --config $config --file $schema
    if ($LASTEXITCODE -ne 0) {
        throw "Could not initialize the local D1 schema."
    }

    npx wrangler dev --config $config
    if ($LASTEXITCODE -ne 0) {
        throw "The local Worker stopped with an error."
    }
}
finally {
    Pop-Location
}
