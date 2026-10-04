param(
    [switch]$UploadOnly,
    [switch]$DeployOnly
)

if ($UploadOnly -and $DeployOnly) {
    throw "Choose either -UploadOnly or -DeployOnly, not both."
}

$ErrorActionPreference = "Stop"

$repoRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot "..\.."))
$python = Join-Path $env:USERPROFILE ".cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe"
$pythonPackages = Join-Path $env:LOCALAPPDATA "Temp\diview-r2-python"

if (-not (Test-Path -LiteralPath $python)) {
    throw "Codex bundled Python was not found: $python"
}
if (-not (Test-Path -LiteralPath $pythonPackages)) {
    throw "R2 upload dependencies were not found: $pythonPackages"
}

Push-Location $repoRoot
try {
    if (-not $UploadOnly) {
        Push-Location "data\irbank\cloudflare"
        try {
            npx wrangler whoami
            if ($LASTEXITCODE -ne 0) {
                throw "Wrangler is not authenticated. Run 'npx wrangler login' and rerun with -DeployOnly after the R2 upload succeeds."
            }
        }
        finally {
            Pop-Location
        }
    }

    if (-not $DeployOnly) {
        # Force the uploader to request fresh R2 credentials instead of reusing stale values.
        Remove-Item Env:R2_ACCESS_KEY_ID -ErrorAction SilentlyContinue
        Remove-Item Env:R2_SECRET_ACCESS_KEY -ErrorAction SilentlyContinue
        $env:PYTHONPATH = $pythonPackages

        & $python "scripts\irbank_r2\upload_r2.py" --upload
        if ($LASTEXITCODE -ne 0) {
            throw "R2 upload failed."
        }
    }

    if (-not $UploadOnly) {
        Push-Location "data\irbank\cloudflare"
        try {
            npx wrangler deploy
            if ($LASTEXITCODE -ne 0) {
                throw "Worker deployment failed."
            }
        }
        finally {
            Pop-Location
        }
    }
}
finally {
    Remove-Item Env:R2_ACCESS_KEY_ID -ErrorAction SilentlyContinue
    Remove-Item Env:R2_SECRET_ACCESS_KEY -ErrorAction SilentlyContinue
    Remove-Item Env:PYTHONPATH -ErrorAction SilentlyContinue
    Pop-Location
}
