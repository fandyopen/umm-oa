@echo off
setlocal
title UMM OA Backend - 127.0.0.1:8080

set "PROJECT_DIR=E:\www\umm-oa\back"
set "JAVA_HOME=%USERPROFILE%\.jdks\jdk-17.0.21"
set "JAR=%PROJECT_DIR%\target\umm-oa-back-0.1.0-SNAPSHOT.jar"

if not exist "%JAVA_HOME%\bin\java.exe" (
    echo JDK 17 was not found at:
    echo %JAVA_HOME%
    echo.
    pause
    exit /b 1
)

if not exist "%JAR%" (
    echo Backend JAR was not found. Building it now...
    pushd "%PROJECT_DIR%"
    set "PATH=%JAVA_HOME%\bin;%PATH%"
    call mvnw.cmd -DskipTests package
    if errorlevel 1 (
        popd
        echo.
        echo Build failed.
        pause
        exit /b 1
    )
    popd
)

netstat -ano | findstr ":8080" | findstr "LISTENING" >nul
if not errorlevel 1 (
    echo Port 8080 is already in use.
    echo Backend URL: http://127.0.0.1:8080
    echo.
    pause
    exit /b 0
)

cd /d "%PROJECT_DIR%"
echo Starting UMM OA backend on http://127.0.0.1:8080
echo Press Ctrl+C or close this window to stop the backend.
echo.

"%JAVA_HOME%\bin\java.exe" -jar "%JAR%"

echo.
echo Backend has stopped.
pause
