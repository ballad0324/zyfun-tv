# 用法：.\build-debug.ps1            构建全部三 flavor debug
#       .\build-debug.ps1 phone      仅构建指定 flavor（universal|phone|tv）
# ===== 本机环境路径（按本机实际修改；以下为占位，勿填他人路径）=====
# $env:JAVA_HOME    本机 JDK 17 安装目录
# $env:ANDROID_HOME Android SDK 目录
# $gradle           gradle 可执行文件路径，或项目根 gradlew.bat
$env:JAVA_HOME    = if ($env:JAVA_HOME)    { $env:JAVA_HOME }    else { "<JDK17_HOME>" }
$env:ANDROID_HOME = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { "<ANDROID_SDK_HOME>" }
$gradle = "<GRADLE_BAT_OR_PROJECT_GRADW>"
$proj = $PSScriptRoot

$targets = if ($args.Count -gt 0) { @($args[0]) } else { @("universal", "phone", "tv") }
foreach ($f in $targets) {
    $cap = $f.Substring(0, 1).ToUpper() + $f.Substring(1)
    Write-Output "===== assemble $f debug ====="
    & $gradle -p $proj ":app:assemble${cap}Debug" --console=plain
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}
exit 0
