package com.notificationpocket;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Android may stop the old listener process while installing an update. */
public final class PackageUpdateReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction())) {
            NotificationController.getInstance().recoverConnection(context);
        }
    }
}
