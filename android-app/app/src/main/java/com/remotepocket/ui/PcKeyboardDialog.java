package com.remotepocket.ui;

import android.app.Dialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.HapticFeedbackConstants;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.remotepocket.R;
import com.remotepocket.network.WebSocketManager;

import java.util.ArrayList;
import java.util.List;

public class PcKeyboardDialog extends Dialog {
    private final WebSocketManager webSocketManager;
    private Vibrator vibrator;
    private Runnable onSwitchToNormalKeyboard;

    private boolean isCtrlActive = false;
    private boolean isAltActive = false;
    private boolean isShiftActive = false;
    private boolean isWinActive = false;
    private boolean isCapsLockActive = false;

    private MaterialButton btnModCtrl, btnModAlt, btnModShift, btnModWin, btnModCaps;
    private MaterialButton btnToggleSpecial;
    private LinearLayout layoutSpecialFunctions;

    private final List<MaterialButton> letterButtons = new ArrayList<>();

    public PcKeyboardDialog(@NonNull Context context, WebSocketManager webSocketManager) {
        super(context);
        this.webSocketManager = webSocketManager;
    }

    public void setOnSwitchToNormalKeyboard(Runnable runnable) {
        this.onSwitchToNormalKeyboard = runnable;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.dialog_pc_keyboard);

        vibrator = (Vibrator) getContext().getSystemService(Context.VIBRATOR_SERVICE);

        Window window = getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            window.setGravity(Gravity.BOTTOM);
            window.setFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL, WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL);
            window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        }

        // Header controls
        findViewById(R.id.btnCloseKeyboard).setOnClickListener(v -> dismiss());
        findViewById(R.id.btnEscHeader).setOnClickListener(v -> {
            triggerHaptic(v);
            sendKeyWithModifiers("esc");
        });

        View btnSwitchToNormal = findViewById(R.id.btnSwitchToNormal);
        if (btnSwitchToNormal != null) {
            btnSwitchToNormal.setOnClickListener(v -> {
                triggerHaptic(v);
                dismiss();
                if (onSwitchToNormalKeyboard != null) {
                    onSwitchToNormalKeyboard.run();
                }
            });
        }

        // Special section toggle
        btnToggleSpecial = findViewById(R.id.btnToggleSpecial);
        layoutSpecialFunctions = findViewById(R.id.layoutSpecialFunctions);
        btnToggleSpecial.setOnClickListener(v -> {
            triggerHaptic(v);
            toggleSpecialSection();
        });

        // Setup special panel actions
        setupSpecialPanel();

        // Modifiers & Caps
        btnModCtrl = findViewById(R.id.key_ctrl);
        btnModWin = findViewById(R.id.key_win);
        btnModAlt = findViewById(R.id.key_alt);
        btnModShift = findViewById(R.id.key_shift);
        btnModCaps = findViewById(R.id.key_caps);

        btnModCtrl.setOnClickListener(v -> {
            triggerHaptic(v);
            toggleModifier("ctrl");
        });
        btnModWin.setOnClickListener(v -> {
            triggerHaptic(v);
            toggleModifier("win");
        });
        btnModAlt.setOnClickListener(v -> {
            triggerHaptic(v);
            toggleModifier("alt");
        });
        btnModShift.setOnClickListener(v -> {
            triggerHaptic(v);
            toggleModifier("shift");
        });
        btnModCaps.setOnClickListener(v -> {
            triggerHaptic(v);
            toggleCapsLock();
        });

        // Row 1: Numbers & Symbols & Backspace
        setupKey(R.id.key_1, "1");
        setupKey(R.id.key_2, "2");
        setupKey(R.id.key_3, "3");
        setupKey(R.id.key_4, "4");
        setupKey(R.id.key_5, "5");
        setupKey(R.id.key_6, "6");
        setupKey(R.id.key_7, "7");
        setupKey(R.id.key_8, "8");
        setupKey(R.id.key_9, "9");
        setupKey(R.id.key_0, "0");
        setupKey(R.id.key_minus, "-");
        setupKey(R.id.key_equal, "=");
        setupKey(R.id.key_bksp, "backspace");

        // Row 2: Tab, QWERTY, Brackets
        setupKey(R.id.key_tab, "tab");
        setupLetterKey(R.id.key_q, "q");
        setupLetterKey(R.id.key_w, "w");
        setupLetterKey(R.id.key_e, "e");
        setupLetterKey(R.id.key_r, "r");
        setupLetterKey(R.id.key_t, "t");
        setupLetterKey(R.id.key_y, "y");
        setupLetterKey(R.id.key_u, "u");
        setupLetterKey(R.id.key_i, "i");
        setupLetterKey(R.id.key_o, "o");
        setupLetterKey(R.id.key_p, "p");
        setupKey(R.id.key_bracket_left, "[");
        setupKey(R.id.key_bracket_right, "]");

        // Row 3: ASDF, Semicolon, Quote, Enter
        setupLetterKey(R.id.key_a, "a");
        setupLetterKey(R.id.key_s, "s");
        setupLetterKey(R.id.key_d, "d");
        setupLetterKey(R.id.key_f, "f");
        setupLetterKey(R.id.key_g, "g");
        setupLetterKey(R.id.key_h, "h");
        setupLetterKey(R.id.key_j, "j");
        setupLetterKey(R.id.key_k, "k");
        setupLetterKey(R.id.key_l, "l");
        setupKey(R.id.key_semicolon, ";");
        setupKey(R.id.key_quote, "'");
        setupKey(R.id.key_enter, "enter");

        // Row 4: Backslash, ZXCV, Punctuation, Arrow Up
        setupKey(R.id.key_backslash, "\\");
        setupLetterKey(R.id.key_z, "z");
        setupLetterKey(R.id.key_x, "x");
        setupLetterKey(R.id.key_c, "c");
        setupLetterKey(R.id.key_v, "v");
        setupLetterKey(R.id.key_b, "b");
        setupLetterKey(R.id.key_n, "n");
        setupLetterKey(R.id.key_m, "m");
        setupKey(R.id.key_comma, ",");
        setupKey(R.id.key_period, ".");
        setupKey(R.id.key_slash, "/");
        setupKey(R.id.key_arrow_up, "up");

        // Row 5: Space & Left/Down/Right Arrows
        setupKey(R.id.key_space, "space");
        setupKey(R.id.key_arrow_left, "left");
        setupKey(R.id.key_arrow_down, "down");
        setupKey(R.id.key_arrow_right, "right");
    }

    private void triggerHaptic(View v) {
        if (v != null) {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        }
        if (vibrator != null && vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(22, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(22);
            }
        }
    }

    private void toggleSpecialSection() {
        if (layoutSpecialFunctions.getVisibility() == View.VISIBLE) {
            layoutSpecialFunctions.setVisibility(View.GONE);
            btnToggleSpecial.setText(R.string.toggle_special_keys);
            btnToggleSpecial.setTextColor(ContextCompat.getColor(getContext(), R.color.accent_indigo));
            btnToggleSpecial.setStrokeColor(ColorStateList.valueOf(ContextCompat.getColor(getContext(), R.color.accent_indigo)));
        } else {
            layoutSpecialFunctions.setVisibility(View.VISIBLE);
            btnToggleSpecial.setText(R.string.toggle_special_keys_hide);
            btnToggleSpecial.setTextColor(ContextCompat.getColor(getContext(), R.color.accent_emerald));
            btnToggleSpecial.setStrokeColor(ColorStateList.valueOf(ContextCompat.getColor(getContext(), R.color.accent_emerald)));
        }
    }

    private void setupSpecialPanel() {
        // Direct Action Shortcuts
        findViewById(R.id.btnShortcutCtrlAltDel).setOnClickListener(v -> {
            triggerHaptic(v);
            sendKey("ctrl+alt+del");
        });
        findViewById(R.id.btnShortcutAltTab).setOnClickListener(v -> {
            triggerHaptic(v);
            sendKey("alt+tab");
        });
        findViewById(R.id.btnShortcutWinD).setOnClickListener(v -> {
            triggerHaptic(v);
            sendKey("win+d");
        });
        findViewById(R.id.btnShortcutTaskMgr).setOnClickListener(v -> {
            triggerHaptic(v);
            sendKey("ctrl+shift+esc");
        });
        findViewById(R.id.btnShortcutWinL).setOnClickListener(v -> {
            triggerHaptic(v);
            sendKey("win+l");
        });
        findViewById(R.id.btnShortcutCopy).setOnClickListener(v -> {
            triggerHaptic(v);
            sendKey("ctrl+c");
        });
        findViewById(R.id.btnShortcutPaste).setOnClickListener(v -> {
            triggerHaptic(v);
            sendKey("ctrl+v");
        });

        // Extended Nav Keys
        findViewById(R.id.btnIns).setOnClickListener(v -> {
            triggerHaptic(v);
            sendKeyWithModifiers("ins");
        });
        findViewById(R.id.btnDel).setOnClickListener(v -> {
            triggerHaptic(v);
            sendKeyWithModifiers("delete");
        });
        findViewById(R.id.btnHome).setOnClickListener(v -> {
            triggerHaptic(v);
            sendKeyWithModifiers("home");
        });
        findViewById(R.id.btnEnd).setOnClickListener(v -> {
            triggerHaptic(v);
            sendKeyWithModifiers("end");
        });
        findViewById(R.id.btnPgUp).setOnClickListener(v -> {
            triggerHaptic(v);
            sendKeyWithModifiers("pageup");
        });
        findViewById(R.id.btnPgDn).setOnClickListener(v -> {
            triggerHaptic(v);
            sendKeyWithModifiers("pagedown");
        });
        findViewById(R.id.btnPrtScn).setOnClickListener(v -> {
            triggerHaptic(v);
            sendKeyWithModifiers("prtscn");
        });

        // F1 - F12 Keys
        LinearLayout layoutFKeys = findViewById(R.id.layoutFKeys);
        for (int i = 1; i <= 12; i++) {
            final String fKey = "f" + i;
            Button btnF = new Button(getContext());
            btnF.setText("F" + i);
            btnF.setTextSize(11f);
            btnF.setTextColor(ContextCompat.getColor(getContext(), R.color.text_primary));
            btnF.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(getContext(), R.color.btn_surface)));

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dpToPx(46), dpToPx(34));
            lp.setMargins(0, 0, dpToPx(4), 0);
            btnF.setLayoutParams(lp);

            btnF.setOnClickListener(v -> {
                triggerHaptic(v);
                sendKeyWithModifiers(fKey);
            });
            layoutFKeys.addView(btnF);
        }
    }

    private void setupKey(int resId, String keyName) {
        View view = findViewById(resId);
        if (view != null) {
            view.setOnClickListener(v -> {
                triggerHaptic(v);
                sendKeyWithModifiers(keyName);
            });
        }
    }

    private void setupLetterKey(int resId, String letter) {
        MaterialButton btn = findViewById(resId);
        if (btn != null) {
            letterButtons.add(btn);
            btn.setOnClickListener(v -> {
                triggerHaptic(v);
                sendKeyWithModifiers(letter);
            });
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

    private void toggleCapsLock() {
        isCapsLockActive = !isCapsLockActive;
        updateButtonVisual(btnModCaps, isCapsLockActive);
        sendKey("capslock");

        // Reflect visual uppercase / lowercase across letter keycaps
        for (MaterialButton btn : letterButtons) {
            CharSequence text = btn.getText();
            if (text != null && text.length() == 1) {
                btn.setText(isCapsLockActive ? text.toString().toUpperCase() : text.toString().toLowerCase());
            }
        }
    }

    private void updateButtonVisual(MaterialButton btn, boolean active) {
        if (btn == null) return;
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

        // Reset temporary modifier latches after dispatching
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
