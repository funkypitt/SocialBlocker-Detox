package com.parentcontrol.socialblocker;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.parentcontrol.socialblocker.widget.DetoxWidget;

public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        // after a reboot, and after an update of the app (which kills the running VPN)
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction())) {
            BlockPreferences prefs = new BlockPreferences(context);
            if (prefs.isBlockingEnabled()) {
                Intent vpnIntent = new Intent(context, SocialBlockerVpnService.class);
                vpnIntent.setAction("START");
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(vpnIntent);
                } else {
                    context.startService(vpnIntent);
                }
            }
            DetoxWidget.INSTANCE.refresh(context);
        }
    }
}
