#requires -Version 5.1
param(
    [Parameter(Mandatory = $true)][string]$ImagePath,
    [string]$OutputPath
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$utf8 = [Text.UTF8Encoding]::new($false)
[Console]::OutputEncoding = $utf8
$OutputEncoding = $utf8

# This adapter supports only the known Huawei portrait attendance layout.
# Coordinates are scaled from 1080 x 2400; never run OCR on the full image.
$result = [ordered]@{
    OCRAvailable = $false
    failureReason = $null
    Layout = 'HuaweiAttendancePortraitV1'
    ImageWidth = $null
    ImageHeight = $null
    EngineLanguage = $null
    MorningLines = @()
    EveningLines = @()
    DateLines = @()
}
$sourceImage = $null
$failureStage = 'windows-ocr-unavailable'

function Wait-WinRt {
    param([object]$Operation, [type]$ResultType)
    $task = $script:asTaskMethod.MakeGenericMethod($ResultType).Invoke($null, @($Operation))
    if (!$task.Wait(30000)) {
        try { $Operation.Cancel() } catch {}
        throw 'OCR asynchronous operation timed out.'
    }
    return $task.GetAwaiter().GetResult()
}

function Read-CropLines {
    param([Drawing.Bitmap]$Image, [int[]]$ReferenceBounds, [object]$Engine)
    $left = [int][Math]::Floor($ReferenceBounds[0] * $Image.Width / 1080.0)
    $top = [int][Math]::Floor($ReferenceBounds[1] * $Image.Height / 2400.0)
    $right = [int][Math]::Ceiling($ReferenceBounds[2] * $Image.Width / 1080.0)
    $bottom = [int][Math]::Ceiling($ReferenceBounds[3] * $Image.Height / 2400.0)
    $rectangle = [Drawing.Rectangle]::new($left, $top, $right - $left, $bottom - $top)
    $crop = $null
    $memory = $null
    $randomAccess = $null
    $softwareBitmap = $null
    try {
        $crop = $Image.Clone($rectangle, [Drawing.Imaging.PixelFormat]::Format32bppArgb)
        if ($crop.Width -gt [Windows.Media.Ocr.OcrEngine]::MaxImageDimension -or
            $crop.Height -gt [Windows.Media.Ocr.OcrEngine]::MaxImageDimension) {
            throw 'Supported OCR crop dimensions were exceeded.'
        }
        $memory = [IO.MemoryStream]::new()
        $crop.Save($memory, [Drawing.Imaging.ImageFormat]::Png)
        $memory.Position = 0
        $randomAccess = [IO.WindowsRuntimeStreamExtensions]::AsRandomAccessStream($memory)
        $decoder = Wait-WinRt ([Windows.Graphics.Imaging.BitmapDecoder]::CreateAsync($randomAccess)) ([Windows.Graphics.Imaging.BitmapDecoder])
        $softwareBitmap = Wait-WinRt ($decoder.GetSoftwareBitmapAsync(
            [Windows.Graphics.Imaging.BitmapPixelFormat]::Bgra8,
            [Windows.Graphics.Imaging.BitmapAlphaMode]::Premultiplied)) ([Windows.Graphics.Imaging.SoftwareBitmap])
        $recognized = Wait-WinRt ($Engine.RecognizeAsync($softwareBitmap)) ([Windows.Media.Ocr.OcrResult])
        foreach ($line in $recognized.Lines) {
            $text = $line.Text.Trim()
            if ($text) { Write-Output $text }
        }
    } finally {
        foreach ($resource in @($softwareBitmap, $randomAccess, $memory, $crop)) {
            if ($resource -is [IDisposable]) { $resource.Dispose() }
        }
    }
}

try {
    if ([Environment]::OSVersion.Platform -ne [PlatformID]::Win32NT) {
        throw 'Windows is required.'
    }
    Add-Type -AssemblyName System.Drawing
    Add-Type -AssemblyName System.Runtime.WindowsRuntime
    [void][Windows.Media.Ocr.OcrEngine, Windows.Foundation, ContentType = WindowsRuntime]
    [void][Windows.Globalization.Language, Windows.Foundation, ContentType = WindowsRuntime]
    [void][Windows.Graphics.Imaging.BitmapDecoder, Windows.Foundation, ContentType = WindowsRuntime]
    [void][Windows.Graphics.Imaging.SoftwareBitmap, Windows.Foundation, ContentType = WindowsRuntime]
    [void][Windows.Media.Ocr.OcrResult, Windows.Foundation, ContentType = WindowsRuntime]
    $script:asTaskMethod = @([System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object {
        $_.Name -eq 'AsTask' -and $_.IsGenericMethodDefinition -and
        $_.GetGenericArguments().Count -eq 1 -and $_.GetParameters().Count -eq 1 -and
        $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1'
    })[0]
    if (!$script:asTaskMethod) { throw 'WinRT task bridge is unavailable.' }

    $failureStage = 'simplified-chinese-ocr-unavailable'
    $language = @([Windows.Media.Ocr.OcrEngine]::AvailableRecognizerLanguages | Where-Object {
        $_.LanguageTag -match '^zh-Hans(?:-|$)'
    } | Select-Object -First 1)
    if (!$language.Count) { throw 'A Simplified Chinese OCR engine is not installed.' }
    $engine = [Windows.Media.Ocr.OcrEngine]::TryCreateFromLanguage($language[0])
    if (!$engine) { throw 'The installed Simplified Chinese OCR engine could not be created.' }
    $result.EngineLanguage = $language[0].LanguageTag

    $failureStage = 'image-load-failed'
    $resolvedImagePath = [IO.Path]::GetFullPath($ImagePath)
    $sourceImage = [Drawing.Bitmap][Drawing.Image]::FromFile($resolvedImagePath)
    $result.ImageWidth = $sourceImage.Width
    $result.ImageHeight = $sourceImage.Height
    $failureStage = 'unsupported-layout-dimensions'
    if ($sourceImage.Width -le 0 -or $sourceImage.Height -le 0 -or
        [Math]::Abs(($sourceImage.Width / [double]$sourceImage.Height) - (1080.0 / 2400.0)) -gt 0.005) {
        throw 'Only the known Huawei portrait aspect ratio is supported.'
    }

    $failureStage = 'attendance-crop-ocr-failed'
    $result.MorningLines = @(Read-CropLines $sourceImage @(92, 590, 518, 765) $engine)
    $result.EveningLines = @(Read-CropLines $sourceImage @(540, 590, 992, 765) $engine)
    $result.DateLines = @(Read-CropLines $sourceImage @(310, 1320, 780, 1605) $engine)
    $result.OCRAvailable = $true
} catch {
    # Return a stable reason, never exception text containing a path or UI text.
    $result.failureReason = $failureStage
    $result.MorningLines = @()
    $result.EveningLines = @()
    $result.DateLines = @()
} finally {
    if ($sourceImage) { $sourceImage.Dispose() }
}

if ($OutputPath) {
    try {
        $resolvedOutputPath = [IO.Path]::GetFullPath($OutputPath)
        if ([string]::Equals([IO.Path]::GetFullPath($ImagePath), $resolvedOutputPath,
                [StringComparison]::OrdinalIgnoreCase)) {
            throw 'Output must not overwrite the source image.'
        }
        [IO.File]::WriteAllText($resolvedOutputPath, (ConvertTo-Json $result -Depth 5), $utf8)
    } catch {
        $result.OCRAvailable = $false
        $result.failureReason = 'output-write-failed'
        $result.MorningLines = @()
        $result.EveningLines = @()
        $result.DateLines = @()
    }
}
ConvertTo-Json $result -Depth 5
