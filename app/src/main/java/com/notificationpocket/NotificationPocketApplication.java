package com.notificationpocket;

import android.app.Application;

/** Restores the system-owned listener connection whenever our process starts. */
public final class NotificationPocketApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        NotificationController.getInstance().recoverConnection(this);
    }
}
