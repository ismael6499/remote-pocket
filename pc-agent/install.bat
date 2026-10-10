@echo off
setlocal enabledelayedexpansion
title Remote Pocket - Installation Wizard
cd /d "%~dp0"

echo ========================================================
echo        Remote Pocket PC Agent - Setup Wizard
echo ========================================================
echo.

:: 1. Check Node.js
where node >nul 2>nul
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Node.js is not installed or not in PATH!
    echo Please download and install Node.js (LTS version) from:
    echo https://nodejs.org/
    echo.
    pause
    exit /b 1
)

echo [1/4] Node.js detected:
node -v

:: 2. Check & Install NPM dependencies
echo.
echo [2/4] Installing dependencies with npm...
call npm install --production
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Failed to install npm dependencies. Check internet connection.
    pause
    exit /b 1
)
echo Dependencies installed successfully.

:: 3. Compile InputHelper.cs with native Windows csc.exe
echo.
echo [3/4] Compiling native Win32 InputHelper...
set CSC_EXE=""
if exist "%windir%\Microsoft.NET\Framework64\v4.0.30319\csc.exe" (
    set CSC_EXE="%windir%\Microsoft.NET\Framework64\v4.0.30319\csc.exe"
) else if exist "%windir%\Microsoft.NET\Framework\v4.0.30319\csc.exe" (
    set CSC_EXE="%windir%\Microsoft.NET\Framework\v4.0.30319\csc.exe"
)

if %CSC_EXE%=="" (
    echo [ERROR] Could not find csc.exe (.NET Framework 4.0 is required).
    pause
    exit /b 1
)

%CSC_EXE% /nologo /optimize+ /target:exe /r:System.Drawing.dll /r:System.Windows.Forms.dll /out:InputHelper.exe InputHelper.cs
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Failed to compile InputHelper.cs.
    pause
    exit /b 1
)
echo InputHelper.exe compiled successfully.

:: 4. Create run_silent.vbs and Scheduled Task
echo.
echo [4/4] Configuring background auto-start at Windows logon...

:: Create run_silent.vbs dynamically with current directory
(
echo Set WshShell = CreateObject^("WScript.Shell"^)
echo WshShell.Run Chr^(34^) ^& "%~dp0start.bat" ^& Chr^(34^), 0, False
) > "%~dp0run_silent.vbs"

:: Create shortcut in Windows Startup folder (same as Express Gateway)
powershell -NoProfile -Command "$ws = New-Object -ComObject WScript.Shell; $sc = $ws.CreateShortcut(\"$env:APPDATA\Microsoft\Windows\Start Menu\Programs\Startup\RemotePocket_silent.vbs.lnk\"); $sc.TargetPath = \"%~dp0run_silent.vbs\"; $sc.WorkingDirectory = \"%~dp0\"; $sc.Save()" >nul 2>nul
if %ERRORLEVEL% EQU 0 (
    echo [OK] Windows Startup shortcut created in shell:startup.
)

echo.
echo ========================================================
echo        Installation Complete!
echo ========================================================
echo.
set /p START_NOW="Do you want to start the agent now in the background? (Y/N): "
if /i "!START_NOW!"=="Y" (
    wscript.exe "%~dp0run_silent.vbs"
    echo Remote Pocket is now running in the background!
)
pause
