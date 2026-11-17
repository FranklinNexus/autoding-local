#requires -Version 7.0
param()
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$researchDirectory = Split-Path $PSScriptRoot
$repositoryDirectory = Split-Path $researchDirectory
$fixtureDirectory = Join-Path $PSScriptRoot 'fixtures/native-unlock'
$testBuildDirectory = Join-Path $repositoryDirectory ('build/native-unlock-fixtures-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $testBuildDirectory -Force | Out-Null
$fixtureSources = @(Get-ChildItem -LiteralPath $fixtureDirectory -Filter '*.java' -Recurse | ForEach-Object FullName)
$sourcePath = Join-Path $researchDirectory 'android/src/org/autoding/research/NativeUnlock.java'
& javac -encoding UTF-8 -d $testBuildDirectory @fixtureSources $sourcePath
if ($LASTEXITCODE) { throw 'Native unlock fixture compilation failed.' }
& java -cp $testBuildDirectory org.autoding.research.NativeUnlockHarness
if ($LASTEXITCODE) { throw 'Native unlock fixture checks failed.' }
