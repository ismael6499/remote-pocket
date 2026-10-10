package com.remotepocket.ui;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.remotepocket.R;
import com.remotepocket.model.Device;

import java.util.ArrayList;
import java.util.List;

public class DeviceManagerDialog extends Dialog {
    public interface OnDeviceSelectedListener {
        void onDeviceSelected(Device device);
        void onDeviceDeleted(Device device);
        void onAddCustomDevice(Device device);
        void onForceRescan();
        void onOpenLogs();
    }

    private final List<Device> devices = new ArrayList<>();
    private final OnDeviceSelectedListener listener;
    private LinearLayout layoutDeviceList;
    private TextView txtDiscoveryStatus;

    public DeviceManagerDialog(@NonNull Context context, List<Device> initialDevices, OnDeviceSelectedListener listener) {
        super(context);
        if (initialDevices != null) {
            this.devices.addAll(initialDevices);
        }
        this.listener = listener;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.dialog_device_manager);

        Window window = getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        layoutDeviceList = findViewById(R.id.layoutDeviceList);
        txtDiscoveryStatus = findViewById(R.id.txtDiscoveryStatus);

        findViewById(R.id.btnForceRescan).setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            if (listener != null) {
                listener.onForceRescan();
            }
        });

        findViewById(R.id.btnAddRemoteDevice).setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            showAddDeviceDialog();
        });

        findViewById(R.id.btnViewLogs).setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            if (listener != null) {
                listener.onOpenLogs();
            }
        });

        renderDevices();
    }

    public void updateDevices(List<Device> newDevices) {
        this.devices.clear();
        if (newDevices != null) {
            this.devices.addAll(newDevices);
        }
        if (layoutDeviceList != null) {
            renderDevices();
        }
    }

    public void setStatusText(String status) {
        if (txtDiscoveryStatus != null) {
            txtDiscoveryStatus.setText(status);
        }
    }

    private void renderDevices() {
        layoutDeviceList.removeAllViews();
        if (devices.isEmpty()) {
            TextView emptyView = new TextView(getContext());
            emptyView.setText(R.string.status_searching);
            emptyView.setTextColor(getContext().getColor(R.color.text_muted));
            emptyView.setPadding(0, 20, 0, 20);
            layoutDeviceList.addView(emptyView);
            return;
        }

        for (Device d : devices) {
            LinearLayout row = new LinearLayout(getContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            row.setBackgroundResource(R.drawable.bg_btn_rounded);
            row.setPadding(28, 18, 20, 18);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, 0, 14);
            row.setLayoutParams(lp);

            // Left text block (clickable)
            LinearLayout textBlock = new LinearLayout(getContext());
            textBlock.setOrientation(LinearLayout.VERTICAL);
            textBlock.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

            TextView title = new TextView(getContext());
            title.setText(d.getDeviceName());
            title.setTextColor(getContext().getColor(R.color.text_primary));
            title.setTextSize(15f);
            title.setTypeface(null, android.graphics.Typeface.BOLD);
            textBlock.addView(title);

            TextView sub = new TextView(getContext());
            boolean isLive = (System.currentTimeMillis() - d.getLastSeen()) < 15000;
            String badge = isLive ? " • " + getContext().getString(R.string.badge_discovered_udp) : "";
            sub.setText(d.getHost() + ":" + d.getPort() + badge);
            sub.setTextColor(getContext().getColor(isLive ? R.color.accent_emerald : R.color.text_secondary));
            sub.setTextSize(11f);
            textBlock.addView(sub);

            View.OnClickListener onSelectClick = v -> {
                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                if (listener != null) {
                    listener.onDeviceSelected(d);
                }
                dismiss();
            };
            row.setOnClickListener(onSelectClick);
            textBlock.setOnClickListener(onSelectClick);

            row.addView(textBlock);

            // Delete button
            TextView btnDelete = new TextView(getContext());
            btnDelete.setText("✕");
            btnDelete.setTextSize(16f);
            btnDelete.setTextColor(getContext().getColor(R.color.text_muted));
            btnDelete.setPadding(24, 16, 16, 16);
            btnDelete.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                if (listener != null) {
                    listener.onDeviceDeleted(d);
                }
                devices.remove(d);
                renderDevices();
            });

            row.addView(btnDelete);
            layoutDeviceList.addView(row);
        }
    }

    private void showAddDeviceDialog() {
        LinearLayout layout = new LinearLayout(getContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 24, 48, 24);

        EditText editName = new EditText(getContext());
        editName.setHint(R.string.dialog_device_name_hint);
        editName.setTextColor(getContext().getColor(R.color.text_primary));
        editName.setHintTextColor(getContext().getColor(R.color.text_muted));
        layout.addView(editName);

        EditText editHost = new EditText(getContext());
        editHost.setHint(R.string.dialog_device_host_hint);
        editHost.setTextColor(getContext().getColor(R.color.text_primary));
        editHost.setHintTextColor(getContext().getColor(R.color.text_muted));
        layout.addView(editHost);

        EditText editPort = new EditText(getContext());
        editPort.setHint(R.string.dialog_device_port_hint);
        editPort.setText("5050");
        editPort.setTextColor(getContext().getColor(R.color.text_primary));
        editPort.setHintTextColor(getContext().getColor(R.color.text_muted));
        layout.addView(editPort);

        new AlertDialog.Builder(getContext())
                .setTitle(R.string.dialog_add_device_title)
                .setView(layout)
                .setPositiveButton(R.string.action_save, (dialog, which) -> {
                    try {
                        String name = editName.getText().toString().trim();
                        String host = editHost.getText().toString().trim();
                        String portStr = editPort.getText().toString().trim();
                        int port = 5050;
                        try { port = Integer.parseInt(portStr); } catch (Exception ignored) {}

                        if (host.startsWith("http://")) host = host.substring(7);
                        else if (host.startsWith("https://")) host = host.substring(8);
                        else if (host.startsWith("ws://")) host = host.substring(5);
                        else if (host.startsWith("wss://")) host = host.substring(6);

                        while (host.endsWith("/")) host = host.substring(0, host.length() - 1).trim();

                        if (host.contains(":")) {
                            int colon = host.lastIndexOf(':');
                            String p = host.substring(colon + 1).trim();
                            host = host.substring(0, colon).trim();
                            try {
                                int parsedP = Integer.parseInt(p);
                                if (parsedP > 0 && parsedP <= 65535) port = parsedP;
                            } catch (Exception ignored) {}
                        }

                        if (!host.isEmpty()) {
                            Device dev = new Device(name.isEmpty() ? host : name, host, port, true, false);
                            if (listener != null) {
                                listener.onAddCustomDevice(dev);
                            }
                            dismiss();
                        }
                    } catch (Exception e) {
                        com.remotepocket.util.AppLogger.e("DeviceManagerDialog", "Failed to add device", e);
                    }
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }
}
