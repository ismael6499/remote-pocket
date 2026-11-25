const http = require('http');
const dgram = require('dgram');
const fs = require('fs');
const path = require('path');
const WebSocket = require('ws');

let pin = '';
try {
  const cfg = JSON.parse(fs.readFileSync(path.join(__dirname, 'config.json'), 'utf-8'));
  pin = cfg.pin || '';
} catch (e) {}

function httpRequest(method, reqPath, bodyObj = null) {
  return new Promise((resolve, reject) => {
    const bodyStr = bodyObj ? JSON.stringify(bodyObj) : null;
    const headers = {};
    if (pin) headers['X-PIN'] = pin;
    if (bodyStr) {
      headers['Content-Type'] = 'application/json';
      headers['Content-Length'] = Buffer.byteLength(bodyStr);
    }
    const req = http.request({
      hostname: '127.0.0.1',
      port: 5050,
      path: reqPath,
      method,
      headers
    }, (res) => {
      let raw = '';
      res.on('data', (c) => raw += c);
      res.on('end', () => {
        try {
          resolve({ status: res.statusCode, data: JSON.parse(raw) });
        } catch (e) {
          reject(new Error(`Invalid JSON (${res.statusCode}): ${raw}`));
        }
      });
    });
    req.on('error', reject);
    if (bodyStr) req.write(bodyStr);
    req.end();
  });
}

async function runTest() {
  console.log('[Test] Starting Remote Pocket Agent verification...');

  // Start the server process
  const { fork } = require('child_process');
  const serverProc = fork(path.join(__dirname, 'server.js'), [], {
    stdio: 'pipe'
  });

  serverProc.stdout.on('data', (d) => process.stdout.write('[Agent-Out] ' + d.toString()));
  serverProc.stderr.on('data', (d) => process.stderr.write('[Agent-Err] ' + d.toString()));

  // Wait for server to bind port 5050 (up to 6s)
  for (let i = 0; i < 20; i++) {
    try {
      await httpRequest('GET', '/api/version');
      break;
    } catch (e) {
      await new Promise((r) => setTimeout(r, 300));
    }
  }

  try {
    // 1. Test HTTP GET /api/status
    console.log('\n--- 1. Testing GET /api/status ---');
    const statusRes = await httpRequest('GET', '/api/status');
    if (statusRes.status !== 200) throw new Error(`/api/status returned ${statusRes.status}`);
    console.log('[OK] Status received:', JSON.stringify(statusRes.data, null, 2));

    // 2. Test HTTP GET /api/shortcuts
    console.log('\n--- 2. Testing GET /api/shortcuts ---');
    const shortcutsRes = await httpRequest('GET', '/api/shortcuts');
    if (shortcutsRes.status !== 200) throw new Error(`/api/shortcuts returned ${shortcutsRes.status}`);
    const shortcuts = shortcutsRes.data;
    console.log(`[OK] Shortcuts loaded (${shortcuts.length} items):`, shortcuts.map(s => s.name).join(', '));

    // 3. Test HTTP GET /api/system/idle
    console.log('\n--- 3. Testing GET /api/system/idle ---');
    const idleRes = await httpRequest('GET', '/api/system/idle');
    if (idleRes.status !== 200 || typeof idleRes.data.idleMs !== 'number' || typeof idleRes.data.idleSeconds !== 'number') {
      throw new Error(`/api/system/idle failed: ${JSON.stringify(idleRes)}`);
    }
    console.log('[OK] Idle status:', JSON.stringify(idleRes.data));

    // 4. Test HTTP POST /api/input/ping (threshold skip + forced dispatch)
    console.log('\n--- 4. Testing POST /api/input/ping ---');
    const skipRes = await httpRequest('POST', '/api/input/ping', { mode: 'both', minIdleSeconds: 999999, force: false });
    if (skipRes.status !== 200 || !skipRes.data.skipped || skipRes.data.reason !== 'user_active') {
      throw new Error(`Expected skip on high minIdleSeconds, got: ${JSON.stringify(skipRes)}`);
    }
    console.log('[OK] Active threshold skip verified:', JSON.stringify(skipRes.data));

    const forceRes = await httpRequest('POST', '/api/input/ping', { mode: 'shift', force: true });
    if (forceRes.status !== 200 || !forceRes.data.executed) {
      throw new Error(`Expected forced execution, got: ${JSON.stringify(forceRes)}`);
    }
    await new Promise((r) => setTimeout(r, 150));
    console.log('[OK] Forced input ping verified:', JSON.stringify(forceRes.data));

    // 5. Test UDP Discovery
    console.log('\n--- 5. Testing UDP Discovery Responder on Port 5051 ---');
    const udpClient = dgram.createSocket('udp4');
    const udpPromise = new Promise((resolve, reject) => {
      const timeout = setTimeout(() => reject(new Error('UDP Discovery timeout')), 2500);
      udpClient.on('message', (msg) => {
        clearTimeout(timeout);
        resolve(JSON.parse(msg.toString()));
      });
    });

    const discMsg = Buffer.from('REMOTE_POCKET_DISCOVER');
    udpClient.send(discMsg, 0, discMsg.length, 5051, '127.0.0.1');
    const udpResponse = await udpPromise;
    udpClient.close();
    console.log('[OK] UDP Discovery response received:', JSON.stringify(udpResponse, null, 2));

    // 6. Test WebSocket Connection & Ping
    console.log('\n--- 6. Testing WebSocket Connection & Ping ---');
    const wsUrl = pin ? `ws://127.0.0.1:5050?pin=${encodeURIComponent(pin)}` : 'ws://127.0.0.1:5050';
    const ws = new WebSocket(wsUrl);
    await new Promise((resolve, reject) => {
      ws.on('open', () => {
        console.log('[OK] WebSocket connected successfully');
        ws.send(JSON.stringify({ type: 'ping' }));
      });
      ws.on('message', (data) => {
        const parsed = JSON.parse(data.toString());
        if (parsed.type === 'pong') {
          console.log('[OK] Received pong response from server');
          ws.close();
          resolve();
        }
      });
      ws.on('error', reject);
    });

    console.log('\n====================================================');
    console.log('   ALL PC AGENT TESTS PASSED SUCCESSFULLY!   ');
    console.log('====================================================\n');
  } catch (err) {
    console.error('[FAIL] Test failed:', err.message);
    process.exitCode = 1;
  } finally {
    console.log('[Test] Shutting down test server...');
    serverProc.kill('SIGTERM');
    setTimeout(() => {
      try { serverProc.kill('SIGKILL'); } catch (e) {}
      process.exit();
    }, 500);
  }
}

runTest();
