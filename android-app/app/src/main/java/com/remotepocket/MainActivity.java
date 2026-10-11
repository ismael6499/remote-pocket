package com.remotepocket;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.remotepocket.model.Device;
import com.remotepocket.network.UdpDiscovery;
import com.remotepocket.network.UpdateManager;
import com.remotepocket.network.WebSocketManager;
import com.remotepocket.ui.DeviceManagerDialog;
import com.remotepocket.ui.LogViewerDialog;
import com.remotepocket.ui.PcKeyboardDialog;
import com.remotepocket.ui.SystemSheet;
import com.remotepocket.ui.TouchpadView;
import com.remotepocket.util.AppLogger;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final String PREFS_NAME = "remote_pocket_prefs";
    private static final String KEY_SAVED_DEVICES = "saved_devices";
    private static final String KEY_LAST_DEVICE_HOST = "last_device_host";
    private static final String KEY_SCREEN_FPS = "screen_fps";

    private final List<Device> knownDevices = new ArrayList<>();
    private Device currentDevice;

    private WebSocketManager webSocketManager;
    private UdpDiscovery udpDiscovery;
    private UpdateManager updateManager;

    private TextView txtDeviceName, txtDeviceStatus, txtTouchpadHint;
    private View viewStatusDot;
    private ImageView imgScreenPreview;
    private TouchpadView touchpadView;
    private Button tabTrackpad, tabScreen;
    private LinearLayout layoutTypingInput;
    private EditText editTypingText;
    private FloatingActionButton fabKeyboard;

    private LinearLayout layoutScreenControls;
    private TextView btnToggleFollow, btnZoomLevel, btnFpsSelector, btnEnterFullscreen, btnMonitorSelector;
    private TextView btnFullscreenMonitor;
    private LinearLayout layoutFullscreenTopBar;
    private ImageButton btnExitFullscreen;
    private LinearLayout headerLayout, layoutTabs, layoutMouseButtons;
    private FrameLayout containerMain;
    private boolean isFullscreen = false;
    private int currentFps = 30;
    private int monitorCount = 1;
    private int currentMonitorIndex = 0;
    private final List<String> monitorNames = new ArrayList<>();

    private View btnCollapseScreenControls, btnExpandScreenControls;
    private View btnCollapseFullscreenControls, btnExpandFullscreenControls;
    private boolean isScreenControlsCollapsed = false;
    private boolean isFullscreenControlsCollapsed = false;

    private final Runnable fadeFullscreenControlsRunnable = () -> {
        if (layoutFullscreenTopBar != null && isFullscreen && !isFullscreenControlsCollapsed) {
            layoutFullscreenTopBar.animate().alpha(0.20f).setDuration(400).start();
        }
    };

    private final Handler handler = new Handler(Looper.getMainLooper());
    private int devTapCount = 0;
    private long lastDevTapTime = 0;
    private boolean isAppInForeground = false;
    private final Runnable reconnectRunnable = new Runnable() {
        @Override
        public void run() {
            if (isAppInForeground && currentDevice != null && !webSocketManager.isConnected() && !webSocketManager.isConnecting()) {
                AppLogger.i("MainActivity", "Auto-reconnecting to " + currentDevice.getHost() + ":" + currentDevice.getPort());
                webSocketManager.connect(currentDevice, true);
            }
        }
    };

    private void resetFullscreenControlsFade() {
        handler.removeCallbacks(fadeFullscreenControlsRunnable);
        if (layoutFullscreenTopBar != null) {
            layoutFullscreenTopBar.animate().alpha(1.0f).setDuration(150).start();
        }
        if (isFullscreen && !isFullscreenControlsCollapsed) {
            handler.postDelayed(fadeFullscreenControlsRunnable, 3500);
        }
    }

    private final Runnable fadeFabKeyboardRunnable = () -> {
        if (fabKeyboard != null) {
            fabKeyboard.animate().alpha(0.20f).setDuration(400).start();
        }
    };

    private void resetFabKeyboardFade() {
        handler.removeCallbacks(fadeFabKeyboardRunnable);
        if (fabKeyboard != null) {
            fabKeyboard.animate().alpha(1.0f).setDuration(150).start();
            handler.postDelayed(fadeFabKeyboardRunnable, 3000);
        }
    }

    private final Runnable fadeExpandScreenControlsRunnable = () -> {
        if (btnExpandScreenControls != null && btnExpandScreenControls.getVisibility() == View.VISIBLE && !isFullscreen) {
            btnExpandScreenControls.animate().alpha(0.20f).setDuration(400).start();
        }
    };

    private void resetExpandScreenControlsFade() {
        handler.removeCallbacks(fadeExpandScreenControlsRunnable);
        if (btnExpandScreenControls != null) {
            btnExpandScreenControls.animate().alpha(1.0f).setDuration(150).start();
            if (btnExpandScreenControls.getVisibility() == View.VISIBLE && !isFullscreen) {
                handler.postDelayed(fadeExpandScreenControlsRunnable, 3000);
            }
        }
    }

    private void scheduleAutoReconnect() {
        if (!isAppInForeground || currentDevice == null) return;
        handler.removeCallbacks(reconnectRunnable);
        handler.postDelayed(reconnectRunnable, 1200);
    }

    private boolean isScreenMode = false;
    private DeviceManagerDialog deviceDialog;
    private final Gson gson = new Gson();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        initManagers();
        loadSavedDevices();
        setupListeners();

        // Handle Back button to exit fullscreen if active
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (isFullscreen) {
                    exitFullscreen();
                } else if (activePcKeyboardDialog != null && activePcKeyboardDialog.isShowing()) {
                    activePcKeyboardDialog.dismiss();
                } else if (layoutTypingInput != null && layoutTypingInput.getVisibility() == View.VISIBLE) {
                    hideNormalKeyboard();
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });

        // Start initial auto-discovery on launch
        startUdpDiscovery();
    }

    private void initViews() {
        headerLayout = findViewById(R.id.headerLayout);
        layoutTabs = findViewById(R.id.layoutTabs);
        layoutMouseButtons = findViewById(R.id.layoutMouseButtons);
        containerMain = findViewById(R.id.containerMain);

        txtDeviceName = findViewById(R.id.txtDeviceName);
        txtDeviceStatus = findViewById(R.id.txtDeviceStatus);
        txtTouchpadHint = findViewById(R.id.txtTouchpadHint);
        viewStatusDot = findViewById(R.id.viewStatusDot);
        imgScreenPreview = findViewById(R.id.imgScreenPreview);
        touchpadView = findViewById(R.id.touchpadView);
        tabTrackpad = findViewById(R.id.tabTrackpad);
        tabScreen = findViewById(R.id.tabScreen);
        layoutTypingInput = findViewById(R.id.layoutTypingInput);
        editTypingText = findViewById(R.id.editTypingText);
        fabKeyboard = findViewById(R.id.fabKeyboard);

        layoutScreenControls = findViewById(R.id.layoutScreenControls);
        btnToggleFollow = findViewById(R.id.btnToggleFollow);
        btnZoomLevel = findViewById(R.id.btnZoomLevel);
        btnFpsSelector = findViewById(R.id.btnFpsSelector);
        btnEnterFullscreen = findViewById(R.id.btnEnterFullscreen);
        btnMonitorSelector = findViewById(R.id.btnMonitorSelector);
        btnExitFullscreen = findViewById(R.id.btnExitFullscreen);
        btnFullscreenMonitor = findViewById(R.id.btnFullscreenMonitor);
        layoutFullscreenTopBar = findViewById(R.id.layoutFullscreenTopBar);
        btnCollapseScreenControls = findViewById(R.id.btnCollapseScreenControls);
        btnExpandScreenControls = findViewById(R.id.btnExpandScreenControls);
        btnCollapseFullscreenControls = findViewById(R.id.btnCollapseFullscreenControls);
        btnExpandFullscreenControls = findViewById(R.id.btnExpandFullscreenControls);
    }

    private final java.util.concurrent.ExecutorService frameDecoderExecutor = java.util.concurrent.Executors.newSingleThreadExecutor();

    private void initManagers() {
        webSocketManager = new WebSocketManager();
        touchpadView.setWebSocketManager(webSocketManager);
        touchpadView.setZoomTarget(imgScreenPreview);
        touchpadView.setOnZoomChangeListener(this::updateScreenControlsUi);
        udpDiscovery = new UdpDiscovery(this);
        updateManager = new UpdateManager(this);

        webSocketManager.setConnectionListener(new WebSocketManager.ConnectionListener() {
            @Override
            public void onConnected() {
                handler.removeCallbacks(reconnectRunnable);
                updateConnectionUi(true);
                touchpadView.resyncCursorFromServer();
                webSocketManager.sendGetMonitors();
                if (isScreenMode) {
                    webSocketManager.startScreenStream();
                    webSocketManager.sendScreenFps(currentFps);
                }
                if (currentDevice != null) {
                    updateManager.checkForUpdates(currentDevice, true);
                }
            }

            @Override
            public void onDisconnected() {
                updateConnectionUi(false);
                if (isAppInForeground) {
                    scheduleAutoReconnect();
                }
            }

            @Override
            public void onScreenFrame(byte[] jpegData) {
                if (isScreenMode && jpegData != null && jpegData.length > 0) {
                    frameDecoderExecutor.execute(() -> {
                        try {
                            int offset = 0;
                            int curX = -1, curY = -1, scrW = 1920, scrH = 1080;
                            if (jpegData.length > 12 && jpegData[0] == 'R' && jpegData[1] == 'P' && jpegData[2] == 'K' && jpegData[3] == 'T') {
                                curX = (jpegData[4] & 0xFF) | ((jpegData[5] & 0xFF) << 8);
                                curY = (jpegData[6] & 0xFF) | ((jpegData[7] & 0xFF) << 8);
                                scrW = (jpegData[8] & 0xFF) | ((jpegData[9] & 0xFF) << 8);
                                scrH = (jpegData[10] & 0xFF) | ((jpegData[11] & 0xFF) << 8);
                                offset = 12;
                            }

                            Bitmap bmp = BitmapFactory.decodeByteArray(jpegData, offset, jpegData.length - offset);
                            if (bmp != null) {
                                final int finalCurX = curX;
                                final int finalCurY = curY;
                                final int finalScrW = scrW;
                                final int finalScrH = scrH;
                                runOnUiThread(() -> {
                                    imgScreenPreview.setImageBitmap(bmp);
                                    touchpadView.updateServerCursorPosition(finalCurX, finalCurY, finalScrW, finalScrH);
                                });
                            }
                        } catch (Exception ignored) {}
                    });
                }
            }

            @Override
            public void onError(String error) {
                updateConnectionUi(false);
                if (isAppInForeground) {
                    scheduleAutoReconnect();
                }
            }

            @Override
            public void onMonitorsInfo(JsonObject monitorsJson) {
                handleMonitorsInfo(monitorsJson);
            }
        });
    }

    private boolean isManualDragActive = false;

    private void setupListeners() {
        // Device Selector Click (Click anywhere on card, texts, dot or arrow to open selector)
        View.OnClickListener openDeviceSelectorClick = v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            showDeviceManagerDialog();
        };
        findViewById(R.id.btnDeviceSelector).setOnClickListener(openDeviceSelectorClick);
        txtDeviceName.setOnClickListener(v -> {
            long now = System.currentTimeMillis();
            if (now - lastDevTapTime > 2500) {
                devTapCount = 0;
            }
            lastDevTapTime = now;
            devTapCount++;

            if (devTapCount >= 4 && devTapCount < 7) {
                int remaining = 7 - devTapCount;
                Toast.makeText(this, getString(R.string.dev_settings_steps_remaining, remaining), Toast.LENGTH_SHORT).show();
            } else if (devTapCount == 7) {
                devTapCount = 0;
                Toast.makeText(this, R.string.dev_settings_unlocked, Toast.LENGTH_SHORT).show();
                new com.remotepocket.ui.DeveloperSettingsDialog(this, null).show();
            } else {
                openDeviceSelectorClick.onClick(v);
            }
        });
        txtDeviceStatus.setOnClickListener(openDeviceSelectorClick);
        viewStatusDot.setOnClickListener(openDeviceSelectorClick);
        View arrowView = findViewById(R.id.btnDeviceSelectorArrow);
        if (arrowView != null) arrowView.setOnClickListener(openDeviceSelectorClick);

        // Refresh Discovery (Single tap = Fresh force scan, Long press = Diagnostics Logs)
        findViewById(R.id.btnRefreshDiscovery).setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            startUdpDiscovery(true);
        });

        View.OnLongClickListener openLogsLongClick = v -> {
            v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            showLogViewerDialog();
            return true;
        };
        findViewById(R.id.btnDeviceSelector).setOnLongClickListener(openLogsLongClick);
        txtDeviceStatus.setOnLongClickListener(openLogsLongClick);
        txtDeviceName.setOnLongClickListener(openLogsLongClick);
        viewStatusDot.setOnLongClickListener(openLogsLongClick);
        if (arrowView != null) arrowView.setOnLongClickListener(openLogsLongClick);
        findViewById(R.id.btnRefreshDiscovery).setOnLongClickListener(openLogsLongClick);

        // Open System Controls Sheet
        findViewById(R.id.btnOpenSystemSheet).setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            if (currentDevice != null) {
                SystemSheet sheet = new SystemSheet(this, currentDevice, webSocketManager);
                sheet.show();
            } else {
                Toast.makeText(this, R.string.no_device_selected, Toast.LENGTH_SHORT).show();
            }
        });

        // Tabs
        tabTrackpad.setOnClickListener(v -> switchMode(false));
        tabScreen.setOnClickListener(v -> switchMode(true));

        // Screen Zoom & Follow Controls
        if (btnToggleFollow != null) {
            btnToggleFollow.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                touchpadView.toggleFollowCursor();
            });
        }
        if (btnZoomLevel != null) {
            btnZoomLevel.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                touchpadView.cycleZoomLevel();
            });
        }
        if (btnFpsSelector != null) {
            btnFpsSelector.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                if (currentFps == 30) currentFps = 60;
                else if (currentFps == 60) currentFps = 2;
                else if (currentFps == 2) currentFps = 15;
                else currentFps = 30;

                getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                        .putInt(KEY_SCREEN_FPS, currentFps)
                        .apply();
                updateFpsUi();
                if (webSocketManager.isConnected()) {
                    webSocketManager.sendScreenFps(currentFps);
                }
            });
        }
        if (btnEnterFullscreen != null) {
            btnEnterFullscreen.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                enterFullscreen();
            });
        }
        if (btnExitFullscreen != null) {
            btnExitFullscreen.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                exitFullscreen();
            });
        }

        View.OnClickListener monitorClickListener = v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            cycleOrSelectMonitor();
        };
        View.OnLongClickListener monitorLongClickListener = v -> {
            v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            showMonitorSelectionDialog();
            return true;
        };

        if (btnMonitorSelector != null) {
            btnMonitorSelector.setOnClickListener(monitorClickListener);
            btnMonitorSelector.setOnLongClickListener(monitorLongClickListener);
        }
        if (btnFullscreenMonitor != null) {
            btnFullscreenMonitor.setOnClickListener(monitorClickListener);
            btnFullscreenMonitor.setOnLongClickListener(monitorLongClickListener);
        }

        // Screen Controls Collapse / Expand (Normal Mode)
        if (btnCollapseScreenControls != null) {
            btnCollapseScreenControls.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                isScreenControlsCollapsed = true;
                if (layoutScreenControls != null) layoutScreenControls.setVisibility(View.GONE);
                if (btnExpandScreenControls != null) {
                    btnExpandScreenControls.setVisibility(View.VISIBLE);
                    resetExpandScreenControlsFade();
                }
            });
        }
        if (btnExpandScreenControls != null) {
            btnExpandScreenControls.setOnTouchListener((v, event) -> {
                resetExpandScreenControlsFade();
                return false;
            });
            btnExpandScreenControls.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                isScreenControlsCollapsed = false;
                handler.removeCallbacks(fadeExpandScreenControlsRunnable);
                if (btnExpandScreenControls != null) btnExpandScreenControls.setVisibility(View.GONE);
                if (layoutScreenControls != null) layoutScreenControls.setVisibility(View.VISIBLE);
            });
        }

        // Fullscreen Controls Collapse / Expand
        if (btnCollapseFullscreenControls != null) {
            btnCollapseFullscreenControls.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                isFullscreenControlsCollapsed = true;
                handler.removeCallbacks(fadeFullscreenControlsRunnable);
                if (layoutFullscreenTopBar != null) layoutFullscreenTopBar.setVisibility(View.GONE);
                if (btnExpandFullscreenControls != null) btnExpandFullscreenControls.setVisibility(View.VISIBLE);
            });
        }
        if (btnExpandFullscreenControls != null) {
            btnExpandFullscreenControls.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                isFullscreenControlsCollapsed = false;
                if (btnExpandFullscreenControls != null) btnExpandFullscreenControls.setVisibility(View.GONE);
                if (btnExitFullscreen != null) btnExitFullscreen.setVisibility(View.VISIBLE);
                if (layoutFullscreenTopBar != null) {
                    layoutFullscreenTopBar.setAlpha(1.0f);
                    layoutFullscreenTopBar.setVisibility(View.VISIBLE);
                }
                resetFullscreenControlsFade();
            });
        }

        if (layoutFullscreenTopBar != null) {
            layoutFullscreenTopBar.setOnTouchListener((v, event) -> {
                resetFullscreenControlsFade();
                return false;
            });
        }

        // Mouse Action Buttons
        findViewById(R.id.btnLeftClick).setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            webSocketManager.sendMouseClick("left");
        });
        findViewById(R.id.btnMiddleClick).setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            webSocketManager.sendMouseClick("middle");
        });
        findViewById(R.id.btnRightClick).setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            webSocketManager.sendMouseClick("right");
        });
        Button btnDragHold = findViewById(R.id.btnDragHold);
        btnDragHold.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            isManualDragActive = !isManualDragActive;
            if (isManualDragActive) {
                btnDragHold.setText(R.string.btn_drag_held);
                btnDragHold.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.accent_indigo)));
                webSocketManager.sendMouseDown("left");
            } else {
                btnDragHold.setText(R.string.btn_drag_hold);
                btnDragHold.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.btn_surface)));
                webSocketManager.sendMouseUp("left");
            }
        });
        findViewById(R.id.btnDoubleClick).setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            webSocketManager.sendMouseDoubleClick("left");
        });
        findViewById(R.id.btnScrollUp).setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            webSocketManager.sendMouseScroll(120);
        });
        findViewById(R.id.btnScrollDown).setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            webSocketManager.sendMouseScroll(-120);
        });

        // Send text typing
        findViewById(R.id.btnSendTyping).setOnClickListener(v -> sendEnteredText());
        View btnSwitchToPcKb = findViewById(R.id.btnSwitchToPcKb);
        if (btnSwitchToPcKb != null) {
            btnSwitchToPcKb.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                showPcKeyboard();
            });
        }
        editTypingText.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendEnteredText();
                return true;
            }
            return false;
        });

        // Draggable Floating Keyboard Action Button (FAB)
        setupDraggableFab();
    }

    private PcKeyboardDialog activePcKeyboardDialog = null;

    private void setupDraggableFab() {
        resetFabKeyboardFade();

        fabKeyboard.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            View parent = (View) v.getParent();
            if (parent != null && parent.getWidth() > 0 && parent.getHeight() > 0) {
                int margin = (int) (8 * getResources().getDisplayMetrics().density);
                float minX = margin;
                float maxX = Math.max(minX, parent.getWidth() - v.getWidth() - margin);
                float minY = margin;
                float maxY = Math.max(minY, parent.getHeight() - v.getHeight() - margin);
                float curX = v.getX();
                float curY = v.getY();
                if (curX < minX || curX > maxX || curY < minY || curY > maxY) {
                    v.setX(Math.max(minX, Math.min(curX, maxX)));
                    v.setY(Math.max(minY, Math.min(curY, maxY)));
                }
            }
        });

        fabKeyboard.setOnTouchListener(new View.OnTouchListener() {
            private float dX, dY;
            private float startX, startY;
            private static final int CLICK_DRAG_TOLERANCE = 10;

            @Override
            public boolean onTouch(View view, MotionEvent event) {
                resetFabKeyboardFade();
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        dX = view.getX() - event.getRawX();
                        dY = view.getY() - event.getRawY();
                        startX = event.getRawX();
                        startY = event.getRawY();
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        float targetX = event.getRawX() + dX;
                        float targetY = event.getRawY() + dY;
                        View parent = (View) view.getParent();
                        if (parent != null) {
                            int margin = (int) (8 * getResources().getDisplayMetrics().density);
                            float minX = margin;
                            float maxX = Math.max(minX, parent.getWidth() - view.getWidth() - margin);
                            float minY = margin;
                            float maxY = Math.max(minY, parent.getHeight() - view.getHeight() - margin);
                            targetX = Math.max(minX, Math.min(targetX, maxX));
                            targetY = Math.max(minY, Math.min(targetY, maxY));
                        }
                        view.setX(targetX);
                        view.setY(targetY);
                        return true;

                    case MotionEvent.ACTION_UP:
                        float diffX = Math.abs(event.getRawX() - startX);
                        float diffY = Math.abs(event.getRawY() - startY);
                        if (diffX < CLICK_DRAG_TOLERANCE && diffY < CLICK_DRAG_TOLERANCE) {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                            onKeyboardFabClicked();
                        }
                        resetFabKeyboardFade();
                        return true;

                    case MotionEvent.ACTION_CANCEL:
                        resetFabKeyboardFade();
                        return true;

                    default:
                        return false;
                }
            }
        });
    }

    private void onKeyboardFabClicked() {
        if (activePcKeyboardDialog != null && activePcKeyboardDialog.isShowing()) {
            activePcKeyboardDialog.dismiss();
            activePcKeyboardDialog = null;
        } else if (layoutTypingInput.getVisibility() == View.VISIBLE) {
            hideNormalKeyboard();
        } else {
            showNormalKeyboard();
        }
    }

    private void showNormalKeyboard() {
        if (activePcKeyboardDialog != null && activePcKeyboardDialog.isShowing()) {
            activePcKeyboardDialog.dismiss();
            activePcKeyboardDialog = null;
        }
        layoutTypingInput.setVisibility(View.VISIBLE);
        editTypingText.requestFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(editTypingText, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    private void hideNormalKeyboard() {
        layoutTypingInput.setVisibility(View.GONE);
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(editTypingText.getWindowToken(), 0);
        }
    }

    private void showPcKeyboard() {
        hideNormalKeyboard();
        if (activePcKeyboardDialog != null && activePcKeyboardDialog.isShowing()) {
            activePcKeyboardDialog.dismiss();
        }
        activePcKeyboardDialog = new PcKeyboardDialog(this, webSocketManager);
        activePcKeyboardDialog.setOnSwitchToNormalKeyboard(() -> {
            activePcKeyboardDialog = null;
            showNormalKeyboard();
        });
        activePcKeyboardDialog.setOnDismissListener(dialog -> {
            activePcKeyboardDialog = null;
        });
        activePcKeyboardDialog.show();
    }

    private void sendEnteredText() {
        String text = editTypingText.getText().toString();
        if (!text.isEmpty()) {
            if (text.length() > 25 || text.contains("\n") || text.contains("\r")) {
                webSocketManager.sendPaste(text);
            } else {
                webSocketManager.sendTypeText(text);
            }
            editTypingText.setText("");
        }
    }

    private void switchMode(boolean screenMode) {
        if (!screenMode && isFullscreen) {
            exitFullscreen();
        }
        this.isScreenMode = screenMode;
        touchpadView.setScreenMode(screenMode);
        if (screenMode) {
            tabScreen.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.accent_indigo)));
            tabScreen.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
            tabTrackpad.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.btn_surface)));
            tabTrackpad.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));

            imgScreenPreview.setVisibility(View.VISIBLE);
            txtTouchpadHint.setVisibility(View.GONE);
            if (!isFullscreen) {
                if (isScreenControlsCollapsed) {
                    if (layoutScreenControls != null) layoutScreenControls.setVisibility(View.GONE);
                    if (btnExpandScreenControls != null) {
                        btnExpandScreenControls.setVisibility(View.VISIBLE);
                        resetExpandScreenControlsFade();
                    }
                } else {
                    if (layoutScreenControls != null) layoutScreenControls.setVisibility(View.VISIBLE);
                    if (btnExpandScreenControls != null) {
                        btnExpandScreenControls.setVisibility(View.GONE);
                        handler.removeCallbacks(fadeExpandScreenControlsRunnable);
                    }
                }
                updateScreenControlsUi(touchpadView.getCurrentScale(), touchpadView.isFollowCursor());
            }
            webSocketManager.startScreenStream();
            webSocketManager.sendScreenFps(currentFps);
        } else {
            tabTrackpad.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.accent_indigo)));
            tabTrackpad.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
            tabScreen.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.btn_surface)));
            tabScreen.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));

            imgScreenPreview.setVisibility(View.GONE);
            if (layoutScreenControls != null) {
                layoutScreenControls.setVisibility(View.GONE);
            }
            if (btnExpandScreenControls != null) {
                btnExpandScreenControls.setVisibility(View.GONE);
                handler.removeCallbacks(fadeExpandScreenControlsRunnable);
            }
            txtTouchpadHint.setVisibility(View.VISIBLE);
            touchpadView.resetZoom();
            webSocketManager.stopScreenStream();
        }
    }

    private void enterFullscreen() {
        if (isFullscreen) return;
        isFullscreen = true;

        // Force landscape orientation
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);

        // Hide Android system bars (status bar, navigation bar) - Immersive Sticky
        WindowInsetsControllerCompat insetsController = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        if (insetsController != null) {
            insetsController.hide(WindowInsetsCompat.Type.systemBars());
            insetsController.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // Hide toolbars, tabs, mouse buttons, hints and portrait controls
        if (headerLayout != null) headerLayout.setVisibility(View.GONE);
        if (layoutTabs != null) layoutTabs.setVisibility(View.GONE);
        if (layoutMouseButtons != null) layoutMouseButtons.setVisibility(View.GONE);
        if (txtTouchpadHint != null) txtTouchpadHint.setVisibility(View.GONE);
        if (layoutScreenControls != null) layoutScreenControls.setVisibility(View.GONE);
        if (btnExpandScreenControls != null) btnExpandScreenControls.setVisibility(View.GONE);
        if (layoutTypingInput != null && layoutTypingInput.getVisibility() == View.VISIBLE) {
            hideNormalKeyboard();
        }

        // Show discrete top-right exit button & monitor selector
        if (isFullscreenControlsCollapsed) {
            if (layoutFullscreenTopBar != null) layoutFullscreenTopBar.setVisibility(View.GONE);
            if (btnExpandFullscreenControls != null) btnExpandFullscreenControls.setVisibility(View.VISIBLE);
        } else {
            if (btnExitFullscreen != null) btnExitFullscreen.setVisibility(View.VISIBLE);
            if (layoutFullscreenTopBar != null) {
                layoutFullscreenTopBar.setAlpha(1.0f);
                layoutFullscreenTopBar.setVisibility(View.VISIBLE);
            }
            if (btnExpandFullscreenControls != null) btnExpandFullscreenControls.setVisibility(View.GONE);
            resetFullscreenControlsFade();
        }

        // Expand containerMain to 0dp margins edge-to-edge with black background
        if (containerMain != null) {
            ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) containerMain.getLayoutParams();
            lp.setMargins(0, 0, 0, 0);
            containerMain.setLayoutParams(lp);
            containerMain.setBackgroundColor(Color.BLACK);
        }

        // Ensure FAB keyboard button is visible and accessible
        if (fabKeyboard != null) {
            fabKeyboard.setVisibility(View.VISIBLE);
        }

        if (touchpadView != null) {
            touchpadView.onEnterFullscreenLandscape();
        }
    }

    private void exitFullscreen() {
        if (!isFullscreen) return;
        isFullscreen = false;

        // Restore portrait orientation
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        // Restore Android system bars
        WindowInsetsControllerCompat insetsController = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        if (insetsController != null) {
            insetsController.show(WindowInsetsCompat.Type.systemBars());
        }
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // Restore toolbars and tabs
        if (headerLayout != null) headerLayout.setVisibility(View.VISIBLE);
        if (layoutTabs != null) layoutTabs.setVisibility(View.VISIBLE);
        if (layoutMouseButtons != null) layoutMouseButtons.setVisibility(View.VISIBLE);
        handler.removeCallbacks(fadeFullscreenControlsRunnable);
        if (layoutFullscreenTopBar != null) layoutFullscreenTopBar.setVisibility(View.GONE);
        if (btnExpandFullscreenControls != null) btnExpandFullscreenControls.setVisibility(View.GONE);

        // Restore containerMain 16dp margins and trackpad background
        if (containerMain != null) {
            int margin16 = dpToPx(16);
            ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) containerMain.getLayoutParams();
            lp.setMargins(margin16, margin16, margin16, margin16);
            containerMain.setLayoutParams(lp);
            containerMain.setBackground(ContextCompat.getDrawable(this, R.drawable.bg_trackpad));
        }

        // Restore view visibility according to current mode
        if (isScreenMode) {
            if (isScreenControlsCollapsed) {
                if (layoutScreenControls != null) layoutScreenControls.setVisibility(View.GONE);
                if (btnExpandScreenControls != null) {
                    btnExpandScreenControls.setVisibility(View.VISIBLE);
                    resetExpandScreenControlsFade();
                }
            } else {
                if (layoutScreenControls != null) layoutScreenControls.setVisibility(View.VISIBLE);
                if (btnExpandScreenControls != null) {
                    btnExpandScreenControls.setVisibility(View.GONE);
                    handler.removeCallbacks(fadeExpandScreenControlsRunnable);
                }
            }
            if (txtTouchpadHint != null) txtTouchpadHint.setVisibility(View.GONE);
        } else {
            if (layoutScreenControls != null) layoutScreenControls.setVisibility(View.GONE);
            if (btnExpandScreenControls != null) {
                btnExpandScreenControls.setVisibility(View.GONE);
                handler.removeCallbacks(fadeExpandScreenControlsRunnable);
            }
            if (txtTouchpadHint != null) txtTouchpadHint.setVisibility(View.VISIBLE);
        }

        if (touchpadView != null) {
            touchpadView.onExitFullscreen();
        }
    }

    @Override
    public void onConfigurationChanged(@androidx.annotation.NonNull android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (isFullscreen) {
            WindowInsetsControllerCompat insetsController = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
            if (insetsController != null) {
                insetsController.hide(WindowInsetsCompat.Type.systemBars());
                insetsController.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        }
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private void updateFpsUi() {
        runOnUiThread(() -> {
            if (btnFpsSelector != null) {
                btnFpsSelector.setText("⚡ " + currentFps + " FPS");
                int colorRes = R.color.accent_emerald;
                if (currentFps == 60) colorRes = R.color.accent_cyan;
                else if (currentFps == 2) colorRes = R.color.accent_amber;
                else if (currentFps == 15) colorRes = R.color.accent_indigo;
                btnFpsSelector.setTextColor(ContextCompat.getColor(this, colorRes));
            }
        });
    }

    private void updateScreenControlsUi(float scale, boolean followCursor) {
        runOnUiThread(() -> {
            View dividerFollow = findViewById(R.id.dividerFollow);
            if (btnToggleFollow != null) {
                if (scale <= 1.05f) {
                    btnToggleFollow.setVisibility(View.GONE);
                    if (dividerFollow != null) dividerFollow.setVisibility(View.GONE);
                } else {
                    btnToggleFollow.setVisibility(View.VISIBLE);
                    if (dividerFollow != null) dividerFollow.setVisibility(View.VISIBLE);
                    btnToggleFollow.setText(followCursor ? R.string.screen_follow_on : R.string.screen_follow_off);
                    btnToggleFollow.setTextColor(ContextCompat.getColor(this, followCursor ? R.color.accent_cyan : R.color.text_secondary));
                }
            }
            if (btnZoomLevel != null) {
                if (scale <= 1.05f) {
                    btnZoomLevel.setText(R.string.screen_reset_zoom);
                } else {
                    btnZoomLevel.setText(getString(R.string.screen_zoom_format, scale));
                }
            }
            updateFpsUi();
        });
    }

    private void handleMonitorsInfo(JsonObject json) {
        if (json == null) return;
        runOnUiThread(() -> {
            try {
                if (json.has("count")) {
                    monitorCount = Math.max(1, json.get("count").getAsInt());
                }
                if (json.has("active")) {
                    currentMonitorIndex = Math.max(0, json.get("active").getAsInt());
                }
                monitorNames.clear();
                if (json.has("monitors") && json.get("monitors").isJsonArray()) {
                    for (JsonElement el : json.getAsJsonArray("monitors")) {
                        JsonObject m = el.getAsJsonObject();
                        String name = m.has("name") ? m.get("name").getAsString() : "Display";
                        if (m.has("w") && m.has("h")) {
                            name += " (" + m.get("w").getAsInt() + "x" + m.get("h").getAsInt() + ")";
                        }
                        monitorNames.add(name);
                    }
                }
                updateMonitorButtonsUI();
            } catch (Exception ignored) {}
        });
    }

    private void updateMonitorButtonsUI() {
        runOnUiThread(() -> {
            String text;
            if (monitorCount > 1) {
                text = getString(R.string.monitor_format, (currentMonitorIndex + 1), monitorCount);
            } else {
                text = getString(R.string.monitor_single_format, (currentMonitorIndex + 1));
            }
            if (btnMonitorSelector != null) {
                btnMonitorSelector.setText(text);
            }
            if (btnFullscreenMonitor != null) {
                btnFullscreenMonitor.setText(text);
            }
        });
    }

    private void cycleOrSelectMonitor() {
        if (monitorCount <= 1) {
            Toast.makeText(this, getString(R.string.toast_single_monitor), Toast.LENGTH_SHORT).show();
            webSocketManager.sendGetMonitors();
            return;
        }
        int nextMonitor = (currentMonitorIndex + 1) % monitorCount;
        switchToMonitor(nextMonitor);
    }

    private void switchToMonitor(int index) {
        currentMonitorIndex = index;
        webSocketManager.sendSelectMonitor(index);
        updateMonitorButtonsUI();
        if (touchpadView != null) {
            touchpadView.resyncCursorFromServer();
        }
        Toast.makeText(this, getString(R.string.toast_monitor_switched, (index + 1)), Toast.LENGTH_SHORT).show();
    }

    private void showMonitorSelectionDialog() {
        if (monitorCount <= 1 && monitorNames.isEmpty()) {
            Toast.makeText(this, getString(R.string.toast_single_monitor), Toast.LENGTH_SHORT).show();
            webSocketManager.sendGetMonitors();
            return;
        }

        String[] items = new String[monitorCount];
        for (int i = 0; i < monitorCount; i++) {
            String name = (i < monitorNames.size()) ? monitorNames.get(i) : ("Display " + (i + 1));
            items[i] = (i == currentMonitorIndex ? "✓ " : "   ") + name;
        }

        new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_select_monitor_title)
                .setItems(items, (dialog, which) -> switchToMonitor(which))
                .setNegativeButton(R.string.btn_close, null)
                .show();
    }

    private void updateConnectionUi(boolean connected) {
        runOnUiThread(() -> {
            if (connected) {
                viewStatusDot.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.accent_emerald)));
                txtDeviceStatus.setText(R.string.status_connected);
                txtDeviceStatus.setTextColor(ContextCompat.getColor(this, R.color.accent_emerald));
            } else {
                viewStatusDot.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.accent_coral)));
                txtDeviceStatus.setText(R.string.status_disconnected);
                txtDeviceStatus.setTextColor(ContextCompat.getColor(this, R.color.accent_coral));
            }
        });
    }

    private void selectDevice(Device device) {
        if (device == null) return;
        this.currentDevice = device;
        txtDeviceName.setText(device.getDeviceName());
        txtDeviceStatus.setText(R.string.status_connecting);
        txtDeviceStatus.setTextColor(ContextCompat.getColor(this, R.color.accent_amber));
        viewStatusDot.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.accent_amber)));

        AppLogger.i("MainActivity", "Selected device: " + device.getDeviceName() + " (" + device.getHost() + ":" + device.getPort() + ")");

        // Save last connected host and port
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                .putString(KEY_LAST_DEVICE_HOST, device.getHost() + ":" + device.getPort())
                .apply();

        // Connect WebSocket (force clean reconnect)
        webSocketManager.connect(device, true);
    }

    private void startUdpDiscovery(boolean forceFresh) {
        if (!webSocketManager.isConnected()) {
            txtDeviceStatus.setText(R.string.status_searching);
            txtDeviceStatus.setTextColor(ContextCompat.getColor(this, R.color.accent_amber));
            viewStatusDot.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.accent_amber)));
        }
        AppLogger.i("MainActivity", "Triggering discovery scan (forceFresh: " + forceFresh + ")");

        if (forceFresh) {
            // Keep user-added remote devices, but refresh LAN discovered ones
            List<Device> preserved = new ArrayList<>();
            for (Device d : knownDevices) {
                if (d.isRemote()) preserved.add(d);
            }
            knownDevices.clear();
            knownDevices.addAll(preserved);
            if (deviceDialog != null && deviceDialog.isShowing()) {
                deviceDialog.updateDevices(knownDevices);
                deviceDialog.setStatusText("Scanning LAN via UDP 5051...");
            }
        }

        udpDiscovery.startDiscovery(3500, new UdpDiscovery.DiscoveryCallback() {
            @Override
            public void onDeviceFound(Device device) {
                boolean exists = false;
                for (int i = 0; i < knownDevices.size(); i++) {
                    if (knownDevices.get(i).getHost().equals(device.getHost())) {
                        knownDevices.set(i, device);
                        exists = true;
                        break;
                    }
                }
                if (!exists) {
                    knownDevices.add(device);
                }
                saveKnownDevices();

                if (deviceDialog != null && deviceDialog.isShowing()) {
                    deviceDialog.updateDevices(knownDevices);
                }

                // If not currently connected, auto-connect to discovered PC
                if (!webSocketManager.isConnected() && !webSocketManager.isConnecting()) {
                    selectDevice(device);
                }
            }

            @Override
            public void onDiscoveryFinished() {
                if (deviceDialog != null && deviceDialog.isShowing()) {
                    deviceDialog.setStatusText("Scan finished (" + knownDevices.size() + " devices found)");
                }
                runOnUiThread(() -> {
                    if (webSocketManager.isConnected()) {
                        updateConnectionUi(true);
                    } else if (webSocketManager.isConnecting()) {
                        txtDeviceStatus.setText(R.string.status_connecting);
                        txtDeviceStatus.setTextColor(ContextCompat.getColor(MainActivity.this, R.color.accent_amber));
                        viewStatusDot.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(MainActivity.this, R.color.accent_amber)));
                    } else {
                        updateConnectionUi(false);
                        if (currentDevice == null && !knownDevices.isEmpty()) {
                            selectDevice(knownDevices.get(0));
                        }
                    }
                });
            }
        });
    }

    private void startUdpDiscovery() {
        startUdpDiscovery(false);
    }

    private void showDeviceManagerDialog() {
        deviceDialog = new DeviceManagerDialog(this, knownDevices, new DeviceManagerDialog.OnDeviceSelectedListener() {
            @Override
            public void onDeviceSelected(Device device) {
                selectDevice(device);
            }

            @Override
            public void onDeviceDeleted(Device device) {
                knownDevices.remove(device);
                saveKnownDevices();
                AppLogger.i("MainActivity", "Deleted device: " + device.getHost() + ":" + device.getPort());
                if (currentDevice != null && currentDevice.getHost().equals(device.getHost())) {
                    webSocketManager.disconnect();
                    currentDevice = null;
                    txtDeviceName.setText(R.string.no_device_selected);
                    updateConnectionUi(false);
                }
            }

            @Override
            public void onDeviceRenamed(Device device, String newName) {
                device.setDeviceName(newName);
                saveKnownDevices();
                if (currentDevice != null && currentDevice.getHost().equals(device.getHost()) && currentDevice.getPort() == device.getPort()) {
                    currentDevice.setDeviceName(newName);
                    txtDeviceName.setText(newName);
                }
                Toast.makeText(MainActivity.this, getString(R.string.toast_device_renamed, newName), Toast.LENGTH_SHORT).show();
                renameDeviceOnPc(device, newName);
            }

            @Override
            public void onAddCustomDevice(Device device) {
                for (int i = knownDevices.size() - 1; i >= 0; i--) {
                    Device d = knownDevices.get(i);
                    if (d.getHost().equals(device.getHost()) && d.getPort() == device.getPort()) {
                        knownDevices.remove(i);
                    }
                }
                knownDevices.add(0, device);
                saveKnownDevices();
                AppLogger.i("MainActivity", "Added custom device: " + device.getHost() + ":" + device.getPort());
                selectDevice(device);
            }

            @Override
            public void onForceRescan() {
                startUdpDiscovery(true);
            }

            @Override
            public void onOpenLogs() {
                showLogViewerDialog();
            }
        });
        deviceDialog.show();
    }

    private void renameDeviceOnPc(Device device, String newName) {
        if (device == null || newName == null) return;
        if (webSocketManager.isConnected() && currentDevice != null && currentDevice.getHost().equals(device.getHost())) {
            webSocketManager.sendSetDeviceName(newName);
        }
        new Thread(() -> {
            try {
                okhttp3.MediaType JSON = okhttp3.MediaType.parse("application/json; charset=utf-8");
                JsonObject bodyJson = new JsonObject();
                bodyJson.addProperty("name", newName);
                okhttp3.RequestBody body = okhttp3.RequestBody.create(bodyJson.toString(), JSON);
                okhttp3.Request request = new okhttp3.Request.Builder()
                        .url(device.getHttpUrl() + "/api/device/name")
                        .post(body)
                        .build();
                new okhttp3.OkHttpClient().newCall(request).execute();
            } catch (Exception e) {
                AppLogger.e("MainActivity", "Failed to update device name on PC", e);
            }
        }).start();
    }

    private void showLogViewerDialog() {
        String serverUrl = currentDevice != null ? currentDevice.getHttpUrl() : "http://192.168.1.33:5050";
        LogViewerDialog dialog = new LogViewerDialog(this, serverUrl);
        dialog.show();
    }

    private void loadSavedDevices() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        currentFps = prefs.getInt(KEY_SCREEN_FPS, 30);
        updateFpsUi();

        String json = prefs.getString(KEY_SAVED_DEVICES, null);
        if (json != null) {
            try {
                Type listType = new TypeToken<List<Device>>() {}.getType();
                List<Device> list = gson.fromJson(json, listType);
                if (list != null) {
                    knownDevices.addAll(list);
                }
            } catch (Exception ignored) {}
        }

        String lastHost = prefs.getString(KEY_LAST_DEVICE_HOST, null);
        if (lastHost != null) {
            for (Device d : knownDevices) {
                String key = d.getHost() + ":" + d.getPort();
                if (key.equals(lastHost) || d.getHost().equals(lastHost)) {
                    selectDevice(d);
                    break;
                }
            }
        }
    }

    private void saveKnownDevices() {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                .putString(KEY_SAVED_DEVICES, gson.toJson(knownDevices))
                .apply();
    }

    @Override
    protected void onResume() {
        super.onResume();
        isAppInForeground = true;
        updateFpsUi();
        resetFabKeyboardFade();
        if (btnExpandScreenControls != null && btnExpandScreenControls.getVisibility() == View.VISIBLE && !isFullscreen) {
            resetExpandScreenControlsFade();
        }

        if (isFullscreen) {
            WindowInsetsControllerCompat insetsController = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
            if (insetsController != null) {
                insetsController.hide(WindowInsetsCompat.Type.systemBars());
                insetsController.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        }

        if (currentDevice != null) {
            if (!webSocketManager.isConnected()) {
                selectDevice(currentDevice);
            } else if (isScreenMode) {
                webSocketManager.startScreenStream();
                webSocketManager.sendScreenFps(currentFps);
            }
        } else {
            // Restore from SharedPreferences
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            String lastHost = prefs.getString(KEY_LAST_DEVICE_HOST, null);
            Device found = null;
            if (lastHost != null) {
                for (Device d : knownDevices) {
                    String key = d.getHost() + ":" + d.getPort();
                    if (key.equals(lastHost) || d.getHost().equals(lastHost)) {
                        found = d;
                        break;
                    }
                }
            }
            if (found != null) {
                selectDevice(found);
            } else if (!knownDevices.isEmpty()) {
                selectDevice(knownDevices.get(0));
            } else {
                startUdpDiscovery(true);
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        isAppInForeground = false;
        handler.removeCallbacks(reconnectRunnable);
        handler.removeCallbacks(fadeFabKeyboardRunnable);
        handler.removeCallbacks(fadeExpandScreenControlsRunnable);
        handler.removeCallbacks(fadeFullscreenControlsRunnable);
        if (isScreenMode && webSocketManager.isConnected()) {
            webSocketManager.stopScreenStream();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        udpDiscovery.stop();
        webSocketManager.disconnect();
        try {
            frameDecoderExecutor.shutdown();
        } catch (Exception ignored) {}
    }
}
