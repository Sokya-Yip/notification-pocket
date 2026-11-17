package com.notificationpocket;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Collections;
import java.util.List;

public final class MainActivity extends Activity implements NotificationController.Listener {
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

    private boolean showHidden;
    private boolean resumeAuthorization;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Remove obsolete automation state from older versions; this version stores no rule state.
        deleteSharedPreferences("quiet_rules");
        setContentView(R.layout.activity_main);
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
            if (hasNotificationAccess()) {
                requestServiceRebind();
            } else {
                openNotificationAccessSettings();
            }
        });
        findViewById(R.id.about_button).setOnClickListener(view -> showAboutDialog());
        findViewById(R.id.support_button).setOnClickListener(view -> showSupportDialog());
        findViewById(R.id.restricted_settings_button).setOnClickListener(
                view -> showRestrictedSettingsDialog());
        selectTab(false);
    }

    @Override
    protected void onStart() {
        super.onStart();
        controller.addListener(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        controller.recoverConnection(this);
        refresh();
        if (resumeAuthorization) {
            resumeAuthorization = false;
            if (!hasNotificationAccess()) {
                openNotificationAccessSettings();
            }
        }
    }

    @Override
    protected void onStop() {
        controller.removeListener(this);
        super.onStop();
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
        boolean granted = hasNotificationAccess();
        boolean connected = controller.isConnected();
        boolean recovering = controller.isRecovering();
        List<NotificationEntry> hidden = granted && connected
                ? controller.getHidden(this) : Collections.emptyList();
        List<NotificationEntry> active = granted && connected
                ? controller.getActive(this) : Collections.emptyList();

        hiddenTabText.setText(getString(R.string.hidden_tab) + " " + hidden.size());
        activeTabText.setText(getString(R.string.active_tab) + " " + active.size());
        adapter.setItems(showHidden ? hidden : active);
        updateTabAppearance();
        updatePermissionAppearance(granted, connected, recovering);

        if (!granted) {
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

    private void updatePermissionAppearance(boolean granted, boolean connected, boolean recovering) {
        if (!granted) {
            permissionText.setText(R.string.permission_needed);
        } else if (connected) {
            permissionText.setText(R.string.permission_granted);
        } else {
            permissionText.setText(recovering
                    ? R.string.permission_connecting : R.string.permission_disconnected);
        }
        permissionButton.setText(granted ? R.string.reconnect : R.string.grant_permission);
        permissionGuide.setVisibility(granted ? View.GONE : View.VISIBLE);
        permissionDot.setColorFilter(getColor(granted && connected ? R.color.forest : R.color.muted));
    }

    private boolean hasNotificationAccess() {
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        return manager != null && manager.isNotificationListenerAccessGranted(
                new ComponentName(this, NotificationPocketService.class));
    }

    private void openNotificationAccessSettings() {
        ComponentName listener = new ComponentName(this, NotificationPocketService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent detail = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                    .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, listener);
            if (startSettingsActivity(detail)) {
                return;
            }
        }
        if (startSettingsActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))) {
            Toast.makeText(this, R.string.find_notification_pocket, Toast.LENGTH_LONG).show();
            return;
        }
        if (!openApplicationDetailsSettings()) {
            Toast.makeText(this, R.string.permission_settings_unavailable, Toast.LENGTH_LONG).show();
        }
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
        try {
            startActivity(intent);
            return true;
        } catch (RuntimeException unavailable) {
            return false;
        }
    }

    private void requestServiceRebind() {
        if (controller.isConnected()) {
            Toast.makeText(this, R.string.already_connected, Toast.LENGTH_SHORT).show();
            return;
        }
        controller.recoverConnection(this);
        Toast.makeText(this, R.string.reconnecting, Toast.LENGTH_SHORT).show();
    }

    private void showAboutDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(R.string.about_title)
                .setMessage(getString(R.string.about_message, BuildConfig.VERSION_NAME))
                .setPositiveButton(R.string.close, null);
        builder.setNegativeButton(R.string.support_author,
                (dialog, which) -> showSupportDialog());
        builder.show();
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
