#requires -Version 7.0
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$standalone = Split-Path $PSScriptRoot
$research = Split-Path $standalone
$classes = Join-Path $standalone 'build/success-parser-test'
New-Item -ItemType Directory -Path $classes -Force | Out-Null
& javac -encoding UTF-8 -d $classes (Join-Path $research 'android/src/org/autoding/research/CheckoutSuccessParser.java') (Join-Path $PSScriptRoot 'CheckoutSuccessParserHarness.java')
if ($LASTEXITCODE -ne 0) { throw 'Checkout-success parser compilation failed.' }
& java -cp $classes org.autoding.research.CheckoutSuccessParserHarness
if ($LASTEXITCODE -ne 0) { throw 'Checkout-success parser checks failed.' }
