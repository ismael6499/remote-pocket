package com.remotepocket.ui;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.EditText;
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
        void onAddCustomDevice(Device device);
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

        findViewById(R.id.btnAddRemoteDevice).setOnClickListener(v -> showAddDeviceDialog());

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
            emptyView.setPadding(0, 16, 0, 16);
            layoutDeviceList.addView(emptyView);
            return;
        }

        for (Device d : devices) {
            View itemView = LayoutInflater.from(getContext()).inflate(android.R.layout.simple_list_item_2, layoutDeviceList, false);
            TextView text1 = itemView.findViewById(android.R.id.text1);
            TextView text2 = itemView.findViewById(android.R.id.text2);

            text1.setText(d.getDeviceName());
            text1.setTextColor(getContext().getColor(R.color.text_primary));
            text1.setTextSize(15f);

            text2.setText(d.getHost() + ":" + d.getPort() + (d.isRemote() ? " (Remote)" : " (LAN)"));
            text2.setTextColor(getContext().getColor(R.color.text_secondary));
            text2.setTextSize(12f);

            itemView.setBackgroundResource(R.drawable.bg_btn_rounded);
            itemView.setPadding(32, 20, 32, 20);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, 0, 16);
            itemView.setLayoutParams(lp);

            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onDeviceSelected(d);
                }
                dismiss();
            });

            layoutDeviceList.addView(itemView);
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
                    String name = editName.getText().toString().trim();
                    String host = editHost.getText().toString().trim();
                    String portStr = editPort.getText().toString().trim();
                    int port = 5050;
                    try { port = Integer.parseInt(portStr); } catch (Exception ignored) {}

                    if (!host.isEmpty()) {
                        Device dev = new Device(name.isEmpty() ? host : name, host, port, true, false);
                        if (listener != null) {
                            listener.onAddCustomDevice(dev);
                        }
                        dismiss();
                    }
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }
}
