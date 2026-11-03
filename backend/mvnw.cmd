@echo off
setlocal
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\maven-bootstrap.ps1" %*
exit /b %errorlevel%
