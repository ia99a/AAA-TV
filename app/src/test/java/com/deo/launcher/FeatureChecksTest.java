package com.sva.launcher;

import org.junit.Test;

import java.io.File;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** Checks that the main features behave as described: prayer times, alerts, calendar, Quran, wallpapers. */
public final class FeatureChecksTest {
    private static final ZoneId RIYADH = ZoneId.of("Asia/Riyadh");

    @Test public void everyCityHasPrayersInOrder() {
        for (PrayerTimes.City city : PrayerTimes.CITIES) {
            LocalDateTime[] t = PrayerTimes.forDay(LocalDate.of(2026, 10, 21), city.latitude, city.longitude, 3);
            for (int i = 1; i < t.length; i++)
                assertTrue(city.name + " prayer " + i + " out of order", t[i].isAfter(t[i - 1]));
            // Sanity: Saudi Fajr is between 3:30 and 5:30, Isha before 21:30, in October.
            assertTrue(city.name, t[PrayerTimes.FAJR].getHour() >= 3 && t[PrayerTimes.FAJR].getHour() <= 5);
            assertTrue(city.name, t[PrayerTimes.ISHA].getHour() <= 21);
        }
    }

    @Test public void northernCitiesAreAvailable() {
        for (String key : new String[]{"hail", "hafar", "rafha", "arar"})
            assertEquals(key, PrayerTimes.city(key).key);
        // An unknown key falls back to Riyadh rather than crashing.
        assertEquals("riyadh", PrayerTimes.city("nowhere").key);
    }

    @Test public void afterIshaIqamaTheNextAlertIsTomorrowsFajr() {
        PrayerAlerts.Next next = PrayerAlerts.next("riyadh", LocalDateTime.of(2026, 10, 21, 23, 30), RIYADH);
        assertNotNull(next);
        assertEquals(PrayerTimes.FAJR, next.prayer);
        assertFalse(next.iqama);
        assertTrue(next.at > LocalDateTime.of(2026, 10, 22, 3, 0).atZone(RIYADH).toInstant().toEpochMilli());
    }

    @Test public void alertsAreAlwaysInTheFuture() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 21, 0, 0);
        for (int minute = 0; minute < 24 * 60; minute += 7) {
            LocalDateTime at = now.plusMinutes(minute);
            PrayerAlerts.Next next = PrayerAlerts.next("arar", at, RIYADH);
            assertNotNull(next);
            assertTrue(next.at > at.atZone(RIYADH).toInstant().toEpochMilli());
        }
    }

    @Test public void personalOccasionIsEveryTwentiethOfMay() {
        for (int year = 2027; year <= 2029; year++) {
            List<SaudiCalendar.Item> items = SaudiCalendar.between(LocalDate.of(year, 5, 1), LocalDate.of(year, 5, 31));
            boolean found = false;
            for (SaudiCalendar.Item item : items)
                if (item.title.equals("هبة الكريم")) {
                    found = true;
                    assertEquals(LocalDate.of(year, 5, 20), item.start);
                    assertEquals(SaudiCalendar.PERSONAL, item.kind);
                }
            assertTrue("missing in " + year, found);
        }
    }

    @Test public void quranHasAllSurahsAndSecureAudio() {
        assertEquals(114, QuranData.SURAHS.length);
        assertEquals(114, QuranData.AYAHS.length);
        assertEquals(6236, java.util.Arrays.stream(QuranData.AYAHS).sum());
        for (int reciter = 0; reciter < QuranData.RECITERS.length; reciter++) {
            String url = QuranData.url(reciter, 1);
            assertTrue(url, url.startsWith("https://cdn.mp3quran.net/") && url.endsWith("/001.mp3"));
        }
    }

    @Test public void wallpaperIntervalsReadWell() {
        assertEquals("مثبّتة", GalleryView.intervalName(0));
        assertEquals("كل دقيقة", GalleryView.intervalName(1));
        assertEquals("كل ٥ دقائق", GalleryView.intervalName(5));
        assertEquals("كل ١٥ دقيقة", GalleryView.intervalName(15));
        assertEquals("كل ساعة", GalleryView.intervalName(60));
    }

    @Test public void builtInWallpapersAreFourK() throws Exception {
        File[] pictures = new File("src/main/assets/frames").listFiles((dir, name) -> name.endsWith(".jpg"));
        assertNotNull(pictures);
        assertTrue(pictures.length >= 5);
        for (File picture : pictures) {
            int[] size = jpegSize(java.nio.file.Files.readAllBytes(picture.toPath()));
            assertEquals(picture.getName(), 3840, size[0]);
            assertEquals(picture.getName(), 2160, size[1]);
            assertTrue(picture.getName() + " is too large", picture.length() < 4_000_000);
        }
    }

    /** Width and height from a JPEG's start-of-frame marker. */
    private static int[] jpegSize(byte[] data) {
        int i = 2;
        while (i + 9 < data.length) {
            if ((data[i] & 0xFF) != 0xFF) { i++; continue; }
            int marker = data[i + 1] & 0xFF;
            int length = ((data[i + 2] & 0xFF) << 8) | (data[i + 3] & 0xFF);
            if (marker >= 0xC0 && marker <= 0xCF && marker != 0xC4 && marker != 0xC8 && marker != 0xCC) {
                int height = ((data[i + 5] & 0xFF) << 8) | (data[i + 6] & 0xFF);
                int width = ((data[i + 7] & 0xFF) << 8) | (data[i + 8] & 0xFF);
                return new int[]{width, height};
            }
            i += 2 + length;
        }
        throw new AssertionError("not a JPEG");
    }
}
