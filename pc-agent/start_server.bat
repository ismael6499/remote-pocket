@echo off
cd /d "%~dp0"
if exist "C:\Program Files\nodejs\node.exe" (
    "C:\Program Files\nodejs\node.exe" server.js >> server_out.log 2>> server_err.log
) else (
    node server.js >> server_out.log 2>> server_err.log
)
if %ERRORLEVEL% NEQ 0 pause

