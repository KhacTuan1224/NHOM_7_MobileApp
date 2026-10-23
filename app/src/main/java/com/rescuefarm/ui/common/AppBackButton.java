package com.rescuefarm.ui.common;

import android.content.Context;
import android.content.res.ColorStateList;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.widget.ImageView;
import androidx.appcompat.widget.AppCompatImageButton;
import com.rescuefarm.R;

/** Shared icon-only navigation control; use a 48dp square in title rows. */
public final class AppBackButton extends AppCompatImageButton {
    public AppBackButton(Context context) { this(context, null); }
    public AppBackButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        setImageResource(R.drawable.ic_arrow_back);
        setImageTintList(ColorStateList.valueOf(context.getColor(R.color.rescue_on_surface)));
        setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        int inset = Math.round(12 * getResources().getDisplayMetrics().density);
        setPadding(inset, inset, inset, inset);
        setMinimumWidth(inset * 4);
        setMinimumHeight(inset * 4);
        TypedValue ripple = new TypedValue();
        context.getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, ripple, true);
        setBackgroundResource(ripple.resourceId);
        if (getContentDescription() == null) setContentDescription(context.getString(R.string.navigation_back));
        setFocusable(true);
    }
}
