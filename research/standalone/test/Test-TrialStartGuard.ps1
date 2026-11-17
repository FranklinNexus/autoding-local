#requires -Version 7.0
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$standalone = Split-Path $PSScriptRoot
$research = Split-Path $standalone
$classes = Join-Path $standalone 'build/trial-start-test'
New-Item -ItemType Directory -Path $classes -Force | Out-Null
& javac -encoding UTF-8 -d $classes (Join-Path $research 'android/src/org/autoding/research/TrialStartGuard.java') (Join-Path $PSScriptRoot 'TrialStartGuardHarness.java')
if ($LASTEXITCODE -ne 0) { throw 'Wall-trial start guard compilation failed.' }
& java -cp $classes org.autoding.research.TrialStartGuardHarness
if ($LASTEXITCODE -ne 0) { throw 'Wall-trial start guard checks failed.' }
