package com.notificationpocket;

public final class FeedbackTextSelfTest {
    public static void main(String[] args) {
        includesUserAndDiagnosticSections();
        emptyOptionalContentIsExplicit();
        longContentIsBoundedWithBothEndsRetained();
        lineEndingsAndNullsAreNormalized();
        System.out.println("FeedbackTextSelfTest: 4 groups passed");
    }

    private static void includesUserAndDiagnosticSections() {
        String report = FeedbackText.build("设备: emulator", "授权时闪退", "点击后回桌面",
                "reason=USER_STOPPED", "permission_button_clicked", "java.lang.Exception");
        check(report.contains("设备: emulator"), "metadata");
        check(report.contains("问题类型: 授权时闪退"), "issue type");
        check(report.contains("点击后回桌面"), "description");
        check(report.contains("系统历史退出记录（不单独代表 App 故障）"), "exit heading");
        check(report.contains("reason=USER_STOPPED"), "exit body");
        check(report.contains("permission_button_clicked"), "authorization trace");
        check(report.contains("java.lang.Exception"), "crash log");
        check(report.contains("不包含通知标题、正文或列表"), "privacy boundary");
    }

    private static void emptyOptionalContentIsExplicit() {
        String report = FeedbackText.build("metadata", "其他问题", "  \n ", "", null, null);
        check(!report.contains("问题描述:\n"), "empty description omitted");
        check(count(report, "暂无记录") == 3, "three empty diagnostic sections");
        check(!report.contains("null"), "null never rendered");
    }

    private static void longContentIsBoundedWithBothEndsRetained() {
        String description = repeat('述', FeedbackText.MAX_DESCRIPTION_CHARS + 100);
        String trace = "TRACE_BEGIN" + repeat('中', 70_000) + "TRACE_END";
        String report = FeedbackText.build("metadata", "其他问题", description,
                "none", trace, "none");
        check(report.contains("TRACE_BEGIN"), "long trace beginning retained");
        check(report.contains("TRACE_END"), "long trace end retained");
        check(report.contains("中间内容因长度限制省略"), "long trace truncation marked");
        check(!report.contains(description), "description limited to 500 characters");
        check(report.length() < 70_000, "whole report remains bounded");
    }

    private static void lineEndingsAndNullsAreNormalized() {
        String report = FeedbackText.build(" metadata\r\nAndroid ", null, "第一行\r第二行\r\n第三行",
                null, null, null);
        check(report.contains("metadata\nAndroid"), "metadata line endings");
        check(report.contains("第一行\n第二行\n第三行"), "description line endings");
        check(report.contains("问题类型: \n"), "null issue is empty");
        check(!report.contains("\r"), "no CR remains");
    }

    private static int count(String text, String token) {
        int count = 0;
        int offset = 0;
        while ((offset = text.indexOf(token, offset)) >= 0) {
            count++;
            offset += token.length();
        }
        return count;
    }

    private static String repeat(char value, int count) {
        StringBuilder result = new StringBuilder(count);
        for (int index = 0; index < count; index++) {
            result.append(value);
        }
        return result.toString();
    }

    private static void check(boolean condition, String name) {
        if (!condition) {
            throw new AssertionError("Failed: " + name);
        }
    }
}
