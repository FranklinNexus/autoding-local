#requires -Version 7.0
param([string]$SdkPath)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$root = Split-Path $PSScriptRoot
$repo = Split-Path $root
if (!$SdkPath) {
    $SdkPath = if ($env:ANDROID_HOME) {$env:ANDROID_HOME} elseif ($env:ANDROID_SDK_ROOT) {$env:ANDROID_SDK_ROOT} else {Join-Path $env:LOCALAPPDATA 'Android/Sdk'}
}
$tools = Join-Path $SdkPath 'build-tools/35.0.0'
$androidJar = Join-Path $SdkPath 'platforms/android-35/android.jar'
$java = if ($env:JAVA_HOME) {Join-Path $env:JAVA_HOME 'bin'} else {Split-Path (Get-Command javac).Source}
$build = Join-Path $repo ('build/research-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Force -Path $build,(Join-Path $build 'classes'),(Join-Path $build 'dex') | Out-Null
& (Join-Path $tools 'aapt2.exe') compile --dir (Join-Path $root 'android/res') -o (Join-Path $build 'resources.zip')
if ($LASTEXITCODE) {throw 'Research resources failed.'}
& (Join-Path $tools 'aapt2.exe') link -I $androidJar --manifest (Join-Path $root 'android/AndroidManifest.xml') -R (Join-Path $build 'resources.zip') -o (Join-Path $build 'unsigned.apk')
if ($LASTEXITCODE) {throw 'Research manifest link failed.'}
$sources = @(Get-ChildItem -LiteralPath (Join-Path $root 'android/src') -Filter '*.java' -Recurse | ForEach-Object FullName)
& (Join-Path $java 'javac.exe') -encoding UTF-8 -source 8 -target 8 -bootclasspath ((Join-Path $tools 'core-lambda-stubs.jar') + [IO.Path]::PathSeparator + $androidJar) -d (Join-Path $build 'classes') @sources
if ($LASTEXITCODE) {throw 'Research compilation failed.'}
& (Join-Path $java 'jar.exe') cf (Join-Path $build 'classes.jar') -C (Join-Path $build 'classes') .
if ($LASTEXITCODE) {throw 'Research jar failed.'}
& (Join-Path $tools 'd8.bat') --lib $androidJar --min-api 31 --output (Join-Path $build 'dex') (Join-Path $build 'classes.jar')
if ($LASTEXITCODE) {throw 'Research dex failed.'}
$archive = [IO.Compression.ZipFile]::Open((Join-Path $build 'unsigned.apk'), [IO.Compression.ZipArchiveMode]::Update)
try { [void][IO.Compression.ZipFileExtensions]::CreateEntryFromFile($archive,(Join-Path $build 'dex/classes.dex'),'classes.dex') } finally {$archive.Dispose()}
& (Join-Path $tools 'zipalign.exe') -f -p 4 (Join-Path $build 'unsigned.apk') (Join-Path $build 'aligned.apk')
if ($LASTEXITCODE) {throw 'Research alignment failed.'}
$key = Join-Path $repo 'build/research-test.jks'
if (!(Test-Path -LiteralPath $key)) {
    & (Join-Path $java 'keytool.exe') -genkeypair -keystore $key -storepass android -keypass android -alias research -keyalg RSA -keysize 2048 -validity 3650 -dname 'CN=AutoDing Research Test'
    if ($LASTEXITCODE) {throw 'Research signing key failed.'}
}
$apk = Join-Path $repo 'build/AutoDing-research.apk'
& (Join-Path $tools 'apksigner.bat') sign --ks $key --ks-key-alias research --ks-pass pass:android --key-pass pass:android --out $apk (Join-Path $build 'aligned.apk')
if ($LASTEXITCODE) {throw 'Research signing failed.'}
& (Join-Path $tools 'apksigner.bat') verify $apk
if ($LASTEXITCODE) {throw 'Research signature verification failed.'}
Write-Output $apk
