$ErrorActionPreference = 'Stop'

$Here = Split-Path -Parent $MyInvocation.MyCommand.Path
$ImportDir = Join-Path $Here 'import'
$StatePath = Join-Path $Here 'import-state.json'
$DatabaseName = 'diview-irbank'

if (!(Test-Path $ImportDir)) {
    throw 'Import directory is missing. Run node ..\scripts\build-d1-import.cjs first.'
}

$done = @{}
if (Test-Path $StatePath) {
    $state = Get-Content $StatePath -Raw | ConvertFrom-Json
    foreach ($name in $state.completed) { $done[$name] = $true }
}

$AnnualFinancialFiles = @(Get-ChildItem $ImportDir -Filter 'financial_periods_annual_*.sql' | Sort-Object Name)
if ($AnnualFinancialFiles.Count -eq 0) {
    throw 'Annual-only D1 import files are missing. Run node ..\scripts\build-d1-import.cjs first.'
}

$files = @(
    Get-ChildItem $ImportDir -Filter 'securities_*.sql' | Sort-Object Name
    $AnnualFinancialFiles
    Get-ChildItem $ImportDir -Filter 'dividend_events_*.sql' | Sort-Object Name
)
foreach ($file in $files) {
    if ($done.ContainsKey($file.Name)) { continue }

    Write-Host "Importing $($file.Name)"
    npx wrangler d1 execute $DatabaseName --remote --file $file.FullName
    if ($LASTEXITCODE -ne 0) { throw "D1 import failed: $($file.Name)" }

    $done[$file.Name] = $true
    @{ completed = @($done.Keys | Sort-Object) } |
        ConvertTo-Json -Depth 3 |
        Set-Content -Encoding UTF8 $StatePath
}

Write-Host "D1 import complete: $($done.Count) files"
