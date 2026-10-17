package com.rescuefarm.ui.common;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import com.rescuefarm.R;

/** Equal-width tiles; columns follow the measured viewport, including split-screen. */
public class ResponsiveGridLayout extends LinearLayout {
    private final int minItemWidth;
    private final int maxColumns;
    private final int gap;
    private int columns = 1;

    public ResponsiveGridLayout(Context context) { this(context, null); }

    public ResponsiveGridLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        TypedArray values = context.obtainStyledAttributes(attrs, R.styleable.ResponsiveGridLayout);
        minItemWidth = values.getDimensionPixelSize(R.styleable.ResponsiveGridLayout_minItemWidth,
                getResources().getDimensionPixelSize(R.dimen.product_tile_min_width));
        maxColumns = Math.max(1, values.getInt(R.styleable.ResponsiveGridLayout_maxColumns, 4));
        values.recycle();
        gap = getResources().getDimensionPixelSize(R.dimen.responsive_grid_gap);
        setClipChildren(false);
        setClipToPadding(false);
    }

    public int getColumnCount() { return columns; }

    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED) {
            width = minItemWidth + getPaddingLeft() + getPaddingRight();
        }
        int available = Math.max(0, width - getPaddingLeft() - getPaddingRight());
        // Give larger system fonts more space instead of squeezing the same number of columns.
        int minimum = Math.round(minItemWidth * Math.max(1f,
                getResources().getConfiguration().fontScale));
        columns = Math.max(1, Math.min(maxColumns, (available + gap) / (minimum + gap)));
        int tileWidth = Math.max(0, (available - gap * (columns - 1)) / columns);
        int height = getPaddingTop();
        int rowHeight = 0;
        int visible = 0;
        for (int index = 0; index < getChildCount(); index++) {
            View child = getChildAt(index);
            if (child.getVisibility() == GONE) continue;
            if (visible > 0 && visible % columns == 0) {
                height += rowHeight + gap;
                rowHeight = 0;
            }
            ViewGroup.LayoutParams params = child.getLayoutParams();
            child.measure(MeasureSpec.makeMeasureSpec(tileWidth, MeasureSpec.EXACTLY),
                    params.height >= 0 ? MeasureSpec.makeMeasureSpec(params.height, MeasureSpec.EXACTLY)
                            : MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
            rowHeight = Math.max(rowHeight, child.getMeasuredHeight());
            visible++;
        }
        height += rowHeight + getPaddingBottom();
        setMeasuredDimension(resolveSize(width, widthMeasureSpec),
                resolveSize(Math.max(height, getSuggestedMinimumHeight()), heightMeasureSpec));
    }

    @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int y = getPaddingTop();
        int visible = 0;
        int rowHeight = 0;
        boolean rtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
        for (int index = 0; index < getChildCount(); index++) {
            View child = getChildAt(index);
            if (child.getVisibility() == GONE) continue;
            int column = visible % columns;
            if (visible > 0 && column == 0) {
                y += rowHeight + gap;
                rowHeight = 0;
            }
            int x = rtl ? getWidth() - getPaddingRight() - child.getMeasuredWidth()
                    - column * (child.getMeasuredWidth() + gap)
                    : getPaddingLeft() + column * (child.getMeasuredWidth() + gap);
            child.layout(x, y, x + child.getMeasuredWidth(), y + child.getMeasuredHeight());
            rowHeight = Math.max(rowHeight, child.getMeasuredHeight());
            visible++;
        }
    }
}
