#requires -Version 7.0
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
Import-Module (Join-Path $PSScriptRoot '..\tools\AttendanceScreen.psm1') -Force
$zone = [TimeZoneInfo]::FindSystemTimeZoneById('Asia/Shanghai')
$fixture = @{
    MorningLines = @('上 班 09 ： 00', '． 09 ： 18 已 打 卡')
    EveningLines = @('下 班 18 ： 18', '18 ： 22 已 打 卡')
    DateLines = @('下 班 打 卡', '1 1 月 5 日 周 四', '18 ： 25 ： 00')
    PhoneNow = [datetimeoffset]::Parse('2026-11-05T18:25:00+08:00')
    PhoneTimeZone = $zone
    AttendancePageConfirmed = $true
}
$observation = ConvertFrom-AttendanceObservation @fixture
if (!$observation.accepted -or $observation.pageStatus -cne 'complete' -or
    $observation.workDate -cne '2026-11-05' -or
    $observation.checkIn.precision -cne 'minute' -or
    $observation.checkOut.precision -cne 'minute') {
    throw 'Synthetic attendance page did not produce a complete verified shift.'
}
$expectedIn = [datetimeoffset]::Parse('2026-11-05T09:18:00+08:00').ToUnixTimeMilliseconds()
$expectedOut = [datetimeoffset]::Parse('2026-11-05T18:22:00+08:00').ToUnixTimeMilliseconds()
if ($observation.checkIn.epochMs -ne $expectedIn -or $observation.checkOut.epochMs -ne $expectedOut) {
    throw 'Synthetic punch timestamps do not match the observed lines.'
}
# Compile a disposable copy of the real Scheduler with only its wall-clock
# calls redirected. The production source and installed APK stay untouched.
$repository = Split-Path $PSScriptRoot -Parent
$output = Join-Path $repository 'build\test-full-dryrun'
$instrumentedSource = Join-Path $output 'source\xcom\warof\chosen\Scheduler.java'
New-Item -ItemType Directory -Force -Path (Split-Path $instrumentedSource -Parent) | Out-Null
$source = [IO.File]::ReadAllText((Join-Path $repository 'src\xcom\warof\chosen\Scheduler.java'))
$clockCall = 'System.currentTimeMillis()'
if (!$source.Contains($clockCall)) { throw 'Scheduler wall-clock seam could not be instrumented.' }
$instrumented = $source.Replace($clockCall, 'FixtureClock.now()')
if ($instrumented.Contains($clockCall)) { throw 'Scheduler wall-clock instrumentation is incomplete.' }
[IO.File]::WriteAllText($instrumentedSource, $instrumented, [Text.UTF8Encoding]::new($false))
$fixtureSources = @(Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'fixtures\scheduler') -Recurse -Filter '*.java' | ForEach-Object { $_.FullName })
$plannerSource = Join-Path $repository 'src\xcom\warof\chosen\AttendancePlanner.java'
$javac = (Get-Command javac -CommandType Application -ErrorAction Stop).Source
$java = (Get-Command java -CommandType Application -ErrorAction Stop).Source
& $javac -encoding UTF-8 -d $output @fixtureSources $plannerSource $instrumentedSource
if ($LASTEXITCODE -ne 0) { throw 'Instrumented dry-run fixture compilation failed.' }
& $java -cp $output xcom.warof.chosen.FullDryRunHarness $observation.checkIn.epochMs $observation.checkOut.epochMs
if ($LASTEXITCODE -ne 0) { throw 'Full-shift dry run failed.' }
