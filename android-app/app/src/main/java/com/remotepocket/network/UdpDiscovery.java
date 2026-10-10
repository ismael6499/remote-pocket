package com.remotepocket.network;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;

import com.google.gson.Gson;
import com.remotepocket.model.Device;
import com.remotepocket.util.AppLogger;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
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

    private List<InetAddress> getBroadcastAddresses() {
        List<InetAddress> list = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces != null && interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                if (ni.isLoopback() || !ni.isUp()) continue;

                for (InterfaceAddress ia : ni.getInterfaceAddresses()) {
                    InetAddress broadcast = ia.getBroadcast();
                    if (broadcast != null && !list.contains(broadcast)) {
                        list.add(broadcast);
                    }
                }
            }
        } catch (Exception e) {
            AppLogger.e(TAG, "Failed to resolve network broadcast addresses", e);
        }
        try {
            InetAddress global = InetAddress.getByName("255.255.255.255");
            if (!list.contains(global)) list.add(global);
        } catch (Exception ignored) {}
        return list;
    }

    public void startDiscovery(int timeoutMs, DiscoveryCallback callback) {
        if (isRunning) return;
        isRunning = true;
        AppLogger.i(TAG, "Starting forced LAN scan on UDP port " + UDP_PORT + " (timeout " + timeoutMs + "ms)");

        executor.execute(() -> {
            DatagramSocket socket = null;
            WifiManager.MulticastLock multicastLock = null;

            try {
                // Acquire Wi-Fi Multicast Lock
                WifiManager wifi = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
                if (wifi != null) {
                    multicastLock = wifi.createMulticastLock("RemotePocketUdpLock");
                    multicastLock.setReferenceCounted(true);
                    multicastLock.acquire();
                }

                socket = new DatagramSocket();
                socket.setBroadcast(true);
                socket.setSoTimeout(500);

                byte[] sendData = DISCOVER_MESSAGE.getBytes("UTF-8");
                List<InetAddress> broadcastAddrs = getBroadcastAddresses();

                // Send 1st burst across all interfaces
                for (InetAddress addr : broadcastAddrs) {
                    DatagramPacket packet = new DatagramPacket(sendData, sendData.length, addr, UDP_PORT);
                    socket.send(packet);
                    AppLogger.d(TAG, "Sent discovery packet to " + addr.getHostAddress() + ":" + UDP_PORT);
                }

                long startTime = System.currentTimeMillis();
                byte[] recvBuf = new byte[2048];
                boolean secondBurstSent = false;

                while (isRunning && (System.currentTimeMillis() - startTime < timeoutMs)) {
                    // Send 2nd burst after 350ms to defeat Wi-Fi packet drop
                    if (!secondBurstSent && (System.currentTimeMillis() - startTime > 350)) {
                        secondBurstSent = true;
                        for (InetAddress addr : broadcastAddrs) {
                            try {
                                DatagramPacket packet = new DatagramPacket(sendData, sendData.length, addr, UDP_PORT);
                                socket.send(packet);
                            } catch (Exception ignored) {}
                        }
                    }

                    try {
                        DatagramPacket recvPacket = new DatagramPacket(recvBuf, recvBuf.length);
                        socket.receive(recvPacket);

                        String json = new String(recvPacket.getData(), 0, recvPacket.getLength(), "UTF-8").trim();
                        String responderIp = recvPacket.getAddress().getHostAddress();
                        AppLogger.i(TAG, "Discovered PC from " + responderIp + ":" + recvPacket.getPort() + " -> " + json);

                        Device discovered = gson.fromJson(json, Device.class);
                        if (discovered != null) {
                            discovered.setHost(responderIp);
                            discovered.setLastSeen(System.currentTimeMillis());
                            mainHandler.post(() -> callback.onDeviceFound(discovered));
                        }
                    } catch (java.net.SocketTimeoutException ste) {
                        // socket timeout expected for polling
                    }
                }
            } catch (Exception e) {
                AppLogger.e(TAG, "Error during UDP discovery", e);
            } finally {
                isRunning = false;
                if (socket != null && !socket.isClosed()) {
                    socket.close();
                }
                if (multicastLock != null && multicastLock.isHeld()) {
                    try { multicastLock.release(); } catch (Exception ignored) {}
                }
                AppLogger.i(TAG, "Discovery completed.");
                mainHandler.post(callback::onDiscoveryFinished);
            }
        });
    }

    public void stop() {
        isRunning = false;
    }
}
