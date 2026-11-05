$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

function Find-JavaTool([string]$Name) {
    if ($env:JAVA_HOME) {
        $javaHomeTool = Join-Path $env:JAVA_HOME "bin\$Name.exe"
        if (Test-Path -LiteralPath $javaHomeTool) { return $javaHomeTool }
    }
    $javaCommand = Get-Command $Name -CommandType Application -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($javaCommand) { return $javaCommand.Source }
    throw "Install JDK 17 and set JAVA_HOME or add $Name to PATH."
}

$repositoryDirectory = Split-Path $PSScriptRoot -Parent
$fixtureDirectory = Join-Path $PSScriptRoot 'fixtures\scheduler'
$outputDirectory = Join-Path $repositoryDirectory 'build\test-scheduler'
$javacTool = Find-JavaTool 'javac'
$javaTool = Find-JavaTool 'java'
New-Item -ItemType Directory -Force -Path $outputDirectory | Out-Null
$fixtureSources = @(Get-ChildItem -LiteralPath $fixtureDirectory -Recurse -Filter '*.java' | ForEach-Object { $_.FullName })
$businessSources = @(
    (Join-Path $repositoryDirectory 'src\xcom\warof\chosen\Scheduler.java'),
    (Join-Path $repositoryDirectory 'src\xcom\warof\chosen\AttendancePlanner.java')
)
$allSources = $fixtureSources + $businessSources
& $javacTool -encoding UTF-8 -d $outputDirectory @allSources
if ($LASTEXITCODE -ne 0) { throw 'Scheduler state fixture compilation failed.' }
& $javaTool -cp $outputDirectory xcom.warof.chosen.SchedulerHarness
if ($LASTEXITCODE -ne 0) { throw 'Scheduler state checks failed.' }
