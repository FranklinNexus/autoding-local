#requires -Version 7.0
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$scriptPath = Join-Path $PSScriptRoot '..\tools\Watch-PhoneUnlock.ps1'
$tokens=$null; $parseErrors=$null
$ast=[Management.Automation.Language.Parser]::ParseFile($scriptPath,[ref]$tokens,[ref]$parseErrors)
if ($parseErrors.Count) { throw 'Watcher script did not parse.' }
foreach ($name in @('Test-PinQuotaConsumed','Get-RecoverablePending')) {
    $function=$ast.Find({param($node) $node -is [Management.Automation.Language.FunctionDefinitionAst] -and $node.Name -eq $name},$true)
    if (!$function) { throw "Missing helper: $name" }
    . ([scriptblock]::Create($function.Extent.Text))
}
$checks=0
function Assert([bool]$Condition,[string]$Message) { if (!$Condition) { throw $Message }; $script:checks++ }
Assert (!(Test-PinQuotaConsumed $null)) 'No previous observation should allow an input.'
foreach ($outcome in @('already-unlocked','unlocked-before-pin','no-input')) {
    Assert (!(Test-PinQuotaConsumed @{outcome=$outcome})) 'Observing an unlocked phone must not spend its PIN quota.'
}
foreach ($outcome in @('started','verified-unlocked','blocked','unexpected')) {
    Assert (Test-PinQuotaConsumed @{outcome=$outcome}) 'Started, verified, blocked or unknown attempts must not repeat PIN input.'
}
$now=1793845900L
$pending=@(@{kind=100;at=($now-30)*1000},@{kind=101;at=($now-121)*1000},@{kind=200;at=($now+10)*1000})
$recoverable=@(Get-RecoverablePending $pending $now)
Assert ($recoverable.Count -eq 1 -and $recoverable[0].kind -eq 100) 'Only the live post-trigger request should recover.'
Assert (@(Get-RecoverablePending @() $now).Count -eq 0) 'No token means no recovery.'
Assert (@(Get-RecoverablePending @(@{at=($now-120)*1000}) $now).Count -eq 1) 'Valid boundary token should be retained for the app to revalidate.'
Write-Output "PASS: $checks unlock quota and bounded recovery checks"
