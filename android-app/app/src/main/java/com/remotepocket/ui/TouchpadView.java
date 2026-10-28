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
    private float downX = 0f;
    private float downY = 0f;
    private float lastX, lastY;
    private float lastTwoFingerY;
    private float scrollAccumulator = 0f;
    private float accumX = 0f;
    private float accumY = 0f;

    private long touchDownTime;
    private boolean isMultiTouch = false;
    private boolean hasMoved = false;
    private boolean isScaling = false;
    private boolean isTouching = false;

    // Tap and Drag tracking (double tap and hold to drag)
    private long lastTapTime = 0;
    private float lastTapX = 0f, lastTapY = 0f;
    private static final int DOUBLE_TAP_TIMEOUT_MS = 280;
    private boolean isTapAndDrag = false;
    private final Runnable pendingTapRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isTapAndDrag && webSocketManager != null && webSocketManager.isConnected()) {
                triggerHaptic(20);
                webSocketManager.sendMouseClick("left");
            }
            lastTapTime = 0;
        }
    };

    private static final int TAP_TIMEOUT_MS = 240;
    private static final float MOVE_SLOP = 12f;
    private static final float SENSITIVITY = 1.35f;

    private ScaleGestureDetector scaleGestureDetector;

    // Clean Windows cursor arrow graphics (no halo / no golden decorations)
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
        paintCursorStroke.setStrokeWidth(1.6f);
        paintCursorStroke.setStrokeJoin(Paint.Join.ROUND);

        cursorPath = new android.graphics.Path();
        float s = getResources().getDisplayMetrics().density * 0.75f;
        cursorPath.moveTo(0 * s, 0 * s);
        cursorPath.lineTo(0 * s, 17 * s);
        cursorPath.lineTo(4.5f * s, 13 * s);
        cursorPath.lineTo(8 * s, 21 * s);
        cursorPath.lineTo(11 * s, 19.5f * s);
        cursorPath.lineTo(7.5f * s, 12 * s);
        cursorPath.lineTo(13 * s, 12 * s);
        cursorPath.close();

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
                currentScale = Math.max(1.0f, Math.min(currentScale, getMaxScale()));

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

    public float getMaxScale() {
        int viewW = getWidth();
        int viewH = getHeight();
        if (viewW > 0 && viewH > 0 && scrW > 0 && scrH > 0) {
            float scaleFit = Math.min((float) viewW / scrW, (float) viewH / scrH);
            if (scaleFit > 0) {
                float singleFit = Math.min((float) viewW / 1920f, (float) viewH / 1080f);
                float spanFactor = Math.max(1.0f, singleFit / scaleFit);
                return Math.max(16.0f, spanFactor * 8.0f);
            }
        }
        return 20.0f;
    }

    public void cycleZoomLevel() {
        float max = getMaxScale();
        float singleScreenRatio = 1.0f;
        int viewW = getWidth();
        int viewH = getHeight();
        if (viewW > 0 && viewH > 0 && scrW > 0 && scrH > 0) {
            float singleFit = Math.min((float) viewW / 1920f, (float) viewH / 1080f);
            float currentFit = Math.min((float) viewW / scrW, (float) viewH / scrH);
            if (currentFit > 0) {
                singleScreenRatio = Math.max(1.0f, singleFit / currentFit);
            }
        }

        if (singleScreenRatio > 1.3f) {
            float step1 = 1.0f;
            float step2 = singleScreenRatio;
            float step3 = singleScreenRatio * 2.0f;
            float step4 = Math.min(max, singleScreenRatio * 4.0f);

            if (currentScale < step2 * 0.85f) {
                currentScale = step2;
            } else if (currentScale < step3 * 0.85f) {
                currentScale = step3;
            } else if (currentScale < step4 * 0.85f) {
                currentScale = step4;
            } else {
                currentScale = step1;
            }
        } else {
            if (currentScale < 1.8f) {
                currentScale = 2.5f;
            } else if (currentScale < 3.8f) {
                currentScale = 5.0f;
            } else {
                currentScale = 1.0f;
            }
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

    public void onEnterFullscreenLandscape() {
        // In Fullscreen Landscape, set scale to 1.0x to fit full desktop edge-to-edge
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

    public void onExitFullscreen() {
        if (isScreenMode) {
            currentScale = 2.0f;
            post(() -> {
                applyCenterTranslationDirect();
                invalidate();
            });
        } else {
            resetZoom();
        }
        notifyZoomChanged();
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w > 0 && h > 0) {
            if (currentScale <= 1.05f) {
                currentTransX = 0f;
                currentTransY = 0f;
                if (zoomTargetView != null) {
                    zoomTargetView.setTranslationX(0f);
                    zoomTargetView.setTranslationY(0f);
                    zoomTargetView.setScaleX(1.0f);
                    zoomTargetView.setScaleY(1.0f);
                }
            } else if (followCursor) {
                applyCenterTranslationDirect();
            }
            invalidate();
        }
    }

    private int serverX = -1, serverY = -1;
    private long lastLocalMoveTime = 0;
    private static final long SERVER_SYNC_GRACE_MS = 900;

    /**
     * Forgets the local cursor so the next server frame (or touch) re-syncs with the PC's real position.
     */
    public void resyncCursorFromServer() {
        curX = scrW > 0 ? scrW / 2 : -1;
        curY = scrH > 0 ? scrH / 2 : -1;
        pendingSrvX = -1;
        pendingSrvY = -1;
        pendingSrvCount = 0;
        lastLocalMoveTime = 0;
        if (followCursor && currentScale > 1.0f && curX > 0 && curY > 0) {
            applyCenterTranslationDirect();
        }
        invalidate();
    }

    /**
     * Receives authoritative desktop dimensions and cursor position from PC.
     * Server frames are delayed, so they are ignored while touching and for a short grace
     * period after the last local move to avoid cursor jitter ("earthquake" effect).
     */
    private int pendingSrvX = -1, pendingSrvY = -1;
    private int pendingSrvCount = 0;
    private static final int SERVER_STABLE_FRAMES = 2;

    public void updateServerCursorPosition(int x, int y, int w, int h) {
        int oldW = this.scrW;
        int oldH = this.scrH;
        this.scrW = w > 0 ? w : this.scrW;
        this.scrH = h > 0 ? h : this.scrH;

        if (w > 0 && h > 0 && oldW > 0 && oldH > 0 && (oldW != this.scrW || oldH != this.scrH)) {
            int viewW = getWidth();
            int viewH = getHeight();
            if (viewW > 0 && viewH > 0) {
                float oldScaleFit = Math.min((float) viewW / oldW, (float) viewH / oldH);
                float newScaleFit = Math.min((float) viewW / this.scrW, (float) viewH / this.scrH);
                if (oldScaleFit > 0 && newScaleFit > 0) {
                    float ratio = oldScaleFit / newScaleFit;
                    currentScale = Math.max(1.0f, Math.min(currentScale * ratio, getMaxScale()));
                    notifyZoomChanged();
                }
            }
        }

        // Delayed frames carry a "trail" of old positions while the cursor moves.
        // Only trust a position once it has been identical for several consecutive frames.
        if (x == pendingSrvX && y == pendingSrvY) {
            pendingSrvCount++;
        } else {
            pendingSrvX = x;
            pendingSrvY = y;
            pendingSrvCount = 1;
        }
        boolean stable = pendingSrvCount >= SERVER_STABLE_FRAMES && x >= 0 && y >= 0;
        if (stable) {
            serverX = x;
            serverY = y;
        }

        boolean inGrace = isTouching || (System.currentTimeMillis() - lastLocalMoveTime) < SERVER_SYNC_GRACE_MS;

        if (this.curX < 0 || this.curY < 0) {
            this.curX = (x > 0) ? x : scrW / 2;
            this.curY = (y > 0) ? y : scrH / 2;
            if (followCursor && currentScale > 1.0f) {
                applyCenterTranslationDirect();
            }
        } else if (!inGrace && stable && (Math.abs(curX - x) > 2 || Math.abs(curY - y) > 2)) {
            this.curX = x;
            this.curY = y;
            if (followCursor && currentScale > 1.05f) {
                applyCenterTranslationDirect();
            }
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

    /**
     * Converts a touch point on TouchpadView to the corresponding desktop pixel coordinate.
     */
    public int[] viewToDesktopCoords(float touchX, float touchY) {
        int viewW = getWidth();
        int viewH = getHeight();
        if (viewW <= 0 || viewH <= 0 || scrW <= 0 || scrH <= 0) {
            return null;
        }

        float scaleFit = Math.min((float) viewW / scrW, (float) viewH / scrH);
        float renderW = scrW * scaleFit;
        float renderH = scrH * scaleFit;
        float offsetX = (viewW - renderW) / 2f;
        float offsetY = (viewH - renderH) / 2f;

        float localX = (touchX - currentTransX - viewW / 2f) / currentScale + viewW / 2f;
        float localY = (touchY - currentTransY - viewH / 2f) / currentScale + viewH / 2f;

        int dx = Math.round((localX - offsetX) / scaleFit);
        int dy = Math.round((localY - offsetY) / scaleFit);

        dx = Math.max(0, Math.min(scrW - 1, dx));
        dy = Math.max(0, Math.min(scrH - 1, dy));

        return new int[]{dx, dy};
    }

    private float getPointerAcceleration(float deltaX, float deltaY) {
        float density = getResources().getDisplayMetrics().density;
        if (density <= 0) density = 1.0f;
        float distDp = (float) Math.hypot(deltaX, deltaY) / density;
        if (distDp <= 3.0f) {
            return 1.0f; // Micro-movements: 1:1 pixel precision
        } else if (distDp <= 15.0f) {
            return 1.0f + (distDp - 3.0f) * 0.065f; // Progressive smooth acceleration
        } else {
            float accel = 1.78f + (distDp - 15.0f) * 0.075f; // Swift flicks across screen
            return Math.min(accel, 3.5f); // Safe clamp
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
                downX = event.getX();
                downY = event.getY();
                lastX = event.getX();
                lastY = event.getY();
                accumX = 0f;
                accumY = 0f;
                touchDownTime = System.currentTimeMillis();
                hasMoved = false;
                isMultiTouch = false;
                isScaling = false;
                isTouching = true;

                // Initial cursor position: if not initialized, snap to the real PC position
                if (serverX >= 0 && serverY >= 0 && (curX < 0 || curY < 0)) {
                    curX = serverX;
                    curY = serverY;
                    if (followCursor && currentScale > 1.05f) {
                        applyCenterTranslationDirect();
                    }
                    invalidate();
                }

                // Safety: ensure any lingering drag state on PC is released
                if (isTapAndDrag) {
                    webSocketManager.sendMouseUp("left");
                    isTapAndDrag = false;
                }

                // Check if this touch is the second tap of a double-tap-to-drag gesture
                long timeSinceLastTap = touchDownTime - lastTapTime;
                float distFromLastTap = (float) Math.hypot(downX - lastTapX, downY - lastTapY);
                if (lastTapTime > 0 && timeSinceLastTap < DOUBLE_TAP_TIMEOUT_MS && distFromLastTap < 60f) {
                    removeCallbacks(pendingTapRunnable);
                    isTapAndDrag = true;
                    lastTapTime = 0;
                    triggerHaptic(35);
                    webSocketManager.sendMouseDown("left");
                } else {
                    isTapAndDrag = false;
                }
                break;

            case MotionEvent.ACTION_POINTER_DOWN:
                removeCallbacks(pendingTapRunnable);
                if (isTapAndDrag) {
                    webSocketManager.sendMouseUp("left");
                    isTapAndDrag = false;
                }
                lastTapTime = 0;
                if (pointerCount == 2) {
                    isMultiTouch = true;
                    lastTwoFingerY = (event.getY(0) + event.getY(1)) / 2f;
                    scrollAccumulator = 0f;
                }
                break;

            case MotionEvent.ACTION_MOVE:
                if (pointerCount == 1 && !isMultiTouch && !isScaling) {
                    float currentX = event.getX();
                    float currentY = event.getY();
                    float deltaRawX = currentX - lastX;
                    float deltaRawY = currentY - lastY;

                    float totalDistFromDown = (float) Math.hypot(currentX - downX, currentY - downY);
                    if (totalDistFromDown > MOVE_SLOP) {
                        hasMoved = true;
                        lastTapTime = 0;
                        removeCallbacks(pendingTapRunnable);
                    }

                    float accel = getPointerAcceleration(deltaRawX, deltaRawY);
                    if (isTapAndDrag) {
                        accel = Math.min(accel, 1.8f);
                    }
                    float effectiveDeltaX = deltaRawX * accel;
                    float effectiveDeltaY = deltaRawY * accel;

                    if (isScreenMode) {
                        if (currentScale > 1.05f) {
                            if (followCursor || isTapAndDrag) {
                                // Locked center cursor mode: sliding finger moves cursor relative to desktop
                                int viewW = getWidth();
                                int viewH = getHeight();
                                if (viewW > 0 && viewH > 0 && scrW > 0 && scrH > 0) {
                                    float scaleFit = Math.min((float) viewW / scrW, (float) viewH / scrH);
                                    float effectiveScale = scaleFit * currentScale;
                                    if (effectiveScale > 0) {
                                        float moveDesktopX = effectiveDeltaX / effectiveScale;
                                        float moveDesktopY = effectiveDeltaY / effectiveScale;
                                        accumX += moveDesktopX;
                                        accumY += moveDesktopY;
                                        int stepX = Math.round(accumX);
                                        int stepY = Math.round(accumY);
                                        if (stepX != 0 || stepY != 0) {
                                            accumX -= stepX;
                                            accumY -= stepY;
                                            curX = Math.max(0, Math.min(scrW - 1, (curX >= 0 ? curX : scrW / 2) + stepX));
                                            curY = Math.max(0, Math.min(scrH - 1, (curY >= 0 ? curY : scrH / 2) + stepY));
                                            webSocketManager.sendMouseAbsolute(curX, curY);
                                            lastLocalMoveTime = System.currentTimeMillis();
                                            applyCenterTranslationDirect();
                                            invalidate();
                                        }
                                    }
                                }
                            } else {
                                // Free viewport panning (when followCursor is disabled and not dragging)
                                currentTransX += deltaRawX;
                                currentTransY += deltaRawY;
                                if (zoomTargetView != null) {
                                    zoomTargetView.setTranslationX(currentTransX);
                                    zoomTargetView.setTranslationY(currentTransY);
                                }
                                invalidate();
                            }
                        } else {
                            // Full desktop fit (scale 1.0x, e.g. Fullscreen Landscape):
                            int viewW = getWidth();
                            int viewH = getHeight();
                            if (viewW > 0 && viewH > 0 && scrW > 0 && scrH > 0) {
                                float scaleFit = Math.min((float) viewW / scrW, (float) viewH / scrH);
                                if (scaleFit > 0) {
                                    float moveDesktopX = effectiveDeltaX / scaleFit;
                                    float moveDesktopY = effectiveDeltaY / scaleFit;
                                    accumX += moveDesktopX;
                                    accumY += moveDesktopY;
                                    int stepX = Math.round(accumX);
                                    int stepY = Math.round(accumY);
                                    if (stepX != 0 || stepY != 0) {
                                        accumX -= stepX;
                                        accumY -= stepY;
                                        curX = Math.max(0, Math.min(scrW - 1, (curX >= 0 ? curX : scrW / 2) + stepX));
                                        curY = Math.max(0, Math.min(scrH - 1, (curY >= 0 ? curY : scrH / 2) + stepY));
                                        webSocketManager.sendMouseAbsolute(curX, curY);
                                        lastLocalMoveTime = System.currentTimeMillis();
                                        invalidate();
                                    }
                                }
                            }
                        }
                    } else {
                        // Classic Trackpad Mode: relative trackpad movements
                        accumX += effectiveDeltaX * SENSITIVITY;
                        accumY += effectiveDeltaY * SENSITIVITY;

                        int moveX = Math.round(accumX);
                        int moveY = Math.round(accumY);

                        if (moveX != 0 || moveY != 0) {
                            webSocketManager.sendMouseMove(moveX, moveY);
                            accumX -= moveX;
                            accumY -= moveY;
                        }
                    }

                    lastX = currentX;
                    lastY = currentY;
                } else if (pointerCount == 2 && !isScaling) {
                    // Two-finger smooth, dynamic & controllable scroll
                    float currentTwoFingerY = (event.getY(0) + event.getY(1)) / 2f;
                    float deltaY = currentTwoFingerY - lastTwoFingerY;
                    lastTwoFingerY = currentTwoFingerY;

                    if (Math.abs(deltaY) > 0.5f) {
                        hasMoved = true;
                        lastTapTime = 0;

                        // Dynamic progressive velocity curve:
                        // Gentle on slow micro-scrolling (precision reading),
                        // smoothly scaling up on faster flicks.
                        float absDelta = Math.abs(deltaY);
                        float dynamicMultiplier = 2.2f + Math.min(absDelta * 0.08f, 2.5f);

                        scrollAccumulator += deltaY * dynamicMultiplier;

                        int step = Math.round(scrollAccumulator);
                        if (Math.abs(step) >= 12) {
                            webSocketManager.sendMouseScroll(step);
                            scrollAccumulator -= step;
                        }
                    }
                }
                break;

            case MotionEvent.ACTION_POINTER_UP:
                scrollAccumulator = 0f;
                break;

            case MotionEvent.ACTION_UP:
                isTouching = false;
                scrollAccumulator = 0f;
                long duration = System.currentTimeMillis() - touchDownTime;

                if (isTapAndDrag) {
                    // Released tap-and-drag
                    triggerHaptic(25);
                    webSocketManager.sendMouseUp("left");
                    isTapAndDrag = false;
                    lastTapTime = 0;
                } else if (!hasMoved && duration < TAP_TIMEOUT_MS && !isScaling) {
                    // Single or multi-finger tap directly at CURRENT mouse cursor position (no jump!)
                    if (pointerCount == 3) {
                        removeCallbacks(pendingTapRunnable);
                        triggerHaptic(35);
                        webSocketManager.sendMouseClick("middle");
                        lastTapTime = 0;
                    } else if (isMultiTouch || pointerCount == 2) {
                        removeCallbacks(pendingTapRunnable);
                        triggerHaptic(30);
                        webSocketManager.sendMouseClick("right");
                        lastTapTime = 0;
                    } else {
                        // Single tap: queue pending click with short double-tap window
                        lastTapTime = System.currentTimeMillis();
                        lastTapX = downX;
                        lastTapY = downY;
                        removeCallbacks(pendingTapRunnable);
                        postDelayed(pendingTapRunnable, 175);
                    }
                } else {
                    removeCallbacks(pendingTapRunnable);
                    lastTapTime = 0;
                }

                isMultiTouch = false;
                hasMoved = false;
                isScaling = false;
                break;

            case MotionEvent.ACTION_CANCEL:
                isTouching = false;
                removeCallbacks(pendingTapRunnable);
                if (isTapAndDrag) {
                    webSocketManager.sendMouseUp("left");
                    isTapAndDrag = false;
                }
                lastTapTime = 0;
                isMultiTouch = false;
                hasMoved = false;
                isScaling = false;
                scrollAccumulator = 0f;
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
            paintCursorFill.setColor(Color.WHITE);
            canvas.drawPath(cursorPath, paintCursorFill);
            canvas.drawPath(cursorPath, paintCursorStroke);
            canvas.restore();
        }
    }

    @Override
    public void cancelLongPress() {
        super.cancelLongPress();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        removeCallbacks(pendingTapRunnable);
        if (isTapAndDrag && webSocketManager != null) {
            webSocketManager.sendMouseUp("left");
            isTapAndDrag = false;
        }
        lastTapTime = 0;
        isTouching = false;
    }
}
