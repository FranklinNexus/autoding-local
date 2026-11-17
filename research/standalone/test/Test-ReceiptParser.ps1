#requires -Version 7.0
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$standalone = Split-Path $PSScriptRoot
$research = Split-Path $standalone
$classes = Join-Path $standalone 'build/parser-test'
New-Item -ItemType Directory -Path $classes -Force | Out-Null
& javac -encoding UTF-8 -d $classes (Join-Path $research 'android/src/org/autoding/research/ReceiptParser.java') (Join-Path $PSScriptRoot 'ReceiptParserHarness.java')
if ($LASTEXITCODE -ne 0) { throw 'Standalone receipt parser compilation failed.' }
& java -cp $classes org.autoding/research/ReceiptParserHarness
if ($LASTEXITCODE -ne 0) { throw 'Standalone receipt parser checks failed.' }
