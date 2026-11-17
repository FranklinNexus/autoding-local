#requires -Version 7.0
param([string]$GradlePath, [string]$SdkPath, [string]$GradleUserHome, [switch]$Offline)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$project = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../ocr'))
if (!$GradlePath) {
    $gradleCommand = Get-Command gradle -CommandType Application -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($gradleCommand) { $GradlePath = $gradleCommand.Source }
    else {
        $cache = Join-Path $env:USERPROFILE '.gradle/wrapper/dists/gradle-8.14.3-bin'
        if (Test-Path -LiteralPath $cache) {
            $candidate = Get-ChildItem -LiteralPath $cache -Filter gradle.bat -File -Recurse | Select-Object -First 1
            if ($candidate) { $GradlePath = $candidate.FullName }
        }
        if (!$GradlePath) { $GradlePath = Join-Path $project 'gradlew.bat' }
    }
}
if (!$GradlePath -or !(Test-Path -LiteralPath $GradlePath -PathType Leaf)) {
    throw 'Install Gradle 8.14.3 and pass -GradlePath, or use the generated project wrapper.'
}
if (!$SdkPath) {
    $SdkPath = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } elseif ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } else { Join-Path $env:LOCALAPPDATA 'Android/Sdk' }
}
if (!(Test-Path -LiteralPath (Join-Path $SdkPath 'platforms/android-35/android.jar'))) { throw 'Install Android SDK Platform 35.' }
$sdkProperty = [IO.Path]::GetFullPath($SdkPath).Replace('\','/').Replace(':','\:')
[IO.File]::WriteAllText((Join-Path $project 'local.properties'), 'sdk.dir=' + $sdkProperty + [Environment]::NewLine, [Text.UTF8Encoding]::new($false))
if (!$GradleUserHome) { $GradleUserHome = Join-Path $project 'build/gradle-home' }
$options = @('--no-daemon','--console=plain','--gradle-user-home',$GradleUserHome,'-p',$project,':app:assembleDebug')
if ($Offline) { $options += '--offline' }
& $GradlePath @options
if ($LASTEXITCODE -ne 0) { throw 'OCR research APK build failed; no device was changed.' }
$apk = Join-Path $project 'app/build/outputs/apk/debug/app-debug.apk'
$aapt = Join-Path $SdkPath 'build-tools/35.0.0/aapt2.exe'
$permissions = & $aapt dump permissions $apk
if ($LASTEXITCODE -ne 0 -or ($permissions -join "`n") -match 'android.permission.INTERNET|android.permission.ACCESS_NETWORK_STATE') {
    throw 'Offline probe manifest verification failed.'
}
$archive = [IO.Compression.ZipFile]::OpenRead($apk)
try {
    $entryNames = @($archive.Entries | ForEach-Object FullName)
    if (!($entryNames -match '^assets/mlkit-google-ocr-models/.*Hani_ctc/') -or
        !($entryNames -match '^lib/arm64-v8a/libmlkit_google_ocr_pipeline\.so$') -or
        !($entryNames -match '^lib/armeabi-v7a/libmlkit_google_ocr_pipeline\.so$')) {
        throw 'Bundled Chinese model or ARM OCR pipeline missing from APK.'
    }
} finally { $archive.Dispose() }
$distribution = Join-Path $project 'build/distribution'
New-Item -ItemType Directory -Path $distribution -Force | Out-Null
$output = Join-Path $distribution 'AutoDing-OcrProbe-debug.apk'
Copy-Item -LiteralPath $apk -Destination $output -Force
$hash = (Get-FileHash -LiteralPath $output -Algorithm SHA256).Hash.ToLowerInvariant()
[IO.File]::WriteAllText((Join-Path $distribution 'SHA256SUMS.txt'), $hash + '  AutoDing-OcrProbe-debug.apk' + [Environment]::NewLine, [Text.UTF8Encoding]::new($false))
Write-Output 'OCR research APK built; bundled Chinese model and ARM pipelines present; no network permission. Device offline recognition still needs a first-run test.'
Write-Output $output
