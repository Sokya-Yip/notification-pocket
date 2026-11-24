package com.notificationpocket;

import android.content.Context;
import android.os.Build;
import android.os.Process;
import android.os.SystemClock;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Bounded, notification-content-free trace of the latest authorization handoff. */
final class AuthorizationDiagnostics {
    private static final String TAG = "NotificationPocket";
    private static final String FILE_NAME = "authorization-diagnostics.log";
    private static final String LIMIT_EVENT = "trace_limit_reached";
    private static final String LIMIT_MARKER = "\n" + LIMIT_EVENT
            + ": 日志已到 192KB 上限；再次点“去授权”会重新开始记录。\n";
    private static final long MAX_TRACE_BYTES = 192L * 1024L;

    private AuthorizationDiagnostics() {
    }

    /** Starts a fresh trace for one button-triggered authorization attempt. */
    static void begin(Context context) {
        String header = "设备: " + Build.MANUFACTURER + " " + Build.MODEL
                + "\n系统: Android " + Build.VERSION.RELEASE
                + " (SDK " + Build.VERSION.SDK_INT + ", " + Build.DISPLAY + ")"
                + "\n版本: " + BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE
                + ", " + (BuildConfig.DEBUG ? "debug" : "release") + ")"
                + "\n说明: 仅记录最近一次授权步骤，不包含通知标题、正文或列表。\n";
        write(context, header, false);
        record(context, "permission_button_clicked");
    }

    /** Appends and synchronously flushes one event; an existing trace is never created implicitly. */
    static void record(Context context, String event) {
        if (!file(context).isFile()) {
            return;
        }
        String stamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
                .format(new Date());
        String line = stamp + " | elapsed=" + SystemClock.elapsedRealtime()
                + " | pid=" + Process.myPid() + " | " + event + "\n";
        write(context, line, true);
    }

    static boolean isActive(Context context) {
        File file = file(context);
        return file.isFile() && !hasLimitReached(file);
    }

    /**
     * True when the last authorization jump was never followed by this app coming back to the
     * foreground. A vendor can kill the caller so abruptly that onStop never runs, so an
     * activity_stop cannot be required here; the absence of an activity_resume after the jump
     * is the only signal that survives both a clean kill and a hard one. Anchoring on the
     * attempt (not the return) also covers a startActivity that threw on a vendor build.
     */
    static boolean lastHandoffNeverReturned(Context context) {
        String trace = read(context);
        if (trace == null) {
            return false;
        }
        int jump = trace.lastIndexOf("settings_start_attempt");
        return jump >= 0 && !trace.substring(jump).contains("activity_resume");
    }

    static void record(Context context, String event, Throwable failure) {
        record(context, event + " | " + failure.getClass().getName() + ": "
                + failure.getMessage() + "\n" + Log.getStackTraceString(failure));
    }

    static String read(Context context) {
        File file = file(context);
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
        } catch (IOException | RuntimeException failure) {
            Log.w(TAG, "Unable to read authorization diagnostics", failure);
            return null;
        }
    }

    static boolean clear(Context context) {
        File file = file(context);
        return !file.exists() || file.delete();
    }

    private static void write(Context context, String text, boolean append) {
        File file = file(context);
        // Trimming leaves room below the cap, so a size-only check would resume appending.
        if (append && file.isFile() && hasLimitReached(file)) {
            return;
        }
        if (append && file.isFile()
                && file.length() + text.getBytes(StandardCharsets.UTF_8).length > MAX_TRACE_BYTES) {
            String previous = read(context);
            if (previous != null && previous.contains(LIMIT_EVENT)) {
                return;
            }
            String retained = previous == null ? "" : previous;
            while (!retained.isEmpty() && retained.getBytes(StandardCharsets.UTF_8).length
                    + LIMIT_MARKER.getBytes(StandardCharsets.UTF_8).length > MAX_TRACE_BYTES) {
                retained = retained.substring(0, Math.max(0, retained.length() - 4096));
            }
            write(context, retained + LIMIT_MARKER, false);
            return;
        }
        try (FileOutputStream output = new FileOutputStream(file, append)) {
            OutputStreamWriter writer = new OutputStreamWriter(output, StandardCharsets.UTF_8);
            writer.write(text);
            writer.flush();
            output.getFD().sync();
        } catch (IOException | RuntimeException failure) {
            // Diagnostics must never interfere with the authorization path being measured.
            Log.w(TAG, "Unable to persist authorization diagnostics", failure);
        }
    }

    private static boolean hasLimitReached(File file) {
        int tailBytes = (int) Math.min(file.length(), 512L);
        if (tailBytes < LIMIT_EVENT.getBytes(StandardCharsets.UTF_8).length) {
            return false;
        }
        try (RandomAccessFile input = new RandomAccessFile(file, "r")) {
            input.seek(file.length() - tailBytes);
            byte[] tail = new byte[tailBytes];
            input.readFully(tail);
            return new String(tail, StandardCharsets.UTF_8).contains(LIMIT_EVENT);
        } catch (IOException | RuntimeException failure) {
            return false;
        }
    }

    private static File file(Context context) {
        return new File(context.getApplicationContext().getFilesDir(), FILE_NAME);
    }
}
