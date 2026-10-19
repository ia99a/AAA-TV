package com.sva.launcher;

import org.junit.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class PrayerAlertsTest {
    private static final ZoneId RIYADH = ZoneId.of("Asia/Riyadh");

    @Test public void iqamaFollowsItsAdhan() {
        // Just after Asr's adhan (14:56 on 18 October 2026), the next alert is its iqama.
        PrayerAlerts.Next next = PrayerAlerts.next("riyadh", LocalDateTime.of(2026, 10, 18, 14, 58), RIYADH);
        assertEquals(PrayerTimes.ASR, next.prayer);
        assertTrue(next.iqama);
    }

    @Test public void afterIshaIqamaComesTomorrowsFajr() {
        PrayerAlerts.Next next = PrayerAlerts.next("riyadh", LocalDateTime.of(2026, 10, 18, 22, 0), RIYADH);
        assertEquals(PrayerTimes.FAJR, next.prayer);
        assertFalse(next.iqama);
        LocalDateTime at = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(next.at), RIYADH);
        assertEquals(19, at.getDayOfMonth());
    }
}
