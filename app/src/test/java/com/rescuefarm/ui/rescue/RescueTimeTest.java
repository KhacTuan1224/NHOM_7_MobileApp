package com.rescuefarm.ui.rescue;

import java.time.Instant;
import java.util.Calendar;
import java.util.TimeZone;
import org.junit.Test;
import static org.junit.Assert.*;

public class RescueTimeTest {
    @Test public void allQuickChoicesUpdateOneConsistentPickupWindow() {
        long now = Instant.parse("2026-10-21T09:00:00Z").toEpochMilli();
        for (int minutes : new int[]{15, 30, 60}) {
            RescueTime.PickupWindow window = RescueTime.quickWindow(now, minutes);
            assertEquals(minutes * 60000L, window.closesAt - now);
            assertEquals(60000L, window.pickupStartAt - now);
            assertEquals(30 * 60000L, window.pickupEndAt - window.closesAt);
            assertTrue(window.pickupEndAt > window.pickupStartAt);
        }
    }

    @Test public void quickWindowCrossingMidnightDisplaysTheNextVietnamDate() {
        long now = Instant.parse("2026-10-21T16:50:00Z").toEpochMilli();
        RescueTime.PickupWindow window = RescueTime.quickWindow(now, 30);
        assertEquals("21/10/2026 23:51", RescueTime.format(window.pickupStartAt));
        assertEquals("22/10/2026 00:20", RescueTime.format(window.closesAt));
        assertEquals("22/10/2026 00:50", RescueTime.format(window.pickupEndAt));
        // Presets are anchored to each press, rather than the time the editor opened.
        RescueTime.PickupWindow later = RescueTime.quickWindow(now + 5 * 60000L, 30);
        assertEquals(window.closesAt + 5 * 60000L, later.closesAt);
    }

    @Test public void vietnamDisplayDoesNotDependOnEmulatorTimeZone() {
        TimeZone original = TimeZone.getDefault();
        try {
            for (String zone : new String[]{"GMT", "Asia/Ho_Chi_Minh", "America/Los_Angeles"}) {
                TimeZone.setDefault(TimeZone.getTimeZone(zone));
                assertEquals("20/10/2026 21:30", RescueTime.format(Instant.parse("2026-10-20T14:30:00Z").toEpochMilli()));
            }
        } finally { TimeZone.setDefault(original); }
    }

    @Test public void selecting2130OnGmtDeviceProduces1430UtcNot2130Utc() {
        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("GMT"));
            Calendar selection = RescueTime.calendar(Instant.parse("2026-10-20T14:00:00Z").toEpochMilli());
            selection.set(2026, Calendar.OCTOBER, 20, 21, 30, 0);
            selection.set(Calendar.MILLISECOND, 0);
            assertEquals(Instant.parse("2026-10-20T14:30:00Z").toEpochMilli(), selection.getTimeInMillis());
        } finally { TimeZone.setDefault(original); }
    }

    @Test public void quick30MinuteWindowShowsVietnamHoursWithoutChangingItsDuration() {
        long now = Instant.parse("2026-10-20T14:00:00Z").toEpochMilli();
        assertEquals("20/10/2026 21:30", RescueTime.format(now + 30 * 60000L));
        assertEquals("20/10/2026 21:01", RescueTime.format(now + 60000L));
        assertEquals("20/10/2026 22:00", RescueTime.format(now + 60 * 60000L));
        assertEquals(now, RescueTime.calendar(now).getTimeInMillis());
    }

    @Test public void datePickerMinimumUsesVietnamDateWhenGmtIsStillOnPreviousDay() {
        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("GMT"));
            long now = Instant.parse("2026-10-20T17:30:00Z").toEpochMilli();
            assertEquals("21/10/2026 00:30", RescueTime.format(now));
            Calendar minimum = Calendar.getInstance(); minimum.setTimeInMillis(RescueTime.pickerMinDate(now));
            assertEquals(2026, minimum.get(Calendar.YEAR));
            assertEquals(Calendar.OCTOBER, minimum.get(Calendar.MONTH));
            assertEquals(21, minimum.get(Calendar.DAY_OF_MONTH));
            assertEquals(0, minimum.get(Calendar.HOUR_OF_DAY));
        } finally { TimeZone.setDefault(original); }
    }
}
