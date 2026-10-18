package com.rescuefarm.ui.common;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.widget.LinearLayout;

import com.rescuefarm.R;

/** A centered scroll-content column with a readable maximum width. */
public class ResponsiveContentLayout extends LinearLayout {
    private int maxContentWidth;

    public ResponsiveContentLayout(Context context) { this(context, null); }

    public ResponsiveContentLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        TypedArray values = context.obtainStyledAttributes(attrs,
                R.styleable.ResponsiveContentLayout);
        maxContentWidth = values.getDimensionPixelSize(
                R.styleable.ResponsiveContentLayout_maxContentWidth,
                getResources().getDimensionPixelSize(R.dimen.content_max_width));
        values.recycle();
    }

    public void setMaxContentWidth(int pixels) {
        maxContentWidth = pixels;
        requestLayout();
    }

    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int mode = MeasureSpec.getMode(widthMeasureSpec);
        int width = MeasureSpec.getSize(widthMeasureSpec);
        if (maxContentWidth > 0 && (mode == MeasureSpec.UNSPECIFIED || width > maxContentWidth)) {
            widthMeasureSpec = MeasureSpec.makeMeasureSpec(maxContentWidth,
                    mode == MeasureSpec.EXACTLY ? MeasureSpec.EXACTLY : MeasureSpec.AT_MOST);
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }
}
