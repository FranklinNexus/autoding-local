#requires -Version 5.1
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$Id,
    [Parameter(Mandatory = $true)][int]$ReferenceWidth,
    [Parameter(Mandatory = $true)][int]$ReferenceHeight,
    [Parameter(Mandatory = $true)][int[]]$MorningBounds,
    [Parameter(Mandatory = $true)][int[]]$EveningBounds,
    [Parameter(Mandatory = $true)][int[]]$DateBounds,
    [Parameter(Mandatory = $true)][string]$OutputPath,
    [double]$AspectTolerance = 0.005,
    [switch]$Force
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
Import-Module (Join-Path $PSScriptRoot 'AttendanceLayout.psm1') -Force
$profile = [pscustomobject][ordered]@{
    schemaVersion = 1
    id = $Id
    referenceWidth = $ReferenceWidth
    referenceHeight = $ReferenceHeight
    aspectTolerance = $AspectTolerance
    crops = [pscustomobject][ordered]@{ morning = $MorningBounds; evening = $EveningBounds; date = $DateBounds }
}
# Validate every field and all three rectangles before opening an output file.
$layout = ConvertTo-AttendanceLayout -Profile $profile
[void](Resolve-AttendanceLayout -Layout $layout -ImageWidth $ReferenceWidth -ImageHeight $ReferenceHeight)
$path = [IO.Path]::GetFullPath($OutputPath)
$parent = [IO.Path]::GetDirectoryName($path)
if (![IO.Directory]::Exists($parent)) { throw 'The output directory must already exist.' }
$mode = if ($Force) { [IO.FileMode]::Create } else { [IO.FileMode]::CreateNew }
$bytes = [Text.UTF8Encoding]::new($false).GetBytes(($profile | ConvertTo-Json -Depth 4) + [Environment]::NewLine)
$stream = $null
try {
    $stream = [IO.File]::Open($path, $mode, [IO.FileAccess]::Write, [IO.FileShare]::None)
    $stream.Write($bytes, 0, $bytes.Length)
    $stream.Flush()
} finally { if ($stream) { $stream.Dispose() } }
[pscustomobject]@{ Path = $path; Layout = $layout.Id; RequiresDeviceAcceptance = $true }
