package com.remotepocket.util;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class AppLogger {
    public interface LogListener {
        void onNewLogEntry(String entry);
    }

    private static final String TAG = "AppLogger";
    private static final int MAX_ENTRIES = 300;
    private static final LinkedList<String> logEntries = new LinkedList<>();
    private static final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US);
    private static final SimpleDateFormat fileDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
    private static final OkHttpClient httpClient = new OkHttpClient();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());
    private static LogListener activeListener;

    public static synchronized void d(String tag, String message) {
        record("DEBUG", tag, message);
        Log.d(tag, message);
    }

    public static synchronized void i(String tag, String message) {
        record("INFO", tag, message);
        Log.i(tag, message);
    }

    public static synchronized void w(String tag, String message) {
        record("WARN", tag, message);
        Log.w(tag, message);
    }

    public static synchronized void e(String tag, String message, Throwable t) {
        String errDetails = message;
        if (t != null) {
            errDetails += " -> [" + t.getClass().getSimpleName() + ": " + t.getMessage() + "]";
        }
        record("ERROR", tag, errDetails);
        Log.e(tag, message, t);
    }

    private static synchronized void record(String level, String tag, String message) {
        String timestamp = timeFormat.format(new Date());
        String entry = String.format("[%s] [%s/%s] %s", timestamp, level, tag, message);
        logEntries.addLast(entry);
        while (logEntries.size() > MAX_ENTRIES) {
            logEntries.removeFirst();
        }

        if (activeListener != null) {
            mainHandler.post(() -> {
                if (activeListener != null) {
                    activeListener.onNewLogEntry(entry);
                }
            });
        }
    }

    public static synchronized void setListener(LogListener listener) {
        activeListener = listener;
    }

    public static synchronized void clear() {
        logEntries.clear();
        record("INFO", "AppLogger", "Logs buffer cleared");
    }

    public static synchronized List<String> getEntries() {
        return new ArrayList<>(logEntries);
    }

    public static synchronized String getFullReport(Context context) {
        StringBuilder sb = new StringBuilder();
        sb.append("====================================================\n");
        sb.append("         REMOTE POCKET - DIAGNOSTIC REPORT          \n");
        sb.append("====================================================\n");
        sb.append("Generated: ").append(fileDateFormat.format(new Date())).append("\n");
        sb.append("App Version: 1.0.0\n");
        sb.append("Device: ").append(Build.MANUFACTURER).append(" ").append(Build.MODEL)
                .append(" (SDK ").append(Build.VERSION.SDK_INT).append(", Android ").append(Build.VERSION.RELEASE).append(")\n");
        sb.append("----------------------------------------------------\n");
        sb.append("LOGS DUMP (Most recent ").append(logEntries.size()).append(" events):\n");
        sb.append("----------------------------------------------------\n");
        for (String line : logEntries) {
            sb.append(line).append("\n");
        }
        sb.append("====================================================\n");
        return sb.toString();
    }

    public static void copyToClipboard(Context context) {
        String report = getFullReport(context);
        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            ClipData clip = ClipData.newPlainText("Remote Pocket Diagnostics", report);
            clipboard.setPrimaryClip(clip);
            Toast.makeText(context, "Diagnostic logs copied to clipboard!", Toast.LENGTH_SHORT).show();
        }
    }

    public static void sendToServer(Context context, String serverBaseUrl, Callback callback) {
        if (serverBaseUrl == null || serverBaseUrl.isEmpty()) {
            if (callback != null) {
                callback.onFailure(null, new IOException("No server URL configured"));
            }
            return;
        }

        String url = serverBaseUrl;
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "http://" + url;
        }
        if (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        final String targetUrl = url + "/api/logs";

        JsonObject json = new JsonObject();
        json.addProperty("deviceName", Build.MANUFACTURER + " " + Build.MODEL);
        json.addProperty("logs", getFullReport(context));

        JsonObject clientInfo = new JsonObject();
        clientInfo.addProperty("manufacturer", Build.MANUFACTURER);
        clientInfo.addProperty("model", Build.MODEL);
        clientInfo.addProperty("androidVersion", Build.VERSION.RELEASE);
        clientInfo.addProperty("sdkInt", Build.VERSION.SDK_INT);
        json.add("clientInfo", clientInfo);

        RequestBody body = RequestBody.create(
                new Gson().toJson(json),
                MediaType.parse("application/json; charset=utf-8")
        );

        Request request = new Request.Builder()
                .url(targetUrl)
                .post(body)
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                AppLogger.e(TAG, "Failed to send logs to server: " + targetUrl, e);
                mainHandler.post(() -> {
                    Toast.makeText(context, "Failed to send logs: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    if (callback != null) callback.onFailure(call, e);
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    AppLogger.i(TAG, "Logs successfully transmitted to " + targetUrl);
                    mainHandler.post(() -> {
                        Toast.makeText(context, "Logs successfully saved on PC server!", Toast.LENGTH_SHORT).show();
                    });
                } else {
                    AppLogger.w(TAG, "Server rejected logs transmission: HTTP " + response.code());
                }
                if (callback != null) callback.onResponse(call, response);
            }
        });
    }
}
