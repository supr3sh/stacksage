@echo off
if not defined JAVA_HOME (
    echo JAVA_HOME is not set. Please set it to your JDK 17 installation.
    exit /b 1
)
call mvnw.cmd -s .mvn/settings.xml %*
