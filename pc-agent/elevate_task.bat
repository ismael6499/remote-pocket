@echo off
setlocal enabledelayedexpansion
title Remote Pocket - Elevated Task Setup
cd /d "%~dp0"

:: 1. Check for Admin rights and auto-elevate if needed
net session >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo [INFO] Requesting Administrator privileges to create highest integrity task...
    powershell -NoProfile -Command "Start-Process cmd.exe -ArgumentList '/c \"\"%~f0\"\"' -Verb RunAs"
    exit /b 0
)

echo ========================================================
echo   Remote Pocket - Setting up Elevated Background Task
echo ========================================================
echo.

:: 2. Stop existing running instances to avoid port conflicts
echo [1/4] Stopping existing processes on port 5050...
call "%~dp0stop_server.bat"

:: 3. Remove Startup folder shortcuts (avoids duplicate execution)
echo [2/4] Removing user Startup shortcuts...
del "%APPDATA%\Microsoft\Windows\Start Menu\Programs\Startup\RemotePocket_silent.vbs.lnk" >nul 2>&1
del "%APPDATA%\Microsoft\Windows\Start Menu\Programs\Startup\run_silent.vbs.lnk" >nul 2>&1

:: 4. Create high-integrity scheduled task at logon
echo [3/4] Creating scheduled task 'RemotePocketAgent' (highest integrity / UIPI bypass)...
schtasks /create /tn "RemotePocketAgent" /tr "wscript.exe \"%~dp0run_silent.vbs\"" /sc onlogon /rl highest /f
if %ERRORLEVEL% EQU 0 (
    echo [OK] Scheduled task 'RemotePocketAgent' created successfully with highest integrity.
) else (
    echo [ERROR] Failed to create scheduled task.
    pause
    exit /b 1
)

:: 5. Launch the elevated task now
echo [4/4] Starting elevated agent...
schtasks /run /tn "RemotePocketAgent"
timeout /t 3 /nobreak >nul

echo.
echo ========================================================
echo   Setup Complete!
echo ========================================================
echo Remote Pocket is now running with Highest Privileges (UIPI bypass active).
echo.
pause
