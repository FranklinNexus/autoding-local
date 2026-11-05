#requires -Version 7.0
param(
    [string]$WatcherPath = (Join-Path $PSScriptRoot '..\tools\Watch-PhoneUnlock.ps1')
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

# Load only the recognizer definition. Never dot-source the watcher: its main
# program connects to ADB, imports a credential, and may attempt an unlock.
$tokens = $null
$parseErrors = $null
$sourceAst = [Management.Automation.Language.Parser]::ParseFile(
    [IO.Path]::GetFullPath($WatcherPath), [ref]$tokens, [ref]$parseErrors)
if ($parseErrors.Count) { throw 'The watcher contains PowerShell parse errors.' }
$definitions = @($sourceAst.FindAll({
    param($node)
    $node -is [Management.Automation.Language.FunctionDefinitionAst] -and
        $node.Name -eq 'Test-SystemUiPin'
}, $true))
if ($definitions.Count -ne 1) { throw 'Expected exactly one Test-SystemUiPin definition.' }
. ([scriptblock]::Create($definitions[0].Extent.Text))

function New-PinFixture {
    param(
        [string]$Package = 'com.android.systemui',
        [string]$EntryId = 'fixedPinEntry',
        [string]$InputEmpty = 'true',
        [string]$Focused = 'true',
        [string]$Password = 'true',
        [string]$Enabled = 'true',
        [int[]]$Digits = @(0..9),
        [switch]$OmitInputEmpty
    )
    $document = [xml]::new()
    [void]$document.AppendChild($document.CreateXmlDeclaration('1.0', 'UTF-8', $null))
    $root = $document.CreateElement('hierarchy')
    [void]$document.AppendChild($root)
    $entry = $document.CreateElement('node')
    $attributes = [ordered]@{
        package = $Package
        'resource-id' = 'com.android.systemui:id/' + $EntryId
        password = $Password
        focused = $Focused
        enabled = $Enabled
        bounds = '[0,0][100,100]'
        text = ''
        'content-desc' = ''
    }
    if (!$OmitInputEmpty) { $attributes['input-empty'] = $InputEmpty }
    foreach ($attribute in $attributes.GetEnumerator()) {
        $entry.SetAttribute($attribute.Key, [string]$attribute.Value)
    }
    [void]$root.AppendChild($entry)
    foreach ($digit in $Digits) {
        $key = $document.CreateElement('node')
        $key.SetAttribute('package', $Package)
        $key.SetAttribute('resource-id', 'com.android.systemui:id/key' + $digit)
        $key.SetAttribute('enabled', 'true')
        $key.SetAttribute('bounds', '[0,0][100,100]')
        $key.SetAttribute('text', [string]$digit)
        [void]$root.AppendChild($key)
    }
    return $document.OuterXml
}

$cases = @(
    @{ Name = 'Empty focused fixed PIN'; Dump = (New-PinFixture); Accept = $true; Submit = $false }
    @{ Name = 'Existing PIN input'; Dump = (New-PinFixture -InputEmpty 'false'); Accept = $false }
    @{ Name = 'Unfocused PIN entry'; Dump = (New-PinFixture -Focused 'false'); Accept = $false }
    @{ Name = 'Different app package'; Dump = (New-PinFixture -Package 'example.other.app'); Accept = $false }
    @{ Name = 'Missing digit key'; Dump = (New-PinFixture -Digits @(0..8)); Accept = $false }
    @{ Name = 'Missing empty-input evidence'; Dump = (New-PinFixture -OmitInputEmpty); Accept = $false }
    @{ Name = 'Non-password entry'; Dump = (New-PinFixture -Password 'false'); Accept = $false }
    @{ Name = 'Disabled PIN entry'; Dump = (New-PinFixture -Enabled 'false'); Accept = $false }
    @{ Name = 'Malformed snapshot'; Dump = '<?xml version="1.0"?><hierarchy><node></hierarchy>'; Accept = $false }
    @{ Name = 'Standard PIN needs explicit submit'; Dump = (New-PinFixture -EntryId 'pinEntry'); Accept = $true; Submit = $true }
)
$failures = [Collections.Generic.List[string]]::new()
foreach ($case in $cases) {
    try {
        $submit = $null
        $result = @(Test-SystemUiPin -Dump $case.Dump -SubmitExplicitly ([ref]$submit))
        if ($result.Count -ne 1 -or $result[0] -isnot [bool]) {
            throw 'Recognizer must return exactly one Boolean.'
        }
        if ($result[0] -ne $case.Accept) {
            throw "Expected accept=$($case.Accept), got $($result[0])."
        }
        if ($case.Accept -and $submit -ne $case.Submit) {
            throw "Expected explicit submit=$($case.Submit), got $submit."
        }
        Write-Output "PASS: $($case.Name)"
    } catch {
        $failures.Add("$($case.Name): $($_.Exception.Message)")
    }
}
if ($failures.Count) { throw ($failures -join [Environment]::NewLine) }
Write-Output "Passed $($cases.Count) synthetic PIN recognition tests; no device or credential was accessed."
