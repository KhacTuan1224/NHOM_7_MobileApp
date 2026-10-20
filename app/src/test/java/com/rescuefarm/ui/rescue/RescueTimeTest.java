package com.rescuefarm.ui.rescue;

import java.time.Instant;
import java.util.Calendar;
import java.util.TimeZone;
import org.junit.Test;
import static org.junit.Assert.*;

public class RescueTimeTest {
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
