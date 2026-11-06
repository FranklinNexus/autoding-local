#requires -Version 5.1
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
Import-Module (Join-Path $PSScriptRoot '..\tools\AttendanceLayout.psm1') -Force
$script:checks = 0
function Assert-Layout([bool]$Condition, [string]$Message) {
    if (!$Condition) { throw $Message }
    $script:checks++
}
function Assert-LayoutRejected([scriptblock]$Action, [string]$Message) {
    $rejected = $false
    try { & $Action | Out-Null } catch { $rejected = $true }
    Assert-Layout $rejected $Message
}
$repository = Split-Path $PSScriptRoot -Parent
$directory = Join-Path $repository ('build\test-layout-' + [guid]::NewGuid().ToString('N'))
[void][IO.Directory]::CreateDirectory($directory)
$profilePath = Join-Path $directory 'profile.json'
$defaultPath = Join-Path $repository 'layouts\huawei-attendance-portrait-v1.json'
$defaultJson = [IO.File]::ReadAllText($defaultPath)
function Read-TestProfile([string]$Json) {
    [IO.File]::WriteAllText($profilePath, $Json, [Text.UTF8Encoding]::new($false))
    return Get-AttendanceLayout -LayoutPath $profilePath
}
try {
    $default = Get-AttendanceLayout
    Assert-Layout ($default.Id -ceq 'HuaweiAttendancePortraitV1') 'Default layout id changed.'
    Assert-Layout ($default.ReferenceWidth -eq 1080 -and $default.ReferenceHeight -eq 2400) 'Default reference dimensions changed.'
    Assert-Layout (($default.Crops.Morning -join ',') -ceq '44,590,518,730') 'Default morning crop changed.'
    Assert-Layout (($default.Crops.Evening -join ',') -ceq '506,590,806,730') 'Default evening crop changed.'
    Assert-Layout (($default.Crops.Date -join ',') -ceq '310,1320,780,1605') 'Default date crop changed.'
    $resolved = Resolve-AttendanceLayout -Layout $default -ImageWidth 1080 -ImageHeight 2400 -MaxCropDimension 4096
    Assert-Layout (!$resolved.IsScaled -and !$resolved.RequiresDeviceAcceptance) 'Known reference should retain its device acceptance marker.'
    Assert-Layout (($resolved.Crops.Evening -join ',') -ceq '506,590,806,730') 'Reference crop must remain exact.'
    $scaled = Resolve-AttendanceLayout -Layout $default -ImageWidth 720 -ImageHeight 1600
    Assert-Layout ($scaled.IsScaled -and $scaled.RequiresDeviceAcceptance) 'Scaled reference must require device acceptance.'
    Assert-Layout (($scaled.Crops.Morning -join ',') -ceq '29,393,346,487') 'Scaling must floor left/top and ceil right/bottom.'
    Assert-Layout (($scaled.Crops.Date -join ',') -ceq '206,880,520,1070') 'Scaled date crop is incorrect.'
    $large = Resolve-AttendanceLayout -Layout $default -ImageWidth 1440 -ImageHeight 3200
    Assert-Layout (($large.Crops.Evening -join ',') -ceq '674,786,1075,974' -and $large.RequiresDeviceAcceptance) 'Larger same-ratio layout must scale and remain unverified.'
    [void](Resolve-AttendanceLayout -Layout $default -ImageWidth 1080 -ImageHeight 2390)
    Assert-Layout $true 'Small ratio differences within tolerance should resolve.'
    Assert-LayoutRejected { Resolve-AttendanceLayout -Layout $default -ImageWidth 1080 -ImageHeight 1920 } 'Different ratio must require a custom profile.'
    Assert-LayoutRejected { Resolve-AttendanceLayout -Layout $default -ImageWidth 0 -ImageHeight 2400 } 'Zero image width was accepted.'
    Assert-LayoutRejected { Resolve-AttendanceLayout -Layout $default -ImageWidth 1080 -ImageHeight 20000 } 'Excessive image size was accepted.'
    Assert-LayoutRejected { Resolve-AttendanceLayout -Layout $default -ImageWidth 1080 -ImageHeight 2400 -MaxCropDimension 473 } 'OCR limit must check crop dimensions.'
    [void](Resolve-AttendanceLayout -Layout $default -ImageWidth 1080 -ImageHeight 2400 -MaxCropDimension 474)
    Assert-Layout $true 'An exact OCR crop dimension limit should pass.'
    $copy = Read-TestProfile $defaultJson
    $copy.IsBuiltInProfile = $true
    Assert-Layout ((Resolve-AttendanceLayout $copy 1080 2400).RequiresDeviceAcceptance) 'Custom file must not inherit acceptance by copying the built-in id and coordinates.'
    $default.Crops.Morning[0] = 45
    Assert-Layout ((Resolve-AttendanceLayout $default 1080 2400).RequiresDeviceAcceptance) 'Editing an already-loaded built-in crop must remove acceptance.'
    $default.Crops.Morning[0] = -1
    Assert-LayoutRejected { Resolve-AttendanceLayout $default 1080 2400 } 'Mutating loaded crop bounds must be revalidated.'

    $generator = Join-Path $repository 'tools\New-AttendanceLayout.ps1'
    $generated = Join-Path $directory 'generated.json'
    $arguments = @{
        Id = 'SyntheticPortraitConfig'; ReferenceWidth = 1000; ReferenceHeight = 2000
        MorningBounds = @(10, 100, 400, 300); EveningBounds = @(510, 100, 900, 300)
        DateBounds = @(300, 900, 700, 1200); OutputPath = $generated; AspectTolerance = 0.001
    }
    $created = & $generator @arguments
    Assert-Layout ([IO.File]::Exists($generated) -and $created.RequiresDeviceAcceptance) 'Generator should write a validated custom profile.'
    $custom = Get-AttendanceLayout $generated
    $customResolved = Resolve-AttendanceLayout $custom 1500 3000
    Assert-Layout ($customResolved.Layout -ceq 'SyntheticPortraitConfig' -and $customResolved.RequiresDeviceAcceptance) 'Custom profile id or acceptance flag was lost.'
    Assert-Layout (($customResolved.Crops.Evening -join ',') -ceq '765,150,1350,450') 'Custom aspect-ratio crop scaling failed.'
    Assert-LayoutRejected { & $generator @arguments } 'Generator overwrote an existing file without Force.'
    $arguments.Id = 'SyntheticUpdatedConfig'
    [void](& $generator @arguments -Force)
    Assert-Layout ((Get-AttendanceLayout $generated).Id -ceq 'SyntheticUpdatedConfig') 'Force did not replace a valid profile.'
    $before = [IO.File]::ReadAllText($generated)
    $arguments.MorningBounds = @(-1, 100, 400, 300)
    Assert-LayoutRejected { & $generator @arguments -Force } 'Generator accepted out-of-image bounds.'
    Assert-Layout ([IO.File]::ReadAllText($generated) -ceq $before) 'Failed validation changed an existing output file.'
    $arguments.OutputPath = Join-Path $directory 'invalid.json'
    Assert-LayoutRejected { & $generator @arguments } 'Invalid profile unexpectedly generated.'
    Assert-Layout (![IO.File]::Exists($arguments.OutputPath)) 'Invalid generator input created an output file.'

    $invalidProfiles = @(
        ($defaultJson.Replace('"schemaVersion": 1', '"schemaVersion": 2')),
        ($defaultJson.Replace('"schemaVersion": 1', '"schemaVersion": "1"')),
        ($defaultJson.Replace('"referenceWidth": 1080', '"referenceWidth": 0')),
        ($defaultJson.Replace('"referenceWidth": 1080', '"referenceWidth": 1080.0')),
        ($defaultJson.Replace('"referenceHeight": 2400', '"referenceHeight": 16385')),
        ($defaultJson.Replace('"aspectTolerance": 0.005', '"aspectTolerance": 0.5')),
        ($defaultJson.Replace('"aspectTolerance": 0.005', '"aspectTolerance": -0.001')),
        ($defaultJson.Replace('"id": "HuaweiAttendancePortraitV1"', '"id": "../invalid"')),
        ($defaultJson.Replace('"schemaVersion": 1,', '"schemaVersion": 1, "extra": 1,')),
        ($defaultJson.Replace('"schemaVersion": 1,', '"schemaVersion": 1, "SchemaVersion": 1,')),
        ($defaultJson.Replace('"schemaVersion": 1,', '"schemaVersion": 1, "schemaVersion": 1,')),
        ($defaultJson.Replace('"morning": [44, 590, 518, 730]', '"morning": [44, 590, 518]')),
        ($defaultJson.Replace('"morning": [44, 590, 518, 730]', '"morning": [-1, 590, 518, 730]')),
        ($defaultJson.Replace('"morning": [44, 590, 518, 730]', '"morning": [44, 590, 1081, 730]')),
        ($defaultJson.Replace('"morning": [44, 590, 518, 730]', '"morning": [44, 590, 518, 2401]')),
        ($defaultJson.Replace('"morning": [44, 590, 518, 730]', '"morning": [518, 590, 44, 730]')),
        ($defaultJson.Replace('"morning": [44, 590, 518, 730]', '"morning": [44, 590, 44, 730]')),
        ($defaultJson.Replace('"morning": [44, 590, 518, 730]', '"morning": [0, 0, 1080, 2400]')),
        ($defaultJson.Replace('"morning": [44, 590, 518, 730]', '"morning": ["44", 590, 518, 730]')),
        ($defaultJson.Replace('"morning": [44, 590, 518, 730]', '"morning": [44.1, 590, 518, 730]')),
        ($defaultJson.Replace('"schemaVersion": 1', 'schemaVersion: 1')),
        ($defaultJson.Replace('"schemaVersion": 1', '"schemaVersion": 01')),
        ($defaultJson.Replace('"schemaVersion": 1', '"schemaVersion": +1')),
        ($defaultJson.Replace('"schemaVersion": 1,', '"schemaVersion": 1, // comment')),
        ($defaultJson.Replace('"schemaVersion": 1,', '"schemaVersion": 1, /* comment */')),
        ($defaultJson.TrimEnd().Substring(0, $defaultJson.TrimEnd().Length - 1) + ',}'),
        '{}',
        '[]',
        'null'
    )
    foreach ($invalid in $invalidProfiles) {
        Assert-LayoutRejected { Read-TestProfile $invalid } 'Malformed or out-of-bounds layout was accepted.'
    }
    $largeText = ' ' * 16385
    Assert-LayoutRejected { Read-TestProfile $largeText } 'Oversized profile was accepted.'
    [IO.File]::WriteAllBytes($profilePath, [byte[]]@(0xFF, 0xFF, 0xFE))
    Assert-LayoutRejected { Get-AttendanceLayout $profilePath } 'Invalid UTF-8 profile was accepted.'
    Assert-LayoutRejected { Get-AttendanceLayout $directory } 'A directory was accepted as a profile.'
    Assert-LayoutRejected { Get-AttendanceLayout (Join-Path $directory 'missing.json') } 'Missing profile was accepted.'
    Write-Output ("PASS: {0} attendance layout and generator checks (PowerShell {1})" -f $script:checks, $PSVersionTable.PSVersion.ToString())
} finally {
    # This random per-test folder contains only this script's synthetic profiles.
    foreach ($item in @($profilePath, (Join-Path $directory 'generated.json'), (Join-Path $directory 'invalid.json'))) {
        if ([IO.File]::Exists($item)) { [IO.File]::Delete($item) }
    }
    if ([IO.Directory]::Exists($directory)) { [IO.Directory]::Delete($directory, $false) }
}
