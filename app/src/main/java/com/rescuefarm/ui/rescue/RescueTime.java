package com.rescuefarm.ui.rescue;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** Rescue pickup times use Vietnam time for both display and user selection. */
final class RescueTime {
    private RescueTime() { }

    static Calendar calendar(long epochMillis) {
        Calendar result = Calendar.getInstance(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
        result.setTimeInMillis(epochMillis);
        return result;
    }

    static String format(long epochMillis) {
        SimpleDateFormat format = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.forLanguageTag("vi-VN"));
        format.setTimeZone(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
        return format.format(new Date(epochMillis));
    }

    // DatePicker interprets minDate in the device zone. Supply the same calendar
    // date as Vietnam today, including when the two zones are on different days.
    static long pickerMinDate(long now) {
        Calendar vietnam = calendar(now);
        Calendar picker = Calendar.getInstance();
        picker.clear();
        picker.set(vietnam.get(Calendar.YEAR), vietnam.get(Calendar.MONTH), vietnam.get(Calendar.DAY_OF_MONTH));
        return picker.getTimeInMillis();
    }
}
