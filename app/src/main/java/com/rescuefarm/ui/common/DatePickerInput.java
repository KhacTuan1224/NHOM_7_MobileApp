package com.rescuefarm.ui.common;

import android.app.DatePickerDialog;
import android.widget.EditText;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

/** Binds a date-only text field to the system calendar picker. */
public final class DatePickerInput {
    private DatePickerInput() { }

    public static void bind(EditText input) {
        input.setInputType(android.text.InputType.TYPE_NULL);
        input.setFocusable(false);
        input.setFocusableInTouchMode(false);
        input.setOnClickListener(view -> show(input));
    }

    private static void show(EditText input) {
        Calendar selected = Calendar.getInstance();
        String currentValue = input.getText() == null ? "" : input.getText().toString().trim();
        if (!currentValue.isEmpty()) {
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            format.setLenient(false);
            try {
                java.util.Date parsed = format.parse(currentValue);
                if (parsed != null) selected.setTime(parsed);
            } catch (ParseException ignored) { }
        }
        new DatePickerDialog(input.getContext(), (picker, year, month, day) ->
                input.setText(String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, day)),
                selected.get(Calendar.YEAR), selected.get(Calendar.MONTH),
                selected.get(Calendar.DAY_OF_MONTH)).show();
    }
}
