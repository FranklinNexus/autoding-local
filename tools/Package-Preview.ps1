#requires -Version 7.0
param(
    [string]$ApkPath,
    [string]$UiJarPath,
    [string]$SdkPath,
    [string]$BuildToolsVersion = '35.0.0'
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$repository = [IO.Path]::GetFullPath((Split-Path $PSScriptRoot))
$distribution = Join-Path $repository 'build\distribution'

function Invoke-PackageCommand([string]$File, [string[]]$Arguments) {
    # Capture tool output so private paths from diagnostics never enter metadata.
    $output = & $File @Arguments 2>&1
    if ($LASTEXITCODE -ne 0) { throw 'A packaging verification command failed.' }
    return ($output -join "`n")
}
function Assert-PackageSourceEntry([string]$Name) {
    $normalized = $Name.Replace('\','/')
    if ($normalized.StartsWith('/') -or $normalized -match '(^|/)\.\.(/|$)|:' -or
        $normalized -match '(?i)\.(?:jks|keystore|clixml|pem|p12|apk|idsig|jsonl|log|png|jpe?g)$' -or
        $normalized -match '(?i)(^|/)(?:build|logs|trial-logs)/|(^|/)(?:status|attempts|unlock\.blocked)\.json$|(^|/)\.env(?:$|\.(?!example$))') {
        throw 'The Git source archive contains a private or generated artifact.'
    }
}
function Assert-ZipEntry([string]$Path, [string]$Entry) {
    $zip = [IO.Compression.ZipFile]::OpenRead($Path)
    try { if ($null -eq $zip.GetEntry($Entry)) { throw 'A required binary archive entry is missing.' } }
    finally { $zip.Dispose() }
}

Push-Location $repository
try {
    [void](Invoke-PackageCommand 'git' @('rev-parse','--is-inside-work-tree'))
    $dirty = Invoke-PackageCommand 'git' @('status','--porcelain','--untracked-files=all')
    if ($dirty.Trim()) { throw 'Commit all source changes before packaging; the Git worktree must be clean.' }
    $commit = (Invoke-PackageCommand 'git' @('rev-parse','HEAD')).Trim()
    if ($commit -notmatch '^[0-9a-f]{40,64}$') { throw 'The source commit was not recognized.' }
    $manifest = [xml](Invoke-PackageCommand 'git' @('show','HEAD:AndroidManifest.xml'))
    $androidNamespace = 'http://schemas.android.com/apk/res/android'
    $version = $manifest.manifest.GetAttribute('versionName',$androidNamespace)
    $versionCode = $manifest.manifest.GetAttribute('versionCode',$androidNamespace)
    $packageId = $manifest.manifest.GetAttribute('package')
    if ($version -cnotmatch '^[A-Za-z0-9_.+-]{1,64}$' -or $versionCode -notmatch '^[0-9]+$' -or $packageId -cne 'xcom.warof.chosen') {
        throw 'The tracked manifest version or package was not recognized.'
    }
    if (!$ApkPath) { $ApkPath = Join-Path $repository 'build\AutoDing-local-test.apk' }
    if (!$UiJarPath) { $UiJarPath = Join-Path $repository 'build\ui-dump\autoding-ui.jar' }
    foreach ($path in @($ApkPath,$UiJarPath)) {
        if (!(Test-Path -LiteralPath $path -PathType Leaf)) { throw 'Build the APK and UI helper before packaging.' }
    }
    $ApkPath = [IO.Path]::GetFullPath($ApkPath)
    $UiJarPath = [IO.Path]::GetFullPath($UiJarPath)
    if (!$SdkPath) {
        $SdkPath = if ($env:ANDROID_HOME) { $env:ANDROID_HOME }
            elseif ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT }
            elseif ($env:LOCALAPPDATA) { Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
            else { throw 'Set ANDROID_HOME or pass -SdkPath for binary verification.' }
    }
    $tools = Join-Path $SdkPath ('build-tools\' + $BuildToolsVersion)
    $aapt = Join-Path $tools 'aapt2.exe'
    $signer = Join-Path $tools 'apksigner.bat'
    if (!(Test-Path -LiteralPath $aapt -PathType Leaf) -or !(Test-Path -LiteralPath $signer -PathType Leaf)) {
        throw 'Install the selected Android build-tools for binary verification.'
    }
    $badging = Invoke-PackageCommand $aapt @('dump','badging',$ApkPath)
    $packageMatch = [regex]::Match($badging, "(?m)^package: name='(?<id>[^']+)' versionCode='(?<code>[0-9]+)' versionName='(?<version>[^']+)'")
    if (!$packageMatch.Success -or $packageMatch.Groups['id'].Value -cne $packageId -or
        $packageMatch.Groups['code'].Value -cne $versionCode -or $packageMatch.Groups['version'].Value -cne $version) {
        throw 'The APK version does not match the clean source commit; rebuild before packaging.'
    }
    $signature = Invoke-PackageCommand $signer @('verify','--print-certs',$ApkPath)
    $certificate = [regex]::Match($signature, '(?im)^Signer #1 certificate SHA-256 digest:\s*(?<hash>[0-9a-f]{64})\s*$')
    if (!$certificate.Success) { throw 'The verified APK signer certificate was not recognized.' }
    Assert-ZipEntry $ApkPath 'classes.dex'
    Assert-ZipEntry $UiJarPath 'classes.dex'

    New-Item -ItemType Directory -Force -Path $distribution | Out-Null
    $staging = Join-Path $distribution ('.package-' + [guid]::NewGuid().ToString('N'))
    New-Item -ItemType Directory -Path $staging | Out-Null
    try {
        $apkName = 'AutoDing-local-test.apk'
        $zipName = 'AutoDing-windows-tools.zip'
        $apkOutput = Join-Path $staging $apkName
        $zipOutput = Join-Path $staging $zipName
        [void](Invoke-PackageCommand 'git' @('archive','--format=zip',('--output=' + $zipOutput),'HEAD'))
        $archive = [IO.Compression.ZipFile]::Open($zipOutput,[IO.Compression.ZipArchiveMode]::Update)
        try {
            foreach ($entry in $archive.Entries) { Assert-PackageSourceEntry $entry.FullName }
            [void][IO.Compression.ZipFileExtensions]::CreateEntryFromFile($archive,$UiJarPath,'build/ui-dump/autoding-ui.jar')
        } finally { $archive.Dispose() }
        Copy-Item -LiteralPath $ApkPath -Destination $apkOutput
        $artifacts = @(
            [ordered]@{ name=$apkName; sha256=(Get-FileHash -LiteralPath $apkOutput -Algorithm SHA256).Hash.ToLowerInvariant() },
            [ordered]@{ name=$zipName; sha256=(Get-FileHash -LiteralPath $zipOutput -Algorithm SHA256).Hash.ToLowerInvariant() }
        )
        $metadata = [ordered]@{
            schemaVersion=1; commit=$commit; version=$version; versionCode=[long]$versionCode
            packageId=$packageId; apkCertificateSha256=$certificate.Groups['hash'].Value.ToLowerInvariant()
            signing=[ordered]@{
                type='local-test-key'
                notice='Preview test signing. Keep the same private local key for upgrades. CI generates a fresh test key on each run; APKs from separate CI runs do not share an upgrade identity. Maintainers publish stable-signature previews from a retained local key and create the GitHub release manually.'
            }
            contents='Clean Git HEAD source archive plus the explicitly selected UI helper; APK supplied separately.'
            artifacts=$artifacts
        }
        $metadataPath = Join-Path $staging 'metadata.json'
        [IO.File]::WriteAllText($metadataPath,($metadata|ConvertTo-Json -Depth 7)+[Environment]::NewLine,[Text.UTF8Encoding]::new($false))
        $hashLines = @($artifacts | ForEach-Object { $_.sha256 + '  ' + $_.name })
        $hashLines += (Get-FileHash -LiteralPath $metadataPath -Algorithm SHA256).Hash.ToLowerInvariant() + '  metadata.json'
        [IO.File]::WriteAllLines((Join-Path $staging 'SHA256SUMS.txt'),$hashLines,[Text.UTF8Encoding]::new($false))
        # Recheck after verification and archive creation, before publishing files.
        if ((Invoke-PackageCommand 'git' @('status','--porcelain','--untracked-files=all')).Trim() -or
            (Invoke-PackageCommand 'git' @('rev-parse','HEAD')).Trim() -cne $commit) {
            throw 'Source changed during packaging; rerun from a clean commit.'
        }
        foreach ($name in @($apkName,$zipName,'metadata.json','SHA256SUMS.txt')) {
            Move-Item -LiteralPath (Join-Path $staging $name) -Destination (Join-Path $distribution $name) -Force
        }
        Write-Output ('Packaged preview ' + $version + ' from ' + $commit + ' into build/distribution.')
    } finally {
        foreach ($name in @('AutoDing-local-test.apk','AutoDing-windows-tools.zip','metadata.json','SHA256SUMS.txt')) {
            Remove-Item -LiteralPath (Join-Path $staging $name) -Force -ErrorAction SilentlyContinue
        }
        Remove-Item -LiteralPath $staging -Force -ErrorAction SilentlyContinue
    }
} finally { Pop-Location }
