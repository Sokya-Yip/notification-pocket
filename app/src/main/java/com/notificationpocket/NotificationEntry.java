package com.notificationpocket;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.service.notification.StatusBarNotification;
import android.text.TextUtils;
import android.text.format.DateUtils;

/** Immutable UI snapshot. The source notification remains owned by Android. */
public final class NotificationEntry {
    public final String key;
    public final String appLabel;
    public final String title;
    public final String text;
    public final String meta;
    public final long postTime;
    public final boolean snoozed;
    public final Drawable icon;

    private NotificationEntry(
            String key,
            String appLabel,
            String title,
            String text,
            String meta,
            long postTime,
            boolean snoozed,
            Drawable icon
    ) {
        this.key = key;
        this.appLabel = appLabel;
        this.title = title;
        this.text = text;
        this.meta = meta;
        this.postTime = postTime;
        this.snoozed = snoozed;
        this.icon = icon;
    }

    public static NotificationEntry from(Context context, StatusBarNotification source, boolean snoozed) {
        PackageManager packageManager = context.getPackageManager();
        String packageName = source.getPackageName();
        String appLabel = packageName;
        Drawable icon = null;
        try {
            ApplicationInfo info = packageManager.getApplicationInfo(packageName, 0);
            CharSequence label = packageManager.getApplicationLabel(info);
            if (!TextUtils.isEmpty(label)) {
                appLabel = label.toString();
            }
            icon = packageManager.getApplicationIcon(info);
        } catch (PackageManager.NameNotFoundException | RuntimeException ignored) {
            // Package visibility varies by Android version. A package name is a safe fallback.
        }

        String title = titleOf(source);
        String text = textOf(source);
        String relativeTime = DateUtils.getRelativeTimeSpanString(
                source.getPostTime(),
                System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS,
                DateUtils.FORMAT_ABBREV_RELATIVE
        ).toString();
        String meta = (snoozed ? "已隐藏 · " : "") + relativeTime;
        return new NotificationEntry(
                source.getKey(), appLabel, title, text, meta, source.getPostTime(), snoozed, icon
        );
    }

    private static String titleOf(StatusBarNotification source) {
        Bundle extras = source.getNotification().extras;
        CharSequence value = extras == null ? null : extras.getCharSequence(android.app.Notification.EXTRA_TITLE);
        return value == null ? "" : value.toString();
    }

    private static String textOf(StatusBarNotification source) {
        Bundle extras = source.getNotification().extras;
        CharSequence value = extras == null ? null : extras.getCharSequence(android.app.Notification.EXTRA_TEXT);
        return value == null ? "" : value.toString();
    }
}
