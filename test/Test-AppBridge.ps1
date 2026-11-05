#requires -Version 7.0
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
Import-Module (Join-Path $PSScriptRoot '..\tools\AppBridge.psm1') -Force
$checks = 0
function Assert([bool]$Condition, [string]$Message) { if (!$Condition) { throw $Message }; $script:checks++ }
function Assert-Rejected([scriptblock]$Action) {
    $rejected = $false
    try { & $Action | Out-Null } catch { $rejected = $true }
    Assert $rejected 'Invalid bridge response was accepted.'
}
$json = '{"zone":"Asia\/Shanghai","pending":[],"checkIn":1793841480000,"outcome":"state"}'
$response = 'Broadcasting: Intent { act=xcom.warof.chosen.STATE cmp=xcom.warof.chosen/.BridgeReceiver }' + "`r`n" + 'Broadcast completed: result=1, data="' + $json + '"'
$state = ConvertFrom-AppBridgeReply $response
Assert ($state.zone -ceq 'Asia/Shanghai' -and $state.checkIn -eq 1793841480000L) 'Intent envelope must be excluded from JSON.'
Assert (@($state.pending).Count -eq 0) 'An empty pending list must stay empty.'
$escaped = 'Broadcast completed: result=1, data="' + $json.Replace('"','\"') + '"'
Assert ((ConvertFrom-AppBridgeReply $escaped).outcome -ceq 'state') 'Escaped JSON must parse.'
foreach ($invalid in @('Broadcast completed: result=-1, data="{}"', 'Broadcast completed: result=10, data="{}"', 'Broadcast completed: result=1', 'Broadcast completed: result=1, data="null"', 'Broadcast completed: result=1, data="[]"', 'Broadcast completed: result=1, data="{broken}"', ($response + ' trailing'))) {
    Assert-Rejected { ConvertFrom-AppBridgeReply $invalid }
}
foreach ($outcome in @('confirmed','unchanged')) {
    Assert ((Get-AppConfirmationOutcome ([pscustomobject]@{outcome=$outcome})) -ceq $outcome) 'Exact confirmation outcome must be retained.'
}
foreach ($value in @([pscustomobject]@{},[pscustomobject]@{outcome='state'},[pscustomobject]@{outcome='Confirmed'},[pscustomobject]@{changed=$false})) {
    Assert-Rejected { Get-AppConfirmationOutcome $value }
}
Write-Output "PASS: $checks app bridge checks"
