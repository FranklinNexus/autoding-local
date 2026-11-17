#requires -Version 7.0
param([string]$SdkPath, [string]$GradlePath, [string]$GradleUserHome, [switch]$Offline)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
# Compatibility entry point. The shared sources now require bundled ML Kit,
# so every research build goes through the same Gradle/signature checks.
& (Join-Path $PSScriptRoot 'Build-Standalone.ps1') @PSBoundParameters
$repo = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$source = Join-Path $repo 'build/AutoDing-standalone-research.apk'
$compat = Join-Path $repo 'build/AutoDing-research.apk'
Copy-Item -LiteralPath $source -Destination $compat -Force
$hash = (Get-FileHash -LiteralPath $compat -Algorithm SHA256).Hash.ToLowerInvariant()
[IO.File]::WriteAllText((Join-Path $repo 'build/AutoDing-research.sha256'), $hash + '  AutoDing-research.apk' + [Environment]::NewLine, [Text.UTF8Encoding]::new($false))
Write-Output 'Compatibility output copied from the same signed standalone research APK.'
Write-Output $compat
