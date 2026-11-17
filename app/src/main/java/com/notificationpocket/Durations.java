package com.notificationpocket;

/** Shared snooze durations, independent of notification source or content. */
public final class Durations {
    public static final long ONE_HOUR_MS = 60L * 60L * 1000L;
    public static final long ONE_DAY_MS = 24L * ONE_HOUR_MS;
    public static final long LONG_TERM_MS = 3650L * ONE_DAY_MS;
    public static final long DEFAULT_MS = LONG_TERM_MS;

    private Durations() {
    }
}
