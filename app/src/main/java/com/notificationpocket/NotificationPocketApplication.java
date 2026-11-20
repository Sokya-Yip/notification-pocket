package com.notificationpocket;

import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Restores the system-owned listener connection whenever our process starts. */
public final class NotificationPocketApplication extends Application {
    private static final String TAG = "NotificationPocket";
    private static final String CRASH_LOG = "crash.log";

    @Override
    public void onCreate() {
        super.onCreate();
        installCrashLogger();
        NotificationController.getInstance().recoverConnection(this);
    }

    /**
     * Persists the first uncaught stack trace inside the app so field reports do not
     * depend on adb access, then chains to the platform handler unchanged.
     */
    private void installCrashLogger() {
        Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            try {
                writeCrashLog(buildCrashReport(thread, throwable));
            } catch (RuntimeException ignored) {
                // Diagnostics must never replace or mask the original failure.
            }
            if (previous != null) {
                previous.uncaughtException(thread, throwable);
            }
        });
    }

    private String buildCrashReport(Thread thread, Throwable throwable) {
        StringWriter stack = new StringWriter();
        throwable.printStackTrace(new PrintWriter(stack));
        String stamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
        return "时间: " + stamp
                + "\n设备: " + Build.MANUFACTURER + " " + Build.MODEL
                + "\n系统: Android " + Build.VERSION.RELEASE
                + " (SDK " + Build.VERSION.SDK_INT + ", " + Build.DISPLAY + ")"
                + "\n版本: " + BuildConfig.VERSION_NAME
                + "\n线程: " + thread.getName()
                + "\n\n" + stack;
    }

    private void writeCrashLog(String report) {
        try (OutputStreamWriter writer = new OutputStreamWriter(
                new FileOutputStream(new File(getFilesDir(), CRASH_LOG), false),
                StandardCharsets.UTF_8)) {
            writer.write(report);
        } catch (IOException failure) {
            Log.w(TAG, "Unable to persist crash log", failure);
        }
    }

    /** Returns the most recent persisted crash report, or null when none exists. */
    static String readCrashLog(Context context) {
        File file = new File(context.getFilesDir(), CRASH_LOG);
        if (!file.isFile()) {
            return null;
        }
        try (InputStreamReader reader = new InputStreamReader(
                new FileInputStream(file), StandardCharsets.UTF_8)) {
            StringBuilder report = new StringBuilder();
            char[] buffer = new char[4096];
            int read;
            while ((read = reader.read(buffer)) != -1) {
                report.append(buffer, 0, read);
            }
            return report.toString();
        } catch (IOException failure) {
            Log.w(TAG, "Unable to read crash log", failure);
            return null;
        }
    }

    /** Removes the acknowledged report so it is not shown again on later launches. */
    static void clearCrashLog(Context context) {
        File file = new File(context.getFilesDir(), CRASH_LOG);
        if (file.isFile() && !file.delete()) {
            Log.w(TAG, "Unable to clear crash log");
        }
    }
}
