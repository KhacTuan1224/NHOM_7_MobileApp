package com.rescuefarm.ui.rescue;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.rescuefarm.ui.common.AppTypography;

final class RescueMarketUi {
    static final int INK = 0xFF20252A, MUTED = 0xFF778087, RED = 0xFFDE442C,
            PURPLE = 0xFF7751BB, BORDER = 0xFFF0ECE8, CREAM = 0xFFFFF5EE;
    private RescueMarketUi() { }
    static int dp(Context c, float value) { return Math.round(c.getResources().getDisplayMetrics().density * value); }
    static LinearLayout col(Context c) { LinearLayout l = new LinearLayout(c); l.setOrientation(1); return l; }
    static LinearLayout row(Context c) { LinearLayout l = new LinearLayout(c); l.setGravity(Gravity.CENTER_VERTICAL); return l; }
    static TextView label(LinearLayout parent, String text, int size, int color, boolean bold) {
        TextView v = new TextView(parent.getContext()); v.setText(text); v.setTextSize(size); v.setTextColor(color);
        v.setIncludeFontPadding(false); v.setLineSpacing(dp(v.getContext(), 3), 1);
        v.setTypeface(AppTypography.bodyTypeface(v.getContext()), bold ? Typeface.BOLD : Typeface.NORMAL);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.topMargin = dp(v.getContext(), 7);
        parent.addView(v, p); return v;
    }
    static TextView heading(LinearLayout parent, String text, int size, int color) {
        TextView view = label(parent, text, size, color, true);
        AppTypography.title(view); view.setAccessibilityHeading(true); return view;
    }
    static GradientDrawable background(int color, float radius, Context c) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(c, radius)); return d;
    }
    static MaterialButton action(LinearLayout parent, String text, int color, Runnable click) {
        Context c = parent.getContext(); MaterialButton b = new MaterialButton(c); b.setText(text); b.setAllCaps(false);
        AppTypography.body(b);
        b.setTextSize(14); b.setCornerRadius(dp(c, 14)); b.setMinHeight(dp(c, 48));
        b.setBackgroundTintList(ColorStateList.valueOf(color)); b.setTextColor(0xFFFFFFFF);
        b.setOnClickListener(v -> click.run()); parent.addView(b, new LinearLayout.LayoutParams(-1, -2)); return b;
    }
    static LinearLayout panel(LinearLayout parent, int color, int padding) {
        Context c = parent.getContext(); MaterialCardView card = new MaterialCardView(c);
        card.setRadius(dp(c, 22)); card.setCardElevation(0); card.setStrokeWidth(dp(c, 1));
        card.setStrokeColor(BORDER); card.setCardBackgroundColor(color);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.topMargin = dp(c, 14); parent.addView(card, p);
        LinearLayout content = col(c); int pad = dp(c, padding); content.setPadding(pad, pad, pad, pad); card.addView(content); return content;
    }
    static String countdown(long end, long now) {
        if (end <= now) return "Đã kết thúc";
        long seconds = (end - now + 999) / 1000;
        return String.format(java.util.Locale.ROOT, "%02d : %02d : %02d", seconds / 3600, seconds / 60 % 60, seconds % 60);
    }
    static int discount(double original, double price) {
        return original > 0 ? Math.max(0, Math.min(100, (int) Math.round((1 - price / original) * 100))) : 0;
    }
}
