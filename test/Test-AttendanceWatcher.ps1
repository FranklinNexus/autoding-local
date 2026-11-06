#requires -Version 7.0
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$tokens = $null; $errors = $null
$source = Join-Path $PSScriptRoot '..\tools\Watch-Attendance.ps1'
$ast = [Management.Automation.Language.Parser]::ParseFile($source, [ref]$tokens, [ref]$errors)
if ($errors.Count) { throw 'Attendance watcher script did not parse.' }
$function = $ast.Find({ param($node)
    $node -is [Management.Automation.Language.FunctionDefinitionAst] -and $node.Name -ceq 'Test-AttendanceTabs'
}, $true)
if (!$function) { throw 'Attendance page validator was not found.' }
. ([scriptblock]::Create($function.Extent.Text))
$checks = 0
function Assert([bool]$Value, [string]$Message) {
    if (!$Value) { throw $Message }
    $script:checks++
}
function New-Node([string]$Text, [string]$Package = 'com.alibaba.android.rimet', [string]$Enabled = 'true', [string]$Bounds = '[0,10][100,100]') {
    return '<node package="' + $Package + '" resource-id="com.alibaba.android.rimet:id/h5_tabbaritem_txticon" enabled="' + $Enabled + '" bounds="' + $Bounds + '" text="' + $Text + '" />'
}
function New-Page([string[]]$Nodes) {
    return '<?xml version="1.0" encoding="UTF-8"?><hierarchy>' + ($Nodes -join '') + '</hierarchy>'
}
$valid = @(New-Node '打卡'; New-Node '统计'; New-Node '设置')
Assert (Test-AttendanceTabs (New-Page $valid)) 'A known attendance tab group should be recognized.'
Assert (Test-AttendanceTabs ('diagnostic prefix' + (New-Page $valid) + 'trailer')) 'Only the well-formed hierarchy should be parsed.'
foreach ($sample in @(
    (New-Page @($valid[0], $valid[1])),
    (New-Page @($valid[0], $valid[1], (New-Node '消息'))),
    (New-Page @($valid[0], $valid[1], (New-Node '设置' -Package 'other.app'))),
    (New-Page @($valid[0], $valid[1], (New-Node '设置' -Enabled 'false'))),
    (New-Page @($valid[0], $valid[1], (New-Node '设置' -Bounds '[0,0][0,0]'))),
    (New-Page @($valid[0], $valid[0], $valid[1], $valid[2])),
    (New-Page @($valid[0], $valid[0], $valid[2])),
    'not xml',
    '<?xml version="1.0"?><hierarchy><node></hierarchy>'
)) {
    Assert (!(Test-AttendanceTabs $sample)) 'A missing, foreign, disabled or ambiguous page must be rejected.'
}
Write-Output "PASS: $checks attendance page recognition checks"
