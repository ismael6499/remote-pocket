@echo off
cd /d "%~dp0"
if exist "C:\Program Files\nodejs\node.exe" (
    "C:\Program Files\nodejs\node.exe" server.js
) else (
    node server.js
)
if %ERRORLEVEL% NEQ 0 pause

