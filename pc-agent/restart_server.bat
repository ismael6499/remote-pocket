@echo off
cd /d "%~dp0"
echo Restarting Remote Pocket Agent...
call stop_server.bat
ping 127.0.0.1 -n 2 > nul
wscript.exe run_silent.vbs
echo Remote Pocket Agent restarted in background.
