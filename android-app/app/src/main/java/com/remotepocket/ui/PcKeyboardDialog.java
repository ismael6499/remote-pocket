package com.remotepocket.ui;

import android.app.Dialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.text.Html;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;

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

    private final Handler repeatHandler = new Handler(Looper.getMainLooper());
    private static final int INITIAL_REPEAT_DELAY_MS = 380;
    private static final int REPEAT_INTERVAL_MS = 65;

    // Modifiers State
    private boolean isCtrlActive = false;
    private boolean isAltActive = false;
    private boolean isShiftActive = false;
    private boolean isWinActive = false;
    private boolean isCapsLockActive = false;
    private boolean isAltGrActive = false;

    // Layout: "ESP" (Spanish - Traditional / Latin America ISO) or "ENG" (US English)
    private String currentLayout = "ESP";

    private MaterialButton btnModCtrl, btnModCtrlRight, btnModAlt, btnModWin;
    private MaterialButton btnModShift, btnModShiftRight, btnModCaps, btnModAltGr;
    private MaterialButton btnModFn, btnLayoutToggle;
    private MaterialButton btnToggleSpecial;
    private LinearLayout layoutSpecialFunctions;

    // Key definition holder
    private static class KeyDef {
        final int resId;
        final String espNormalLabel, espShiftLabel, espAltGrLabel;
        final String espNormalVal, espShiftVal, espAltGrVal;
        final String engNormalLabel, engShiftLabel;
        final String engNormalVal, engShiftVal;
        final boolean isLetter;
        MaterialButton button;

        KeyDef(int resId,
               String espNormalLabel, String espShiftLabel, String espAltGrLabel,
               String espNormalVal, String espShiftVal, String espAltGrVal,
               String engNormalLabel, String engShiftLabel,
               String engNormalVal, String engShiftVal,
               boolean isLetter) {
            this.resId = resId;
            this.espNormalLabel = espNormalLabel;
            this.espShiftLabel = espShiftLabel;
            this.espAltGrLabel = espAltGrLabel;
            this.espNormalVal = espNormalVal;
            this.espShiftVal = espShiftVal;
            this.espAltGrVal = espAltGrVal;
            this.engNormalLabel = engNormalLabel;
            this.engShiftLabel = engShiftLabel;
            this.engNormalVal = engNormalVal;
            this.engShiftVal = engShiftVal;
            this.isLetter = isLetter;
        }

        static KeyDef letter(int resId, String letter, String espAltGrLabel, String espAltGrVal) {
            return new KeyDef(
                    resId,
                    letter.toLowerCase(), letter.toUpperCase(), espAltGrLabel,
                    letter.toLowerCase(), letter.toUpperCase(), espAltGrVal,
                    letter.toLowerCase(), letter.toUpperCase(),
                    letter.toLowerCase(), letter.toUpperCase(),
                    true
            );
        }

        static KeyDef letter(int resId, String letter) {
            return letter(resId, letter, null, null);
        }

        static KeyDef symbol(int resId,
                             String espNorm, String espShift, String espAltGr,
                             String engNorm, String engShift) {
            return new KeyDef(
                    resId,
                    espNorm, espShift, espAltGr,
                    espNorm, espShift, espAltGr,
                    engNorm, engShift,
                    engNorm, engShift,
                    false
            );
        }
    }

    private final List<KeyDef> keys = new ArrayList<>();

    public PcKeyboardDialog(@NonNull Context context, WebSocketManager webSocketManager) {
        super(context);
        this.webSocketManager = webSocketManager;
        initKeyDefinitions();
    }

    public void setDetectedLayout(String layout) {
        if (layout != null) {
            String l = layout.trim().toUpperCase();
            if (l.startsWith("ES") || l.contains("SPANISH")) {
                currentLayout = "ESP";
            } else if (l.startsWith("EN") || l.contains("US") || l.contains("ENGLISH")) {
                currentLayout = "ENG";
            }
        }
        if (btnLayoutToggle != null) {
            btnLayoutToggle.setText(currentLayout);
            refreshAllKeycaps();
        }
    }

    public void setOnSwitchToNormalKeyboard(Runnable runnable) {
        this.onSwitchToNormalKeyboard = runnable;
    }

    private void compactHeights(View root, float factor) {
        if (root == null) return;
        ViewGroup.LayoutParams lp = root.getLayoutParams();
        if (lp != null && lp.height > 0 && !(root instanceof ViewGroup && ((ViewGroup) root).getChildCount() > 0)) {
            float min = 24f * getContext().getResources().getDisplayMetrics().density;
            lp.height = Math.max((int) min, Math.round(lp.height * factor));
            root.setLayoutParams(lp);
        }
        if (root instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) root;
            for (int i = 0; i < g.getChildCount(); i++) {
                compactHeights(g.getChildAt(i), factor);
            }
        }
    }

    private void initKeyDefinitions() {
        keys.clear();

        // Row 1: Esc, º, 1, 2, 3, 4, 5, 6, 7, 8, 9, 0, ', ¡
        keys.add(KeyDef.symbol(R.id.key_ord, "º", "ª", "\\", "`", "~"));
        keys.add(KeyDef.symbol(R.id.key_1, "1", "!", "|", "1", "!"));
        keys.add(KeyDef.symbol(R.id.key_2, "2", "\"", "@", "2", "@"));
        keys.add(KeyDef.symbol(R.id.key_3, "3", "·", "#", "3", "#"));
        keys.add(KeyDef.symbol(R.id.key_4, "4", "$", "~", "4", "$"));
        keys.add(KeyDef.symbol(R.id.key_5, "5", "%", "€", "5", "%"));
        keys.add(KeyDef.symbol(R.id.key_6, "6", "&", "¬", "6", "^"));
        keys.add(KeyDef.symbol(R.id.key_7, "7", "/", null, "7", "&"));
        keys.add(KeyDef.symbol(R.id.key_8, "8", "(", null, "8", "*"));
        keys.add(KeyDef.symbol(R.id.key_9, "9", ")", null, "9", "("));
        keys.add(KeyDef.symbol(R.id.key_0, "0", "=", null, "0", ")"));
        keys.add(KeyDef.symbol(R.id.key_quote, "'", "?", null, "-", "_"));
        keys.add(KeyDef.symbol(R.id.key_excl_open, "¡", "¿", null, "=", "+"));

        // Row 2: Q W E R T Y U I O P ` +
        keys.add(KeyDef.letter(R.id.key_q, "q"));
        keys.add(KeyDef.letter(R.id.key_w, "w"));
        keys.add(KeyDef.letter(R.id.key_e, "e", "€", "€"));
        keys.add(KeyDef.letter(R.id.key_r, "r"));
        keys.add(KeyDef.letter(R.id.key_t, "t"));
        keys.add(KeyDef.letter(R.id.key_y, "y"));
        keys.add(KeyDef.letter(R.id.key_u, "u"));
        keys.add(KeyDef.letter(R.id.key_i, "i"));
        keys.add(KeyDef.letter(R.id.key_o, "o"));
        keys.add(KeyDef.letter(R.id.key_p, "p"));
        keys.add(KeyDef.symbol(R.id.key_grave, "`", "^", "[", "[", "{"));
        keys.add(KeyDef.symbol(R.id.key_plus, "+", "*", "]", "]", "}"));

        // Row 3: A S D F G H J K L Ñ ´ ç
        keys.add(KeyDef.letter(R.id.key_a, "a"));
        keys.add(KeyDef.letter(R.id.key_s, "s"));
        keys.add(KeyDef.letter(R.id.key_d, "d"));
        keys.add(KeyDef.letter(R.id.key_f, "f"));
        keys.add(KeyDef.letter(R.id.key_g, "g"));
        keys.add(KeyDef.letter(R.id.key_h, "h"));
        keys.add(KeyDef.letter(R.id.key_j, "j"));
        keys.add(KeyDef.letter(R.id.key_k, "k"));
        keys.add(KeyDef.letter(R.id.key_l, "l"));
        // Ñ (in ENG layout corresponds to ;)
        keys.add(new KeyDef(
                R.id.key_nn,
                "ñ", "Ñ", null, "ñ", "Ñ", null,
                ";", ":", ";", ":",
                false
        ));
        keys.add(KeyDef.symbol(R.id.key_acute, "´", "¨", "{", "'", "\""));
        keys.add(KeyDef.symbol(R.id.key_cedilla, "ç", "Ç", "}", "\\", "|"));

        // Row 4: < Z X C V B N M , . -
        keys.add(KeyDef.symbol(R.id.key_less, "<", ">", null, "\\", "|"));
        keys.add(KeyDef.letter(R.id.key_z, "z"));
        keys.add(KeyDef.letter(R.id.key_x, "x"));
        keys.add(KeyDef.letter(R.id.key_c, "c"));
        keys.add(KeyDef.letter(R.id.key_v, "v"));
        keys.add(KeyDef.letter(R.id.key_b, "b"));
        keys.add(KeyDef.letter(R.id.key_n, "n"));
        keys.add(KeyDef.letter(R.id.key_m, "m"));
        keys.add(KeyDef.symbol(R.id.key_comma, ",", ";", null, ",", "<"));
        keys.add(KeyDef.symbol(R.id.key_period, ".", ":", null, ".", ">"));
        keys.add(KeyDef.symbol(R.id.key_minus, "-", "_", null, "/", "?"));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.dialog_pc_keyboard);

        // Landscape compaction
        if (getContext().getResources().getConfiguration().orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) {
            compactHeights(findViewById(android.R.id.content), 0.74f);
        }

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

        btnToggleSpecial = findViewById(R.id.btnToggleSpecial);
        layoutSpecialFunctions = findViewById(R.id.layoutSpecialFunctions);
        btnToggleSpecial.setOnClickListener(v -> {
            triggerHaptic(v);
            toggleSpecialSection();
        });

        // Setup special panel actions (Ctrl+C and Ctrl+V FIRST!)
        setupSpecialPanel();

        // Modifiers & Controls
        btnModCtrl = findViewById(R.id.key_ctrl);
        btnModCtrlRight = findViewById(R.id.key_ctrl_right);
        btnModWin = findViewById(R.id.key_win);
        btnModAlt = findViewById(R.id.key_alt);
        btnModShift = findViewById(R.id.key_shift);
        btnModShiftRight = findViewById(R.id.key_shift_right);
        btnModCaps = findViewById(R.id.key_caps);
        btnModAltGr = findViewById(R.id.key_altgr);
        btnModFn = findViewById(R.id.key_fn);
        btnLayoutToggle = findViewById(R.id.key_layout);

        View.OnClickListener ctrlListener = v -> {
            triggerHaptic(v);
            toggleModifier("ctrl");
        };
        btnModCtrl.setOnClickListener(ctrlListener);
        if (btnModCtrlRight != null) btnModCtrlRight.setOnClickListener(ctrlListener);

        btnModWin.setOnClickListener(v -> {
            triggerHaptic(v);
            toggleModifier("win");
        });

        btnModAlt.setOnClickListener(v -> {
            triggerHaptic(v);
            toggleModifier("alt");
        });

        View.OnClickListener shiftListener = v -> {
            triggerHaptic(v);
            toggleModifier("shift");
        };
        btnModShift.setOnClickListener(shiftListener);
        if (btnModShiftRight != null) btnModShiftRight.setOnClickListener(shiftListener);

        btnModCaps.setOnClickListener(v -> {
            triggerHaptic(v);
            toggleCapsLock();
        });

        btnModAltGr.setOnClickListener(v -> {
            triggerHaptic(v);
            toggleModifier("altgr");
        });

        btnModFn.setOnClickListener(v -> {
            triggerHaptic(v);
            toggleSpecialSection();
        });

        btnLayoutToggle.setText(currentLayout);
        btnLayoutToggle.setOnClickListener(v -> {
            triggerHaptic(v);
            currentLayout = "ESP".equals(currentLayout) ? "ENG" : "ESP";
            btnLayoutToggle.setText(currentLayout);
            refreshAllKeycaps();
            Toast.makeText(getContext(), getContext().getString(R.string.toast_layout_switched, currentLayout), Toast.LENGTH_SHORT).show();
        });

        // Setup Direct Control Keys
        setupControlKey(R.id.key_esc, "esc");
        setupControlKey(R.id.key_tab, "tab");
        setupControlKey(R.id.key_enter, "enter");
        setupControlKey(R.id.key_bksp, "backspace");
        setupControlKey(R.id.key_supr, "delete");
        setupControlKey(R.id.key_space, "space");
        setupControlKey(R.id.key_arrow_up, "up");
        setupControlKey(R.id.key_arrow_down, "down");
        setupControlKey(R.id.key_arrow_left, "left");
        setupControlKey(R.id.key_arrow_right, "right");

        // Bind all letter and symbol keys
        for (KeyDef def : keys) {
            def.button = findViewById(def.resId);
            if (def.button != null) {
                attachRepeatListener(def.button, () -> handleKeyClicked(def));
            }
        }

        refreshAllKeycaps();
    }

    private void refreshAllKeycaps() {
        boolean isEsp = "ESP".equals(currentLayout);
        boolean isShift = isShiftActive || isCapsLockActive;

        for (KeyDef def : keys) {
            if (def.button == null) continue;

            String normLabel = isEsp ? def.espNormalLabel : def.engNormalLabel;
            String shiftLabel = isEsp ? def.espShiftLabel : def.engShiftLabel;
            String altGrLabel = isEsp ? def.espAltGrLabel : null;

            if (isAltGrActive && altGrLabel != null) {
                def.button.setText(Html.fromHtml("<b>" + altGrLabel + "</b>"));
                def.button.setTextColor(ContextCompat.getColor(getContext(), R.color.accent_emerald));
            } else if (isShift) {
                if (def.isLetter) {
                    def.button.setText(normLabel.toUpperCase());
                } else if (shiftLabel != null) {
                    def.button.setText(Html.fromHtml("<b>" + shiftLabel + "</b>"));
                } else {
                    def.button.setText(normLabel);
                }
                def.button.setTextColor(ContextCompat.getColor(getContext(), R.color.text_primary));
            } else {
                if (def.isLetter) {
                    def.button.setText(normLabel.toLowerCase());
                } else if (shiftLabel != null) {
                    // Windows Touch Keyboard Style: Superscript shift symbol on top, normal label below!
                    String html = "<small><font color='#94A3B8'>" + shiftLabel + "</font></small><br><b>" + normLabel + "</b>";
                    def.button.setText(Html.fromHtml(html));
                } else {
                    def.button.setText(normLabel);
                }
                def.button.setTextColor(ContextCompat.getColor(getContext(), R.color.text_primary));
            }
        }
    }

    private void handleKeyClicked(KeyDef def) {
        boolean isEsp = "ESP".equals(currentLayout);

        String normVal = isEsp ? def.espNormalVal : def.engNormalVal;
        String shiftVal = isEsp ? def.espShiftVal : def.engShiftVal;
        String altGrVal = isEsp ? def.espAltGrVal : null;

        if (isAltGrActive && altGrVal != null) {
            sendDirectText(altGrVal);
            isAltGrActive = false;
            updateModifierVisual(btnModAltGr, false);
            refreshAllKeycaps();
            return;
        }

        // Modifiers (Ctrl, Alt, Win) combo
        if (isCtrlActive || isAltActive || isWinActive) {
            sendKeyWithModifiers(def.isLetter ? def.espNormalVal.toLowerCase() : normVal);
            return;
        }

        // Shift or CapsLock
        if (isShiftActive || isCapsLockActive) {
            if (def.isLetter) {
                sendDirectText(def.isLetter ? normVal.toUpperCase() : shiftVal);
            } else if (shiftVal != null) {
                sendDirectText(shiftVal);
            } else {
                sendDirectText(normVal);
            }
            if (isShiftActive) {
                isShiftActive = false;
                updateModifierVisual(btnModShift, false);
                if (btnModShiftRight != null) updateModifierVisual(btnModShiftRight, false);
                refreshAllKeycaps();
            }
            return;
        }

        // Normal character typing
        sendDirectText(normVal);
    }

    private void setupControlKey(int resId, String keyName) {
        View view = findViewById(resId);
        if (view != null) {
            attachRepeatListener(view, () -> sendKeyWithModifiers(keyName));
        }
    }

    private void setupSpecialPanel() {
        // Direct Action Shortcuts: Ctrl+C and Ctrl+V FIRST!
        findViewById(R.id.btnShortcutCopy).setOnClickListener(v -> {
            triggerHaptic(v);
            sendKey("ctrl+c");
        });
        findViewById(R.id.btnShortcutPaste).setOnClickListener(v -> {
            triggerHaptic(v);
            sendKey("ctrl+v");
        });
        findViewById(R.id.btnShortcutUndo).setOnClickListener(v -> {
            triggerHaptic(v);
            sendKey("ctrl+z");
        });
        findViewById(R.id.btnShortcutSelectAll).setOnClickListener(v -> {
            triggerHaptic(v);
            sendKey("ctrl+a");
        });
        findViewById(R.id.btnShortcutCut).setOnClickListener(v -> {
            triggerHaptic(v);
            sendKey("ctrl+x");
        });
        findViewById(R.id.btnShortcutSave).setOnClickListener(v -> {
            triggerHaptic(v);
            sendKey("ctrl+s");
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
        findViewById(R.id.btnShortcutCtrlAltDel).setOnClickListener(v -> {
            triggerHaptic(v);
            sendKey("ctrl+alt+del");
        });

        // Extended Nav Keys
        setupControlKey(R.id.btnIns, "ins");
        setupControlKey(R.id.btnDel, "delete");
        setupControlKey(R.id.btnHome, "home");
        setupControlKey(R.id.btnEnd, "end");
        setupControlKey(R.id.btnPgUp, "pageup");
        setupControlKey(R.id.btnPgDn, "pagedown");
        setupControlKey(R.id.btnPrtScn, "prtscn");

        // F1 - F12 Keys
        LinearLayout layoutFKeys = findViewById(R.id.layoutFKeys);
        if (layoutFKeys != null && layoutFKeys.getChildCount() == 0) {
            for (int i = 1; i <= 12; i++) {
                final String fKey = "f" + i;
                Button btnF = new Button(getContext());
                btnF.setText("F" + i);
                btnF.setTextSize(11f);
                btnF.setTextColor(ContextCompat.getColor(getContext(), R.color.text_primary));
                btnF.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(getContext(), R.color.btn_surface)));

                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dpToPx(44), dpToPx(32));
                lp.setMargins(0, 0, dpToPx(4), 0);
                btnF.setLayoutParams(lp);

                attachRepeatListener(btnF, () -> sendKeyWithModifiers(fKey));
                layoutFKeys.addView(btnF);
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

    private void toggleModifier(String mod) {
        switch (mod) {
            case "ctrl":
                isCtrlActive = !isCtrlActive;
                updateModifierVisual(btnModCtrl, isCtrlActive);
                if (btnModCtrlRight != null) updateModifierVisual(btnModCtrlRight, isCtrlActive);
                break;
            case "alt":
                isAltActive = !isAltActive;
                updateModifierVisual(btnModAlt, isAltActive);
                break;
            case "shift":
                isShiftActive = !isShiftActive;
                updateModifierVisual(btnModShift, isShiftActive);
                if (btnModShiftRight != null) updateModifierVisual(btnModShiftRight, isShiftActive);
                refreshAllKeycaps();
                break;
            case "win":
                isWinActive = !isWinActive;
                updateModifierVisual(btnModWin, isWinActive);
                break;
            case "altgr":
                isAltGrActive = !isAltGrActive;
                updateModifierVisual(btnModAltGr, isAltGrActive);
                refreshAllKeycaps();
                break;
        }
    }

    private void toggleCapsLock() {
        isCapsLockActive = !isCapsLockActive;
        updateModifierVisual(btnModCaps, isCapsLockActive);
        sendKey("capslock");
        refreshAllKeycaps();
    }

    private void updateModifierVisual(MaterialButton btn, boolean active) {
        if (btn == null) return;
        int color = ContextCompat.getColor(getContext(), active ? R.color.accent_indigo : R.color.btn_surface);
        btn.setBackgroundTintList(ColorStateList.valueOf(color));
    }

    private void sendDirectText(String text) {
        if (webSocketManager != null && text != null) {
            webSocketManager.sendTypeText(text);
        }
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

        // Reset temporary modifier latches
        if (isCtrlActive || isAltActive || isShiftActive || isWinActive || isAltGrActive) {
            isCtrlActive = false;
            isAltActive = false;
            isShiftActive = false;
            isWinActive = false;
            isAltGrActive = false;
            updateModifierVisual(btnModCtrl, false);
            if (btnModCtrlRight != null) updateModifierVisual(btnModCtrlRight, false);
            updateModifierVisual(btnModAlt, false);
            updateModifierVisual(btnModShift, false);
            if (btnModShiftRight != null) updateModifierVisual(btnModShiftRight, false);
            updateModifierVisual(btnModWin, false);
            updateModifierVisual(btnModAltGr, false);
            refreshAllKeycaps();
        }
    }

    private void attachRepeatListener(View view, Runnable action) {
        if (view == null || action == null) return;

        Runnable repeatRunnable = new Runnable() {
            @Override
            public void run() {
                triggerRepeatHaptic(view);
                action.run();
                repeatHandler.postDelayed(this, REPEAT_INTERVAL_MS);
            }
        };

        view.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    triggerHaptic(v);
                    action.run();
                    repeatHandler.removeCallbacks(repeatRunnable);
                    repeatHandler.postDelayed(repeatRunnable, INITIAL_REPEAT_DELAY_MS);
                    v.setPressed(true);
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float x = event.getX();
                    float y = event.getY();
                    if (x < 0 || x > v.getWidth() || y < 0 || y > v.getHeight()) {
                        repeatHandler.removeCallbacks(repeatRunnable);
                        v.setPressed(false);
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    repeatHandler.removeCallbacks(repeatRunnable);
                    v.setPressed(false);
                    return true;
            }
            return false;
        });
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

    private void triggerRepeatHaptic(View v) {
        if (v != null) {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        }
    }

    @Override
    public void dismiss() {
        repeatHandler.removeCallbacksAndMessages(null);
        super.dismiss();
    }

    @Override
    protected void onStop() {
        repeatHandler.removeCallbacksAndMessages(null);
        super.onStop();
    }

    private int dpToPx(int dp) {
        float density = getContext().getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }
}
