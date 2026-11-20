param(
    [string]$ImportDir,
    [string]$AcknowledgeLegacyD1Write
)

$ErrorActionPreference = 'Stop'
throw @'
Disabled: market catalog, financial, and dividend imports into D1 are retired.
Do not execute generated D1 SQL or re-import these records. The ownership migration
uses the reviewed D1-to-R2 workflow documented in docs/d1-r2-migration.md; that
workflow is not authorization to run a production migration.
'@
