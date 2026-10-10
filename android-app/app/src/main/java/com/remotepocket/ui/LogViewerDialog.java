package com.remotepocket.ui;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.HapticFeedbackConstants;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.remotepocket.R;
import com.remotepocket.util.AppLogger;

public class LogViewerDialog extends Dialog {
    private final String currentServerUrl;
    private TextView txtLogContent;
    private ScrollView scrollLogs;

    public LogViewerDialog(@NonNull Context context, String currentServerUrl) {
        super(context);
        this.currentServerUrl = currentServerUrl;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.dialog_log_viewer);

        Window window = getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        txtLogContent = findViewById(R.id.txtLogContent);
        scrollLogs = findViewById(R.id.scrollLogs);

        findViewById(R.id.btnCloseLogs).setOnClickListener(v -> dismiss());

        findViewById(R.id.btnCopyLogs).setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            AppLogger.copyToClipboard(getContext());
        });

        findViewById(R.id.btnSendLogs).setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            AppLogger.sendToServer(getContext(), currentServerUrl, null);
        });

        findViewById(R.id.btnClearLogs).setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            AppLogger.clear();
            refreshLogs();
        });

        refreshLogs();

        AppLogger.setListener(entry -> {
            if (txtLogContent != null) {
                txtLogContent.append("\n" + entry);
                scrollLogs.post(() -> scrollLogs.fullScroll(ScrollView.FOCUS_DOWN));
            }
        });
    }

    private void refreshLogs() {
        if (txtLogContent != null) {
            StringBuilder sb = new StringBuilder();
            for (String entry : AppLogger.getEntries()) {
                if (sb.length() > 0) sb.append("\n");
                sb.append(entry);
            }
            txtLogContent.setText(sb.toString());
            scrollLogs.post(() -> scrollLogs.fullScroll(ScrollView.FOCUS_DOWN));
        }
    }

    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        AppLogger.setListener(null);
    }
}
