package com.rescuefarm.ui.common;

import android.content.Context;
import android.graphics.Typeface;
import android.widget.TextView;
import com.rescuefarm.R;

/** Shared font roles for Java-created views and Canvas charts. Font files are bundled offline. */
public final class AppTypography {
    private static Typeface title, body;
    private AppTypography() { }
    public static Typeface titleTypeface(Context context) {
        if (title == null) title = context.getResources().getFont(R.font.ibm_plex_sans);
        return title;
    }
    public static Typeface bodyTypeface(Context context) {
        if (body == null) body = context.getResources().getFont(R.font.barlow);
        return body;
    }
    public static TextView title(TextView view) {
        view.setTypeface(titleTypeface(view.getContext()), view.getTypeface() == null ? Typeface.NORMAL : view.getTypeface().getStyle());
        return view;
    }
    public static TextView body(TextView view) {
        view.setTypeface(bodyTypeface(view.getContext()), view.getTypeface() == null ? Typeface.NORMAL : view.getTypeface().getStyle());
        return view;
    }
}
