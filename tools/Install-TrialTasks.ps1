#requires -Version 7.0
param(
    [Parameter(Mandatory = $true)][ValidatePattern('^[A-Za-z0-9_.:-]+$')][string]$Serial,
    [Parameter(Mandatory = $true)][string]$LogDirectory,
    [string]$CredentialPath
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$LogDirectory = [IO.Path]::GetFullPath($LogDirectory)
if ($LogDirectory.Contains('"') -or $PSScriptRoot.Contains('"')) { throw 'Unexpected quote in path.' }
if (!$CredentialPath) { $CredentialPath = Join-Path $env:LOCALAPPDATA 'AutoDingLocal\phone-unlock.clixml' }
$CredentialPath = [IO.Path]::GetFullPath($CredentialPath)
if ($CredentialPath.Contains('"')) { throw 'Unexpected quote in credential path.' }
if (!(Test-Path -LiteralPath $credentialPath)) { throw 'Save the local encrypted credential first.' }
if (!(Test-Path -LiteralPath (Join-Path (Split-Path $PSScriptRoot) 'build\ui-dump\autoding-ui.jar'))) {
    throw 'Build the UI dump helper first.'
}
$account = [Security.Principal.WindowsIdentity]::GetCurrent().Name
$principal = New-ScheduledTaskPrincipal -UserId $account -LogonType Interactive -RunLevel Limited
$trigger = New-ScheduledTaskTrigger -AtLogOn -User $account
$settings = New-ScheduledTaskSettingsSet -ExecutionTimeLimit ([TimeSpan]::Zero) -RestartCount 3 -RestartInterval (New-TimeSpan -Minutes 1) -StartWhenAvailable -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries -MultipleInstances IgnoreNew
$settings.Hidden = $true
$powerShellPath = (Get-Command pwsh).Source
foreach ($item in @(
    @{ Name = 'AutoDingLocal-Observe'; Mode = 'Observe'; Folder = 'AutoDing-trial' },
    @{ Name = 'AutoDingLocal-Unlock'; Mode = 'Unlock'; Folder = 'AutoDing-unlock' },
    @{ Name = 'AutoDingLocal-Attendance'; Mode = 'Attendance'; Folder = 'AutoDing-attendance' }
)) {
    $scriptPath = Join-Path $PSScriptRoot 'Run-TrialTask.ps1'
    $outputPath = Join-Path $LogDirectory $item.Folder
    $arguments = '-NoProfile -WindowStyle Hidden -ExecutionPolicy Bypass -File "{0}" -Mode {1} -Serial {2} -OutputDirectory "{3}"' -f $scriptPath, $item.Mode, $Serial, $outputPath
    if ($item.Mode -eq 'Unlock') { $arguments += ' -CredentialPath "' + $CredentialPath + '"' }
    $action = New-ScheduledTaskAction -Execute $powerShellPath -Argument $arguments -WorkingDirectory (Split-Path $PSScriptRoot)
    Register-ScheduledTask -TaskName $item.Name -Action $action -Trigger $trigger -Settings $settings -Principal $principal -Description 'Local USB phone trial; runs only while this Windows user is logged in.' -Force | Out-Null
    Write-Output "Registered $($item.Name). Start it after stopping any manually launched copy."
}
