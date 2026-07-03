package com.skarian.outlookgooglesync;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction()) && AppConfig.scheduled(context)) {
            SyncScheduler.schedule(context);
        }
    }
}
