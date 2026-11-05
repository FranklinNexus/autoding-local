#requires -Version 7.0
Set-StrictMode -Version Latest

function Get-NormalizedAttendanceLines([string[]]$Lines) {
    $seen = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    foreach ($line in $Lines) {
        if ($null -eq $line) { continue }
        $normalized = [regex]::Replace($line, '\s+', '').Replace('：', ':')
        if ($normalized -and $seen.Add($normalized)) { $normalized }
    }
}

function New-AttendanceRejection([string]$Reason) {
    return [pscustomobject][ordered]@{
        accepted = $false; reason = $Reason; pageStatus = 'rejected'
        workDate = $null; checkIn = $null; checkOut = $null
    }
}

function Get-AttendancePunch {
    param(
        [string[]]$Lines,
        [string]$Kind,
        [datetime]$LocalDate,
        [datetimeoffset]$PhoneNow,
        [TimeZoneInfo]$PhoneTimeZone
    )
    $label = if ($Kind -eq 'check-in') { '上班' } else { '下班' }
    $labels = @($Lines | Where-Object { $_ -cmatch ('^' + $label + '(?<hour>[0-9]{2}):(?<minute>[0-9]{2})$') })
    if ($labels.Count -ne 1) {
        return [pscustomobject]@{ accepted = $false; reason = $Kind + '-label-unconfirmed'; record = $null }
    }
    [void]($labels[0] -cmatch ('^' + $label + '(?<hour>[0-9]{2}):(?<minute>[0-9]{2})$'))
    if ([int]$Matches.hour -gt 23 -or [int]$Matches.minute -gt 59) {
        return [pscustomobject]@{ accepted = $false; reason = $Kind + '-label-invalid'; record = $null }
    }
    # Only a complete status line is an actual punch. Shift labels and the live
    # clock elsewhere on the screen are never candidates for these timestamps.
    $Lines = @($Lines | ForEach-Object {
        # One leading checkbox glyph is a known OCR artifact on this layout.
        # Strip it only from an otherwise complete actual-punch status line.
        if ($_ -cmatch '^[．.•✓](?:[0-9]{2}:[0-9]{2}(?::[0-9]{2})?已打卡|已打卡[0-9]{2}:[0-9]{2}(?::[0-9]{2})?)$') { $_.Substring(1) }
        else { $_ }
    })
    $actualLines = @($Lines | Where-Object { $_ -cmatch '^(?:[0-9]{2}:[0-9]{2}(?::[0-9]{2})?已打卡|已打卡[0-9]{2}:[0-9]{2}(?::[0-9]{2})?)$' })
    $unparsedStatus = @($Lines | Where-Object { $_.Contains('已打卡') -and $_ -cnotin $actualLines })
    if ($unparsedStatus.Count) {
        return [pscustomobject]@{ accepted = $false; reason = $Kind + '-status-unconfirmed'; record = $null }
    }
    $notPunched = @($Lines | Where-Object { $_ -ceq '未打卡' })
    if ($actualLines.Count -gt 1 -or ($actualLines.Count -and $notPunched.Count)) {
        return [pscustomobject]@{ accepted = $false; reason = $Kind + '-status-ambiguous'; record = $null }
    }
    if (!$actualLines.Count) {
        if ($notPunched.Count -eq 1) {
            return [pscustomobject]@{ accepted = $true; reason = ''; record = $null }
        }
        return [pscustomobject]@{ accepted = $false; reason = $Kind + '-status-unconfirmed'; record = $null }
    }
    $timeText = $actualLines[0].Replace('已打卡', '')
    [void]($timeText -cmatch '^(?<hour>[0-9]{2}):(?<minute>[0-9]{2})(?::(?<second>[0-9]{2}))?$')
    $hour = [int]$Matches.hour; $minute = [int]$Matches.minute
    $hasSeconds = $Matches.ContainsKey('second')
    $second = if ($hasSeconds) { [int]$Matches.second } else { 0 }
    if ($hour -gt 23 -or $minute -gt 59 -or $second -gt 59) {
        return [pscustomobject]@{ accepted = $false; reason = $Kind + '-time-invalid'; record = $null }
    }
    $localTime = [datetime]::SpecifyKind($LocalDate.Date.AddHours($hour).AddMinutes($minute).AddSeconds($second), [DateTimeKind]::Unspecified)
    if ($PhoneTimeZone.IsInvalidTime($localTime) -or $PhoneTimeZone.IsAmbiguousTime($localTime)) {
        return [pscustomobject]@{ accepted = $false; reason = $Kind + '-time-zone-ambiguous'; record = $null }
    }
    $timestamp = [datetimeoffset][TimeZoneInfo]::ConvertTimeToUtc($localTime, $PhoneTimeZone)
    if ($timestamp -gt $PhoneNow) {
        return [pscustomobject]@{ accepted = $false; reason = $Kind + '-time-in-future'; record = $null }
    }
    return [pscustomobject]@{
        accepted = $true; reason = ''
        record = [pscustomobject][ordered]@{
            # For minute precision, this is the start of the observed minute,
            # not proof that the actual punch happened at second zero.
            epochMs = $timestamp.ToUnixTimeMilliseconds()
            precision = if ($hasSeconds) { 'second' } else { 'minute' }
            source = 'attendance-screen'
        }
    }
}

function ConvertFrom-AttendanceObservation {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][AllowNull()][AllowEmptyCollection()][string[]]$MorningLines,
        [Parameter(Mandatory)][AllowNull()][AllowEmptyCollection()][string[]]$EveningLines,
        [Parameter(Mandatory)][AllowNull()][AllowEmptyCollection()][string[]]$DateLines,
        [Parameter(Mandatory)][datetimeoffset]$PhoneNow,
        [Parameter(Mandatory)][TimeZoneInfo]$PhoneTimeZone,
        [Parameter(Mandatory)][bool]$AttendancePageConfirmed
    )
    if (!$AttendancePageConfirmed) { return New-AttendanceRejection 'attendance-page-unconfirmed' }
    $morning = @(Get-NormalizedAttendanceLines $MorningLines)
    $evening = @(Get-NormalizedAttendanceLines $EveningLines)
    $dates = @(Get-NormalizedAttendanceLines $DateLines)
    $actions = @($dates | Where-Object { $_ -cin @('上班打卡', '下班打卡') })
    if ($actions.Count -ne 1) { return New-AttendanceRejection 'attendance-action-unconfirmed' }

    $phoneLocalNow = [TimeZoneInfo]::ConvertTime($PhoneNow, $PhoneTimeZone)
    $dateRows = @($dates | Where-Object { $_ -cmatch '^(?:[0-9]{4}年)?[0-9]{1,2}月[0-9]{1,2}日(?:周|星期)[一二三四五六日天]$' })
    if ($dateRows.Count -ne 1) { return New-AttendanceRejection 'work-date-unconfirmed' }
    [void]($dateRows[0] -cmatch '^(?:(?<year>[0-9]{4})年)?(?<month>[0-9]{1,2})月(?<day>[0-9]{1,2})日(?:周|星期)(?<weekday>[一二三四五六日天])$')
    $year = if ($Matches.ContainsKey('year')) { [int]$Matches.year } else { $phoneLocalNow.Year }
    $month = [int]$Matches.month; $day = [int]$Matches.day; $weekday = $Matches.weekday
    try { $workDate = [datetime]::new($year, $month, $day) }
    catch { return New-AttendanceRejection 'work-date-invalid' }
    if ($workDate.Date -ne $phoneLocalNow.Date) { return New-AttendanceRejection 'work-date-not-today' }
    $weekdays = @('日', '一', '二', '三', '四', '五', '六')
    $expectedWeekday = $weekdays[[int]$workDate.DayOfWeek]
    if ($weekday -cne $expectedWeekday -and !($expectedWeekday -ceq '日' -and $weekday -ceq '天')) {
        return New-AttendanceRejection 'work-weekday-mismatch'
    }

    $checkIn = Get-AttendancePunch $morning 'check-in' $workDate $PhoneNow $PhoneTimeZone
    if (!$checkIn.accepted) { return New-AttendanceRejection $checkIn.reason }
    $checkOut = Get-AttendancePunch $evening 'check-out' $workDate $PhoneNow $PhoneTimeZone
    if (!$checkOut.accepted) { return New-AttendanceRejection $checkOut.reason }
    if ($checkIn.record -and $actions[0] -ceq '上班打卡') { return New-AttendanceRejection 'attendance-action-record-conflict' }
    if ($checkIn.record -and $checkOut.record -and $checkOut.record.epochMs -lt $checkIn.record.epochMs) {
        return New-AttendanceRejection 'checkout-before-checkin'
    }
    $pageStatus = if ($checkIn.record -and $checkOut.record) { 'complete' }
        elseif ($checkIn.record) { 'check-in-recorded' }
        elseif ($checkOut.record) { 'check-out-recorded' } else { 'no-records' }
    return [pscustomobject][ordered]@{
        accepted = $true; reason = ''; pageStatus = $pageStatus
        workDate = $workDate.ToString('yyyy-MM-dd', [Globalization.CultureInfo]::InvariantCulture)
        checkIn = $checkIn.record; checkOut = $checkOut.record
    }
}

Export-ModuleMember -Function ConvertFrom-AttendanceObservation
