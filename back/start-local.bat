@echo off
setlocal
title UMM OA Backend - 127.0.0.1:8080

powershell.exe -NoLogo -NoProfile -ExecutionPolicy RemoteSigned -File "%~dp0start-local.ps1"
set "START_EXIT=%ERRORLEVEL%"

echo.
if not "%START_EXIT%"=="0" echo Backend startup failed. See the error above.
pause
exit /b %START_EXIT%
