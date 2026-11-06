#requires -Version 7.0
param(
    [Parameter(Mandatory = $true)][ValidatePattern('^[A-Za-z0-9_.:-]+$')][string]$Serial,
    [Parameter(Mandatory = $true)][string]$LogDirectory,
    [string]$CredentialPath,
    [string]$LayoutPath,
    [switch]$AcceptUnverifiedLayout,
    [switch]$SkipUnlock
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$LogDirectory = [IO.Path]::GetFullPath($LogDirectory)
if ($LogDirectory.Contains('"') -or $PSScriptRoot.Contains('"')) { throw 'Unexpected quote in path.' }
if (!$CredentialPath) { $CredentialPath = Join-Path $env:LOCALAPPDATA 'AutoDingLocal\phone-unlock.clixml' }
$CredentialPath = [IO.Path]::GetFullPath($CredentialPath)
if ($CredentialPath.Contains('"')) { throw 'Unexpected quote in credential path.' }
if ($LayoutPath) {
    $LayoutPath = [IO.Path]::GetFullPath($LayoutPath)
    if ($LayoutPath.Contains('"')) { throw 'Unexpected quote in layout path.' }
    if (!(Test-Path -LiteralPath $LayoutPath -PathType Leaf)) { throw 'Layout file was not found.' }
}
if (!$SkipUnlock -and !(Test-Path -LiteralPath $credentialPath)) { throw 'Save the local encrypted credential first, or use -SkipUnlock for an already-unlocked phone.' }
if (!(Test-Path -LiteralPath (Join-Path (Split-Path $PSScriptRoot) 'build\ui-dump\autoding-ui.jar'))) {
    throw 'Build the UI dump helper first.'
}
$account = [Security.Principal.WindowsIdentity]::GetCurrent().Name
$principal = New-ScheduledTaskPrincipal -UserId $account -LogonType Interactive -RunLevel Limited
$logonTrigger = New-ScheduledTaskTrigger -AtLogOn -User $account
# A console interruption can terminate a long-running task without activating
# Task Scheduler's RestartCount. Re-try the launch every two minutes; IgnoreNew
# below keeps a healthy watcher as the only instance.
$repetitionTemplate = New-ScheduledTaskTrigger -Once -At (Get-Date).AddMinutes(1) -RepetitionInterval (New-TimeSpan -Minutes 2) -RepetitionDuration (New-TimeSpan -Days 1)
$recoveryTrigger = New-ScheduledTaskTrigger -Daily -At (Get-Date).AddMinutes(1)
$recoveryTrigger.Repetition = $repetitionTemplate.Repetition
$triggers = @($logonTrigger, $recoveryTrigger)
$settings = New-ScheduledTaskSettingsSet -ExecutionTimeLimit ([TimeSpan]::Zero) -RestartCount 3 -RestartInterval (New-TimeSpan -Minutes 1) -StartWhenAvailable -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries -MultipleInstances IgnoreNew
$settings.Hidden = $true
$powerShellPath = (Get-Command pwsh).Source
foreach ($item in @(
    @{ Name = 'AutoDingLocal-Observe'; Mode = 'Observe'; Folder = 'AutoDing-trial' },
    @{ Name = 'AutoDingLocal-Unlock'; Mode = 'Unlock'; Folder = 'AutoDing-unlock' },
    @{ Name = 'AutoDingLocal-Attendance'; Mode = 'Attendance'; Folder = 'AutoDing-attendance' }
)) {
    if ($SkipUnlock -and $item.Mode -eq 'Unlock') {
        Write-Output 'Skipped Unlock registration. Disable any previously registered AutoDingLocal-Unlock task separately if it is no longer needed.'
        continue
    }
    $scriptPath = Join-Path $PSScriptRoot 'Run-TrialTask.ps1'
    $outputPath = Join-Path $LogDirectory $item.Folder
    $arguments = '-NoProfile -WindowStyle Hidden -ExecutionPolicy Bypass -File "{0}" -Mode {1} -Serial {2} -OutputDirectory "{3}"' -f $scriptPath, $item.Mode, $Serial, $outputPath
    if ($item.Mode -eq 'Unlock') { $arguments += ' -CredentialPath "' + $CredentialPath + '"' }
    if ($item.Mode -eq 'Attendance' -and $LayoutPath) { $arguments += ' -LayoutPath "' + $LayoutPath + '"' }
    if ($item.Mode -eq 'Attendance' -and $AcceptUnverifiedLayout) { $arguments += ' -AcceptUnverifiedLayout' }
    $action = New-ScheduledTaskAction -Execute $powerShellPath -Argument $arguments -WorkingDirectory (Split-Path $PSScriptRoot)
    Register-ScheduledTask -TaskName $item.Name -Action $action -Trigger $triggers -Settings $settings -Principal $principal -Description 'Local USB phone trial; runs only while this Windows user is logged in.' -Force | Out-Null
    Write-Output "Registered $($item.Name). Start it after stopping any manually launched copy."
}
