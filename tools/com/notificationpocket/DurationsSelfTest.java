package com.notificationpocket;

public final class DurationsSelfTest {
    public static void main(String[] args) {
        usesDocumentedDurations();
        keepsDurationsOrdered();
        keepsDurationsPositive();
        usesLongTermDefault();
        System.out.println("DurationsSelfTest: 4 groups passed");
    }

    private static void usesDocumentedDurations() {
        check(Durations.ONE_HOUR_MS == 3_600_000L, "one hour");
        check(Durations.ONE_DAY_MS == 86_400_000L, "one day");
        check(Durations.LONG_TERM_MS == 315_360_000_000L, "3650 days");
    }

    private static void keepsDurationsOrdered() {
        check(Durations.ONE_HOUR_MS < Durations.ONE_DAY_MS, "hour before day");
        check(Durations.ONE_DAY_MS < Durations.LONG_TERM_MS, "day before long term");
    }

    private static void keepsDurationsPositive() {
        check(Durations.ONE_HOUR_MS > 0L, "positive hour");
        check(Durations.ONE_DAY_MS > 0L, "positive day");
        check(Durations.LONG_TERM_MS > 0L, "positive long term");
    }

    private static void usesLongTermDefault() {
        check(Durations.DEFAULT_MS == Durations.LONG_TERM_MS, "long-term default");
    }

    private static void check(boolean condition, String name) {
        if (!condition) {
            throw new AssertionError("Failed: " + name);
        }
    }
}
