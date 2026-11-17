#requires -Version 7.0
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$standalone = Split-Path $PSScriptRoot
$research = Split-Path $standalone
$classes = Join-Path $standalone 'build/early-sheet-parser-test'
New-Item -ItemType Directory -Path $classes -Force | Out-Null
& javac -encoding UTF-8 -d $classes (Join-Path $research 'android/src/org/autoding/research/EarlyCheckoutParser.java') (Join-Path $PSScriptRoot 'EarlyCheckoutParserHarness.java')
if ($LASTEXITCODE -ne 0) { throw 'Early-checkout sheet parser compilation failed.' }
& java -cp $classes org.autoding.research.EarlyCheckoutParserHarness
if ($LASTEXITCODE -ne 0) { throw 'Early-checkout sheet parser checks failed.' }
