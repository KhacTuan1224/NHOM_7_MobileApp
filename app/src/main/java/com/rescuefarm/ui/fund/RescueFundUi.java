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

    static LinearLayout rankingTop(LinearLayout parent) {
        Context context = parent.getContext();
        LinearLayout panel = CustomerUi.column(context);
        panel.setPadding(dp(context, 12), dp(context, 20), dp(context, 12), dp(context, 22));
        GradientDrawable gradient = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{context.getColor(R.color.fund_rank_purple_light), context.getColor(R.color.fund_rank_purple_deep)});
        float radius = dp(context, 24);
        gradient.setCornerRadii(new float[]{radius,radius,radius,radius,radius,radius,radius,radius});
        panel.setBackground(gradient);
        parent.addView(panel, new LinearLayout.LayoutParams(-1, -2));
        TextView title = label(panel, "NHỮNG TẤM LÒNG SẺ CHIA", 14, R.color.rescue_on_primary, true);
        title.setGravity(Gravity.CENTER); title.setLetterSpacing(.06f);
        TextView subtitle = label(panel, "Top lượt ủng hộ đã xác nhận", 12, R.color.fund_rank_lavender, false);
        subtitle.setGravity(Gravity.CENTER);
        LinearLayout winners = row(panel);
        winners.setGravity(Gravity.BOTTOM);
        winners.setPadding(0, dp(context, 18), 0, 0);
        return winners;
    }

    private static String donorName(String name) {
        return name == null || name.trim().isEmpty() ? "Người ủng hộ ẩn danh" : name.trim();
    }

    private static android.widget.FrameLayout rankAvatar(Context context, String name, int size, boolean winner) {
        android.widget.FrameLayout avatar = new android.widget.FrameLayout(context);
        GradientDrawable circle = background(context, R.color.fund_rank_avatar, 64, false);
        circle.setStroke(dp(context, winner ? 3 : 2), context.getColor(winner ? R.color.fund_gold : R.color.fund_rank_lavender));
        avatar.setBackground(circle);
        int inset = dp(context, 8);
        avatar.setPadding(inset, inset, inset, inset);
        if (name == null || name.isEmpty() || name.equals("Người ủng hộ ẩn danh")) {
            ImageView person = new ImageView(context);
            person.setImageResource(R.drawable.ic_nav_profile);
            person.setImageTintList(ColorStateList.valueOf(context.getColor(R.color.rescue_on_primary)));
            person.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            avatar.addView(person, new android.widget.FrameLayout.LayoutParams(-1, -1));
        } else {
            TextView initial = new TextView(context);
            AppTypography.title(initial);
            initial.setText(name.substring(0, name.offsetByCodePoints(0, 1)).toUpperCase(java.util.Locale.forLanguageTag("vi-VN")));
            initial.setTextSize(size >= 72 ? 26 : 18);
            initial.setGravity(Gravity.CENTER);
            initial.setTextColor(context.getColor(R.color.rescue_on_primary));
            initial.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            avatar.addView(initial, new android.widget.FrameLayout.LayoutParams(-1, -1));
        }
        avatar.setLayoutParams(new LinearLayout.LayoutParams(dp(context, size), dp(context, size)));
        return avatar;
    }

    static void podium(LinearLayout parent, int rank, String name, String amount) {
        Context context = parent.getContext();
        boolean present = name != null;
        LinearLayout place = CustomerUi.column(context);
        place.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, rank == 1 ? 1.16f : 1f);
        params.setMargins(dp(context, 3), 0, dp(context, 3), 0);
        parent.addView(place, params);
        if (rank == 1) icon(place, R.drawable.ic_fund_crown, 30);
        place.addView(rankAvatar(context, present ? donorName(name) : null, rank == 1 ? 76 : 54, rank == 1));
        TextView position = label(place, "Hạng " + rank, 11, R.color.fund_rank_lavender, false);
        position.setGravity(Gravity.CENTER);
        TextView total = label(place, present ? amount : "—", rank == 1 ? 18 : 15, R.color.rescue_on_primary, true);
        total.setGravity(Gravity.CENTER); total.setMinLines(1);
        TextView donor = label(place, present ? donorName(name) : "Chờ sẻ chia", 12, R.color.fund_rank_lavender, false);
        donor.setGravity(Gravity.CENTER); donor.setLines(2);
        donor.setEllipsize(android.text.TextUtils.TruncateAt.END);
        donor.setContentDescription(present ? "Hạng " + rank + ", " + donorName(name) + ", " + amount : "Hạng " + rank + " chưa có lượt ủng hộ");
        if (!present) place.setAlpha(.55f);
    }

    static void rankRow(LinearLayout parent, int rank, String name, String amount) {
        Context context = parent.getContext();
        String displayName = donorName(name);
        LinearLayout row = row(parent);
        row.setPadding(dp(context, 12), dp(context, 14), dp(context, 12), dp(context, 14));
        TextView number = label(row, String.valueOf(rank), 14, R.color.rescue_on_surface_variant, false);
        number.setGravity(Gravity.CENTER);
        number.setLayoutParams(new LinearLayout.LayoutParams(dp(context, 26), -2));
        row.addView(rankAvatar(context, displayName, 34, false));
        TextView donor = label(row, displayName, 13, R.color.rescue_on_surface, true);
        donor.setPadding(dp(context, 10), 0, dp(context, 8), 0);
        donor.setMaxLines(2); donor.setEllipsize(android.text.TextUtils.TruncateAt.END);
        donor.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
        TextView total = label(row, amount, 13, R.color.fund_rank_purple_deep, true);
        total.setGravity(Gravity.END);
        total.setLayoutParams(new LinearLayout.LayoutParams(-2, -2));
        View divider = new View(context);
        divider.setBackgroundColor(context.getColor(R.color.rescue_outline));
        LinearLayout.LayoutParams line = new LinearLayout.LayoutParams(-1, dp(context, 1));
        line.setMargins(dp(context, 16), 0, dp(context, 16), 0);
        parent.addView(divider, line);
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
