package com.ledowalled.threerrood;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class AmazonPhoneBootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {

        if (intent == null ||
            !Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            return;
        }

        boolean enabled = context.getSharedPreferences(
            "phone_monitor", Context.MODE_PRIVATE
        ).getBoolean("enabled", false);

        if (!enabled) return;

        Intent service = new Intent(
            context, AmazonPhoneMonitorService.class
        );

        try {
            if (Build.VERSION.SDK_INT >= 26) {
                context.startForegroundService(service);
            } else {
                context.startService(service);
            }
        } catch (Exception e) {
            android.util.Log.e(
                "AmazonPhoneMonitor",
                "Boot restart failed: " + e.getMessage()
            );
        }
    }
}
