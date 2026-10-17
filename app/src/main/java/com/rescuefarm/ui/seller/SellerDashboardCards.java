package com.rescuefarm.ui.seller;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.core.content.ContextCompat;
import com.rescuefarm.R;
import java.util.List;

/** Presentation-only metric cards. Values come from the existing Seller dashboard snapshot. */
final class SellerDashboardCards {
    private SellerDashboardCards() { }

    static final class Metric {
        final String title;
        final String value;
        final String detail;

        Metric(String title, String value, String detail) {
            this.title = title;
            this.value = value;
            this.detail = detail;
        }
    }

    static void addSectionTitle(Context context, LinearLayout parent, String title) {
        TextView heading = new TextView(context);
        heading.setText(title);
        heading.setTextSize(18);
        heading.setTypeface(null, Typeface.BOLD);
        heading.setTextColor(ContextCompat.getColor(context, R.color.rescue_on_surface));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(context, 20);
        params.bottomMargin = dp(context, 8);
        parent.addView(heading, params);
    }

    static void addGrid(Context context, LinearLayout parent, List<Metric> metrics) {
        int gap = dp(context, 8);
        for (int index = 0; index < metrics.size(); index += 2) {
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            if (index > 0) rowParams.topMargin = gap;
            parent.addView(row, rowParams);
            addCard(context, row, metrics.get(index), gap);
            if (index + 1 < metrics.size()) addCard(context, row, metrics.get(index + 1), 0);
            else row.addView(new View(context), new LinearLayout.LayoutParams(0, 1, 1));
        }
    }

    static void addFootnote(Context context, LinearLayout parent, String text) {
        TextView note = new TextView(context);
        note.setText(text);
        note.setTextSize(12);
        note.setTextColor(ContextCompat.getColor(context, R.color.rescue_on_surface_variant));
        note.setPadding(dp(context, 4), dp(context, 8), dp(context, 4), 0);
        parent.addView(note, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
    }

    private static void addCard(Context context, LinearLayout row, Metric metric, int endMargin) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.TOP);
        int padding = dp(context, 14);
        card.setPadding(padding, padding, padding, padding);
        GradientDrawable background = new GradientDrawable();
        background.setColor(ContextCompat.getColor(context, R.color.rescue_surface));
        background.setCornerRadius(dp(context, 14));
        background.setStroke(dp(context, 1), ContextCompat.getColor(context, R.color.rescue_outline));
        card.setBackground(background);
        card.setElevation(0);
        card.setMinimumHeight(dp(context, 112));

        TextView title = new TextView(context);
        title.setText(metric.title);
        title.setTextSize(12);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(ContextCompat.getColor(context, R.color.rescue_on_surface_variant));
        card.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView value = new TextView(context);
        value.setText(metric.value);
        value.setTextSize(22);
        value.setTypeface(null, Typeface.BOLD);
        value.setTextColor(ContextCompat.getColor(context, R.color.rescue_primary));
        LinearLayout.LayoutParams valueParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        valueParams.topMargin = dp(context, 6);
        card.addView(value, valueParams);

        if (metric.detail != null && !metric.detail.trim().isEmpty()) {
            TextView detail = new TextView(context);
            detail.setText(metric.detail);
            detail.setTextSize(11);
            detail.setTextColor(ContextCompat.getColor(context, R.color.rescue_on_surface_variant));
            LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            detailParams.topMargin = dp(context, 4);
            card.addView(detail, detailParams);
        }

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.MATCH_PARENT, 1);
        cardParams.setMarginEnd(endMargin);
        row.addView(card, cardParams);
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
