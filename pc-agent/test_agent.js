const http = require('http');
const dgram = require('dgram');
const WebSocket = require('ws');

async function runTest() {
  console.log('[Test] Starting Remote Pocket Agent verification...');

  // Start the server process
  const { fork } = require('child_process');
  const path = require('path');
  const serverProc = fork(path.join(__dirname, 'server.js'), [], {
    stdio: 'pipe'
  });

  serverProc.stdout.on('data', (d) => process.stdout.write('[Agent-Out] ' + d.toString()));
  serverProc.stderr.on('data', (d) => process.stderr.write('[Agent-Err] ' + d.toString()));

  // Wait 1.5s for startup
  await new Promise((r) => setTimeout(r, 1500));

  try {
    // 1. Test HTTP GET /api/status
    console.log('\n--- 1. Testing GET /api/status ---');
    const statusData = await new Promise((resolve, reject) => {
      http.get('http://127.0.0.1:5050/api/status', (res) => {
        let raw = '';
        res.on('data', (c) => raw += c);
        res.on('end', () => resolve(JSON.parse(raw)));
      }).on('error', reject);
    });
    console.log('[OK] Status received:', JSON.stringify(statusData, null, 2));

    // 2. Test HTTP GET /api/shortcuts
    console.log('\n--- 2. Testing GET /api/shortcuts ---');
    const shortcuts = await new Promise((resolve, reject) => {
      http.get('http://127.0.0.1:5050/api/shortcuts', (res) => {
        let raw = '';
        res.on('data', (c) => raw += c);
        res.on('end', () => resolve(JSON.parse(raw)));
      }).on('error', reject);
    });
    console.log(`[OK] Shortcuts loaded (${shortcuts.length} items):`, shortcuts.map(s => s.name).join(', '));

    // 3. Test UDP Discovery
    console.log('\n--- 3. Testing UDP Discovery Responder on Port 5051 ---');
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

    // 4. Test WebSocket Connection & Ping
    console.log('\n--- 4. Testing WebSocket Connection & Ping ---');
    const ws = new WebSocket('ws://127.0.0.1:5050');
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
