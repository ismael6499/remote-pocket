package com.remotepocket.ui;

import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
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
import com.remotepocket.util.TextHistoryManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    // Mode: Mobile Comfort (10 wide keys) vs Full Desktop (15 columns)
    private boolean isMobileComfortMode = true;

    // Containers & Mode Buttons
    private LinearLayout layoutMobileComfortKeyboard;
    private LinearLayout layoutFullDesktopKeyboard;
    private LinearLayout layoutMobileLetters;
    private LinearLayout layoutMobileSymbols;
    private MaterialButton btnToggleLayoutMode;

    // Desktop Modifiers & Controls
    private MaterialButton btnModCtrl, btnModCtrlRight, btnModAlt, btnModWin;
    private MaterialButton btnModShift, btnModShiftRight, btnModCaps, btnModAltGr;
    private MaterialButton btnModFn, btnLayoutToggle;
    private MaterialButton btnToggleSpecial;
    private LinearLayout layoutSpecialFunctions;

    // Mobile Top Quick Bar & Modifiers
    private MaterialButton mKeyCtrl, mKeyAlt, mKeyWin, mKeyShift, mKeyLayout, mKeyNn;
    private final Map<MaterialButton, String> mobileLetterMap = new HashMap<>();

    // Desktop Key definition holder
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
        }
        if (mKeyLayout != null) {
            mKeyLayout.setText(currentLayout);
        }
        refreshAllKeycaps();
    }

    public void setOnSwitchToNormalKeyboard(Runnable runnable) {
        this.onSwitchToNormalKeyboard = runnable;
    }

    public interface OnHeightChangeListener {
        void onHeightChanged(int heightPx);
    }

    public interface OnScreenFitToggleListener {
        void onScreenFitToggled(boolean isAutoFit);
    }

    private OnHeightChangeListener onHeightChangeListener;
    private OnScreenFitToggleListener onScreenFitToggleListener;
    private boolean isScreenAutoFit = true;

    private static final String PREF_KB_SIZE = "pref_pc_kb_size_preset";
    private int currentSizeIndex = 1; // 0 = Compact (0.68f), 1 = Medium (0.82f), 2 = Large (1.00f)
    private static final float[] SIZE_FACTORS = {0.68f, 0.82f, 1.00f};
    private static final int[] SIZE_BTN_TEXTS = {R.string.btn_kb_size_compact, R.string.btn_kb_size_medium, R.string.btn_kb_size_large};
    private static final int[] SIZE_NAMES = {R.string.kb_size_compact, R.string.kb_size_medium, R.string.kb_size_large};
    private static final String[] DESKTOP_PERCENTAGES = {"60%", "53%", "42%"};

    private final Map<View, Integer> originalHeights = new HashMap<>();

    public void setOnHeightChangeListener(OnHeightChangeListener listener) {
        this.onHeightChangeListener = listener;
    }

    public void setOnScreenFitToggleListener(boolean currentAutoFit, OnScreenFitToggleListener listener) {
        this.isScreenAutoFit = currentAutoFit;
        this.onScreenFitToggleListener = listener;
    }

    private void recordOriginalHeights(View root) {
        if (root == null) return;
        ViewGroup.LayoutParams lp = root.getLayoutParams();
        if (lp != null && lp.height > 0 && !(root instanceof ViewGroup && ((ViewGroup) root).getChildCount() > 0)) {
            originalHeights.put(root, lp.height);
        }
        if (root instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) root;
            for (int i = 0; i < g.getChildCount(); i++) {
                recordOriginalHeights(g.getChildAt(i));
            }
        }
    }

    private void applyKeyboardScale(float factor) {
        float density = getContext().getResources().getDisplayMetrics().density;
        float minHeight = 22f * density;

        for (Map.Entry<View, Integer> entry : originalHeights.entrySet()) {
            View view = entry.getKey();
            int orig = entry.getValue();
            ViewGroup.LayoutParams lp = view.getLayoutParams();
            if (lp != null) {
                lp.height = Math.max((int) minHeight, Math.round(orig * factor));
                view.setLayoutParams(lp);
            }
        }

        View root = findViewById(R.id.rootKeyboardLayout);
        if (root != null) {
            root.requestLayout();
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

        View root = findViewById(R.id.rootKeyboardLayout);
        recordOriginalHeights(root);

        SharedPreferences prefs = getContext().getSharedPreferences("remote_pocket_prefs", Context.MODE_PRIVATE);
        currentSizeIndex = prefs.getInt(PREF_KB_SIZE, 1);
        if (currentSizeIndex < 0 || currentSizeIndex >= SIZE_FACTORS.length) {
            currentSizeIndex = 1;
        }

        boolean isLandscape = getContext().getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        isMobileComfortMode = !isLandscape; // Default to Mobile Comfort in portrait, Full Desktop in landscape

        // Apply scalable compaction
        if (isLandscape) {
            applyKeyboardScale(SIZE_FACTORS[currentSizeIndex]);
        }

        if (root != null) {
            root.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
                int height = bottom - top;
                if (height > 0 && onHeightChangeListener != null) {
                    onHeightChangeListener.onHeightChanged(height);
                }
            });
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

        // Mode containers & toggle button
        layoutMobileComfortKeyboard = findViewById(R.id.layoutMobileComfortKeyboard);
        layoutFullDesktopKeyboard = findViewById(R.id.layoutFullDesktopKeyboard);
        layoutMobileLetters = findViewById(R.id.layoutMobileLetters);
        layoutMobileSymbols = findViewById(R.id.layoutMobileSymbols);
        btnToggleLayoutMode = findViewById(R.id.btnToggleLayoutMode);

        if (btnToggleLayoutMode != null) {
            btnToggleLayoutMode.setOnClickListener(v -> {
                triggerHaptic(v);
                isMobileComfortMode = !isMobileComfortMode;
                updateLayoutModeVisibility();
                String modeName = getContext().getString(isMobileComfortMode ? R.string.btn_mode_mobile : R.string.btn_mode_desktop);
                Toast.makeText(getContext(), getContext().getString(R.string.toast_mode_switched, modeName), Toast.LENGTH_SHORT).show();
            });
        }
        updateLayoutModeVisibility();

        // Keyboard Height Regulation Button
        MaterialButton btnToggleKeyboardSize = findViewById(R.id.btnToggleKeyboardSize);
        if (btnToggleKeyboardSize != null) {
            btnToggleKeyboardSize.setText(SIZE_BTN_TEXTS[currentSizeIndex]);
            btnToggleKeyboardSize.setOnClickListener(v -> {
                triggerHaptic(v);
                currentSizeIndex = (currentSizeIndex + 1) % SIZE_FACTORS.length;
                prefs.edit().putInt(PREF_KB_SIZE, currentSizeIndex).apply();
                btnToggleKeyboardSize.setText(SIZE_BTN_TEXTS[currentSizeIndex]);
                applyKeyboardScale(SIZE_FACTORS[currentSizeIndex]);
                String sizeName = getContext().getString(SIZE_NAMES[currentSizeIndex]);
                String desc = DESKTOP_PERCENTAGES[currentSizeIndex];
                Toast.makeText(getContext(), getContext().getString(R.string.toast_kb_size_switched, sizeName, desc), Toast.LENGTH_SHORT).show();
            });
        }

        // Screen Fit Mode Toggle Button
        MaterialButton btnToggleScreenFit = findViewById(R.id.btnToggleScreenFit);
        if (btnToggleScreenFit != null) {
            btnToggleScreenFit.setText(isScreenAutoFit ? R.string.btn_screen_fit_on : R.string.btn_screen_fit_off);
            btnToggleScreenFit.setOnClickListener(v -> {
                triggerHaptic(v);
                isScreenAutoFit = !isScreenAutoFit;
                btnToggleScreenFit.setText(isScreenAutoFit ? R.string.btn_screen_fit_on : R.string.btn_screen_fit_off);
                if (onScreenFitToggleListener != null) {
                    onScreenFitToggleListener.onScreenFitToggled(isScreenAutoFit);
                }
                Toast.makeText(getContext(), isScreenAutoFit ? R.string.toast_screen_fit_on : R.string.toast_screen_fit_off, Toast.LENGTH_SHORT).show();
            });
        }

        // Setup special panel actions (Ctrl+C and Ctrl+V FIRST!)
        setupSpecialPanel();

        // ----------------- Desktop Layout Setup -----------------
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
        if (btnModCtrl != null) btnModCtrl.setOnClickListener(ctrlListener);
        if (btnModCtrlRight != null) btnModCtrlRight.setOnClickListener(ctrlListener);

        if (btnModWin != null) {
            btnModWin.setOnClickListener(v -> {
                triggerHaptic(v);
                toggleModifier("win");
            });
        }

        if (btnModAlt != null) {
            btnModAlt.setOnClickListener(v -> {
                triggerHaptic(v);
                toggleModifier("alt");
            });
        }

        View.OnClickListener shiftListener = v -> {
            triggerHaptic(v);
            toggleModifier("shift");
        };
        if (btnModShift != null) btnModShift.setOnClickListener(shiftListener);
        if (btnModShiftRight != null) btnModShiftRight.setOnClickListener(shiftListener);

        if (btnModCaps != null) {
            btnModCaps.setOnClickListener(v -> {
                triggerHaptic(v);
                toggleCapsLock();
            });
        }

        if (btnModAltGr != null) {
            btnModAltGr.setOnClickListener(v -> {
                triggerHaptic(v);
                toggleModifier("altgr");
            });
        }

        if (btnModFn != null) {
            btnModFn.setOnClickListener(v -> {
                triggerHaptic(v);
                toggleSpecialSection();
            });
        }

        View.OnClickListener layoutToggleListener = v -> {
            triggerHaptic(v);
            currentLayout = "ESP".equals(currentLayout) ? "ENG" : "ESP";
            if (btnLayoutToggle != null) btnLayoutToggle.setText(currentLayout);
            if (mKeyLayout != null) mKeyLayout.setText(currentLayout);
            refreshAllKeycaps();
            Toast.makeText(getContext(), getContext().getString(R.string.toast_layout_switched, currentLayout), Toast.LENGTH_SHORT).show();
        };

        if (btnLayoutToggle != null) {
            btnLayoutToggle.setText(currentLayout);
            btnLayoutToggle.setOnClickListener(layoutToggleListener);
        }

        // Setup Desktop Control Keys
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

        // Bind all desktop letter and symbol keys
        for (KeyDef def : keys) {
            def.button = findViewById(def.resId);
            if (def.button != null) {
                attachRepeatListener(def.button, () -> handleDesktopKeyClicked(def));
            }
        }

        // ----------------- Mobile Comfort Setup -----------------
        setupMobileComfortLayout(layoutToggleListener);

        refreshAllKeycaps();
    }

    private void updateLayoutModeVisibility() {
        if (layoutMobileComfortKeyboard == null || layoutFullDesktopKeyboard == null) return;
        if (isMobileComfortMode) {
            layoutMobileComfortKeyboard.setVisibility(View.VISIBLE);
            layoutFullDesktopKeyboard.setVisibility(View.GONE);
            if (btnToggleLayoutMode != null) {
                btnToggleLayoutMode.setText(R.string.btn_mode_desktop);
            }
        } else {
            layoutMobileComfortKeyboard.setVisibility(View.GONE);
            layoutFullDesktopKeyboard.setVisibility(View.VISIBLE);
            if (btnToggleLayoutMode != null) {
                btnToggleLayoutMode.setText(R.string.btn_mode_mobile);
            }
        }
    }

    private void setupMobileComfortLayout(View.OnClickListener layoutToggleListener) {
        // Mobile Top PC Quick Bar
        setupControlKey(R.id.m_key_esc, "esc");
        setupControlKey(R.id.m_key_tab, "tab");

        mKeyCtrl = findViewById(R.id.m_key_ctrl);
        if (mKeyCtrl != null) {
            mKeyCtrl.setOnClickListener(v -> {
                triggerHaptic(v);
                toggleModifier("ctrl");
            });
        }

        mKeyAlt = findViewById(R.id.m_key_alt);
        if (mKeyAlt != null) {
            mKeyAlt.setOnClickListener(v -> {
                triggerHaptic(v);
                toggleModifier("alt");
            });
        }

        mKeyWin = findViewById(R.id.m_key_win);
        if (mKeyWin != null) {
            mKeyWin.setOnClickListener(v -> {
                triggerHaptic(v);
                toggleModifier("win");
            });
        }

        setupActionKey(R.id.m_key_copy, () -> sendKey("ctrl+c"));
        setupActionKey(R.id.m_key_paste, () -> sendKey("ctrl+v"));
        setupControlKey(R.id.m_key_del, "delete");

        mKeyLayout = findViewById(R.id.m_key_layout);
        if (mKeyLayout != null) {
            mKeyLayout.setText(currentLayout);
            mKeyLayout.setOnClickListener(layoutToggleListener);
        }

        // Sub-view toggle buttons (?123 <-> ABC)
        View btnToSymbols = findViewById(R.id.m_key_to_symbols);
        if (btnToSymbols != null) {
            btnToSymbols.setOnClickListener(v -> {
                triggerHaptic(v);
                if (layoutMobileLetters != null) layoutMobileLetters.setVisibility(View.GONE);
                if (layoutMobileSymbols != null) layoutMobileSymbols.setVisibility(View.VISIBLE);
            });
        }

        View btnToLetters = findViewById(R.id.m_key_to_letters);
        if (btnToLetters != null) {
            btnToLetters.setOnClickListener(v -> {
                triggerHaptic(v);
                if (layoutMobileSymbols != null) layoutMobileSymbols.setVisibility(View.GONE);
                if (layoutMobileLetters != null) layoutMobileLetters.setVisibility(View.VISIBLE);
            });
        }

        // Mobile Letters
        mobileLetterMap.clear();
        bindMobileLetter(R.id.m_key_q, "q");
        bindMobileLetter(R.id.m_key_w, "w");
        bindMobileLetter(R.id.m_key_e, "e");
        bindMobileLetter(R.id.m_key_r, "r");
        bindMobileLetter(R.id.m_key_t, "t");
        bindMobileLetter(R.id.m_key_y, "y");
        bindMobileLetter(R.id.m_key_u, "u");
        bindMobileLetter(R.id.m_key_i, "i");
        bindMobileLetter(R.id.m_key_o, "o");
        bindMobileLetter(R.id.m_key_p, "p");

        bindMobileLetter(R.id.m_key_a, "a");
        bindMobileLetter(R.id.m_key_s, "s");
        bindMobileLetter(R.id.m_key_d, "d");
        bindMobileLetter(R.id.m_key_f, "f");
        bindMobileLetter(R.id.m_key_g, "g");
        bindMobileLetter(R.id.m_key_h, "h");
        bindMobileLetter(R.id.m_key_j, "j");
        bindMobileLetter(R.id.m_key_k, "k");
        bindMobileLetter(R.id.m_key_l, "l");

        mKeyNn = findViewById(R.id.m_key_nn);
        if (mKeyNn != null) {
            attachRepeatListener(mKeyNn, () -> {
                boolean isEsp = "ESP".equals(currentLayout);
                boolean isShift = isShiftActive || isCapsLockActive;
                if (isEsp) {
                    handleMobileCharacter(isShift ? "Ñ" : "ñ", "ñ");
                } else {
                    handleMobileCharacter(isShift ? ":" : ";", ";");
                }
            });
        }

        mKeyShift = findViewById(R.id.m_key_shift);
        if (mKeyShift != null) {
            mKeyShift.setOnClickListener(v -> {
                triggerHaptic(v);
                toggleModifier("shift");
            });
        }

        bindMobileLetter(R.id.m_key_z, "z");
        bindMobileLetter(R.id.m_key_x, "x");
        bindMobileLetter(R.id.m_key_c, "c");
        bindMobileLetter(R.id.m_key_v, "v");
        bindMobileLetter(R.id.m_key_b, "b");
        bindMobileLetter(R.id.m_key_n, "n");
        bindMobileLetter(R.id.m_key_m, "m");

        setupControlKey(R.id.m_key_bksp, "backspace");
        setupTextKey(R.id.m_key_comma, ",");
        setupControlKey(R.id.m_key_space, "space");
        setupTextKey(R.id.m_key_period, ".");
        setupControlKey(R.id.m_key_arrow_left, "left");
        setupControlKey(R.id.m_key_arrow_right, "right");
        setupControlKey(R.id.m_key_enter, "enter");

        // Mobile Symbols & Numbers
        setupTextKey(R.id.m_sym_1, "1");
        setupTextKey(R.id.m_sym_2, "2");
        setupTextKey(R.id.m_sym_3, "3");
        setupTextKey(R.id.m_sym_4, "4");
        setupTextKey(R.id.m_sym_5, "5");
        setupTextKey(R.id.m_sym_6, "6");
        setupTextKey(R.id.m_sym_7, "7");
        setupTextKey(R.id.m_sym_8, "8");
        setupTextKey(R.id.m_sym_9, "9");
        setupTextKey(R.id.m_sym_0, "0");

        // Row 2 Special Symbols
        setupTextKey(R.id.m_sym_excl, "!");
        setupTextKey(R.id.m_sym_dquote, "\"");
        setupTextKey(R.id.m_sym_middot, "·");
        setupTextKey(R.id.m_sym_dollar, "$");
        setupTextKey(R.id.m_sym_percent, "%");
        setupTextKey(R.id.m_sym_amp, "&");
        setupTextKey(R.id.m_sym_slash, "/");
        setupTextKey(R.id.m_sym_paren_open, "(");
        setupTextKey(R.id.m_sym_paren_close, ")");
        setupTextKey(R.id.m_sym_equal, "=");

        // Row 3 Special Symbols
        MaterialButton mSymAltGr = findViewById(R.id.m_sym_altgr);
        if (mSymAltGr != null) {
            mSymAltGr.setOnClickListener(v -> {
                triggerHaptic(v);
                toggleModifier("altgr");
            });
        }
        setupTextKey(R.id.m_sym_quote, "'");
        setupTextKey(R.id.m_sym_question, "?");
        setupTextKey(R.id.m_sym_excl_open, "¡");
        setupTextKey(R.id.m_sym_question_open, "¿");
        setupTextKey(R.id.m_sym_backslash, "\\");
        setupTextKey(R.id.m_sym_pipe, "|");
        setupTextKey(R.id.m_sym_at, "@");
        setupTextKey(R.id.m_sym_hash, "#");
        setupControlKey(R.id.m_sym_bksp, "backspace");

        // Row 4 Special Symbols
        setupTextKey(R.id.m_sym_euro, "€");
        setupTextKey(R.id.m_sym_bracket_left, "[");
        setupTextKey(R.id.m_sym_bracket_right, "]");
        setupTextKey(R.id.m_sym_brace_left, "{");
        setupTextKey(R.id.m_sym_brace_right, "}");
        setupTextKey(R.id.m_sym_less, "<");
        setupTextKey(R.id.m_sym_greater, ">");
        setupTextKey(R.id.m_sym_semicolon, ";");
        setupTextKey(R.id.m_sym_colon, ":");
    }

    private void bindMobileLetter(int resId, String letter) {
        MaterialButton btn = findViewById(resId);
        if (btn != null) {
            mobileLetterMap.put(btn, letter);
            attachRepeatListener(btn, () -> {
                boolean upper = isShiftActive || isCapsLockActive;
                handleMobileCharacter(upper ? letter.toUpperCase() : letter.toLowerCase(), letter);
            });
        }
    }

    private void handleMobileCharacter(String textToSend, String baseKey) {
        if (isCtrlActive || isAltActive || isWinActive) {
            sendKeyWithModifiers(baseKey);
            return;
        }

        sendDirectText(textToSend);

        if (isShiftActive) {
            isShiftActive = false;
            updateModifierVisual(btnModShift, false);
            if (btnModShiftRight != null) updateModifierVisual(btnModShiftRight, false);
            updateModifierVisual(mKeyShift, false);
            refreshAllKeycaps();
        }
    }

    private void setupActionKey(int resId, Runnable action) {
        View view = findViewById(resId);
        if (view != null) {
            view.setOnClickListener(v -> {
                triggerHaptic(v);
                action.run();
            });
        }
    }

    private void setupTextKey(int resId, String text) {
        View view = findViewById(resId);
        if (view != null) {
            attachRepeatListener(view, () -> sendDirectText(text));
        }
    }

    private void refreshAllKeycaps() {
        boolean isEsp = "ESP".equals(currentLayout);
        boolean isShift = isShiftActive || isCapsLockActive;

        // Desktop keycaps
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
                    String html = "<small><font color='#94A3B8'>" + shiftLabel + "</font></small><br><b>" + normLabel + "</b>";
                    def.button.setText(Html.fromHtml(html));
                } else {
                    def.button.setText(normLabel);
                }
                def.button.setTextColor(ContextCompat.getColor(getContext(), R.color.text_primary));
            }
        }

        // Mobile letter caps
        for (Map.Entry<MaterialButton, String> entry : mobileLetterMap.entrySet()) {
            MaterialButton btn = entry.getKey();
            String letter = entry.getValue();
            btn.setText(isShift ? letter.toUpperCase() : letter.toUpperCase()); // Standard mobile keyboard aesthetic
        }

        if (mKeyNn != null) {
            if (isEsp) {
                mKeyNn.setText(isShift ? "Ñ" : "Ñ");
            } else {
                mKeyNn.setText(isShift ? ":" : ";");
            }
        }

        // Layout indicators
        if (btnLayoutToggle != null) btnLayoutToggle.setText(currentLayout);
        if (mKeyLayout != null) mKeyLayout.setText(currentLayout);
    }

    private void handleDesktopKeyClicked(KeyDef def) {
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
                sendDirectText(normVal.toUpperCase());
            } else if (shiftVal != null) {
                sendDirectText(shiftVal);
            } else {
                sendDirectText(normVal);
            }
            if (isShiftActive) {
                isShiftActive = false;
                updateModifierVisual(btnModShift, false);
                if (btnModShiftRight != null) updateModifierVisual(btnModShiftRight, false);
                updateModifierVisual(mKeyShift, false);
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
        setupActionKey(R.id.btnShortcutCopy, () -> sendKey("ctrl+c"));
        setupActionKey(R.id.btnShortcutPaste, () -> sendKey("ctrl+v"));

        View btnShortcutHistory = findViewById(R.id.btnShortcutHistory);
        if (btnShortcutHistory != null) {
            btnShortcutHistory.setOnClickListener(v -> {
                triggerHaptic(v);
                TextHistoryDialog historyDialog = new TextHistoryDialog(getContext(), new TextHistoryDialog.OnTextActionListener() {
                    @Override
                    public void onRestoreText(String text) {
                        dismiss();
                        if (onSwitchToNormalKeyboard != null) {
                            onSwitchToNormalKeyboard.run();
                        }
                    }

                    @Override
                    public void onResendText(String text) {
                        if (text != null && !text.isEmpty()) {
                            TextHistoryManager.getInstance(getContext()).recordSentText(text);
                            if (text.length() > 25 || text.contains("\n") || text.contains("\r")) {
                                webSocketManager.sendPaste(text);
                            } else {
                                webSocketManager.sendTypeText(text);
                            }
                            Toast.makeText(getContext(), R.string.toast_text_resent, Toast.LENGTH_SHORT).show();
                        }
                    }
                });
                historyDialog.show();
            });
        }

        setupActionKey(R.id.btnShortcutUndo, () -> sendKey("ctrl+z"));
        setupActionKey(R.id.btnShortcutSelectAll, () -> sendKey("ctrl+a"));
        setupActionKey(R.id.btnShortcutCut, () -> sendKey("ctrl+x"));
        setupActionKey(R.id.btnShortcutSave, () -> sendKey("ctrl+s"));
        setupActionKey(R.id.btnShortcutAltTab, () -> sendKey("alt+tab"));
        setupActionKey(R.id.btnShortcutWinD, () -> sendKey("win+d"));
        setupActionKey(R.id.btnShortcutTaskMgr, () -> sendKey("ctrl+shift+esc"));
        setupActionKey(R.id.btnShortcutWinL, () -> sendKey("win+l"));
        setupActionKey(R.id.btnShortcutCtrlAltDel, () -> sendKey("ctrl+alt+del"));

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
                updateModifierVisual(mKeyCtrl, isCtrlActive);
                break;
            case "alt":
                isAltActive = !isAltActive;
                updateModifierVisual(btnModAlt, isAltActive);
                updateModifierVisual(mKeyAlt, isAltActive);
                break;
            case "shift":
                isShiftActive = !isShiftActive;
                updateModifierVisual(btnModShift, isShiftActive);
                if (btnModShiftRight != null) updateModifierVisual(btnModShiftRight, isShiftActive);
                updateModifierVisual(mKeyShift, isShiftActive);
                refreshAllKeycaps();
                break;
            case "win":
                isWinActive = !isWinActive;
                updateModifierVisual(btnModWin, isWinActive);
                updateModifierVisual(mKeyWin, isWinActive);
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
            updateModifierVisual(mKeyCtrl, false);
            updateModifierVisual(btnModAlt, false);
            updateModifierVisual(mKeyAlt, false);
            updateModifierVisual(btnModShift, false);
            if (btnModShiftRight != null) updateModifierVisual(btnModShiftRight, false);
            updateModifierVisual(mKeyShift, false);
            updateModifierVisual(btnModWin, false);
            updateModifierVisual(mKeyWin, false);
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
