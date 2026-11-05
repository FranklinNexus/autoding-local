param(
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[A-Za-z0-9_.:-]+$')]
    [string]$Serial,
    [string]$CredentialPath
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
if ([Environment]::OSVersion.Platform -ne [PlatformID]::Win32NT) {
    throw 'Run this setup on Windows so SecureString is protected with the current user DPAPI key.'
}
if ([Threading.Thread]::CurrentThread.GetApartmentState() -ne [Threading.ApartmentState]::STA) {
    throw 'Start PowerShell with -STA to display the local PIN setup window.'
}
if (!$CredentialPath) {
    if (!$env:LOCALAPPDATA) { throw 'LOCALAPPDATA is unavailable; pass -CredentialPath.' }
    $CredentialPath = Join-Path $env:LOCALAPPDATA 'AutoDingLocal\phone-unlock.clixml'
}
$CredentialPath = [IO.Path]::GetFullPath($CredentialPath)

Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Drawing
[Windows.Forms.Application]::EnableVisualStyles()

$dialogState = @{ Secret = $null }
$form = [Windows.Forms.Form]::new()
$form.Text = 'AutoDing - 本机保存手机解锁 PIN'
$form.ClientSize = [Drawing.Size]::new(520, 320)
$form.FormBorderStyle = [Windows.Forms.FormBorderStyle]::FixedDialog
$form.MaximizeBox = $false
$form.MinimizeBox = $false
$form.StartPosition = [Windows.Forms.FormStartPosition]::CenterScreen

$description = [Windows.Forms.Label]::new()
$description.Location = [Drawing.Point]::new(20, 16)
$description.Size = [Drawing.Size]::new(480, 86)
$description.Text = "手机：$Serial`r`n请输入 4–16 位数字 PIN，两次输入须一致。`r`n仅在本机使用当前 Windows 账户的 DPAPI 加密保存，供 USB 解锁使用。请勿在聊天中填写 PIN。"
$form.Controls.Add($description)

$firstLabel = [Windows.Forms.Label]::new()
$firstLabel.Text = '手机 PIN'
$firstLabel.Location = [Drawing.Point]::new(20, 116)
$firstLabel.AutoSize = $true
$form.Controls.Add($firstLabel)
$pinBox = [Windows.Forms.TextBox]::new()
$pinBox.Location = [Drawing.Point]::new(145, 112)
$pinBox.Size = [Drawing.Size]::new(345, 28)
$pinBox.UseSystemPasswordChar = $true
$pinBox.MaxLength = 16
$pinBox.ShortcutsEnabled = $false
$form.Controls.Add($pinBox)

$secondLabel = [Windows.Forms.Label]::new()
$secondLabel.Text = '再次输入 PIN'
$secondLabel.Location = [Drawing.Point]::new(20, 160)
$secondLabel.AutoSize = $true
$form.Controls.Add($secondLabel)
$confirmBox = [Windows.Forms.TextBox]::new()
$confirmBox.Location = [Drawing.Point]::new(145, 156)
$confirmBox.Size = [Drawing.Size]::new(345, 28)
$confirmBox.UseSystemPasswordChar = $true
$confirmBox.MaxLength = 16
$confirmBox.ShortcutsEnabled = $false
$form.Controls.Add($confirmBox)

$errorLabel = [Windows.Forms.Label]::new()
$errorLabel.Location = [Drawing.Point]::new(20, 200)
$errorLabel.Size = [Drawing.Size]::new(480, 42)
$errorLabel.ForeColor = [Drawing.Color]::DarkRed
$form.Controls.Add($errorLabel)

$saveButton = [Windows.Forms.Button]::new()
$saveButton.Text = '加密保存'
$saveButton.Location = [Drawing.Point]::new(270, 260)
$saveButton.Size = [Drawing.Size]::new(105, 34)
$form.Controls.Add($saveButton)
$cancelButton = [Windows.Forms.Button]::new()
$cancelButton.Text = '取消'
$cancelButton.Location = [Drawing.Point]::new(385, 260)
$cancelButton.Size = [Drawing.Size]::new(105, 34)
$cancelButton.DialogResult = [Windows.Forms.DialogResult]::Cancel
$form.Controls.Add($cancelButton)
$form.AcceptButton = $saveButton
$form.CancelButton = $cancelButton
$form.Add_Shown({ $pinBox.Select() })
$saveButton.Add_Click({
    if ($pinBox.Text -cnotmatch '^[0-9]{4,16}$') {
        $errorLabel.Text = '请输入 4–16 位数字 PIN。'
        return
    }
    if (![string]::Equals($pinBox.Text, $confirmBox.Text, [StringComparison]::Ordinal)) {
        $errorLabel.Text = '两次输入不一致，请重新填写。'
        return
    }
    $secret = [Security.SecureString]::new()
    for ($index = 0; $index -lt $pinBox.Text.Length; $index++) {
        $secret.AppendChar($pinBox.Text[$index])
    }
    $secret.MakeReadOnly()
    $dialogState.Secret = $secret
    $pinBox.Clear()
    $confirmBox.Clear()
    $form.DialogResult = [Windows.Forms.DialogResult]::OK
    $form.Close()
})

$temporaryPath = $null
try {
    if ($form.ShowDialog() -ne [Windows.Forms.DialogResult]::OK -or !$dialogState.Secret) {
        Write-Output 'PIN setup canceled; any existing credential was left unchanged.'
        return
    }
    $directory = [IO.Path]::GetDirectoryName($CredentialPath)
    New-Item -ItemType Directory -Path $directory -Force | Out-Null
    $temporaryPath = Join-Path $directory ('.phone-unlock-' + [guid]::NewGuid().ToString('N') + '.tmp')
    $record = [pscustomobject]@{ Serial = $Serial; Kind = 'pin'; Secret = $dialogState.Secret }
    Export-Clixml -InputObject $record -LiteralPath $temporaryPath -Depth 3
    if ([IO.File]::Exists($CredentialPath)) {
        [IO.File]::Replace($temporaryPath, $CredentialPath, $null)
    } else {
        [IO.File]::Move($temporaryPath, $CredentialPath)
    }
    $temporaryPath = $null
    Write-Output "Encrypted phone unlock credential saved: $CredentialPath"
} finally {
    $pinBox.Clear()
    $confirmBox.Clear()
    if ($dialogState.Secret) { $dialogState.Secret.Dispose() }
    $form.Dispose()
    if ($temporaryPath -and [IO.File]::Exists($temporaryPath)) {
        Remove-Item -LiteralPath $temporaryPath -Force
    }
}
