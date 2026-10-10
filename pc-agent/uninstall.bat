@echo off
title Remote Pocket - Uninstallation
cd /d "%~dp0"

echo ========================================================
echo        Remote Pocket PC Agent - Uninstaller
echo ========================================================
echo.

echo Removing Windows Startup shortcut...
del "%APPDATA%\Microsoft\Windows\Start Menu\Programs\Startup\RemotePocket_silent.vbs.lnk" >nul 2>nul
echo [OK] Startup shortcut removed.

echo Stopping any running agent processes on port 5050...
for /f "tokens=5" %%a in ('netstat -aon ^| findstr ":5050" ^| findstr "LISTENING"') do (
    echo Terminating PID %%a...
    taskkill /F /PID %%a >nul 2>nul
)

echo.
echo ========================================================
echo        Uninstallation Complete!
echo ========================================================
echo Remote Pocket has been successfully stopped and unregistered.
pause
