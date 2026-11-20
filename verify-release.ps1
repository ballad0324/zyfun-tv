# 用法：.\verify-release.ps1            校验全部三 flavor release
#       .\verify-release.ps1 phone      仅校验指定 flavor（universal|phone|tv）
# ===== 本机环境路径（按本机实际修改；以下为占位，勿填他人路径）=====
# $env:JAVA_HOME    本机 JDK 17 安装目录
# $env:ANDROID_HOME Android SDK 目录（aapt/apksigner 位于其 build-tools/<version> 下）
$env:JAVA_HOME    = if ($env:JAVA_HOME)    { $env:JAVA_HOME }    else { "<JDK17_HOME>" }
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
$sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { "<ANDROID_SDK_HOME>" }
$buildTools = Join-Path $sdk "build-tools\36.0.0"
$aapt = Join-Path $buildTools "aapt.exe"
$apksigner = Join-Path $buildTools "apksigner.bat"
$proj = $PSScriptRoot
$base = Join-Path $proj "app\build\outputs\apk"

$targets = if ($args.Count -gt 0) { @($args[0]) } else { @("universal", "phone", "tv") }
foreach ($f in $targets) {
    $apk = Join-Path $base "$f\release\app-$f-release.apk"
    Write-Output "===== verify $f : $apk ====="
    if (-not (Test-Path $apk)) {
        Write-Output "APK_NOT_FOUND: $apk"
        exit 1
    }
    $size = (Get-Item $apk).Length
    Write-Output ("APK_SIZE_BYTES=" + $size)
    Write-Output "--- badging ---"
    & $aapt dump badging $apk | Select-String "^package:|application-label:|sdkVersion:|targetSdkVersion:"
    Write-Output "--- signer ---"
    & $apksigner verify --print-certs $apk
    Write-Output ("VERIFY_EXIT=" + $LASTEXITCODE)
}
exit 0
