package com.remotepocket.ui;

import android.app.Dialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.remotepocket.R;
import com.remotepocket.network.WebSocketManager;

public class PcKeyboardDialog extends Dialog {
    private final WebSocketManager webSocketManager;

    private boolean isCtrlActive = false;
    private boolean isAltActive = false;
    private boolean isShiftActive = false;
    private boolean isWinActive = false;

    private MaterialButton btnModCtrl, btnModAlt, btnModShift, btnModWin;

    public PcKeyboardDialog(@NonNull Context context, WebSocketManager webSocketManager) {
        super(context);
        this.webSocketManager = webSocketManager;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.dialog_pc_keyboard);

        Window window = getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            window.setGravity(Gravity.BOTTOM);
        }

        findViewById(R.id.btnCloseKeyboard).setOnClickListener(v -> dismiss());

        // Modifiers
        btnModCtrl = findViewById(R.id.btnModCtrl);
        btnModAlt = findViewById(R.id.btnModAlt);
        btnModShift = findViewById(R.id.btnModShift);
        btnModWin = findViewById(R.id.btnModWin);

        btnModCtrl.setOnClickListener(v -> toggleModifier("ctrl"));
        btnModAlt.setOnClickListener(v -> toggleModifier("alt"));
        btnModShift.setOnClickListener(v -> toggleModifier("shift"));
        btnModWin.setOnClickListener(v -> toggleModifier("win"));

        // Direct Shortcuts
        findViewById(R.id.btnShortcutAltTab).setOnClickListener(v -> sendKey("alt+tab"));
        findViewById(R.id.btnShortcutWinD).setOnClickListener(v -> sendKey("win+d"));
        findViewById(R.id.btnShortcutTaskMgr).setOnClickListener(v -> sendKey("ctrl+shift+esc"));
        findViewById(R.id.btnShortcutCopy).setOnClickListener(v -> sendKey("ctrl+c"));
        findViewById(R.id.btnShortcutPaste).setOnClickListener(v -> sendKey("ctrl+v"));

        // Standard Keys
        findViewById(R.id.btnEsc).setOnClickListener(v -> sendKeyWithModifiers("esc"));
        findViewById(R.id.btnTab).setOnClickListener(v -> sendKeyWithModifiers("tab"));
        findViewById(R.id.btnEnter).setOnClickListener(v -> sendKeyWithModifiers("enter"));
        findViewById(R.id.btnBksp).setOnClickListener(v -> sendKeyWithModifiers("backspace"));
        findViewById(R.id.btnHome).setOnClickListener(v -> sendKeyWithModifiers("home"));
        findViewById(R.id.btnEnd).setOnClickListener(v -> sendKeyWithModifiers("end"));
        findViewById(R.id.btnDel).setOnClickListener(v -> sendKeyWithModifiers("delete"));
        findViewById(R.id.btnPgUp).setOnClickListener(v -> sendKeyWithModifiers("pageup"));
        findViewById(R.id.btnPgDn).setOnClickListener(v -> sendKeyWithModifiers("pagedown"));

        // Arrows
        findViewById(R.id.btnArrowLeft).setOnClickListener(v -> sendKeyWithModifiers("left"));
        findViewById(R.id.btnArrowRight).setOnClickListener(v -> sendKeyWithModifiers("right"));
        findViewById(R.id.btnArrowUp).setOnClickListener(v -> sendKeyWithModifiers("up"));
        findViewById(R.id.btnArrowDown).setOnClickListener(v -> sendKeyWithModifiers("down"));

        // F1 - F12 Keys
        LinearLayout layoutFKeys = findViewById(R.id.layoutFKeys);
        for (int i = 1; i <= 12; i++) {
            final String fKey = "f" + i;
            Button btnF = new Button(getContext());
            btnF.setText("F" + i);
            btnF.setTextSize(11f);
            btnF.setTextColor(ContextCompat.getColor(getContext(), R.color.text_primary));
            btnF.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(getContext(), R.color.btn_surface)));

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dpToPx(48), dpToPx(38));
            lp.setMargins(0, 0, dpToPx(4), 0);
            btnF.setLayoutParams(lp);

            btnF.setOnClickListener(v -> sendKeyWithModifiers(fKey));
            layoutFKeys.addView(btnF);
        }
    }

    private void toggleModifier(String mod) {
        switch (mod) {
            case "ctrl":
                isCtrlActive = !isCtrlActive;
                updateButtonVisual(btnModCtrl, isCtrlActive);
                break;
            case "alt":
                isAltActive = !isAltActive;
                updateButtonVisual(btnModAlt, isAltActive);
                break;
            case "shift":
                isShiftActive = !isShiftActive;
                updateButtonVisual(btnModShift, isShiftActive);
                break;
            case "win":
                isWinActive = !isWinActive;
                updateButtonVisual(btnModWin, isWinActive);
                break;
        }
    }

    private void updateButtonVisual(MaterialButton btn, boolean active) {
        int color = ContextCompat.getColor(getContext(), active ? R.color.accent_indigo : R.color.btn_surface);
        btn.setBackgroundTintList(ColorStateList.valueOf(color));
    }

    private void sendKey(String keyExpr) {
        if (webSocketManager != null) {
            webSocketManager.sendKeyPress(keyExpr);
        }
    }

    private void sendKeyWithModifiers(String baseKey) {
        StringBuilder sb = new StringBuilder();
        if (isCtrlActive) sb.append("ctrl+");
        if (isAltActive) sb.append("alt+");
        if (isShiftActive) sb.append("shift+");
        if (isWinActive) sb.append("win+");
        sb.append(baseKey);

        sendKey(sb.toString());

        // Reset modifier latches after dispatch
        if (isCtrlActive || isAltActive || isShiftActive || isWinActive) {
            isCtrlActive = false;
            isAltActive = false;
            isShiftActive = false;
            isWinActive = false;
            updateButtonVisual(btnModCtrl, false);
            updateButtonVisual(btnModAlt, false);
            updateButtonVisual(btnModShift, false);
            updateButtonVisual(btnModWin, false);
        }
    }

    private int dpToPx(int dp) {
        float density = getContext().getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }
}
