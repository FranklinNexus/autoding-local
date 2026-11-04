param(
    [string]$SdkPath,
    [string]$BuildToolsVersion = '35.0.0',
    [string]$CompilePlatform = 'android-35'
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
Push-Location $PSScriptRoot
try {
    if (!$SdkPath) {
        $SdkPath = if ($env:ANDROID_HOME) { $env:ANDROID_HOME }
            elseif ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT }
            elseif ($env:LOCALAPPDATA) { Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
            else { throw 'Set ANDROID_HOME or pass -SdkPath.' }
    }
    $toolsPath = Join-Path $SdkPath "build-tools\$BuildToolsVersion"
    $androidJar = Join-Path $SdkPath "platforms\$CompilePlatform\android.jar"
    if (!(Test-Path -LiteralPath $androidJar) -or !(Test-Path -LiteralPath (Join-Path $toolsPath 'aapt2.exe'))) {
        throw "Install SDK platform $CompilePlatform and build-tools $BuildToolsVersion, or pass their versions."
    }
    $javaPath = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin' } else { Split-Path (Get-Command javac).Source }
    $buildPath = Join-Path $PSScriptRoot 'build'
    New-Item -ItemType Directory -Force -Path $buildPath,(Join-Path $buildPath 'classes'),(Join-Path $buildPath 'dex') | Out-Null
    $sources = @(Get-ChildItem -Recurse -Filter '*.java' src | ForEach-Object { $_.FullName })
    $compileBootPath = (Join-Path $toolsPath 'core-lambda-stubs.jar') + [System.IO.Path]::PathSeparator + $androidJar
    & (Join-Path $javaPath 'javac.exe') -encoding UTF-8 -source 8 -target 8 -bootclasspath $compileBootPath -d (Join-Path $buildPath 'classes') @sources
    if ($LASTEXITCODE -ne 0) { throw 'javac failed' }
    & (Join-Path $javaPath 'jar.exe') cf (Join-Path $buildPath 'classes.jar') -C (Join-Path $buildPath 'classes') .
    if ($LASTEXITCODE -ne 0) { throw 'jar failed' }
    & (Join-Path $toolsPath 'd8.bat') --lib $androidJar --min-api 23 --output (Join-Path $buildPath 'dex') (Join-Path $buildPath 'classes.jar')
    if ($LASTEXITCODE -ne 0) { throw 'd8 failed' }
    & (Join-Path $toolsPath 'aapt2.exe') link -I $androidJar --manifest AndroidManifest.xml -o (Join-Path $buildPath 'unsigned.apk')
    if ($LASTEXITCODE -ne 0) { throw 'aapt2 failed' }
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $archive = [System.IO.Compression.ZipFile]::Open((Join-Path $buildPath 'unsigned.apk'), [System.IO.Compression.ZipArchiveMode]::Update)
    try { [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($archive, (Join-Path $buildPath 'dex\classes.dex'), 'classes.dex') | Out-Null } finally { $archive.Dispose() }
    & (Join-Path $toolsPath 'zipalign.exe') -f -p 4 (Join-Path $buildPath 'unsigned.apk') (Join-Path $buildPath 'aligned.apk')
    if ($LASTEXITCODE -ne 0) { throw 'zipalign failed' }
    $keyPath = Join-Path $buildPath 'local-test.jks'
    if (!(Test-Path -LiteralPath $keyPath)) {
        & (Join-Path $javaPath 'keytool.exe') -genkeypair -keystore $keyPath -storepass android -keypass android -alias local-test -keyalg RSA -keysize 2048 -validity 3650 -dname 'CN=AutoDing Local Test'
        if ($LASTEXITCODE -ne 0) { throw 'keytool failed' }
    }
    & (Join-Path $toolsPath 'apksigner.bat') sign --ks $keyPath --ks-key-alias local-test --ks-pass pass:android --key-pass pass:android --out (Join-Path $buildPath 'AutoDing-local-test.apk') (Join-Path $buildPath 'aligned.apk')
    if ($LASTEXITCODE -ne 0) { throw 'apksigner failed' }
    & (Join-Path $toolsPath 'apksigner.bat') verify --verbose (Join-Path $buildPath 'AutoDing-local-test.apk')
    if ($LASTEXITCODE -ne 0) { throw 'APK verification failed' }
} finally { Pop-Location }
