#requires -Version 7.0
param(
    [Parameter(Mandatory = $true)][ValidatePattern('^[A-Za-z0-9_.:-]+$')][string]$Serial,
    [Parameter(Mandatory = $true)][string]$OutputDirectory,
    [string]$CredentialPath,
    [string]$AdbPath,
    [string]$UiDumpJarPath,
    [switch]$TestOnce
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
Import-Module (Join-Path $PSScriptRoot 'AppBridge.psm1') -Force
if ([Environment]::OSVersion.Platform -ne [PlatformID]::Win32NT) { throw 'Run this tool on Windows.' }
if (!$env:LOCALAPPDATA) { throw 'LOCALAPPDATA is unavailable.' }
if (!$CredentialPath) {
    if (!$env:LOCALAPPDATA) { throw 'LOCALAPPDATA is unavailable; pass -CredentialPath.' }
    $CredentialPath = Join-Path $env:LOCALAPPDATA 'AutoDingLocal\phone-unlock.clixml'
}
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
if (!(Test-Path -LiteralPath $AdbPath -PathType Leaf)) { throw 'ADB executable was not found.' }
$AdbPath = [IO.Path]::GetFullPath($AdbPath)
if (!$UiDumpJarPath) { $UiDumpJarPath = Join-Path $PSScriptRoot '..\build\ui-dump\autoding-ui.jar' }
$UiDumpJarPath = [IO.Path]::GetFullPath($UiDumpJarPath)
if (!(Test-Path -LiteralPath $UiDumpJarPath -PathType Leaf)) { throw 'Build the local UI snapshot helper with tools/Build-UiDump.ps1 first.' }
$OutputDirectory = [IO.Path]::GetFullPath($OutputDirectory)
New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null
$stopPath = Join-Path $OutputDirectory 'stop.request'
$hasher = [Security.Cryptography.SHA256]::Create()
try { $deviceKey = [BitConverter]::ToString($hasher.ComputeHash([Text.Encoding]::UTF8.GetBytes($Serial))).Replace('-', '').ToLowerInvariant() }
finally { $hasher.Dispose() }
$deviceStateDirectory = Join-Path $env:LOCALAPPDATA ('AutoDingLocal\devices\' + $deviceKey)
New-Item -ItemType Directory -Force -Path $deviceStateDirectory | Out-Null
$blockPath = Join-Path $deviceStateDirectory 'unlock.blocked.json'
$attemptPath = Join-Path $deviceStateDirectory 'attempts.json'
if (Test-Path -LiteralPath $stopPath) { throw 'Remove the previous stop.request file before restarting.' }

function Write-JsonFile([string]$Path, [object]$Value) {
    $temporary = $Path + '.' + [guid]::NewGuid().ToString('N') + '.tmp'
    try {
        [IO.File]::WriteAllText($temporary, (ConvertTo-Json -InputObject $Value -Depth 6 -Compress), [Text.UTF8Encoding]::new($false))
        [IO.File]::Move($temporary, $Path, $true)
    } finally {
        if ([IO.File]::Exists($temporary)) { [IO.File]::Delete($temporary) }
    }
}
function Write-Event([string]$Event, [string]$Outcome, [string]$AttemptKey = '') {
    $record = [ordered]@{ capturedAt = [datetimeoffset]::Now.ToString('o'); event = $Event; outcome = $Outcome; attemptKey = $AttemptKey }
    [IO.File]::AppendAllText((Join-Path $OutputDirectory 'events.jsonl'), ((ConvertTo-Json $record -Compress) + [Environment]::NewLine), [Text.UTF8Encoding]::new($false))
}
function New-AdbProcess([string[]]$AdbArguments, [bool]$RedirectInput = $false) {
    $startInfo = [Diagnostics.ProcessStartInfo]::new()
    $startInfo.FileName = $AdbPath
    $startInfo.UseShellExecute = $false
    $startInfo.CreateNoWindow = $true
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    $startInfo.RedirectStandardInput = $RedirectInput
    if ($RedirectInput) { $startInfo.StandardInputEncoding = [Text.UTF8Encoding]::new($false) }
    foreach ($argument in (@('-s', $Serial) + $AdbArguments)) { $startInfo.ArgumentList.Add($argument) }
    $process = [Diagnostics.Process]::new()
    $process.StartInfo = $startInfo
    return $process
}
function Invoke-Adb([string[]]$AdbArguments, [int]$TimeoutMs = 15000) {
    $process = New-AdbProcess $AdbArguments
    try {
        [void]$process.Start()
        $outputTask = $process.StandardOutput.ReadToEndAsync()
        $errorTask = $process.StandardError.ReadToEndAsync()
        if (!$process.WaitForExit($TimeoutMs)) {
            $process.Kill($true)
            [void]$process.WaitForExit(1000)
            throw 'ADB operation timed out.'
        }
        if ($process.ExitCode -ne 0) { throw 'ADB operation failed.' }
        [void]$errorTask.GetAwaiter().GetResult()
        return $outputTask.GetAwaiter().GetResult()
    } finally { $process.Dispose() }
}
function Read-Keyguard([int]$TimeoutMs = 15000) {
    $policy = Invoke-Adb @('shell', 'dumpsys', 'window', 'policy') $TimeoutMs
    $delegate = [regex]::Match($policy, '(?ms)^\s*KeyguardServiceDelegate\b(?<body>.*?)(?=^\s*KeyguardStateMonitor\b|\z)').Groups['body'].Value
    if (!$delegate -or $delegate -notmatch '(?m)^\s*showing=(true|false)\s*$') { throw 'Keyguard state was not recognized.' }
    $locked = $Matches[1] -eq 'true'
    if ($delegate -notmatch '(?m)^\s*secure=(true|false)\s*$') { throw 'Keyguard security state was not recognized.' }
    $secure = $Matches[1] -eq 'true'
    return [pscustomobject]@{ Locked = $locked; Secure = $secure; Interactive = $delegate -match 'interactiveState=INTERACTIVE_STATE_AWAKE' }
}
function Test-SystemUiPin([string]$Dump, [ref]$SubmitExplicitly) {
    $start = $Dump.IndexOf('<?xml', [StringComparison]::Ordinal)
    $end = $Dump.LastIndexOf('</hierarchy>', [StringComparison]::Ordinal)
    if ($start -lt 0 -or $end -lt $start) { return $false }
    try {
        $document = [xml]::new()
        $document.XmlResolver = $null
        $document.LoadXml($Dump.Substring($start, $end + '</hierarchy>'.Length - $start))
    } catch { return $false }
    $nodes = @($document.SelectNodes('//node'))
    $systemNodes = @($nodes | Where-Object { $_.GetAttribute('package') -in @('com.android.systemui', 'com.huawei.systemui') })
    $entries = @($systemNodes | Where-Object {
        $_.GetAttribute('resource-id') -cmatch '^(com\.android\.systemui|com\.huawei\.systemui):id/(fixedPinEntry|pinEntry|passwordEntry|pin_entry|password_entry)$' -and
        $_.GetAttribute('password') -eq 'true' -and $_.GetAttribute('focused') -eq 'true' -and
        $_.GetAttribute('input-empty') -eq 'true' -and $_.GetAttribute('enabled') -eq 'true' -and
        $_.GetAttribute('bounds') -ne '[0,0][0,0]'
    })
    if ($entries.Count -ne 1) { return $false }
    $digits = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    foreach ($node in $systemNodes) {
        if ($node.GetAttribute('enabled') -ne 'true' -or $node.GetAttribute('bounds') -eq '[0,0][0,0]') { continue }
        $id = $node.GetAttribute('resource-id')
        if ($id -cmatch ':id/(?:key|keyguard_key_?)([0-9])$') { [void]$digits.Add($Matches[1]) }
        foreach ($attribute in @('text', 'content-desc')) {
            $label = $node.GetAttribute($attribute)
            if ($label -cmatch '^[0-9]$') { [void]$digits.Add($label) }
        }
    }
    if ($digits.Count -ne 10) { return $false }
    if ($SubmitExplicitly) { $SubmitExplicitly.Value = $entries[0].GetAttribute('resource-id') -cnotmatch ':id/fixedPinEntry$' }
    return $true
}
function Get-PhoneZone {
    $zoneId = (Invoke-Adb @('shell', 'getprop', 'persist.sys.timezone')).Trim()
    if (!$zoneId) { throw 'Phone timezone was not available.' }
    return [TimeZoneInfo]::FindSystemTimeZoneById($zoneId)
}
function Get-PhoneEpoch {
    $value = (Invoke-Adb @('shell', 'date', '+%s')).Trim()
    if ($value -notmatch '^\d{10,12}$') { throw 'Phone clock was not recognized.' }
    return [long]$value
}
function Get-AlarmTimes([TimeZoneInfo]$PhoneZone) {
    $alarms = Invoke-Adb @('shell', 'dumpsys', 'alarm')
    $matchesFound = [regex]::Matches($alarms, 'tag=\*walarm\*:xcom\.warof\.chosen\.LOCAL_ALARM\s+type=RTC_WAKEUP origWhen=(?<at>\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d+)')
    return @($matchesFound | ForEach-Object {
        $localTime = [datetime]::ParseExact($_.Groups['at'].Value, 'yyyy-MM-dd HH:mm:ss.FFFFFFF', [Globalization.CultureInfo]::InvariantCulture)
        $utcTime = [TimeZoneInfo]::ConvertTimeToUtc([datetime]::SpecifyKind($localTime, [DateTimeKind]::Unspecified), $PhoneZone)
        ([datetimeoffset]$utcTime).ToUnixTimeSeconds()
    } | Sort-Object -Unique)
}
function Read-AppState {
    $reply = Invoke-Adb @('shell', 'am', 'broadcast', '-n', 'xcom.warof.chosen/.BridgeReceiver', '-a', 'xcom.warof.chosen.STATE')
    return ConvertFrom-AppBridgeReply -Response $reply
}
function Test-PinQuotaConsumed([object]$Record) {
    return $null -ne $Record -and $Record.outcome -notin @('already-unlocked','unlocked-before-pin','no-input')
}
function Get-RecoverablePending([object[]]$Pending, [long]$PhoneNow) {
    # The app bridge has already validated its exact millisecond launch window.
    # One second of forward tolerance covers reading the phone's integer clock first.
    return @($Pending | Where-Object { $_.at -le ($PhoneNow + 1) * 1000 -and $_.at -ge ($PhoneNow - 120) * 1000 })
}
function Resume-AppRequest([int]$Kind, [long]$At) {
    $reply = Invoke-Adb @('shell', 'am', 'broadcast', '-n', 'xcom.warof.chosen/.BridgeReceiver', '-a', 'xcom.warof.chosen.RESUME', '--ei', 'kind', [string]$Kind, '--el', 'scheduled_at', [string]$At)
    $result = ConvertFrom-AppBridgeReply -Response $reply
    if ($result.outcome -cne 'resumed') { throw 'App resume request was not resumed.' }
}
function Set-Blocked([string]$Reason, [string]$AttemptKey) {
    Write-JsonFile $blockPath ([ordered]@{ capturedAt = [datetimeoffset]::Now.ToString('o'); serial = $Serial; reason = $Reason; attemptKey = $AttemptKey })
    Write-Event 'unlock-blocked' $Reason $AttemptKey
}
function Send-PinOnce([Security.SecureString]$Secret, [bool]$SubmitExplicitly) {
    # Only the fixed shell command is on the process command line. PIN characters
    # go to an unbuffered pipe, never to console output, a file, or adb arguments.
    $process = New-AdbProcess @('shell', '-T') $true
    $pointer = [IntPtr]::Zero
    $started = $false
    try {
        $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($Secret)
        if ($Secret.Length -lt 4 -or $Secret.Length -gt 16) { throw 'PIN credential format is invalid.' }
        for ($index = 0; $index -lt $Secret.Length; $index++) {
            $code = [Runtime.InteropServices.Marshal]::ReadInt16($pointer, 2 * $index)
            if ($code -lt 48 -or $code -gt 57) { throw 'PIN credential format is invalid.' }
        }
        [void]$process.Start()
        $started = $true
        $process.StandardInput.NewLine = "`n"
        # Drain both streams without recording their contents, including shell echo.
        $outputTask = $process.StandardOutput.ReadToEndAsync()
        $errorTask = $process.StandardError.ReadToEndAsync()
        $process.StandardInput.Write('input text ')
        for ($index = 0; $index -lt $Secret.Length; $index++) {
            $process.StandardInput.Write([char][Runtime.InteropServices.Marshal]::ReadInt16($pointer, 2 * $index))
        }
        $process.StandardInput.WriteLine()
        if ($SubmitExplicitly) { $process.StandardInput.WriteLine('input keyevent KEYCODE_ENTER') }
        $process.StandardInput.WriteLine('exit')
        $process.StandardInput.Close()
        if (!$process.WaitForExit(15000)) {
            $process.Kill($true)
            [void]$process.WaitForExit(1000)
            throw 'PIN input operation timed out.'
        }
        if ($process.ExitCode -ne 0) { throw 'PIN input operation failed.' }
        [void]$outputTask.GetAwaiter().GetResult()
        [void]$errorTask.GetAwaiter().GetResult()
    } catch {
        # Never propagate an exception carrying subprocess text or entered data.
        throw 'The single PIN input operation did not complete.'
    } finally {
        if ($pointer -ne [IntPtr]::Zero) { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer) }
        if ($started -and !$process.HasExited) {
            $process.Kill($true)
            [void]$process.WaitForExit(1000)
        }
        $process.Dispose()
    }
}
function Invoke-Unlock([string]$AttemptKey, [Security.SecureString]$Secret) {
    try {
        $state = Read-Keyguard
        if (!$state.Locked) { Write-Event 'unlock' 'already-unlocked' $AttemptKey; return 'already-unlocked' }
        if (!$state.Secure) { Set-Blocked 'secure-keyguard-not-confirmed' $AttemptKey; return 'blocked' }
        [void](Invoke-Adb @('shell', 'input', 'keyevent', 'KEYCODE_WAKEUP'))
        $size = Invoke-Adb @('shell', 'wm', 'size')
        $sizes = [regex]::Matches($size, '(?:Physical|Override) size:\s*(\d+)x(\d+)')
        if ($sizes.Count -eq 0) { throw 'Display size was not recognized.' }
        $screen = $sizes[$sizes.Count - 1]
        $width = [int]$screen.Groups[1].Value; $height = [int]$screen.Groups[2].Value
        [void](Invoke-Adb @('shell', 'input', 'swipe', [string][int]($width * .5), [string][int]($height * .82), [string][int]($width * .5), [string][int]($height * .2), '350'))
        Start-Sleep -Milliseconds 500
        $ui = Invoke-Adb @('shell', 'CLASSPATH=/data/local/tmp/autoding-ui.jar app_process /system/bin xcom.warof.chosen.tools.ReadWindow')
        $state = Read-Keyguard
        if (!$state.Locked) { Write-Event 'unlock' 'unlocked-before-pin' $AttemptKey; return 'unlocked-before-pin' }
        $submitExplicitly = $true
        if (!$state.Secure -or !(Test-SystemUiPin $ui ([ref]$submitExplicitly))) {
            Set-Blocked 'pin-ui-not-recognized' $AttemptKey
            return 'blocked'
        }
        $ui = $null
        # Fail closed across process termination: only verified success clears this.
        Set-Blocked 'pin-verification-pending' $AttemptKey
        Send-PinOnce $Secret $submitExplicitly
        $deadline = [datetime]::UtcNow.AddSeconds(5)
        do {
            $remainingMs = [int][Math]::Max(1, ($deadline - [datetime]::UtcNow).TotalMilliseconds)
            $state = Read-Keyguard $remainingMs
            if (!$state.Locked) {
                [IO.File]::Delete($blockPath)
                Write-Event 'unlock' 'verified-unlocked' $AttemptKey
                return 'verified-unlocked'
            }
            Start-Sleep -Milliseconds 400
        } while ([datetime]::UtcNow -lt $deadline)
        Set-Blocked 'pin-not-verified' $AttemptKey
    } catch {
        Set-Blocked 'unlock-operation-failed' $AttemptKey
    }
    return 'blocked'
}

$deviceMutex = $null
$mutexAcquired = $false
$credential = $null
$sleepHold = $false
try {
    $deviceMutex = [Threading.Mutex]::new($false, ('Global\AutoDingLocal-PhoneUnlock-' + $deviceKey))
    try { $mutexAcquired = $deviceMutex.WaitOne(0) }
    catch [Threading.AbandonedMutexException] { $mutexAcquired = $true }
    if (!$mutexAcquired) { throw 'Another watcher is already using this phone.' }
    [IO.File]::WriteAllText((Join-Path $OutputDirectory 'watcher.pid'), [string]$PID)
    if (Test-Path -LiteralPath $blockPath) {
        Write-Event 'watcher' 'blocked-file-present'
        Write-JsonFile (Join-Path $OutputDirectory 'status.json') ([ordered]@{ capturedAt = [datetimeoffset]::Now.ToString('o'); blocked = $true; outcome = 'blocked-file-present' })
        throw 'A previous unlock is blocked; inspect the local outcome before explicitly clearing the block.'
    }
    try { $credential = Import-Clixml -LiteralPath $CredentialPath }
    catch { throw 'Encrypted PIN credential could not be loaded by this Windows user.' }
    if ($credential.Serial -cne $Serial -or $credential.Kind -cne 'pin' -or $credential.Secret -isnot [Security.SecureString]) {
        throw 'The encrypted credential does not match this phone and PIN mode.'
    }
    [void](Invoke-Adb @('push', $UiDumpJarPath, '/data/local/tmp/autoding-ui.jar'))
    $attempts = @{}
    if (Test-Path -LiteralPath $attemptPath) {
        try {
            $stored = Get-Content -LiteralPath $attemptPath -Raw | ConvertFrom-Json -AsHashtable
            if ($stored -isnot [Collections.IDictionary]) { throw 'Invalid attempts file.' }
            $attempts = $stored
        } catch { throw 'The persisted attempt list could not be read; inspect it before restarting.' }
    }
    if (!('AutoDingUnlock.PowerState' -as [type])) {
        Add-Type -TypeDefinition 'namespace AutoDingUnlock { public static class PowerState { [System.Runtime.InteropServices.DllImport("kernel32.dll")] public static extern uint SetThreadExecutionState(uint flags); } }'
    }
    $sleepHold = [AutoDingUnlock.PowerState]::SetThreadExecutionState([uint32]2147483649) -ne 0
    if (!$sleepHold) { throw 'The watcher could not request an idle-sleep hold.' }
    Write-Event 'watcher' 'started'
    $lastOutcome = 'waiting-for-alarm'
    $lastStateSignature = ''
    do {
        if (Test-Path -LiteralPath $stopPath) { Write-Event 'watcher' 'stop-requested'; break }
        if (Test-Path -LiteralPath $blockPath) { break }
        $status = [ordered]@{ capturedAt = [datetimeoffset]::Now.ToString('o'); connected = $false; locked = $null; secure = $null; interactive = $null; ready = $false; phoneEpochSeconds = $null; nextAlarms = @(); blocked = $false; outcome = $lastOutcome }
        try {
            if ((Invoke-Adb @('get-state')).Trim() -ne 'device') { throw 'Phone is not connected.' }
            $status.connected = $true
            $state = Read-Keyguard
            $status.locked = $state.Locked; $status.secure = $state.Secure
            $status.interactive = $state.Interactive
            $phoneNow = Get-PhoneEpoch
            $status.phoneEpochSeconds = $phoneNow
            $zone = Get-PhoneZone
            $alarmTimes = @(Get-AlarmTimes $zone)
            $status.nextAlarms = @($alarmTimes | ForEach-Object { [TimeZoneInfo]::ConvertTime([datetimeoffset]::FromUnixTimeSeconds($_), $zone).ToString('o') })
            $due = @($alarmTimes | Where-Object { $_ -ge $phoneNow -and $_ -le $phoneNow + 30 })
            $pending = @()
            if (!$TestOnce) {
                $appState = Read-AppState
                $pending = @(Get-RecoverablePending @($appState.pending) $phoneNow)
            }
            $pendingRequest = if ($pending.Count) { $pending[0] } else { $null }
            $attemptKey = if ($TestOnce) { 'test-' + [guid]::NewGuid().ToString('N') }
                elseif ($pendingRequest) { 'alarm-' + [string][long][Math]::Floor($pendingRequest.at / 1000) }
                elseif ($due.Count) { 'alarm-' + [string]$due[0] } else { '' }
            if ($attemptKey) {
                $record = if ($attempts.ContainsKey($attemptKey)) { $attempts[$attemptKey] } else { $null }
                $consumed = Test-PinQuotaConsumed $record
                if (!$state.Locked) {
                    # An unlocked observation never consumes the one PIN attempt.
                    $lastOutcome = 'already-unlocked'
                    if (!$state.Interactive) { [void](Invoke-Adb @('shell', 'input', 'keyevent', 'KEYCODE_WAKEUP')) }
                } elseif (!$consumed) {
                    $attempts[$attemptKey] = [ordered]@{ startedAt = [datetimeoffset]::Now.ToString('o'); outcome = 'started'; resumeRequested = $false }
                    Write-JsonFile $attemptPath $attempts
                    Write-Event 'attempt' 'started' $attemptKey
                    $lastOutcome = Invoke-Unlock $attemptKey $credential.Secret
                    $attempts[$attemptKey].outcome = $lastOutcome
                    Write-JsonFile $attemptPath $attempts
                } else { $lastOutcome = 'pin-attempt-already-consumed' }
                $status.locked = (Read-Keyguard).Locked
                if ($pendingRequest -and !$status.locked -and !(Test-Path -LiteralPath $blockPath)) {
                    if (!$attempts.ContainsKey($attemptKey)) { $attempts[$attemptKey] = [ordered]@{ outcome = 'no-input'; resumeRequested = $false } }
                    $record = $attempts[$attemptKey]
                    $resumed = $record.Contains('resumeRequested') -and $record.resumeRequested
                    if (!$resumed) {
                        $record.resumeRequested = $true
                        Write-JsonFile $attemptPath $attempts
                        Resume-AppRequest ([int]$pendingRequest.kind) ([long]$pendingRequest.at)
                        Write-Event 'resume' 'requested-within-launch-window' $attemptKey
                    }
                }
            }
            $status.blocked = Test-Path -LiteralPath $blockPath
            $status.outcome = $lastOutcome
            $status.ready = !$status.blocked -and (!$status.locked -or $status.secure)
        } catch {
            # Device disconnects retry discovery, never a PIN attempt already recorded.
            $status.outcome = 'device-read-error'
            $status.blocked = Test-Path -LiteralPath $blockPath
        }
        Write-JsonFile (Join-Path $OutputDirectory 'status.json') $status
        $stateSignature = "connected=$($status.connected);locked=$($status.locked);ready=$($status.ready);blocked=$($status.blocked)"
        if ($stateSignature -ne $lastStateSignature) {
            Write-Event 'device-state' $stateSignature
            $lastStateSignature = $stateSignature
        }
        if ($status.blocked -or $TestOnce) { break }
        Start-Sleep -Seconds 5
    } while ($true)
} finally {
    if ($sleepHold) { [void][AutoDingUnlock.PowerState]::SetThreadExecutionState([uint32]2147483648) }
    if ($credential -and $credential.Secret -is [Security.SecureString]) { $credential.Secret.Dispose() }
    if ($mutexAcquired) { $deviceMutex.ReleaseMutex() }
    if ($deviceMutex) { $deviceMutex.Dispose() }
}
