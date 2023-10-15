package com.hpp3.smartrentwidget;

import android.app.Activity;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.RemoteViews;
import android.widget.TextView;

import com.hpp3.smartrentwidget.R;
import com.hpp3.smartrentwidget.SmartRentLock;
import com.hpp3.smartrentwidget.WidgetClickedReceiver;

import org.json.JSONException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

public class ConfigureWidgetActivity extends Activity {
    private EditText usernameEditText;
    private EditText passwordEditText;
    private Button loginButton;
    private ListView deviceListView;
    private TextView descTextView;
    int mAppWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
    private static final String TAG = "ConfigureWidgetActivity";

    private final Executor executor = Executors.newSingleThreadExecutor();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setResult(RESULT_CANCELED);
        setContentView(R.layout.activity_login);

        usernameEditText = findViewById(R.id.usernameEditText);
        passwordEditText = findViewById(R.id.passwordEditText);
        loginButton = findViewById(R.id.loginButton);
        deviceListView = findViewById(R.id.deviceListView);
        descTextView = findViewById(R.id.descText);
        Intent intent = getIntent();
        Bundle extras = intent.getExtras();
        if (extras != null) {
            mAppWidgetId = extras.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        }
        if (mAppWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            Log.i(TAG, "onCreate: appwidgetID is invalid");
            finish();
            return;
        }

        EncryptedSharedPreferences encryptedSharedPreferences = getEncryptedSharedPreferences(this);
        usernameEditText.setText(encryptedSharedPreferences.getString("username", ""));
        passwordEditText.setText(encryptedSharedPreferences.getString("password", ""));

        loginButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                onLoginClicked(usernameEditText.getText().toString(), passwordEditText.getText().toString());
            }
        });
    }

    private void populateDeviceList(List<SmartRentLock> devices) {
        ArrayAdapter<SmartRentLock> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, devices);
        descTextView.setVisibility(View.VISIBLE);
        deviceListView.setAdapter(adapter);
        deviceListView.setVisibility(View.VISIBLE);
        Context ctx = this;

        deviceListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(ctx);
                Log.i(TAG, "onItemClick: appwidgetID " + mAppWidgetId);
                SmartRentLock selectedDevice = devices.get(position);

                Intent resultValue = new Intent();
                resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, mAppWidgetId);

                RemoteViews views = new RemoteViews(ctx.getPackageName(), R.layout.lock_widget);
                views.setTextViewText(R.id.lockButton, selectedDevice.getNameShort());

                Intent intent = new Intent(ctx, WidgetClickedReceiver.class);
                intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, mAppWidgetId);
                intent.putExtra("lock_name", selectedDevice.getNameShort());
                intent.putExtra("lock_id", selectedDevice.getDeviceId());
                PendingIntent pendingIntent = PendingIntent.getBroadcast(ctx, mAppWidgetId, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

                views.setOnClickPendingIntent(R.id.lockButton, pendingIntent);

                appWidgetManager.updateAppWidget(mAppWidgetId, views);
                setResult(RESULT_OK, resultValue);
                finish();
            }
        });
    }

    public void onLoginClicked(String username, String password) {
        // Store credentials securely
        storeCredentials(username, password);
        executor.execute(() -> {
            try {
                SmartRentClient client = new SmartRentClient(username, password);
                ArrayList<SmartRentLock> locks = client.getDevicesData().stream().filter(device -> {
                    try {
                        return device.get("type").equals("entry_control");
                    } catch (JSONException e) {
                        throw new RuntimeException(e);
                    }
                }).map(device -> {
                    try {
                        return new SmartRentLock(device.getInt("id"), device.get("name").toString());
                    } catch (JSONException e) {
                        throw new RuntimeException(e);
                    }
                }).collect(Collectors.toCollection(ArrayList::new));
                Handler mainHandler = new Handler(Looper.getMainLooper());
                mainHandler.post(() -> {
                    populateDeviceList(locks);
                });
            } catch (SmartRentClient.InvalidAuthException | IOException | JSONException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public static EncryptedSharedPreferences getEncryptedSharedPreferences(Context ctx) {
        try {
            MasterKey masterKey = new MasterKey.Builder(ctx)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();

            return (EncryptedSharedPreferences) EncryptedSharedPreferences.create(
                    ctx,
                    "encrypted_prefs",
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    };

    private void storeCredentials(String username, String password) {
        try {
            EncryptedSharedPreferences encryptedSharedPreferences = getEncryptedSharedPreferences(this);

            encryptedSharedPreferences.edit()
                    .putString("username", username)
                    .putString("password", password)
                    .apply();
        } catch (Exception e) {
            // Handle exceptions here (e.g., show error message or log the error)
            e.printStackTrace();
        }
    }

}
