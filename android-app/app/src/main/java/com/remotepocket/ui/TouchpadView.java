package com.remotepocket.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;

import androidx.annotation.Nullable;

import com.remotepocket.network.WebSocketManager;

public class TouchpadView extends View {
    public interface OnZoomChangeListener {
        void onZoomChanged(float scale, boolean followCursor);
    }

    private WebSocketManager webSocketManager;
    private Vibrator vibrator;
    private ImageView zoomTargetView;
    private OnZoomChangeListener zoomChangeListener;

    // Server-reported Desktop & Cursor Coordinates
    private int curX = -1, curY = -1;
    private int scrW = 1920, scrH = 1080;

    // Viewport Zoom & Translation
    private float currentScale = 1.0f;
    private float currentTransX = 0f;
    private float currentTransY = 0f;
    private boolean followCursor = true;
    private boolean isScreenMode = false;

    // Touch interaction tracking
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
    private GestureDetector gestureDetector;

    // Clean Windows cursor arrow graphics
    private Paint paintCursorFill;
    private Paint paintCursorStroke;
    private android.graphics.Path cursorPath;

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
        setWillNotDraw(false);
        vibrator = (Vibrator) getContext().getSystemService(Context.VIBRATOR_SERVICE);

        // Classic crisp Windows cursor arrow
        paintCursorFill = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintCursorFill.setStyle(Paint.Style.FILL);
        paintCursorFill.setColor(Color.WHITE);

        paintCursorStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintCursorStroke.setStyle(Paint.Style.STROKE);
        paintCursorStroke.setColor(Color.BLACK);
        paintCursorStroke.setStrokeWidth(2.0f);
        paintCursorStroke.setStrokeJoin(Paint.Join.ROUND);

        cursorPath = new android.graphics.Path();
        float s = getResources().getDisplayMetrics().density * 1.15f;
        cursorPath.moveTo(0 * s, 0 * s);
        cursorPath.lineTo(0 * s, 17 * s);
        cursorPath.lineTo(4.5f * s, 13 * s);
        cursorPath.lineTo(8 * s, 21 * s);
        cursorPath.lineTo(11 * s, 19.5f * s);
        cursorPath.lineTo(7.5f * s, 12 * s);
        cursorPath.lineTo(13 * s, 12 * s);
        cursorPath.close();

        // Double-Tap sends double-click directly to PC
        gestureDetector = new GestureDetector(getContext(), new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDoubleTap(MotionEvent e) {
                if (webSocketManager != null && webSocketManager.isConnected()) {
                    triggerHaptic(30);
                    webSocketManager.sendMouseDoubleClick("left");
                    return true;
                }
                return false;
            }
        });

        // Pinch-to-zoom updates scale and keeps cursor centered
        scaleGestureDetector = new ScaleGestureDetector(getContext(), new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScaleBegin(ScaleGestureDetector detector) {
                isScaling = true;
                return true;
            }

            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                currentScale *= detector.getScaleFactor();
                currentScale = Math.max(1.0f, Math.min(currentScale, 4.0f));

                if (followCursor && currentScale > 1.0f) {
                    applyCenterTranslationDirect();
                } else if (zoomTargetView != null) {
                    zoomTargetView.setScaleX(currentScale);
                    zoomTargetView.setScaleY(currentScale);
                }

                notifyZoomChanged();
                invalidate();
                return true;
            }

            @Override
            public void onScaleEnd(ScaleGestureDetector detector) {
                isScaling = false;
                if (currentScale <= 1.05f) {
                    resetZoom();
                } else if (followCursor) {
                    applyCenterTranslationDirect();
                }
                notifyZoomChanged();
                invalidate();
            }
        });
    }

    public void setWebSocketManager(WebSocketManager webSocketManager) {
        this.webSocketManager = webSocketManager;
    }

    public void setZoomTarget(ImageView view) {
        this.zoomTargetView = view;
    }

    public void setOnZoomChangeListener(OnZoomChangeListener listener) {
        this.zoomChangeListener = listener;
    }

    public void setScreenMode(boolean screenMode) {
        this.isScreenMode = screenMode;
        if (!screenMode) {
            resetZoom();
        } else if (currentScale < 1.5f) {
            currentScale = 2.0f;
            post(() -> {
                applyCenterTranslationDirect();
                invalidate();
            });
        }
        notifyZoomChanged();
        invalidate();
    }

    public void setFollowCursor(boolean follow) {
        this.followCursor = follow;
        if (follow && currentScale > 1.0f) {
            applyCenterTranslationDirect();
        }
        notifyZoomChanged();
        invalidate();
    }

    public boolean isFollowCursor() {
        return followCursor;
    }

    public void toggleFollowCursor() {
        setFollowCursor(!followCursor);
    }

    public float getCurrentScale() {
        return currentScale;
    }

    public void cycleZoomLevel() {
        if (currentScale < 1.5f) {
            currentScale = 2.0f;
        } else if (currentScale < 2.5f) {
            currentScale = 3.0f;
        } else {
            currentScale = 1.0f;
        }
        applyCenterTranslationDirect();
        notifyZoomChanged();
        invalidate();
    }

    public void resetZoom() {
        currentScale = 1.0f;
        currentTransX = 0f;
        currentTransY = 0f;
        if (zoomTargetView != null) {
            zoomTargetView.setTranslationX(0f);
            zoomTargetView.setTranslationY(0f);
            zoomTargetView.setScaleX(1.0f);
            zoomTargetView.setScaleY(1.0f);
        }
        notifyZoomChanged();
        invalidate();
    }

    private void notifyZoomChanged() {
        if (zoomChangeListener != null) {
            zoomChangeListener.onZoomChanged(currentScale, followCursor);
        }
    }

    /**
     * Receives authoritative desktop dimensions and cursor position from PC.
     * NOTE: Does NOT pan or jerk the camera if the mouse was moved on the PC!
     */
    public void updateServerCursorPosition(int x, int y, int w, int h) {
        this.scrW = w > 0 ? w : this.scrW;
        this.scrH = h > 0 ? h : this.scrH;

        if (this.curX < 0 || this.curY < 0) {
            this.curX = x >= 0 ? x : scrW / 2;
            this.curY = y >= 0 ? y : scrH / 2;
            if (followCursor && currentScale > 1.0f) {
                applyCenterTranslationDirect();
            }
        } else if (!hasMoved && !isDragging) {
            // Keep coordinates up to date, but NEVER pan viewport on PC mouse movement
            this.curX = x;
            this.curY = y;
        }
        invalidate();
    }

    /**
     * Instantly locks viewport translation so mouse cursor is centered on phone screen (0ms latency).
     */
    public void applyCenterTranslationDirect() {
        if (zoomTargetView == null || scrW <= 0 || scrH <= 0) return;
        int viewW = getWidth();
        int viewH = getHeight();
        if (viewW <= 0 || viewH <= 0) return;

        if (currentScale <= 1.01f) {
            currentTransX = 0f;
            currentTransY = 0f;
            zoomTargetView.setTranslationX(0f);
            zoomTargetView.setTranslationY(0f);
            zoomTargetView.setScaleX(1.0f);
            zoomTargetView.setScaleY(1.0f);
            return;
        }

        float scaleFit = Math.min((float) viewW / scrW, (float) viewH / scrH);
        float renderW = scrW * scaleFit;
        float renderH = scrH * scaleFit;
        float offsetX = (viewW - renderW) / 2f;
        float offsetY = (viewH - renderH) / 2f;

        float normCurX = curX >= 0 ? curX : scrW / 2f;
        float normCurY = curY >= 0 ? curY : scrH / 2f;

        float cursorViewX = offsetX + (normCurX / (float) scrW) * renderW;
        float cursorViewY = offsetY + (normCurY / (float) scrH) * renderH;

        float targetTx = - (cursorViewX - viewW / 2f) * currentScale;
        float targetTy = - (cursorViewY - viewH / 2f) * currentScale;

        float scaledW = renderW * currentScale;
        float scaledH = renderH * currentScale;
        float limitTx = (scaledW / 2f) + (viewW * 0.45f);
        float limitTy = (scaledH / 2f) + (viewH * 0.45f);

        currentTransX = Math.max(-limitTx, Math.min(limitTx, targetTx));
        currentTransY = Math.max(-limitTy, Math.min(limitTy, targetTy));

        zoomTargetView.setPivotX(viewW / 2f);
        zoomTargetView.setPivotY(viewH / 2f);
        zoomTargetView.setScaleX(currentScale);
        zoomTargetView.setScaleY(currentScale);
        zoomTargetView.setTranslationX(currentTransX);
        zoomTargetView.setTranslationY(currentTransY);
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

        if (gestureDetector != null) {
            gestureDetector.onTouchEvent(event);
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
                    if (!hasMoved && !isMultiTouch && !isScaling) {
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

                        // Predictive instant cursor update for 0ms camera tracking
                        if (curX >= 0 && curY >= 0) {
                            curX = Math.max(0, Math.min(scrW, curX + moveX));
                            curY = Math.max(0, Math.min(scrH, curY + moveY));
                            if (isScreenMode && followCursor && currentScale > 1.05f) {
                                applyCenterTranslationDirect();
                            }
                            invalidate();
                        }
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
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (!isScreenMode || scrW <= 0 || scrH <= 0) {
            return;
        }

        int viewW = getWidth();
        int viewH = getHeight();
        if (viewW <= 0 || viewH <= 0) return;

        float pointerX;
        float pointerY;

        if (currentScale > 1.05f && followCursor) {
            // In centered follow mode: mouse cursor is locked at the exact center of mobile screen
            pointerX = viewW / 2f;
            pointerY = viewH / 2f;
        } else {
            float scaleFit = Math.min((float) viewW / scrW, (float) viewH / scrH);
            float renderW = scrW * scaleFit;
            float renderH = scrH * scaleFit;
            float offsetX = (viewW - renderW) / 2f;
            float offsetY = (viewH - renderH) / 2f;

            float normCurX = curX >= 0 ? curX : scrW / 2f;
            float normCurY = curY >= 0 ? curY : scrH / 2f;

            float cursorViewX = offsetX + (normCurX / (float) scrW) * renderW;
            float cursorViewY = offsetY + (normCurY / (float) scrH) * renderH;

            pointerX = viewW / 2f + (cursorViewX - viewW / 2f) * currentScale + currentTransX;
            pointerY = viewH / 2f + (cursorViewY - viewH / 2f) * currentScale + currentTransY;
        }

        // Draw crisp iconic Windows mouse arrow (tip at pointerX, pointerY)
        if (pointerX >= -30 && pointerX <= viewW + 30 && pointerY >= -30 && pointerY <= viewH + 30) {
            canvas.save();
            canvas.translate(pointerX, pointerY);
            canvas.drawPath(cursorPath, paintCursorFill);
            canvas.drawPath(cursorPath, paintCursorStroke);
            canvas.restore();
        }
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
