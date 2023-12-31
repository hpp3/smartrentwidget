/**
 * MIT License
 * Copyright (c) 2021 Zachery Thomas
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * Based on https://github.com/ZacheryThomas/smartrent.py
 */

package com.hpp3.smartrentwidget;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.security.crypto.EncryptedSharedPreferences;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

public class SmartRentClient {

    private static final String SMARTRENT_BASE_URI = "https://control.smartrent.com/api/v2/";
    private static final String SMARTRENT_SESSIONS_URI = SMARTRENT_BASE_URI + "sessions";
    private static final String SMARTRENT_HUBS_URI = SMARTRENT_BASE_URI + "hubs";
    private static final String SMARTRENT_HUBS_ID_URI = SMARTRENT_BASE_URI + "hubs/{}/devices";
    private static final String COMMAND_PAYLOAD =
            "[\"null\", \"null\", \"devices:%d\", \"update_attributes\", {\"device_id\": %d, \"attributes\": [{\"name\": \"%s\", \"value\": \"%s\"}]}]";
    private static final String SMARTRENT_WEBSOCKET_URI = "wss://control.smartrent.com/socket/websocket?token=%s&vsn=2.0.0";
    private static final String JOINER_PAYLOAD = "[\"null\", \"null\", \"devices:%d\", \"phx_join\", {}]";


    private final OkHttpClient httpClient = new OkHttpClient();

    private final String email;
    private final String password;
    private String token;

    private static volatile SmartRentClient instance;

    private SmartRentClient(Context context) {
        EncryptedSharedPreferences preferences = CredentialManager.getInstance(context).getEncryptedSharedPreferences();
        this.email = preferences.getString("username", "");
        this.password = preferences.getString("password", "");
        fetchToken();
    }

    public static SmartRentClient getInstance(Context context) {
        if (instance == null) {
            synchronized (SmartRentClient.class) {
                if (instance == null) {
                    instance = new SmartRentClient(context);
                }
            }
        }
        return instance;
    }

    private void fetchToken() {
        Log.i("SmartRentClient", "fetchToken: fetchingToken");
        RequestBody body = new FormBody.Builder()
                .add("email", email)
                .add("password", password)
                .build();

        Request request = new Request.Builder()
                .url(SMARTRENT_SESSIONS_URI)
                .post(body)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (response.isSuccessful()) {
                JSONObject jsonResponse = new JSONObject(response.body().string());
                this.token = jsonResponse.getString("access_token");
            } else {
                throw new RuntimeException("Failed to fetch the token");
            }
        } catch (IOException | JSONException e) {
            throw new RuntimeException(e);
        }
    }

    public List<JSONObject> getDevicesData() throws IOException, InvalidAuthException, JSONException {
        if (this.token == null) {
            fetchToken();
        }

        // Fetch the hubs
        Request hubsRequest = new Request.Builder()
                .url(SMARTRENT_HUBS_URI)
                .addHeader("authorization", "Bearer " + this.token)
                .build();

        JSONArray hubsArray;
        try (Response hubsResponse = httpClient.newCall(hubsRequest).execute()) {
            if (!hubsResponse.isSuccessful()) {
                // Handle potential errors, including checking if it's an authentication issue
                // For simplicity, just throwing a generic exception
                throw new InvalidAuthException("Failed to fetch hubs data");
            }
            hubsArray = new JSONArray(hubsResponse.body().string());
        }
        ArrayList<JSONObject> devicesList = new ArrayList<>();

        // Iterate through each hub and fetch devices for it
        for (int i = 0; i < hubsArray.length(); i++) {
            JSONObject hub = hubsArray.getJSONObject(i);
            String hubId = hub.getString("id");

            String devicesUrl = SMARTRENT_HUBS_ID_URI.replace("{}", hubId);
            Request devicesRequest = new Request.Builder()
                    .url(devicesUrl)
                    .addHeader("authorization", "Bearer " + this.token)
                    .build();

            JSONArray hubDevices;
            try (Response devicesResponse = httpClient.newCall(devicesRequest).execute()) {

                if (!devicesResponse.isSuccessful()) {
                    // Handle potential errors, including checking if it's an authentication issue
                    throw new InvalidAuthException("Failed to fetch devices for hub " + hubId);
                }

                hubDevices = new JSONArray(devicesResponse.body().string());
            }
            for (int j = 0; j < hubDevices.length(); j++) {
                devicesList.add(hubDevices.getJSONObject(j));
            }
        }

        return devicesList;
    }

    public static class InvalidAuthException extends Exception {
        public InvalidAuthException(String message) {
            super(message);
        }
    }

    public void sendCommandAsync(int deviceId, String attributeName, String value, Runnable success, Runnable failure) {
        String payload = String.format(Locale.US, COMMAND_PAYLOAD, deviceId, deviceId, attributeName, value);
        String joiner = String.format(Locale.US, JOINER_PAYLOAD, deviceId);
        Runnable retry = () -> {
            fetchToken();
            sendPayload(deviceId, payload, joiner, success, failure);
        };
        sendPayload(deviceId, payload, joiner, success, retry);
    }
    private void sendPayload(int deviceId, String payload, String joiner, Runnable success, Runnable failure) {
        String uri = String.format(Locale.US, SMARTRENT_WEBSOCKET_URI, token);
        Request request = new Request.Builder().url(uri).build();
        httpClient.newWebSocket(request, new WebSocketListener() {
            int counter = 0;
            boolean failed = false;
            private void sendPayload(@NonNull WebSocket webSocket) {
                Log.i("onOpen", joiner);
                Log.i("onOpen", payload);
                this.counter = 0;
                this.failed = false;
                webSocket.send(joiner);
                webSocket.send(payload);
            }
            @Override
            public void onOpen(@NonNull WebSocket webSocket, @NonNull Response response) {
                super.onOpen(webSocket, response);
                sendPayload(webSocket);
            }

            private boolean isPhxReply(String message) {
                try {
                    JSONArray jsonArray = new JSONArray(message);
                    if (jsonArray.length() < 5) {
                        return false;
                    }

                    String thirdElement = jsonArray.getString(2);
                    String fourthElement = jsonArray.getString(3);

                    if (!thirdElement.startsWith("devices:") || !"phx_reply".equals(fourthElement)) {
                        return false;
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                    return false;
                }
                return true;
            }

            private String getStatus(String message) {
                try {
                    JSONArray jsonArray = new JSONArray(message);
                    if (jsonArray.length() < 5) {
                        throw new RuntimeException("Not PHX reply");
                    }
                    JSONObject jsonObject = jsonArray.getJSONObject(4);
                    return jsonObject.optString("status");

                } catch (JSONException e) {
                    e.printStackTrace();
                    throw new RuntimeException(e);
                }
            }

            @Override
            public void onMessage(@NonNull WebSocket webSocket, @NonNull String text) {
                super.onMessage(webSocket, text);
                Log.i("onMessage", "onMessage: " + text);
                if (isPhxReply(text)) {
                    String status = getStatus(text);
                    if ("ok".equals(status)) {
                        this.counter += 1;
                        if (this.counter == 2) {
                            success.run();
                            webSocket.close(1000, "Success");
                        }
                    } else {
                        if (!failed) {
                            failure.run();
                            webSocket.close(1000, text);
                            failed = true;
                        }
                    }
                }
            }

            @Override
            public void onFailure(@NonNull WebSocket webSocket, @NonNull Throwable t, Response response) {
                super.onFailure(webSocket, t, response);
                Log.i("onFailure", "failure: " + t + response);
                failure.run();
                webSocket.close(1000, "Failed to connect");
            }
        });
    }
}