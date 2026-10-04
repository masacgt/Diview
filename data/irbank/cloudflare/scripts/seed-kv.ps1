$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$items = @(
    @{ Key = 'diagnosis-definitions:v1'; File = 'diagnosis-definitions.json' },
    @{ Key = 'valuation-scenarios:v1'; File = 'valuation-scenarios.json' },
    @{ Key = 'stock-analysis-definitions:v1'; File = 'stock-analysis-definitions.json' },
    @{ Key = 'watchlist-analysis-definitions:v1'; File = 'watchlist-analysis-definitions.json' },
    @{ Key = 'portfolio-analysis-definitions:v1'; File = 'portfolio-analysis-definitions.json' },
    @{ Key = 'backend-config:v1'; File = 'backend-config.json' }
)

foreach ($item in $items) {
    $path = Join-Path $root ("kv\\" + $item.File)
    npx wrangler kv key put $item.Key --binding APP_DEFINITIONS --remote --path $path
    if ($LASTEXITCODE -ne 0) { throw "KV seed failed: $($item.Key)" }
}
