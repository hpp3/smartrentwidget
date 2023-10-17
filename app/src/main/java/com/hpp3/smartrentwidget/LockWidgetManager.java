package com.hpp3.smartrentwidget;

import android.content.Context;
import android.content.SharedPreferences;

public class LockWidgetManager {
    private static final String PREFERENCES_NAME = "LockWidgetPrefs";
    private static final String PREF_PREFIX = "smartrentwidget_";
    private static final String PREF_PREFIX_KEY_NAME = PREF_PREFIX + "name_";
    private static final String PREF_PREFIX_KEY_ID = PREF_PREFIX + "id_";

    private LockWidgetManager() {
    }

    public static void saveLockConfiguration(Context context, int appWidgetId, SmartRentLock lock) {
        SharedPreferences.Editor prefs = context.getSharedPreferences(PREFERENCES_NAME, 0).edit();
        prefs.putString(PREF_PREFIX_KEY_NAME + appWidgetId, lock.getName());
        prefs.putInt(PREF_PREFIX_KEY_ID + appWidgetId, lock.getDeviceId());
        prefs.apply();
    }

    // Call this method to retrieve the widget configuration from persistent storage.
    public static SmartRentLock loadLockConfiguration(Context context, int appWidgetId) {
        SharedPreferences prefs = context.getSharedPreferences(PREFERENCES_NAME, 0);
        String name = prefs.getString(PREF_PREFIX_KEY_NAME + appWidgetId, null);
        int deviceId = prefs.getInt(PREF_PREFIX_KEY_ID + appWidgetId, -1);
        if (name != null && deviceId != -1) {
            return new SmartRentLock(deviceId, name);
        } else {
            return null;
        }
    }

    // Call this method when a widget is deleted to clean up any associated configuration.
    public static void deleteLockConfiguration(Context context, int appWidgetId) {
        SharedPreferences.Editor prefs = context.getSharedPreferences(PREFERENCES_NAME, 0).edit();
        prefs.remove(PREF_PREFIX_KEY_NAME + appWidgetId);
        prefs.remove(PREF_PREFIX_KEY_ID + appWidgetId);
        prefs.apply();
    }
}
