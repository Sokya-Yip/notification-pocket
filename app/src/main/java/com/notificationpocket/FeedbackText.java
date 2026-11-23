package com.notificationpocket;

/** Pure text assembly kept separate so report limits and formatting can be tested on the JVM. */
final class FeedbackText {
    static final int MAX_DESCRIPTION_CHARS = 500;
    private static final int MAX_SECTION_CHARS = 60_000;
    private static final String TRUNCATED = "\n……[中间内容因长度限制省略]……\n";

    private FeedbackText() {
    }

    static String build(String metadata, String issueType, String description,
            String processExits, String authorizationTrace, String crashLog) {
        StringBuilder report = new StringBuilder();
        report.append("通知收纳问题反馈\n")
                .append("由用户主动生成并分享；App 不会自动上传，接收方后续处理适用其自身规则。\n\n")
                .append(clean(metadata)).append("\n\n")
                .append("问题类型: ").append(clean(issueType)).append('\n');
        String details = clean(description);
        if (!details.isEmpty()) {
            if (details.length() > MAX_DESCRIPTION_CHARS) {
                details = details.substring(0, MAX_DESCRIPTION_CHARS) + "……";
            }
            report.append("问题描述:\n").append(details).append("\n\n");
        } else {
            report.append('\n');
        }
        appendSection(report, "系统历史退出记录（不单独代表 App 故障）", processExits);
        appendSection(report, "最近一次授权轨迹", authorizationTrace);
        appendSection(report, "最近一次未捕获异常", crashLog);
        report.append("说明: 不包含通知标题、正文或列表；请在分享前检查用户描述和公开反馈内容。\n");
        return report.toString();
    }

    private static void appendSection(StringBuilder report, String title, String content) {
        String value = clean(content);
        if (value.isEmpty()) {
            value = "暂无记录";
        } else if (value.length() > MAX_SECTION_CHARS) {
            int remaining = MAX_SECTION_CHARS - TRUNCATED.length();
            int head = remaining * 2 / 3;
            int tail = remaining - head;
            value = value.substring(0, head) + TRUNCATED
                    + value.substring(value.length() - tail);
        }
        report.append("--- ").append(title).append(" ---\n")
                .append(value).append("\n\n");
    }

    private static String clean(String value) {
        return value == null ? "" : value.replace("\r\n", "\n").replace('\r', '\n').trim();
    }
}
