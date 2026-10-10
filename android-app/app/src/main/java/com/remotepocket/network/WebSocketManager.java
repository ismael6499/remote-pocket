package com.remotepocket.network;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.remotepocket.model.Device;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

public class WebSocketManager {
    private static final String TAG = "WebSocketManager";

    public interface ConnectionListener {
        void onConnected();
        void onDisconnected();
        void onScreenFrame(byte[] jpegData);
        void onError(String error);
    }

    private final OkHttpClient client;
    private final Gson gson = new Gson();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private WebSocket webSocket;
    private ConnectionListener listener;
    private boolean isConnecting = false;
    private volatile boolean isConnected = false;
    private Device currentDevice;

    public WebSocketManager() {
        this.client = new OkHttpClient.Builder()
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .connectTimeout(5, TimeUnit.SECONDS)
                .pingInterval(10, TimeUnit.SECONDS)
                .build();
    }

    public void setConnectionListener(ConnectionListener listener) {
        this.listener = listener;
    }

    public boolean isConnecting() {
        return isConnecting;
    }

    public void connect(Device device) {
        connect(device, false);
    }

    public void connect(Device device, boolean forceReconnect) {
        if (device == null) return;
        if (!forceReconnect && currentDevice != null && currentDevice.getHost().equals(device.getHost()) && currentDevice.getPort() == device.getPort()) {
            if (isConnected() || isConnecting) {
                Log.d(TAG, "Already connected or connecting to: " + device.getHost());
                return;
            }
        }
        this.currentDevice = device;
        disconnect();

        isConnecting = true;
        isConnected = false;
        String wsUrl = device.getWebSocketUrl();
        Log.d(TAG, "Connecting to WebSocket: " + wsUrl);
        com.remotepocket.util.AppLogger.i(TAG, "Connecting to WebSocket: " + wsUrl + " (" + device.getDeviceName() + ")");

        try {
            Request request = new Request.Builder()
                    .url(wsUrl)
                    .build();

            webSocket = client.newWebSocket(request, new WebSocketListener() {
                @Override
                public void onOpen(WebSocket ws, Response response) {
                    isConnecting = false;
                    isConnected = true;
                    Log.d(TAG, "WebSocket connected successfully");
                    com.remotepocket.util.AppLogger.i(TAG, "WebSocket connected successfully: " + wsUrl);
                    if (listener != null) {
                        mainHandler.post(listener::onConnected);
                    }
                }

            @Override
            public void onMessage(WebSocket ws, String text) {
                // Handled in background, e.g. status or pong
                // Can parse JSON if needed
            }

            @Override
            public void onMessage(WebSocket ws, ByteString bytes) {
                // Binary JPEG frame for screen preview
                if (listener != null) {
                    byte[] data = bytes.toByteArray();
                    mainHandler.post(() -> listener.onScreenFrame(data));
                }
            }

            @Override
            public void onClosing(WebSocket ws, int code, String reason) {
                com.remotepocket.util.AppLogger.w(TAG, "WebSocket closing (code " + code + "): " + reason);
                ws.close(1000, null);
            }

            @Override
            public void onClosed(WebSocket ws, int code, String reason) {
                isConnecting = false;
                isConnected = false;
                if (webSocket == ws) {
                    webSocket = null;
                }
                Log.d(TAG, "WebSocket closed: " + reason);
                com.remotepocket.util.AppLogger.i(TAG, "WebSocket closed: " + reason);
                if (listener != null) {
                    mainHandler.post(listener::onDisconnected);
                }
            }

            @Override
            public void onFailure(WebSocket ws, Throwable t, Response response) {
                isConnecting = false;
                isConnected = false;
                if (webSocket == ws) {
                    webSocket = null;
                }
                String responseInfo = response != null ? " [HTTP " + response.code() + " " + response.message() + "]" : "";
                Log.e(TAG, "WebSocket failure: " + t.getMessage() + responseInfo);
                com.remotepocket.util.AppLogger.e(TAG, "WebSocket failure: " + wsUrl + responseInfo, t);
                if (listener != null) {
                    mainHandler.post(() -> listener.onError(t.getMessage()));
                }
            }
        });
        } catch (Throwable t) {
            isConnecting = false;
            isConnected = false;
            webSocket = null;
            Log.e(TAG, "Failed to create WebSocket: " + t.getMessage(), t);
            com.remotepocket.util.AppLogger.e(TAG, "Failed to connect to " + wsUrl, t);
            if (listener != null) {
                mainHandler.post(() -> listener.onError("Connection error: " + t.getMessage()));
            }
        }
    }

    public void disconnect() {
        isConnected = false;
        isConnecting = false;
        if (webSocket != null) {
            try {
                webSocket.close(1000, "User disconnected");
            } catch (Exception ignored) {}
            webSocket = null;
        }
    }

    public boolean isConnected() {
        return isConnected && webSocket != null;
    }

    public void sendScreenFps(int fps) {
        JsonObject obj = new JsonObject();
        obj.addProperty("type", "set_screen_fps");
        obj.addProperty("fps", fps);
        sendJson(obj);
    }

    private void sendJson(JsonObject obj) {
        if (webSocket != null) {
            try {
                webSocket.send(gson.toJson(obj));
            } catch (Exception e) {
                Log.e(TAG, "Failed to send WebSocket message", e);
            }
        }
    }

    // ==========================================
    // MOUSE COMMANDS (<5ms latency)
    // ==========================================
    public void sendMouseMove(int dx, int dy) {
        JsonObject obj = new JsonObject();
        obj.addProperty("type", "mouse_move");
        obj.addProperty("dx", dx);
        obj.addProperty("dy", dy);
        sendJson(obj);
    }

    public void sendMouseAbsolute(int x, int y) {
        JsonObject obj = new JsonObject();
        obj.addProperty("type", "mouse_abs");
        obj.addProperty("x", x);
        obj.addProperty("y", y);
        sendJson(obj);
    }

    public void sendMouseClick(String button) {
        JsonObject obj = new JsonObject();
        obj.addProperty("type", "mouse_click");
        obj.addProperty("button", button != null ? button : "left");
        sendJson(obj);
    }

    public void sendMouseDoubleClick(String button) {
        JsonObject obj = new JsonObject();
        obj.addProperty("type", "mouse_double_click");
        obj.addProperty("button", button != null ? button : "left");
        sendJson(obj);
    }

    public void sendMouseDown(String button) {
        JsonObject obj = new JsonObject();
        obj.addProperty("type", "mouse_down");
        obj.addProperty("button", button != null ? button : "left");
        sendJson(obj);
    }

    public void sendMouseUp(String button) {
        JsonObject obj = new JsonObject();
        obj.addProperty("type", "mouse_up");
        obj.addProperty("button", button != null ? button : "left");
        sendJson(obj);
    }

    public void sendMouseScroll(int delta) {
        JsonObject obj = new JsonObject();
        obj.addProperty("type", "mouse_scroll");
        obj.addProperty("delta", delta);
        sendJson(obj);
    }

    // ==========================================
    // KEYBOARD COMMANDS
    // ==========================================
    public void sendKeyPress(String key) {
        JsonObject obj = new JsonObject();
        obj.addProperty("type", "key_press");
        obj.addProperty("key", key);
        sendJson(obj);
    }

    public void sendTypeText(String text) {
        JsonObject obj = new JsonObject();
        obj.addProperty("type", "type_text");
        obj.addProperty("text", text);
        sendJson(obj);
    }

    public void sendPaste(String text) {
        JsonObject obj = new JsonObject();
        obj.addProperty("type", "paste");
        obj.addProperty("text", text);
        sendJson(obj);
    }

    public void sendReleaseModifiers() {
        JsonObject obj = new JsonObject();
        obj.addProperty("type", "release_modifiers");
        sendJson(obj);
    }

    // ==========================================
    // SCREEN STREAMING CONTROLS
    // ==========================================
    public void startScreenStream() {
        JsonObject obj = new JsonObject();
        obj.addProperty("type", "start_screen_stream");
        sendJson(obj);
    }

    public void stopScreenStream() {
        JsonObject obj = new JsonObject();
        obj.addProperty("type", "stop_screen_stream");
        sendJson(obj);
    }

    // ==========================================
    // AUDIO & MEDIA CONTROLS
    // ==========================================
    public void sendAudioVolume(int volume) {
        JsonObject obj = new JsonObject();
        obj.addProperty("type", "audio_volume");
        obj.addProperty("volume", volume);
        sendJson(obj);
    }

    public void sendAudioMute() {
        JsonObject obj = new JsonObject();
        obj.addProperty("type", "audio_mute");
        sendJson(obj);
    }

    public void sendMediaKey(String key) {
        JsonObject obj = new JsonObject();
        obj.addProperty("type", "media_key");
        obj.addProperty("key", key);
        sendJson(obj);
    }
}
