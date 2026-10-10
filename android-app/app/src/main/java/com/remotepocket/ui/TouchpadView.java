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

    // High-visibility beacon paints
    private Paint paintHalo;
    private Paint paintHaloStroke;
    private Paint paintCenterDot;
    private Paint paintCrosshair;

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

        // Configure cursor beacon graphics
        paintHalo = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintHalo.setStyle(Paint.Style.STROKE);
        paintHalo.setColor(Color.parseColor("#FFD700")); // Gold
        paintHalo.setStrokeWidth(6f);
        paintHalo.setAlpha(230);

        paintHaloStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintHaloStroke.setStyle(Paint.Style.STROKE);
        paintHaloStroke.setColor(Color.parseColor("#0F172A")); // Dark slate contrast
        paintHaloStroke.setStrokeWidth(2.5f);
        paintHaloStroke.setAlpha(200);

        paintCenterDot = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintCenterDot.setStyle(Paint.Style.FILL);
        paintCenterDot.setColor(Color.WHITE);

        paintCrosshair = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintCrosshair.setStyle(Paint.Style.STROKE);
        paintCrosshair.setColor(Color.parseColor("#FFD700"));
        paintCrosshair.setStrokeWidth(4f);

        // Gesture detector for Double-Tap (Zoom toggle in Screen mode, Double-click in Trackpad mode)
        gestureDetector = new GestureDetector(getContext(), new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDoubleTap(MotionEvent e) {
                if (isScreenMode) {
                    triggerHaptic(35);
                    if (currentScale > 1.2f) {
                        setZoomLevelAnimated(1.0f);
                    } else {
                        setZoomLevelAnimated(2.2f);
                    }
                    return true;
                } else if (webSocketManager != null && webSocketManager.isConnected()) {
                    triggerHaptic(30);
                    webSocketManager.sendMouseDoubleClick("left");
                    return true;
                }
                return false;
            }
        });

        // Scale gesture detector for Pinch-to-Zoom
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
                    updateViewportTranslation(false);
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
                    updateViewportTranslation(false);
                }
                notifyZoomChanged();
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
            // Default to comfortable 2.0x zoomed follow mode when opening Live Screen
            setZoomLevelAnimated(2.0f);
        }
        invalidate();
    }

    public void setFollowCursor(boolean follow) {
        this.followCursor = follow;
        if (follow && currentScale > 1.0f) {
            updateViewportTranslation(true);
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
            setZoomLevelAnimated(2.0f);
        } else if (currentScale < 2.5f) {
            setZoomLevelAnimated(3.0f);
        } else {
            setZoomLevelAnimated(1.0f);
        }
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

    public void setZoomLevelAnimated(float targetScale) {
        currentScale = Math.max(1.0f, Math.min(4.0f, targetScale));
        if (zoomTargetView != null) {
            int viewW = getWidth();
            int viewH = getHeight();
            if (viewW > 0 && viewH > 0) {
                float targetTx = 0f;
                float targetTy = 0f;

                if (currentScale > 1.01f && scrW > 0 && scrH > 0) {
                    float scaleFit = Math.min((float) viewW / scrW, (float) viewH / scrH);
                    float renderW = scrW * scaleFit;
                    float renderH = scrH * scaleFit;
                    float offsetX = (viewW - renderW) / 2f;
                    float offsetY = (viewH - renderH) / 2f;
                    float normCurX = curX >= 0 ? curX : scrW / 2f;
                    float normCurY = curY >= 0 ? curY : scrH / 2f;
                    float cursorViewX = offsetX + (normCurX / (float) scrW) * renderW;
                    float cursorViewY = offsetY + (normCurY / (float) scrH) * renderH;

                    float scaledW = renderW * currentScale;
                    float scaledH = renderH * currentScale;
                    float maxTx = Math.max(0f, (scaledW - viewW) / 2f);
                    float maxTy = Math.max(0f, (scaledH - viewH) / 2f);

                    targetTx = Math.max(-maxTx, Math.min(maxTx, -(cursorViewX - viewW / 2f) * currentScale));
                    targetTy = Math.max(-maxTy, Math.min(maxTy, -(cursorViewY - viewH / 2f) * currentScale));
                }

                currentTransX = targetTx;
                currentTransY = targetTy;

                zoomTargetView.setPivotX(viewW / 2f);
                zoomTargetView.setPivotY(viewH / 2f);
                zoomTargetView.animate()
                        .scaleX(currentScale)
                        .scaleY(currentScale)
                        .translationX(targetTx)
                        .translationY(targetTy)
                        .setDuration(220)
                        .setInterpolator(new DecelerateInterpolator())
                        .withEndAction(this::invalidate)
                        .start();
            }
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
     * Receives latest authoritative mouse & desktop dimensions from PC Agent frame header.
     */
    public void updateServerCursorPosition(int x, int y, int w, int h) {
        this.curX = x;
        this.curY = y;
        this.scrW = w > 0 ? w : this.scrW;
        this.scrH = h > 0 ? h : this.scrH;

        if (followCursor && currentScale > 1.0f && !isScaling) {
            updateViewportTranslation(true);
        }
        invalidate();
    }

    /**
     * Calculates and applies viewport translation so the camera stays centered on the mouse.
     */
    public void updateViewportTranslation(boolean smooth) {
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
            zoomTargetView.setPivotX(viewW / 2f);
            zoomTargetView.setPivotY(viewH / 2f);
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

        zoomTargetView.setPivotX(viewW / 2f);
        zoomTargetView.setPivotY(viewH / 2f);
        zoomTargetView.setScaleX(currentScale);
        zoomTargetView.setScaleY(currentScale);

        float scaledW = renderW * currentScale;
        float scaledH = renderH * currentScale;
        float maxTx = Math.max(0f, (scaledW - viewW) / 2f);
        float maxTy = Math.max(0f, (scaledH - viewH) / 2f);

        float targetTx = - (cursorViewX - viewW / 2f) * currentScale;
        float targetTy = - (cursorViewY - viewH / 2f) * currentScale;

        targetTx = Math.max(-maxTx, Math.min(maxTx, targetTx));
        targetTy = Math.max(-maxTy, Math.min(maxTy, targetTy));

        if (smooth) {
            currentTransX += (targetTx - currentTransX) * 0.45f;
            currentTransY += (targetTy - currentTransY) * 0.45f;
        } else {
            currentTransX = targetTx;
            currentTransY = targetTy;
        }

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
                            if (followCursor && currentScale > 1.0f) {
                                updateViewportTranslation(false);
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

        if (!isScreenMode || curX < 0 || curY < 0 || scrW <= 0 || scrH <= 0) {
            return;
        }

        int viewW = getWidth();
        int viewH = getHeight();
        if (viewW <= 0 || viewH <= 0) return;

        float scaleFit = Math.min((float) viewW / scrW, (float) viewH / scrH);
        float renderW = scrW * scaleFit;
        float renderH = scrH * scaleFit;
        float offsetX = (viewW - renderW) / 2f;
        float offsetY = (viewH - renderH) / 2f;

        float cursorViewX = offsetX + (curX / (float) scrW) * renderW;
        float cursorViewY = offsetY + (curY / (float) scrH) * renderH;

        float screenX = viewW / 2f + (cursorViewX - viewW / 2f) * currentScale + currentTransX;
        float screenY = viewH / 2f + (cursorViewY - viewH / 2f) * currentScale + currentTransY;

        // Draw glowing beacon target reticle if on/near screen
        if (screenX >= -40 && screenX <= viewW + 40 && screenY >= -40 && screenY <= viewH + 40) {
            float r = 22f;
            // Contrast shadow outlines
            canvas.drawCircle(screenX, screenY, r + 2.5f, paintHaloStroke);
            canvas.drawCircle(screenX, screenY, r, paintHalo);
            canvas.drawCircle(screenX, screenY, r - 2.5f, paintHaloStroke);

            // Crosshair tick marks
            canvas.drawLine(screenX - r - 7f, screenY, screenX - r + 2f, screenY, paintCrosshair);
            canvas.drawLine(screenX + r - 2f, screenY, screenX + r + 7f, screenY, paintCrosshair);
            canvas.drawLine(screenX, screenY - r - 7f, screenX, screenY - r + 2f, paintCrosshair);
            canvas.drawLine(screenX, screenY + r - 2f, screenX, screenY + r + 7f, paintCrosshair);

            // Center beacon point
            canvas.drawCircle(screenX, screenY, 4.5f, paintCenterDot);
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
