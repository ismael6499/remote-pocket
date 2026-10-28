const express = require('express');
const http = require('http');
const WebSocket = require('ws');
const dgram = require('dgram');
const os = require('os');
const path = require('path');
const fs = require('fs');
const { spawn, exec, execFile } = require('child_process');
require('dotenv').config();

process.on('uncaughtException', (err) => {
  try {
    fs.appendFileSync(path.join(__dirname, 'server_err.log'), `[${new Date().toISOString()}] Uncaught: ${err.stack || err}\n`);
  } catch (e) {}
});

process.on('unhandledRejection', (reason) => {
  try {
    fs.appendFileSync(path.join(__dirname, 'server_err.log'), `[${new Date().toISOString()}] Rejection: ${reason}\n`);
  } catch (e) {}
});

process.on('beforeExit', (code) => {
  try {
    fs.appendFileSync(path.join(__dirname, 'server_err.log'), `[${new Date().toISOString()}] beforeExit code=${code}\n`);
  } catch (e) {}
});

process.on('exit', (code) => {
  try {
    fs.appendFileSync(path.join(__dirname, 'server_err.log'), `[${new Date().toISOString()}] exit code=${code}\n`);
  } catch (e) {}
});

// ==========================================
// CONFIGURATION & LOGGING
// ==========================================
const LOGS_DIR = path.join(__dirname, 'logs');
if (!fs.existsSync(LOGS_DIR)) {
  try { fs.mkdirSync(LOGS_DIR, { recursive: true }); } catch (e) {}
}
const AGENT_LOG_PATH = path.join(LOGS_DIR, 'agent.log');

function logAgent(level, message) {
  const ts = new Date().toISOString();
  const line = `[${ts}] [${level}] ${message}\n`;
  try {
    fs.appendFileSync(AGENT_LOG_PATH, line, 'utf-8');
  } catch (err) {
    console.error('[Logger] Failed to write to agent.log:', err.message);
  }
  if (level === 'ERROR') {
    console.error(`[${level}] ${message}`);
  } else if (level === 'WARN') {
    console.warn(`[${level}] ${message}`);
  } else {
    console.log(`[${level}] ${message}`);
  }
}
const CONFIG_PATH = path.join(__dirname, 'config.json');
const SHORTCUTS_PATH = path.join(__dirname, 'shortcuts.json');
const UPDATES_DIR = path.join(__dirname, 'updates');
const INPUT_HELPER_PATH = path.join(__dirname, 'InputHelper.exe');

let config = {
  deviceName: 'PC Personal',
  port: 5050,
  udpPort: 5051,
  pin: ''
};

try {
  if (fs.existsSync(CONFIG_PATH)) {
    const raw = fs.readFileSync(CONFIG_PATH, 'utf-8');
    config = { ...config, ...JSON.parse(raw) };
  }
} catch (err) {
  console.warn('[Config] Failed to load config.json, using defaults:', err.message);
}

if (config.screenOffApp === undefined) {
  const pushMonitorOffExists = fs.existsSync('C:\\Program Files\\PushMonitorOff\\PushMonitOff.exe');
  config.screenOffApp = pushMonitorOffExists;
  try {
    fs.writeFileSync(CONFIG_PATH, JSON.stringify(config, null, 2), 'utf-8');
    console.log(`[Config] One-time detected screenOffApp: ${config.screenOffApp} and saved to config.json`);
  } catch (err) {
    console.warn('[Config] Failed to persist detected screenOffApp:', err.message);
  }
}

if (!fs.existsSync(UPDATES_DIR)) {
  fs.mkdirSync(UPDATES_DIR, { recursive: true });
}

// ==========================================
// PERSISTENT INPUT HELPER PROCESS (--pipe)
// ==========================================
let inputProcess = null;
let inputProcessReady = false;

let restartingInputProcess = false;

function startInputProcess() {
  if (restartingInputProcess) return;
  if (!fs.existsSync(INPUT_HELPER_PATH)) {
    console.error(`[InputHelper] Binary not found at: ${INPUT_HELPER_PATH}. Run install.bat to compile.`);
    return;
  }

  try {
    if (inputProcess) {
      try { inputProcess.kill(); } catch (e) {}
      inputProcess = null;
    }
    inputProcessReady = false;

    inputProcess = spawn(INPUT_HELPER_PATH, ['--pipe'], {
      stdio: ['pipe', 'pipe', 'pipe'],
      windowsHide: true
    });

    if (inputProcess.stderr) {
      inputProcess.stderr.on('data', (data) => {
        const str = data.toString().trim();
        if (str) {
          logAgent('ERROR', `InputHelper: ${str}`);
        }
      });
    }

    if (inputProcess.stdin) {
      inputProcess.stdin.on('error', (err) => {
        console.warn('[InputHelper] stdin stream error (prevented crash):', err.message);
      });
    }

    inputProcess.stdout.on('data', (data) => {
      const str = data.toString().trim();
      if (str.includes('READY')) {
        inputProcessReady = true;
        console.log('[InputHelper] Native pipe process ready.');
      } else if (str.includes('ERR')) {
        console.warn('[InputHelper] Native pipe error:', str);
      }
    });

    inputProcess.on('exit', (code, signal) => {
      logAgent('WARN', `InputHelper pipe process exited (code=${code}, signal=${signal}). Restarting in 250ms...`);
      inputProcessReady = false;
      inputProcess = null;
      if (!restartingInputProcess) {
        restartingInputProcess = true;
        setTimeout(() => {
          restartingInputProcess = false;
          startInputProcess();
        }, 250);
      }
    });

    inputProcess.on('error', (err) => {
      console.error('[InputHelper] Pipe process error:', err.message);
    });
  } catch (err) {
    console.error('[InputHelper] Failed to spawn InputHelper:', err.message);
  }
}

function sendInputCommand(cmd) {
  if (!inputProcess || !inputProcess.stdin || inputProcess.stdin.destroyed) {
    startInputProcess();
  }
  if (inputProcess && inputProcess.stdin && !inputProcess.stdin.destroyed) {
    try {
      inputProcess.stdin.write(cmd + '\n', (err) => {
        if (err) console.warn('[InputHelper] write error callback:', err.message);
      });
      return true;
    } catch (e) {
      console.error('[InputHelper] Write error:', e.message);
    }
  }
  return false;
}

startInputProcess();

// ==========================================
// SCREEN STREAMING & MULTI-MONITOR MANAGER
// ==========================================
let screenStreamProcess = null;
const screenSubscribers = new Set();

let currentScreenFps = 30;
let currentMonitorIndex = 0;
let cachedMonitorsInfo = { count: 1, active: 0, monitors: [{ index: 0, name: 'Display 1', w: 1920, h: 1080, primary: true }] };

let cachedKeyboardLayout = 'ESP';
let cachedKeyboardCulture = 'es-ES';
let cachedKeyboardName = 'Spanish';

function updateKeyboardLayout() {
  execFile(INPUT_HELPER_PATH, ['layout'], { timeout: 2000 }, (err, stdout) => {
    if (!err && stdout) {
      const match = stdout.match(/LAYOUT:([^,\r\n]+),([^\r\n]+)/);
      if (match) {
        cachedKeyboardCulture = match[1].trim();
        cachedKeyboardName = match[2].trim();
        if (cachedKeyboardCulture.toLowerCase().startsWith('es') || cachedKeyboardName.toLowerCase().includes('spanish')) {
          cachedKeyboardLayout = 'ESP';
        } else {
          cachedKeyboardLayout = 'ENG';
        }
        console.log(`[Keyboard] Detected layout: ${cachedKeyboardLayout} (${cachedKeyboardCulture} - ${cachedKeyboardName})`);
      }
    }
  });
}
updateKeyboardLayout();

function fetchMonitorsInfo(cb) {
  if (!fs.existsSync(INPUT_HELPER_PATH)) {
    if (cb) cb(cachedMonitorsInfo);
    return;
  }
  execFile(INPUT_HELPER_PATH, ['--monitors'], (err, stdout) => {
    if (!err && stdout) {
      try {
        const parsed = JSON.parse(stdout.trim());
        if (parsed && typeof parsed.count === 'number') {
          cachedMonitorsInfo = parsed;
          cachedMonitorsInfo.active = currentMonitorIndex;
          cachedMonitorsInfo.isContinuous = (currentMonitorIndex === -1);
        }
      } catch (e) {}
    }
    if (cb) cb(cachedMonitorsInfo);
  });
}

function broadcastMonitors() {
  fetchMonitorsInfo((info) => {
    const payload = JSON.stringify({ type: 'monitors_info', ...info, active: currentMonitorIndex });
    for (const client of wss.clients) {
      if (client.readyState === WebSocket.OPEN) {
        client.send(payload);
      }
    }
  });
}

function qualityForFps(fps) {
  if (fps >= 60) return '70';
  if (fps >= 30) return '75';
  if (fps >= 15) return '80';
  return '85';
}

function startScreenStreamer(fps = currentScreenFps) {
  if (screenStreamProcess) return;
  if (!fs.existsSync(INPUT_HELPER_PATH)) return;

  const targetFps = Math.max(1, Math.min(60, fps));
  const q = qualityForFps(targetFps);
  console.log(`[ScreenStream] Starting on-demand screen stream (Monitor ${currentMonitorIndex}, ${targetFps} FPS, ${q}% quality, 1080p Full HD)...`);
  try {
    screenStreamProcess = spawn(INPUT_HELPER_PATH, ['--stream', String(targetFps), q, '1080', String(currentMonitorIndex)], {
      stdio: ['pipe', 'pipe', 'ignore']
    });

    let buffer = '';
    screenStreamProcess.stdout.on('data', (chunk) => {
      buffer += chunk.toString();
      const lines = buffer.split('\n');
      buffer = lines.pop(); // keep remainder

      for (let line of lines) {
        line = line.trim();
        if (line.startsWith('FRAME:')) {
          const rest = line.substring(6);
          const colonIdx = rest.indexOf(':');
          let metaHeader = null;
          let b64 = rest;
          if (colonIdx !== -1) {
            const coordsStr = rest.substring(0, colonIdx);
            b64 = rest.substring(colonIdx + 1);
            const coords = coordsStr.split(',');
            if (coords.length === 4) {
              const rawCx = parseInt(coords[0], 10);
              const rawCy = parseInt(coords[1], 10);
              const sw = parseInt(coords[2], 10) || 0;
              const sh = parseInt(coords[3], 10) || 0;
              const cx = (isNaN(rawCx) || rawCx < 0) ? Math.round(sw / 2) : rawCx;
              const cy = (isNaN(rawCy) || rawCy < 0) ? Math.round(sh / 2) : rawCy;
              metaHeader = Buffer.alloc(12);
              metaHeader.write('RPKT', 0, 4, 'ascii');
              metaHeader.writeUInt16LE(Math.max(0, Math.min(65535, cx)), 4);
              metaHeader.writeUInt16LE(Math.max(0, Math.min(65535, cy)), 6);
              metaHeader.writeUInt16LE(Math.max(0, Math.min(65535, sw)), 8);
              metaHeader.writeUInt16LE(Math.max(0, Math.min(65535, sh)), 10);
            }
          }
          try {
            const jpegBuffer = Buffer.from(b64, 'base64');
            const payload = metaHeader ? Buffer.concat([metaHeader, jpegBuffer]) : jpegBuffer;
            // Broadcast binary payload to all subscribed WebSocket clients
            for (const client of screenSubscribers) {
              if (client.readyState === WebSocket.OPEN) {
                client.send(payload, { binary: true });
              }
            }
          } catch (e) {
            console.error('[ScreenStream] Frame decode error:', e.message);
          }
        }
      }
    });

    screenStreamProcess.on('exit', () => {
      console.log('[ScreenStream] Screen streamer stopped.');
      screenStreamProcess = null;
    });

    screenStreamProcess.on('error', (err) => {
      console.error('[ScreenStream] Stream process error:', err.message);
      screenStreamProcess = null;
    });
  } catch (err) {
    console.error('[ScreenStream] Failed to start stream process:', err.message);
    screenStreamProcess = null;
  }
}

function stopScreenStreamer() {
  if (screenStreamProcess) {
    console.log('[ScreenStream] No subscribers active. Stopping streamer (0% CPU mode)...');
    try {
      if (screenStreamProcess.stdin && !screenStreamProcess.stdin.destroyed) {
        screenStreamProcess.stdin.write('stop\n');
      }
      setTimeout(() => {
        if (screenStreamProcess) {
          try { screenStreamProcess.kill(); } catch (e) {}
          screenStreamProcess = null;
        }
      }, 300);
    } catch (e) {
      screenStreamProcess = null;
    }
  }
}

function subscribeScreen(ws) {
  screenSubscribers.add(ws);
  if (screenSubscribers.size === 1) {
    startScreenStreamer();
  }
}

function unsubscribeScreen(ws) {
  screenSubscribers.delete(ws);
  if (screenSubscribers.size === 0) {
    stopScreenStreamer();
  }
}

// ==========================================
// HELPER FUNCTIONS (Audio, Brightness, Shortcuts)
// ==========================================
let cachedAudio = { volume: 50, muted: false, playing: false };
let cachedBrightness = 100;

function getAudioStatus() {
  return new Promise((resolve) => {
    exec(`"${INPUT_HELPER_PATH}" volume`, { timeout: 2000 }, (err, stdout) => {
      if (err) {
        return resolve(cachedAudio);
      }
      const match = stdout.match(/AUDIO:(.+)/);
      if (match) {
        try {
          cachedAudio = JSON.parse(match[1]);
          return resolve(cachedAudio);
        } catch (e) {}
      }
      resolve(cachedAudio);
    });
  });
}

function setAudioVolume(level) {
  const safeVol = Math.max(0, Math.min(100, parseInt(level, 10) || 0));
  cachedAudio.volume = safeVol;
  sendInputCommand(`vol ${safeVol}`);
}

function toggleAudioMute(muted) {
  if (typeof muted === 'boolean') {
    cachedAudio.muted = muted;
    sendInputCommand(`mute ${muted}`);
  } else {
    cachedAudio.muted = !cachedAudio.muted;
    sendInputCommand('mute');
  }
}

function getBrightness() {
  return new Promise((resolve) => {
    const cmd = `powershell -NoProfile -Command "try { (Get-CimInstance -Namespace root/wmi -ClassName WmiMonitorBrightness -ErrorAction Stop).CurrentBrightness } catch { 100 }"`;
    exec(cmd, { timeout: 3000 }, (err, stdout) => {
      if (err) return resolve(cachedBrightness);
      const val = parseInt(stdout.trim(), 10);
      cachedBrightness = isNaN(val) ? 100 : val;
      resolve(cachedBrightness);
    });
  });
}

let brightnessBusy = false;
let brightnessPending = null;
let monitorianPath = null;

function detectMonitorian() {
  const userProf = process.env.USERPROFILE || 'C:\\Users\\' + (process.env.USERNAME || 'agust');
  const localApp = process.env.LOCALAPPDATA || path.join(userProf, 'AppData', 'Local');
  const progFiles = process.env.ProgramFiles || 'C:\\Program Files';

  const candidates = [
    path.join(localApp, 'Microsoft', 'WindowsApps', 'Monitorian.exe'),
    path.join(localApp, 'Monitorian', 'Monitorian.exe'),
    path.join(localApp, 'Programs', 'Monitorian', 'Monitorian.exe'),
    path.join(progFiles, 'Monitorian', 'Monitorian.exe'),
    path.join(userProf, 'Tools', 'launcher', 'Monitorian.exe'),
    path.join(userProf, 'Tools', 'Monitorian.exe')
  ];
  for (const c of candidates) {
    if (c && fs.existsSync(c)) {
      monitorianPath = c;
      console.log(`[Brightness] Detected Monitorian CLI at: ${c}`);
      return;
    }
  }
}
detectMonitorian();

function setBrightness(level, monitorIndex = currentMonitorIndex) {
  const safeLevel = Math.max(0, Math.min(100, parseInt(level, 10) || 0));
  cachedBrightness = safeLevel;
  const targetMon = (monitorIndex !== undefined && monitorIndex !== null) ? monitorIndex : currentMonitorIndex;

  // 1. Send native DDC/CI & Gamma command to persistent InputHelper (0ms latency)
  sendInputCommand(`brightness ${safeLevel} ${targetMon}`);

  // 2. If Monitorian CLI is available, trigger it
  if (monitorianPath) {
    try {
      const monArg = targetMon >= 0 ? String(targetMon + 1) : 'all';
      execFile(monitorianPath, ['/set', monArg, String(safeLevel)], { windowsHide: true }, (err) => {
        if (err) console.warn('[Brightness] Monitorian CLI warning:', err.message);
      });
    } catch (e) {}
  }

  // 3. If primary/internal display or fallback, invoke WMI
  if (targetMon === 0 || targetMon < 0) {
    if (brightnessBusy) {
      brightnessPending = safeLevel;
      return Promise.resolve(safeLevel);
    }
    brightnessBusy = true;
    return new Promise((resolve) => {
      const cmd = `powershell -NoProfile -NonInteractive -Command "try { Invoke-CimMethod -InputObject (Get-CimInstance -Namespace root/wmi -ClassName WmiMonitorBrightnessMethods -ErrorAction Stop) -MethodName WmiSetBrightness -Arguments @{ Timeout = 1; Brightness = ${safeLevel} } -ErrorAction Stop } catch { }"`;
      exec(cmd, { timeout: 4000, windowsHide: true }, () => {
        brightnessBusy = false;
        resolve(safeLevel);
        if (brightnessPending !== null) {
          const next = brightnessPending;
          brightnessPending = null;
          setBrightness(next, targetMon);
        }
      });
    });
  }

  return Promise.resolve(safeLevel);
}

function getClipboardText() {
  return new Promise((resolve) => {
    exec(`"${INPUT_HELPER_PATH}" clip`, { timeout: 2000 }, (err, stdout) => {
      if (err) return resolve('');
      const match = stdout.match(/CLIP:(.+)/);
      if (match) {
        try {
          const txt = Buffer.from(match[1].trim(), 'base64').toString('utf-8');
          return resolve(txt);
        } catch (e) {}
      }
      resolve('');
    });
  });
}

function setClipboardText(text) {
  const b64 = Buffer.from(text || '', 'utf-8').toString('base64');
  sendInputCommand(`setclip ${b64}`);
}

function expandEnv(str) {
  if (!str || typeof str !== 'string') return str;
  return str.replace(/%([^%]+)%/g, (_, n) => process.env[n] || '');
}

function loadShortcuts() {
  try {
    if (fs.existsSync(SHORTCUTS_PATH)) {
      const items = JSON.parse(fs.readFileSync(SHORTCUTS_PATH, 'utf-8'));
      if (Array.isArray(items)) {
        return items.filter((item) => {
          const check = item.checkPath || (item.checkExists ? item.path : null);
          if (check) {
            const exp = expandEnv(check);
            return fs.existsSync(check) || fs.existsSync(exp);
          }
          return true;
        });
      }
    }
  } catch (err) {
    console.error('[Shortcuts] Load error:', err.message);
  }
  return [];
}

// ==========================================
// EXPRESS & REST API
// ==========================================
const app = express();
app.use(express.json());
app.use(express.urlencoded({ extended: true }));

// Pin verification middleware (if pin configured)
function verifyPin(req, res, next) {
  if (!config.pin || config.pin.trim() === '') return next();
  const reqPin = req.headers['x-pin'] || req.query.pin || req.body?.pin;
  if (reqPin === config.pin) return next();
  const clientIp = req.ip || req.socket?.remoteAddress || 'unknown';
  logAgent('WARN', `Unauthorized HTTP ${req.method} ${req.path} from ${clientIp} - invalid or missing PIN`);
  return res.status(401).json({ error: 'Unauthorized: Invalid PIN' });
}

// Static serve for OTA updates
app.use('/updates', express.static(UPDATES_DIR));

// Status & Info
app.get('/api/status', verifyPin, async (req, res) => {
  const audio = await getAudioStatus();
  const brightness = await getBrightness();
  res.json({
    deviceName: config.deviceName,
    hostname: os.hostname(),
    platform: os.platform(),
    uptime: Math.round(os.uptime()),
    totalMemory: Math.round(os.totalmem() / 1024 / 1024),
    freeMemory: Math.round(os.freemem() / 1024 / 1024),
    audio,
    brightness,
    screenOffApp: Boolean(config.screenOffApp),
    keyboardLayout: cachedKeyboardLayout,
    keyboardCulture: cachedKeyboardCulture,
    keyboardName: cachedKeyboardName,
    version: '1.0.6'
  });
});

// Single screen capture frame (HTTP)
app.get('/api/screen/frame', verifyPin, (req, res) => {
  exec(`"${INPUT_HELPER_PATH}" --capture 65 720`, { timeout: 3000, maxBuffer: 10 * 1024 * 1024 }, (err, stdout) => {
    if (err) return res.status(500).json({ error: 'Failed to capture frame' });
    const match = stdout.match(/FRAME:(.+)/);
    if (!match) return res.status(500).json({ error: 'Invalid frame output' });
    const raw = match[1].trim();
    const colonIdx = raw.indexOf(':');
    let b64 = raw;
    if (colonIdx !== -1) {
      const coords = raw.substring(0, colonIdx).split(',');
      if (coords.length === 4) {
        res.setHeader('X-Cursor-X', coords[0]);
        res.setHeader('X-Cursor-Y', coords[1]);
        res.setHeader('X-Screen-W', coords[2]);
        res.setHeader('X-Screen-H', coords[3]);
      }
      b64 = raw.substring(colonIdx + 1);
    }
    const buffer = Buffer.from(b64, 'base64');
    res.contentType('image/jpeg');
    res.send(buffer);
  });
});

// Audio API
app.get('/api/audio', verifyPin, async (req, res) => {
  const audio = await getAudioStatus();
  res.json(audio);
});

app.post('/api/audio/volume', verifyPin, (req, res) => {
  const { volume } = req.body;
  if (volume !== undefined) {
    setAudioVolume(volume);
    return res.json({ success: true, volume });
  }
  res.status(400).json({ error: 'Missing volume parameter' });
});

app.post('/api/audio/mute', verifyPin, (req, res) => {
  const { muted } = req.body;
  toggleAudioMute(muted);
  res.json({ success: true });
});

app.post('/api/audio/action', verifyPin, (req, res) => {
  const { action } = req.body;
  switch (action) {
    case 'up':
      sendInputCommand('k volume_up');
      break;
    case 'down':
      sendInputCommand('k volume_down');
      break;
    case 'mute':
      sendInputCommand('k volume_mute');
      break;
    case 'play_pause':
    case 'media_play_pause':
      sendInputCommand('k media_play_pause');
      break;
    case 'next':
    case 'media_next':
      sendInputCommand('k media_next');
      break;
    case 'prev':
    case 'media_prev':
      sendInputCommand('k media_prev');
      break;
    default:
      return res.status(400).json({ error: 'Invalid audio action' });
  }
  res.json({ success: true, action });
});

// Brightness API
app.get('/api/brightness', verifyPin, async (req, res) => {
  const brightness = await getBrightness();
  res.json({ brightness });
});

app.post('/api/brightness', verifyPin, async (req, res) => {
  const { brightness, monitor } = req.body;
  if (brightness !== undefined) {
    const mon = monitor !== undefined ? monitor : currentMonitorIndex;
    const val = await setBrightness(brightness, mon);
    return res.json({ success: true, brightness: val, monitor: mon });
  }
  res.status(400).json({ error: 'Missing brightness parameter' });
});

// Clipboard API
app.get('/api/clipboard', verifyPin, async (req, res) => {
  const text = await getClipboardText();
  res.json({ text });
});

app.post('/api/clipboard', verifyPin, (req, res) => {
  const { text } = req.body;
  setClipboardText(text || '');
  res.json({ success: true });
});

// Rename Device API (persists to config.json)
app.post('/api/device/name', verifyPin, (req, res) => {
  const { name } = req.body || {};
  if (!name || typeof name !== 'string' || !name.trim()) {
    return res.status(400).json({ success: false, error: 'Invalid name' });
  }
  config.deviceName = name.trim();
  try {
    fs.writeFileSync(CONFIG_PATH, JSON.stringify(config, null, 2), 'utf-8');
    console.log(`[Config] Device renamed to: "${config.deviceName}" via REST`);
    res.json({ success: true, deviceName: config.deviceName });
  } catch (err) {
    console.error('[Config] Failed to save config:', err);
    res.status(500).json({ success: false, error: err.message });
  }
});

// Mobile Diagnostics & Logs API

app.post('/api/logs', (req, res) => {
  const { logs, deviceName, clientInfo } = req.body || {};
  const ip = req.ip || req.connection.remoteAddress;
  const timestamp = new Date().toISOString();
  console.log(`[MobileLogs] Received logs from ${deviceName || ip} (${timestamp})`);

  const logEntry = `\n======================================================\n` +
                   `[LOG ENTRY] Timestamp: ${timestamp} | IP: ${ip} | Device: ${deviceName || 'Unknown'}\n` +
                   `Client Info: ${JSON.stringify(clientInfo || {})}\n` +
                   `------------------------------------------------------\n` +
                   `${logs || 'No log content'}\n` +
                   `======================================================\n`;

  const logFilePath = path.join(LOGS_DIR, 'mobile_client.log');
  fs.appendFile(logFilePath, logEntry, 'utf8', (err) => {
    if (err) {
      console.error('[MobileLogs] Error saving log file:', err.message);
      return res.status(500).json({ error: 'Failed to write logs to disk' });
    }
    res.json({ success: true, message: 'Logs saved successfully' });
  });
});

app.get('/api/logs', verifyPin, (req, res) => {
  const logFilePath = path.join(LOGS_DIR, 'mobile_client.log');
  if (fs.existsSync(logFilePath)) {
    res.contentType('text/plain');
    res.sendFile(logFilePath);
  } else {
    res.status(404).send('No mobile logs recorded yet.');
  }
});

// Shortcuts API
app.get('/api/shortcuts', verifyPin, (req, res) => {
  res.json(loadShortcuts());
});

app.post('/api/shortcuts/run', verifyPin, (req, res) => {
  const { id } = req.body;
  const shortcuts = loadShortcuts();
  const target = shortcuts.find((s) => s.id === id);
  if (!target) {
    return res.status(404).json({ error: 'Shortcut not found' });
  }

  let cmd = expandEnv(target.command);
  let targetCwd = target.cwd ? expandEnv(target.cwd) : null;
  if (!targetCwd && target.checkPath) {
    try {
      const expCheck = expandEnv(target.checkPath);
      targetCwd = fs.existsSync(expCheck) ? path.dirname(expCheck) : (fs.existsSync(target.checkPath) ? path.dirname(target.checkPath) : null);
    } catch (e) {}
  }

  logAgent('INFO', `Executing shortcut: ${target.name} (id: ${target.id})`);
  exec(cmd, { cwd: targetCwd || undefined, windowsHide: true }, (err) => {
    if (err) logAgent('ERROR', `Error running shortcut ${target.name}: ${err.message}`);
  });
  res.json({ success: true, message: `Executed shortcut: ${target.name}` });
});

// System Power Controls
app.post('/api/system/lock', verifyPin, (req, res) => {
  console.log('[System] Lock workstation requested');
  exec('rundll32.exe user32.dll,LockWorkStation');
  res.json({ success: true, action: 'lock' });
});

app.post('/api/system/sleep', verifyPin, (req, res) => {
  console.log('[System] Sleep requested');
  res.json({ success: true, action: 'sleep' });
  setTimeout(() => {
    exec('rundll32.exe powrprof.dll,SetSuspendState 0,1,0');
  }, 1000);
});

app.post('/api/system/shutdown', verifyPin, (req, res) => {
  console.log('[System] Shutdown requested');
  res.json({ success: true, action: 'shutdown' });
  setTimeout(() => {
    exec('shutdown /s /t 0');
  }, 1000);
});

app.post('/api/system/restart', verifyPin, (req, res) => {
  console.log('[System] Restart requested');
  res.json({ success: true, action: 'restart' });
  setTimeout(() => {
    exec('shutdown /r /t 0');
  }, 1000);
});

function executeScreenOff() {
  console.log('[System] Screen off sequence (Win -> Win -> Alt+X) requested');
  sendInputCommand('screenoff');
}

app.post('/api/system/screen_off', verifyPin, (req, res) => {
  executeScreenOff();
  res.json({ success: true, action: 'screen_off' });
});

// Multi-Monitor API
app.get('/api/monitors', (req, res) => {
  fetchMonitorsInfo((info) => {
    res.json({ success: true, ...info, active: currentMonitorIndex });
  });
});

app.post('/api/monitors/select', (req, res) => {
  const index = parseInt(req.body.index, 10);
  if (!isNaN(index)) {
    const targetIndex = Math.max(-1, index);
    currentMonitorIndex = targetIndex;
    console.log(`[Monitor] Switched to monitor index ${targetIndex} via REST`);
    sendInputCommand(`monitor ${targetIndex}`);
    if (screenStreamProcess && screenStreamProcess.stdin && !screenStreamProcess.stdin.destroyed) {
      try {
        screenStreamProcess.stdin.write(`monitor ${targetIndex}\n`);
      } catch (e) {}
    }
    broadcastMonitors();
    return res.json({ success: true, active: currentMonitorIndex });
  }
  res.status(400).json({ success: false, error: 'Invalid monitor index' });
});

// Agent Self-Restart Endpoint (Express Gateway style via remote_restart.vbs)
app.post(['/api/server/restart', '/api/agent/restart', '/gateway/restart'], verifyPin, (req, res) => {
  console.log('[Server] Received agent restart request (/api/server/restart)...');
  res.json({ success: true, message: 'Restarting Remote Pocket agent in background...' });

  const vbsPath = path.join(__dirname, 'remote_restart.vbs');
  const child = spawn('wscript.exe', [vbsPath], {
    detached: true,
    stdio: 'ignore'
  });
  child.unref();

  setTimeout(() => {
    cleanup();
    process.exit(0);
  }, 1000);
});

// OTA Updates Endpoint
app.get('/api/version', (req, res) => {
  const apkPath = path.join(UPDATES_DIR, 'app-release.apk');
  const exists = fs.existsSync(apkPath);
  res.json({
    versionCode: 3,
    versionName: '1.0.2',
    hasUpdateFile: exists,
    apkUrl: exists ? '/updates/app-release.apk' : null
  });
});

// ==========================================
// HTTP SERVER & WEBSOCKET SERVER
// ==========================================
const server = http.createServer(app);
const wss = new WebSocket.Server({ server });

wss.on('connection', (ws, req) => {
  const clientIp = req.socket?.remoteAddress || 'unknown';

  let reqPin = null;
  try {
    const parsedUrl = new URL(req.url, 'http://localhost');
    reqPin = parsedUrl.searchParams.get('pin');
  } catch (e) {}
  if (!reqPin && req.headers) {
    reqPin = req.headers['x-pin'];
  }

  if (config.pin && config.pin.trim() !== '') {
    if (reqPin !== config.pin) {
      logAgent('WARN', `Rejected WebSocket connection from ${clientIp} - invalid or missing PIN`);
      try {
        ws.close(4401, 'Unauthorized');
      } catch (e) {
        try { ws.terminate(); } catch (ignored) {}
      }
      return;
    }
  }

  logAgent('INFO', `WebSocket client connected from ${clientIp}`);

  ws.isSubscribedToScreen = false;

  if (ws.readyState === WebSocket.OPEN) {
    ws.send(JSON.stringify({
      type: 'device_info',
      deviceName: config.deviceName,
      keyboardLayout: cachedKeyboardLayout,
      keyboardCulture: cachedKeyboardCulture,
      keyboardName: cachedKeyboardName,
      brightness: cachedBrightness,
      audio: cachedAudio,
      screenOffApp: Boolean(config.screenOffApp)
    }));
  }

  fetchMonitorsInfo((info) => {
    if (ws.readyState === WebSocket.OPEN) {
      ws.send(JSON.stringify({ type: 'monitors_info', ...info, active: currentMonitorIndex }));
    }
  });

  ws.on('message', (message, isBinary) => {
    if (isBinary) return; // ignore incoming binary frames
    try {
      const data = JSON.parse(message.toString());
      if (data.type !== 'mouse_move' && data.type !== 'mouse_abs' && data.type !== 'mouse_move_abs') {
        console.log(`[WS Input] Action: ${data.type} ${JSON.stringify(data)}`);
      }
      handleWebSocketMessage(ws, data);
    } catch (err) {
      console.error('[WebSocket] Bad message JSON:', err.message);
    }
  });

  ws.on('close', (code) => {
    logAgent('INFO', `WebSocket client disconnected from ${clientIp} (code=${code})`);
    if (ws.isSubscribedToScreen) {
      unsubscribeScreen(ws);
    }
  });

  ws.on('error', (err) => {
    logAgent('ERROR', `WebSocket error from ${clientIp}: ${err.message}`);
    if (ws.isSubscribedToScreen) {
      unsubscribeScreen(ws);
    }
  });
});

function handleWebSocketMessage(ws, data) {
  switch (data.type) {
    // Mouse
    case 'mouse_move':
      if (typeof data.dx === 'number' && typeof data.dy === 'number') {
        sendInputCommand(`m ${Math.round(data.dx)} ${Math.round(data.dy)}`);
      }
      break;
    case 'mouse_abs':
    case 'mouse_move_abs':
      if (typeof data.x === 'number' && typeof data.y === 'number') {
        sendInputCommand(`ma ${Math.round(data.x)} ${Math.round(data.y)}`);
      }
      break;
    case 'mouse_click':
      sendInputCommand(`c ${data.button || 'left'}`);
      break;
    case 'mouse_double_click':
      sendInputCommand(`dc ${data.button || 'left'}`);
      break;
    case 'mouse_down':
      sendInputCommand(`d ${data.button || 'left'}`);
      break;
    case 'mouse_up':
      sendInputCommand(`u ${data.button || 'left'}`);
      break;
    case 'mouse_scroll':
      if (typeof data.delta === 'number') {
        sendInputCommand(`s ${Math.round(data.delta)}`);
      }
      break;

    // Clipboard
    case 'get_clipboard':
      getClipboardText().then((clipText) => {
        if (ws.readyState === WebSocket.OPEN) {
          ws.send(JSON.stringify({ type: 'clipboard_content', text: clipText }));
        }
      });
      break;
    case 'set_clipboard':
      if (typeof data.text === 'string') {
        setClipboardText(data.text);
      }
      break;

    // Keyboard
    case 'key_press':
      if (data.key) {
        sendInputCommand(`k ${data.key}`);
      }
      break;
    case 'type_text':
      if (data.text) {
        if (data.text.length > 25 || data.text.includes('\n') || data.text.includes('\r')) {
          const b64 = Buffer.from(data.text, 'utf-8').toString('base64');
          sendInputCommand(`p ${b64}`);
        } else {
          const clean = data.text.replace(/[\r\n]+/g, ' ');
          sendInputCommand(`t ${clean}`);
        }
      }
      break;
    case 'paste':
      if (data.text) {
        const b64 = Buffer.from(data.text, 'utf-8').toString('base64');
        sendInputCommand(`p ${b64}`);
      }
      break;
    case 'set_device_name':
    case 'rename_device':
      if (data.name && typeof data.name === 'string' && data.name.trim()) {
        config.deviceName = data.name.trim();
        try {
          fs.writeFileSync(CONFIG_PATH, JSON.stringify(config, null, 2), 'utf-8');
          console.log(`[Config] Device renamed to: "${config.deviceName}" via WS`);
          ws.send(JSON.stringify({ type: 'device_name_updated', deviceName: config.deviceName }));
        } catch (err) {
          console.error('[Config] Failed to save config:', err);
        }
      }
      break;
    case 'release_modifiers':
      sendInputCommand('r');
      break;

    // Screen Stream control
    case 'start_screen_stream':
      ws.isSubscribedToScreen = true;
      subscribeScreen(ws);
      ws.send(JSON.stringify({ type: 'screen_stream_started' }));
      break;
    case 'stop_screen_stream':
      if (ws.isSubscribedToScreen) {
        ws.isSubscribedToScreen = false;
        unsubscribeScreen(ws);
        ws.send(JSON.stringify({ type: 'screen_stream_stopped' }));
      }
      break;
    case 'set_screen_fps':
    case 'screen_fps':
      if (typeof data.fps === 'number') {
        const newFps = Math.max(1, Math.min(60, Math.round(data.fps)));
        currentScreenFps = newFps;
        console.log(`[ScreenStream] Client requested ${newFps} FPS`);
        if (screenStreamProcess && screenStreamProcess.stdin && !screenStreamProcess.stdin.destroyed) {
          try {
            screenStreamProcess.stdin.write(`fps ${newFps}\n`);
          } catch (e) {}
        }
        try {
          ws.send(JSON.stringify({ type: 'screen_fps_updated', fps: newFps }));
        } catch (e) {}
      }
      break;

    // Multi-Monitor control
    case 'get_monitors':
      fetchMonitorsInfo((info) => {
        if (ws.readyState === WebSocket.OPEN) {
          ws.send(JSON.stringify({ type: 'monitors_info', ...info, active: currentMonitorIndex }));
        }
      });
      break;

    case 'select_monitor':
    case 'set_monitor':
      if (typeof data.index === 'number') {
        const targetIndex = Math.max(-1, Math.round(data.index));
        currentMonitorIndex = targetIndex;
        console.log(`[Monitor] Switched to monitor index ${targetIndex}`);
        sendInputCommand(`monitor ${targetIndex}`);
        if (screenStreamProcess && screenStreamProcess.stdin && !screenStreamProcess.stdin.destroyed) {
          try {
            screenStreamProcess.stdin.write(`monitor ${targetIndex}\n`);
          } catch (e) {}
        }
        broadcastMonitors();
      }
      break;

    // Audio / Media
    case 'audio_volume':
      if (data.volume !== undefined) {
        setAudioVolume(data.volume);
      }
      break;
    case 'audio_mute':
      toggleAudioMute();
      break;
    case 'media_key':
      if (data.key) {
        sendInputCommand(`k ${data.key}`);
      }
      break;

    // Brightness
    case 'set_brightness':
    case 'brightness':
      if (data.level !== undefined || data.brightness !== undefined) {
        const b = data.brightness !== undefined ? data.brightness : data.level;
        const mon = data.monitor !== undefined ? data.monitor : currentMonitorIndex;
        setBrightness(b, mon);
      }
      break;

    // Ping / Heartbeat
    case 'ping':
      ws.send(JSON.stringify({ type: 'pong', timestamp: Date.now() }));
      break;

    // Agent Self-Restart (Express Gateway style)
    case 'restart_agent':
    case 'restart_server':
      console.log('[WebSocket] Restart agent requested from client');
      try {
        ws.send(JSON.stringify({ type: 'restarting_agent' }));
      } catch (e) {}
      const restartVbs = path.join(__dirname, 'remote_restart.vbs');
      const rChild = spawn('wscript.exe', [restartVbs], {
        detached: true,
        stdio: 'ignore'
      });
      rChild.unref();
      setTimeout(() => {
        cleanup();
        process.exit(0);
      }, 1000);
      break;

    case 'screen_off':
      executeScreenOff();
      break;

    default:
      console.warn('[WebSocket] Unknown command type:', data.type);
  }
}

// ==========================================
// UDP DISCOVERY RESPONDER (Port 5051)
// ==========================================
const udpSocket = dgram.createSocket({ type: 'udp4', reuseAddr: true });

udpSocket.on('error', (err) => {
  console.error('[UDP] Discovery socket error:', err.message);
});

let activePort = config.port || 5050;

udpSocket.on('message', (msg, rinfo) => {
  const text = msg.toString().trim();
  if (text.includes('REMOTE_POCKET_DISCOVER') || text.includes('DISCOVER')) {
    const responsePayload = JSON.stringify({
      deviceName: config.deviceName || 'PC Personal',
      port: activePort,
      version: 1,
      hostname: os.hostname(),
      platform: 'win32',
      hasPin: Boolean(config.pin && config.pin.length > 0)
    });

    const responseBuf = Buffer.from(responsePayload);
    udpSocket.send(responseBuf, 0, responseBuf.length, rinfo.port, rinfo.address, (err) => {
      if (err) {
        console.error(`[UDP] Failed to respond to ${rinfo.address}:${rinfo.port}:`, err.message);
      } else {
        console.log(`[UDP] Responded discovery to ${rinfo.address}:${rinfo.port} (Port ${activePort})`);
      }
    });
  }
});

udpSocket.on('listening', () => {
  const address = udpSocket.address();
  console.log(`[UDP] Discovery responder listening on ${address.address}:${address.port}`);
});

try {
  udpSocket.bind(config.udpPort || 5051);
} catch (err) {
  console.error('[UDP] Bind error:', err.message);
}

// ==========================================
// NGROK TUNNEL SUPPORT (Optional)
// ==========================================
async function initNgrok(portToExpose) {
  const token = process.env.NGROK_AUTHTOKEN || config.ngrokAuthToken;
  if (!token) return;

  try {
    const ngrok = require('@ngrok/ngrok');
    const domain = process.env.NGROK_DOMAIN || config.ngrokDomain;
    const opts = {
      addr: portToExpose,
      authtoken: token
    };
    if (domain) opts.domain = domain;

    const listener = await ngrok.forward(opts);
    const url = listener.url();
    console.log(`[Ngrok] Public Tunnel active: ${url}`);
    fs.writeFileSync(path.join(__dirname, 'ngrok_url.txt'), url, 'utf-8');
  } catch (err) {
    console.warn('[Ngrok] Failed to establish tunnel:', err.message);
  }
}

// ==========================================
// START SERVER (Automatic Port Fallback)
// ==========================================
function startHttpServer(initialPort, maxAttempts = 15) {
  let candidatePort = initialPort;
  let attempts = 0;

  function tryListen() {
    server.removeAllListeners('error');

    server.once('error', (err) => {
      if (err.code === 'EADDRINUSE') {
        attempts++;
        if (attempts < maxAttempts) {
          candidatePort++;
          console.warn(`[Server] Port in use. Trying next available port: ${candidatePort}...`);
          setTimeout(tryListen, 200);
        } else {
          console.error(`[Server] Failed to bind after ${maxAttempts} attempts.`);
        }
      } else {
        console.error('[Server] Fatal error:', err.message);
      }
    });

    server.listen(candidatePort, '0.0.0.0', () => {
      activePort = candidatePort;
      logAgent('INFO', '====================================================');
      logAgent('INFO', `Remote Pocket PC Agent running on port ${activePort}`);
      logAgent('INFO', `Device Name: "${config.deviceName}"`);
      logAgent('INFO', `UDP Discovery: Port ${config.udpPort || 5051}`);
      if (!config.pin || config.pin.trim() === '') {
        logAgent('WARN', 'Security warning: PIN is not configured. Endpoints and WebSocket are unprotected.');
      } else {
        logAgent('INFO', 'Security: PIN protection is enabled for HTTP and WebSocket.');
      }
      logAgent('INFO', '====================================================');
      initNgrok(activePort);
      getAudioStatus().catch(() => {});
      getBrightness().catch(() => {});
    });
  }

  tryListen();
}

function cleanup() {
  if (inputProcess) {
    try {
      inputProcess.stdin.write('exit\n');
      inputProcess.kill();
    } catch (e) {}
    inputProcess = null;
  }
  if (screenStreamProcess) {
    try {
      screenStreamProcess.kill();
    } catch (e) {}
    screenStreamProcess = null;
  }
  try {
    udpSocket.close();
  } catch (e) {}
  try {
    wss.close();
  } catch (e) {}
  try {
    server.close();
  } catch (e) {}
}

process.on('SIGINT', () => {
  cleanup();
  process.exit(0);
});

process.on('SIGTERM', () => {
  cleanup();
  process.exit(0);
});

startHttpServer(config.port || 5050);
