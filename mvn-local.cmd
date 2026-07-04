@echo off
setlocal

set "IDEA_MVN=C:\Program Files\JetBrains\IntelliJ IDEA Community Edition 2024.2.6\plugins\maven\lib\maven3\bin\mvn.cmd"

if exist "%IDEA_MVN%" (
    "%IDEA_MVN%" %*
) else (
    mvn %*
)
