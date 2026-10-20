param(
    [Parameter(Mandatory = $true)]
    [string]$ImportDir
)
$ErrorActionPreference = 'Stop'

$Here = Split-Path -Parent $MyInvocation.MyCommand.Path
$ImportDir = (Resolve-Path -LiteralPath $ImportDir).Path
$StatePath = Join-Path $ImportDir 'import-state-v2.json'
$DatabaseName = 'diview-irbank'

$ManifestPath = Join-Path $ImportDir 'manifest.json'
if (!(Test-Path -LiteralPath $ManifestPath)) {
    throw 'Validated manifest missing. Regenerate SQL into a new directory.'
}
$manifest = Get-Content -LiteralPath $ManifestPath -Raw | ConvertFrom-Json
if ($manifest.format_version -ne 2 -or @($manifest.files).Count -eq 0) { throw 'Unsupported or empty import manifest' }
# Validate the complete input before making any remote change.
$files = @()
foreach ($entry in $manifest.files) {
    if ($entry.name -notmatch '^(securities|financial_periods_annual|dividend_events)_\d{5}\.sql$') { throw 'Invalid manifest filename' }
    $file = Get-Item -LiteralPath (Join-Path $ImportDir $entry.name)
    $hash = (Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($hash -ne $entry.sha256) { throw "Import hash mismatch: $($entry.name)" }
    $files += [pscustomobject]@{ Name = $entry.name; FullName = $file.FullName; Hash = $hash }
}
if (@(Get-ChildItem -LiteralPath $ImportDir -Filter '*.sql').Count -ne $files.Count) { throw 'Unlisted SQL files in import directory' }
# Read-only schema preflight: fail before importing any data when migration 0005 is missing.
npx wrangler d1 execute $DatabaseName --remote --command 'SELECT security_code, fiscal_period, event_type, data_json FROM issuer_dividend_periods LIMIT 0'
if ($LASTEXITCODE -ne 0) { throw 'Required D1 schema is missing. Apply reviewed migrations first.' }

$done = @{}
if (Test-Path $StatePath) {
    $state = Get-Content $StatePath -Raw | ConvertFrom-Json
    foreach ($entry in $state.completed) { $done[$entry.name] = $entry.sha256 }
}

foreach ($file in $files) {
    if ($done[$file.Name] -eq $file.Hash) { continue }

    Write-Host "Importing $($file.Name)"
    npx wrangler d1 execute $DatabaseName --remote --file $file.FullName
    if ($LASTEXITCODE -ne 0) { throw "D1 import failed: $($file.Name)" }

    $done[$file.Name] = $file.Hash
    @{ completed = @($done.Keys | Sort-Object | ForEach-Object { @{ name = $_; sha256 = $done[$_] } }) } |
        ConvertTo-Json -Depth 5 |
        Set-Content -Encoding UTF8 $StatePath
}

Write-Host "D1 import complete: $($done.Count) files"
