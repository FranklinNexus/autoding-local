#requires -Version 7.0
param(
    [ValidatePattern('^[A-Za-z0-9_.:-]+$')][string]$Serial,
    [string]$AdbPath,
    [string]$LayoutPath
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

# Read-only doctor: no app launches, screenshots, UI interaction, settings
# changes, credential access, or raw adb output. Redirect stdout to save JSON.
function ConvertFrom-DoctorDevices([string]$Text) {
    return @([regex]::Matches($Text, '(?m)^(?<id>[A-Za-z0-9_.:-]+)\s+(?<state>device|offline|unauthorized|recovery|sideload|bootloader|no permissions)(?:\s|$)') |
        ForEach-Object { [pscustomobject]@{ Id = $_.Groups['id'].Value; State = $_.Groups['state'].Value } })
}
function ConvertFrom-DoctorSize([string]$Text) {
    $physical = [regex]::Match($Text, 'Physical size:\s*(?<w>[0-9]+)x(?<h>[0-9]+)')
    $override = [regex]::Match($Text, 'Override size:\s*(?<w>[0-9]+)x(?<h>[0-9]+)')
    $effective = if ($override.Success) { $override } else { $physical }
    return [pscustomobject]@{
        Physical = if ($physical.Success) { @([int]$physical.Groups['w'].Value, [int]$physical.Groups['h'].Value) } else { $null }
        Override = if ($override.Success) { @([int]$override.Groups['w'].Value, [int]$override.Groups['h'].Value) } else { $null }
        Width = if ($effective.Success) { [int]$effective.Groups['w'].Value } else { $null }
        Height = if ($effective.Success) { [int]$effective.Groups['h'].Value } else { $null }
    }
}
function ConvertFrom-DoctorPackage([string]$Text) {
    $versionName = [regex]::Match($Text, '(?m)^\s*versionName=(?<v>[A-Za-z0-9_.+() -]{1,100})\s*$')
    $versionCode = [regex]::Match($Text, '(?m)^\s*versionCode=(?<v>[0-9]+)\b')
    return [pscustomobject]@{
        Installed = $versionCode.Success
        VersionName = if ($versionName.Success) { $versionName.Groups['v'].Value.Trim() } else { $null }
        VersionCode = if ($versionCode.Success) { [long]$versionCode.Groups['v'].Value } else { $null }
    }
}
function ConvertFrom-DoctorAppOp([string]$Text, [string]$Operation) {
    $match = [regex]::Match($Text, '(?im)^\s*' + [regex]::Escape($Operation) + ':\s*(?<mode>allow|ignore|deny|default|foreground)\b')
    if (!$match.Success) { return 'unknown' }
    switch ($match.Groups['mode'].Value.ToLowerInvariant()) {
        'allow' { return 'allowed' }
        'ignore' { return 'denied' }
        'deny' { return 'denied' }
        'foreground' { return 'foreground-only' }
        default { return 'unknown' }
    }
}
function ConvertTo-DoctorDeviceValue([string]$Text) {
    # Restrict to a single short public model/version string, never dumpsys text.
    $value = $Text.Trim()
    if ($value -match '^[\p{L}\p{N}_.+() /-]{1,100}$') { return $value }
    return $null
}
function Invoke-DoctorProcess([string]$File, [string[]]$Arguments, [int]$TimeoutMs = 12000) {
    $info = [Diagnostics.ProcessStartInfo]::new()
    $info.FileName = $File; $info.UseShellExecute = $false; $info.CreateNoWindow = $true
    $info.RedirectStandardOutput = $true; $info.RedirectStandardError = $true
    $info.StandardOutputEncoding = [Text.UTF8Encoding]::new($false)
    foreach ($argument in $Arguments) { $info.ArgumentList.Add($argument) }
    $process = [Diagnostics.Process]::new(); $process.StartInfo = $info
    $started = $false
    try {
        [void]$process.Start(); $started = $true
        $outTask = $process.StandardOutput.ReadToEndAsync()
        $errTask = $process.StandardError.ReadToEndAsync()
        if (!$process.WaitForExit($TimeoutMs)) { throw 'doctor-command-timeout' }
        if ($process.ExitCode -ne 0) { throw 'doctor-command-failed' }
        if (!$outTask.Wait(1000) -or !$errTask.Wait(1000)) { throw 'doctor-pipe-timeout' }
        [void]$errTask.GetAwaiter().GetResult()
        return $outTask.GetAwaiter().GetResult()
    } finally {
        if ($started -and !$process.HasExited) { $process.Kill($true); [void]$process.WaitForExit(1000) }
        $process.Dispose()
    }
}
function Add-DoctorIssue([string]$Code, [string]$Severity, [string]$Action) {
    $report.issues.Add([pscustomobject]@{ code = $Code; severity = $Severity; action = $Action })
}
function Read-DoctorAdb([string[]]$Arguments) {
    return Invoke-DoctorProcess $AdbPath (@('-s', $selectedDevice) + $Arguments)
}
function Read-DoctorProperty([string]$Name) {
    try { return ConvertTo-DoctorDeviceValue (Read-DoctorAdb @('shell','getprop',$Name)) } catch { return $null }
}
function Read-DoctorPermission([string]$Operation) {
    try { return ConvertFrom-DoctorAppOp (Read-DoctorAdb @('shell','cmd','appops','get','xcom.warof.chosen',$Operation)) $Operation }
    catch { return 'unknown' }
}

$report = [ordered]@{
    schemaVersion = 1; capturedAt = [datetimeoffset]::Now.ToString('o')
    readOnly = $true; readyForTrial = $false; acceptance = 'not-tested'
    host = [ordered]@{ windows = ([Environment]::OSVersion.Platform -eq [PlatformID]::Win32NT); powerShellMajor = $PSVersionTable.PSVersion.Major; windowsPowerShell = $false; simplifiedChineseOcr = $null; uiHelperBuilt = (Test-Path -LiteralPath (Join-Path $PSScriptRoot '..\build\ui-dump\autoding-ui.jar') -PathType Leaf) }
    adb = [ordered]@{ available = $false; inventoryReadable = $false; connectedCount = 0; usableCount = 0; unauthorizedCount = 0; offlineCount = 0; selection = 'none' }
    device = $null; layout = $null; deviceAcceptance = 'not-checked'; issues = [Collections.Generic.List[object]]::new()
    limits = @('Device checks do not prove attendance success.', 'DingTalk login, instant attendance, location and identity requirements need on-device review.', 'PIN automation uses standard controls; only the documented Huawei model has device acceptance.', 'Notification channel settings and vendor startup management need on-device review.')
}

if (!$report.host.windows) { Add-DoctorIssue 'windows-required' 'error' 'Run the companion on Windows with PowerShell 7.' }
if (!$report.host.uiHelperBuilt) { Add-DoctorIssue 'ui-helper-not-built' 'error' 'Run tools/Build-UiDump.ps1 before starting the companion.' }
if ($report.host.windows) {
    $windowsPowerShell = Join-Path $env:WINDIR 'System32\WindowsPowerShell\v1.0\powershell.exe'
    $report.host.windowsPowerShell = Test-Path -LiteralPath $windowsPowerShell -PathType Leaf
    if (!$report.host.windowsPowerShell) { Add-DoctorIssue 'windows-powershell-missing' 'error' 'Install or enable Windows PowerShell 5.1 for local OCR.' }
    else {
        $ocrProbe = @'
$ErrorActionPreference='Stop'
try {
  Add-Type -AssemblyName System.Runtime.WindowsRuntime
  [void][Windows.Media.Ocr.OcrEngine, Windows.Foundation, ContentType=WindowsRuntime]
  $languages=@([Windows.Media.Ocr.OcrEngine]::AvailableRecognizerLanguages | Where-Object {$_.LanguageTag -match '^zh-Hans(?:-|$)'})
  $available=$languages.Count -gt 0 -and $null -ne [Windows.Media.Ocr.OcrEngine]::TryCreateFromLanguage($languages[0])
  [pscustomobject]@{available=$available}|ConvertTo-Json -Compress
} catch { '{"available":null}' }
'@
        try {
            $encoded = [Convert]::ToBase64String([Text.Encoding]::Unicode.GetBytes($ocrProbe))
            $result = Invoke-DoctorProcess $windowsPowerShell @('-NoProfile','-EncodedCommand',$encoded)
            $report.host.simplifiedChineseOcr = ($result | ConvertFrom-Json).available
        } catch { $report.host.simplifiedChineseOcr = $null }
        if ($report.host.simplifiedChineseOcr -ne $true) { Add-DoctorIssue 'chinese-ocr-unavailable' 'error' 'Install the Simplified Chinese OCR language feature and rerun this doctor.' }
    }
}

try {
    Import-Module (Join-Path $PSScriptRoot 'AttendanceLayout.psm1') -Force
    $layout = Get-AttendanceLayout -LayoutPath $LayoutPath
    $report.layout = [ordered]@{ id = $layout.id; dimensionsAccepted = $null; requiresDeviceAcceptance = $true }
} catch { Add-DoctorIssue 'layout-invalid' 'error' 'Use a valid layout JSON or omit -LayoutPath to use the bundled layout.'; $layout = $null }

if (!$AdbPath) {
    $adbCommand = Get-Command adb -CommandType Application -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($adbCommand) { $AdbPath = $adbCommand.Source }
    else {
        $sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } elseif ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } elseif ($env:LOCALAPPDATA) { Join-Path $env:LOCALAPPDATA 'Android\Sdk' } else { $null }
        if ($sdk) { $AdbPath = Join-Path $sdk 'platform-tools\adb.exe' }
    }
}
if (!$AdbPath -or !(Test-Path -LiteralPath $AdbPath -PathType Leaf)) {
    Add-DoctorIssue 'adb-missing' 'error' 'Install Android SDK platform-tools, set ANDROID_HOME, or pass -AdbPath.'
} else {
    $report.adb.available = $true
    $devices = @()
    try {
        $devices = @(ConvertFrom-DoctorDevices (Invoke-DoctorProcess $AdbPath @('devices')))
        $report.adb.inventoryReadable = $true
        $report.adb.connectedCount = $devices.Count
        $report.adb.usableCount = @($devices | Where-Object State -CEQ 'device').Count
        $report.adb.unauthorizedCount = @($devices | Where-Object State -CEQ 'unauthorized').Count
        $report.adb.offlineCount = @($devices | Where-Object State -CEQ 'offline').Count
    } catch { Add-DoctorIssue 'adb-inventory-failed' 'error' 'Check the ADB installation and rerun the doctor.' }
    $selectedDevice = $null
    if ($report.adb.inventoryReadable) {
        if ($Serial) {
            $selected = @($devices | Where-Object Id -CEQ $Serial)
            if ($selected.Count -eq 1 -and $selected[0].State -ceq 'device') { $selectedDevice = $selected[0].Id; $report.adb.selection = 'explicit' }
            else { Add-DoctorIssue 'selected-device-not-ready' 'error' 'Connect the selected phone, enable USB debugging and accept its computer authorization prompt.' }
        } elseif ($devices.Count -eq 1 -and $devices[0].State -ceq 'device') { $selectedDevice = $devices[0].Id; $report.adb.selection = 'single-device' }
        elseif ($devices.Count -gt 1) { Add-DoctorIssue 'device-selection-required' 'error' 'More than one ADB device is listed; pass -Serial to select one.' }
        else { Add-DoctorIssue 'device-not-ready' 'error' 'Connect one phone, enable USB debugging and accept its computer authorization prompt.' }
    }
    if ($selectedDevice) {
        $apiText = Read-DoctorProperty 'ro.build.version.sdk'
        $api = if ($apiText -match '^[0-9]{1,3}$') { [int]$apiText } else { $null }
        $report.device = [ordered]@{
            manufacturer = Read-DoctorProperty 'ro.product.manufacturer'; model = Read-DoctorProperty 'ro.product.model'
            androidRelease = Read-DoctorProperty 'ro.build.version.release'; apiLevel = $api
            vendorOsVersion = Read-DoctorProperty 'hw_sc.build.platform.version'
            dingTalk = $null; autoDing = $null; screen = $null
            permissions = [ordered]@{ exactAlarm = 'unknown'; overlay = 'unknown'; appNotifications = 'unknown' }
            battery = $null
        }
        if ($null -eq $api) { Add-DoctorIssue 'api-unreadable' 'error' 'Check the device connection; the Android API level could not be read.' }
        elseif ($api -lt 23) { Add-DoctorIssue 'android-api-too-old' 'error' 'Use Android API 23 or newer.' }
        foreach ($item in @(@{ key='dingTalk'; package='com.alibaba.android.rimet' }, @{ key='autoDing'; package='xcom.warof.chosen' })) {
            try { $state = ConvertFrom-DoctorPackage (Read-DoctorAdb @('shell','dumpsys','package',$item.package)); $report.device[$item.key] = $state }
            catch { $state = $null }
            if (!$state -or !$state.Installed) { Add-DoctorIssue ($item.key.ToLowerInvariant() + '-not-installed') 'error' ('Install ' + $item.key + ' on this phone, then rerun the doctor.') }
        }
        try {
            $screen = ConvertFrom-DoctorSize (Read-DoctorAdb @('shell','wm','size'))
            $orientation = $null
            try {
                $inputState = Read-DoctorAdb @('shell','dumpsys','input')
                if ($inputState -match '(?m)SurfaceOrientation:\s*([0-3])\b') { $orientation = [int]$Matches[1] }
            } catch {}
            $report.device.screen = [ordered]@{ physical = $screen.Physical; override = $screen.Override; effective = @($screen.Width,$screen.Height); rotation = $orientation }
            if ($null -eq $screen.Width) { throw 'screen-unreadable' }
            if ($layout) {
                try {
                    $resolved = Resolve-AttendanceLayout -Layout $layout -ImageWidth $screen.Width -ImageHeight $screen.Height
                    $report.layout.dimensionsAccepted = $true
                    $report.layout.requiresDeviceAcceptance = $resolved.RequiresDeviceAcceptance
                } catch { $report.layout.dimensionsAccepted = $false; Add-DoctorIssue 'layout-dimensions-mismatch' 'error' 'Create and review a layout for the effective screen dimensions before enabling OCR.' }
            }
            if ($orientation -in @(1,3)) { Add-DoctorIssue 'landscape-orientation' 'error' 'Use the portrait orientation supported by the attendance layout.' }
            elseif ($null -eq $orientation) { Add-DoctorIssue 'orientation-unverified' 'warning' 'Confirm portrait orientation on the phone before the first OCR trial.' }
        } catch { Add-DoctorIssue 'screen-unreadable' 'error' 'Check ADB access to the display size.' }
        if ($api -and $api -lt 31) { $report.device.permissions.exactAlarm = 'not-required-before-api31' }
        elseif ($api) { $report.device.permissions.exactAlarm = Read-DoctorPermission 'SCHEDULE_EXACT_ALARM' }
        $report.device.permissions.overlay = Read-DoctorPermission 'SYSTEM_ALERT_WINDOW'
        $report.device.permissions.appNotifications = Read-DoctorPermission 'POST_NOTIFICATION'
        foreach ($name in @('exactAlarm','overlay','appNotifications')) {
            $value = $report.device.permissions[$name]
            if ($value -in @('denied','foreground-only')) { Add-DoctorIssue ('permission-' + $name) 'error' ('Enable the AutoDing ' + $name + ' setting on the phone.') }
            elseif ($value -ceq 'unknown') { Add-DoctorIssue ('permission-' + $name + '-unverified') 'warning' ('Verify AutoDing ' + $name + ' in Android settings; this firmware did not expose a definitive value.') }
        }
        try {
            $battery = Read-DoctorAdb @('shell','dumpsys','battery')
            $level = if ($battery -match '(?m)^\s*level:\s*([0-9]{1,3})\s*$') { [int]$Matches[1] } else { $null }
            $powered = if ($battery -match '(?m)^\s*(?:AC|USB|Wireless) powered:\s*true\s*$') { $true }
                elseif ($battery -match '(?m)^\s*(?:AC|USB|Wireless) powered:\s*false\s*$') { $false } else { $null }
            $report.device.battery = [ordered]@{ levelPercent = $level; powered = $powered }
            if ($powered -eq $false) { Add-DoctorIssue 'phone-not-powered' 'warning' 'Keep the phone connected to reliable power during the trial.' }
        } catch { Add-DoctorIssue 'battery-unreadable' 'warning' 'Check phone power and battery status on the device.' }
        $referenceDevice = $report.device.model -ceq 'JEF-AN00' -and $api -eq 31 -and
            $report.device.dingTalk -and $report.device.dingTalk.VersionName -ceq '9.0.5' -and
            $report.layout -and $report.layout.dimensionsAccepted -eq $true -and !$report.layout.requiresDeviceAcceptance
        $report.deviceAcceptance = if ($referenceDevice) { 'reference-device' } else { 'requires-device-acceptance' }
        if (!$referenceDevice) { Add-DoctorIssue 'device-acceptance-required' 'warning' 'This model, Android API, DingTalk version or layout differs from the reference; calibrate and test before explicit opt-in with -AcceptUnverifiedLayout.' }
        else { Add-DoctorIssue 'first-run-check-required' 'warning' 'The reference combination is known, but verify your permissions, login and actual attendance results during first use.' }
    }
}
$report.readyForTrial = @($report.issues | Where-Object severity -CEQ 'error').Count -eq 0
$report.acceptance = if ($report.readyForTrial) { 'preflight-passed-device-trial-required' } else { 'setup-required' }
Write-Output (ConvertTo-Json -InputObject $report -Depth 10)
if (!$report.readyForTrial) { exit 1 }
