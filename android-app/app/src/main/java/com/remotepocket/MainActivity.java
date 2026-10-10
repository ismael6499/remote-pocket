package com.remotepocket;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
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

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.remotepocket.model.Device;
import com.remotepocket.network.UdpDiscovery;
import com.remotepocket.network.UpdateManager;
import com.remotepocket.network.WebSocketManager;
import com.remotepocket.ui.DeviceManagerDialog;
import com.remotepocket.ui.PcKeyboardDialog;
import com.remotepocket.ui.SystemSheet;
import com.remotepocket.ui.TouchpadView;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final String PREFS_NAME = "remote_pocket_prefs";
    private static final String KEY_SAVED_DEVICES = "saved_devices";
    private static final String KEY_LAST_DEVICE_HOST = "last_device_host";

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

        // Start initial auto-discovery on launch
        startUdpDiscovery();
    }

    private void initViews() {
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
    }

    private void initManagers() {
        webSocketManager = new WebSocketManager();
        touchpadView.setWebSocketManager(webSocketManager);
        touchpadView.setZoomTarget(imgScreenPreview);
        udpDiscovery = new UdpDiscovery(this);
        updateManager = new UpdateManager(this);

        webSocketManager.setConnectionListener(new WebSocketManager.ConnectionListener() {
            @Override
            public void onConnected() {
                updateConnectionUi(true);
                if (isScreenMode) {
                    webSocketManager.startScreenStream();
                }
                if (currentDevice != null) {
                    updateManager.checkForUpdates(currentDevice, true);
                }
            }

            @Override
            public void onDisconnected() {
                updateConnectionUi(false);
            }

            @Override
            public void onScreenFrame(byte[] jpegData) {
                if (isScreenMode && jpegData != null && jpegData.length > 0) {
                    try {
                        Bitmap bmp = BitmapFactory.decodeByteArray(jpegData, 0, jpegData.length);
                        if (bmp != null) {
                            imgScreenPreview.setImageBitmap(bmp);
                        }
                    } catch (Exception ignored) {}
                }
            }

            @Override
            public void onError(String error) {
                updateConnectionUi(false);
            }
        });
    }

    private boolean isManualDragActive = false;

    private void setupListeners() {
        // Device Selector Click
        findViewById(R.id.btnDeviceSelector).setOnClickListener(v -> showDeviceManagerDialog());

        // Refresh Discovery
        findViewById(R.id.btnRefreshDiscovery).setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            startUdpDiscovery();
        });

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

    private void setupDraggableFab() {
        fabKeyboard.setOnTouchListener(new View.OnTouchListener() {
            private float dX, dY;
            private float startX, startY;
            private static final int CLICK_DRAG_TOLERANCE = 10;

            @Override
            public boolean onTouch(View view, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        dX = view.getX() - event.getRawX();
                        dY = view.getY() - event.getRawY();
                        startX = event.getRawX();
                        startY = event.getRawY();
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        view.animate()
                                .x(event.getRawX() + dX)
                                .y(event.getRawY() + dY)
                                .setDuration(0)
                                .start();
                        return true;

                    case MotionEvent.ACTION_UP:
                        float diffX = Math.abs(event.getRawX() - startX);
                        float diffY = Math.abs(event.getRawY() - startY);
                        if (diffX < CLICK_DRAG_TOLERANCE && diffY < CLICK_DRAG_TOLERANCE) {
                            showKeyboardOptionsDialog();
                        }
                        return true;
                    default:
                        return false;
                }
            }
        });
    }

    private void showKeyboardOptionsDialog() {
        CharSequence[] options = new CharSequence[] {
                getString(R.string.keyboard_android),
                getString(R.string.keyboard_extended)
        };

        new AlertDialog.Builder(this)
                .setTitle(R.string.choose_keyboard)
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        // Show Android Gboard input bar
                        layoutTypingInput.setVisibility(View.VISIBLE);
                        editTypingText.requestFocus();
                        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                        if (imm != null) {
                            imm.showSoftInput(editTypingText, InputMethodManager.SHOW_IMPLICIT);
                        }
                    } else {
                        // Show extended PC keyboard dialog
                        layoutTypingInput.setVisibility(View.GONE);
                        PcKeyboardDialog pcKeyboard = new PcKeyboardDialog(this, webSocketManager);
                        pcKeyboard.show();
                    }
                })
                .show();
    }

    private void sendEnteredText() {
        String text = editTypingText.getText().toString();
        if (!text.isEmpty()) {
            webSocketManager.sendTypeText(text);
            editTypingText.setText("");
        }
    }

    private void switchMode(boolean screenMode) {
        this.isScreenMode = screenMode;
        if (screenMode) {
            tabScreen.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.accent_indigo)));
            tabScreen.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
            tabTrackpad.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.btn_surface)));
            tabTrackpad.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));

            imgScreenPreview.setVisibility(View.VISIBLE);
            txtTouchpadHint.setVisibility(View.GONE);
            webSocketManager.startScreenStream();
        } else {
            tabTrackpad.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.accent_indigo)));
            tabTrackpad.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
            tabScreen.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.btn_surface)));
            tabScreen.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));

            imgScreenPreview.setVisibility(View.GONE);
            txtTouchpadHint.setVisibility(View.VISIBLE);
            touchpadView.resetZoom();
            webSocketManager.stopScreenStream();
        }
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

        // Save last connected host
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                .putString(KEY_LAST_DEVICE_HOST, device.getHost())
                .apply();

        // Connect WebSocket
        webSocketManager.connect(device);
    }

    private void startUdpDiscovery() {
        txtDeviceStatus.setText(R.string.status_searching);
        txtDeviceStatus.setTextColor(ContextCompat.getColor(this, R.color.accent_amber));

        udpDiscovery.startDiscovery(3000, new UdpDiscovery.DiscoveryCallback() {
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
                    deviceDialog.setStatusText("Discovery completed (" + knownDevices.size() + " devices found)");
                }
                if (!webSocketManager.isConnected() && !webSocketManager.isConnecting() && currentDevice == null && !knownDevices.isEmpty()) {
                    selectDevice(knownDevices.get(0));
                }
            }
        });
    }

    private void showDeviceManagerDialog() {
        deviceDialog = new DeviceManagerDialog(this, knownDevices, new DeviceManagerDialog.OnDeviceSelectedListener() {
            @Override
            public void onDeviceSelected(Device device) {
                selectDevice(device);
            }

            @Override
            public void onAddCustomDevice(Device device) {
                knownDevices.add(device);
                saveKnownDevices();
                selectDevice(device);
            }
        });
        deviceDialog.show();
    }

    private void loadSavedDevices() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
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
                if (d.getHost().equals(lastHost)) {
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
        if (currentDevice != null) {
            if (!webSocketManager.isConnected() && !webSocketManager.isConnecting()) {
                webSocketManager.connect(currentDevice);
            }
        } else {
            // Restore from SharedPreferences
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            String lastHost = prefs.getString(KEY_LAST_DEVICE_HOST, null);
            if (lastHost != null) {
                for (Device d : knownDevices) {
                    if (d.getHost().equals(lastHost)) {
                        selectDevice(d);
                        break;
                    }
                }
            }
        }
        if (isScreenMode && webSocketManager.isConnected()) {
            webSocketManager.startScreenStream();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (isScreenMode) {
            webSocketManager.stopScreenStream();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        udpDiscovery.stop();
        webSocketManager.disconnect();
    }
}
