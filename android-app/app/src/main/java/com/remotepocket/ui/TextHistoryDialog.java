package com.remotepocket.ui;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.remotepocket.R;
import com.remotepocket.util.TextHistoryItem;
import com.remotepocket.util.TextHistoryManager;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class TextHistoryDialog extends Dialog {

    public interface OnTextActionListener {
        void onRestoreText(String text);
        void onResendText(String text);
    }

    private final OnTextActionListener listener;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM HH:mm:ss", Locale.getDefault());

    private LinearLayout layoutHistoryList;
    private View cardQuickRestore;
    private TextView txtQuickRestorePreview;
    private TextView txtHistoryEmpty;
    private MaterialButton btnFilterSession;
    private MaterialButton btnFilterAll;
    private boolean filterThisSessionOnly = true;

    private View cardPcClipboard;
    private TextView txtPcClipboardPreview;
    private MaterialButton btnCopyPcClipToPhone;
    private String pcClipboardContent = null;

    public TextHistoryDialog(@NonNull Context context, OnTextActionListener listener) {
        super(context);
        this.listener = listener;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.dialog_text_history);

        Window window = getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        layoutHistoryList = findViewById(R.id.layoutHistoryList);
        cardQuickRestore = findViewById(R.id.cardQuickRestore);
        txtQuickRestorePreview = findViewById(R.id.txtQuickRestorePreview);
        txtHistoryEmpty = findViewById(R.id.txtHistoryEmpty);
        btnFilterSession = findViewById(R.id.btnFilterSession);
        btnFilterAll = findViewById(R.id.btnFilterAll);

        cardPcClipboard = findViewById(R.id.cardPcClipboard);
        txtPcClipboardPreview = findViewById(R.id.txtPcClipboardPreview);
        btnCopyPcClipToPhone = findViewById(R.id.btnCopyPcClipToPhone);

        if (cardPcClipboard != null) {
            cardPcClipboard.setOnClickListener(v -> {
                if (pcClipboardContent != null && !pcClipboardContent.isEmpty()) {
                    v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                    if (listener != null) {
                        listener.onRestoreText(pcClipboardContent);
                    }
                    dismiss();
                }
            });
        }

        if (btnCopyPcClipToPhone != null) {
            btnCopyPcClipToPhone.setOnClickListener(v -> {
                if (pcClipboardContent != null && !pcClipboardContent.isEmpty()) {
                    ClipboardManager cm = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
                    if (cm != null) {
                        cm.setPrimaryClip(ClipData.newPlainText("PC Clipboard", pcClipboardContent));
                    }
                    v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                    Toast.makeText(getContext(), R.string.pc_clipboard_copied, Toast.LENGTH_SHORT).show();
                }
            });
        }

        if (pcClipboardContent != null && !pcClipboardContent.trim().isEmpty()) {
            setPcClipboardText(pcClipboardContent);
        }

        findViewById(R.id.btnCloseHistory).setOnClickListener(v -> dismiss());

        findViewById(R.id.btnClearHistory).setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            promptClearHistory();
        });

        btnFilterSession.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            filterThisSessionOnly = true;
            updateFilterTabs();
            renderHistory();
        });

        btnFilterAll.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            filterThisSessionOnly = false;
            updateFilterTabs();
            renderHistory();
        });

        updateFilterTabs();
        renderHistory();
    }

    private void updateFilterTabs() {
        Context context = getContext();
        int activeBg = ContextCompat.getColor(context, R.color.accent_indigo);
        int inactiveBg = Color.TRANSPARENT;
        int activeText = ContextCompat.getColor(context, R.color.text_primary);
        int inactiveText = ContextCompat.getColor(context, R.color.text_secondary);

        int sessionCount = TextHistoryManager.getInstance(context).getCurrentSessionItems().size();
        int allCount = TextHistoryManager.getInstance(context).getAllItems().size();

        btnFilterSession.setText(context.getString(R.string.history_filter_session, sessionCount));
        btnFilterAll.setText(context.getString(R.string.history_filter_all, allCount));

        if (filterThisSessionOnly) {
            btnFilterSession.setBackgroundTintList(ColorStateList.valueOf(activeBg));
            btnFilterSession.setTextColor(activeText);
            btnFilterAll.setBackgroundTintList(ColorStateList.valueOf(inactiveBg));
            btnFilterAll.setTextColor(inactiveText);
        } else {
            btnFilterSession.setBackgroundTintList(ColorStateList.valueOf(inactiveBg));
            btnFilterSession.setTextColor(inactiveText);
            btnFilterAll.setBackgroundTintList(ColorStateList.valueOf(activeBg));
            btnFilterAll.setTextColor(activeText);
        }
    }

    private void renderHistory() {
        Context context = getContext();
        TextHistoryManager manager = TextHistoryManager.getInstance(context);

        // Quick restore card
        TextHistoryItem lastItem = manager.getLastSentItem();
        if (lastItem != null) {
            cardQuickRestore.setVisibility(View.VISIBLE);
            txtQuickRestorePreview.setText(lastItem.getText().replace('\n', ' '));
            cardQuickRestore.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                if (listener != null) {
                    listener.onRestoreText(lastItem.getText());
                }
                dismiss();
            });
        } else {
            cardQuickRestore.setVisibility(View.GONE);
        }

        // List
        List<TextHistoryItem> items = filterThisSessionOnly
                ? manager.getCurrentSessionItems()
                : manager.getAllItems();

        layoutHistoryList.removeAllViews();
        layoutHistoryList.addView(txtHistoryEmpty);

        if (items.isEmpty()) {
            txtHistoryEmpty.setVisibility(View.VISIBLE);
            return;
        }

        txtHistoryEmpty.setVisibility(View.GONE);
        LayoutInflater inflater = LayoutInflater.from(context);
        long currentSessionId = manager.getCurrentSessionId();

        for (TextHistoryItem item : items) {
            View itemView = inflater.inflate(R.layout.item_text_history, layoutHistoryList, false);

            TextView txtItemTime = itemView.findViewById(R.id.txtItemTime);
            TextView txtItemSessionBadge = itemView.findViewById(R.id.txtItemSessionBadge);
            TextView txtItemText = itemView.findViewById(R.id.txtItemText);
            ImageButton btnItemDelete = itemView.findViewById(R.id.btnItemDelete);
            MaterialButton btnItemCopy = itemView.findViewById(R.id.btnItemCopy);
            MaterialButton btnItemResend = itemView.findViewById(R.id.btnItemResend);
            MaterialButton btnItemRestore = itemView.findViewById(R.id.btnItemRestore);

            txtItemTime.setText(dateFormat.format(new Date(item.getTimestamp())));
            if (item.getSessionId() == currentSessionId) {
                txtItemSessionBadge.setVisibility(View.VISIBLE);
            } else {
                txtItemSessionBadge.setVisibility(View.GONE);
            }

            txtItemText.setText(item.getText());

            btnItemCopy.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null) {
                    cm.setPrimaryClip(ClipData.newPlainText("RemotePocket", item.getText()));
                    Toast.makeText(context, R.string.toast_text_copied, Toast.LENGTH_SHORT).show();
                }
            });

            btnItemResend.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                if (listener != null) {
                    listener.onResendText(item.getText());
                }
            });

            btnItemRestore.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                if (listener != null) {
                    listener.onRestoreText(item.getText());
                }
                dismiss();
            });

            btnItemDelete.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                manager.deleteItem(item.getId());
                updateFilterTabs();
                renderHistory();
            });

            layoutHistoryList.addView(itemView);
        }
    }

    public void setPcClipboardText(String text) {
        this.pcClipboardContent = text;
        if (cardPcClipboard != null && txtPcClipboardPreview != null) {
            if (text != null && !text.trim().isEmpty()) {
                cardPcClipboard.setVisibility(View.VISIBLE);
                txtPcClipboardPreview.setText(text.replace('\n', ' '));
            } else {
                cardPcClipboard.setVisibility(View.GONE);
            }
        }
    }

    private void promptClearHistory() {
        new AlertDialog.Builder(getContext())
                .setTitle(R.string.confirm_clear_history_title)
                .setMessage(R.string.confirm_clear_history_msg)
                .setPositiveButton(R.string.btn_clear_logs, (dialog, which) -> {
                    TextHistoryManager.getInstance(getContext()).clearHistory();
                    updateFilterTabs();
                    renderHistory();
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }
}
