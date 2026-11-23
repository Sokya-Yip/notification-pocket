package com.notificationpocket;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.ApplicationExitInfo;
import android.content.Context;
import android.os.Build;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Reads only this app's system-owned exit history; no permission or notification data is needed. */
final class ProcessExitDiagnostics {
    private ProcessExitDiagnostics() {
    }

    static String read(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return "Android 11 以下不提供系统历史退出原因。";
        }
        return Api30Impl.read(context);
    }

    /** Kept in a separate class so API 27–29 never verify API 30 method signatures. */
    @SuppressLint("NewApi")
    private static final class Api30Impl {
        private Api30Impl() {
        }

        static String read(Context context) {
            ActivityManager manager = (ActivityManager) context.getSystemService(
                    Context.ACTIVITY_SERVICE);
            if (manager == null) {
                return "无法读取系统历史退出原因：ActivityManager 不可用。";
            }
            try {
                List<ApplicationExitInfo> exits = manager.getHistoricalProcessExitReasons(
                        context.getPackageName(), 0, 5);
                if (exits == null || exits.isEmpty()) {
                    return "暂无系统历史退出记录（空列表不代表从未发生故障）。";
                }
                StringBuilder result = new StringBuilder();
                SimpleDateFormat stamps = new SimpleDateFormat(
                        "yyyy-MM-dd HH:mm:ss.SSS", Locale.US);
                for (ApplicationExitInfo exit : exits) {
                    if (exit == null) {
                        continue;
                    }
                    if (result.length() > 0) {
                        result.append('\n');
                    }
                    result.append(stamps.format(new Date(exit.getTimestamp())))
                            .append(" | pid=").append(exit.getPid())
                            .append(" | reason=").append(reasonName(exit.getReason()))
                            .append(" (").append(exit.getReason()).append(')');
                }
                return result.length() == 0 ? "暂无系统历史退出记录。" : result.toString();
            } catch (RuntimeException failure) {
                return "无法读取系统历史退出原因: " + failure.getClass().getName()
                        + ": " + String.valueOf(failure.getMessage());
            }
        }

        private static String reasonName(int reason) {
            switch (reason) {
                case ApplicationExitInfo.REASON_EXIT_SELF:
                    return "EXIT_SELF";
                case ApplicationExitInfo.REASON_SIGNALED:
                    return "SIGNALED";
                case ApplicationExitInfo.REASON_LOW_MEMORY:
                    return "LOW_MEMORY";
                case ApplicationExitInfo.REASON_CRASH:
                    return "CRASH";
                case ApplicationExitInfo.REASON_CRASH_NATIVE:
                    return "CRASH_NATIVE";
                case ApplicationExitInfo.REASON_ANR:
                    return "ANR";
                case ApplicationExitInfo.REASON_INITIALIZATION_FAILURE:
                    return "INITIALIZATION_FAILURE";
                case ApplicationExitInfo.REASON_PERMISSION_CHANGE:
                    return "PERMISSION_CHANGE";
                case ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE:
                    return "EXCESSIVE_RESOURCE_USAGE";
                case ApplicationExitInfo.REASON_USER_REQUESTED:
                    return "USER_REQUESTED";
                case ApplicationExitInfo.REASON_USER_STOPPED:
                    return "USER_STOPPED";
                case ApplicationExitInfo.REASON_DEPENDENCY_DIED:
                    return "DEPENDENCY_DIED";
                case ApplicationExitInfo.REASON_OTHER:
                    return "OTHER";
                case ApplicationExitInfo.REASON_UNKNOWN:
                default:
                    return "UNKNOWN_OR_NEW_REASON";
            }
        }
    }
}
