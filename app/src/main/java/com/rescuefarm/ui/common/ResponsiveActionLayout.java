package com.rescuefarm.ui.common;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.IdentityHashMap;
import java.util.Map;

/** Stacks related actions when their labels no longer fit in a horizontal row. */
public class ResponsiveActionLayout extends LinearLayout {
    private final Map<View, LayoutParams> horizontalParams = new IdentityHashMap<>();
    private boolean stacked;

    public ResponsiveActionLayout(Context context) { this(context, null); }

    public ResponsiveActionLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        setOrientation(HORIZONTAL);
    }

    @Override public void onViewRemoved(View child) {
        super.onViewRemoved(child);
        horizontalParams.remove(child);
    }

    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int available = MeasureSpec.getSize(widthMeasureSpec) - getPaddingLeft() - getPaddingRight();
        int required = 0;
        float density = getResources().getDisplayMetrics().density;
        for (int index = 0; index < getChildCount(); index++) {
            View child = getChildAt(index);
            LayoutParams original = horizontalParams.get(child);
            if (original == null) {
                original = new LayoutParams((LayoutParams) child.getLayoutParams());
                horizontalParams.put(child, original);
            }
            if (child.getVisibility() == GONE) continue;
            int preferred;
            if (child instanceof TextView) {
                TextView text = (TextView) child;
                CharSequence label = text.getText();
                if (text instanceof EditText && ((EditText) text).getHint() != null) {
                    label = ((EditText) text).getHint();
                }
                if (text.getTransformationMethod() != null) {
                    label = text.getTransformationMethod().getTransformation(label, text);
                }
                preferred = (int) Math.ceil(android.text.Layout.getDesiredWidth(label, text.getPaint()))
                        + text.getCompoundPaddingLeft() + text.getCompoundPaddingRight()
                        + Math.round(16 * density);
                preferred = Math.max(preferred, text.getMinWidth());
            } else {
                child.measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
                        MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
                preferred = child.getMeasuredWidth();
            }
            required += Math.max(Math.max(original.width, preferred), Math.round(48 * density))
                    + original.leftMargin + original.rightMargin;
        }
        boolean shouldStack = MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.UNSPECIFIED
                && required > available;
        if (shouldStack != stacked) {
            stacked = shouldStack;
            setOrientation(stacked ? VERTICAL : HORIZONTAL);
        }
        for (int index = 0; index < getChildCount(); index++) {
            View child = getChildAt(index);
            LayoutParams params = new LayoutParams(horizontalParams.get(child));
            if (stacked) {
                params.width = LayoutParams.MATCH_PARENT;
                params.height = LayoutParams.WRAP_CONTENT;
                params.weight = 0;
                params.setMarginStart(0);
                params.setMarginEnd(0);
                if (index > 0) params.topMargin += Math.round(8 * density);
            }
            // Assign without setLayoutParams/requestLayout during measurement.
            LayoutParams current = (LayoutParams) child.getLayoutParams();
            current.width = params.width;
            current.height = params.height;
            current.weight = params.weight;
            current.leftMargin = params.leftMargin;
            current.rightMargin = params.rightMargin;
            current.topMargin = params.topMargin;
            current.bottomMargin = params.bottomMargin;
            current.setMarginStart(params.getMarginStart());
            current.setMarginEnd(params.getMarginEnd());
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }
}
