package com.hpp3.smartrentwidget;

import android.content.Context;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

public class CredentialManager {
    private static volatile CredentialManager instance;
    private final EncryptedSharedPreferences encryptedSharedPreferences;

    private CredentialManager(Context context) {
        try {
            MasterKey masterKey = new MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();

            encryptedSharedPreferences = (EncryptedSharedPreferences) EncryptedSharedPreferences.create(
                    context,
                    "encrypted_prefs",
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public static CredentialManager getInstance(Context context) {
        if (instance == null) {
            synchronized (CredentialManager.class) {
                if (instance == null) {
                    instance = new CredentialManager(context);
                }
            }
        }
        return instance;
    }

    public void storeCredentials(String username, String password) {
        encryptedSharedPreferences.edit()
                .putString("username", username)
                .putString("password", password).apply();
    }

    public EncryptedSharedPreferences getEncryptedSharedPreferences() {
        return encryptedSharedPreferences;
    }
}
