package com.remotepocket.ui;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.remotepocket.R;
import com.remotepocket.model.Device;
import com.remotepocket.model.Shortcut;
import com.remotepocket.network.WebSocketManager;

import java.lang.reflect.Type;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class SystemSheet extends Dialog {
    private static final String TAG = "SystemSheet";
    private final Device device;
    private final WebSocketManager webSocketManager;
    private final OkHttpClient httpClient = new OkHttpClient();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Gson gson = new Gson();

    private SeekBar seekBrightness, seekVolume;
    private TextView txtBrightnessVal, txtVolumeVal;
    private LinearLayout layoutShortcutsContainer;
    private View btnScreenOff;
    private boolean serverDetectedScreenOff = false;
    private int devTapCount = 0;
    private long lastDevTapTime = 0;

    public SystemSheet(@NonNull Context context, Device device, WebSocketManager webSocketManager) {
        super(context);
        this.device = device;
        this.webSocketManager = webSocketManager;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.dialog_system_sheet);

        Window window = getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            window.setGravity(Gravity.BOTTOM);
        }

        findViewById(R.id.btnCloseSystemSheet).setOnClickListener(v -> dismiss());

        seekBrightness = findViewById(R.id.seekBrightness);
        txtBrightnessVal = findViewById(R.id.txtBrightnessVal);
        seekVolume = findViewById(R.id.seekVolume);
        txtVolumeVal = findViewById(R.id.txtVolumeVal);
        layoutShortcutsContainer = findViewById(R.id.layoutShortcutsContainer);
        btnScreenOff = findViewById(R.id.btnScreenOff);

        String hostKey = (device != null && device.getHost() != null) ? device.getHost() : "default";
        android.content.SharedPreferences prefs = getContext().getSharedPreferences("remote_pocket_prefs", Context.MODE_PRIVATE);
        serverDetectedScreenOff = prefs.getBoolean("pref_device_screen_off_" + hostKey, true);

        // Pre-load cached Brightness & Volume immediately to eliminate visual slider jump/movement
        int cachedBrightness = prefs.getInt("pref_device_brightness_" + hostKey, 100);
        seekBrightness.setProgress(cachedBrightness);
        txtBrightnessVal.setText(cachedBrightness + "%");

        int cachedVolume = prefs.getInt("pref_device_volume_" + hostKey, 50);
        seekVolume.setProgress(cachedVolume);
        txtVolumeVal.setText(cachedVolume + "%");

        if (btnScreenOff != null) {
            btnScreenOff.setOnClickListener(v -> triggerScreenOff());
        }
        updateScreenOffVisibility();

        View txtTitle = findViewById(R.id.txtSystemSheetTitle);
        if (txtTitle != null) {
            txtTitle.setOnClickListener(v -> handleDevTitleTap());
        }

        // Brightness Seek Listener (throttled: every PowerShell call is heavy on the PC)
        seekBrightness.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            private long lastSend = 0;

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                txtBrightnessVal.setText(progress + "%");
                if (fromUser) {
                    prefs.edit().putInt("pref_device_brightness_" + hostKey, progress).apply();
                }
                long now = System.currentTimeMillis();
                if (fromUser && webSocketManager != null && now - lastSend >= 400) {
                    lastSend = now;
                    webSocketManager.sendBrightness(progress);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                prefs.edit().putInt("pref_device_brightness_" + hostKey, seekBar.getProgress()).apply();
                postBrightness(seekBar.getProgress());
            }
        });

        // Volume Seek Listener (throttled)
        seekVolume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            private long lastSend = 0;

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                txtVolumeVal.setText(progress + "%");
                if (fromUser) {
                    prefs.edit().putInt("pref_device_volume_" + hostKey, progress).apply();
                }
                long now = System.currentTimeMillis();
                if (fromUser && webSocketManager != null && now - lastSend >= 80) {
                    lastSend = now;
                    webSocketManager.sendAudioVolume(progress);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                prefs.edit().putInt("pref_device_volume_" + hostKey, seekBar.getProgress()).apply();
                postAudioVolume(seekBar.getProgress());
            }
        });

        // Media Buttons
        findViewById(R.id.btnMediaPrev).setOnClickListener(v -> postMediaAction("media_prev"));
        findViewById(R.id.btnMediaPlayPause).setOnClickListener(v -> postMediaAction("media_play_pause"));
        findViewById(R.id.btnMediaNext).setOnClickListener(v -> postMediaAction("media_next"));
        findViewById(R.id.btnMediaMute).setOnClickListener(v -> postAudioMute());

        // Power Buttons
        findViewById(R.id.btnPowerLock).setOnClickListener(v -> postSystemAction("lock"));
        findViewById(R.id.btnPowerSleep).setOnClickListener(v -> postSystemAction("sleep"));
        findViewById(R.id.btnPowerRestart).setOnClickListener(v -> confirmPowerAction("restart"));
        findViewById(R.id.btnPowerShutdown).setOnClickListener(v -> confirmPowerAction("shutdown"));

        // Restart PC Agent Button
        View btnRestartAgent = findViewById(R.id.btnRestartAgent);
        if (btnRestartAgent != null) {
            btnRestartAgent.setOnClickListener(v -> confirmRestartAgent());
        }

        // Pre-load Cached Shortcuts immediately to avoid pop-in
        if (device != null) {
            String cachedShortcutsJson = prefs.getString("pref_device_shortcuts_" + hostKey, null);
            if (cachedShortcutsJson != null) {
                try {
                    Type listType = new TypeToken<List<Shortcut>>() {}.getType();
                    List<Shortcut> cachedShortcuts = gson.fromJson(cachedShortcutsJson, listType);
                    if (cachedShortcuts != null && !cachedShortcuts.isEmpty()) {
                        renderShortcuts(cachedShortcuts);
                    }
                } catch (Exception ignored) {}
            }
        }

        // Load Initial State & Shortcuts
        loadInitialState();
    }

    private String getDevicePin() {
        if (webSocketManager != null && webSocketManager.getActivePin() != null && !webSocketManager.getActivePin().isEmpty()) {
            return webSocketManager.getActivePin();
        }
        if (device != null && device.getHost() != null && getContext() != null) {
            android.content.SharedPreferences prefs = getContext().getSharedPreferences("remote_pocket_prefs", Context.MODE_PRIVATE);
            return prefs.getString("pref_device_pin_" + device.getHost(), null);
        }
        return null;
    }

    private Request.Builder newAuthenticatedRequestBuilder(String url) {
        Request.Builder builder = new Request.Builder().url(url);
        String pin = getDevicePin();
        if (pin != null && !pin.isEmpty()) {
            builder.addHeader("x-pin", pin);
        }
        return builder;
    }

    private void loadInitialState() {
        if (device == null) return;
        String hostKey = device.getHost() != null ? device.getHost() : "default";
        android.content.SharedPreferences prefs = getContext().getSharedPreferences("remote_pocket_prefs", Context.MODE_PRIVATE);

        executor.execute(() -> {
            try {
                // Fetch status first (brightness, audio, screenOffApp)
                Request reqStatus = newAuthenticatedRequestBuilder(device.getBaseHttpUrl() + "/api/status").build();
                Response respStatus = httpClient.newCall(reqStatus).execute();
                if (respStatus.isSuccessful() && respStatus.body() != null) {
                    String json = respStatus.body().string();
                    com.google.gson.JsonObject obj = gson.fromJson(json, com.google.gson.JsonObject.class);
                    if (obj.has("brightness")) {
                        int b = obj.get("brightness").getAsInt();
                        prefs.edit().putInt("pref_device_brightness_" + hostKey, b).apply();
                        mainHandler.post(() -> {
                            if (seekBrightness != null && seekBrightness.getProgress() != b) {
                                seekBrightness.setProgress(b);
                                txtBrightnessVal.setText(b + "%");
                            }
                        });
                    }
                    if (obj.has("audio") && obj.getAsJsonObject("audio").has("volume")) {
                        com.google.gson.JsonElement vElem = obj.getAsJsonObject("audio").get("volume");
                        if (!vElem.isJsonNull()) {
                            int v = vElem.getAsInt();
                            prefs.edit().putInt("pref_device_volume_" + hostKey, v).apply();
                            mainHandler.post(() -> {
                                if (seekVolume != null && seekVolume.getProgress() != v) {
                                    seekVolume.setProgress(v);
                                    txtVolumeVal.setText(v + "%");
                                }
                            });
                        }
                    }
                    if (obj.has("screenOffApp") && !obj.get("screenOffApp").isJsonNull()) {
                        boolean isScreenOffApp = obj.get("screenOffApp").getAsBoolean();
                        serverDetectedScreenOff = isScreenOffApp;
                        prefs.edit().putBoolean("pref_device_screen_off_" + hostKey, isScreenOffApp).apply();
                        mainHandler.post(this::updateScreenOffVisibility);
                    }
                    if (obj.has("keyboardLayout") && !obj.get("keyboardLayout").isJsonNull()) {
                        String kbLayout = obj.get("keyboardLayout").getAsString();
                        prefs.edit().putString("pref_device_keyboard_layout_" + hostKey, kbLayout).apply();
                    }
                }

                // Fetch shortcuts second
                Request reqShortcuts = newAuthenticatedRequestBuilder(device.getBaseHttpUrl() + "/api/shortcuts").build();
                Response respShortcuts = httpClient.newCall(reqShortcuts).execute();
                if (respShortcuts.isSuccessful() && respShortcuts.body() != null) {
                    String json = respShortcuts.body().string();
                    prefs.edit().putString("pref_device_shortcuts_" + hostKey, json).apply();
                    Type listType = new TypeToken<List<Shortcut>>() {}.getType();
                    List<Shortcut> shortcuts = gson.fromJson(json, listType);
                    mainHandler.post(() -> renderShortcuts(shortcuts));
                }
            } catch (Exception e) {
                Log.e(TAG, "Error loading system sheet state", e);
            }
        });
    }

    private void renderShortcuts(List<Shortcut> shortcuts) {
        if (shortcuts == null || layoutShortcutsContainer == null) return;
        layoutShortcutsContainer.removeAllViews();

        for (Shortcut s : shortcuts) {
            Button btn = new Button(getContext());
            btn.setText(s.getName());
            btn.setTextSize(11.5f);
            btn.setSingleLine(true);
            btn.setMaxLines(1);
            btn.setPadding(dpToPx(14), 0, dpToPx(14), 0);
            btn.setTextColor(ContextCompat.getColor(getContext(), R.color.text_primary));
            btn.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(getContext(), R.color.btn_surface)));

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dpToPx(42));
            lp.setMargins(0, 0, dpToPx(8), 0);
            btn.setLayoutParams(lp);

            btn.setOnClickListener(v -> executeShortcut(s));
            layoutShortcutsContainer.addView(btn);
        }
    }

    private void executeShortcut(Shortcut s) {
        if (s == null) return;
        if (webSocketManager != null && webSocketManager.isConnected()) {
            webSocketManager.sendRunShortcut(s.getId());
            return;
        }
        if (device == null) return;
        executor.execute(() -> {
            try {
                MediaType JSON = MediaType.parse("application/json; charset=utf-8");
                String payload = "{\"id\":\"" + s.getId() + "\"}";
                RequestBody body = RequestBody.create(payload, JSON);
                Request req = newAuthenticatedRequestBuilder(device.getBaseHttpUrl() + "/api/shortcuts/run")
                        .post(body)
                        .build();
                httpClient.newCall(req).execute();
            } catch (Exception e) {
                Log.e(TAG, "Failed to run shortcut", e);
            }
        });
    }

    private void postAudioVolume(int volume) {
        if (webSocketManager != null && webSocketManager.isConnected()) {
            webSocketManager.sendAudioVolume(volume);
            return;
        }
        if (device == null) return;
        executor.execute(() -> {
            try {
                MediaType JSON = MediaType.parse("application/json; charset=utf-8");
                String payload = "{\"volume\":" + volume + "}";
                RequestBody body = RequestBody.create(payload, JSON);
                Request req = newAuthenticatedRequestBuilder(device.getBaseHttpUrl() + "/api/audio/volume")
                        .post(body)
                        .build();
                httpClient.newCall(req).execute();
            } catch (Exception e) {
                Log.e(TAG, "Failed to post audio volume", e);
            }
        });
    }

    private void postAudioMute() {
        if (webSocketManager != null && webSocketManager.isConnected()) {
            webSocketManager.sendAudioMute();
            return;
        }
        if (device == null) return;
        executor.execute(() -> {
            try {
                MediaType JSON = MediaType.parse("application/json; charset=utf-8");
                RequestBody body = RequestBody.create("{}", JSON);
                Request req = newAuthenticatedRequestBuilder(device.getBaseHttpUrl() + "/api/audio/mute")
                        .post(body)
                        .build();
                httpClient.newCall(req).execute();
            } catch (Exception e) {
                Log.e(TAG, "Failed to post audio mute", e);
            }
        });
    }

    private void postMediaAction(String action) {
        if (webSocketManager != null && webSocketManager.isConnected()) {
            webSocketManager.sendMediaKey(action);
            return;
        }
        if (device == null) return;
        executor.execute(() -> {
            try {
                MediaType JSON = MediaType.parse("application/json; charset=utf-8");
                String payload = "{\"action\":\"" + action + "\"}";
                RequestBody body = RequestBody.create(payload, JSON);
                Request req = newAuthenticatedRequestBuilder(device.getBaseHttpUrl() + "/api/audio/action")
                        .post(body)
                        .build();
                httpClient.newCall(req).execute();
            } catch (Exception e) {
                Log.e(TAG, "Failed to post media action", e);
            }
        });
    }

    private void postBrightness(int level) {
        if (webSocketManager != null && webSocketManager.isConnected()) {
            webSocketManager.sendBrightness(level);
            return;
        }
        if (device == null) return;
        executor.execute(() -> {
            try {
                MediaType JSON = MediaType.parse("application/json; charset=utf-8");
                String payload = "{\"brightness\":" + level + "}";
                RequestBody body = RequestBody.create(payload, JSON);
                Request req = newAuthenticatedRequestBuilder(device.getBaseHttpUrl() + "/api/brightness")
                        .post(body)
                        .build();
                httpClient.newCall(req).execute();
            } catch (Exception e) {
                Log.e(TAG, "Failed to post brightness", e);
            }
        });
    }

    private void confirmPowerAction(String action) {
        int titleRes = "shutdown".equals(action) ? R.string.confirm_shutdown_title : R.string.confirm_restart_title;
        int msgRes = "shutdown".equals(action) ? R.string.confirm_shutdown_msg : R.string.confirm_restart_msg;

        new AlertDialog.Builder(getContext())
                .setTitle(titleRes)
                .setMessage(msgRes)
                .setPositiveButton(R.string.action_yes, (d, w) -> postSystemAction(action))
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void confirmRestartAgent() {
        new AlertDialog.Builder(getContext())
                .setTitle(R.string.confirm_restart_agent_title)
                .setMessage(R.string.confirm_restart_agent_msg)
                .setPositiveButton(R.string.action_yes, (d, w) -> {
                    Toast.makeText(getContext(), R.string.toast_restarting_agent, Toast.LENGTH_SHORT).show();
                    postRestartAgent();
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void postRestartAgent() {
        if (device == null) return;
        executor.execute(() -> {
            try {
                RequestBody body = RequestBody.create("", MediaType.parse("application/json"));
                Request req = newAuthenticatedRequestBuilder(device.getBaseHttpUrl() + "/api/server/restart")
                        .post(body)
                        .build();
                httpClient.newCall(req).execute();
                mainHandler.post(this::dismiss);
            } catch (Exception e) {
                Log.e(TAG, "Failed agent restart request", e);
            }
        });
    }

    private void postSystemAction(String action) {
        if (device == null) return;
        executor.execute(() -> {
            try {
                RequestBody body = RequestBody.create("", MediaType.parse("application/json"));
                Request req = newAuthenticatedRequestBuilder(device.getBaseHttpUrl() + "/api/system/" + action)
                        .post(body)
                        .build();
                httpClient.newCall(req).execute();
                mainHandler.post(() -> dismiss());
            } catch (Exception e) {
                Log.e(TAG, "Failed system action: " + action, e);
            }
        });
    }

    private int dpToPx(int dp) {
        float density = getContext().getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    private void handleDevTitleTap() {
        long now = System.currentTimeMillis();
        if (now - lastDevTapTime > 2500) {
            devTapCount = 0;
        }
        lastDevTapTime = now;
        devTapCount++;

        if (devTapCount >= 4 && devTapCount < 7) {
            int remaining = 7 - devTapCount;
            Toast.makeText(getContext(), getContext().getString(R.string.dev_settings_steps_remaining, remaining), Toast.LENGTH_SHORT).show();
        } else if (devTapCount == 7) {
            devTapCount = 0;
            Toast.makeText(getContext(), R.string.dev_settings_unlocked, Toast.LENGTH_SHORT).show();
            new DeveloperSettingsDialog(getContext(), this::updateScreenOffVisibility).show();
        }
    }

    private void updateScreenOffVisibility() {
        if (btnScreenOff == null) return;
        android.content.SharedPreferences prefs = getContext().getSharedPreferences("remote_pocket_prefs", Context.MODE_PRIVATE);
        boolean forceScreenOff = prefs.getBoolean(DeveloperSettingsDialog.PREF_FORCE_SCREEN_OFF, false);
        boolean show = serverDetectedScreenOff || forceScreenOff;
        btnScreenOff.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void triggerScreenOff() {
        if (webSocketManager != null && webSocketManager.isConnected()) {
            webSocketManager.sendScreenOff();
        } else {
            postSystemAction("screen_off");
        }
    }
}
