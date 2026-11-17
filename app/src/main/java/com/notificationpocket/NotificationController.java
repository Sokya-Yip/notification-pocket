package com.notificationpocket;

import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/** Single seam between Android's listener service and the activity. */
public final class NotificationController {
    private static final String TAG = "NotificationPocket";

    public interface Listener {
        void onNotificationEnvironmentChanged();
    }

    public interface OperationCallback {
        void onResult(OperationResult result);
    }

    public enum OperationResult {
        SUCCESS,
        SERVICE_UNAVAILABLE,
        REJECTED
    }

    private static final NotificationController INSTANCE = new NotificationController();

    public static NotificationController getInstance() {
        return INSTANCE;
    }

    private static final long[] REBIND_DELAYS_MS = {0L, 1000L, 3000L, 8000L};
    private static final long DISCONNECT_RECOVERY_COOLDOWN_MS = 60000L;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Set<Listener> listeners = new CopyOnWriteArraySet<>();
    private volatile NotificationPocketService service;
    private Context applicationContext;
    private int nextRebindAttempt;
    private boolean rebindScheduled;
    private long lastDisconnectRecoveryMs = -DISCONNECT_RECOVERY_COOLDOWN_MS;

    private final Runnable rebindTask = new Runnable() {
        @Override
        public void run() {
            rebindScheduled = false;
            Context context = applicationContext;
            if (service != null || context == null) {
                return;
            }
            if (nextRebindAttempt >= REBIND_DELAYS_MS.length) {
                applicationContext = null;
                return;
            }
            ComponentName listener = new ComponentName(context, NotificationPocketService.class);
            try {
                NotificationManager manager = context.getSystemService(NotificationManager.class);
                if (manager == null || !manager.isNotificationListenerAccessGranted(listener)) {
                    stopRebindAttempts();
                    notifyChanged();
                    return;
                }
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "Requesting listener rebind, attempt " + (nextRebindAttempt + 1));
                }
                requestListenerRebind(listener, nextRebindAttempt == 0);
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "Listener rebind request returned");
                }
            } catch (RuntimeException unavailable) {
                // Permission can change between the status check and this system request.
                if (BuildConfig.DEBUG) {
                    Log.w(TAG, "Listener rebind request failed", unavailable);
                }
                stopRebindAttempts();
                notifyChanged();
                return;
            }
            nextRebindAttempt++;
            if (nextRebindAttempt < REBIND_DELAYS_MS.length) {
                scheduleRebind(REBIND_DELAYS_MS[nextRebindAttempt]);
            } else {
                applicationContext = null;
                notifyChanged();
            }
        }
    };

    private NotificationController() {
    }

    void attach(NotificationPocketService attachedService) {
        mainHandler.removeCallbacks(rebindTask);
        rebindScheduled = false;
        nextRebindAttempt = 0;
        lastDisconnectRecoveryMs = -DISCONNECT_RECOVERY_COOLDOWN_MS;
        applicationContext = null;
        service = attachedService;
        notifyChanged();
    }

    void detach(NotificationPocketService detachedService) {
        if (service == detachedService) {
            service = null;
            notifyChanged();
        }
    }

    public boolean isConnected() {
        return service != null;
    }

    public boolean isRecovering() {
        return rebindScheduled;
    }

    /** Starts a bounded recovery round from an activity, process start, or package update. */
    public void recoverConnection(Context context) {
        recoverConnection(context, false);
    }

    /** Rate-limits recovery caused by repeated background disconnect callbacks. */
    void recoverConnectionAfterDisconnect(Context context) {
        recoverConnection(context, true);
    }

    private void recoverConnection(Context context, boolean afterDisconnect) {
        Context appContext = context.getApplicationContext();
        mainHandler.post(() -> {
            if (service != null || rebindScheduled) {
                return;
            }
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Recovery requested, afterDisconnect=" + afterDisconnect);
            }
            if (afterDisconnect) {
                long now = SystemClock.elapsedRealtime();
                if (now - lastDisconnectRecoveryMs < DISCONNECT_RECOVERY_COOLDOWN_MS) {
                    return;
                }
                lastDisconnectRecoveryMs = now;
            }
            applicationContext = appContext;
            nextRebindAttempt = 0;
            scheduleRebind(REBIND_DELAYS_MS[0]);
            notifyChanged();
        });
    }

    private void requestListenerRebind(ComponentName listener, boolean resetSystemBinding) {
        if (resetSystemBinding && Build.VERSION.SDK_INT >= 34) {
            // requestRebind is a no-op while the system still marks the component enabled.
            // Reset only the service binding, never the user's notification-access grant.
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Resetting system listener binding before rebind");
            }
            NotificationListenerService.requestUnbind(listener);
        }
        NotificationListenerService.requestRebind(listener);
    }

    private void scheduleRebind(long delayMs) {
        rebindScheduled = true;
        mainHandler.postDelayed(rebindTask, delayMs);
    }

    private void stopRebindAttempts() {
        mainHandler.removeCallbacks(rebindTask);
        rebindScheduled = false;
        nextRebindAttempt = REBIND_DELAYS_MS.length;
        applicationContext = null;
    }

    public void addListener(Listener listener) {
        listeners.add(listener);
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    public void notifyChanged() {
        mainHandler.post(() -> {
            for (Listener listener : listeners) {
                listener.onNotificationEnvironmentChanged();
            }
        });
    }

    public List<NotificationEntry> getActive(Context context) {
        NotificationPocketService current = service;
        if (current == null) {
            return Collections.emptyList();
        }
        try {
            return snapshots(context, current.getActiveNotifications(), false);
        } catch (RuntimeException ignored) {
            return Collections.emptyList();
        }
    }

    public List<NotificationEntry> getHidden(Context context) {
        NotificationPocketService current = service;
        if (current == null) {
            return Collections.emptyList();
        }
        try {
            return snapshots(context, current.getSnoozedNotifications(), true);
        } catch (RuntimeException ignored) {
            return Collections.emptyList();
        }
    }

    public void snooze(String key, long durationMs, OperationCallback callback) {
        NotificationPocketService current = service;
        if (current == null) {
            callback.onResult(OperationResult.SERVICE_UNAVAILABLE);
            return;
        }
        try {
            current.snoozeNotification(key, durationMs);
            notifyChangedAndSettle();
            mainHandler.postDelayed(() -> confirmSnooze(current, key, callback, true), 450L);
        } catch (RuntimeException ignored) {
            callback.onResult(OperationResult.REJECTED);
        }
    }

    /** Ask Android to repost an existing snooze almost immediately, then verify active state. */
    public void restore(String key, OperationCallback callback) {
        NotificationPocketService current = service;
        if (current == null) {
            callback.onResult(OperationResult.SERVICE_UNAVAILABLE);
            return;
        }
        try {
            // A positive duration updates an existing snooze. Unlike 0L, 1L reaches reposting.
            current.snoozeNotification(key, 1L);
            notifyChangedAndSettle();
            mainHandler.postDelayed(() -> confirmRestore(current, key, callback, 0), 750L);
        } catch (RuntimeException ignored) {
            callback.onResult(OperationResult.REJECTED);
        }
    }

    private void confirmRestore(
            NotificationPocketService current, String key, OperationCallback callback, int attempt) {
        boolean confirmed;
        try {
            confirmed = isActive(current, key);
        } catch (RuntimeException ignored) {
            confirmed = false;
        }
        if (confirmed) {
            callback.onResult(OperationResult.SUCCESS);
        } else if (attempt < 2) {
            long delay = attempt == 0 ? 1250L : 2000L;
            mainHandler.postDelayed(() -> confirmRestore(current, key, callback, attempt + 1), delay);
        } else {
            callback.onResult(OperationResult.REJECTED);
        }
    }

    private void confirmSnooze(
            NotificationPocketService current, String key, OperationCallback callback, boolean retry) {
        boolean confirmed;
        try {
            confirmed = isSnoozed(current, key);
        } catch (RuntimeException ignored) {
            confirmed = false;
        }
        if (confirmed) {
            callback.onResult(OperationResult.SUCCESS);
        } else if (retry) {
            mainHandler.postDelayed(() -> confirmSnooze(current, key, callback, false), 1000L);
        } else {
            callback.onResult(OperationResult.REJECTED);
        }
    }

    private boolean isSnoozed(NotificationPocketService current, String key) {
        StatusBarNotification[] snoozed = current.getSnoozedNotifications();
        if (snoozed == null) {
            return false;
        }
        for (StatusBarNotification item : snoozed) {
            if (key.equals(item.getKey())) {
                return true;
            }
        }
        return false;
    }

    private boolean isActive(NotificationPocketService current, String key) {
        StatusBarNotification[] active = current.getActiveNotifications();
        if (active == null) {
            return false;
        }
        for (StatusBarNotification item : active) {
            if (key.equals(item.getKey())) {
                return true;
            }
        }
        return false;
    }

    private List<NotificationEntry> snapshots(
            Context context, StatusBarNotification[] sources, boolean snoozed) {
        if (sources == null || sources.length == 0) {
            return Collections.emptyList();
        }
        List<NotificationEntry> result = new ArrayList<>(sources.length);
        for (StatusBarNotification source : sources) {
            result.add(NotificationEntry.from(context, source, snoozed));
        }
        result.sort((left, right) -> Long.compare(right.postTime, left.postTime));
        return result;
    }

    private void notifyChangedAndSettle() {
        notifyChanged();
        mainHandler.postDelayed(this::notifyChanged, 350L);
        mainHandler.postDelayed(this::notifyChanged, 1200L);
    }
}
