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

    public void connect(Device device) {
        if (device == null) return;
        this.currentDevice = device;
        disconnect();

        isConnecting = true;
        String wsUrl = device.getWebSocketUrl();
        Log.d(TAG, "Connecting to WebSocket: " + wsUrl);

        Request request = new Request.Builder()
                .url(wsUrl)
                .build();

        webSocket = client.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket ws, Response response) {
                isConnecting = false;
                Log.d(TAG, "WebSocket connected successfully");
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
                ws.close(1000, null);
            }

            @Override
            public void onClosed(WebSocket ws, int code, String reason) {
                isConnecting = false;
                Log.d(TAG, "WebSocket closed: " + reason);
                if (listener != null) {
                    mainHandler.post(listener::onDisconnected);
                }
            }

            @Override
            public void onFailure(WebSocket ws, Throwable t, Response response) {
                isConnecting = false;
                Log.e(TAG, "WebSocket failure: " + t.getMessage());
                if (listener != null) {
                    mainHandler.post(() -> listener.onError(t.getMessage()));
                }
            }
        });
    }

    public void disconnect() {
        if (webSocket != null) {
            try {
                webSocket.close(1000, "User disconnected");
            } catch (Exception ignored) {}
            webSocket = null;
        }
        isConnecting = false;
    }

    public boolean isConnected() {
        return webSocket != null && !isConnecting;
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
