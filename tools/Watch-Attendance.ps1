#requires -Version 7.0
param(
    [Parameter(Mandatory)][ValidatePattern('^[A-Za-z0-9_.:-]+$')][string]$Serial,
    [Parameter(Mandatory)][string]$OutputDirectory,
    [string]$AdbPath,
    [string]$UiDumpJarPath,
    [switch]$Once,
    [switch]$NavigateToAttendance
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
Import-Module (Join-Path $PSScriptRoot 'AppBridge.psm1') -Force
if ([Environment]::OSVersion.Platform -ne [PlatformID]::Win32NT) { throw 'Run the attendance reader on Windows.' }
Import-Module (Join-Path $PSScriptRoot 'AttendanceScreen.psm1') -Force
if (!$AdbPath) {
    $command = Get-Command adb -ErrorAction SilentlyContinue
    if ($command) { $AdbPath = $command.Source }
    else {
        $sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME }
            elseif ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT }
            elseif ($env:LOCALAPPDATA) { Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
            else { throw 'Set ANDROID_HOME or pass -AdbPath.' }
        $AdbPath = Join-Path $sdk 'platform-tools\adb.exe'
    }
}
if (!$UiDumpJarPath) { $UiDumpJarPath = Join-Path $PSScriptRoot '..\build\ui-dump\autoding-ui.jar' }
$AdbPath = [IO.Path]::GetFullPath($AdbPath)
$UiDumpJarPath = [IO.Path]::GetFullPath($UiDumpJarPath)
$ocrScript = Join-Path $PSScriptRoot 'Read-AttendanceOcr.ps1'
$windowsPowerShell = Join-Path $env:WINDIR 'System32\WindowsPowerShell\v1.0\powershell.exe'
foreach ($required in @($AdbPath, $UiDumpJarPath, $ocrScript, $windowsPowerShell)) {
    if (!(Test-Path -LiteralPath $required -PathType Leaf)) { throw 'An attendance reader dependency is missing; build the UI helper first.' }
}
$OutputDirectory = [IO.Path]::GetFullPath($OutputDirectory)
New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null
$stopPath = Join-Path $OutputDirectory 'stop.request'
if (Test-Path -LiteralPath $stopPath) { throw 'Remove the previous stop.request before restarting.' }

function Write-Status([object]$Value) {
    $path = Join-Path $OutputDirectory 'status.json'
    $temporary = $path + '.' + [guid]::NewGuid().ToString('N') + '.tmp'
    try {
        [IO.File]::WriteAllText($temporary, (ConvertTo-Json -InputObject $Value -Depth 8 -Compress), [Text.UTF8Encoding]::new($false))
        [IO.File]::Move($temporary, $path, $true)
    } finally { if ([IO.File]::Exists($temporary)) { [IO.File]::Delete($temporary) } }
}
function Invoke-TextProcess([string]$Executable, [string[]]$Arguments, [int]$TimeoutMs) {
    $info = [Diagnostics.ProcessStartInfo]::new()
    $info.FileName = $Executable; $info.UseShellExecute = $false; $info.CreateNoWindow = $true
    $info.RedirectStandardOutput = $true; $info.RedirectStandardError = $true
    $info.StandardOutputEncoding = [Text.UTF8Encoding]::new($false)
    foreach ($argument in $Arguments) { $info.ArgumentList.Add($argument) }
    $process = [Diagnostics.Process]::new(); $process.StartInfo = $info
    $started = $false
    try {
        [void]$process.Start(); $started = $true
        $outputTask = $process.StandardOutput.ReadToEndAsync()
        $errorTask = $process.StandardError.ReadToEndAsync()
        if (!$process.WaitForExit($TimeoutMs)) { throw 'Attendance subprocess timed out.' }
        if ($process.ExitCode -ne 0) { throw 'Attendance subprocess failed.' }
        if (!$outputTask.Wait(1000) -or !$errorTask.Wait(1000)) { throw 'Attendance subprocess pipe did not close.' }
        [void]$errorTask.GetAwaiter().GetResult()
        return $outputTask.GetAwaiter().GetResult()
    } finally {
        if ($started -and !$process.HasExited) { $process.Kill($true); [void]$process.WaitForExit(1000) }
        $process.Dispose()
    }
}
function Read-Adb([string[]]$Arguments) { Invoke-TextProcess $AdbPath (@('-s', $Serial) + $Arguments) 15000 }
function Read-KeyguardLocked {
    $policy = Read-Adb @('shell', 'dumpsys', 'window', 'policy')
    $section = [regex]::Match($policy, '(?ms)^\s*KeyguardServiceDelegate\b(?<body>.*?)(?=^\s*KeyguardStateMonitor\b|\z)').Groups['body'].Value
    if ($section -notmatch '(?m)^\s*showing=(true|false)\s*$') { throw 'Keyguard state is unknown.' }
    return $Matches[1] -ceq 'true'
}
function Test-AttendanceTabs([string]$Xml) {
    $start = $Xml.IndexOf('<?xml', [StringComparison]::Ordinal)
    $end = $Xml.LastIndexOf('</hierarchy>', [StringComparison]::Ordinal)
    if ($start -lt 0 -or $end -lt $start) { return $false }
    try {
        $document = [xml]::new(); $document.XmlResolver = $null
        $document.LoadXml($Xml.Substring($start, $end + '</hierarchy>'.Length - $start))
    } catch { return $false }
    $tabs = @($document.SelectNodes('//node') | Where-Object {
        $_.GetAttribute('package') -ceq 'com.alibaba.android.rimet' -and
        $_.GetAttribute('resource-id') -ceq 'com.alibaba.android.rimet:id/h5_tabbaritem_txticon' -and
        $_.GetAttribute('enabled') -ceq 'true' -and $_.GetAttribute('bounds') -ne '[0,0][0,0]'
    } | ForEach-Object { $_.GetAttribute('text') })
    return $tabs.Count -eq 3 -and @($tabs | Sort-Object -Unique).Count -eq 3 -and
        '打卡' -cin $tabs -and '统计' -cin $tabs -and '设置' -cin $tabs
}
function Read-BridgeReply([string]$Action, [string[]]$Extras = @()) {
    $response = Read-Adb (@('shell', 'am', 'broadcast', '-n', 'xcom.warof.chosen/.BridgeReceiver', '-a', ('xcom.warof.chosen.' + $Action)) + $Extras)
    return ConvertFrom-AppBridgeReply -Response $response
}

$lockStream = $null
$lastEvent = ''
$helperPushed = $false
try {
    try { $lockStream = [IO.File]::Open((Join-Path $OutputDirectory 'watcher.lock'), [IO.FileMode]::OpenOrCreate, [IO.FileAccess]::ReadWrite, [IO.FileShare]::None) }
    catch { throw 'Another attendance watcher is using this output directory.' }
    [IO.File]::WriteAllText((Join-Path $OutputDirectory 'watcher.pid'), [string]$PID)
    do {
        if (Test-Path -LiteralPath $stopPath) { break }
        $imagePath = $null; $deviceImage = $null
        $stopAfterCycle = $false
        $stage = 'device-read-failed'
        $status = [ordered]@{
            capturedAt = [datetimeoffset]::Now.ToString('o'); connected = $false; locked = $null
            attendancePageConfirmed = $false; outcome = 'waiting-for-attendance-page'
            observation = $null; confirmations = @(); appStateAvailable = $false
        }
        try {
            if ((Read-Adb @('get-state')).Trim() -cne 'device') { throw 'Phone is not connected.' }
            $status.connected = $true
            $status.locked = Read-KeyguardLocked
            if ($status.locked) { $status.outcome = 'phone-locked' }
            else {
                $windows = Read-Adb @('shell', 'dumpsys', 'window')
                if ($windows -cmatch '(?m)^\s*mCurrentFocus=.*com\.alibaba\.android\.rimet/[^\r\n]*LaunchHomeActivity') {
                    $schedule = Read-BridgeReply 'STATE'
                    $needsPage = $NavigateToAttendance -or $schedule.stageMorning -in @('attempted','page-opened','launch-requested') -or $schedule.stageEvening -in @('attempted','page-opened','launch-requested')
                    if ($needsPage) {
                        if (!$helperPushed) { [void](Read-Adb @('push', $UiDumpJarPath, '/data/local/tmp/autoding-ui.jar')); $helperPushed = $true }
                        $shortcutXml = Read-Adb @('shell', 'CLASSPATH=/data/local/tmp/autoding-ui.jar app_process /system/bin xcom.warof.chosen.tools.ReadWindow')
                        $start = $shortcutXml.IndexOf('<?xml'); $end = $shortcutXml.LastIndexOf('</hierarchy>')
                        if ($start -ge 0 -and $end -gt $start) {
                            $shortcutDoc = [xml]::new(); $shortcutDoc.XmlResolver = $null
                            $shortcutDoc.LoadXml($shortcutXml.Substring($start, $end + 12 - $start))
                            $shortcuts = @($shortcutDoc.SelectNodes('//node[@package="com.alibaba.android.rimet" and @resource-id="com.alibaba.android.rimet:id/im_ding_kit_item_txt" and @text="打卡" and @enabled="true"]'))
                            if ($shortcuts.Count -eq 1 -and $shortcuts[0].GetAttribute('bounds') -match '^\[(\d+),(\d+)\]\[(\d+),(\d+)\]$') {
                                $x = [string][int](([int]$Matches[1] + [int]$Matches[3]) / 2)
                                $y = [string][int](([int]$Matches[2] + [int]$Matches[4]) / 2)
                                [void](Read-Adb @('shell','input','tap',$x,$y))
                                Start-Sleep -Milliseconds 1200
                                $windows = Read-Adb @('shell', 'dumpsys', 'window')
                            }
                        }
                        $shortcutXml = $null; $shortcutDoc = $null; $shortcuts = $null
                    }
                }
                $dingForeground = $windows -cmatch '(?m)^\s*mCurrentFocus=.*com\.alibaba\.android\.rimet/[^\r\n]*TheOneActivity'
                if ($dingForeground) {
                    $stage = 'unsupported-device-layout'
                    $size = Read-Adb @('shell', 'wm', 'size')
                    $sizes = [regex]::Matches($size, '(?:Physical|Override) size:\s*([0-9]+)x([0-9]+)')
                    if (!$sizes.Count -or $sizes[$sizes.Count - 1].Groups[1].Value -cne '1080' -or $sizes[$sizes.Count - 1].Groups[2].Value -cne '2400') { throw 'Unsupported screen dimensions.' }
                    $stage = 'attendance-page-read-failed'
                    if (!$helperPushed) { [void](Read-Adb @('push', $UiDumpJarPath, '/data/local/tmp/autoding-ui.jar')); $helperPushed = $true }
                    $xml = Read-Adb @('shell', 'CLASSPATH=/data/local/tmp/autoding-ui.jar app_process /system/bin xcom.warof.chosen.tools.ReadWindow')
                    $status.attendancePageConfirmed = Test-AttendanceTabs $xml
                    $xml = $null
                    if ($status.attendancePageConfirmed) {
                        $stage = 'attendance-screenshot-failed'
                        $suffix = [guid]::NewGuid().ToString('N')
                        $imagePath = Join-Path $OutputDirectory ('.attendance-' + $suffix + '.png')
                        $deviceImage = '/data/local/tmp/autoding-attendance-' + $suffix + '.png'
                        [void](Read-Adb @('shell', 'screencap', '-p', $deviceImage))
                        [void](Read-Adb @('pull', $deviceImage, $imagePath))
                        $stage = 'attendance-ocr-failed'
                        $ocrText = Invoke-TextProcess $windowsPowerShell @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', $ocrScript, '-ImagePath', $imagePath) 30000
                        $ocr = $ocrText | ConvertFrom-Json -Depth 8
                        $ocrText = $null
                        if ($ocr.OCRAvailable -ne $true -or $ocr.Layout -cne 'HuaweiAttendancePortraitV1' -or $ocr.ImageWidth -ne 1080 -or $ocr.ImageHeight -ne 2400) { throw 'OCR observation was not available in the supported layout.' }
                        $stage = 'phone-clock-read-failed'
                        $epochText = (Read-Adb @('shell', 'date', '+%s')).Trim()
                        if ($epochText -notmatch '^[0-9]{10,12}$') { throw 'Phone epoch was not recognized.' }
                        $phoneNow = [datetimeoffset]::FromUnixTimeSeconds([long]$epochText)
                        $zoneId = (Read-Adb @('shell', 'getprop', 'persist.sys.timezone')).Trim()
                        $zone = [TimeZoneInfo]::FindSystemTimeZoneById($zoneId)
                        $stage = 'attendance-observation-rejected'
                        $observation = ConvertFrom-AttendanceObservation -MorningLines @($ocr.MorningLines) -EveningLines @($ocr.EveningLines) -DateLines @($ocr.DateLines) -PhoneNow $phoneNow -PhoneTimeZone $zone -AttendancePageConfirmed $true
                        $ocr = $null
                        $status.observation = $observation
                        if (!$observation.accepted) { $status.outcome = 'observation-rejected' }
                        else {
                            $stage = 'attendance-confirmation-failed'
                            foreach ($record in @(@{ Kind = 100; Value = $observation.checkIn }, @{ Kind = 101; Value = $observation.checkOut })) {
                                if (!$record.Value) { continue }
                                $reply = Read-BridgeReply 'CONFIRM' @('--ei', 'kind', [string]$record.Kind, '--es', 'work_date', $observation.workDate, '--el', 'actual_at', [string]$record.Value.epochMs, '--es', 'precision', $record.Value.precision, '--es', 'source', 'attendance-screen')
                                $status.confirmations += [pscustomobject]@{ kind = $record.Kind; outcome = Get-AppConfirmationOutcome $reply }
                            }
                            $stage = 'attendance-state-read-failed'
                            [void](Read-BridgeReply 'STATE')
                            $status.appStateAvailable = $true
                            $status.outcome = if ($status.confirmations.Count) { 'records-verified' } else { 'no-records' }
                        }
                    }
                }
            }
        } catch {
            # No subprocess output, original OCR or exception message is persisted.
            $status.outcome = $stage
            $helperPushed = $false
        } finally {
            if ($imagePath) {
                for ($attempt = 0; $attempt -lt 3 -and [IO.File]::Exists($imagePath); $attempt++) {
                    try { [IO.File]::Delete($imagePath) } catch { Start-Sleep -Milliseconds 100 }
                }
                if ([IO.File]::Exists($imagePath)) { $status.outcome = 'temporary-image-cleanup-failed'; $stopAfterCycle = $true }
            }
            if ($deviceImage -and $deviceImage -cmatch '^/data/local/tmp/autoding-attendance-[a-f0-9]{32}\.png$') {
                try { [void](Read-Adb @('shell', 'rm', '-f', $deviceImage)) }
                catch { $status.outcome = 'device-image-cleanup-failed'; $stopAfterCycle = $true }
            }
            $ocr = $null; $ocrText = $null; $xml = $null
        }
        Write-Status $status
        $event = [ordered]@{ connected = $status.connected; locked = $status.locked; attendancePageConfirmed = $status.attendancePageConfirmed; outcome = $status.outcome; observation = $status.observation; confirmations = $status.confirmations; appStateAvailable = $status.appStateAvailable }
        $eventJson = ConvertTo-Json -InputObject $event -Depth 8 -Compress
        if ($eventJson -cne $lastEvent) {
            $record = [ordered]@{ capturedAt = $status.capturedAt; result = $event }
            [IO.File]::AppendAllText((Join-Path $OutputDirectory 'events.jsonl'), ((ConvertTo-Json -InputObject $record -Depth 9 -Compress) + [Environment]::NewLine), [Text.UTF8Encoding]::new($false))
            $lastEvent = $eventJson
        }
        if ($Once -or $stopAfterCycle) { Write-Output (ConvertTo-Json -InputObject $status -Depth 8 -Compress); break }
        for ($second = 0; $second -lt 30 -and !(Test-Path -LiteralPath $stopPath); $second++) { Start-Sleep -Seconds 1 }
    } while ($true)
} finally { if ($lockStream) { $lockStream.Dispose() } }
