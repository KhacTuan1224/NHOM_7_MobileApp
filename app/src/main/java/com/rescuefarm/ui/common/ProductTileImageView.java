package com.rescuefarm.ui.common;

import android.content.Context;
import androidx.appcompat.widget.AppCompatImageView;

/** Product photos follow their tile width rather than the physical display width. */
public class ProductTileImageView extends AppCompatImageView {
    public ProductTileImageView(Context context) { super(context); }

    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        setMeasuredDimension(getMeasuredWidth(), Math.round(getMeasuredWidth() * 0.75f));
    }
}
