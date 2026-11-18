#requires -Version 7.0
$ErrorActionPreference = 'Stop'
$standalone = Split-Path $PSScriptRoot
$research = Split-Path $standalone
$classes = Join-Path $standalone 'build/native-run-policy-test'
New-Item -ItemType Directory -Path $classes -Force | Out-Null
& javac -encoding UTF-8 -d $classes (Join-Path $research 'android/src/org/autoding/research/NativeRunPolicy.java') (Join-Path $research 'android/src/org/autoding/research/NativeTrialPlanner.java') (Join-Path $research 'android/src/org/autoding/research/ReceiptParser.java') (Join-Path $PSScriptRoot 'NativeRunPolicyHarness.java')
if ($LASTEXITCODE -ne 0) { throw 'Native runner policy compilation failed.' }
& java -cp $classes org.autoding.research.NativeRunPolicyHarness
if ($LASTEXITCODE -ne 0) { throw 'Native runner policy checks failed.' }
