#requires -Version 7.0
Set-StrictMode -Version Latest
function ConvertFrom-AppBridgeReply {
    param([Parameter(Mandatory)][string]$Response)
    # am prints an Intent { ... } before the actual broadcast data.
    if ($Response -notmatch '(?s)Broadcast completed:\s*result=(?<code>-?[0-9]+)(?:,\s*data="(?<data>.*)")?\s*$') {
        throw 'App bridge reply was not recognized.'
    }
    $code = [int]$Matches.code
    $data = if ($Matches.ContainsKey('data')) { $Matches.data } else { '' }
    if ($code -ne 1 -or !$data) { throw 'App bridge rejected the request.' }
    try { $parsed = $data | ConvertFrom-Json -Depth 12 -ErrorAction Stop }
    catch {
        try {
            $unescaped = ('"' + $data + '"') | ConvertFrom-Json -ErrorAction Stop
            $parsed = $unescaped | ConvertFrom-Json -Depth 12 -ErrorAction Stop
        } catch { throw 'App bridge JSON was not recognized.' }
    }
    if ($null -eq $parsed -or $parsed -isnot [pscustomobject]) { throw 'App bridge result must be an object.' }
    return $parsed
}
function Get-AppConfirmationOutcome {
    param([Parameter(Mandatory)][object]$Reply)
    $property = $Reply.PSObject.Properties['outcome']
    if ($property -and $property.Value -cin @('confirmed', 'unchanged')) { return [string]$property.Value }
    throw 'App bridge did not confirm the attendance record.'
}
Export-ModuleMember -Function ConvertFrom-AppBridgeReply, Get-AppConfirmationOutcome
