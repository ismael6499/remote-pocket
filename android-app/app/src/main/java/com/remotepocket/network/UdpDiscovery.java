package com.remotepocket.network;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.gson.Gson;
import com.remotepocket.model.Device;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class UdpDiscovery {
    private static final String TAG = "UdpDiscovery";
    public static final int UDP_PORT = 5051;
    public static final String DISCOVER_MESSAGE = "REMOTE_POCKET_DISCOVER";

    public interface DiscoveryCallback {
        void onDeviceFound(Device device);
        void onDiscoveryFinished();
    }

    private final Context context;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Gson gson = new Gson();
    private volatile boolean isRunning = false;

    public UdpDiscovery(Context context) {
        this.context = context.getApplicationContext();
    }

    public void startDiscovery(int timeoutMs, DiscoveryCallback callback) {
        if (isRunning) return;
        isRunning = true;

        executor.execute(() -> {
            DatagramSocket socket = null;
            WifiManager.MulticastLock multicastLock = null;

            try {
                // Acquire MulticastLock if on Wi-Fi
                WifiManager wifi = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
                if (wifi != null) {
                    multicastLock = wifi.createMulticastLock("RemotePocketUdpLock");
                    multicastLock.setReferenceCounted(true);
                    multicastLock.acquire();
                }

                socket = new DatagramSocket();
                socket.setBroadcast(true);
                socket.setSoTimeout(1000);

                // Send broadcast datagram
                byte[] sendData = DISCOVER_MESSAGE.getBytes("UTF-8");
                InetAddress broadcastAddress = InetAddress.getByName("255.255.255.255");
                DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, broadcastAddress, UDP_PORT);
                socket.send(sendPacket);
                Log.d(TAG, "Sent broadcast discovery packet to 255.255.255.255:" + UDP_PORT);

                long startTime = System.currentTimeMillis();
                byte[] recvBuf = new byte[2048];

                while (isRunning && (System.currentTimeMillis() - startTime < timeoutMs)) {
                    try {
                        DatagramPacket recvPacket = new DatagramPacket(recvBuf, recvBuf.length);
                        socket.receive(recvPacket);

                        String json = new String(recvPacket.getData(), 0, recvPacket.getLength(), "UTF-8").trim();
                        Log.d(TAG, "Received UDP response from " + recvPacket.getAddress().getHostAddress() + ": " + json);

                        Device discovered = gson.fromJson(json, Device.class);
                        if (discovered != null) {
                            discovered.setHost(recvPacket.getAddress().getHostAddress());
                            discovered.setLastSeen(System.currentTimeMillis());
                            mainHandler.post(() -> callback.onDeviceFound(discovered));
                        }
                    } catch (java.net.SocketTimeoutException ste) {
                        // socket timeout normal to check loop condition
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error during UDP discovery", e);
            } finally {
                isRunning = false;
                if (socket != null && !socket.isClosed()) {
                    socket.close();
                }
                if (multicastLock != null && multicastLock.isHeld()) {
                    try { multicastLock.release(); } catch (Exception ignored) {}
                }
                mainHandler.post(callback::onDiscoveryFinished);
            }
        });
    }

    public void stop() {
        isRunning = false;
    }
}
