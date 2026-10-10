# Remote Pocket - Handoff & Project Status

## Project Overview
Remote Pocket is a monorepo containing:
1. `pc-agent/`: Lightweight Node.js + native C# daemon for Windows that controls mouse, keyboard, display brightness, system power, volume, custom shortcuts, and serves live screen preview (~2 FPS).
2. `android-app/`: Native Java Android application with zero-touch LAN discovery (UDP broadcast), Ngrok support, full trackpad with direct touch, dual keyboards (Gboard + extended PC keyboard), system controls, and OTA updates.

## Current Status

### 1. Scaffolding & Configuration
- [x] Initialized Git repository and local identity (`Agustin` / `acostaagustin6499@gmail.com`).
- [x] Configured `.gitignore` and `README.md`.
- [x] Configured `.agents/AGENTS.md` (identidad, commits secuenciales, no daemons colgados, strings en inglés por defecto, etc.).

### 2. PC Agent (`pc-agent/`) - COMPLETED & VERIFIED
- [x] `InputHelper.cs` & `InputHelper.exe`:
  - Native Win32 high-performance mouse movements, clicks, double click, mouse down/up, and wheel scroll.
  - Full keyboard support (Unicode text typing, shortcut combos with Ctrl/Alt/Shift/Win, F1-F12, Esc, Tab, arrows, etc.).
  - Windows Volume & Media keys (`VK_VOLUME_UP`, `VK_VOLUME_DOWN`, `VK_VOLUME_MUTE`, `VK_MEDIA_PLAY_PAUSE`, `VK_MEDIA_NEXT_TRACK`, `VK_MEDIA_PREV_TRACK`).
  - CoreAudioApi (COM) native volume scalar query & setting, mute toggle, and audio peak detection without external utilities.
  - Native Win32 GDI+ Screen Capture with DPI awareness, cursor overlay, 720p downscaling, and 65% JPEG compression in memory.
  - Desktop access resolution (`winsta0\default`) and graceful fallback rendering for locked sessions.
  - Clipboard get/set via native Win32 STA COM API.
- [x] `config.json` & `shortcuts.json`:
  - Multi-PC device naming, custom ports (5050/5051), PIN support.
  - Built-in shortcuts (Calculator, Notepad, Task Manager, File Explorer, OGC automation runner).
- [x] `server.js`:
  - Persistent input pipe (`InputHelper.exe --pipe`) with automatic crash recovery.
  - On-demand screen stream at 2 FPS: runs only when WebSocket clients are subscribed (0% CPU and zero bandwidth when closed).
  - UDP Discovery Responder on port 5051 responding to `REMOTE_POCKET_DISCOVER` broadcasts.
  - WebSocket server on port 5050 handling mouse, keyboard, clipboard, media keys, and binary JPEG frames.
  - REST endpoints: `/api/status`, `/api/screen/frame`, `/api/audio`, `/api/brightness`, `/api/clipboard`, `/api/shortcuts`, `/api/system/lock|sleep|shutdown|restart`, `/api/version`, and OTA static APK server at `/updates`.
  - Ngrok tunnel integration with `@ngrok/ngrok` (using `NGROK_AUTHTOKEN`).
- [x] Setup & Execution Scripts:
  - `install.bat`: Self-install wizard that verifies Node.js, runs `npm install --production`, auto-detects `csc.exe` in .NET 4.0, compiles `InputHelper.cs`, creates `run_silent.vbs`, and registers Windows Scheduled Task (`schtasks /sc onlogon`).
  - `start.bat`: Self-healing launcher (auto-installs npm dependencies and auto-compiles C# if missing on new PCs).
  - `uninstall.bat`: Clean uninstaller removing scheduled tasks and stopping background processes.
  - `update.bat`: Git pull, package update, C# recompilation, and scheduled task refresh.
  - `run_silent.vbs`: Hidden window runner.
- [x] End-to-End Verification:
  - Verified via `test_agent.js`: HTTP status, shortcuts, UDP broadcast response, WebSocket ping/pong, and clean shutdown passed 100%.

## Immediate Next Steps (Paso 3 & 4)
1. **Initialize `android-app/`**:
   - Create Android Java structure (`com.remotepocket`).
   - Configure `build.gradle` (minSdk 26, targetSdk 34, OkHttp, Material Design).
   - Setup centralized i18n (`res/values/strings.xml` in English, `res/values-es/strings.xml` in Spanish).
2. **Network Layer**:
   - `UdpDiscovery.java`: Broadcast listener for automatic LAN PC detection.
   - `WebSocketManager.java`: Low-latency connection for mouse/keyboard inputs & receiving JPEG screen frames.
   - `UpdateManager.java`: OTA update checker against `/api/version` and APK download.
3. **UI & Control Views (Glassmorphism Dark `#0B0F19`)**:
   - PC selector dropdown/toolbar.
   - Live Screen view with interactive trackpad overlay.
   - Floating Action Button (FAB) toggling:
     - Android standard keyboard (Gboard) + modifier accessory bar.
     - Extended PC keyboard (F1-F12, Win, Alt+Tab, Taskmgr, arrows).
   - System control bottom sheet (brightness slider, volume slider, power dialogs with confirmations, custom shortcut buttons).
4. **Build & Verify APK**.
