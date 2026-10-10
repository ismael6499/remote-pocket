@echo off
title Remote Pocket - Uninstallation
cd /d "%~dp0"

echo ========================================================
echo        Remote Pocket PC Agent - Uninstaller
echo ========================================================
echo.

echo Removing Windows Scheduled Task 'RemotePocketAgent'...
schtasks /end /tn "RemotePocketAgent" >nul 2>nul
schtasks /delete /tn "RemotePocketAgent" /f >nul 2>nul
if %ERRORLEVEL% EQU 0 (
    echo [OK] Scheduled task removed.
) else (
    echo [INFO] No scheduled task found.
)

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
