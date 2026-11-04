param(
    [string]$AdbPath,
    [string]$Serial,
    [Parameter(Mandatory = $true)][string]$OutputDirectory,
    [ValidateRange(10, 3600)][int]$IntervalSeconds = 60,
    [switch]$Once
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
if (!$AdbPath) {
    $adbCommand = Get-Command adb -ErrorAction SilentlyContinue
    if ($adbCommand) { $AdbPath = $adbCommand.Source }
    else {
        $sdkDirectory = if ($env:ANDROID_HOME) { $env:ANDROID_HOME }
            elseif ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT }
            elseif ($env:LOCALAPPDATA) { Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
            else { throw 'Set ANDROID_HOME or pass -AdbPath.' }
        $AdbPath = Join-Path $sdkDirectory 'platform-tools\adb.exe'
    }
}
if (!(Test-Path -LiteralPath $AdbPath)) { throw 'ADB executable was not found.' }
$OutputDirectory = [IO.Path]::GetFullPath($OutputDirectory)
New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null
if (!$Serial) {
    $devices = @(& $AdbPath devices | Where-Object { $_ -match '^\S+\s+device$' })
    if ($devices.Count -ne 1) { throw 'Connect one permitted device, or pass its -Serial.' }
    $Serial = ($devices[0] -split '\s+')[0]
}
$deviceArguments = @('-s', $Serial)
$eventProcess = $null
$eventReadTask = $null
$eventErrorTask = $null
$eventWriter = $null
$eventDay = ''
$nextSnapshot = [datetime]::MinValue
$stopFile = Join-Path $OutputDirectory 'stop.request'
if (Test-Path -LiteralPath $stopFile) { throw 'Remove the previous stop.request file before restarting.' }
if (!$Once) { [IO.File]::WriteAllText((Join-Path $OutputDirectory 'observer.pid'), [string]$PID) }
if (!$Once) {
    if (!('AutoDingTrial.PowerState' -as [type])) {
        Add-Type -TypeDefinition 'namespace AutoDingTrial { public static class PowerState { [System.Runtime.InteropServices.DllImport("kernel32.dll")] public static extern uint SetThreadExecutionState(uint flags); } }'
    }
    # Prevent idle system sleep for this collector process; display sleep remains allowed.
    if ([AutoDingTrial.PowerState]::SetThreadExecutionState([uint32]2147483649) -eq 0) {
        throw 'The collector could not request an idle-sleep hold.'
    }
}

function Read-Adb([string[]]$AdbArguments) {
    $lines = & $AdbPath @deviceArguments @AdbArguments 2>&1
    if ($LASTEXITCODE -ne 0) { throw "ADB exited $LASTEXITCODE while running $($AdbArguments[0])." }
    return ($lines -join "`n")
}
function Drain-EventCapture {
    while ($script:eventReadTask -and $script:eventReadTask.IsCompleted) {
        $line = $script:eventReadTask.GetAwaiter().GetResult()
        if ($null -eq $line) { $script:eventReadTask = $null; break }
        $script:eventWriter.WriteLine($line)
        $script:eventReadTask = $script:eventProcess.StandardOutput.ReadLineAsync()
    }
}
function Stop-EventCapture {
    if ($script:eventProcess -and !$script:eventProcess.HasExited) {
        $script:eventProcess.Kill()
        [void]$script:eventProcess.WaitForExit(1000)
    }
    Drain-EventCapture
    if ($script:eventWriter) { $script:eventWriter.Dispose() }
    if ($script:eventErrorTask -and $script:eventErrorTask.IsCompleted) {
        $diagnostic = $script:eventErrorTask.GetAwaiter().GetResult()
        if ($diagnostic) { Add-Content -LiteralPath (Join-Path $OutputDirectory 'collector-errors.txt') -Encoding UTF8 -Value $diagnostic }
    }
    $script:eventReadTask = $null
    $script:eventErrorTask = $null
    $script:eventWriter = $null
    if ($script:eventProcess) { $script:eventProcess.Dispose() }
    $script:eventProcess = $null
}
function Start-EventCapture {
    $today = Get-Date -Format yyyyMMdd
    if ($script:eventProcess -and !$script:eventProcess.HasExited -and $script:eventDay -eq $today) { return }
    Stop-EventCapture
    $suffix = Get-Date -Format yyyyMMdd-HHmmss-fff
    if ($Serial -notmatch '^[A-Za-z0-9_.:-]+$') { throw 'Unexpected characters in the device identifier.' }
    $startInfo = [Diagnostics.ProcessStartInfo]::new()
    $startInfo.FileName = $AdbPath
    $startInfo.Arguments = "-s $Serial logcat -T 1 -v threadtime -s AutoDingLocal:I *:S"
    $startInfo.UseShellExecute = $false
    $startInfo.CreateNoWindow = $true
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    $script:eventWriter = [IO.StreamWriter]::new((Join-Path $OutputDirectory "events-$suffix.txt"), $true, [Text.UTF8Encoding]::new($false))
    $script:eventWriter.AutoFlush = $true
    $script:eventProcess = [Diagnostics.Process]::new()
    $script:eventProcess.StartInfo = $startInfo
    [void]$script:eventProcess.Start()
    $script:eventReadTask = $script:eventProcess.StandardOutput.ReadLineAsync()
    $script:eventErrorTask = $script:eventProcess.StandardError.ReadToEndAsync()
    $script:eventDay = $today
}
try {
    do {
        Drain-EventCapture
        if (Test-Path -LiteralPath $stopFile) { break }
        if ([datetime]::UtcNow -ge $nextSnapshot) {
            $snapshot = [ordered]@{
                capturedAt = [datetimeoffset]::Now.ToString('o'); connected = $false
                powered = $null; batteryPercent = $null; wakefulness = $null
                stayAwakeWhilePlugged = $null; keyguardLocked = $null
                watchServiceRunning = $null; dingForeground = $null
                upcomingTimers = @(); concerns = @()
            }
            try {
                if ((Read-Adb @('get-state')).Trim() -ne 'device') { throw 'Device is not ready.' }
                $snapshot.connected = $true
                if (!$Once) { Start-EventCapture }
                $power = Read-Adb @('shell', 'dumpsys', 'power')
                $services = Read-Adb @('shell', 'dumpsys', 'activity', 'services', 'xcom.warof.chosen')
                $alarms = Read-Adb @('shell', 'dumpsys', 'alarm')
                $windows = Read-Adb @('shell', 'dumpsys', 'window')
                $policy = Read-Adb @('shell', 'dumpsys', 'window', 'policy')
                $snapshot.powered = $power -match 'mIsPowered=true'
                $snapshot.stayAwakeWhilePlugged = $power -match 'mStayOn=true'
                if ($power -match 'mBatteryLevel=(\d+)') { $snapshot.batteryPercent = [int]$Matches[1] }
                if ($power -match 'mWakefulness=(\w+)') { $snapshot.wakefulness = $Matches[1] }
                $snapshot.keyguardLocked = $policy -match '(?m)^\s*showing=true\s*$'
                $watchBlock = [regex]::Match($services, '(?ms)^\s*\* ServiceRecord\{[^}]*WatchService\}.*?(?=^\s*\* ServiceRecord\{|\z)').Value
                $snapshot.watchServiceRunning = $watchBlock -match 'isForeground=true'
                $snapshot.dingForeground = $windows -match '(?m)mCurrentFocus=.*com\.alibaba\.android\.rimet/'
                $timerMatches = [regex]::Matches($alarms, 'tag=\*walarm\*:xcom\.warof\.chosen\.LOCAL_ALARM\s+type=RTC_WAKEUP origWhen=(?<at>\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d+)')
                $snapshot.upcomingTimers = @($timerMatches | ForEach-Object { $_.Groups['at'].Value } | Sort-Object -Unique)
                if (!$snapshot.powered) { $snapshot.concerns += 'not-powered' }
                if ($snapshot.wakefulness -ne 'Awake') { $snapshot.concerns += 'screen-not-awake' }
                if ($snapshot.keyguardLocked) { $snapshot.concerns += 'keyguard-locked' }
                if (!$snapshot.watchServiceRunning) { $snapshot.concerns += 'watch-service-missing' }
                if ($snapshot.upcomingTimers.Count -eq 0) { $snapshot.concerns += 'no-future-timers' }
            } catch {
                $snapshot.concerns += 'collection-error'; $snapshot.error = $_.Exception.Message
                Add-Content -LiteralPath (Join-Path $OutputDirectory 'collector-errors.txt') -Encoding UTF8 -Value "$([datetimeoffset]::Now.ToString('o')) $($_.Exception.Message)"
                Stop-EventCapture
            }
            $json = ConvertTo-Json -InputObject $snapshot -Depth 4 -Compress
            Add-Content -LiteralPath (Join-Path $OutputDirectory 'health.jsonl') -Encoding UTF8 -Value $json
            $temporaryPath = Join-Path $OutputDirectory 'current.json.tmp'
            [IO.File]::WriteAllText($temporaryPath, $json, [Text.UTF8Encoding]::new($false))
            Move-Item -LiteralPath $temporaryPath -Destination (Join-Path $OutputDirectory 'current.json') -Force
            Write-Output $json
            $nextSnapshot = [datetime]::UtcNow.AddSeconds($IntervalSeconds)
        }
        if (!$Once) { Start-Sleep -Seconds 2 }
    } while (!$Once)
} finally {
    Stop-EventCapture
    if (!$Once) { [void][AutoDingTrial.PowerState]::SetThreadExecutionState([uint32]2147483648) }
}
