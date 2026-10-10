@echo off
cd /d "%~dp0"
node server.js
if %ERRORLEVEL% NEQ 0 pause

