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
  - Autonomous Agent Restarts: Whenever files change or when requested by Agustín, the server is cleanly restarted detached in his interactive desktop session via `explorer.exe run_silent.vbs` without holding background daemon tasks in Antigravity.
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
    - Complete physical QWERTY layout: Numbers (1-0, -, =, Bksp), Letters (QWERTY, ASDF, ZXCV), Spanish letter **`Ñ`**, Punctuation ([, ], ;, ', \, ,, ., /), Space, Enter, Tab.
    - Directional arrows: Up, Down, Left, Right.
    - Sticky modifiers: `Ctrl`, `Alt`, `Shift`, `Win` with visual state feedback and auto-release after chord dispatch.
    - Caps Lock: Syncs state with PC (`capslock`), highlights visual indicator, and dynamically toggles uppercase/lowercase on keycap labels (including `Ñ` / `ñ`).
    - **Key Hold & Auto-Repeat**:
      - Keys can be held down to repeat continuously (Backspace, Letters, Numbers, Space, Navigation arrows, F-keys, etc.).
      - 380ms initial hold delay before repeating, 65ms repeat interval.
      - Crisp initial haptic tap on finger down, subtle tick feedback on repeat pulses.
      - Clean cancellation when finger is released or slid outside button boundary.
      - Zero callback leaks (`repeatHandler.removeCallbacksAndMessages(null)` on dialog dismiss and stop).
    - Header quick `Esc` button for instantaneous one-touch escape/exit.
    - Collapsible "⚡ Special Actions & F-Keys ▾" panel with 1-click shortcuts (`Ctrl+Alt+Del`, `Alt+Tab`, `Win+D`, `TaskMgr`, `Win+L`, `Ctrl+C`, `Ctrl+V`), navigation (`Ins`, `Del`, `Home`, `End`, `PgUp`, `PgDn`, `PrtScn`), and `F1` to `F12`.
    - Native Win32 Unicode typing for `ñ` and `Ñ` in `InputHelper.cs` via `KEYEVENTF_UNICODE`, checking Shift and Windows OS CapsLock state (`GetKeyState(0x14)`).
- [x] Live Screen & Direct Touch Precision (`TouchpadView` & `InputHelper`):
  - **Classic Crisp Windows Mouse Pointer**:
    - Authentic Windows white arrow with clean 2px black outline, pointing precisely at `pointerX, pointerY`.
    - Zero visual clutter: removed halos, glowing rings, and golden circles as requested.
    - Subtle amber fill during active tap-and-drag for visual drag state confirmation.
    - Smooth sub-pixel positioning in both full-desktop and centered-zoom follow modes.
  - **1080p Full HD Streaming Quality**:
    - Screen streamer upgraded to native 1080p Full HD at 85% JPEG quality (`--stream 2 85 1080`).
    - Added native resolution bypass in `InputHelper.cs`: when `destW == srcW && destH == srcH`, skips downscaling and encodes the GDI bitmap directly to high-quality JPEG.
  - **Direct Touch Desktop Coordinate Mapping**:
    - `viewToDesktopCoords(touchX, touchY)` mathematically maps phone touch coordinates to exact desktop pixel coordinates taking into account aspect-ratio letterboxing, zoom scale, and viewport pan offsets.
    - Single-tap, double-tap, and 2-finger right-click in Live Screen mode dispatch `mouse_abs(x, y)` (`SetCursorPos(x, y)`) before clicking, ensuring clicks occur exactly where the user taps on the screen.
  - **Drift-Free Centered Cursor Tracking (AnyDesk / Google Remote Desktop Style)**:
    - In zoomed mode with `followCursor` enabled, sliding a finger moves the cursor via `sendMouseAbsolute` without Windows pointer acceleration drift, smoothly panning the viewport to keep the cursor centered.
    - Decoupled from PC mouse moves: moving the mouse on the PC updates cursor coordinates without jerking the phone's camera.
  - **Distinct System Controls Header Icon**:
    - Replaced duplicate refresh icon on `btnOpenSystemSheet` with Material sliders/tune vector (`@drawable/ic_tune`).
  - **Offloaded Frame Decoding & UI Smoothness**:
    - Binary JPEG frame decoding executed on a dedicated background `ExecutorService` rather than the main thread, keeping the UI silky smooth at all times.
  - **Manual Device Addition & URL Sanitization**:
    - Robust `sanitizeHostAndPort()` splits combined `host:port` strings and strips protocol prefixes.
    - Comprehensive `try-catch` guards around OkHttp `newWebSocket` creation and dialog dismissal prevent crashes.
  - Direct touch mouse movement across live screen preview with sub-pixel motion accumulation.
  - Multi-touch gestures directly on the screen:
    - 1-finger tap: Left click directly at CURRENT mouse position on PC (no coordinate jump/snap).
    - 2-finger tap: Right click directly at CURRENT mouse position on PC.
    - 3-finger tap: Middle click directly at CURRENT mouse position.
    - 2-finger vertical drag: Smooth mouse wheel scrolling.
    - Double-Tap & Hold to Drag (Tap-and-Half gesture):
      - Uses pending tap queue (`postDelayed(175ms)`) so single taps do not send immediate synthetic clicks that trigger Windows `WM_LBUTTONDBLCLK`.
      - On second tap within double-tap timeout, pending click is cancelled and pure `sendMouseDown("left")` is dispatched.
      - Hardware `mouse_event(MOUSEEVENTF_MOVE, 0, 0, 0, UIntPtr.Zero)` in `InputHelper.cs` notifies Windows message loops of cursor motion, enabling 100% reliable OLE/Win32 drag-and-drop for windows, files, and text.
      - Releases cleanly on finger lift (`sendMouseUp("left")`). Normal sliding never engages accidental drag.
- [x] System Controls & Multimedia Sheet (`SystemSheet.java` & `server.js`):
  - **Display Brightness Control**: Physical screen brightness adjustments via PowerShell CIM (`Invoke-CimMethod -Namespace root/wmi -ClassName WmiMonitorBrightnessMethods -MethodName WmiSetBrightness`). Real-time slider tracking via dual WebSocket (`set_brightness`) and REST (`POST /api/brightness`).
  - **System Volume & Mute**: Fixed native CoreAudioApi COM pipe in `InputHelper.cs` (safely handled console encodings to prevent `IOException: Invalid handle` crash). Real-time volume slider and mute button dispatched via dual WebSocket (`audio_volume`, `audio_mute`) and REST (`/api/audio/volume`, `/api/audio/mute`).
  - **Multimedia Keys**: Play/pause, Next track, Previous track wired with dual dispatch to `media_key` and `/api/audio/action`.
  - **Close Button Icon**: Replaced misplaced refresh icon with `@drawable/ic_close` in `dialog_system_sheet.xml`.
- [x] Resilient Background Lifecycle & Auto-Reconnect:
  - `MainActivity.java` `onResume()` immediately triggers `selectDevice(currentDevice)` (or saved preference), restoring connecting status and establishing WebSocket link.
  - `WebSocketManager.java` `disconnect()` evicts OkHttp connection pool (`client.connectionPool().evictAll()`), preventing dead aborted sockets from blocking fresh reconnects when returning from Android Home.
  - Persistent bottom mouse buttons: `L` (Left click), `Mid` (Middle click), `R` (Right click), `Hold` (Toggle drag lock), `2x` (Double click), and `▲`/`▼` (Mouse scroll).
- [x] Configurable Live Screen FPS & Stream Optimization:
  - Dynamic FPS selector pill in Live Screen toolbar: `⚡ 30 FPS` (default, emerald), `⚡ 60 FPS` (cyan), `⚡ 2 FPS` (amber), `⚡ 15 FPS` (indigo).
  - Persisted in `SharedPreferences` and communicated on-the-fly via WebSocket `set_screen_fps` to `InputHelper.exe` without restarting the process.
- [x] Robust Auto-Reconnect & Background Lifecycle Management:
  - Fixed `WebSocketManager.isConnected()` which previously retained non-null dead socket references following `SocketException` connection aborts.
  - Cleaned up dead socket references in `onClosed()` and `onFailure()`.
  - In `MainActivity.onResume()`: automatically forces reconnect (`connect(currentDevice, true)`) when returning from Android Home/background.
  - In `MainActivity.onPause()`: cleanly stops screen streaming to prevent socket buffer exhaustion and aborts in background.
  - Background disconnects trigger `scheduleAutoReconnect()` with 1.2s delay for seamless recovery without user intervention.
- [x] Silent Windows Server Execution:
  - Fixed `run_silent.vbs` to directly spawn `node.exe server.js` with window style 0 (`hidden`), completely eliminating visible CMD windows.
  - Added `start_console.bat` for visible console debugging when desired.
- [x] PC Agent Remote Management & System Sheet (`SystemSheet`):
  - "Restart PC Agent" button with confirmation prompt, triggering `/api/server/restart` for 3-second background reboot.
  - Real-time display brightness slider (0-100%).
  - Audio volume slider (0-100%), mute toggle, media controls (Prev, Play/Pause, Next).
  - Power action buttons (Sleep, Lock, Restart, Shutdown) with modal confirmations.
  - Dynamic shortcut buttons loaded from the connected PC's `shortcuts.json`.
- [x] Diagnostics & Live Logging Subsystem:
  - `AppLogger.java`: in-memory ring buffer capturing discovery bursts, packet receipt, WebSocket URLs, HTTP codes, and stack traces.
  - `LogViewerDialog.java`: dark terminal modal viewer with real-time log stream.
    - One-click "📋 Copy Logs" to clipboard.
    - One-click "☁️ Send to PC" to `POST /api/logs` on the PC agent.
    - Accessible via long-press on header status area or "📋 Logs" in `DeviceManagerDialog`.
  - PC Agent (`server.js`): `/api/logs` endpoints (POST saves client report to `pc-agent/logs/mobile_client.log`, GET serves log text for browser inspection).
- [x] Network & Multi-PC Management:
  - `UdpDiscovery`: multi-interface LAN broadcast discovery on port 5051 across all network interfaces + subnet broadcasts (`192.168.1.255` + `255.255.255.255`) with double burst dispatch.
  - `WebSocketManager`: Low-latency WebSocket connection for mouse, keyboard, and binary JPEG frames.
  - `DeviceManagerDialog`:
    - Shows live badges: `🟢 Live Discovered (UDP 5051)` vs `💾 Saved Device`.
    - Delete button (`✕`) for each device to purge stale/default cached entries.
    - "🔄 Force Scan" button: clears stale LAN cache and triggers a fresh network discovery.
    - "📋 Logs" button: opens the diagnostic log viewer directly.
  - `SharedPreferences` persistence for known devices and last connected PC.
- [x] OTA Auto-Updates (`UpdateManager`):
  - Checks `/api/version` on connected PC.
  - Downloads newer APKs and launches native `FileProvider` installer.
- [x] Internationalization (i18n):
  - Centralized strings in English by default (`res/values/strings.xml`).
  - Spanish translations in `res/values-es/strings.xml`.
  - Zero hardcoded UI strings.
- [x] UI & Interaction Improvements:
  - **Expanded Device Selector Hitbox**: Tapping anywhere on the device card, device name, connection status, status dot, or dropdown arrow immediately opens `DeviceManagerDialog`. Long-pressing opens diagnostic logs.
  - **Bottom-Docked PC Keyboard Layout**:
    - Fixed empty space issue where an overflowing header `TextView` was vertically wrapping into ~20 lines, pushing keys down.
    - Compact, proportional action bar with `[Standard]`, `[⚡ Shortcuts ▾]`, `[ESC]`, and `[✕]`.
    - Pinned to bottom of screen with rounded top corners (`bg_keyboard_dialog.xml`) and window gravity in `onStart()`.
  - **Connection Status & Discovery Flow**:
    - UDP scan no longer overwrites "Connected" status with "Searching PCs on LAN..." if already connected.
    - Selecting a device immediately updates UI to "Connecting..." (amber), forces clean reconnect, and updates to "Connected" (emerald green).
    - Entire device item card in `DeviceManagerDialog` is clickable to select and connect.
- [x] Build Verification:
  - Successfully built `app-debug.apk` (`BUILD SUCCESSFUL`).
  - Staged APK in `pc-agent/updates/app-release.apk` for immediate OTA serving.
  - Auto-copied to `C:\Users\agust\Documents\Mobile Drop\RemotePocket.apk` (and `RemotePocket-v1.0.0.apk`).
- [x] PC Agent Live State:
  - Active PID on port 5050 (TCP HTTP/WS) and 5051 (UDP discovery). Tested and verified `/api/status`, `/api/logs` (POST & GET), and WebSocket screen frame streaming (87KB payload).

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
