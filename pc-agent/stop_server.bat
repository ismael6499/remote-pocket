@echo off
echo Finding and terminating processes on port 5050...
for /f "tokens=5" %%a in ('netstat -aon ^| findstr :5050') do (
    taskkill /f /pid %%a 2>nul
    echo Stopped process on port 5050 (PID: %%a).
)
taskkill /f /im InputHelper.exe 2>nul
echo Remote Pocket Agent stopped.
