#requires -Version 7.0
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
Import-Module (Join-Path $PSScriptRoot '..\tools\AttendanceScreen.psm1') -Force
$zone = [TimeZoneInfo]::FindSystemTimeZoneById('Asia/Shanghai')
$now = [datetimeoffset]::Parse('2026-11-05T10:47:17+08:00')
$script:checks = 0
function Assert-Condition([bool]$Condition, [string]$Message) {
    if (!$Condition) { throw $Message }
    $script:checks++
}
function Read-Fixture {
    param(
        [string[]]$Morning = @('上班09:00', '09:18已打卡'),
        [string[]]$Evening = @('下班18:18', '未打卡'),
        [string[]]$Date = @('下班打卡', '11月5日 周四', '10:47:17'),
        [datetimeoffset]$At = $now,
        [bool]$Confirmed = $true
    )
    ConvertFrom-AttendanceObservation -MorningLines $Morning -EveningLines $Evening -DateLines $Date -PhoneNow $At -PhoneTimeZone $zone -AttendancePageConfirmed $Confirmed
}
$result = Read-Fixture
Assert-Condition $result.accepted 'Actual current-day check-in should be accepted.'
Assert-Condition ($result.pageStatus -ceq 'check-in-recorded') 'Check-in status was lost.'
Assert-Condition ($result.workDate -ceq '2026-11-05') 'Current phone work date was not retained.'
$expected = [datetimeoffset]::Parse('2026-11-05T09:18:00+08:00').ToUnixTimeMilliseconds()
Assert-Condition ($result.checkIn.epochMs -eq $expected) 'Shift 09:00 or live clock was mistaken for actual 09:18.'
Assert-Condition ($result.checkIn.precision -ceq 'minute' -and $result.checkIn.source -ceq 'attendance-screen') 'Minute precision/source was lost.'
Assert-Condition ($null -eq $result.checkOut) 'Shift 18:18 was mistaken for an actual checkout.'
$result = Read-Fixture -Morning @('上 班 09 ： 00', '． 09 ： 18 已 打 卡') -Evening @('下 班 18 ： 18', '未 打 卡') -Date @('下 班 打 卡', '1 1 月 5 日 周 四', '10 ： 47 ： 17')
Assert-Condition ($result.accepted -and $result.checkIn.epochMs -eq $expected -and $null -eq $result.checkOut) 'Actual Huawei OCR fixture failed.'
foreach ($prefix in @('.', '•', '✓')) {
    $result = Read-Fixture -Morning @('上班09:00', ($prefix + '09:18已打卡'))
    Assert-Condition ($result.accepted -and $result.checkIn.epochMs -eq $expected) 'A single known checkbox OCR prefix should be supported.'
}

$result = Read-Fixture -Morning @('上班 09：00', '已打卡 09：18：04')
$expected = [datetimeoffset]::Parse('2026-11-05T09:18:04+08:00').ToUnixTimeMilliseconds()
Assert-Condition ($result.accepted -and $result.checkIn.epochMs -eq $expected -and $result.checkIn.precision -ceq 'second') 'Chinese colon, whitespace, reverse status or seconds parsing failed.'
$result = Read-Fixture -Morning @('上班09:00', '未打卡') -Date @('上班打卡', '11月5日星期四')
Assert-Condition ($result.accepted -and $result.pageStatus -ceq 'no-records' -and $null -eq $result.checkIn -and $null -eq $result.checkOut) 'Explicit unpunched cards should remain empty.'
$result = Read-Fixture -Evening @('下班18:18', '18:20已打卡') -At ([datetimeoffset]::Parse('2026-11-05T18:25:00+08:00'))
Assert-Condition ($result.accepted -and $result.pageStatus -ceq 'complete' -and $result.checkOut.precision -ceq 'minute') 'Complete current-day records should be accepted.'
$result = Read-Fixture -Morning @('上 班 09 ： 00', '． 09 ： 18 已 打 卡') -Evening @('下 班 18 ： 18', '． 18 ： 22 已 打 卡') -Date @('下 班 打 卡', '1 1 月 5 日 周 四', '18 ： 23 ： 23') -At ([datetimeoffset]::Parse('2026-11-05T18:23:23+08:00'))
$actualCheckout = [datetimeoffset]::Parse('2026-11-05T18:22:00+08:00').ToUnixTimeMilliseconds()
Assert-Condition ($result.accepted -and $result.pageStatus -ceq 'complete') 'Observed checkout card OCR lines should form a complete shift.'
Assert-Condition ($result.checkIn.epochMs -eq [datetimeoffset]::Parse('2026-11-05T09:18:00+08:00').ToUnixTimeMilliseconds() -and $result.checkOut.epochMs -eq $actualCheckout) 'Observed card lines should yield the actual 09:18 and 18:22 minute receipts.'

$rejections = @(
    @{ Params = @{ Confirmed = $false }; Reason = 'attendance-page-unconfirmed' },
    @{ Params = @{ Date = @('11月5日周四', '10:47:17') }; Reason = 'attendance-action-unconfirmed' },
    @{ Params = @{ Date = @('下班打卡', '10:47:17') }; Reason = 'work-date-unconfirmed' },
    @{ Params = @{ Date = @('下班打卡', '11月4日周三') }; Reason = 'work-date-not-today' },
    @{ Params = @{ Date = @('下班打卡', '11月6日周五') }; Reason = 'work-date-not-today' },
    @{ Params = @{ Date = @('下班打卡', '2025年11月5日周三') }; Reason = 'work-date-not-today' },
    @{ Params = @{ Date = @('下班打卡', '11月5日周五') }; Reason = 'work-weekday-mismatch' },
    @{ Params = @{ Date = @('下班打卡', '11月5日周四', '11月4日周三') }; Reason = 'work-date-unconfirmed' },
    @{ Params = @{ Morning = @('09:18已打卡') }; Reason = 'check-in-label-unconfirmed' },
    @{ Params = @{ Morning = @('上班09:00') }; Reason = 'check-in-status-unconfirmed' },
    @{ Params = @{ Morning = @('上班09:00', 'O9:18已打卡') }; Reason = 'check-in-status-unconfirmed' },
    @{ Params = @{ Morning = @('上班09:00', 'O9:18已打卡', '未打卡') }; Reason = 'check-in-status-unconfirmed' },
    @{ Params = @{ Morning = @('上班09:00', '..09:18已打卡') }; Reason = 'check-in-status-unconfirmed' },
    @{ Params = @{ Morning = @('上班09:00', 'X09:18已打卡') }; Reason = 'check-in-status-unconfirmed' },
    @{ Params = @{ Morning = @('上班09:00', '09:18已打卡', '09:19已打卡') }; Reason = 'check-in-status-ambiguous' },
    @{ Params = @{ Morning = @('上班09:00', '09:18已打卡', '未打卡') }; Reason = 'check-in-status-ambiguous' },
    @{ Params = @{ Morning = @('上班09:00', '25:18已打卡') }; Reason = 'check-in-time-invalid' },
    @{ Params = @{ Morning = @('上班09:00', '09:68已打卡') }; Reason = 'check-in-time-invalid' },
    @{ Params = @{ Morning = @('上班09:00', '11:18已打卡') }; Reason = 'check-in-time-in-future' },
    @{ Params = @{ Evening = @('下班18:18', '09:00已打卡') }; Reason = 'checkout-before-checkin' },
    @{ Params = @{ Evening = @('下班18:18', '18:18') }; Reason = 'check-out-status-unconfirmed' },
    @{ Params = @{ Evening = @('下班18:18', '18:22已打卡更新打卡'); At = [datetimeoffset]::Parse('2026-11-05T18:23:23+08:00') }; Reason = 'check-out-status-unconfirmed' },
    @{ Params = @{ Date = @('上班打卡', '11月5日周四') }; Reason = 'attendance-action-record-conflict' }
)
foreach ($fixture in $rejections) {
    $arguments = $fixture.Params
    $result = Read-Fixture @arguments
    Assert-Condition (!$result.accepted -and $result.reason -ceq $fixture.Reason) ('Expected conservative rejection: ' + $fixture.Reason)
    Assert-Condition ($null -eq $result.checkIn -and $null -eq $result.checkOut) 'Rejected observation leaked partial records.'
}
$result = Read-Fixture -At ([datetimeoffset]::Parse('2026-11-05T02:47:17Z'))
Assert-Condition ($result.accepted -and $result.workDate -ceq '2026-11-05') 'Phone timezone should decide the local work date.'
$result = Read-Fixture -Morning @('上班09:00', '00:10已打卡') -At ([datetimeoffset]::Parse('2026-11-04T16:30:00Z'))
Assert-Condition ($result.accepted -and $result.workDate -ceq '2026-11-05') 'UTC previous day must still use the phone local date.'
$serialized = Read-Fixture | ConvertTo-Json -Depth 6 -Compress
Assert-Condition ($serialized -notmatch 'MorningLines|EveningLines|DateLines|上班09:00|下班18:18|10:47:17') 'Output should contain parsed records, not OCR input.'
Write-Output "PASS: $script:checks attendance observation checks"
