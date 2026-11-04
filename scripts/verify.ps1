param([string]$JavaHome = $env:JAVA_HOME)

$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
if (-not $JavaHome -and (Test-Path "$root\local.properties")) {
    $entry = Get-Content "$root\local.properties" | Where-Object { $_ -match '^jdk\.dir=' } | Select-Object -First 1
    if ($entry) { $JavaHome = $entry.Substring(8).Replace('\\', '\').Replace('\:', ':') }
}
if ($JavaHome) {
    if (-not (Test-Path "$JavaHome\bin\java.exe")) { throw "No Java executable in $JavaHome" }
    $env:JAVA_HOME = $JavaHome
}
Push-Location $root
try {
    & '.\gradlew.bat' testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest --console=plain
    if ($LASTEXITCODE -ne 0) { throw "Gradle verification failed with exit code $LASTEXITCODE" }
} finally {
    Pop-Location
}
