package com.remotepocket.ui;

import android.content.Context;
import android.os.Vibrator;
import android.util.AttributeSet;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

import com.remotepocket.network.WebSocketManager;

public class TouchpadView extends View {
    private WebSocketManager webSocketManager;
    private Vibrator vibrator;

    private float lastX, lastY;
    private float lastTwoFingerY;
    private long touchDownTime;
    private boolean isMultiTouch = false;
    private boolean hasMoved = false;
    private boolean isDragging = false;

    private static final int TAP_TIMEOUT_MS = 220;
    private static final float MOVE_SLOP = 10f;
    private static final float SENSITIVITY = 1.35f;

    public TouchpadView(Context context) {
        super(context);
        init();
    }

    public TouchpadView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public TouchpadView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setClickable(true);
        setFocusable(true);
        vibrator = (Vibrator) getContext().getSystemService(Context.VIBRATOR_SERVICE);
    }

    public void setWebSocketManager(WebSocketManager webSocketManager) {
        this.webSocketManager = webSocketManager;
    }

    private void triggerHaptic() {
        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (webSocketManager == null || !webSocketManager.isConnected()) {
            return super.onTouchEvent(event);
        }

        int pointerCount = event.getPointerCount();
        int action = event.getActionMasked();

        switch (action) {
            case MotionEvent.ACTION_DOWN:
                lastX = event.getX();
                lastY = event.getY();
                touchDownTime = System.currentTimeMillis();
                hasMoved = false;
                isMultiTouch = false;
                break;

            case MotionEvent.ACTION_POINTER_DOWN:
                if (pointerCount == 2) {
                    isMultiTouch = true;
                    lastTwoFingerY = (event.getY(0) + event.getY(1)) / 2f;
                }
                break;

            case MotionEvent.ACTION_MOVE:
                if (pointerCount == 1 && !isMultiTouch) {
                    float currentX = event.getX();
                    float currentY = event.getY();
                    float dx = (currentX - lastX) * SENSITIVITY;
                    float dy = (currentY - lastY) * SENSITIVITY;

                    if (Math.abs(dx) > 0.5f || Math.abs(dy) > 0.5f) {
                        hasMoved = true;
                        webSocketManager.sendMouseMove(Math.round(dx), Math.round(dy));
                    }
                    lastX = currentX;
                    lastY = currentY;
                } else if (pointerCount >= 2) {
                    float currentTwoFingerY = (event.getY(0) + event.getY(1)) / 2f;
                    float scrollDelta = currentTwoFingerY - lastTwoFingerY;

                    if (Math.abs(scrollDelta) > 4f) {
                        hasMoved = true;
                        int scrollStep = Math.round(scrollDelta * 12f);
                        webSocketManager.sendMouseScroll(scrollStep);
                        lastTwoFingerY = currentTwoFingerY;
                    }
                }
                break;

            case MotionEvent.ACTION_POINTER_UP:
                break;

            case MotionEvent.ACTION_UP:
                long duration = System.currentTimeMillis() - touchDownTime;
                if (!hasMoved && duration < TAP_TIMEOUT_MS) {
                    if (isMultiTouch) {
                        // Two finger tap -> Right Click
                        triggerHaptic();
                        webSocketManager.sendMouseClick("right");
                    } else {
                        // Single finger tap -> Left Click
                        triggerHaptic();
                        webSocketManager.sendMouseClick("left");
                    }
                }

                if (isDragging) {
                    webSocketManager.sendMouseUp("left");
                    isDragging = false;
                }
                isMultiTouch = false;
                hasMoved = false;
                break;

            case MotionEvent.ACTION_CANCEL:
                if (isDragging) {
                    webSocketManager.sendMouseUp("left");
                    isDragging = false;
                }
                isMultiTouch = false;
                hasMoved = false;
                break;
        }

        return true;
    }
}
