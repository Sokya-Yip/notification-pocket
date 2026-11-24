package com.notificationpocket;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Collections;
import java.util.List;

public final class MainActivity extends Activity implements NotificationController.Listener {
    private static final String TAG = "NotificationPocket";
    /** Vendor Settings fragment-routing keys; values are the flattened listener component. */
    private static final String FRAGMENT_ARGS_KEY = ":settings:fragment_args_key";
    private static final String SHOW_FRAGMENT_ARGS = ":settings:show_fragment_args";

    private enum NotificationAccess {
        GRANTED,
        DENIED,
        UNKNOWN
    }

    private final NotificationController controller = NotificationController.getInstance();

    private NotificationAdapter adapter;
    private ListView notificationList;
    private TextView emptyText;
    private TextView permissionText;
    private ImageView permissionDot;
    private Button permissionButton;
    private View permissionGuide;
    private TextView hiddenTabText;
    private TextView activeTabText;
    private View hiddenIndicator;
    private View activeIndicator;
    private LinearLayout hiddenTab;
    private LinearLayout activeTab;

    /** How long to wait for the system Settings page to actually take window focus. */
    private static final long SETTINGS_HANDOFF_CHECK_MS = 1200L;

    private boolean showHidden;
    private boolean resumeAuthorization;
    /** Set once the vendor detail page failed to appear, so a retry skips it. */
    private boolean detailSettingsUnreachable;
    private Runnable settingsHandoffCheck;
    private Runnable settingsHandoffRepair;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AuthorizationDiagnostics.record(this, "activity_create");
        // Remove obsolete automation state from older versions; this version stores no rule state.
        deleteSharedPreferences("quiet_rules");
        setContentView(R.layout.activity_main);
        // Must run after setContentView: the insets controller needs a created decor view.
        applyEdgeToEdge();
        bindViews();
        applySystemBarInsets();

        adapter = new NotificationAdapter(this, entry -> {
            if (entry.snoozed) {
                restoreNotification(entry);
            } else {
                snooze(entry, Durations.DEFAULT_MS);
            }
        }, entry -> {
            if (!entry.snoozed) {
                showDurationDialog(entry);
            }
        });
        notificationList.setAdapter(adapter);
        notificationList.setEmptyView(emptyText);

        hiddenTab.setOnClickListener(view -> selectTab(true));
        activeTab.setOnClickListener(view -> selectTab(false));
        permissionButton.setOnClickListener(view -> {
            AuthorizationDiagnostics.begin(this);
            AuthorizationDiagnostics.record(this, "connected=" + controller.isConnected()
                    + " | recovering=" + controller.isRecovering());
            if (queryNotificationAccess(true) == NotificationAccess.DENIED) {
                openNotificationAccessSettings();
            } else {
                AuthorizationDiagnostics.record(this, "permission_not_definitely_denied_rebind");
                requestServiceRebind();
            }
        });
        findViewById(R.id.about_button).setOnClickListener(view -> showAboutDialog());
        findViewById(R.id.support_button).setOnClickListener(view -> showSupportDialog());
        findViewById(R.id.restricted_settings_button).setOnClickListener(
                view -> showRestrictedSettingsDialog());
        selectTab(false);
        // A crash dialog and an unfinished-handoff notice are mutually exclusive: both are
        // startup notices, and stacking two AlertDialogs would hide the first behind the second.
        if (!showCrashDialogIfPresent()) {
            showUnfinishedHandoffDialogIfNeeded();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        controller.addListener(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Coming back means the Settings page did take over; the handoff needs no repair.
        cancelSettingsHandoffWatch();
        AuthorizationDiagnostics.record(this, "activity_resume");
        if (AuthorizationDiagnostics.isActive(this)) {
            queryNotificationAccess(true);
        }
        AuthorizationDiagnostics.record(this, "recover_connection_before");
        controller.recoverConnection(this);
        AuthorizationDiagnostics.record(this, "recover_connection_after");
        refresh();
        if (resumeAuthorization) {
            resumeAuthorization = false;
            if (queryNotificationAccess() == NotificationAccess.DENIED) {
                openNotificationAccessSettings();
            }
        }
    }

    @Override
    protected void onPause() {
        AuthorizationDiagnostics.record(this, "activity_pause");
        super.onPause();
    }

    @Override
    protected void onStop() {
        AuthorizationDiagnostics.record(this, "activity_stop");
        controller.removeListener(this);
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        cancelSettingsHandoffWatch();
        AuthorizationDiagnostics.record(this, "activity_destroy");
        super.onDestroy();
    }

    @Override
    public void onNotificationEnvironmentChanged() {
        refresh();
    }

    private void bindViews() {
        notificationList = findViewById(R.id.notification_list);
        emptyText = findViewById(R.id.empty_text);
        permissionText = findViewById(R.id.permission_text);
        permissionDot = findViewById(R.id.permission_dot);
        permissionButton = findViewById(R.id.permission_button);
        permissionGuide = findViewById(R.id.permission_guide);
        hiddenTab = findViewById(R.id.hidden_tab);
        activeTab = findViewById(R.id.active_tab);
        hiddenTabText = findViewById(R.id.hidden_tab_text);
        activeTabText = findViewById(R.id.active_tab_text);
        hiddenIndicator = findViewById(R.id.hidden_indicator);
        activeIndicator = findViewById(R.id.active_indicator);
    }

    private void selectTab(boolean hidden) {
        showHidden = hidden;
        refresh();
    }

    private void refresh() {
        if (adapter == null) {
            return;
        }
        NotificationAccess access = queryNotificationAccess();
        boolean accessDenied = access == NotificationAccess.DENIED;
        boolean connected = controller.isConnected();
        boolean recovering = controller.isRecovering();
        List<NotificationEntry> hidden = !accessDenied && connected
                ? controller.getHidden(this) : Collections.emptyList();
        List<NotificationEntry> active = !accessDenied && connected
                ? controller.getActive(this) : Collections.emptyList();

        hiddenTabText.setText(getString(R.string.hidden_tab) + " " + hidden.size());
        activeTabText.setText(getString(R.string.active_tab) + " " + active.size());
        adapter.setItems(showHidden ? hidden : active);
        updateTabAppearance();
        updatePermissionAppearance(access, connected, recovering);

        if (accessDenied) {
            emptyText.setText(R.string.empty_need_permission);
        } else if (!connected) {
            emptyText.setText(recovering
                    ? R.string.empty_connecting : R.string.empty_disconnected);
        } else if (showHidden) {
            emptyText.setText(R.string.empty_hidden);
        } else {
            emptyText.setText(R.string.empty_active);
        }
    }

    private void updateTabAppearance() {
        hiddenTab.setSelected(showHidden);
        activeTab.setSelected(!showHidden);
        hiddenIndicator.setVisibility(showHidden ? View.VISIBLE : View.INVISIBLE);
        activeIndicator.setVisibility(showHidden ? View.INVISIBLE : View.VISIBLE);
        hiddenTabText.setTextColor(getColor(showHidden ? R.color.forest : R.color.muted));
        activeTabText.setTextColor(getColor(showHidden ? R.color.muted : R.color.forest));
        hiddenTabText.setTypeface(Typeface.DEFAULT, showHidden ? Typeface.BOLD : Typeface.NORMAL);
        activeTabText.setTypeface(Typeface.DEFAULT, showHidden ? Typeface.NORMAL : Typeface.BOLD);
    }

    private void updatePermissionAppearance(
            NotificationAccess access, boolean connected, boolean recovering) {
        boolean accessDenied = access == NotificationAccess.DENIED;
        if (accessDenied) {
            permissionText.setText(R.string.permission_needed);
        } else if (connected) {
            permissionText.setText(R.string.permission_granted);
        } else {
            permissionText.setText(recovering
                    ? R.string.permission_connecting : R.string.permission_disconnected);
        }
        permissionButton.setText(accessDenied ? R.string.grant_permission : R.string.reconnect);
        permissionGuide.setVisibility(accessDenied ? View.VISIBLE : View.GONE);
        permissionDot.setColorFilter(getColor(
                !accessDenied && connected ? R.color.forest : R.color.muted));
    }

    private NotificationAccess queryNotificationAccess() {
        return queryNotificationAccess(false);
    }

    private NotificationAccess queryNotificationAccess(boolean trace) {
        if (trace) {
            AuthorizationDiagnostics.record(this, "permission_query_start");
        }
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) {
            if (trace) {
                AuthorizationDiagnostics.record(this, "permission_query_manager_null");
            }
            return NotificationAccess.UNKNOWN;
        }
        try {
            NotificationAccess result = manager.isNotificationListenerAccessGranted(
                    new ComponentName(this, NotificationPocketService.class))
                    ? NotificationAccess.GRANTED : NotificationAccess.DENIED;
            if (trace) {
                AuthorizationDiagnostics.record(this, "permission_query_result=" + result);
            }
            return result;
        } catch (RuntimeException unavailable) {
            // A vendor service can be temporarily unavailable while applying the grant.
            Log.w(TAG, "Notification access query failed", unavailable);
            if (trace) {
                AuthorizationDiagnostics.record(this, "permission_query_exception", unavailable);
            }
            return NotificationAccess.UNKNOWN;
        }
    }

    private void openNotificationAccessSettings() {
        // Both builds must exercise the same navigation so emulator checks cover what users run.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !detailSettingsUnreachable
                && !prefersNotificationListPage()) {
            ComponentName listener = new ComponentName(this, NotificationPocketService.class);
            // Settings reads this extra with getStringExtra and unflattens it. Passing the
            // ComponentName parcelable instead yields null inside Settings and crashes its
            // detail page after startActivity has already returned, so our catch cannot see it.
            String flattened = listener.flattenToString();
            Bundle fragmentArgs = new Bundle();
            fragmentArgs.putString(FRAGMENT_ARGS_KEY, flattened);
            Intent detail = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                    .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, flattened)
                    // Some vendor Settings builds route the detail page through fragment args.
                    .putExtra(FRAGMENT_ARGS_KEY, flattened)
                    .putExtra(SHOW_FRAGMENT_ARGS, fragmentArgs);
            if (startSettingsActivity(detail)) {
                watchSettingsHandoff(() -> {
                    detailSettingsUnreachable = true;
                    openNotificationAccessList();
                });
                return;
            }
        }
        openNotificationAccessList();
    }

    /**
     * HyperOS-class builds are the only part of this flow with no device evidence behind the
     * detail page, and they are the reported place where it leaves the caller at the launcher.
     * The list page takes no extra at all, so an extra-type mismatch cannot break it. The cost
     * is one extra scroll for a screen this project cannot verify.
     */
    private boolean prefersNotificationListPage() {
        return "xiaomi".equalsIgnoreCase(Build.MANUFACTURER);
    }

    private void openNotificationAccessList() {
        if (startSettingsActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))) {
            Toast.makeText(this, R.string.find_notification_pocket, Toast.LENGTH_LONG).show();
            watchSettingsHandoff(this::showSettingsBlockedDialog);
            return;
        }
        if (openApplicationDetailsSettings()) {
            return;
        }
        showSettingsBlockedDialog();
    }

    /**
     * A vendor detail page can die after startActivity has returned, which leaves the caller
     * visible with no explanation and nothing to catch. Losing window focus is the only local
     * evidence that Settings really took over, so repair the handoff instead of trusting the
     * return value of startActivity.
     */
    private void watchSettingsHandoff(Runnable repair) {
        cancelSettingsHandoffWatch();
        settingsHandoffRepair = repair;
        settingsHandoffCheck = () -> {
            settingsHandoffCheck = null;
            Runnable next = settingsHandoffRepair;
            settingsHandoffRepair = null;
            if (!hasWindowFocus() || isFinishing() || isDestroyed()) {
                return;
            }
            AuthorizationDiagnostics.record(this, "settings_handoff_not_observed");
            if (next != null) {
                next.run();
            }
        };
        permissionButton.postDelayed(settingsHandoffCheck, SETTINGS_HANDOFF_CHECK_MS);
    }

    private void cancelSettingsHandoffWatch() {
        settingsHandoffRepair = null;
        if (settingsHandoffCheck != null) {
            permissionButton.removeCallbacks(settingsHandoffCheck);
            settingsHandoffCheck = null;
        }
    }

    /**
     * Shown when no system page took over. It states the manual path a blocked vendor build
     * leaves as the only option, and hands the user a ready report to paste back in one tap,
     * so a device we cannot test still produces evidence.
     */
    private void showSettingsBlockedDialog() {
        String report = FeedbackReport.build(
                this, getString(R.string.feedback_issue_unopenable), "");
        new AlertDialog.Builder(this)
                .setTitle(R.string.settings_blocked_title)
                .setMessage(R.string.settings_blocked_message)
                .setPositiveButton(R.string.feedback_share,
                        (dialog, which) -> shareFeedback(report))
                .setNeutralButton(R.string.copy, (dialog, which) ->
                        copyText("notification pocket feedback", report))
                .setNegativeButton(R.string.close, null)
                .show();
    }

    private void showRestrictedSettingsDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.restricted_settings_title)
                .setMessage(R.string.restricted_settings_message)
                .setNegativeButton(R.string.close, null)
                .setPositiveButton(R.string.open_app_details, (dialog, which) -> {
                    resumeAuthorization = true;
                    if (!openApplicationDetailsSettings()) {
                        resumeAuthorization = false;
                        Toast.makeText(this, R.string.permission_settings_unavailable,
                                Toast.LENGTH_LONG).show();
                    }
                })
                .show();
    }

    private boolean openApplicationDetailsSettings() {
        Intent details = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", getPackageName(), null));
        return startSettingsActivity(details);
    }

    private boolean startSettingsActivity(Intent intent) {
        String action = String.valueOf(intent.getAction());
        AuthorizationDiagnostics.record(this, "settings_start_attempt=" + action);
        try {
            startActivity(intent);
            AuthorizationDiagnostics.record(this, "settings_start_returned=" + action);
            return true;
        } catch (RuntimeException unavailable) {
            AuthorizationDiagnostics.record(this, "settings_start_exception=" + action, unavailable);
            return false;
        }
    }

    private void requestServiceRebind() {
        if (controller.isConnected()) {
            Toast.makeText(this, R.string.already_connected, Toast.LENGTH_SHORT).show();
            return;
        }
        AuthorizationDiagnostics.record(this, "manual_rebind_before");
        controller.resetAndRecoverConnection(this);
        AuthorizationDiagnostics.record(this, "manual_rebind_after");
        Toast.makeText(this, R.string.reconnecting, Toast.LENGTH_SHORT).show();
    }

    private void showAboutDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.about_title)
                .setMessage(getString(R.string.about_message, BuildConfig.VERSION_NAME))
                .setPositiveButton(R.string.close, null)
                .setNeutralButton(R.string.check_update,
                        (dialog, which) -> openReleasePage())
                .setNegativeButton(R.string.feedback,
                        (dialog, which) -> showFeedbackDialog())
                .show();
    }

    private void showFeedbackDialog() {
        View content = getLayoutInflater().inflate(R.layout.dialog_feedback, null);
        Spinner issue = content.findViewById(R.id.feedback_issue);
        EditText description = content.findViewById(R.id.feedback_description);
        TextView sources = content.findViewById(R.id.feedback_sources);
        sources.setText(feedbackSourcesSummary());
        new AlertDialog.Builder(this)
                .setTitle(R.string.feedback)
                .setView(content)
                .setPositiveButton(R.string.feedback_generate, (dialog, which) -> {
                    String selectedIssue = issue.getSelectedItem() == null
                            ? getString(R.string.feedback) : issue.getSelectedItem().toString();
                    String report = FeedbackReport.build(
                            this, selectedIssue, description.getText().toString());
                    showFeedbackPreview(report);
                })
                .setNegativeButton(R.string.feedback_clear,
                        (dialog, which) -> confirmClearDiagnostics())
                .setNeutralButton(R.string.close, null)
                .show();
    }

    private String feedbackSourcesSummary() {
        StringBuilder sources = new StringBuilder();
        String trace = AuthorizationDiagnostics.read(this);
        if (trace != null && !trace.isEmpty()) {
            sources.append(getString(R.string.feedback_source_authorization));
        }
        String crash = NotificationPocketApplication.readCrashLog(this);
        if (crash != null && !crash.isEmpty()) {
            if (sources.length() > 0) {
                sources.append("、");
            }
            sources.append(getString(R.string.feedback_source_crash));
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (sources.length() > 0) {
                sources.append("、");
            }
            sources.append(getString(R.string.feedback_source_exits));
        }
        return sources.length() == 0 ? getString(R.string.feedback_source_empty)
                : getString(R.string.feedback_sources, sources.toString());
    }

    private void showFeedbackPreview(String report) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.feedback_preview_title)
                .setMessage(report)
                .setPositiveButton(R.string.close, null)
                .setNegativeButton(R.string.copy, (dialog, which) ->
                        copyText("notification pocket feedback", report))
                .setNeutralButton(R.string.feedback_share, (dialog, which) ->
                        shareFeedback(report))
                .show();
    }

    private void shareFeedback(String report) {
        Uri uri = FeedbackReport.writeShareFile(this, report);
        if (uri == null) {
            Toast.makeText(this, R.string.feedback_share_failed, Toast.LENGTH_LONG).show();
            return;
        }
        Intent share = new Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_STREAM, uri);
        share.setClipData(ClipData.newUri(getContentResolver(),
                getString(R.string.feedback_share_title), uri));
        share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivity(Intent.createChooser(share, getString(R.string.feedback_share_title)));
        } catch (RuntimeException unavailable) {
            Toast.makeText(this, R.string.feedback_share_failed, Toast.LENGTH_LONG).show();
        }
    }

    private void confirmClearDiagnostics() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.feedback_clear_title)
                .setMessage(R.string.feedback_clear_message)
                .setPositiveButton(R.string.feedback_clear_confirm, (dialog, which) -> {
                    boolean cleared = FeedbackReport.clearLocalLogs(this);
                    Toast.makeText(this, cleared ? R.string.feedback_cleared
                            : R.string.feedback_clear_failed, Toast.LENGTH_LONG).show();
                })
                .setNegativeButton(R.string.close, null)
                .show();
    }

    private void copyText(String label, String text) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText(label, text));
            Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show();
        }
    }

    /** Opens the release page in the browser; this app itself keeps no network access. */
    private void openReleasePage() {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://github.com/Sokya-Yip/notification-pocket/releases/latest")));
        } catch (RuntimeException unavailable) {
            Toast.makeText(this, R.string.update_open_failed, Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Detects the vendor-kill case that leaves no crash log at all: the last authorization
     * jump returned from startActivity but the process never resumed. It is reported on the
     * next cold start, because that is the first moment our code runs again to speak about it.
     */
    private void showUnfinishedHandoffDialogIfNeeded() {
        if (!AuthorizationDiagnostics.lastHandoffNeverReturned(this)) {
            return;
        }
        String report = FeedbackReport.build(this, getString(R.string.feedback_issue_crash), "");
        new AlertDialog.Builder(this)
                .setTitle(R.string.handoff_unfinished_title)
                .setMessage(R.string.handoff_unfinished_message)
                .setPositiveButton(R.string.feedback_share,
                        (dialog, which) -> shareFeedback(report))
                .setNeutralButton(R.string.copy, (dialog, which) ->
                        copyText("notification pocket feedback", report))
                .setNegativeButton(R.string.close, null)
                .show();
    }

    /**
     * Surfaces an uncaught stack trace on the next launch so testers can copy it without adb.
     * Returns whether a notice was shown so only one startup notice appears at a time.
     */
    private boolean showCrashDialogIfPresent() {
        String report = NotificationPocketApplication.readCrashLog(this);
        if (report == null || report.isEmpty()) {
            return false;
        }
        // The log outlives this dialog only when the user is on the way into the feedback flow,
        // which reads the file itself. Any other acknowledgement must not resurrect it on the
        // next launch; the log holds only the latest crash, so it never stops being shown.
        new AlertDialog.Builder(this)
                .setTitle(R.string.crash_dialog_title)
                .setMessage(getString(R.string.crash_dialog_message) + "\n\n" + report)
                .setPositiveButton(R.string.feedback,
                        (dialog, which) -> showFeedbackDialog())
                .setNeutralButton(R.string.copy, (dialog, which) -> {
                    copyText("crash log", report);
                    NotificationPocketApplication.clearCrashLog(this);
                })
                .setNegativeButton(R.string.close,
                        (dialog, which) -> NotificationPocketApplication.clearCrashLog(this))
                .setOnCancelListener(dialog ->
                        NotificationPocketApplication.clearCrashLog(this))
                .show();
        return true;
    }

    private void showSupportDialog() {
        View content = getLayoutInflater().inflate(R.layout.dialog_support, null);
        ImageView qr = content.findViewById(R.id.support_wechat_qr);
        qr.setOnLongClickListener(view -> {
            shareSupportQrToWeChat();
            return true;
        });
        content.findViewById(R.id.share_support_wechat).setOnClickListener(
                view -> shareSupportQrToWeChat());
        new AlertDialog.Builder(this)
                .setTitle(R.string.support_author)
                .setView(content)
                .setPositiveButton(R.string.close, null)
                .show();
    }

    private void shareSupportQrToWeChat() {
        Uri uri = SupportQrProvider.contentUri(this);
        // WeChat exports several image receivers. Target its exported send-to-chat receiver so
        // package-only sharing does not make the system offer chat, status, favorite and timeline.
        Intent intent = new Intent(Intent.ACTION_SEND)
                .setClassName("com.tencent.mm", "com.tencent.mm.ui.tools.ShareImgUI")
                .setType("image/jpeg")
                .putExtra(Intent.EXTRA_STREAM, uri);
        intent.setClipData(ClipData.newUri(
                getContentResolver(), getString(R.string.support_wechat_title), uri));
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException directShareUnavailable) {
            // A WeChat version may rename its exported receiver. Keep the standard package share
            // as a compatibility fallback; the user still chooses the recipient and sends manually.
            Intent fallback = new Intent(intent)
                    .setComponent(null)
                    .setPackage("com.tencent.mm");
            try {
                startActivity(fallback);
            } catch (ActivityNotFoundException | SecurityException unavailable) {
                Toast.makeText(this, R.string.support_wechat_unavailable, Toast.LENGTH_LONG).show();
            }
        }
    }

    private void showDurationDialog(NotificationEntry entry) {
        CharSequence[] choices = {
                getString(R.string.long_term),
                getString(R.string.one_day),
                getString(R.string.one_hour)
        };
        long[] durations = {
                Durations.LONG_TERM_MS,
                Durations.ONE_DAY_MS,
                Durations.ONE_HOUR_MS
        };
        new AlertDialog.Builder(this)
                .setTitle(R.string.choose_duration)
                .setItems(choices, (dialog, which) -> snooze(entry, durations[which]))
                .show();
    }

    private void snooze(NotificationEntry entry, long durationMs) {
        controller.snooze(entry.key, durationMs, result -> {
            if (result == NotificationController.OperationResult.SUCCESS) {
                int message = durationMs == Durations.ONE_HOUR_MS
                        ? R.string.hidden_hour_success
                        : durationMs == Durations.ONE_DAY_MS
                                ? R.string.hidden_day_success : R.string.long_hidden_success;
                Toast.makeText(this, message, Toast.LENGTH_LONG).show();
            } else if (result == NotificationController.OperationResult.REJECTED) {
                Toast.makeText(this, R.string.operation_rejected, Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, R.string.service_unavailable, Toast.LENGTH_LONG).show();
            }
            refresh();
        });
    }

    private void restoreNotification(NotificationEntry entry) {
        Toast.makeText(this, R.string.restoring, Toast.LENGTH_SHORT).show();
        controller.restore(entry.key, result -> {
            int message;
            if (result == NotificationController.OperationResult.SUCCESS) {
                message = R.string.restore_success;
            } else if (result == NotificationController.OperationResult.REJECTED) {
                message = R.string.restore_failed;
            } else {
                message = R.string.service_unavailable;
            }
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
            refresh();
        });
    }

    /**
     * Draws the page behind both system bars so the navigation bar shows the same
     * background as the content instead of a separate near-white strip. The root
     * view pads itself with the bar insets, so nothing is covered.
     */
    private void applyEdgeToEdge() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // The window still paints the bar colors itself, so both must go transparent.
            getWindow().setStatusBarColor(Color.TRANSPARENT);
            getWindow().setNavigationBarColor(Color.TRANSPARENT);
            // The page is light in both bar areas; do not add an automatic contrast scrim.
            getWindow().setStatusBarContrastEnforced(false);
            getWindow().setNavigationBarContrastEnforced(false);
            getWindow().setDecorFitsSystemWindows(false);
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                int appearance = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                        | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                controller.setSystemBarsAppearance(appearance, appearance);
            }
        } else {
            // Older devices always paint opaque bars, so match the content color instead.
            getWindow().setStatusBarColor(getColor(R.color.mist));
            getWindow().setNavigationBarColor(getColor(R.color.mist));
        }
    }

    private void applySystemBarInsets() {
        View root = findViewById(R.id.root);
        int left = root.getPaddingLeft();
        int right = root.getPaddingRight();
        root.setOnApplyWindowInsetsListener((view, windowInsets) -> {
            int top;
            int bottom;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                android.graphics.Insets insets = windowInsets.getInsets(
                        WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                top = insets.top;
                bottom = insets.bottom;
            } else {
                top = windowInsets.getSystemWindowInsetTop();
                bottom = windowInsets.getSystemWindowInsetBottom();
            }
            view.setPadding(left, top, right, bottom);
            return windowInsets;
        });
    }
}
