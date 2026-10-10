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
- [x] Setup & Execution Scripts (Express Gateway Architecture):
  - `run_silent.vbs`: Visual Basic Script launching `start.bat` in user interactive session with hidden window (`0, False`).
  - `remote_restart.vbs` & `remote_restart.bat`: Clean 3-second delay restart releasing ports and restarting daemon silently.
  - `restart_server.bat` & `stop_server.bat`: Quick manual stop/restart scripts cleanly terminating processes on port 5050 and `InputHelper.exe`.
  - Windows Startup Shortcut: Shortcut placed in `%APPDATA%\Microsoft\Windows\Start Menu\Programs\Startup\RemotePocket_silent.vbs.lnk` so the agent starts automatically upon user logon in full interactive session.
  - REST Restart Endpoint: `/api/server/restart`, `/api/agent/restart`, `/gateway/restart` and WebSocket `'restart_agent'` command.
  - `start.bat`: Self-healing launcher (auto-installs npm dependencies and auto-compiles C# if missing on new PCs).
  - `install.bat` & `uninstall.bat`: Setup wizards configuring/removing the Startup folder link and dependencies.
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
  - Bottom mouse action buttons (Left, Middle, Right, Hold drag lock, Double Click, Scroll Up, Scroll Down).
  - Floating Action Button (FAB) draggable anywhere on screen:
    - Default first tap: Opens normal soft keyboard (Gboard) input bar with auto-focus.
    - Subsequent tap: Toggles directly to Extended PC Keyboard (`PcKeyboardDialog`).
    - Next tap: Toggles back to normal keyboard.
    - Dedicated switch button ("💻") on the typing input bar to jump to PC keyboard directly.
    - Dedicated switch button ("⌨️ Standard") on PC keyboard header to jump to normal keyboard directly.
- [x] Dual Keyboards:
  - Standard Android Keyboard (Gboard): soft input with direct text transmission and voice-to-text.
  - Full Extended PC Keyboard (`PcKeyboardDialog`):
    - Complete physical QWERTY layout: Numbers (1-0, -, =, Bksp), Letters (QWERTY, ASDF, ZXCV), Punctuation ([, ], ;, ', \, ,, ., /), Space, Enter, Tab.
    - Directional arrows: Up, Down, Left, Right.
    - Sticky modifiers: `Ctrl`, `Alt`, `Shift`, `Win` with visual state feedback and auto-release after chord dispatch.
    - Caps Lock: Syncs state with PC (`capslock`), highlights visual indicator, and dynamically toggles uppercase/lowercase on keycap labels.
    - Header quick `Esc` button for instantaneous one-touch escape/exit.
    - Collapsible "⚡ Special Actions & F-Keys ▾" panel with 1-click shortcuts (`Ctrl+Alt+Del`, `Alt+Tab`, `Win+D`, `TaskMgr`, `Win+L`, `Ctrl+C`, `Ctrl+V`), navigation (`Ins`, `Del`, `Home`, `End`, `PgUp`, `PgDn`, `PrtScn`), and `F1` to `F12`.
    - Crisp tactile haptic feedback on every key, letter, number, modifier, and shortcut press.
- [x] Live Screen & Direct Touch Interactions (`TouchpadView`):
  - **Live Screen Viewport Camera Follow**:
    - The camera/viewport automatically tracks and centers on the mouse cursor in real-time whenever zoomed in (`currentScale > 1.0f`).
    - 0ms latency predictive tracking: when dragging the finger on the phone screen to steer the mouse, the estimated cursor position updates instantly and pans the camera translation synchronously without waiting for network frames.
    - Synchronized with authoritative cursor coordinates received from PC Agent frames.
    - Double-tap gesture: instantly toggles between 1.0x (full desktop overview) and 2.2x (close-up centered directly on the mouse cursor) with smooth deceleration interpolation.
  - **High-Visibility Cursor Beacon & Reticle**:
    - PC Agent (`InputHelper.cs`): renders a 52px diameter golden glowing beacon halo ring (`#FFD700`) with dark contrast borders and an enlarged 42x42 high-contrast cursor arrow directly onto the GDI+ bitmap frame.
    - Android Client (`TouchpadView.java`): draws a 60 FPS vector beacon reticle with crosshair ticks and bright center dot on top of the live stream canvas, guaranteeing the mouse pointer is always 100% visible against any wallpaper or UI.
  - **Live Screen Zoom & Follow Pill Overlay**:
    - Top-right glassmorphism pill displaying live follow state (`🎯 Follow: ON/OFF`) and zoom level (`🔍 2.0x`).
    - Tap follow button to toggle tracking on/off.
    - Tap zoom button to cycle zoom presets (1.0x -> 2.0x -> 3.0x -> 1.0x).
  - Pinch-to-zoom (1.0x to 4.0x) on live screen stream using `ScaleGestureDetector`.
  - Direct touch mouse movement across live screen preview with sub-pixel motion accumulation.
  - Multi-touch gestures directly on the screen:
    - 1-finger tap: Left click.
    - 2-finger tap: Right click.
    - 3-finger tap: Middle click.
    - 2-finger vertical drag: Smooth mouse wheel scrolling.
    - Long-press hold (>380ms): Engages left-click drag & drop with haptic pulse, automatically releasing on touch up.
  - Persistent bottom mouse buttons: `L` (Left click), `Mid` (Middle click), `R` (Right click), `Hold` (Toggle drag lock), `2x` (Double click), and `▲`/`▼` (Mouse scroll).
- [x] Auto-Reconnect & Connection Stability:
  - Automatic reconnection on `onResume()` restoring last active PC from `SharedPreferences`.
  - Guarded `WebSocketManager.connect()` with `isConnecting()` to prevent UDP discovery reconnect storms.
- [x] PC Agent Remote Management & System Sheet (`SystemSheet`):
  - "Restart PC Agent" button with confirmation prompt, triggering `/api/server/restart` for 3-second background reboot.
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
  - Successfully built `app-debug.apk` (`BUILD SUCCESSFUL`).
  - Staged APK in `pc-agent/updates/app-release.apk` for immediate OTA serving.
  - Auto-copied to `C:\Users\agust\Documents\Mobile Drop\RemotePocket.apk` (and `RemotePocket-v1.0.0.apk`).
- [x] PC Agent Live State:
  - Port 5050 (fallback search if busy), UDP discovery on 5051. Tested and responding 200 OK via `/api/status`.

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
