package com.sva.launcher;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.chrono.HijrahDate;
import java.time.temporal.ChronoField;

/**
 * Offline prayer-time calculation using the Umm al-Qura convention used in Saudi Arabia:
 * Fajr at 18.5° below the horizon, Asr at one shadow length (Shafi'i), and Isha a fixed
 * 90 minutes after Maghrib (120 minutes during Ramadan).
 *
 * The solar maths follows the widely used PrayTimes.org algorithm. Results normally match
 * the official calendar to within one minute.
 */
final class PrayerTimes {
    static final int FAJR = 0, SUNRISE = 1, DHUHR = 2, ASR = 3, MAGHRIB = 4, ISHA = 5;
    static final String[] NAMES = {"الفجر", "الشروق", "الظهر", "العصر", "المغرب", "العشاء"};
    /** Default minutes between the adhan and the iqama in most Saudi mosques. Sunrise has none. */
    static final int[] IQAMA_MINUTES = {25, 0, 20, 20, 10, 20};

    static final class City {
        final String key, name;
        final double latitude, longitude;
        City(String key, String name, double latitude, double longitude) {
            this.key = key; this.name = name; this.latitude = latitude; this.longitude = longitude;
        }
    }

    static final City[] CITIES = {
            new City("riyadh", "الرياض", 24.7136, 46.6753),
            new City("qassim", "القصيم", 26.3260, 43.9750),
            new City("jeddah", "جدة", 21.4858, 39.1925),
            new City("makkah", "مكة المكرمة", 21.4225, 39.8262),
            new City("madinah", "المدينة المنورة", 24.4672, 39.6024),
            new City("dammam", "الدمام", 26.4207, 50.0888),
    };

    static City city(String key) {
        for (City city : CITIES) if (city.key.equals(key)) return city;
        return CITIES[0];
    }

    private PrayerTimes() { }

    /** Returns the six times (Fajr, Sunrise, Dhuhr, Asr, Maghrib, Isha) as local date-times. */
    static LocalDateTime[] forDay(LocalDate day, double lat, double lng, double timezoneHours) {
        double[] hours = hours(day, lat, lng, timezoneHours, isRamadan(day));
        LocalDateTime midnight = day.atStartOfDay();
        LocalDateTime[] result = new LocalDateTime[hours.length];
        for (int i = 0; i < hours.length; i++) result[i] = midnight.plusMinutes(Math.round(hours[i] * 60));
        return result;
    }

    static LocalDateTime iqama(LocalDateTime[] times, int prayer) {
        return times[prayer].plusMinutes(IQAMA_MINUTES[prayer]);
    }

    static boolean isRamadan(LocalDate day) {
        try {
            return HijrahDate.from(day).get(ChronoField.MONTH_OF_YEAR) == 9;
        } catch (RuntimeException outsideSupportedRange) {
            return false;
        }
    }

    /** Days until 1 Ramadan, 0 while Ramadan is under way, or -1 when it cannot be worked out. */
    static int daysUntilRamadan(LocalDate today) {
        if (isRamadan(today)) return 0;
        try {
            for (int i = 1; i < 400; i++) {
                HijrahDate h = HijrahDate.from(today.plusDays(i));
                if (h.get(ChronoField.MONTH_OF_YEAR) == 9 && h.get(ChronoField.DAY_OF_MONTH) == 1) return i;
            }
        } catch (RuntimeException ignored) { }
        return -1;
    }

    static double[] hours(LocalDate day, double lat, double lng, double timezoneHours, boolean ramadan) {
        double jd = julian(day.getYear(), day.getMonthValue(), day.getDayOfMonth()) - lng / (15 * 24);
        double fajr = 5, sunrise = 6, dhuhr = 12, asr = 13, maghrib = 18;
        for (int i = 0; i < 2; i++) {
            double f = angleTime(jd, lat, 18.5, fajr / 24, true);
            double s = angleTime(jd, lat, 0.833, sunrise / 24, true);
            double d = midDay(jd, dhuhr / 24);
            double a = asrTime(jd, lat, asr / 24);
            double m = angleTime(jd, lat, 0.833, maghrib / 24, false);
            fajr = f; sunrise = s; dhuhr = d; asr = a; maghrib = m;
        }
        double adjust = timezoneHours - lng / 15;
        fajr += adjust; sunrise += adjust; dhuhr += adjust; asr += adjust; maghrib += adjust;
        double isha = maghrib + (ramadan ? 2 : 1.5);
        return new double[]{fajr, sunrise, dhuhr, asr, maghrib, isha};
    }

    private static double julian(int year, int month, int day) {
        if (month <= 2) { year -= 1; month += 12; }
        double a = Math.floor(year / 100.0);
        double b = 2 - a + Math.floor(a / 4);
        return Math.floor(365.25 * (year + 4716)) + Math.floor(30.6001 * (month + 1)) + day + b - 1524.5;
    }

    /** {declination, equation of time} for the given fraction of a day. */
    private static double[] sun(double jd, double t) {
        double d = jd - 2451545.0 + t;
        double g = fix(357.529 + 0.98560028 * d, 360);
        double q = fix(280.459 + 0.98564736 * d, 360);
        double l = fix(q + 1.915 * dsin(g) + 0.020 * dsin(2 * g), 360);
        double e = 23.439 - 0.00000036 * d;
        double ra = fix(darctan2(dcos(e) * dsin(l), dcos(l)) / 15, 24);
        return new double[]{darcsin(dsin(e) * dsin(l)), q / 15 - ra};
    }

    private static double midDay(double jd, double t) {
        return fix(12 - sun(jd, t)[1], 24);
    }

    private static double angleTime(double jd, double lat, double angle, double t, boolean beforeNoon) {
        double decl = sun(jd, t)[0];
        double cos = (-dsin(angle) - dsin(decl) * dsin(lat)) / (dcos(decl) * dcos(lat));
        double hourAngle = darccos(Math.max(-1, Math.min(1, cos))) / 15;
        return midDay(jd, t) + (beforeNoon ? -hourAngle : hourAngle);
    }

    private static double asrTime(double jd, double lat, double t) {
        double decl = sun(jd, t)[0];
        double angle = -Math.toDegrees(Math.atan(1 / (1 + dtan(Math.abs(lat - decl)))));
        return angleTime(jd, lat, angle, t, false);
    }

    private static double fix(double a, double b) {
        a = a - b * Math.floor(a / b);
        return a < 0 ? a + b : a;
    }
    private static double dsin(double d) { return Math.sin(Math.toRadians(d)); }
    private static double dcos(double d) { return Math.cos(Math.toRadians(d)); }
    private static double dtan(double d) { return Math.tan(Math.toRadians(d)); }
    private static double darcsin(double x) { return Math.toDegrees(Math.asin(x)); }
    private static double darccos(double x) { return Math.toDegrees(Math.acos(x)); }
    private static double darctan2(double y, double x) { return Math.toDegrees(Math.atan2(y, x)); }
}
