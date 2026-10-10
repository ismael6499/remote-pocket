@echo off
title Remote Pocket Agent (Console Mode)
cd /d "%~dp0"
node server.js
if %ERRORLEVEL% NEQ 0 pause
