package com.hpp3.smartrentwidget;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class FileLogger {

    private static final String FILENAME = "app_logs.txt";

    public static void log(Context context, String TAG, String message) {
        Log.i(TAG, message);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        String currentDateAndTime = sdf.format(new Date());
        String logMessage = currentDateAndTime + " : " + message + "\n";
        Log.i(TAG, logMessage);

        try {
            File logFile = new File(context.getExternalFilesDir(null), FILENAME);
            if (!logFile.exists()) {
                logFile.createNewFile();
            }

            FileOutputStream fos = new FileOutputStream(logFile, true); // 'true' for append mode
            fos.write(logMessage.getBytes());
            fos.close();
        } catch (IOException e) {
            e.printStackTrace(); // You might want to handle this exception more gracefully
        }
    }
}