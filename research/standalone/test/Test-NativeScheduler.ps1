#requires -Version 7.0
$ErrorActionPreference='Stop'
$standalone=Split-Path $PSScriptRoot
$research=Split-Path $standalone
$classes=Join-Path $standalone 'build/native-scheduler-tests'
New-Item -ItemType Directory -Path $classes -Force | Out-Null
$fixtures=@(Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'fixtures/schedule') -Filter '*.java' -Recurse | ForEach-Object FullName)
$sources=@('NativeTrialScheduler.java','NativeTrialPlanner.java','NativeRunPolicy.java')|ForEach-Object {Join-Path $research ('android/src/org/autoding/research/'+$_)}
& javac -encoding UTF-8 -d $classes @fixtures @sources
if($LASTEXITCODE -ne 0){throw 'Scheduler test compilation failed'}
& java -cp $classes org.autoding.research.NativeSchedulerHarness
if($LASTEXITCODE -ne 0){throw 'Scheduler lifecycle test failed'}
