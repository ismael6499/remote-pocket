# Remote Pocket - Handoff & Project Status

## Project Overview
Remote Pocket is a monorepo containing:
1. `pc-agent/`: Lightweight Node.js + native C# daemon for Windows that controls mouse, keyboard, display brightness, system power, volume, custom shortcuts, and serves live screen preview (~2 FPS).
2. `android-app/`: Native Java Android application with zero-touch LAN discovery (UDP broadcast), Ngrok support, full trackpad with direct touch, dual keyboards (Gboard + extended PC keyboard), system controls, and OTA updates.

## Current Status (Scaffolding Completed)
- [x] Initialized Git repository and local identity (`Agustin` / `acostaagustin6499@gmail.com`).
- [x] Created repository structure (`pc-agent/`, `android-app/`).
- [x] Configured `.gitignore` and `README.md`.
- [x] Configured `.agents/AGENTS.md` with:
  - User identity and friendly Spanish interaction.
  - Granular commit strategy.
  - Strict work-hours git push restriction (Mon-Fri 09:00-18:00 ART).
  - Clean server execution (no hanging daemon tasks in Antigravity).
  - Default English UI with i18n (`strings.xml`).
  - Handoff maintenance.
- [x] Copied base `InputHelper.cs` to `pc-agent/`.
- [x] Created `pc-agent/config.json`.

## Immediate Next Steps (To be performed in the new chat)
1. **Build `pc-agent/`**:
   - `package.json` with dependencies (`express`, `ws`).
   - `shortcuts.json` with base sample shortcuts.
   - `server.js` implementing:
     - UDP discovery responder on port 5051.
     - WebSocket server on port 5050 for mouse/keyboard and live screen stream.
     - REST API for volume, power actions (sleep, lock, shutdown, restart), brightness (WMI), clipboard, and OTA version check.
   - `install.bat`, `start.bat`, `uninstall.bat`.
   - Test `pc-agent` locally on this PC.
2. **Build `android-app/`**:
   - Initialize Android Gradle project in Java.
   - Implement UDP discovery client & WebSocket client.
   - Implement UI (Glassmorphism dark theme, Touchpad, Live Screen view, dual keyboards, system controls).
   - Test APK build & deployment.
