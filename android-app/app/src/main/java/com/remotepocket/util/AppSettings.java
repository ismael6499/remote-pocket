package com.remotepocket.util;

import android.content.Context;
import android.content.SharedPreferences;

public final class AppSettings {
    public static final String PREFS_NAME = "remote_pocket_prefs";

    public static final String MONITOR_MODE_SINGLE = "single";
    public static final String MONITOR_MODE_SPAN = "span";
    public static final String MONITOR_MODE_LAST = "last";

    public static final String SINGLE_MONITOR_PRIMARY = "primary";
    public static final String SINGLE_MONITOR_FIRST = "first";
    public static final String SINGLE_MONITOR_LAST = "last";

    public static final String VIEW_MODE_SCREEN = "screen";
    public static final String VIEW_MODE_TRACKPAD = "trackpad";

    public static final String KEY_DEFAULT_MONITOR_MODE = "pref_default_monitor_mode";
    public static final String KEY_DEFAULT_SINGLE_MONITOR = "pref_default_single_monitor";
    public static final String KEY_LAST_MONITOR_INDEX = "pref_last_monitor_index";
    public static final String KEY_LAST_SEPARATE_MONITOR_INDEX = "pref_last_separate_monitor_index";
    public static final String KEY_SPAN_INITIAL_ZOOM = "pref_span_initial_zoom";
    public static final String KEY_SINGLE_INITIAL_ZOOM = "pref_single_initial_zoom";
    public static final String KEY_FOLLOW_CURSOR_DEFAULT = "pref_follow_cursor_default";
    public static final String KEY_STARTUP_VIEW_MODE = "pref_startup_view_mode";
    public static final String KEY_START_FULLSCREEN = "pref_start_fullscreen";
    public static final String KEY_SCREEN_FPS = "screen_fps";
    public static final String KEY_MOUSE_SENSITIVITY = "pref_mouse_sensitivity";
    public static final String KEY_SCROLL_SENSITIVITY = "pref_scroll_sensitivity";
    public static final String KEY_INVERT_SCROLL = "pref_invert_scroll";
    public static final String KEY_HAPTIC_ENABLED = "pref_haptic_enabled";
    public static final String KEY_KEEP_SCREEN_ON = "pref_keep_screen_on";

    private AppSettings() {}

    public static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static String getDefaultMonitorMode(Context context) {
        return prefs(context).getString(KEY_DEFAULT_MONITOR_MODE, MONITOR_MODE_SINGLE);
    }

    public static void setDefaultMonitorMode(Context context, String mode) {
        prefs(context).edit().putString(KEY_DEFAULT_MONITOR_MODE, mode).apply();
    }

    public static String getDefaultSingleMonitor(Context context) {
        return prefs(context).getString(KEY_DEFAULT_SINGLE_MONITOR, SINGLE_MONITOR_PRIMARY);
    }

    public static void setDefaultSingleMonitor(Context context, String target) {
        prefs(context).edit().putString(KEY_DEFAULT_SINGLE_MONITOR, target).apply();
    }

    public static int getLastMonitorIndex(Context context) {
        return prefs(context).getInt(KEY_LAST_MONITOR_INDEX, 0);
    }

    public static void setLastMonitorIndex(Context context, int index) {
        prefs(context).edit().putInt(KEY_LAST_MONITOR_INDEX, index).apply();
    }

    public static int getLastSeparateMonitorIndex(Context context) {
        return prefs(context).getInt(KEY_LAST_SEPARATE_MONITOR_INDEX, 0);
    }

    public static void setLastSeparateMonitorIndex(Context context, int index) {
        prefs(context).edit().putInt(KEY_LAST_SEPARATE_MONITOR_INDEX, Math.max(0, index)).apply();
    }

    public static float getSpanInitialZoom(Context context) {
        return prefs(context).getFloat(KEY_SPAN_INITIAL_ZOOM, 1.5f);
    }

    public static void setSpanInitialZoom(Context context, float zoom) {
        prefs(context).edit().putFloat(KEY_SPAN_INITIAL_ZOOM, zoom).apply();
    }

    public static float getSingleInitialZoom(Context context) {
        return prefs(context).getFloat(KEY_SINGLE_INITIAL_ZOOM, 2.0f);
    }

    public static void setSingleInitialZoom(Context context, float zoom) {
        prefs(context).edit().putFloat(KEY_SINGLE_INITIAL_ZOOM, zoom).apply();
    }

    public static boolean isFollowCursorDefault(Context context) {
        return prefs(context).getBoolean(KEY_FOLLOW_CURSOR_DEFAULT, true);
    }

    public static void setFollowCursorDefault(Context context, boolean follow) {
        prefs(context).edit().putBoolean(KEY_FOLLOW_CURSOR_DEFAULT, follow).apply();
    }

    public static String getStartupViewMode(Context context) {
        return prefs(context).getString(KEY_STARTUP_VIEW_MODE, VIEW_MODE_SCREEN);
    }

    public static void setStartupViewMode(Context context, String mode) {
        prefs(context).edit().putString(KEY_STARTUP_VIEW_MODE, mode).apply();
    }

    public static boolean isStartFullscreen(Context context) {
        return prefs(context).getBoolean(KEY_START_FULLSCREEN, false);
    }

    public static void setStartFullscreen(Context context, boolean startFullscreen) {
        prefs(context).edit().putBoolean(KEY_START_FULLSCREEN, startFullscreen).apply();
    }

    public static int getScreenFps(Context context) {
        return prefs(context).getInt(KEY_SCREEN_FPS, 30);
    }

    public static void setScreenFps(Context context, int fps) {
        prefs(context).edit().putInt(KEY_SCREEN_FPS, fps).apply();
    }

    public static float getMouseSensitivity(Context context) {
        return prefs(context).getFloat(KEY_MOUSE_SENSITIVITY, 1.0f);
    }

    public static void setMouseSensitivity(Context context, float mult) {
        prefs(context).edit().putFloat(KEY_MOUSE_SENSITIVITY, mult).apply();
    }

    public static float getScrollSensitivity(Context context) {
        return prefs(context).getFloat(KEY_SCROLL_SENSITIVITY, 1.0f);
    }

    public static void setScrollSensitivity(Context context, float mult) {
        prefs(context).edit().putFloat(KEY_SCROLL_SENSITIVITY, mult).apply();
    }

    public static boolean isInvertScroll(Context context) {
        return prefs(context).getBoolean(KEY_INVERT_SCROLL, false);
    }

    public static void setInvertScroll(Context context, boolean invert) {
        prefs(context).edit().putBoolean(KEY_INVERT_SCROLL, invert).apply();
    }

    public static boolean isHapticEnabled(Context context) {
        return prefs(context).getBoolean(KEY_HAPTIC_ENABLED, true);
    }

    public static void setHapticEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_HAPTIC_ENABLED, enabled).apply();
    }

    public static boolean isKeepScreenOn(Context context) {
        return prefs(context).getBoolean(KEY_KEEP_SCREEN_ON, false);
    }

    public static void setKeepScreenOn(Context context, boolean keepOn) {
        prefs(context).edit().putBoolean(KEY_KEEP_SCREEN_ON, keepOn).apply();
    }

    public static void resetDefaults(Context context) {
        prefs(context).edit()
                .putString(KEY_DEFAULT_MONITOR_MODE, MONITOR_MODE_SINGLE)
                .putString(KEY_DEFAULT_SINGLE_MONITOR, SINGLE_MONITOR_PRIMARY)
                .putFloat(KEY_SPAN_INITIAL_ZOOM, 1.5f)
                .putFloat(KEY_SINGLE_INITIAL_ZOOM, 2.0f)
                .putBoolean(KEY_FOLLOW_CURSOR_DEFAULT, true)
                .putString(KEY_STARTUP_VIEW_MODE, VIEW_MODE_SCREEN)
                .putBoolean(KEY_START_FULLSCREEN, false)
                .putInt(KEY_SCREEN_FPS, 30)
                .putFloat(KEY_MOUSE_SENSITIVITY, 1.0f)
                .putFloat(KEY_SCROLL_SENSITIVITY, 1.0f)
                .putBoolean(KEY_INVERT_SCROLL, false)
                .putBoolean(KEY_HAPTIC_ENABLED, true)
                .putBoolean(KEY_KEEP_SCREEN_ON, false)
                .apply();
    }
}
