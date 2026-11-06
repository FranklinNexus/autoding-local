#requires -Version 7.0
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

function Find-JavaTool([string]$Name) {
    if ($env:JAVA_HOME) {
        $candidate = Join-Path $env:JAVA_HOME "bin\$Name.exe"
        if (Test-Path -LiteralPath $candidate) { return $candidate }
    }
    $command = Get-Command $Name -CommandType Application -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($command) { return $command.Source }
    throw "Install JDK 17 and add $Name to PATH or set JAVA_HOME."
}

$repository = Split-Path $PSScriptRoot -Parent
$fixtures = Join-Path $PSScriptRoot 'fixtures\android-ui'
$source = Join-Path $repository 'tools\android-ui\ReadWindow.java'
$output = Join-Path $repository ('build\test-android-ui-' + [guid]::NewGuid().ToString('N'))
$javac = Find-JavaTool 'javac'
$java = Find-JavaTool 'java'
$common = @(Get-ChildItem -LiteralPath (Join-Path $fixtures 'common') -Filter '*.java' -Recurse | ForEach-Object { $_.FullName })
foreach ($api in @(23, 24, 35)) {
    $classes = Join-Path $output "api$api"
    [void][IO.Directory]::CreateDirectory($classes)
    $apiFixture = if ($api -eq 23) { 'api23' } else { 'api24' }
    $automation = Join-Path $fixtures ($apiFixture + '\android\app\UiAutomation.java')
    & $javac -encoding UTF-8 -d $classes @common $automation $source
    if ($LASTEXITCODE -ne 0) { throw "Android UI API $api fixture compilation failed." }
    & $java "-Dfixture.api=$api" -cp $classes fixture.ReadWindowHarness
    if ($LASTEXITCODE -ne 0) { throw "Android UI API $api checks failed." }
}
