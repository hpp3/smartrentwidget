package com.hpp3.smartrentwidget;

import android.appwidget.AppWidgetManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.TypedValue;
import android.widget.RemoteViews;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import androidx.security.crypto.EncryptedSharedPreferences;

public class WidgetClickedReceiver extends BroadcastReceiver {
    private final Executor executor = Executors.newSingleThreadExecutor();
    private static final String TAG = "WidgetClickedReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        int appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        String lockName = intent.getStringExtra("lock_name");
        if (lockName == null) {
            return;
        }
        int lockId = intent.getIntExtra("lock_id", 0);
        makeApiCall(context, appWidgetId, lockName, lockId);
    }

    private void makeApiCall(Context context, int appWidgetId, String lockName, int lockId) {
        executor.execute(() -> {
            try {
                EncryptedSharedPreferences encryptedSharedPreferences = ConfigureWidgetActivity.getEncryptedSharedPreferences(context);
                String username = encryptedSharedPreferences.getString("username", "");
                String password = encryptedSharedPreferences.getString("password", "");
                SmartRentClient client = new SmartRentClient(username, password);
                showBusyIndicator(context, appWidgetId, lockName);
                client.sendCommandAsync(lockId, "locked", "false", () -> {
                    flashText(context, appWidgetId, lockName, "✔️");
                }, () -> {
                    flashText(context, appWidgetId, lockName, "❌");
                });
            } catch (Exception e) {
                Log.i("LockWidgetProvider", "makeApiCall: " + e.getMessage());
                flashText(context, appWidgetId, lockName, "❌");
            }
        });
    }

    private void showBusyIndicator(Context context, int appWidgetId, String lockName) {
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
        } catch (InterruptedException e) {}
        resetName(context, appWidgetId, lockName);
    }
}