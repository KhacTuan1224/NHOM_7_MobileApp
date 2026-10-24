package com.rescuefarm.ui.fund;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Fund schedules and deadlines are shown in Vietnam time, independently of the device. */
final class RescueFundTime {
    private static final DateTimeFormatter FORMAT = DateTimeFormatter
            .ofPattern("dd/MM/yyyy HH:mm", Locale.forLanguageTag("vi-VN"))
            .withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

    private RescueFundTime() { }

    static String format(long epochMillis) {
        return FORMAT.format(Instant.ofEpochMilli(epochMillis)) + " (giờ VN)";
    }
}
