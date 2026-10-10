@echo off
setlocal enabledelayedexpansion
title Remote Pocket - Update
cd /d "%~dp0"

echo ========================================================
echo        Remote Pocket PC Agent - Updater
echo ========================================================
echo.

echo [1/4] Pulling latest changes from git...
git pull
if %ERRORLEVEL% NEQ 0 (
    echo [WARN] git pull returned an error or git is not configured.
)

echo.
echo [2/4] Updating npm packages...
call npm install --production

echo.
echo [3/4] Recompiling native InputHelper...
set CSC_EXE=""
if exist "%windir%\Microsoft.NET\Framework64\v4.0.30319\csc.exe" (
    set CSC_EXE="%windir%\Microsoft.NET\Framework64\v4.0.30319\csc.exe"
) else if exist "%windir%\Microsoft.NET\Framework\v4.0.30319\csc.exe" (
    set CSC_EXE="%windir%\Microsoft.NET\Framework\v4.0.30319\csc.exe"
)

if !CSC_EXE! NEQ "" (
    !CSC_EXE! /nologo /optimize+ /target:exe /r:System.Drawing.dll /r:System.Windows.Forms.dll /out:InputHelper.exe InputHelper.cs
    echo [OK] InputHelper.exe updated.
)

echo.
echo [4/4] Restarting background service...
schtasks /run /tn "RemotePocketAgent" >nul 2>nul
if %ERRORLEVEL% EQU 0 (
    echo [OK] RemotePocketAgent restarted via Windows Scheduled Task.
) else (
    echo [INFO] You can run start.bat manually to restart the agent.
)

echo.
echo ========================================================
echo        Update Complete!
echo ========================================================
pause
