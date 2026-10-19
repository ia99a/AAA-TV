package com.sva.launcher;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.chrono.HijrahDate;
import java.time.temporal.ChronoField;

/** Arabic text for the prayer screen: Arabic-Indic digits, 12-hour times, dates and durations. */
final class ArabicFormat {
    private static final String[] WEEKDAYS = {"الاثنين", "الثلاثاء", "الأربعاء", "الخميس", "الجمعة", "السبت", "الأحد"};
    private static final String[] MONTHS = {"يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو",
            "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر"};
    private static final String[] HIJRI_MONTHS = {"محرم", "صفر", "ربيع الأول", "ربيع الآخر", "جمادى الأولى",
            "جمادى الآخرة", "رجب", "شعبان", "رمضان", "شوال", "ذو القعدة", "ذو الحجة"};

    private ArabicFormat() { }

    static String digits(String latin) {
        StringBuilder out = new StringBuilder(latin.length());
        for (int i = 0; i < latin.length(); i++) {
            char c = latin.charAt(i);
            out.append(c >= '0' && c <= '9' ? (char) ('٠' + (c - '0')) : c);
        }
        return out.toString();
    }

    static String digits(long value) { return digits(Long.toString(value)); }

    /** "٤:٠٥" — hour and minutes in 12-hour form, without the period. */
    static String clock(LocalDateTime time) {
        int hour = time.getHour() % 12;
        if (hour == 0) hour = 12;
        int minute = time.getMinute();
        return digits(hour + ":" + (minute < 10 ? "0" : "") + minute);
    }

    /** "ص" before noon, "م" after. */
    static String period(LocalDateTime time) { return time.getHour() < 12 ? "ص" : "م"; }

    static String clockWithPeriod(LocalDateTime time) { return clock(time) + " " + period(time); }

    static String gregorian(LocalDate day) {
        return WEEKDAYS[day.getDayOfWeek().getValue() - 1] + "، " + digits(day.getDayOfMonth()) + " "
                + MONTHS[day.getMonthValue() - 1] + " " + digits(day.getYear());
    }

    static String hijri(LocalDate day) {
        try {
            HijrahDate h = HijrahDate.from(day);
            return digits(h.get(ChronoField.DAY_OF_MONTH)) + " " + HIJRI_MONTHS[h.get(ChronoField.MONTH_OF_YEAR) - 1]
                    + " " + digits(h.get(ChronoField.YEAR)) + " هـ";
        } catch (RuntimeException outsideSupportedRange) {
            return "";
        }
    }

    /** Natural Arabic duration, e.g. "ساعتين و٥ دقائق", rounded to the nearest minute. */
    static String duration(long millis) {
        long minutes = Math.max(0, Math.round(millis / 60000.0));
        long h = minutes / 60, m = minutes % 60;
        String hours = h == 0 ? "" : h == 1 ? "ساعة" : h == 2 ? "ساعتين" : h <= 10 ? digits(h) + " ساعات" : digits(h) + " ساعة";
        String mins = m == 0 ? "" : m == 1 ? "دقيقة" : m == 2 ? "دقيقتين" : m <= 10 ? digits(m) + " دقائق" : digits(m) + " دقيقة";
        if (hours.isEmpty() && mins.isEmpty()) return "الآن";
        if (hours.isEmpty()) return mins;
        if (mins.isEmpty()) return hours;
        return hours + " و" + mins;
    }

    static String ramadan(LocalDate today) {
        int days = PrayerTimes.daysUntilRamadan(today);
        if (days == 0) {
            try {
                return "رمضان مبارك · اليوم " + digits(HijrahDate.from(today).get(ChronoField.DAY_OF_MONTH)) + " من رمضان";
            } catch (RuntimeException ignored) {
                return "رمضان مبارك";
            }
        }
        if (days == 1) return "رمضان غدًا بإذن الله";
        if (days > 1) return "باقي على رمضان " + digits(days) + (days <= 10 ? " أيام" : " يومًا");
        return "";
    }
}
