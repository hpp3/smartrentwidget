package com.hpp3.smartrentwidget;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

import androidx.annotation.NonNull;
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

    public SmartRentClient(String email, String password) {
        this.email = email;
        this.password = password;
        this.token = fetchToken();
    }
    private String fetchToken() {
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
                return this.token;
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
        CompletableFuture.runAsync(() -> {
            String payload = String.format(Locale.US, COMMAND_PAYLOAD, deviceId, deviceId, attributeName, value);
            try {
                sendPayload(deviceId, payload, success, failure);
            } catch (Exception e) {
                fetchToken();
                sendPayload(deviceId, payload, success, failure);
            }
        });
    }
    private void sendPayload(int deviceId, String payload, Runnable success, Runnable failure) {
        String uri = String.format(Locale.US, SMARTRENT_WEBSOCKET_URI, token);
        String joiner = String.format(Locale.US, JOINER_PAYLOAD, deviceId);
        Request request = new Request.Builder().url(uri).build();
        httpClient.newWebSocket(request, new WebSocketListener() {
            int counter = 0;
            @Override
            public void onOpen(@NonNull WebSocket webSocket, @NonNull Response response) {
                super.onOpen(webSocket, response);
                Log.i("onOpen", joiner);
                Log.i("onOpen", payload);
                this.counter = 0;
                webSocket.send(joiner);
                webSocket.send(payload);
            }

            private boolean isStatusOk(String message) {
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

                    JSONObject jsonObject = jsonArray.getJSONObject(4);

                    String status = jsonObject.optString("status");
                    return "ok".equals(status);

                } catch (JSONException e) {
                    e.printStackTrace();
                    return false;
                }
            }

            @Override
            public void onMessage(@NonNull WebSocket webSocket, @NonNull String text) {
                super.onMessage(webSocket, text);
                Log.i("onMessage", "onMessage: " + text);
                if (isStatusOk(text)) {
                    this.counter += 1;
                    if (this.counter == 2) {
                        success.run();
                    }
                }
            }

            @Override
            public void onFailure(@NonNull WebSocket webSocket, @NonNull Throwable t, Response response) {
                super.onFailure(webSocket, t, response);
                Log.i("onMessage", "failure: " + t + response);
                failure.run();
            }
        });
    }
}