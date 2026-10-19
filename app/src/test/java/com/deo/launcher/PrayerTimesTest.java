package com.sva.launcher;

import org.junit.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class PrayerTimesTest {
    private static int minutesOfDay(LocalDateTime time) { return time.getHour() * 60 + time.getMinute(); }

    private static void assertNear(int expectedHour, int expectedMinute, LocalDateTime actual) {
        int diff = Math.abs(minutesOfDay(actual) - (expectedHour * 60 + expectedMinute));
        assertTrue("expected " + expectedHour + ":" + expectedMinute + " but was " + actual.toLocalTime(), diff <= 1);
    }

    @Test public void riyadhMatchesUmmAlQuraWithinAMinute() {
        PrayerTimes.City riyadh = PrayerTimes.city("riyadh");
        LocalDateTime[] t = PrayerTimes.forDay(LocalDate.of(2026, 10, 18), riyadh.latitude, riyadh.longitude, 3);
        assertNear(4, 35, t[PrayerTimes.FAJR]);
        assertNear(11, 38, t[PrayerTimes.DHUHR]);
        assertNear(14, 56, t[PrayerTimes.ASR]);
        assertNear(17, 23, t[PrayerTimes.MAGHRIB]);
        assertNear(18, 53, t[PrayerTimes.ISHA]);
    }

    @Test public void ishaIsTwoHoursAfterMaghribInRamadan() {
        LocalDate ramadanDay = LocalDate.of(2026, 3, 1); // 12 Ramadan 1447
        assertTrue(PrayerTimes.isRamadan(ramadanDay));
        LocalDateTime[] t = PrayerTimes.forDay(ramadanDay, 24.7136, 46.6753, 3);
        assertEquals(120, minutesOfDay(t[PrayerTimes.ISHA]) - minutesOfDay(t[PrayerTimes.MAGHRIB]), 1);
    }

    @Test public void iqamaFollowsTheAdhan() {
        LocalDateTime[] t = PrayerTimes.forDay(LocalDate.of(2026, 10, 18), 24.7136, 46.6753, 3);
        assertEquals(t[PrayerTimes.FAJR].plusMinutes(25), PrayerTimes.iqama(t, PrayerTimes.FAJR));
        assertEquals(t[PrayerTimes.MAGHRIB].plusMinutes(10), PrayerTimes.iqama(t, PrayerTimes.MAGHRIB));
    }

    @Test public void formatsArabicText() {
        assertEquals("٤:٠٥", ArabicFormat.clock(LocalDateTime.of(2026, 10, 18, 16, 5)));
        assertEquals("م", ArabicFormat.period(LocalDateTime.of(2026, 10, 18, 16, 5)));
        assertEquals("١٢:٠٠", ArabicFormat.clock(LocalDateTime.of(2026, 10, 18, 0, 0)));
        assertEquals("ساعتين و٥ دقائق", ArabicFormat.duration(125 * 60_000L));
        assertEquals("٨ ساعات و٤٤ دقيقة", ArabicFormat.duration((8 * 60 + 44) * 60_000L));
        assertEquals("الاثنين، ١٩ أكتوبر ٢٠٢٦", ArabicFormat.gregorian(LocalDate.of(2026, 10, 19)));
        assertTrue(ArabicFormat.hijri(LocalDate.of(2026, 10, 19)).contains("جمادى الأولى"));
    }

    @Test public void versesCoverEveryDay() {
        for (long day = -5; day < 100; day++) {
            String[] verse = DailyVerses.forEpochDay(day);
            assertEquals(3, verse.length);
            assertTrue(!verse[1].isEmpty());
        }
    }
}
