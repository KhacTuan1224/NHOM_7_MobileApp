package com.rescuefarm.ui.admin;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.core.content.ContextCompat;
import com.rescuefarm.R;
import java.util.List;

/** Presentation-only card/grid helpers. Metric values are supplied by existing dashboard logic. */
final class AdminDashboardCards {
    private AdminDashboardCards() { }

    static final class Metric {
        final String title;
        final String value;
        final String detail;

        Metric(String title, String value, String detail) {
            this.title = title; this.value = value; this.detail = detail;
        }
    }

    static void addSectionTitle(Context context, LinearLayout parent, String title) {
        TextView heading = new TextView(context);
        heading.setText(title); heading.setTextSize(18); heading.setTypeface(null, Typeface.BOLD);
        heading.setTextColor(ContextCompat.getColor(context, R.color.rescue_on_surface));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(context, 20); params.bottomMargin = dp(context, 8);
        parent.addView(heading, params);
    }

    static void addGrid(Context context, LinearLayout parent, List<Metric> metrics) {
        int horizontalGap = dp(context, 8);
        int verticalGap = dp(context, 8);
        for (int index = 0; index < metrics.size(); index += 2) {
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            if (index > 0) rowParams.topMargin = verticalGap;
            parent.addView(row, rowParams);
            addMetricCard(context, row, metrics.get(index), horizontalGap);
            if (index + 1 < metrics.size()) addMetricCard(context, row, metrics.get(index + 1), 0);
            else {
                View spacer = new View(context);
                row.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1));
            }
        }
    }

    static void addFootnote(Context context, LinearLayout parent, String text) {
        TextView note = new TextView(context);
        note.setText(text); note.setTextSize(12);
        note.setTextColor(ContextCompat.getColor(context, R.color.rescue_on_surface_variant));
        note.setPadding(dp(context, 4), dp(context, 8), dp(context, 4), 0);
        parent.addView(note, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
    }

    static void addColumnChart(Context context, LinearLayout parent, String title, String detail,
            String[] labels, double[] values, String[] valueLabels, int colorOffset) {
        MetricColumnChart chart = new MetricColumnChart(context, labels, values, valueLabels, colorOffset);
        parent.addView(chart, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(context, 176)));
        addChartCaption(context, parent, title, detail);
    }

    static void addCompletionChart(Context context, LinearLayout parent, String title,
            String detail, double percentage) {
        CompletionChart chart = new CompletionChart(context, percentage);
        parent.addView(chart, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(context, 156)));
        addChartCaption(context, parent, title, detail);
    }

    private static void addChartCaption(Context context, LinearLayout parent, String title, String detail) {
        TextView caption = new TextView(context);
        caption.setText(title);
        caption.setTextSize(14);
        caption.setTypeface(null, Typeface.BOLD);
        caption.setTextColor(ContextCompat.getColor(context, R.color.rescue_on_surface));
        parent.addView(caption, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        if (detail != null && !detail.trim().isEmpty()) {
            TextView note = new TextView(context);
            note.setText(detail);
            note.setTextSize(11);
            note.setTextColor(ContextCompat.getColor(context, R.color.rescue_on_surface_variant));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            params.topMargin = dp(context, 2);
            parent.addView(note, params);
        }
        View spacer = new View(context);
        parent.addView(spacer, new LinearLayout.LayoutParams(1, dp(context, 18)));
    }

    private static final class MetricColumnChart extends View {
        private final String[] labels;
        private final double[] values;
        private final String[] valueLabels;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int[] palette;
        private final int text;
        private final int muted;
        private final int grid;

        MetricColumnChart(Context context, String[] labels, double[] values, String[] valueLabels,
                int colorOffset) {
            super(context);
            this.labels = labels;
            this.values = values;
            this.valueLabels = valueLabels;
            int[] colors = {R.color.rescue_chart_blue, R.color.rescue_chart_orange,
                    R.color.rescue_chart_teal, R.color.rescue_chart_purple,
                    R.color.rescue_chart_red, R.color.rescue_chart_yellow};
            palette = new int[colors.length];
            for (int i = 0; i < colors.length; i++)
                palette[i] = ContextCompat.getColor(context, colors[(i + colorOffset) % colors.length]);
            text = ContextCompat.getColor(context, R.color.rescue_on_surface);
            muted = ContextCompat.getColor(context, R.color.rescue_on_surface_variant);
            grid = ContextCompat.getColor(context, R.color.rescue_outline);
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float density = getResources().getDisplayMetrics().density;
            float left = 12 * density;
            float right = getWidth() - 12 * density;
            float top = 25 * density;
            float base = getHeight() - 35 * density;
            float plotHeight = Math.max(10, base - top);
            double max = 0;
            for (double value : values) max = Math.max(max, Math.max(0, value));
            if (max <= 0) max = 1;
            max *= 1.12;

            paint.setStrokeWidth(1 * density);
            paint.setColor(grid);
            for (int i = 0; i < 4; i++) {
                float y = top + plotHeight * i / 3f;
                canvas.drawLine(left, y, right, y, paint);
            }
            int count = Math.max(1, values.length);
            float slot = (right - left) / count;
            float barWidth = Math.min(38 * density, slot * .46f);
            for (int i = 0; i < values.length; i++) {
                float center = left + slot * (i + .5f);
                float barHeight = (float) (plotHeight * Math.max(0, values[i]) / max);
                paint.setColor(palette[i % palette.length]);
                canvas.drawRoundRect(center - barWidth / 2, base - barHeight,
                        center + barWidth / 2, base, 5 * density, 5 * density, paint);

                paint.setColor(text);
                paint.setTextAlign(Paint.Align.CENTER);
                paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
                paint.setTextSize(11 * density);
                canvas.drawText(valueLabels[i], center, Math.max(13 * density, base - barHeight - 6 * density), paint);

                paint.setColor(muted);
                paint.setTypeface(Typeface.DEFAULT);
                paint.setTextSize(9 * density);
                canvas.drawText(ellipsize(labels[i], paint, slot - 2 * density), center,
                        getHeight() - 10 * density, paint);
            }
        }

        private String ellipsize(String value, Paint textPaint, float maxWidth) {
            if (textPaint.measureText(value) <= maxWidth) return value;
            String result = value;
            while (result.length() > 1 && textPaint.measureText(result + "…") > maxWidth) {
                result = result.substring(0, result.length() - 1);
            }
            return result + "…";
        }
    }

    private static final class CompletionChart extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final double percentage;
        private final int progressColor;
        private final int track;
        private final int text;

        CompletionChart(Context context, double percentage) {
            super(context);
            this.percentage = Math.max(0, Math.min(100, percentage));
            progressColor = ContextCompat.getColor(context, R.color.rescue_chart_teal);
            track = ContextCompat.getColor(context, R.color.rescue_outline);
            text = ContextCompat.getColor(context, R.color.rescue_on_surface);
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float density = getResources().getDisplayMetrics().density;
            float radius = 47 * density;
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(12 * density);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setColor(track);
            canvas.drawArc(cx - radius, cy - radius, cx + radius, cy + radius,
                    -90, 360, false, paint);
            paint.setColor(progressColor);
            canvas.drawArc(cx - radius, cy - radius, cx + radius, cy + radius,
                    -90, (float) (360 * percentage / 100), false, paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(text);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            paint.setTextSize(20 * density);
            canvas.drawText(String.format(java.util.Locale.getDefault(), "%.1f%%", percentage),
                    cx, cy + 7 * density, paint);
        }
    }

    private static void addMetricCard(Context context, LinearLayout row, Metric metric, int endMargin) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL); card.setGravity(Gravity.TOP);
        int padding = dp(context, 10); card.setPadding(padding, padding, padding, padding);
        int accent = metricAccent(metric.title);
        GradientDrawable background = new GradientDrawable();
        background.setColor(ContextCompat.getColor(context, softAccent(metric.title)));
        background.setCornerRadius(dp(context, 14));
        background.setStroke(dp(context, 1), ContextCompat.getColor(context, accent));
        card.setBackground(background);
        card.setElevation(0);
        card.setStateListAnimator(null);

        TextView title = new TextView(context);
        title.setText(metric.title); title.setTextSize(11); title.setTypeface(null, Typeface.BOLD);
        title.setMaxLines(2);
        title.setTextColor(ContextCompat.getColor(context, R.color.rescue_on_surface_variant));
        card.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView value = new TextView(context);
        value.setText(metric.value); value.setTextSize(20); value.setTypeface(null, Typeface.BOLD);
        value.setSingleLine(true);
        value.setTextColor(ContextCompat.getColor(context, accent));
        LinearLayout.LayoutParams valueParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        valueParams.topMargin = dp(context, 6); card.addView(value, valueParams);

        if (metric.detail != null && !metric.detail.trim().isEmpty()) {
            TextView detail = new TextView(context);
            detail.setText(metric.detail); detail.setTextSize(10);
            detail.setTextColor(ContextCompat.getColor(context, R.color.rescue_on_surface_variant));
            LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            detailParams.topMargin = dp(context, 4); card.addView(detail, detailParams);
        }

        card.setMinimumHeight(dp(context, 104));
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.MATCH_PARENT, 1);
        cardParams.setMarginEnd(endMargin); row.addView(card, cardParams);
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private static int metricAccent(String title) {
        String key = title.toLowerCase(java.util.Locale.ROOT);
        if (key.contains("báo cáo") || key.contains("gấp")) return R.color.admin_reports;
        if (key.contains("đánh giá")) return R.color.admin_reviews;
        if (key.contains("người bán")) return R.color.admin_seller;
        if (key.contains("chiến dịch") || key.contains("giải cứu") || key.contains("doanh thu")) return R.color.admin_campaigns;
        if (key.contains("sản phẩm")) return R.color.admin_products;
        if (key.contains("đơn hàng")) return R.color.admin_orders;
        if (key.contains("điểm di động")) return R.color.admin_categories;
        return R.color.admin_users;
    }

    private static int softAccent(String title) {
        String key = title.toLowerCase(java.util.Locale.ROOT);
        if (key.contains("báo cáo") || key.contains("gấp")) return R.color.admin_reports_soft;
        if (key.contains("đánh giá")) return R.color.admin_reviews_soft;
        if (key.contains("người bán")) return R.color.admin_seller_soft;
        if (key.contains("chiến dịch") || key.contains("giải cứu") || key.contains("doanh thu")) return R.color.admin_campaigns_soft;
        if (key.contains("sản phẩm")) return R.color.admin_products_soft;
        if (key.contains("đơn hàng")) return R.color.admin_orders_soft;
        if (key.contains("điểm di động")) return R.color.admin_categories_soft;
        return R.color.admin_users_soft;
    }
}
