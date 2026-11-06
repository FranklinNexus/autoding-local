#requires -Version 7.0
param([string]$SdkPath)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
if (!$SdkPath) {
    $SdkPath = if ($env:ANDROID_HOME) { $env:ANDROID_HOME }
        elseif ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT }
        else { Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
}
$target = Join-Path (Split-Path $PSScriptRoot) 'build\ui-dump'
foreach ($name in @('classes', 'dex')) {
    $generated = [IO.Path]::GetFullPath((Join-Path $target $name))
    $expected = [IO.Path]::GetFullPath($target) + [IO.Path]::DirectorySeparatorChar
    if (!$generated.StartsWith($expected, [StringComparison]::OrdinalIgnoreCase)) { throw 'Unexpected UI build output path.' }
    if (Test-Path -LiteralPath $generated) { Remove-Item -LiteralPath $generated -Recurse -Force }
}
New-Item -ItemType Directory -Path $target,(Join-Path $target 'classes'),(Join-Path $target 'dex') -Force | Out-Null
$androidJar = Join-Path $SdkPath 'platforms\android-35\android.jar'
$toolsDirectory = Join-Path $SdkPath 'build-tools\35.0.0'
$javaDirectory = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin' } else { Split-Path (Get-Command javac).Source }
$bootPath = (Join-Path $toolsDirectory 'core-lambda-stubs.jar') + [IO.Path]::PathSeparator + $androidJar
& (Join-Path $javaDirectory 'javac.exe') -encoding UTF-8 -source 8 -target 8 -bootclasspath $bootPath -d (Join-Path $target 'classes') (Join-Path $PSScriptRoot 'android-ui\ReadWindow.java')
if ($LASTEXITCODE -ne 0) { throw 'UI helper compilation failed.' }
& (Join-Path $javaDirectory 'jar.exe') cf (Join-Path $target 'classes.jar') -C (Join-Path $target 'classes') .
if ($LASTEXITCODE -ne 0) { throw 'UI helper packaging failed.' }
& (Join-Path $toolsDirectory 'd8.bat') --lib $androidJar --min-api 23 --output (Join-Path $target 'dex') (Join-Path $target 'classes.jar')
if ($LASTEXITCODE -ne 0) { throw 'UI helper dex compilation failed.' }
& (Join-Path $javaDirectory 'jar.exe') cf (Join-Path $target 'autoding-ui.jar') -C (Join-Path $target 'dex') classes.dex
if ($LASTEXITCODE -ne 0) { throw 'UI helper final packaging failed.' }
Write-Output (Join-Path $target 'autoding-ui.jar')
