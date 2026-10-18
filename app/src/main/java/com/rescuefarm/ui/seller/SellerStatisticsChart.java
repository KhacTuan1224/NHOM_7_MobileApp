package com.rescuefarm.ui.seller;

import android.content.Context;
import android.graphics.*;
import android.text.*;
import android.view.View;
import com.rescuefarm.R;
import java.text.NumberFormat;
import java.util.Locale;

/** Charts use sp text, a responsive plot area and separate quantity units. */
final class SellerStatisticsChart extends View {
    enum Kind { LINE, COLUMN, HORIZONTAL, RING, PROGRESS }
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final String[] labels;
    private final double[] values;
    private final Kind kind;
    private final int[] colors = {Color.rgb(46,107,69), Color.rgb(89,114,211), Color.rgb(193,119,30), Color.rgb(144,95,185)};
    SellerStatisticsChart(Context context, String[] labels, double[] values, boolean line) {
        this(context, labels, values, line ? Kind.LINE : Kind.COLUMN);
    }
    SellerStatisticsChart(Context context, String[] labels, double[] values, Kind kind) {
        super(context);
        if (labels.length != values.length) throw new IllegalArgumentException("Chart labels and values must match");
        this.labels = labels.clone(); this.values = values.clone(); this.kind = kind;
        StringBuilder description = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (!Double.isFinite(this.values[i]) || this.values[i] < 0) this.values[i] = 0;
            description.append(labels[i]).append(": ").append(format(this.values[i])).append(". ");
        }
        setContentDescription(description); setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    }
    private float dp(float value) { return value * getResources().getDisplayMetrics().density; }
    private float sp(float value) { return value * getResources().getDisplayMetrics().scaledDensity; }
    private String format(double value) {
        NumberFormat number = NumberFormat.getNumberInstance(Locale.forLanguageTag("vi-VN"));
        number.setMaximumFractionDigits(1); return number.format(value);
    }
    private String compact(double value) {
        return value >= 1000000 ? format(value / 1000000) + " tr" : value >= 10000 ? format(value / 1000) + " k" : format(value);
    }
    private void ink(float size, Paint.Align align) {
        paint.setColor(getContext().getColor(R.color.rescue_on_surface));
        paint.setTextSize(sp(size)); paint.setTextAlign(align); paint.setStyle(Paint.Style.FILL);
        paint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
    }
    @Override protected void onMeasure(int width, int height) {
        float desired = kind == Kind.HORIZONTAL ? Math.max(1, values.length) * Math.max(dp(86), sp(70))
                : kind == Kind.RING || kind == Kind.PROGRESS ? dp(220) + labels.length * Math.max(dp(32), sp(26))
                : Math.max(dp(260), sp(215));
        setMeasuredDimension(MeasureSpec.getSize(width), resolveSize(Math.round(desired), height));
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (values.length == 0) { ink(16, Paint.Align.LEFT); canvas.drawText("Chưa có dữ liệu", dp(8), sp(24), paint); return; }
        if (kind == Kind.HORIZONTAL) { horizontal(canvas); return; }
        if (kind == Kind.RING || kind == Kind.PROGRESS) { ring(canvas); return; }
        plot(canvas);
    }
    private void horizontal(Canvas canvas) {
        float rowHeight = getHeight() / (float) values.length, left = dp(4), right = getWidth() - dp(4);
        double max = 1; for (double value : values) max = Math.max(max, value);
        for (int i = 0; i < values.length; i++) {
            float top = i * rowHeight;
            TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            textPaint.setColor(getContext().getColor(R.color.rescue_on_surface)); textPaint.setTextSize(sp(16));
            StaticLayout label = StaticLayout.Builder.obtain(labels[i], 0, labels[i].length(), textPaint, Math.max(1, (int) (right-left)))
                    .setMaxLines(2).setEllipsize(TextUtils.TruncateAt.END).setIncludePad(false).build();
            canvas.save(); canvas.translate(left, top + dp(4)); label.draw(canvas); canvas.restore();
            float barTop = top + label.getHeight() + dp(14), barRight = Math.max(left, right - sp(72));
            paint.setColor(getContext().getColor(R.color.rescue_outline));
            canvas.drawRoundRect(left, barTop, barRight, barTop + dp(12), dp(6), dp(6), paint);
            paint.setColor(colors[i % colors.length]);
            canvas.drawRoundRect(left, barTop, left + (float)(values[i]/max)*(barRight-left), barTop+dp(12), dp(6), dp(6), paint);
            ink(14, Paint.Align.RIGHT); canvas.drawText(format(values[i]), right, barTop+dp(12), paint);
        }
    }
    private void ring(Canvas canvas) {
        double total = 0; for (double value : values) total += value;
        float size = Math.min(getWidth()-dp(36), dp(164)), left = (getWidth()-size)/2, top = dp(14);
        RectF oval = new RectF(left, top, left+size, top+size);
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(dp(20)); paint.setColor(getContext().getColor(R.color.rescue_outline));
        canvas.drawOval(oval, paint);
        float angle = -90;
        if (total > 0) for (int i=0; i<values.length; i++) {
            float sweep = (float)(values[i]/total*360); paint.setColor(colors[i%colors.length]);
            canvas.drawArc(oval, angle, sweep, false, paint); angle += sweep;
        }
        ink(20, Paint.Align.CENTER);
        String center = total == 0 ? "Chưa có" : kind == Kind.PROGRESS ? format(values[0]) + "%" : format(total);
        canvas.drawText(center, getWidth()/2f, top+size/2-(paint.ascent()+paint.descent())/2, paint);
        float y = top+size+dp(42);
        for (int i=0; i<labels.length; i++) {
            paint.setColor(colors[i%colors.length]); canvas.drawCircle(dp(10), y-sp(5), dp(5), paint);
            ink(14, Paint.Align.LEFT);
            String legend = labels[i] + ": " + format(values[i]) + (kind == Kind.PROGRESS ? "%" : "");
            canvas.drawText(TextUtils.ellipsize(legend, new TextPaint(paint), Math.max(1,getWidth()-dp(30)), TextUtils.TruncateAt.END).toString(), dp(24), y, paint);
            y += Math.max(dp(32), sp(26));
        }
    }
    private void plot(Canvas canvas) {
        double max = 1; for (double value : values) max = Math.max(max, value);
        float left = Math.min(sp(64), getWidth()*.26f), right = getWidth()-dp(16), top = sp(24), bottom = getHeight()-sp(40);
        for (int i=0; i<=3; i++) {
            float y = bottom-(bottom-top)*i/3;
            paint.setColor(getContext().getColor(R.color.rescue_outline)); paint.setStrokeWidth(dp(1)); canvas.drawLine(left,y,right,y,paint);
            ink(14, Paint.Align.RIGHT); canvas.drawText(compact(max*i/3), left-dp(6), y+sp(4), paint);
        }
        float step=(right-left)/values.length; Path path=new Path();
        int ticks=Math.max(2,(int)((right-left)/sp(65)));
        int stride=Math.max(1,(int)Math.ceil(values.length/(double)ticks));
        for (int i=0; i<values.length; i++) {
            float x=left+step*(i+.5f), y=bottom-(float)(values[i]/max)*(bottom-top);
            paint.setColor(colors[kind==Kind.LINE?1:i%colors.length]);
            if(kind==Kind.LINE) { if(i==0)path.moveTo(x,y);else path.lineTo(x,y); canvas.drawCircle(x,y,dp(3),paint); }
            else canvas.drawRoundRect(x-step*.25f,y,x+step*.25f,bottom,dp(4),dp(4),paint);
            if(kind!=Kind.LINE || i==values.length-1) { ink(14,Paint.Align.CENTER); canvas.drawText(compact(values[i]),x,Math.max(sp(16),y-dp(8)),paint); }
            if(i%stride==0 || i==values.length-1) {
                ink(14,Paint.Align.CENTER);
                canvas.drawText(TextUtils.ellipsize(labels[i],new TextPaint(paint),Math.max(sp(36),step*stride-dp(4)),TextUtils.TruncateAt.END).toString(),x,bottom+sp(24),paint);
            }
        }
        if(kind==Kind.LINE) { paint.setColor(colors[1]);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(2));canvas.drawPath(path,paint);paint.setStyle(Paint.Style.FILL); }
    }
}
