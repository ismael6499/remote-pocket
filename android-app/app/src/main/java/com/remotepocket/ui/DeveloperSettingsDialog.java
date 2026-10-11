package com.remotepocket.ui;

import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.Window;

import androidx.annotation.NonNull;

import com.google.android.material.switchmaterial.SwitchMaterial;
import com.remotepocket.R;

public class DeveloperSettingsDialog extends Dialog {

    public interface OnSettingsChangedListener {
        void onSettingsChanged();
    }

    private static final String PREFS_NAME = "remote_pocket_prefs";
    public static final String PREF_FORCE_SCREEN_OFF = "pref_force_screen_off";

    private final OnSettingsChangedListener listener;

    public DeveloperSettingsDialog(@NonNull Context context, OnSettingsChangedListener listener) {
        super(context);
        this.listener = listener;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.dialog_developer_settings);

        Window window = getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            window.setGravity(Gravity.BOTTOM);
        }

        findViewById(R.id.btnCloseDevSettings).setOnClickListener(v -> dismiss());

        SharedPreferences prefs = getContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SwitchMaterial switchForce = findViewById(R.id.switchForceScreenOff);

        boolean currentVal = prefs.getBoolean(PREF_FORCE_SCREEN_OFF, false);
        switchForce.setChecked(currentVal);

        switchForce.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean(PREF_FORCE_SCREEN_OFF, isChecked).apply();
            if (listener != null) {
                listener.onSettingsChanged();
            }
        });
    }
}
