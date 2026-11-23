package com.notificationpocket;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Builds and optionally exports a report only after an explicit user action. */
final class FeedbackReport {
    private static final String TAG = "NotificationPocket";

    private FeedbackReport() {
    }

    static String build(Context context, String issueType, String description) {
        String metadata = "生成时间: " + new SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(new Date())
                + "\n设备: " + Build.MANUFACTURER + " " + Build.MODEL
                + " (" + Build.DEVICE + ")"
                + "\n系统: Android " + Build.VERSION.RELEASE
                + " (SDK " + Build.VERSION.SDK_INT + ")"
                + "\n系统构建: " + Build.DISPLAY
                + "\nApp: " + BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE
                + ", " + (BuildConfig.DEBUG ? "debug" : "release") + ")";
        return FeedbackText.build(metadata, issueType, description,
                ProcessExitDiagnostics.read(context),
                AuthorizationDiagnostics.read(context),
                NotificationPocketApplication.readCrashLog(context));
    }

    static Uri writeShareFile(Context context, String report) {
        File file = shareFile(context);
        File temporary = new File(file.getParentFile(), file.getName() + ".next");
        File parent = file.getParentFile();
        Uri uri = FeedbackShareProvider.contentUri(context);
        try {
            // A previous recipient must not gain access to a later, different report at this URI.
            context.revokeUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            if (parent == null || (!parent.isDirectory() && !parent.mkdirs())) {
                throw new IOException("Unable to create feedback directory");
            }
            try (FileOutputStream stream = new FileOutputStream(temporary, false);
                    OutputStreamWriter writer = new OutputStreamWriter(stream, StandardCharsets.UTF_8)) {
                writer.write(report);
                writer.flush();
                stream.getFD().sync();
            }
            try {
                Files.move(temporary.toPath(), file.toPath(),
                        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            return uri;
        } catch (IOException | RuntimeException failure) {
            Log.w(TAG, "Unable to export feedback report", failure);
            if (temporary.isFile() && !temporary.delete()) {
                Log.w(TAG, "Unable to remove incomplete feedback report");
            }
            return null;
        }
    }

    static File shareFile(Context context) {
        return new File(new File(context.getFilesDir(), "feedback"),
                "notification-pocket-feedback.txt");
    }

    static boolean clearLocalLogs(Context context) {
        boolean cleared = true;
        try {
            context.revokeUriPermission(FeedbackShareProvider.contentUri(context),
                    Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (RuntimeException failure) {
            Log.w(TAG, "Unable to revoke feedback report grants", failure);
            cleared = false;
        }
        cleared = AuthorizationDiagnostics.clear(context) && cleared;
        cleared = NotificationPocketApplication.clearCrashLog(context) && cleared;
        File shared = shareFile(context);
        File temporary = new File(shared.getParentFile(), shared.getName() + ".next");
        cleared = (!shared.exists() || shared.delete()) && cleared;
        return (!temporary.exists() || temporary.delete()) && cleared;
    }
}
