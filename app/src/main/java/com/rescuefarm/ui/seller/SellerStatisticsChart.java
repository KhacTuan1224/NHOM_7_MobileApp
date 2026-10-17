package com.rescuefarm.ui.seller;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;
import java.text.NumberFormat;
import java.util.Locale;

/** Charts render directly on the page background; a line is used only for a time series. */
final class SellerStatisticsChart extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final String[] labels;
    private final double[] values;
    private final boolean line;
    private final int[] colors = {Color.rgb(46, 107, 69), Color.rgb(89, 114, 211), Color.rgb(215, 142, 52), Color.rgb(144, 95, 185)};
    SellerStatisticsChart(Context context, String[] labels, double[] values, boolean line) {
        super(context); this.labels = labels; this.values = values; this.line = line;
        StringBuilder description = new StringBuilder();
        for (int i = 0; i < values.length; i++) description.append(labels[i]).append(": ").append(format(values[i])).append(". ");
        setContentDescription(description); setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    }
    private float dp(float value) { return value * getResources().getDisplayMetrics().density; }
    private String format(double value) { return NumberFormat.getNumberInstance(Locale.forLanguageTag("vi-VN")).format(value); }
    @Override protected void onMeasure(int width, int height) {
        setMeasuredDimension(MeasureSpec.getSize(width), Math.round(dp(215)));
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float left = dp(12), right = getWidth() - dp(12), top = dp(28), bottom = getHeight() - dp(42);
        double max = 1; for (double value : values) max = Math.max(max, value);
        paint.setColor(Color.rgb(212, 222, 215)); paint.setStrokeWidth(dp(1));
        for (int i = 0; i <= 3; i++) { float y = top + (bottom - top) * i / 3; canvas.drawLine(left, y, right, y, paint); }
        if (values.length == 0) return;
        float step = (right - left) / values.length;
        Path path = new Path();
        for (int i = 0; i < values.length; i++) {
            float x = left + step * (i + .5f), y = bottom - (float)(values[i] / max) * (bottom - top);
            paint.setColor(colors[line ? 1 : i % colors.length]); paint.setStyle(Paint.Style.FILL);
            if (line) {
                if (i == 0) path.moveTo(x, y); else path.lineTo(x, y);
                canvas.drawCircle(x, y, dp(3), paint);
            } else canvas.drawRoundRect(x - step * .24f, y, x + step * .24f, bottom, dp(4), dp(4), paint);
            paint.setColor(Color.rgb(54, 65, 57)); paint.setTextAlign(Paint.Align.CENTER); paint.setTextSize(dp(10));
            if (!line || i == 0 || i == values.length - 1 || i % 2 == 0) {
                String value = format(values[i]);
                if (values[i] >= 1000000) value = String.format(Locale.getDefault(), "%.1f tr", values[i] / 1000000);
                else if (values[i] >= 10000) value = String.format(Locale.getDefault(), "%.0f k", values[i] / 1000);
                canvas.drawText(value, x, Math.max(dp(15), y - dp(7)), paint);
                canvas.drawText(labels[i], x, bottom + dp(20), paint);
            }
        }
        if (line) {
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(dp(2)); paint.setColor(colors[1]); canvas.drawPath(path, paint); paint.setStyle(Paint.Style.FILL);
        }
    }
}
