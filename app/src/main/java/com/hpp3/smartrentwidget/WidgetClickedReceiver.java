package com.hpp3.smartrentwidget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.TypedValue;
import android.widget.RemoteViews;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import androidx.security.crypto.EncryptedSharedPreferences;

public class WidgetClickedReceiver extends AppWidgetProvider {
    private final Executor executor = Executors.newSingleThreadExecutor();
    private static final String TAG = "WidgetClickedReceiver";
    private static final String WIDGET_CLICKED_ACTION = "com.hpp3.smartrentwidget.WIDGET_CLICKED";

    public void logIntentInfo(Intent intent) {
        if (intent == null) {
            Log.i("IntentLogger", "Received null intent");
            return;
        }

        Log.i("IntentLogger", "=== NEW INTENT ===");
        // Log Action
        String action = intent.getAction();
        Log.i("IntentLogger", "Action: " + (action != null ? action : "null"));

        // Log Data
        String dataString = intent.getDataString();
        Log.i("IntentLogger", "Data: " + (dataString != null ? dataString : "null"));

        // Log Categories
        if (intent.getCategories() != null) {
            for (String category : intent.getCategories()) {
                Log.i("IntentLogger", "Category: " + category);
            }
        } else {
            Log.i("IntentLogger", "No categories");
        }

        // Log Extras
        Bundle extras = intent.getExtras();
        if (extras != null) {
            for (String key : extras.keySet()) {
                Log.i("IntentLogger", "Extra key: " + key + " -> value: " + extras.get(key));
            }
        } else {
            Log.i("IntentLogger", "No extras");
        }

        // Log Flags (if any)
        int flags = intent.getFlags();
        if (flags != 0) {
            Log.i("IntentLogger", "Flags: " + Integer.toBinaryString(flags));
        } else {
            Log.i("IntentLogger", "No flags");
        }

        // Log Component (if any)
        if (intent.getComponent() != null) {
            Log.i("IntentLogger", "Component: " + intent.getComponent().toString());
        }
        Log.i("IntentLogger", "=== END OF INTENT ===");
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        logIntentInfo(intent);
        super.onReceive(context, intent);
        if (WIDGET_CLICKED_ACTION.equals(intent.getAction())) {
            int appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
            SmartRentLock lock = LockWidgetManager.loadLockConfiguration(context, appWidgetId);
            assert lock != null;
            makeApiCall(context, appWidgetId, lock.getNameShort(), lock.getDeviceId());
        }
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            Log.i(TAG, "onUpdate: updating " + appWidgetId);
            SmartRentLock lock = LockWidgetManager.loadLockConfiguration(context, appWidgetId);
            if (lock == null) {
                return;
            }
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.lock_widget);
            views.setTextViewText(R.id.lockButton, lock.getNameShort());

            Intent intent = new Intent(context, WidgetClickedReceiver.class);
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
            intent.setAction(WIDGET_CLICKED_ACTION);
            PendingIntent pendingIntent = PendingIntent.getBroadcast(context, appWidgetId, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            views.setOnClickPendingIntent(R.id.lockButton, pendingIntent);
            appWidgetManager.updateAppWidget(appWidgetId, views);
        }
    }

    @Override
    public void onDeleted(Context context, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            LockWidgetManager.deleteLockConfiguration(context, appWidgetId);
            Log.i(TAG, "onDeleted: deleting " + appWidgetId);
        }
    }

    @Override
    public void onRestored(Context context, int[] oldWidgetIds, int[] newWidgetIds) {
        // map the old widget ids to the new widget ids
        assert oldWidgetIds.length == newWidgetIds.length;
        for (int i = 0; i < oldWidgetIds.length; i++) {
            SmartRentLock lock = LockWidgetManager.loadLockConfiguration(context, oldWidgetIds[i]);
            LockWidgetManager.deleteLockConfiguration(context, oldWidgetIds[i]);
            if (lock != null) {
                LockWidgetManager.saveLockConfiguration(context, newWidgetIds[i], lock);
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
            for (int newId : newWidgetIds) {
                Bundle options = new Bundle();
                options.putBoolean(AppWidgetManager.OPTION_APPWIDGET_RESTORE_COMPLETED, true);
                appWidgetManager.updateAppWidgetOptions(newId, options);
            }
        }
    }

    private void makeApiCall(Context context, int appWidgetId, String lockName, int lockId) {
        executor.execute(() -> {
            try {
                EncryptedSharedPreferences encryptedSharedPreferences = ConfigureWidgetActivity.getEncryptedSharedPreferences(context);
                String username = encryptedSharedPreferences.getString("username", "");
                String password = encryptedSharedPreferences.getString("password", "");
                showBusyIndicator(context, appWidgetId);
                SmartRentClient client = new SmartRentClient(username, password);
                client.sendCommandAsync(lockId, "locked", "false",
                        () -> flashText(context, appWidgetId, lockName, "✔️"),
                        () -> flashText(context, appWidgetId, lockName, "❌"));
            } catch (Exception e) {
                Log.i("LockWidgetProvider", "makeApiCall: " + e.getMessage());
                flashText(context, appWidgetId, lockName, "❌");
            }
        });
    }

    private void showBusyIndicator(Context context, int appWidgetId) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.lock_widget);
        views.setTextViewText(R.id.lockButton, "⏳");
        views.setTextViewTextSize(R.id.lockButton, TypedValue.COMPLEX_UNIT_SP, 18);
        AppWidgetManager.getInstance(context).updateAppWidget(appWidgetId, views);
    }

    private void resetName(Context context, int appWidgetId, String lockName) {
        new Handler(Looper.getMainLooper()).post(() -> {
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.lock_widget);

            views.setTextViewText(R.id.lockButton, lockName);
            views.setTextViewTextSize(R.id.lockButton, TypedValue.COMPLEX_UNIT_SP, 11);
            AppWidgetManager.getInstance(context).updateAppWidget(appWidgetId, views);
        });
    }

    private void flashText(Context context, int appWidgetId, String lockName, String text) {
        new Handler(Looper.getMainLooper()).post(() -> {
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.lock_widget);
            views.setTextViewText(R.id.lockButton, text);
            views.setTextViewTextSize(R.id.lockButton, TypedValue.COMPLEX_UNIT_SP, 18);
            AppWidgetManager.getInstance(context).updateAppWidget(appWidgetId, views);
        });
        try {
            Thread.sleep(1000);
        } catch (InterruptedException ignored) {}
        resetName(context, appWidgetId, lockName);
    }
}