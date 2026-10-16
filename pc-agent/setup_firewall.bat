@echo off
title Remote Pocket - Windows Firewall Setup
echo ========================================================
echo   Configuring Windows Firewall for Remote Pocket...
echo ========================================================
echo.

:: Add Inbound Rules for Port 5050 (HTTP/WebSocket) and 5051 (UDP Discovery)
netsh advfirewall firewall delete rule name="Remote Pocket Agent (TCP 5050)" >nul 2>&1
netsh advfirewall firewall add rule name="Remote Pocket Agent (TCP 5050)" dir=in action=allow protocol=TCP localport=5050 profile=any >nul 2>&1
if %ERRORLEVEL% EQU 0 (
    echo [OK] Allowed inbound TCP port 5050 - all profiles: Private, Public, Domain, VPN.
) else (
    echo [WARN] Could not configure firewall automatically. Administrator rights may be required.
)

netsh advfirewall firewall delete rule name="Remote Pocket Discovery (UDP 5051)" >nul 2>&1
netsh advfirewall firewall add rule name="Remote Pocket Discovery (UDP 5051)" dir=in action=allow protocol=UDP localport=5051 profile=any >nul 2>&1
if %ERRORLEVEL% EQU 0 (
    echo [OK] Allowed inbound UDP port 5051 - discovery broadcast.
)

echo.
echo Firewall configuration complete.
