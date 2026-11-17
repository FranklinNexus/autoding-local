#requires -Version 7.0
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$repository = Split-Path (Split-Path $PSScriptRoot)
$fixture = Join-Path $PSScriptRoot 'fixtures/location'
$source = Join-Path $repository 'research/android/src/org/autoding/research/LocationProbe.java'
$output = Join-Path $repository 'build/research-location-test'
New-Item -ItemType Directory -Force -Path $output | Out-Null
$javac = (Get-Command javac -CommandType Application).Source
$java = (Get-Command java -CommandType Application).Source
$sources = @(Get-ChildItem -LiteralPath $fixture -Filter '*.java' -Recurse | ForEach-Object FullName)
& $javac -encoding UTF-8 -source 8 -target 8 -d $output @sources $source
if ($LASTEXITCODE -ne 0) { throw 'Location fixture compilation failed.' }
& $java -cp $output fixture.LocationProbeHarness
if ($LASTEXITCODE -ne 0) { throw 'Location capability checks failed.' }
