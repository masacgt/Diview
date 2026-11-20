param(
    [string]$PlanDir,
    [string]$AcknowledgeLegacyD1Repair
)

$ErrorActionPreference = 'Stop'
throw @'
Disabled: financial market data is no longer repaired by writing to D1.
Keep the offline repair plan and its source artifacts for review, but do not apply
generated SQL to D1. Resolve any verified data gap through the reviewed R2 data
pipeline and the D1-to-R2 runbook; production writes require separate approval.
'@
