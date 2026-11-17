#requires -Version 7.0
param([string]$GradlePath, [string]$SdkPath, [string]$GradleUserHome, [switch]$Offline)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$repo = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$project = Join-Path $repo 'research/standalone'
$key = Join-Path $repo 'build/research-test.jks'
if (!$GradlePath) {
    $gradleCommand = Get-Command gradle -CommandType Application -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($gradleCommand) { $GradlePath = $gradleCommand.Source }
    else {
        $cache = Join-Path $env:USERPROFILE '.gradle/wrapper/dists/gradle-8.14.3-bin'
        if (Test-Path -LiteralPath $cache) {
            $candidate = Get-ChildItem -LiteralPath $cache -Filter gradle.bat -File -Recurse | Select-Object -First 1
            if ($candidate) { $GradlePath = $candidate.FullName }
        }
        if (!$GradlePath) { $GradlePath = Join-Path $repo 'research/ocr/gradlew.bat' }
    }
}
if (!(Test-Path -LiteralPath $GradlePath -PathType Leaf)) { throw 'Gradle 8.14.3 or the research OCR wrapper is required.' }
if (!$SdkPath) {
    $SdkPath = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } elseif ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } else { Join-Path $env:LOCALAPPDATA 'Android/Sdk' }
}
$toolsPath = Join-Path $SdkPath 'build-tools/35.0.0'
if (!(Test-Path -LiteralPath (Join-Path $SdkPath 'platforms/android-35/android.jar'))) { throw 'Android SDK Platform 35 is required.' }
$javaBin = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin' } else { Split-Path (Get-Command javac).Source }
$sdkProperty = [IO.Path]::GetFullPath($SdkPath).Replace('\','/').Replace(':','\:')
[IO.File]::WriteAllText((Join-Path $project 'local.properties'), 'sdk.dir=' + $sdkProperty + [Environment]::NewLine, [Text.UTF8Encoding]::new($false))
if (!$GradleUserHome) { $GradleUserHome = Join-Path $project 'build/gradle-home' }
$options = @('--no-daemon','--console=plain','--gradle-user-home',$GradleUserHome,'-p',$project,':app:assembleRelease')
if ($Offline) { $options += '--offline' }
& $GradlePath @options
if ($LASTEXITCODE -ne 0) { throw 'Standalone research APK compilation failed.' }
$unsigned = Join-Path $project 'app/build/outputs/apk/release/app-release-unsigned.apk'
$aapt = Join-Path $toolsPath 'aapt2.exe'
$permissions = & $aapt dump permissions $unsigned
if ($LASTEXITCODE -ne 0 -or ($permissions -join "`n") -match 'android.permission.INTERNET|android.permission.ACCESS_NETWORK_STATE') {
    throw 'Standalone manifest network-permission verification failed.'
}
$badging = & $aapt dump badging $unsigned
if ($LASTEXITCODE -ne 0 -or ($badging -join "`n") -notmatch "package: name='org.autoding.research' versionCode='2'") {
    throw 'Standalone package/version verification failed.'
}
$archive = [IO.Compression.ZipFile]::OpenRead($unsigned)
try {
    $entryNames = @($archive.Entries | ForEach-Object FullName)
    if (!($entryNames -match '^assets/mlkit-google-ocr-models/.*Hani_ctc/') -or
        !($entryNames -match '^lib/arm64-v8a/libmlkit_google_ocr_pipeline\.so$') -or
        !($entryNames -match '^lib/armeabi-v7a/libmlkit_google_ocr_pipeline\.so$')) {
        throw 'Bundled Chinese model or ARM OCR pipeline missing from APK.'
    }
} finally { $archive.Dispose() }
$output = Join-Path $repo 'build/AutoDing-standalone-research.apk'
New-Item -ItemType Directory -Path (Split-Path $key) -Force | Out-Null
if (!(Test-Path -LiteralPath $key -PathType Leaf)) {
    & (Join-Path $javaBin 'keytool.exe') -genkeypair -keystore $key -storepass android -keypass android -alias research -keyalg RSA -keysize 2048 -validity 3650 -dname 'CN=AutoDing Research Test'
    if ($LASTEXITCODE -ne 0) { throw 'First-build local research signing-key generation failed.' }
    Write-Output 'First build: generated a local test key at build/research-test.jks. Keep this ignored file for future updates on devices installed from this build.'
} else {
    Write-Output 'Update build: reusing the existing build/research-test.jks without replacing it.'
}
& (Join-Path $toolsPath 'apksigner.bat') sign --ks $key --ks-key-alias research --ks-pass pass:android --key-pass pass:android --out $output $unsigned
if ($LASTEXITCODE -ne 0) { throw 'Standalone research signing failed.' }
$signature = & (Join-Path $toolsPath 'apksigner.bat') verify --verbose --print-certs $output
if ($LASTEXITCODE -ne 0) { throw 'Standalone research signature verification failed.' }
$certificate = Join-Path $project 'build/research-signing-certificate.der'
& (Join-Path $javaBin 'keytool.exe') -exportcert -keystore $key -storepass android -alias research -file $certificate 2>&1 | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'Research key certificate export failed.' }
$certificateHash = (Get-FileHash -LiteralPath $certificate -Algorithm SHA256).Hash.ToLowerInvariant()
if (($signature -join "`n") -notmatch ('Signer #1 certificate SHA-256 digest: ' + $certificateHash)) {
    throw 'Signed APK does not match the original research key.'
}
$hash = (Get-FileHash -LiteralPath $output -Algorithm SHA256).Hash.ToLowerInvariant()
[IO.File]::WriteAllText((Join-Path $repo 'build/AutoDing-standalone-research.sha256'), $hash + '  AutoDing-standalone-research.apk' + [Environment]::NewLine, [Text.UTF8Encoding]::new($false))
Write-Output 'Standalone research APK built and signed with the local research key; no network permission; bundled Chinese model verified.'
Write-Output ('Certificate SHA256: ' + $certificateHash)
Write-Output $output
