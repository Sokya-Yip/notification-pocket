package com.notificationpocket;

import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

/** Android owns this service's lifecycle and binds it only after explicit user approval. */
public final class NotificationPocketService extends NotificationListenerService {
    private static final String TAG = "NotificationPocket";

    @Override
    public void onCreate() {
        super.onCreate();
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Listener service created");
        }
    }

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Listener connected");
        }
        NotificationController.getInstance().attach(this);
    }

    @Override
    public void onListenerDisconnected() {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Listener disconnected");
        }
        NotificationController controller = NotificationController.getInstance();
        controller.detach(this);
        controller.recoverConnectionAfterDisconnect(this);
        super.onListenerDisconnected();
    }

    @Override
    public void onDestroy() {
        NotificationController.getInstance().detach(this);
        super.onDestroy();
    }

    @Override
    public void onNotificationPosted(StatusBarNotification item) {
        NotificationController.getInstance().notifyChanged();
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification item) {
        NotificationController.getInstance().notifyChanged();
    }
}
