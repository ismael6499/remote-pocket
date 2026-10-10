package com.remotepocket.ui;

import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.AttributeSet;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.ImageView;

import androidx.annotation.Nullable;

import com.remotepocket.network.WebSocketManager;

public class TouchpadView extends View {
    private WebSocketManager webSocketManager;
    private Vibrator vibrator;
    private ImageView zoomTargetView;

    private float lastX, lastY;
    private float lastTwoFingerY;
    private float accumX = 0f;
    private float accumY = 0f;

    private long touchDownTime;
    private boolean isMultiTouch = false;
    private boolean hasMoved = false;
    private boolean isDragging = false;
    private boolean isScaling = false;

    private static final int TAP_TIMEOUT_MS = 240;
    private static final int LONG_PRESS_TIMEOUT_MS = 380;
    private static final float MOVE_SLOP = 12f;
    private static final float SENSITIVITY = 1.35f;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable longPressRunnable;
    private ScaleGestureDetector scaleGestureDetector;
    private float currentScale = 1.0f;

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

        scaleGestureDetector = new ScaleGestureDetector(getContext(), new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScaleBegin(ScaleGestureDetector detector) {
                isScaling = true;
                return true;
            }

            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                if (zoomTargetView != null) {
                    currentScale *= detector.getScaleFactor();
                    currentScale = Math.max(1.0f, Math.min(currentScale, 4.0f));

                    zoomTargetView.setPivotX(detector.getFocusX());
                    zoomTargetView.setPivotY(detector.getFocusY());
                    zoomTargetView.setScaleX(currentScale);
                    zoomTargetView.setScaleY(currentScale);
                }
                return true;
            }

            @Override
            public void onScaleEnd(ScaleGestureDetector detector) {
                isScaling = false;
            }
        });
    }

    public void setWebSocketManager(WebSocketManager webSocketManager) {
        this.webSocketManager = webSocketManager;
    }

    public void setZoomTarget(ImageView view) {
        this.zoomTargetView = view;
    }

    public void resetZoom() {
        currentScale = 1.0f;
        if (zoomTargetView != null) {
            zoomTargetView.setScaleX(1.0f);
            zoomTargetView.setScaleY(1.0f);
        }
    }

    private void triggerHaptic(int durationMs) {
        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        if (vibrator != null && vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(durationMs);
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (scaleGestureDetector != null && event.getPointerCount() >= 2) {
            scaleGestureDetector.onTouchEvent(event);
        }

        if (webSocketManager == null || !webSocketManager.isConnected()) {
            return super.onTouchEvent(event);
        }

        int pointerCount = event.getPointerCount();
        int action = event.getActionMasked();

        switch (action) {
            case MotionEvent.ACTION_DOWN:
                lastX = event.getX();
                lastY = event.getY();
                accumX = 0f;
                accumY = 0f;
                touchDownTime = System.currentTimeMillis();
                hasMoved = false;
                isMultiTouch = false;
                isScaling = false;

                // Schedule long press to Hold & Drag
                cancelLongPress();
                longPressRunnable = () -> {
                    if (!hasMoved && !isMultiTouch) {
                        isDragging = true;
                        triggerHaptic(45);
                        webSocketManager.sendMouseDown("left");
                    }
                };
                handler.postDelayed(longPressRunnable, LONG_PRESS_TIMEOUT_MS);
                break;

            case MotionEvent.ACTION_POINTER_DOWN:
                cancelLongPress();
                if (pointerCount == 2) {
                    isMultiTouch = true;
                    lastTwoFingerY = (event.getY(0) + event.getY(1)) / 2f;
                }
                break;

            case MotionEvent.ACTION_MOVE:
                if (pointerCount == 1 && !isMultiTouch && !isScaling) {
                    float currentX = event.getX();
                    float currentY = event.getY();
                    float deltaRawX = currentX - lastX;
                    float deltaRawY = currentY - lastY;

                    if (Math.abs(deltaRawX) > MOVE_SLOP || Math.abs(deltaRawY) > MOVE_SLOP) {
                        hasMoved = true;
                        if (!isDragging) {
                            cancelLongPress();
                        }
                    }

                    accumX += deltaRawX * SENSITIVITY;
                    accumY += deltaRawY * SENSITIVITY;

                    int moveX = Math.round(accumX);
                    int moveY = Math.round(accumY);

                    if (moveX != 0 || moveY != 0) {
                        webSocketManager.sendMouseMove(moveX, moveY);
                        accumX -= moveX;
                        accumY -= moveY;
                    }

                    lastX = currentX;
                    lastY = currentY;
                } else if (pointerCount == 2 && !isScaling) {
                    // Two finger scroll
                    float currentTwoFingerY = (event.getY(0) + event.getY(1)) / 2f;
                    float scrollDelta = currentTwoFingerY - lastTwoFingerY;

                    if (Math.abs(scrollDelta) > 5f) {
                        hasMoved = true;
                        int scrollStep = Math.round(scrollDelta * 14f);
                        webSocketManager.sendMouseScroll(scrollStep);
                        lastTwoFingerY = currentTwoFingerY;
                    }
                }
                break;

            case MotionEvent.ACTION_POINTER_UP:
                cancelLongPress();
                break;

            case MotionEvent.ACTION_UP:
                cancelLongPress();
                long duration = System.currentTimeMillis() - touchDownTime;

                if (isDragging) {
                    // Release drag
                    triggerHaptic(25);
                    webSocketManager.sendMouseUp("left");
                    isDragging = false;
                } else if (!hasMoved && duration < TAP_TIMEOUT_MS && !isScaling) {
                    if (pointerCount == 3) {
                        // 3 Finger Tap -> Middle Click
                        triggerHaptic(35);
                        webSocketManager.sendMouseClick("middle");
                    } else if (isMultiTouch || pointerCount == 2) {
                        // 2 Finger Tap -> Right Click
                        triggerHaptic(30);
                        webSocketManager.sendMouseClick("right");
                    } else {
                        // Single Finger Tap -> Left Click
                        triggerHaptic(20);
                        webSocketManager.sendMouseClick("left");
                    }
                }

                isMultiTouch = false;
                hasMoved = false;
                isScaling = false;
                break;

            case MotionEvent.ACTION_CANCEL:
                cancelLongPress();
                if (isDragging) {
                    webSocketManager.sendMouseUp("left");
                    isDragging = false;
                }
                isMultiTouch = false;
                hasMoved = false;
                isScaling = false;
                break;
        }

        return true;
    }

    @Override
    public void cancelLongPress() {
        super.cancelLongPress();
        if (longPressRunnable != null) {
            handler.removeCallbacks(longPressRunnable);
            longPressRunnable = null;
        }
    }
}
