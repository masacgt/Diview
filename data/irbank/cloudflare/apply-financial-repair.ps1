param([Parameter(Mandatory = $true)][string]$PlanDir)
$ErrorActionPreference = 'Stop'
$PlanDir = (Resolve-Path -LiteralPath $PlanDir).Path
$manifest = Get-Content -LiteralPath (Join-Path $PlanDir 'manifest.json') -Raw | ConvertFrom-Json
if (!$manifest.offline_replay_verified -or $manifest.corrupt_rows -le 0) { throw 'No verified repair plan' }
$files = @()
foreach ($entry in $manifest.files) {
    if ($entry.name -notmatch '^repair_\d{5}\.sql$') { throw 'Invalid repair filename' }
    $file = Join-Path $PlanDir $entry.name
    if ((Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash.ToLowerInvariant() -ne $entry.sha256) {
        throw "Repair checksum mismatch: $($entry.name)"
    }
    $files += $entry
}
$statePath = Join-Path $PlanDir 'applied.json'
$done = @{}
if (Test-Path -LiteralPath $statePath) {
    foreach ($entry in (Get-Content -LiteralPath $statePath -Raw | ConvertFrom-Json).completed) { $done[$entry.name] = $entry.sha256 }
}
foreach ($entry in $files) {
    if ($done[$entry.name] -eq $entry.sha256) { continue }
    $file = Join-Path $PlanDir $entry.name
    $log = Join-Path $PlanDir ($entry.name + '.log')
    npx wrangler d1 execute diview-irbank --remote --yes --file $file *> $log
    if ($LASTEXITCODE -ne 0) { throw "Repair stopped at $($entry.name); see local log $log" }
    $done[$entry.name] = $entry.sha256
    @{ completed = @($done.Keys | Sort-Object | ForEach-Object { @{ name = $_; sha256 = $done[$_] } }) } |
        ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $statePath -Encoding UTF8
    Write-Host "Applied $($entry.name) ($($done.Count)/$($files.Count))"
}
Write-Host 'Repair chunks applied. Export and verify the live database before declaring recovery complete.'
