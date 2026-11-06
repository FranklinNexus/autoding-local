#requires -Version 7.0
param(
    [Parameter(Mandatory = $true)][ValidateSet('Observe','Unlock','Attendance')][string]$Mode,
    [Parameter(Mandatory = $true)][string]$Serial,
    [Parameter(Mandatory = $true)][string]$OutputDirectory,
    [string]$CredentialPath,
    [string]$LayoutPath,
    [switch]$AcceptUnverifiedLayout
)
$ErrorActionPreference = 'Stop'
New-Item -ItemType Directory -Path $OutputDirectory -Force | Out-Null
$diagnosticPath = Join-Path $OutputDirectory 'task-events.jsonl'
function Write-TaskEvent([string]$Outcome, [string]$Message = '') {
    [pscustomobject]@{ capturedAt=[datetimeoffset]::Now.ToString('o'); outcome=$Outcome; message=$Message; pid=$PID } |
        ConvertTo-Json -Compress | Add-Content -LiteralPath $diagnosticPath -Encoding utf8
}
try {
    Write-TaskEvent 'started'
    $scriptName = switch ($Mode) { 'Observe' { 'Observe-Phone.ps1' } 'Unlock' { 'Watch-PhoneUnlock.ps1' } 'Attendance' { 'Watch-Attendance.ps1' } }
    $options = @{ Serial = $Serial; OutputDirectory = $OutputDirectory }
    if ($Mode -eq 'Unlock' -and $CredentialPath) { $options.CredentialPath = $CredentialPath }
    if ($Mode -eq 'Attendance' -and $LayoutPath) { $options.LayoutPath = $LayoutPath }
    if ($Mode -eq 'Attendance' -and $AcceptUnverifiedLayout) { $options.AcceptUnverifiedLayout = $true }
    & (Join-Path $PSScriptRoot $scriptName) @options | Out-Null
    Write-TaskEvent 'stopped'
} catch {
    # Child scripts sanitize secret-input errors before they reach this wrapper.
    Write-TaskEvent 'failed' $_.Exception.Message
    exit 1
}
