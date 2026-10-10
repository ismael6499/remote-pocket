@echo off
setlocal enabledelayedexpansion
title Remote Pocket - PC Agent
cd /d "%~dp0"

echo ========================================================
echo        Starting Remote Pocket Agent...
echo ========================================================

:: 1. Self-heal: Check if node_modules exists, install if missing
if not exist "node_modules\" (
    echo [INFO] node_modules not found. Installing dependencies...
    call npm install --production
    if %ERRORLEVEL% NEQ 0 (
        echo [ERROR] Failed to install npm dependencies.
        pause
        exit /b 1
    )
)

:: 2. Self-heal: Check if InputHelper.exe exists, compile if missing
if not exist "InputHelper.exe" (
    echo [INFO] InputHelper.exe not found. Compiling with native csc.exe...
    set CSC_EXE=""
    if exist "%windir%\Microsoft.NET\Framework64\v4.0.30319\csc.exe" (
        set CSC_EXE="%windir%\Microsoft.NET\Framework64\v4.0.30319\csc.exe"
    ) else if exist "%windir%\Microsoft.NET\Framework\v4.0.30319\csc.exe" (
        set CSC_EXE="%windir%\Microsoft.NET\Framework\v4.0.30319\csc.exe"
    )

    if !CSC_EXE! NEQ "" (
        !CSC_EXE! /nologo /optimize+ /target:exe /r:System.Drawing.dll /r:System.Windows.Forms.dll /out:InputHelper.exe InputHelper.cs
        if %ERRORLEVEL% EQU 0 (
            echo [OK] InputHelper.exe compiled successfully.
        ) else (
            echo [WARN] Compilation failed.
        )
    )
)

:: 3. Run Node server
node server.js
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo Server exited with error code %ERRORLEVEL%.
    pause
)
