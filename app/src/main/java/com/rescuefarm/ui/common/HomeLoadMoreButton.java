package com.rescuefarm.ui.common;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.view.Gravity;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import androidx.core.content.ContextCompat;
import com.rescuefarm.R;

/** Compact home paging action. Only its arrow moves; the touch target stays still. */
public final class HomeLoadMoreButton extends LinearLayout {
    private final ImageView arrow;
    private ObjectAnimator animation;

    public HomeLoadMoreButton(Context context) {
        super(context);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER);
        setPadding(dp(12), dp(12), dp(12), dp(12));
        setMinimumWidth(dp(48));
        setMinimumHeight(dp(48));
        setClickable(true);
        setFocusable(true);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        int ink = ContextCompat.getColor(context, R.color.rescue_primary);
        arrow = new ImageView(context);
        arrow.setImageResource(R.drawable.ic_load_more_down);
        arrow.setImageTintList(ColorStateList.valueOf(ink));
        arrow.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        LayoutParams icon = new LayoutParams(dp(18), dp(18));
        addView(arrow, icon);
        setText(R.string.pagination_more);
    }

    public void setText(int resource) {
        // Retain the action/loading label for screen readers without visible text.
        setContentDescription(getContext().getText(resource));
    }

    @Override public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        setAlpha(enabled ? 1f : 0.55f);
        updateAnimation();
    }

    @Override public CharSequence getAccessibilityClassName() { return android.widget.Button.class.getName(); }
    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); updateAnimation(); }
    @Override protected void onDetachedFromWindow() { stopAnimation(); super.onDetachedFromWindow(); }
    @Override protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility); updateAnimation();
    }
    @Override protected void onVisibilityChanged(View changed, int visibility) {
        super.onVisibilityChanged(changed, visibility); updateAnimation();
    }

    private void updateAnimation() {
        if (arrow == null) return;
        boolean motionEnabled = android.os.Build.VERSION.SDK_INT < 26 || ValueAnimator.areAnimatorsEnabled();
        if (!isAttachedToWindow() || !isShown() || getWindowVisibility() != VISIBLE || !isEnabled() || !motionEnabled) {
            stopAnimation(); return;
        }
        if (animation != null) return;
        animation = ObjectAnimator.ofFloat(arrow, View.TRANSLATION_Y, -dp(2), dp(2));
        animation.setDuration(750);
        animation.setRepeatCount(ValueAnimator.INFINITE);
        animation.setRepeatMode(ValueAnimator.REVERSE);
        animation.setInterpolator(new AccelerateDecelerateInterpolator());
        animation.start();
    }

    private void stopAnimation() {
        if (animation != null) { animation.cancel(); animation = null; }
        if (arrow != null) arrow.setTranslationY(0);
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
