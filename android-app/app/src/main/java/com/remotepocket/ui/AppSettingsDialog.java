package com.remotepocket.ui;

import android.app.Dialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.remotepocket.R;
import com.remotepocket.util.AppSettings;

import java.util.Locale;

public class AppSettingsDialog extends Dialog {

    public interface OnSettingsChangedListener {
        void onSettingsChanged();
    }

    private final OnSettingsChangedListener listener;

    private MaterialButton btnMonitorModeSingle, btnMonitorModeSpan, btnMonitorModeLast;
    private MaterialButton btnSpanZoomFitAll, btnSpanZoom1x, btnSpanZoom15x, btnSpanZoom2x;
    private MaterialButton btnSingleTargetPrimary, btnSingleTargetFirst, btnSingleTargetLast;
    private MaterialButton btnStartupTabScreen, btnStartupTabTrackpad;
    private MaterialButton btnFps2, btnFps15, btnFps30, btnFps60;
    private MaterialButton btnSingleZoom10, btnSingleZoom15, btnSingleZoom20, btnSingleZoom25;

    private SwitchMaterial switchFollowCursor, switchStartFullscreen, switchKeepScreenOn;
    private SwitchMaterial switchInvertScroll, switchHaptic;
    private SeekBar seekMouseSensitivity, seekScrollSensitivity;
    private TextView txtMouseSensitivityVal, txtScrollSensitivityVal;

    private boolean isBindingUi = false;

    public AppSettingsDialog(@NonNull Context context, OnSettingsChangedListener listener) {
        super(context);
        this.listener = listener;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.dialog_app_settings);

        Window window = getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            int maxHeight = (int) (getContext().getResources().getDisplayMetrics().heightPixels * 0.85f);
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, maxHeight);
            window.setGravity(Gravity.BOTTOM);
        }

        bindViews();
        loadValuesToUi();
        setupListeners();
    }

    private void bindViews() {
        btnMonitorModeSingle = findViewById(R.id.btnMonitorModeSingle);
        btnMonitorModeSpan = findViewById(R.id.btnMonitorModeSpan);
        btnMonitorModeLast = findViewById(R.id.btnMonitorModeLast);

        btnSpanZoomFitAll = findViewById(R.id.btnSpanZoomFitAll);
        btnSpanZoom1x = findViewById(R.id.btnSpanZoom1x);
        btnSpanZoom15x = findViewById(R.id.btnSpanZoom15x);
        btnSpanZoom2x = findViewById(R.id.btnSpanZoom2x);

        btnSingleTargetPrimary = findViewById(R.id.btnSingleTargetPrimary);
        btnSingleTargetFirst = findViewById(R.id.btnSingleTargetFirst);
        btnSingleTargetLast = findViewById(R.id.btnSingleTargetLast);

        btnStartupTabScreen = findViewById(R.id.btnStartupTabScreen);
        btnStartupTabTrackpad = findViewById(R.id.btnStartupTabTrackpad);

        btnFps2 = findViewById(R.id.btnFps2);
        btnFps15 = findViewById(R.id.btnFps15);
        btnFps30 = findViewById(R.id.btnFps30);
        btnFps60 = findViewById(R.id.btnFps60);

        btnSingleZoom10 = findViewById(R.id.btnSingleZoom10);
        btnSingleZoom15 = findViewById(R.id.btnSingleZoom15);
        btnSingleZoom20 = findViewById(R.id.btnSingleZoom20);
        btnSingleZoom25 = findViewById(R.id.btnSingleZoom25);

        switchFollowCursor = findViewById(R.id.switchFollowCursor);
        switchStartFullscreen = findViewById(R.id.switchStartFullscreen);
        switchKeepScreenOn = findViewById(R.id.switchKeepScreenOn);
        switchInvertScroll = findViewById(R.id.switchInvertScroll);
        switchHaptic = findViewById(R.id.switchHaptic);

        seekMouseSensitivity = findViewById(R.id.seekMouseSensitivity);
        seekScrollSensitivity = findViewById(R.id.seekScrollSensitivity);
        txtMouseSensitivityVal = findViewById(R.id.txtMouseSensitivityVal);
        txtScrollSensitivityVal = findViewById(R.id.txtScrollSensitivityVal);
    }

    private void loadValuesToUi() {
        isBindingUi = true;
        Context ctx = getContext();

        String monitorMode = AppSettings.getDefaultMonitorMode(ctx);
        styleOptionButton(btnMonitorModeSingle, AppSettings.MONITOR_MODE_SINGLE.equals(monitorMode), R.color.accent_indigo);
        styleOptionButton(btnMonitorModeSpan, AppSettings.MONITOR_MODE_SPAN.equals(monitorMode), R.color.accent_cyan);
        styleOptionButton(btnMonitorModeLast, AppSettings.MONITOR_MODE_LAST.equals(monitorMode), R.color.accent_indigo);

        float spanZoom = AppSettings.getSpanInitialZoom(ctx);
        styleOptionButton(btnSpanZoomFitAll, spanZoom <= 0.1f, R.color.accent_cyan);
        styleOptionButton(btnSpanZoom1x, Math.abs(spanZoom - 1.0f) < 0.1f, R.color.accent_cyan);
        styleOptionButton(btnSpanZoom15x, Math.abs(spanZoom - 1.5f) < 0.1f, R.color.accent_cyan);
        styleOptionButton(btnSpanZoom2x, spanZoom >= 1.9f, R.color.accent_cyan);

        String singleTarget = AppSettings.getDefaultSingleMonitor(ctx);
        styleOptionButton(btnSingleTargetPrimary, AppSettings.SINGLE_MONITOR_PRIMARY.equals(singleTarget), R.color.accent_indigo);
        styleOptionButton(btnSingleTargetFirst, AppSettings.SINGLE_MONITOR_FIRST.equals(singleTarget), R.color.accent_indigo);
        styleOptionButton(btnSingleTargetLast, AppSettings.SINGLE_MONITOR_LAST.equals(singleTarget), R.color.accent_indigo);

        String startupView = AppSettings.getStartupViewMode(ctx);
        styleOptionButton(btnStartupTabScreen, AppSettings.VIEW_MODE_SCREEN.equals(startupView), R.color.accent_indigo);
        styleOptionButton(btnStartupTabTrackpad, AppSettings.VIEW_MODE_TRACKPAD.equals(startupView), R.color.accent_indigo);

        int fps = AppSettings.getScreenFps(ctx);
        styleOptionButton(btnFps2, fps == 2, R.color.accent_amber);
        styleOptionButton(btnFps15, fps == 15, R.color.accent_indigo);
        styleOptionButton(btnFps30, fps == 30, R.color.accent_emerald);
        styleOptionButton(btnFps60, fps == 60, R.color.accent_cyan);

        float singleZoom = AppSettings.getSingleInitialZoom(ctx);
        styleOptionButton(btnSingleZoom10, singleZoom <= 1.1f, R.color.accent_emerald);
        styleOptionButton(btnSingleZoom15, Math.abs(singleZoom - 1.5f) < 0.1f, R.color.accent_emerald);
        styleOptionButton(btnSingleZoom20, Math.abs(singleZoom - 2.0f) < 0.1f, R.color.accent_emerald);
        styleOptionButton(btnSingleZoom25, singleZoom >= 2.4f, R.color.accent_emerald);

        switchFollowCursor.setChecked(AppSettings.isFollowCursorDefault(ctx));
        switchStartFullscreen.setChecked(AppSettings.isStartFullscreen(ctx));
        switchKeepScreenOn.setChecked(AppSettings.isKeepScreenOn(ctx));
        switchInvertScroll.setChecked(AppSettings.isInvertScroll(ctx));
        switchHaptic.setChecked(AppSettings.isHapticEnabled(ctx));

        float mouseSens = AppSettings.getMouseSensitivity(ctx);
        int mouseProgress = Math.max(0, Math.min(20, Math.round((mouseSens - 0.5f) / 0.1f)));
        seekMouseSensitivity.setProgress(mouseProgress);
        txtMouseSensitivityVal.setText(String.format(Locale.US, "%.1fx", 0.5f + mouseProgress * 0.1f));

        float scrollSens = AppSettings.getScrollSensitivity(ctx);
        int scrollProgress = Math.max(0, Math.min(20, Math.round((scrollSens - 0.5f) / 0.1f)));
        seekScrollSensitivity.setProgress(scrollProgress);
        txtScrollSensitivityVal.setText(String.format(Locale.US, "%.1fx", 0.5f + scrollProgress * 0.1f));

        isBindingUi = false;
    }

    private void setupListeners() {
        findViewById(R.id.btnCloseAppSettings).setOnClickListener(v -> {
            haptic(v);
            dismiss();
        });
        findViewById(R.id.btnDoneAppSettings).setOnClickListener(v -> {
            haptic(v);
            dismiss();
        });
        findViewById(R.id.btnResetAppSettings).setOnClickListener(v -> {
            haptic(v);
            AppSettings.resetDefaults(getContext());
            loadValuesToUi();
            notifyChanged();
            Toast.makeText(getContext(), R.string.settings_reset_done, Toast.LENGTH_SHORT).show();
        });

        btnMonitorModeSingle.setOnClickListener(v -> {
            haptic(v);
            AppSettings.setDefaultMonitorMode(getContext(), AppSettings.MONITOR_MODE_SINGLE);
            loadValuesToUi();
            notifyChanged();
        });
        btnMonitorModeSpan.setOnClickListener(v -> {
            haptic(v);
            AppSettings.setDefaultMonitorMode(getContext(), AppSettings.MONITOR_MODE_SPAN);
            loadValuesToUi();
            notifyChanged();
        });
        btnMonitorModeLast.setOnClickListener(v -> {
            haptic(v);
            AppSettings.setDefaultMonitorMode(getContext(), AppSettings.MONITOR_MODE_LAST);
            loadValuesToUi();
            notifyChanged();
        });

        btnSpanZoomFitAll.setOnClickListener(v -> setSpanZoom(v, 0f));
        btnSpanZoom1x.setOnClickListener(v -> setSpanZoom(v, 1.0f));
        btnSpanZoom15x.setOnClickListener(v -> setSpanZoom(v, 1.5f));
        btnSpanZoom2x.setOnClickListener(v -> setSpanZoom(v, 2.0f));

        btnSingleTargetPrimary.setOnClickListener(v -> {
            haptic(v);
            AppSettings.setDefaultSingleMonitor(getContext(), AppSettings.SINGLE_MONITOR_PRIMARY);
            loadValuesToUi();
            notifyChanged();
        });
        btnSingleTargetFirst.setOnClickListener(v -> {
            haptic(v);
            AppSettings.setDefaultSingleMonitor(getContext(), AppSettings.SINGLE_MONITOR_FIRST);
            loadValuesToUi();
            notifyChanged();
        });
        btnSingleTargetLast.setOnClickListener(v -> {
            haptic(v);
            AppSettings.setDefaultSingleMonitor(getContext(), AppSettings.SINGLE_MONITOR_LAST);
            loadValuesToUi();
            notifyChanged();
        });

        btnStartupTabScreen.setOnClickListener(v -> {
            haptic(v);
            AppSettings.setStartupViewMode(getContext(), AppSettings.VIEW_MODE_SCREEN);
            loadValuesToUi();
            notifyChanged();
        });
        btnStartupTabTrackpad.setOnClickListener(v -> {
            haptic(v);
            AppSettings.setStartupViewMode(getContext(), AppSettings.VIEW_MODE_TRACKPAD);
            loadValuesToUi();
            notifyChanged();
        });

        btnFps2.setOnClickListener(v -> setFps(v, 2));
        btnFps15.setOnClickListener(v -> setFps(v, 15));
        btnFps30.setOnClickListener(v -> setFps(v, 30));
        btnFps60.setOnClickListener(v -> setFps(v, 60));

        btnSingleZoom10.setOnClickListener(v -> setSingleZoom(v, 1.0f));
        btnSingleZoom15.setOnClickListener(v -> setSingleZoom(v, 1.5f));
        btnSingleZoom20.setOnClickListener(v -> setSingleZoom(v, 2.0f));
        btnSingleZoom25.setOnClickListener(v -> setSingleZoom(v, 2.5f));

        switchFollowCursor.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isBindingUi) return;
            AppSettings.setFollowCursorDefault(getContext(), isChecked);
            notifyChanged();
        });
        switchStartFullscreen.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isBindingUi) return;
            AppSettings.setStartFullscreen(getContext(), isChecked);
            notifyChanged();
        });
        switchKeepScreenOn.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isBindingUi) return;
            AppSettings.setKeepScreenOn(getContext(), isChecked);
            notifyChanged();
        });
        switchInvertScroll.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isBindingUi) return;
            AppSettings.setInvertScroll(getContext(), isChecked);
            notifyChanged();
        });
        switchHaptic.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isBindingUi) return;
            AppSettings.setHapticEnabled(getContext(), isChecked);
            notifyChanged();
        });

        seekMouseSensitivity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float val = 0.5f + progress * 0.1f;
                txtMouseSensitivityVal.setText(String.format(Locale.US, "%.1fx", val));
                if (fromUser && !isBindingUi) {
                    AppSettings.setMouseSensitivity(getContext(), val);
                    notifyChanged();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        seekScrollSensitivity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float val = 0.5f + progress * 0.1f;
                txtScrollSensitivityVal.setText(String.format(Locale.US, "%.1fx", val));
                if (fromUser && !isBindingUi) {
                    AppSettings.setScrollSensitivity(getContext(), val);
                    notifyChanged();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }

    private void setSpanZoom(View v, float zoom) {
        haptic(v);
        AppSettings.setSpanInitialZoom(getContext(), zoom);
        loadValuesToUi();
        notifyChanged();
    }

    private void setSingleZoom(View v, float zoom) {
        haptic(v);
        AppSettings.setSingleInitialZoom(getContext(), zoom);
        loadValuesToUi();
        notifyChanged();
    }

    private void setFps(View v, int fps) {
        haptic(v);
        AppSettings.setScreenFps(getContext(), fps);
        loadValuesToUi();
        notifyChanged();
    }

    private void styleOptionButton(MaterialButton btn, boolean active, int activeColorRes) {
        if (btn == null) return;
        if (active) {
            int bgColor = ContextCompat.getColor(getContext(), activeColorRes);
            int txtColor = (activeColorRes == R.color.accent_amber || activeColorRes == R.color.accent_cyan || activeColorRes == R.color.accent_emerald)
                    ? ContextCompat.getColor(getContext(), R.color.bg_dark)
                    : ContextCompat.getColor(getContext(), R.color.text_primary);
            btn.setBackgroundTintList(ColorStateList.valueOf(bgColor));
            btn.setTextColor(txtColor);
        } else {
            btn.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(getContext(), R.color.surface_card)));
            btn.setTextColor(ContextCompat.getColor(getContext(), R.color.text_secondary));
        }
    }

    private void haptic(View v) {
        if (v != null && AppSettings.isHapticEnabled(getContext())) {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        }
    }

    private void notifyChanged() {
        if (listener != null) {
            listener.onSettingsChanged();
        }
    }
}
