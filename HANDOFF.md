# Remote Pocket - Handoff & Project Status

## Project Overview
Remote Pocket is a monorepo containing:
1. `pc-agent/`: Lightweight Node.js + native C# daemon for Windows that controls mouse, keyboard, display brightness, system power, volume, custom shortcuts, and serves live screen preview (~2 FPS).
2. `android-app/`: Native Java Android application with zero-touch LAN discovery (UDP broadcast), Ngrok support, full trackpad with direct touch, dual keyboards (Gboard + extended PC keyboard), system controls, and OTA updates.

## Current Status

### 1. Scaffolding & Configuration - COMPLETED
- [x] Initialized Git repository and monorepo structure.
- [x] Configured `.gitignore` (ignoring `node_modules`, executables, build dirs, and `.apk` binaries).
- [x] Configured development guidelines and project documentation.

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

### 3. Android App (`android-app/`) - COMPLETED & COMPILED
- [x] Architecture & Build System:
  - Native Java (compileSdk 34, minSdk 26, targetSdk 34).
  - Gradle 8.7 wrapper + Android Gradle Plugin 8.4.0.
  - Java 17 / 21 compatibility.
  - Dependencies: AndroidX, Material Design 3, OkHttp 4.12.0, Gson 2.10.1.
- [x] UI & Design (Glassmorphism Dark `#0B0F19`):
  - Dark surfaces (`#161F30`), translucent cards, Indigo (`#6366F1`) and Emerald (`#10B981`) accents.
  - Dynamic PC selector toolbar with live status dot (Green connected, Red disconnected).
  - Mode Tabs: Trackpad vs Live Screen view.
  - Live Screen stream viewer with simultaneous trackpad interaction.
  - Bottom mouse action buttons (Left, Right, Double Click, Scroll Up, Scroll Down).
  - Floating Action Button (FAB) draggable anywhere on screen to choose between Gboard or Extended PC Keyboard.
- [x] Dual Keyboards:
  - Standard Android Keyboard (Gboard): soft input with direct text transmission and voice-to-text.
  - Full Extended PC Keyboard (`PcKeyboardDialog`):
    - Complete physical QWERTY layout: Numbers (1-0, -, =, Bksp), Letters (QWERTY, ASDF, ZXCV), Punctuation ([, ], ;, ', \, ,, ., /), Space, Enter, Tab.
    - Directional arrows: Up, Down, Left, Right.
    - Sticky modifiers: `Ctrl`, `Alt`, `Shift`, `Win` with visual state feedback and auto-release after chord dispatch.
    - Caps Lock: Syncs state with PC (`capslock`), highlights visual indicator, and dynamically toggles uppercase/lowercase on keycap labels.
    - Header quick `Esc` button for instantaneous one-touch escape/exit.
    - Collapsible "⚡ Special Actions & F-Keys ▾" panel with 1-click shortcuts (`Ctrl+Alt+Del`, `Alt+Tab`, `Win+D`, `TaskMgr`, `Win+L`, `Ctrl+C`, `Ctrl+V`), navigation (`Ins`, `Del`, `Home`, `End`, `PgUp`, `PgDn`, `PrtScn`), and `F1` to `F12`.
- [x] System & Shortcuts Sheet (`SystemSheet`):
  - Real-time display brightness slider (0-100%).
  - Audio volume slider (0-100%), mute toggle, media controls (Prev, Play/Pause, Next).
  - Power action buttons (Sleep, Lock, Restart, Shutdown) with modal confirmations.
  - Dynamic shortcut buttons loaded from the connected PC's `shortcuts.json`.
- [x] Network & Multi-PC Management:
  - `UdpDiscovery`: LAN broadcast discovery on port 5051 with `WifiManager.MulticastLock`.
  - `WebSocketManager`: Low-latency WebSocket connection for mouse, keyboard, and binary JPEG frames.
  - `DeviceManagerDialog`: Lists discovered LAN PCs and allows adding remote PCs (Ngrok or custom IP).
  - `SharedPreferences` persistence for known devices and last connected PC.
- [x] OTA Auto-Updates (`UpdateManager`):
  - Checks `/api/version` on connected PC.
  - Downloads newer APKs and launches native `FileProvider` installer.
- [x] Internationalization (i18n):
  - Centralized strings in English by default (`res/values/strings.xml`).
  - Spanish translations in `res/values-es/strings.xml`.
  - Zero hardcoded UI strings.
- [x] Build Verification:
  - Successfully built `app-debug.apk` (`BUILD SUCCESSFUL in 27s`).
  - Staged APK in `pc-agent/updates/app-release.apk` for immediate OTA serving.
  - Auto-copied to `C:\Users\agust\Documents\Mobile Drop\RemotePocket.apk` (and `RemotePocket-v1.0.0.apk`).
- [x] PC Agent Live State:
  - Running on port 5050 (UDP discovery on port 5051). Tested and responding 200 OK via `/api/status`.

## How to Test & Run

### 1. Start PC Agent
```powershell
# In pc-agent directory:
.\start.bat
# Or run install.bat to register as Windows startup task
```

### 2. Run / Install Android App
- **Download via Express Gateway (Mobile Drop)**:
  - The APK is automatically dropped into `C:\Users\agust\Documents\Mobile Drop\RemotePocket.apk` (and `RemotePocket-v1.0.0.apk`) on every Gradle build!
  - You can immediately download and install it to your phone using your Express Gateway server.
- **Direct ADB Install**:
  ```powershell
  adb install -r "C:\Users\agust\Documents\Mobile Drop\RemotePocket.apk"
  ```
- The app will automatically discover the running PC agent on the LAN via UDP, connect, and allow mouse, keyboard, screen streaming, and system controls!
