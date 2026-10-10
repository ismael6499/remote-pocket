const express = require('express');
const http = require('http');
const WebSocket = require('ws');
const dgram = require('dgram');
const os = require('os');
const path = require('path');
const fs = require('fs');
const { spawn, exec } = require('child_process');
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

// ==========================================
// CONFIGURATION & PATHS
// ==========================================
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

if (!fs.existsSync(UPDATES_DIR)) {
  fs.mkdirSync(UPDATES_DIR, { recursive: true });
}

// ==========================================
// PERSISTENT INPUT HELPER PROCESS (--pipe)
// ==========================================
let inputProcess = null;
let inputProcessReady = false;

function startInputProcess() {
  if (!fs.existsSync(INPUT_HELPER_PATH)) {
    console.error(`[InputHelper] Binary not found at: ${INPUT_HELPER_PATH}. Run install.bat to compile.`);
    return;
  }

  try {
    inputProcess = spawn(INPUT_HELPER_PATH, ['--pipe'], {
      stdio: ['pipe', 'pipe', 'ignore'],
      windowsHide: true
    });

    inputProcess.stdout.on('data', (data) => {
      const str = data.toString().trim();
      if (str.includes('READY')) {
        inputProcessReady = true;
        console.log('[InputHelper] Native pipe process ready.');
      }
    });

    inputProcess.on('exit', (code, signal) => {
      console.warn(`[InputHelper] Pipe process exited (code=${code}, signal=${signal}). Restarting in 1s...`);
      inputProcessReady = false;
      inputProcess = null;
      setTimeout(startInputProcess, 1000);
    });

    inputProcess.on('error', (err) => {
      console.error('[InputHelper] Pipe process error:', err.message);
    });
  } catch (err) {
    console.error('[InputHelper] Failed to spawn InputHelper:', err.message);
  }
}

function sendInputCommand(cmd) {
  if (inputProcess && inputProcess.stdin && !inputProcess.stdin.destroyed) {
    try {
      inputProcess.stdin.write(cmd + '\n');
      return true;
    } catch (e) {
      console.error('[InputHelper] Write error:', e.message);
    }
  }
  return false;
}

startInputProcess();

// ==========================================
// SCREEN STREAMING MANAGER (On-Demand 2 FPS)
// ==========================================
let screenStreamProcess = null;
const screenSubscribers = new Set();

function startScreenStreamer() {
  if (screenStreamProcess) return;
  if (!fs.existsSync(INPUT_HELPER_PATH)) return;

  console.log('[ScreenStream] Starting on-demand screen stream (2 FPS, 65% quality, 720p)...');
  try {
    screenStreamProcess = spawn(INPUT_HELPER_PATH, ['--stream', '2', '65', '720'], {
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
              const cx = parseInt(coords[0], 10) || 0;
              const cy = parseInt(coords[1], 10) || 0;
              const sw = parseInt(coords[2], 10) || 0;
              const sh = parseInt(coords[3], 10) || 0;
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
function getAudioStatus() {
  return new Promise((resolve) => {
    exec(`"${INPUT_HELPER_PATH}" volume`, { timeout: 2000 }, (err, stdout) => {
      if (err) {
        return resolve({ volume: 50, muted: false, playing: false });
      }
      const match = stdout.match(/AUDIO:(.+)/);
      if (match) {
        try {
          return resolve(JSON.parse(match[1]));
        } catch (e) {}
      }
      resolve({ volume: 50, muted: false, playing: false });
    });
  });
}

function setAudioVolume(level) {
  sendInputCommand(`vol ${level}`);
}

function toggleAudioMute(muted) {
  if (typeof muted === 'boolean') {
    sendInputCommand(`mute ${muted}`);
  } else {
    sendInputCommand('mute');
  }
}

function getBrightness() {
  return new Promise((resolve) => {
    const cmd = `powershell -NoProfile -Command "try { (Get-CimInstance -Namespace root/wmi -ClassName WmiMonitorBrightness -ErrorAction Stop).CurrentBrightness } catch { 100 }"`;
    exec(cmd, { timeout: 3000 }, (err, stdout) => {
      if (err) return resolve(100);
      const val = parseInt(stdout.trim(), 10);
      resolve(isNaN(val) ? 100 : val);
    });
  });
}

function setBrightness(level) {
  return new Promise((resolve) => {
    const safeLevel = Math.max(0, Math.min(100, parseInt(level, 10) || 50));
    const cmd = `powershell -NoProfile -Command "try { (Get-CimInstance -Namespace root/wmi -ClassName WmiMonitorBrightnessMethods -ErrorAction Stop).WmiSetBrightness(1, ${safeLevel}) } catch { }"`;
    exec(cmd, { timeout: 3000 }, () => resolve(safeLevel));
  });
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

function loadShortcuts() {
  try {
    if (fs.existsSync(SHORTCUTS_PATH)) {
      return JSON.parse(fs.readFileSync(SHORTCUTS_PATH, 'utf-8'));
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
    version: '1.0.0'
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
      sendInputCommand('k media_play_pause');
      break;
    case 'next':
      sendInputCommand('k media_next');
      break;
    case 'prev':
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
  const { brightness } = req.body;
  if (brightness !== undefined) {
    const val = await setBrightness(brightness);
    return res.json({ success: true, brightness: val });
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

  console.log(`[Shortcuts] Executing: ${target.name} (${target.command})`);
  exec(target.command, { windowsHide: true }, (err) => {
    if (err) console.error(`[Shortcuts] Error running ${target.name}:`, err.message);
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
    versionCode: 1,
    versionName: '1.0.0',
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
  console.log(`[WebSocket] Client connected: ${req.socket.remoteAddress}`);

  ws.isSubscribedToScreen = false;

  ws.on('message', (message, isBinary) => {
    if (isBinary) return; // ignore incoming binary frames
    try {
      const data = JSON.parse(message.toString());
      if (data.type !== 'mouse_move') {
        console.log(`[WS Input] Action: ${data.type} ${JSON.stringify(data)}`);
      }
      handleWebSocketMessage(ws, data);
    } catch (err) {
      console.error('[WebSocket] Bad message JSON:', err.message);
    }
  });

  ws.on('close', () => {
    console.log(`[WebSocket] Client disconnected: ${req.socket.remoteAddress}`);
    if (ws.isSubscribedToScreen) {
      unsubscribeScreen(ws);
    }
  });

  ws.on('error', (err) => {
    console.error('[WebSocket] Error:', err.message);
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

    // Keyboard
    case 'key_press':
      if (data.key) {
        sendInputCommand(`k ${data.key}`);
      }
      break;
    case 'type_text':
      if (data.text) {
        sendInputCommand(`t ${data.text}`);
      }
      break;
    case 'paste':
      if (data.text) {
        const b64 = Buffer.from(data.text, 'utf-8').toString('base64');
        sendInputCommand(`p ${b64}`);
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
      console.log('====================================================');
      console.log(`  Remote Pocket PC Agent running on port ${activePort}`);
      console.log(`  Device Name: "${config.deviceName}"`);
      console.log(`  UDP Discovery: Port ${config.udpPort || 5051}`);
      console.log('====================================================');
      initNgrok(activePort);
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
