# Remote Pocket

Control multiple Windows PCs from your Android device over the local network (LAN) with zero manual configuration (UDP auto-discovery) or remotely via Ngrok.

## Features
- **Zero-Touch LAN Discovery**: Automatically finds all active PCs on the same Wi-Fi using UDP broadcasts.
- **Multi-PC Support**: Seamlessly switch between work and personal PCs from the Android app bar.
- **Live Screen View (2 FPS)**: Low-bandwidth in-memory screen preview (~500ms) with direct touch and trackpad controls.
- **Dual Keyboard Mode**:
  - Native Android IME (voice typing, autocorrect, clipboard).
  - Extended PC Keyboard (F1-F12, sticky Ctrl/Alt/Shift/Win, arrows, shortcuts like Alt+Tab, Win+D, Task Manager).
- **Power & System Controls**: Sleep, Lock, Shutdown, Restart, Display brightness (WMI), volume and media controls.
- **Dynamic Shortcuts**: Configurable actions via `shortcuts.json` (e.g., launching `.bat` scripts).
- **OTA Updates**: One-tap app updates downloaded directly from the PC agent.

## Repository Structure
- `pc-agent/`: Lightweight Node.js + C# daemon for Windows with silent autostart.
- `android-app/`: Native Java Android application.
