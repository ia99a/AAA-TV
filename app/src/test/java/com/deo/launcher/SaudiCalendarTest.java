package com.sva.launcher;

import org.junit.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class SaudiCalendarTest {
    private static SaudiCalendar.Item find(List<SaudiCalendar.Item> items, String title) {
        for (SaudiCalendar.Item item : items) if (item.title.equals(title)) return item;
        throw new AssertionError("missing " + title);
    }

    @Test public void placesIslamicOccasionsOnUmmAlQuraDates() {
        List<SaudiCalendar.Item> items = SaudiCalendar.between(LocalDate.of(2027, 1, 1), LocalDate.of(2027, 6, 30));
        assertEquals(LocalDate.of(2027, 2, 8), find(items, SaudiCalendar.RAMADAN).start);
        assertEquals(LocalDate.of(2027, 3, 9), find(items, SaudiCalendar.EID_FITR).start);
        assertEquals(LocalDate.of(2027, 5, 15), find(items, SaudiCalendar.ARAFAH).start);
        assertEquals(LocalDate.of(2027, 5, 16), find(items, SaudiCalendar.EID_ADHA).start);
    }

    @Test public void whiteDaysSkipRamadanAndTashreeq() {
        for (SaudiCalendar.Item item : SaudiCalendar.between(LocalDate.of(2027, 2, 1), LocalDate.of(2027, 6, 1))) {
            if (!item.title.equals(SaudiCalendar.WHITE)) continue;
            String label = SaudiCalendar.hijriCellLabel(item.start);
            assertTrue(label, label.equals("١٣") || label.equals("١٤"));
            assertTrue(item.start.isBefore(LocalDate.of(2027, 2, 8)) || item.start.isAfter(LocalDate.of(2027, 3, 8)));
        }
    }

    @Test public void includesNationalDaysAndSchoolHolidays() {
        List<SaudiCalendar.Item> items = SaudiCalendar.between(LocalDate.of(2026, 9, 1), LocalDate.of(2027, 3, 31));
        assertEquals(LocalDate.of(2026, 9, 23), find(items, SaudiCalendar.NATIONAL_DAY).start);
        assertEquals(LocalDate.of(2027, 2, 22), find(items, SaudiCalendar.FOUNDING_DAY).start);
        assertEquals(SaudiCalendar.SCHOOL, SaudiCalendar.kindOf("إجازة منتصف العام"));
        assertEquals(-1, SaudiCalendar.kindOf("Dentist"));
    }
}
