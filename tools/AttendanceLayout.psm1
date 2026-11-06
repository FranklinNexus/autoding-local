#requires -Version 5.1
Set-StrictMode -Version Latest
$script:BuiltInLayouts = [Runtime.CompilerServices.ConditionalWeakTable[object, object]]::new()

function Assert-LayoutKeys {
    param([object]$Value, [string[]]$Names)
    if ($null -eq $Value -or $Value -isnot [pscustomobject]) { throw 'Layout values must be JSON objects.' }
    $actual = @($Value.PSObject.Properties | ForEach-Object { $_.Name })
    if ($actual.Count -ne $Names.Count) { throw 'Layout object has missing or unexpected fields.' }
    foreach ($name in $actual) {
        if ($name -cnotin $Names) { throw 'Layout field names must match the schema exactly.' }
    }
}

function Test-LayoutInteger {
    param([object]$Value)
    return $Value -is [int] -or $Value -is [long]
}

function ConvertTo-ValidatedLayout {
    param([object]$Profile, [bool]$IsBuiltInProfile = $false)
    Assert-LayoutKeys $Profile @('schemaVersion', 'id', 'referenceWidth', 'referenceHeight', 'aspectTolerance', 'crops')
    if (!(Test-LayoutInteger $Profile.schemaVersion) -or $Profile.schemaVersion -ne 1) { throw 'Unsupported layout schema version.' }
    if ($Profile.id -isnot [string] -or $Profile.id -cnotmatch '^[A-Za-z][A-Za-z0-9._-]{0,63}$') { throw 'Layout id is invalid.' }
    foreach ($size in @($Profile.referenceWidth, $Profile.referenceHeight)) {
        if (!(Test-LayoutInteger $size) -or $size -lt 1 -or $size -gt 16384) { throw 'Reference dimensions must be integers from 1 to 16384.' }
    }
    $tolerance = $Profile.aspectTolerance
    if ($tolerance -isnot [int] -and $tolerance -isnot [long] -and $tolerance -isnot [double] -and $tolerance -isnot [decimal]) {
        throw 'Aspect tolerance must be numeric.'
    }
    $tolerance = [double]$tolerance
    if ([double]::IsNaN($tolerance) -or [double]::IsInfinity($tolerance) -or $tolerance -lt 0 -or $tolerance -gt 0.05) {
        throw 'Aspect tolerance must be between 0 and 0.05.'
    }
    Assert-LayoutKeys $Profile.crops @('morning', 'evening', 'date')
    $bounds = @{}
    foreach ($name in @('morning', 'evening', 'date')) {
        $values = $Profile.crops.$name
        if ($values -isnot [array] -or $values.Count -ne 4) { throw 'Each crop must have four integer bounds.' }
        foreach ($value in $values) {
            if (!(Test-LayoutInteger $value)) { throw 'Crop bounds must be integers.' }
        }
        $left, $top, $right, $bottom = $values
        if ($left -lt 0 -or $top -lt 0 -or $right -gt $Profile.referenceWidth -or $bottom -gt $Profile.referenceHeight -or
            $left -ge $right -or $top -ge $bottom) { throw 'Crop bounds are empty, reversed, or outside the reference image.' }
        if ($left -eq 0 -and $top -eq 0 -and $right -eq $Profile.referenceWidth -and $bottom -eq $Profile.referenceHeight) {
            throw 'A crop must select a card or date region rather than the full image.'
        }
        $bounds[$name] = [int[]]$values
    }
    return [pscustomobject]@{
        SchemaVersion = 1
        Id = [string]$Profile.id
        ReferenceWidth = [int]$Profile.referenceWidth
        ReferenceHeight = [int]$Profile.referenceHeight
        AspectTolerance = $tolerance
        IsBuiltInProfile = $IsBuiltInProfile
        Crops = [pscustomobject]@{ Morning = $bounds.morning; Evening = $bounds.evening; Date = $bounds.date }
    }
}

function Test-KnownReferenceLayout {
    param([object]$Layout)
    return $Layout.Id -ceq 'HuaweiAttendancePortraitV1' -and $Layout.ReferenceWidth -eq 1080 -and
        $Layout.ReferenceHeight -eq 2400 -and $Layout.AspectTolerance -eq 0.005 -and
        ($Layout.Crops.Morning -join ',') -ceq '44,590,518,730' -and
        ($Layout.Crops.Evening -join ',') -ceq '506,590,806,730' -and
        ($Layout.Crops.Date -join ',') -ceq '310,1320,780,1605'
}

function Get-AttendanceLayout {
    [CmdletBinding()]
    param([string]$LayoutPath)
    $defaultPath = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\layouts\huawei-attendance-portrait-v1.json'))
    if (!$LayoutPath) { $LayoutPath = $defaultPath }
    $path = [IO.Path]::GetFullPath($LayoutPath)
    $info = Get-Item -LiteralPath $path -ErrorAction Stop
    if ($info -isnot [IO.FileInfo] -or $info.Length -gt 16384) { throw 'Layout must be a JSON file of at most 16384 bytes.' }
    $json = [IO.File]::ReadAllText($path, [Text.UTF8Encoding]::new($false, $true))
    # PowerShell versions differ in their permissiveness for comments and
    # trailing commas. Mask valid strings before rejecting those extensions.
    $stringPattern = '"(?:\\["\\/bfnrt]|\\u[0-9A-Fa-f]{4}|[^"\\\x00-\x1F])*"'
    $masked = [regex]::Replace($json, $stringPattern, '""')
    if ($masked -match '/|,\s*[}\]]') { throw 'Layout must use strict JSON syntax.' }
    foreach ($atom in [regex]::Matches($masked, '[^\s{}\[\],:]+')) {
        if ($atom.Value -cnotmatch '^(?:""|-?(?:0|[1-9][0-9]*)(?:\.[0-9]+)?(?:[eE][+-]?[0-9]+)?)$') {
            throw 'Layout contains a non-JSON value or unquoted field.'
        }
    }
    $keys = [Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
    foreach ($token in [regex]::Matches($json, $stringPattern)) {
        $after = $json.Substring($token.Index + $token.Length).TrimStart()
        if ($after.StartsWith(':', [StringComparison]::Ordinal)) {
            $key = $token.Value | ConvertFrom-Json -ErrorAction Stop
            if (!$keys.Add([string]$key)) { throw 'Duplicate layout fields are not allowed.' }
        }
    }
    $profile = $json | ConvertFrom-Json -ErrorAction Stop
    $validated = ConvertTo-ValidatedLayout $profile
    $builtIn = [string]::Equals($path, $defaultPath, [StringComparison]::OrdinalIgnoreCase) -and (Test-KnownReferenceLayout $validated)
    $validated.IsBuiltInProfile = $builtIn
    if ($builtIn) { $script:BuiltInLayouts.Add($validated, [object]::new()) }
    return $validated
}

function ConvertTo-AttendanceLayout {
    [CmdletBinding()]
    param([Parameter(Mandatory = $true)][object]$Profile)
    return ConvertTo-ValidatedLayout $Profile
}

function Resolve-AttendanceLayout {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory = $true)][object]$Layout,
        [Parameter(Mandatory = $true)][ValidateRange(1, 16384)][int]$ImageWidth,
        [Parameter(Mandatory = $true)][ValidateRange(1, 16384)][int]$ImageHeight,
        [ValidateRange(0, 16384)][int]$MaxCropDimension = 0
    )
    # Validate again so callers cannot silently bypass crop checks by editing
    # the object returned by Get-AttendanceLayout.
    $raw = [pscustomobject]@{
        schemaVersion = $Layout.SchemaVersion; id = $Layout.Id
        referenceWidth = $Layout.ReferenceWidth; referenceHeight = $Layout.ReferenceHeight
        aspectTolerance = $Layout.AspectTolerance
        crops = [pscustomobject]@{ morning = $Layout.Crops.Morning; evening = $Layout.Crops.Evening; date = $Layout.Crops.Date }
    }
    $validated = ConvertTo-ValidatedLayout $raw
    $origin = $null
    $builtIn = $script:BuiltInLayouts.TryGetValue($Layout, [ref]$origin) -and (Test-KnownReferenceLayout $validated)
    $referenceRatio = $validated.ReferenceWidth / [double]$validated.ReferenceHeight
    $actualRatio = $ImageWidth / [double]$ImageHeight
    if ([Math]::Abs($actualRatio - $referenceRatio) -gt $validated.AspectTolerance) {
        throw 'Image aspect ratio does not match the selected layout.'
    }
    $resolved = @{}
    foreach ($name in @('Morning', 'Evening', 'Date')) {
        $b = $validated.Crops.$name
        $left = [int][Math]::Floor($b[0] * $ImageWidth / [double]$validated.ReferenceWidth)
        $top = [int][Math]::Floor($b[1] * $ImageHeight / [double]$validated.ReferenceHeight)
        $right = [int][Math]::Ceiling($b[2] * $ImageWidth / [double]$validated.ReferenceWidth)
        $bottom = [int][Math]::Ceiling($b[3] * $ImageHeight / [double]$validated.ReferenceHeight)
        $width = $right - $left; $height = $bottom - $top
        if ($left -lt 0 -or $top -lt 0 -or $right -gt $ImageWidth -or $bottom -gt $ImageHeight -or $width -le 0 -or $height -le 0) {
            throw 'Scaled crop is outside the actual image.'
        }
        if ($MaxCropDimension -gt 0 -and ($width -gt $MaxCropDimension -or $height -gt $MaxCropDimension)) {
            throw 'Scaled crop exceeds the OCR engine dimension limit.'
        }
        $resolved[$name] = [int[]]@($left, $top, $right, $bottom)
    }
    $scaled = $ImageWidth -ne $validated.ReferenceWidth -or $ImageHeight -ne $validated.ReferenceHeight
    return [pscustomobject]@{
        Layout = $validated.Id
        ImageWidth = $ImageWidth; ImageHeight = $ImageHeight
        ReferenceWidth = $validated.ReferenceWidth; ReferenceHeight = $validated.ReferenceHeight
        IsScaled = $scaled
        RequiresDeviceAcceptance = $scaled -or !$builtIn
        Crops = [pscustomobject]@{ Morning = $resolved.Morning; Evening = $resolved.Evening; Date = $resolved.Date }
    }
}

Export-ModuleMember -Function Get-AttendanceLayout, ConvertTo-AttendanceLayout, Resolve-AttendanceLayout
