package com.sva.launcher;

import java.time.LocalDate;
import java.time.MonthDay;
import java.time.chrono.HijrahDate;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Built-in Saudi calendar entries for the Events tab, generated on the device:
 * Islamic occasions (Umm al-Qura), the white days, Saudi national days and the
 * Ministry of Education school holidays for 1448.
 *
 * Islamic dates follow the Umm al-Qura calendar. Eids and Ramadan are confirmed by
 * moon sighting, so the real day can differ by one.
 */
final class SaudiCalendar {
    static final int ISLAMIC = 0, WHITE_DAYS = 1, NATIONAL = 2, SCHOOL = 3, PERSONAL = 4;

    static final class Item {
        final String title;
        final LocalDate start, endInclusive;
        final int kind;
        Item(String title, LocalDate start, LocalDate endInclusive, int kind) {
            this.title = title; this.start = start; this.endInclusive = endInclusive; this.kind = kind;
        }
    }

    static final String NEW_YEAR = "رأس السنة الهجرية";
    static final String TASUA = "تاسوعاء";
    static final String ASHURA = "يوم عاشوراء";
    static final String RAMADAN = "بداية شهر رمضان";
    static final String LAST_TEN = "العشر الأواخر";
    static final String EID_FITR = "عيد الفطر المبارك";
    static final String DHUL_HIJJAH_TEN = "عشر ذي الحجة";
    static final String ARAFAH = "يوم عرفة";
    static final String EID_ADHA = "عيد الأضحى المبارك";
    static final String TASHREEQ = "أيام التشريق";
    static final String WHITE = "الأيام البيض";
    static final String FOUNDING_DAY = "يوم التأسيس";
    static final String FLAG_DAY = "يوم العلم";
    static final String NATIONAL_DAY = "اليوم الوطني";
    /** Personal yearly occasion, shown in dark pink. */
    static final String HIBAT_ALKAREEM = "هبة الكريم";

    private static final String[] HIJRI_MONTHS = {"محرم", "صفر", "ربيع الأول", "ربيع الآخر", "جمادى الأولى",
            "جمادى الآخرة", "رجب", "شعبان", "رمضان", "شوال", "ذو القعدة", "ذو الحجة"};

    /** School year 1448 (2026–2027), Ministry of Education. Outside Makkah, Madinah, Jeddah and Taif. */
    private static final Item[] SCHOOL_1448 = {
            school("بداية العام الدراسي ١٤٤٨", "2026-08-23", "2026-08-23"),
            school("إجازة اليوم الوطني", "2026-09-23", "2026-09-26"),
            school("إجازة الخريف", "2026-11-20", "2026-11-28"),
            school("إجازة منتصف العام", "2027-01-08", "2027-01-16"),
            school("إجازة يوم التأسيس", "2027-02-19", "2027-02-22"),
            school("إجازة عيد الفطر", "2027-02-26", "2027-03-13"),
            school("إجازة عيد الأضحى", "2027-05-07", "2027-05-22"),
            school("الإجازة الصيفية", "2027-06-25", "2027-08-21"),
            school("بداية العام الدراسي ١٤٤٩", "2027-08-22", "2027-08-22"),
    };

    private static final Map<String, Integer> KINDS = new HashMap<>();
    static {
        for (String title : new String[]{NEW_YEAR, TASUA, ASHURA, RAMADAN, LAST_TEN, EID_FITR,
                DHUL_HIJJAH_TEN, ARAFAH, EID_ADHA, TASHREEQ}) KINDS.put(title, ISLAMIC);
        KINDS.put(WHITE, WHITE_DAYS);
        for (String title : new String[]{FOUNDING_DAY, FLAG_DAY, NATIONAL_DAY}) KINDS.put(title, NATIONAL);
        for (Item item : SCHOOL_1448) KINDS.put(item.title, SCHOOL);
        KINDS.put(HIBAT_ALKAREEM, PERSONAL);
    }

    private SaudiCalendar() { }

    /** The kind of a built-in entry, or -1 for events from the person's own calendar. */
    static int kindOf(String title) {
        Integer kind = KINDS.get(title);
        return kind == null ? -1 : kind;
    }

    /** "١٢" for most days, "١ رجب" on the first day of a Hijri month; empty outside the supported range. */
    static String hijriCellLabel(LocalDate day) {
        int[] h = hijri(day);
        if (h == null) return "";
        String number = ArabicFormat.digits(h[2]);
        return h[2] == 1 ? number + " " + HIJRI_MONTHS[h[1] - 1] : number;
    }

    /** All built-in entries that touch the range, each listed once even when it spans several days. */
    static List<Item> between(LocalDate from, LocalDate to) {
        ArrayList<Item> items = new ArrayList<>();
        // Hijri occasions, found by walking the days. Spans start on their first day.
        for (LocalDate day = from.minusDays(15); !day.isAfter(to); day = day.plusDays(1)) {
            int[] h = hijri(day);
            if (h == null) continue;
            int month = h[1], date = h[2];
            if (month == 1 && date == 1) items.add(new Item(NEW_YEAR, day, day, ISLAMIC));
            if (month == 1 && date == 9) items.add(new Item(TASUA, day, day, ISLAMIC));
            if (month == 1 && date == 10) items.add(new Item(ASHURA, day, day, ISLAMIC));
            if (month == 9 && date == 1) items.add(new Item(RAMADAN, day, day, ISLAMIC));
            if (month == 9 && date == 21) items.add(new Item(LAST_TEN, day, endOfMonth(day), ISLAMIC));
            if (month == 10 && date == 1) items.add(new Item(EID_FITR, day, day.plusDays(2), ISLAMIC));
            if (month == 12 && date == 1) items.add(new Item(DHUL_HIJJAH_TEN, day, day.plusDays(7), ISLAMIC));
            if (month == 12 && date == 9) items.add(new Item(ARAFAH, day, day, ISLAMIC));
            if (month == 12 && date == 10) items.add(new Item(EID_ADHA, day, day, ISLAMIC));
            if (month == 12 && date == 11) items.add(new Item(TASHREEQ, day, day.plusDays(2), ISLAMIC));
            // White days: 13–15 of each month. Not in Ramadan (already fasting); in Dhu al-Hijjah
            // the 13th is a day of Tashreeq, when fasting is not allowed, so only the 14th and 15th.
            if (month != 9 && month != 12 && date == 13) items.add(new Item(WHITE, day, day.plusDays(2), WHITE_DAYS));
            if (month == 12 && date == 14) items.add(new Item(WHITE, day, day.plusDays(1), WHITE_DAYS));
        }
        // Saudi national days, fixed Gregorian dates.
        for (int year = from.getYear(); year <= to.getYear(); year++) {
            addFixed(items, FOUNDING_DAY, MonthDay.of(2, 22), year);
            addFixed(items, FLAG_DAY, MonthDay.of(3, 11), year);
            addFixed(items, NATIONAL_DAY, MonthDay.of(9, 23), year);
            LocalDate gift = MonthDay.of(5, 20).atYear(year);
            items.add(new Item(HIBAT_ALKAREEM, gift, gift, PERSONAL));
        }
        for (Item item : SCHOOL_1448) items.add(item);
        ArrayList<Item> inRange = new ArrayList<>();
        for (Item item : items) if (!item.endInclusive.isBefore(from) && !item.start.isAfter(to)) inRange.add(item);
        return inRange;
    }

    private static void addFixed(List<Item> items, String title, MonthDay monthDay, int year) {
        LocalDate day = monthDay.atYear(year);
        items.add(new Item(title, day, day, NATIONAL));
    }

    private static LocalDate endOfMonth(LocalDate day) {
        LocalDate end = day;
        for (int i = 0; i < 12; i++) {
            int[] next = hijri(end.plusDays(1));
            if (next == null || next[2] == 1) break;
            end = end.plusDays(1);
        }
        return end;
    }

    /** {year, month, day} in Umm al-Qura, or null outside the supported range. */
    private static int[] hijri(LocalDate day) {
        try {
            HijrahDate h = HijrahDate.from(day);
            return new int[]{h.get(ChronoField.YEAR), h.get(ChronoField.MONTH_OF_YEAR), h.get(ChronoField.DAY_OF_MONTH)};
        } catch (RuntimeException outsideSupportedRange) {
            return null;
        }
    }

    private static Item school(String title, String start, String end) {
        return new Item(title, LocalDate.parse(start), LocalDate.parse(end), SCHOOL);
    }
}
