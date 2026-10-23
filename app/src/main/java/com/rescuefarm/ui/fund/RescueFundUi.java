package com.rescuefarm.ui.fund;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.google.android.material.button.MaterialButton;
import com.rescuefarm.R;
import com.rescuefarm.ui.common.AppTypography;
import com.rescuefarm.ui.common.CustomerUi;

/** Visual building blocks scoped to the rescue fund, including its role-specific forms. */
final class RescueFundUi {
    private RescueFundUi() { }

    static int dp(Context context, int value) { return CustomerUi.dp(context, value); }

    static GradientDrawable background(Context context, int color, int radius, boolean border) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(context.getColor(color));
        shape.setCornerRadius(dp(context, radius));
        if (border) shape.setStroke(dp(context, 1), context.getColor(R.color.rescue_outline));
        return shape;
    }

    static LinearLayout card(LinearLayout parent) {
        Context context = parent.getContext();
        LinearLayout card = CustomerUi.column(context);
        card.setPadding(dp(context, 20), dp(context, 16), dp(context, 20), dp(context, 16));
        card.setBackground(background(context, R.color.rescue_surface, 24, true));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(context, 16);
        parent.addView(card, params);
        return card;
    }

    static TextView label(LinearLayout parent, String value, int size, int color, boolean bold) {
        TextView text = CustomerUi.text(parent, value);
        text.setTextSize(size);
        text.setTextColor(parent.getContext().getColor(color));
        text.setPadding(0, dp(parent.getContext(), 4), 0, dp(parent.getContext(), 4));
        text.setLineSpacing(dp(parent.getContext(), 2), 1f);
        if (bold) {
            AppTypography.title(text);
            text.setTypeface(text.getTypeface(), Typeface.BOLD);
        }
        return text;
    }

    static void section(LinearLayout parent, String title) {
        TextView text = label(parent, title, 20, R.color.rescue_on_surface, true);
        text.setPadding(0, dp(parent.getContext(), 8), 0, dp(parent.getContext(), 12));
        androidx.core.view.ViewCompat.setAccessibilityHeading(text, true);
    }

    static void podium(LinearLayout parent, int rank, String name, String amount) {
        Context context = parent.getContext();
        LinearLayout place = CustomerUi.column(context);
        place.setGravity(Gravity.CENTER_HORIZONTAL);
        place.setPadding(dp(context, 4), dp(context, 12), dp(context, 4), 0);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1);
        params.setMargins(dp(context, 3), dp(context, 12), dp(context, 3), dp(context, 16));
        parent.addView(place, params);
        TextView number = label(place, String.valueOf(rank), rank == 1 ? 30 : 24, R.color.rescue_primary, true);
        number.setGravity(Gravity.CENTER);
        int size = dp(context, rank == 1 ? 64 : 48);
        number.setLayoutParams(new LinearLayout.LayoutParams(size, size));
        number.setBackground(background(context, R.color.rescue_primary_container, 32, false));
        TextView donor = label(place, name, 13, R.color.rescue_on_surface, true);
        donor.setGravity(Gravity.CENTER); donor.setMinLines(2); donor.setMaxLines(3);
        donor.setEllipsize(android.text.TextUtils.TruncateAt.END);
        TextView total = label(place, amount, 13, R.color.rescue_primary, true);
        total.setGravity(Gravity.CENTER);
        TextView base = label(place, "Hạng " + rank, 12, R.color.rescue_primary, true);
        base.setGravity(Gravity.CENTER);
        base.setBackground(background(context, R.color.rescue_primary_container, 10, false));
        base.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(context, rank == 1 ? 56 : rank == 2 ? 40 : 32)));
    }

    static void rankRow(LinearLayout parent, int rank, String name, String amount) {
        Context context = parent.getContext();
        View divider = new View(context);
        divider.setBackgroundColor(context.getColor(R.color.rescue_outline));
        parent.addView(divider, new LinearLayout.LayoutParams(-1, dp(context, 1)));
        LinearLayout row = row(parent);
        row.setPadding(0, dp(context, 10), 0, dp(context, 10));
        TextView number = label(row, String.valueOf(rank), 16, R.color.rescue_on_surface_variant, true);
        number.setGravity(Gravity.CENTER);
        number.setLayoutParams(new LinearLayout.LayoutParams(dp(context, 36), -2));
        LinearLayout copy = CustomerUi.column(context);
        row.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        label(copy, name, 15, R.color.rescue_on_surface, true);
        label(copy, amount, 14, R.color.rescue_primary, true);
    }

    static LinearLayout row(LinearLayout parent) {
        LinearLayout row = new LinearLayout(parent.getContext());
        row.setGravity(Gravity.CENTER_VERTICAL);
        parent.addView(row, new LinearLayout.LayoutParams(-1, -2));
        return row;
    }

    static ImageView icon(LinearLayout parent, int drawable, int size) {
        ImageView icon = new ImageView(parent.getContext());
        icon.setImageResource(drawable);
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        parent.addView(icon, new LinearLayout.LayoutParams(dp(parent.getContext(), size), dp(parent.getContext(), size)));
        return icon;
    }

    static TextView badge(LinearLayout parent, String value, boolean active) {
        Context context = parent.getContext();
        TextView badge = label(parent, value, 12,
                active ? R.color.rescue_primary : R.color.rescue_on_surface_variant, true);
        badge.setPadding(dp(context, 12), dp(context, 6), dp(context, 12), dp(context, 6));
        badge.setBackground(background(context,
                active ? R.color.rescue_primary_container : R.color.rescue_background, 20, false));
        badge.setLayoutParams(new LinearLayout.LayoutParams(-2, -2));
        return badge;
    }

    static MaterialButton action(LinearLayout parent, String value, boolean primary, View.OnClickListener click) {
        Context context = parent.getContext();
        MaterialButton button = new MaterialButton(context, null,
                primary ? com.google.android.material.R.attr.materialButtonStyle
                        : com.google.android.material.R.attr.materialButtonOutlinedStyle);
        button.setText(value);
        button.setAllCaps(false);
        AppTypography.title(button);
        button.setTextSize(15);
        button.setCornerRadius(dp(context, 16));
        button.setMinHeight(dp(context, 52));
        button.setInsetTop(0);
        button.setInsetBottom(0);
        button.setPadding(dp(context, 16), dp(context, 12), dp(context, 16), dp(context, 12));
        button.setOnClickListener(click);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(context, 8);
        parent.addView(button, params);
        return button;
    }

    static void detail(LinearLayout parent, int icon, String title, String description) {
        Context context = parent.getContext();
        LinearLayout row = row(parent);
        row.setGravity(Gravity.TOP);
        row.setPadding(0, dp(context, 6), 0, dp(context, 6));
        ImageView image = icon(row, icon, 24);
        image.setImageTintList(ColorStateList.valueOf(context.getColor(R.color.rescue_primary)));
        LinearLayout copy = CustomerUi.column(context);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1);
        params.setMarginStart(dp(context, 12));
        row.addView(copy, params);
        label(copy, title, 16, R.color.rescue_on_surface, true);
        label(copy, description, 14, R.color.rescue_on_surface_variant, false);
    }

    static void step(LinearLayout parent, String number, String title, String description) {
        Context context = parent.getContext();
        LinearLayout row = row(parent);
        row.setGravity(Gravity.TOP);
        row.setPadding(0, dp(context, 6), 0, dp(context, 6));
        TextView marker = label(row, number, 14, R.color.rescue_primary, true);
        marker.setGravity(Gravity.CENTER);
        marker.setBackground(background(context, R.color.rescue_primary_container, 12, false));
        marker.setLayoutParams(new LinearLayout.LayoutParams(dp(context, 32), dp(context, 32)));
        LinearLayout copy = CustomerUi.column(context);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1);
        params.setMarginStart(dp(context, 12));
        row.addView(copy, params);
        label(copy, title, 15, R.color.rescue_on_surface, true);
        label(copy, description, 14, R.color.rescue_on_surface_variant, false);
    }
}
