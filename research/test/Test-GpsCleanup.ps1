#requires -Version 7.0
$ErrorActionPreference='Stop'
$research=Split-Path $PSScriptRoot
$output=Join-Path $research '../build/gps-cleanup-test'
New-Item -ItemType Directory -Force -Path $output|Out-Null
$sources=@(Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'fixtures/gps-cleanup') -Filter '*.java' -Recurse|ForEach-Object FullName)
& javac -encoding UTF-8 -d $output @sources (Join-Path $research 'android/src/org/autoding/research/LocationLabCleanupReceiver.java')
if($LASTEXITCODE -ne 0){throw 'Cleanup test compilation failed'}
& java -cp $output org.autoding.research.GpsCleanupHarness
if($LASTEXITCODE -ne 0){throw 'Cleanup lifecycle checks failed'}
