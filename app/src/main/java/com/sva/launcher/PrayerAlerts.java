package com.sva.launcher;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Schedules a gentle alert at every adhan and iqama time for the chosen city. One alarm is set
 * at a time; when it fires, the receiver shows the alert and schedules the next one.
 */
final class PrayerAlerts {
    static final String PREF_ENABLED = "prayerAlerts";
    static final String PREF_CITY = "prayerCity";
    static final String EXTRA_PRAYER = "prayer", EXTRA_IQAMA = "iqama", EXTRA_AT = "at";
    private static final int[] PRAYERS = {PrayerTimes.FAJR, PrayerTimes.DHUHR, PrayerTimes.ASR,
            PrayerTimes.MAGHRIB, PrayerTimes.ISHA};

    static final class Next {
        final long at;
        final int prayer;
        final boolean iqama;
        Next(long at, int prayer, boolean iqama) { this.at = at; this.prayer = prayer; this.iqama = iqama; }
    }

    private PrayerAlerts() { }

    static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences("launcher", Context.MODE_PRIVATE);
    }

    static boolean enabled(Context context) {
        return preferences(context).getBoolean(PREF_ENABLED, true);
    }

    /** The next adhan or iqama after {@code now}, looking at today and tomorrow. */
    static Next next(String cityKey, LocalDateTime now, ZoneId zone) {
        PrayerTimes.City city = PrayerTimes.city(cityKey);
        Next best = null;
        for (int offset = 0; offset <= 1; offset++) {
            LocalDate day = now.toLocalDate().plusDays(offset);
            double hours = zone.getRules().getOffset(day.atTime(12, 0)).getTotalSeconds() / 3600.0;
            LocalDateTime[] times = PrayerTimes.forDay(day, city.latitude, city.longitude, hours);
            for (int prayer : PRAYERS) {
                LocalDateTime adhan = times[prayer], iqama = PrayerTimes.iqama(times, prayer);
                if (adhan.isAfter(now) && (best == null || millis(adhan, zone) < best.at))
                    best = new Next(millis(adhan, zone), prayer, false);
                if (iqama.isAfter(now) && (best == null || millis(iqama, zone) < best.at))
                    best = new Next(millis(iqama, zone), prayer, true);
            }
            if (best != null) return best;
        }
        return best;
    }

    static void schedule(Context context) {
        AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarms == null) return;
        Intent intent = new Intent(context, PrayerAlertReceiver.class);
        if (!enabled(context)) {
            alarms.cancel(PendingIntent.getBroadcast(context, 0, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
            return;
        }
        ZoneId zone = ZoneId.systemDefault();
        Next next = next(preferences(context).getString(PREF_CITY, "riyadh"), LocalDateTime.now(zone), zone);
        if (next == null) return;
        intent.putExtra(EXTRA_PRAYER, next.prayer).putExtra(EXTRA_IQAMA, next.iqama).putExtra(EXTRA_AT, next.at);
        PendingIntent pending = PendingIntent.getBroadcast(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        boolean exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms();
        if (exact) alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.at, pending);
        else alarms.setWindow(AlarmManager.RTC_WAKEUP, next.at, 60_000L, pending);
    }

    static String title(int prayer, boolean iqama) {
        return iqama ? "إقامة صلاة " + PrayerTimes.NAMES[prayer] : "حان وقت صلاة " + PrayerTimes.NAMES[prayer];
    }

    private static long millis(LocalDateTime time, ZoneId zone) {
        return time.atZone(zone).toInstant().toEpochMilli();
    }
}
