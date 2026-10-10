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

        // Brightness Seek Listener
        seekBrightness.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                txtBrightnessVal.setText(progress + "%");
                if (fromUser && webSocketManager != null) {
                    webSocketManager.sendBrightness(progress);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                postBrightness(seekBar.getProgress());
            }
        });

        // Volume Seek Listener
        seekVolume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                txtVolumeVal.setText(progress + "%");
                if (fromUser && webSocketManager != null) {
                    webSocketManager.sendAudioVolume(progress);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
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

        // Load Initial State & Shortcuts
        loadInitialState();
    }

    private void loadInitialState() {
        if (device == null) return;
        executor.execute(() -> {
            try {
                // Fetch shortcuts
                Request reqShortcuts = new Request.Builder().url(device.getBaseHttpUrl() + "/api/shortcuts").build();
                Response respShortcuts = httpClient.newCall(reqShortcuts).execute();
                if (respShortcuts.isSuccessful() && respShortcuts.body() != null) {
                    String json = respShortcuts.body().string();
                    Type listType = new TypeToken<List<Shortcut>>() {}.getType();
                    List<Shortcut> shortcuts = gson.fromJson(json, listType);
                    mainHandler.post(() -> renderShortcuts(shortcuts));
                }

                // Fetch status (brightness & audio)
                Request reqStatus = new Request.Builder().url(device.getBaseHttpUrl() + "/api/status").build();
                Response respStatus = httpClient.newCall(reqStatus).execute();
                if (respStatus.isSuccessful() && respStatus.body() != null) {
                    String json = respStatus.body().string();
                    com.google.gson.JsonObject obj = gson.fromJson(json, com.google.gson.JsonObject.class);
                    if (obj.has("brightness")) {
                        int b = obj.get("brightness").getAsInt();
                        mainHandler.post(() -> {
                            seekBrightness.setProgress(b);
                            txtBrightnessVal.setText(b + "%");
                        });
                    }
                    if (obj.has("audio") && obj.getAsJsonObject("audio").has("volume")) {
                        com.google.gson.JsonElement vElem = obj.getAsJsonObject("audio").get("volume");
                        if (!vElem.isJsonNull()) {
                            int v = vElem.getAsInt();
                            mainHandler.post(() -> {
                                seekVolume.setProgress(v);
                                txtVolumeVal.setText(v + "%");
                            });
                        }
                    }
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
            btn.setTextSize(12f);
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
        if (device == null) return;
        Toast.makeText(getContext(), "Running " + s.getName(), Toast.LENGTH_SHORT).show();
        executor.execute(() -> {
            try {
                MediaType JSON = MediaType.parse("application/json; charset=utf-8");
                String payload = "{\"id\":\"" + s.getId() + "\"}";
                RequestBody body = RequestBody.create(payload, JSON);
                Request req = new Request.Builder()
                        .url(device.getBaseHttpUrl() + "/api/shortcuts/run")
                        .post(body)
                        .build();
                httpClient.newCall(req).execute();
            } catch (Exception e) {
                Log.e(TAG, "Failed to run shortcut", e);
            }
        });
    }

    private void postAudioVolume(int volume) {
        if (webSocketManager != null) {
            webSocketManager.sendAudioVolume(volume);
        }
        if (device == null) return;
        executor.execute(() -> {
            try {
                MediaType JSON = MediaType.parse("application/json; charset=utf-8");
                String payload = "{\"volume\":" + volume + "}";
                RequestBody body = RequestBody.create(payload, JSON);
                Request req = new Request.Builder()
                        .url(device.getBaseHttpUrl() + "/api/audio/volume")
                        .post(body)
                        .build();
                httpClient.newCall(req).execute();
            } catch (Exception e) {
                Log.e(TAG, "Failed to post audio volume", e);
            }
        });
    }

    private void postAudioMute() {
        if (webSocketManager != null) {
            webSocketManager.sendAudioMute();
        }
        if (device == null) return;
        executor.execute(() -> {
            try {
                MediaType JSON = MediaType.parse("application/json; charset=utf-8");
                RequestBody body = RequestBody.create("{}", JSON);
                Request req = new Request.Builder()
                        .url(device.getBaseHttpUrl() + "/api/audio/mute")
                        .post(body)
                        .build();
                httpClient.newCall(req).execute();
            } catch (Exception e) {
                Log.e(TAG, "Failed to post audio mute", e);
            }
        });
    }

    private void postMediaAction(String action) {
        if (webSocketManager != null) {
            webSocketManager.sendMediaKey(action);
        }
        if (device == null) return;
        executor.execute(() -> {
            try {
                MediaType JSON = MediaType.parse("application/json; charset=utf-8");
                String payload = "{\"action\":\"" + action + "\"}";
                RequestBody body = RequestBody.create(payload, JSON);
                Request req = new Request.Builder()
                        .url(device.getBaseHttpUrl() + "/api/audio/action")
                        .post(body)
                        .build();
                httpClient.newCall(req).execute();
            } catch (Exception e) {
                Log.e(TAG, "Failed to post media action", e);
            }
        });
    }

    private void postBrightness(int level) {
        if (webSocketManager != null) {
            webSocketManager.sendBrightness(level);
        }
        if (device == null) return;
        executor.execute(() -> {
            try {
                MediaType JSON = MediaType.parse("application/json; charset=utf-8");
                String payload = "{\"brightness\":" + level + "}";
                RequestBody body = RequestBody.create(payload, JSON);
                Request req = new Request.Builder()
                        .url(device.getBaseHttpUrl() + "/api/brightness")
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
                Request req = new Request.Builder()
                        .url(device.getBaseHttpUrl() + "/api/server/restart")
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
                Request req = new Request.Builder()
                        .url(device.getBaseHttpUrl() + "/api/system/" + action)
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
}
