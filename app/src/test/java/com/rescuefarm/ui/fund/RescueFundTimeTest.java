package com.rescuefarm.ui.fund;

import static org.junit.Assert.assertEquals;
import java.time.Instant;
import java.util.TimeZone;
import org.junit.Test;

public class RescueFundTimeTest {
    @Test public void eveningDonationUsesVietnamTimeRegardlessOfDeviceZone() {
        TimeZone original = TimeZone.getDefault();
        try {
            long timestamp = Instant.parse("2026-10-23T14:00:00Z").toEpochMilli();
            for (String zone : new String[]{"UTC", "America/Los_Angeles", "Asia/Novosibirsk", "Asia/Ho_Chi_Minh"}) {
                TimeZone.setDefault(TimeZone.getTimeZone(zone));
                assertEquals("23/10/2026 21:00 (giờ VN)", RescueFundTime.format(timestamp));
            }
        } finally {
            TimeZone.setDefault(original);
        }
    }

    @Test public void midnightBoundaryUsesVietnamCalendarDay() {
        assertEquals("23/10/2026 23:59 (giờ VN)",
                RescueFundTime.format(Instant.parse("2026-10-23T16:59:00Z").toEpochMilli()));
        assertEquals("24/10/2026 00:00 (giờ VN)",
                RescueFundTime.format(Instant.parse("2026-10-23T17:00:00Z").toEpochMilli()));
    }
}
