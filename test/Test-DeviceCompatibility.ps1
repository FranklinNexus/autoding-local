#requires -Version 7.0
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$scriptPath = Join-Path $PSScriptRoot '..\tools\Test-DeviceCompatibility.ps1'
$tokens=$null; $errors=$null
$ast=[Management.Automation.Language.Parser]::ParseFile($scriptPath,[ref]$tokens,[ref]$errors)
if ($errors.Count) { throw 'Doctor did not parse.' }
foreach ($name in @('ConvertFrom-DoctorDevices','ConvertFrom-DoctorSize','ConvertFrom-DoctorPackage','ConvertFrom-DoctorAppOp','ConvertTo-DoctorDeviceValue')) {
    $function=$ast.Find({param($node) $node -is [Management.Automation.Language.FunctionDefinitionAst] -and $node.Name -eq $name},$true)
    if (!$function) { throw 'Doctor parser helper was missing.' }
    . ([scriptblock]::Create($function.Extent.Text))
}
$checks=0
function Assert([bool]$Condition,[string]$Message) { if (!$Condition) { throw $Message }; $script:checks++ }
$inventory=@(ConvertFrom-DoctorDevices "List of devices attached`nFIXTURE_ONE device`nFIXTURE_TWO unauthorized`nFIXTURE_THREE offline`n")
Assert ($inventory.Count -eq 3) 'ADB inventory should retain all device states for selection.'
Assert ($inventory[1].State -ceq 'unauthorized') 'An authorization prompt must not count as a ready device.'
Assert (@(ConvertFrom-DoctorDevices "List of devices attached`n").Count -eq 0) 'Empty ADB inventory must remain empty.'
Assert (@(ConvertFrom-DoctorDevices "* daemon started successfully *`nnot a device row").Count -eq 0) 'ADB diagnostics must not become device rows.'
$size=ConvertFrom-DoctorSize "Physical size: 1440x3200`nOverride size: 1080x2400"
Assert ($size.Width -eq 1080 -and $size.Height -eq 2400) 'Override resolution must take priority.'
Assert ($size.Physical[0] -eq 1440 -and $size.Override[0] -eq 1080) 'Physical and override dimensions must both remain visible.'
$size=ConvertFrom-DoctorSize 'Physical size: 1080x2400'
Assert ($size.Width -eq 1080 -and $null -eq $size.Override) 'Physical resolution should be used without override.'
Assert ($null -eq (ConvertFrom-DoctorSize 'malformed').Width) 'Unknown dimensions must not be guessed.'
$package=ConvertFrom-DoctorPackage "versionCode=905100 minSdk=21`nversionName=9.0.5`n"
Assert ($package.Installed -and $package.VersionCode -eq 905100 -and $package.VersionName -ceq '9.0.5') 'Installed package version must parse.'
Assert (!(ConvertFrom-DoctorPackage 'Unable to find package').Installed) 'Missing package must be explicit.'
foreach ($case in @(
    @{text='SYSTEM_ALERT_WINDOW: allow; time=+1h';expected='allowed'},
    @{text='SYSTEM_ALERT_WINDOW: deny';expected='denied'},
    @{text='SYSTEM_ALERT_WINDOW: ignore';expected='denied'},
    @{text='SYSTEM_ALERT_WINDOW: foreground';expected='foreground-only'},
    @{text='SYSTEM_ALERT_WINDOW: default';expected='unknown'},
    @{text='No operations.';expected='unknown'},
    @{text='POST_NOTIFICATION: allow';expected='unknown'}
)) {
    Assert ((ConvertFrom-DoctorAppOp $case.text 'SYSTEM_ALERT_WINDOW') -ceq $case.expected) 'Permission status must match the requested app-op exactly.'
}
Assert ((ConvertTo-DoctorDeviceValue 'JEF-AN00') -ceq 'JEF-AN00') 'Public model text should be preserved.'
Assert ($null -eq (ConvertTo-DoctorDeviceValue "model`nprivate data")) 'Multiline dumps must not enter public device metadata.'
Assert ($null -eq (ConvertTo-DoctorDeviceValue ('x'*101))) 'Unexpectedly long metadata must be omitted.'

# Run the actual offline path in a child process: JSON must survive missing ADB,
# include a concrete issue and nonzero exit, and omit identifiers and local paths.
$missing=Join-Path ([IO.Path]::GetTempPath()) ('doctor-private-'+[guid]::NewGuid().ToString('N')+'\adb.exe')
$output=& (Get-Command pwsh).Source -NoProfile -File $scriptPath -AdbPath $missing -Serial FIXTURE_PRIVATE_DEVICE
$exitCode=$LASTEXITCODE
$json=$output -join "`n"
$report=$json | ConvertFrom-Json -Depth 12
Assert ($exitCode -ne 0 -and !$report.readyForTrial -and $report.readOnly) 'Offline doctor must fail clearly without leaving read-only mode.'
Assert (@($report.issues|Where-Object code -CEQ 'adb-missing').Count -eq 1) 'Missing ADB needs a structured issue.'
Assert ($json -notmatch 'FIXTURE_PRIVATE_DEVICE|doctor-private-|[A-Za-z]:\\Users\\') 'Diagnostic JSON must omit serials and local account paths.'
Assert ($null -eq $report.device -and !$report.adb.available) 'Offline doctor must not invent a device.'

# Execute the task wrapper against isolated, non-device scripts to ensure
# calibrated-layout acceptance reaches Attendance and stays out of other modes.
$fixtureRoot=Join-Path ([IO.Path]::GetTempPath()) ('autoding-doctor-wrapper-'+[guid]::NewGuid().ToString('N'))
$outputFolder=Join-Path $fixtureRoot 'output'
New-Item -ItemType Directory -Path $fixtureRoot,$outputFolder | Out-Null
try {
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot '..\tools\Run-TrialTask.ps1') -Destination (Join-Path $fixtureRoot 'Run-TrialTask.ps1')
    $stub=@'
[CmdletBinding()]
param([string]$Serial,[string]$OutputDirectory,[string]$LayoutPath,[switch]$AcceptUnverifiedLayout)
[pscustomobject]@{layout=$LayoutPath;accept=$AcceptUnverifiedLayout.IsPresent}|ConvertTo-Json -Compress|Set-Content -LiteralPath (Join-Path $OutputDirectory 'received.json')
'@
    Set-Content -LiteralPath (Join-Path $fixtureRoot 'Watch-Attendance.ps1') -Value $stub
    $layoutFixture=Join-Path $fixtureRoot 'custom layout.json'
    Set-Content -LiteralPath $layoutFixture -Value '{}'
    & (Get-Command pwsh).Source -NoProfile -File (Join-Path $fixtureRoot 'Run-TrialTask.ps1') -Mode Attendance -Serial FIXTURE -OutputDirectory $outputFolder -LayoutPath $layoutFixture -AcceptUnverifiedLayout
    $received=Get-Content -LiteralPath (Join-Path $outputFolder 'received.json') -Raw | ConvertFrom-Json
    Assert ($LASTEXITCODE -eq 0 -and $received.accept -and $received.layout -ceq $layoutFixture) 'Attendance task must receive the chosen layout and explicit acceptance.'
    $observeStub=@'
[CmdletBinding()]
param([string]$Serial,[string]$OutputDirectory)
Set-Content -LiteralPath (Join-Path $OutputDirectory 'observe.ok') -Value 'ok'
'@
    Set-Content -LiteralPath (Join-Path $fixtureRoot 'Observe-Phone.ps1') -Value $observeStub
    & (Get-Command pwsh).Source -NoProfile -File (Join-Path $fixtureRoot 'Run-TrialTask.ps1') -Mode Observe -Serial FIXTURE -OutputDirectory $outputFolder -LayoutPath $layoutFixture -AcceptUnverifiedLayout
    Assert ($LASTEXITCODE -eq 0 -and (Test-Path -LiteralPath (Join-Path $outputFolder 'observe.ok'))) 'Layout parameters must not leak into Observe mode.'
    & (Get-Command pwsh).Source -NoProfile -File (Join-Path $fixtureRoot 'Run-TrialTask.ps1') -Mode Attendance -Serial FIXTURE -OutputDirectory $outputFolder
    $received=Get-Content -LiteralPath (Join-Path $outputFolder 'received.json') -Raw | ConvertFrom-Json
    Assert ($LASTEXITCODE -eq 0 -and !$received.accept -and !$received.layout) 'Default task invocation must preserve the unaccepted bundled-layout path.'
} finally {
    foreach ($name in @('received.json','task-events.jsonl','observe.ok')) { Remove-Item -LiteralPath (Join-Path $outputFolder $name) -ErrorAction SilentlyContinue }
    Remove-Item -LiteralPath $outputFolder -ErrorAction SilentlyContinue
    foreach ($name in @('Run-TrialTask.ps1','Watch-Attendance.ps1','Observe-Phone.ps1','custom layout.json')) { Remove-Item -LiteralPath (Join-Path $fixtureRoot $name) -ErrorAction SilentlyContinue }
    Remove-Item -LiteralPath $fixtureRoot -ErrorAction SilentlyContinue
}
Write-Output "PASS: $checks read-only device compatibility checks"
