package com.sva.launcher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Fires at each adhan and iqama (private to Sva), shows the alert, and sets the next one. */
public final class PrayerAlertReceiver extends BroadcastReceiver {
    /** Re-arms the next alert after a reboot, an app update, or a clock or time-zone change. */
    public static final class Rearm extends BroadcastReceiver {
        @Override public void onReceive(Context context, Intent intent) {
            PrayerAlerts.schedule(context.getApplicationContext());
        }
    }

    @Override public void onReceive(Context context, Intent intent) {
        Context app = context.getApplicationContext();
        if (!intent.hasExtra(PrayerAlerts.EXTRA_AT)) {
            PrayerAlerts.schedule(app);
            return;
        }
        int prayer = intent.getIntExtra(PrayerAlerts.EXTRA_PRAYER, PrayerTimes.FAJR);
        boolean iqama = intent.getBooleanExtra(PrayerAlerts.EXTRA_IQAMA, false);
        long at = intent.getLongExtra(PrayerAlerts.EXTRA_AT, 0L);
        // Schedule the next alert a moment later so this one is not picked again.
        PrayerAlerts.schedule(app);
        if (!PrayerAlerts.enabled(app)) return;
        // Skip alerts that arrive long after their time, for example after the TV was off.
        if (Math.abs(System.currentTimeMillis() - at) > 5 * 60_000L) return;
        PendingResult result = goAsync();
        PrayerAlertToast.show(app, prayer, iqama, result::finish);
    }
}
