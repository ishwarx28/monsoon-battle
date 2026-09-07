@echo off
powershell -NoProfile -File "%~dp0gradle\bootstrap.ps1" %*
exit /b %ERRORLEVEL%
