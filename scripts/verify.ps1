param([string]$JavaHome = $env:JAVA_HOME)
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
if (-not $JavaHome) {
    $JavaHome = Get-ChildItem 'C:\Program Files\Eclipse Adoptium' -Directory -Filter 'jdk-25*' |
        Sort-Object Name -Descending | Select-Object -First 1 -ExpandProperty FullName
}
if (-not (Test-Path -LiteralPath "$JavaHome\bin\java.exe")) { throw 'Specify a Java 25 JDK using -JavaHome.' }
$originalJava = $env:JAVA_HOME
$originalOptions = $env:JAVA_TOOL_OPTIONS
$temporary = Join-Path $root '.tools\tmp'
New-Item -ItemType Directory -Force -Path $temporary | Out-Null
try {
    $env:JAVA_HOME = $JavaHome
    $env:JAVA_TOOL_OPTIONS = "$originalOptions -Djdk.net.unixdomain.tmpdir=$temporary -Djava.io.tmpdir=$temporary"
    Push-Location $root
    try {
        & .\gradlew.bat releaseBundle :app:installDist --console=plain
        if ($LASTEXITCODE -ne 0) { throw 'Gradle verification failed.' }
    } finally { Pop-Location }
} finally {
    $env:JAVA_HOME = $originalJava
    $env:JAVA_TOOL_OPTIONS = $originalOptions
}
