if (-not $env:JAVA_HOME) {
    Write-Error "JAVA_HOME is not set. Please set it to your JDK 17 installation."
    exit 1
}
& "$PSScriptRoot\mvnw.cmd" -s .mvn/settings.xml @args
