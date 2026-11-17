#requires -Version 7.0
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$root = Split-Path $PSScriptRoot
$classes = Join-Path $root 'build/test-parser'
New-Item -ItemType Directory -Path $classes -Force | Out-Null
& javac -encoding UTF-8 -d $classes (Join-Path $root 'app/src/main/java/org/autoding/ocrprobe/ReceiptParser.java') (Join-Path $PSScriptRoot 'ReceiptParserHarness.java')
if ($LASTEXITCODE -ne 0) { throw 'Receipt parser fixture compilation failed.' }
& java -cp $classes org.autoding.ocrprobe.ReceiptParserHarness
if ($LASTEXITCODE -ne 0) { throw 'Receipt parser fixture checks failed.' }
