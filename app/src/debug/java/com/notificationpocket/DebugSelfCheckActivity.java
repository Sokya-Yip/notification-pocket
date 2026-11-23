package com.notificationpocket;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.util.Log;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Debug-only self check. It lives in the debug source set so release builds carry no
 * test entry point, and it exercises private storage, URI grants and API guards on the
 * real device instead of only on the JVM.
 */
public final class DebugSelfCheckActivity extends Activity {
    private static final String TAG = "NotificationPocketSelfCheck";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TextView output = new TextView(this);
        output.setTextSize(12f);
        output.setText(runChecks());
        setContentView(output);
    }

    private String runChecks() {
        List<String> failures = new ArrayList<>();
        List<String> passes = new ArrayList<>();
        check("authorization limit", failures, passes, this::checkAuthorizationLimit);
        check("provider utf8/replace/reject/clear", failures, passes,
                this::checkProviderAndClear);
        check("exit history guard", failures, passes, this::checkExitHistory);
        StringBuilder text = new StringBuilder();
        text.append(failures.isEmpty() ? "ALL CHECKS PASSED\n" : "CHECKS FAILED\n");
        for (String pass : passes) {
            text.append("PASS ").append(pass).append('\n');
        }
        for (String failure : failures) {
            text.append("FAIL ").append(failure).append('\n');
        }
        Log.i(TAG, text.toString());
        FeedbackReport.clearLocalLogs(this);
        return text.toString();
    }

    private interface Check {
        void run() throws Exception;
    }

    private static void check(String name, List<String> failures, List<String> passes, Check body) {
        try {
            body.run();
            passes.add(name);
        } catch (Throwable failure) {
            failures.add(name + ": " + failure);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private void checkAuthorizationLimit() throws Exception {
        AuthorizationDiagnostics.begin(this);
        require(AuthorizationDiagnostics.isActive(this), "trace should be active after begin");
        String largeEvent = repeat("bounded-event-", 1200);
        for (int index = 0; index < 40 && AuthorizationDiagnostics.isActive(this); index++) {
            AuthorizationDiagnostics.record(this, largeEvent);
        }
        String atLimit = AuthorizationDiagnostics.read(this);
        require(atLimit != null && atLimit.contains("trace_limit_reached"),
                "limit marker missing");
        require(!AuthorizationDiagnostics.isActive(this), "trace should stop at the limit");
        require(new File(getFilesDir(), "authorization-diagnostics.log").length() <= 192L * 1024L,
                "trace file exceeds the 192KB cap");
        AuthorizationDiagnostics.record(this, "after_limit_must_not_appear");
        require(!AuthorizationDiagnostics.read(this).contains("after_limit_must_not_appear"),
                "events were appended after the limit marker");
        AuthorizationDiagnostics.begin(this);
        require(AuthorizationDiagnostics.isActive(this), "next attempt should reset the trace");
        require(!AuthorizationDiagnostics.read(this).contains("trace_limit_reached"),
                "limit marker survived a new authorization attempt");
    }

    private void checkProviderAndClear() throws Exception {
        AuthorizationDiagnostics.begin(this);
        File crash = new File(getFilesDir(), "crash.log");
        try (FileOutputStream stream = new FileOutputStream(crash)) {
            stream.write("测试异常".getBytes(StandardCharsets.UTF_8));
        }
        require("测试异常".equals(NotificationPocketApplication.readCrashLog(this)),
                "UTF-8 crash log round trip failed");
        String first = FeedbackText.build("元数据", "授权时闪退或回桌面", "第一行\r\n第二行",
                "USER_REQUESTED (10)", "授权轨迹", "测试异常");
        Uri uri = FeedbackReport.writeShareFile(this, first);
        require(uri != null, "report export returned null");
        require(uri.equals(FeedbackShareProvider.contentUri(this)), "unexpected export URI");
        ContentResolver resolver = getContentResolver();
        require("text/plain".equals(resolver.getType(uri)), "unexpected MIME type");
        require(first.equals(readUri(resolver, uri)), "UTF-8 report round trip failed");
        try (android.database.Cursor cursor = resolver.query(uri,
                new String[] {OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE},
                null, null, null)) {
            require(cursor != null && cursor.moveToFirst(), "provider query failed");
            require("notification-pocket-feedback.txt".equals(cursor.getString(0)),
                    "unexpected display name");
            require(cursor.getLong(1) == first.getBytes(StandardCharsets.UTF_8).length,
                    "reported size does not match the UTF-8 byte length");
        }
        require(resolver.insert(uri, null) == null, "provider must not accept inserts");
        require(resolver.update(uri, null, null, null) == 0, "provider must not accept updates");
        require(resolver.delete(uri, null, null) == 0, "provider must not delete");

        Uri[] rejected = new Uri[] {
                uri.buildUpon().appendPath("other").build(),
                uri.buildUpon().appendQueryParameter("query", "not-allowed").build(),
                uri.buildUpon().scheme("file").build(),
                uri.buildUpon().authority("com.notificationpocket.other").build()
        };
        for (Uri wrong : rejected) {
            require(resolver.getType(wrong) == null, "foreign URI was accepted: " + wrong);
            requireUnreadable(resolver, wrong);
        }

        String second = FeedbackText.build("元数据二", "其他问题", "REPLACEMENT_OK",
                null, null, null);
        require(FeedbackReport.writeShareFile(this, second).equals(uri),
                "replacement should reuse the same URI");
        require(second.equals(readUri(resolver, uri)), "report was not replaced");
        require(!readUri(resolver, uri).contains("第一行"), "old report content survived");

        require(FeedbackReport.clearLocalLogs(this), "clear reported a failure");
        require(AuthorizationDiagnostics.read(this) == null, "trace survived clearing");
        require(NotificationPocketApplication.readCrashLog(this) == null, "crash log survived");
        require(!FeedbackReport.shareFile(this).exists(), "report file survived clearing");
        requireUnreadable(resolver, uri);
    }

    private void checkExitHistory() {
        String value = ProcessExitDiagnostics.read(this);
        require(value != null, "exit history returned null");
        if (android.os.Build.VERSION.SDK_INT < 30) {
            require(value.contains("Android 11"), "missing pre-API-30 notice");
        } else {
            require(value.length() <= 20_000, "exit history is unexpectedly large");
            require(!value.contains("ApplicationExitInfo"), "raw class name leaked");
        }
    }

    private static void requireUnreadable(ContentResolver resolver, Uri uri) throws Exception {
        try (InputStream stream = resolver.openInputStream(uri)) {
            throw new AssertionError("URI must not be readable: " + uri + " / " + stream);
        } catch (FileNotFoundException expected) {
            // Expected: only the exact, existing report URI is readable.
        }
    }

    private static String readUri(ContentResolver resolver, Uri uri) throws Exception {
        try (InputStream input = resolver.openInputStream(uri)) {
            require(input != null, "provider returned no stream");
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) != -1) {
                output.write(buffer, 0, count);
            }
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static String repeat(String value, int count) {
        StringBuilder result = new StringBuilder(value.length() * count);
        for (int index = 0; index < count; index++) {
            result.append(value);
        }
        return result.toString();
    }

    /** Opens this activity, used only by adb verification. */
    static Intent intent(Activity activity) {
        return new Intent(activity, DebugSelfCheckActivity.class);
    }
}
