@echo off
setlocal enabledelayedexpansion
title Remote Pocket - Change Device Name
cd /d "%~dp0"

echo ========================================================
echo           Remote Pocket - Change Device Name
echo ========================================================
echo.

node -e "const fs = require('fs'); const p = 'config.json'; if (fs.existsSync(p)) { const c = JSON.parse(fs.readFileSync(p)); console.log(c.deviceName || 'PC Personal'); } else { console.log('PC Personal'); }" > "%temp%\rp_current_name.txt"
set /p CURRENT_NAME=<"%temp%\rp_current_name.txt"
del "%temp%\rp_current_name.txt" >nul 2>&1

echo Current device name: "!CURRENT_NAME!"
echo.
set /p NEW_NAME="Enter new name for this PC [Press Enter to keep current]: "
if "!NEW_NAME!"=="" (
    echo [INFO] Name unchanged.
    pause
    exit /b 0
)

node -e "const fs = require('fs'); const p = 'config.json'; let c = fs.existsSync(p) ? JSON.parse(fs.readFileSync(p)) : {}; c.deviceName = process.argv[1]; fs.writeFileSync(p, JSON.stringify(c, null, 2)); console.log('[OK] Device name updated to: ' + c.deviceName);" "!NEW_NAME!"

echo.
echo Applying changes to background agent...
wscript.exe "%~dp0run_silent.vbs"
echo [OK] Done! The new name will now be broadcasted to your devices.
echo.
pause
