$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
Push-Location $PSScriptRoot
try {
    New-Item -ItemType Directory -Force -Path build/test-classes | Out-Null
    & javac -encoding UTF-8 -d build/test-classes src/xcom/warof/chosen/SchedulePlanner.java src/xcom/warof/chosen/AttendancePlanner.java test/xcom/warof/chosen/SchedulePlannerTest.java test/xcom/warof/chosen/AttendancePlannerTest.java
    if ($LASTEXITCODE -ne 0) { throw 'Planner test compilation failed' }
    & java -cp build/test-classes xcom.warof.chosen.SchedulePlannerTest
    if ($LASTEXITCODE -ne 0) { throw 'Planner tests failed' }
    & java -cp build/test-classes xcom.warof.chosen.AttendancePlannerTest
    if ($LASTEXITCODE -ne 0) { throw 'Attendance planner tests failed' }
} finally { Pop-Location }
