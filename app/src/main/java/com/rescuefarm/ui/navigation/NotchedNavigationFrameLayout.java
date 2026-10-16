package com.rescuefarm.ui.navigation;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.rescuefarm.R;

/** Rounded navigation surface with a curved recess for the raised center action. */
public class NotchedNavigationFrameLayout extends FrameLayout {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final float density;

    public NotchedNavigationFrameLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        paint.setColor(ContextCompat.getColor(context, R.color.rescue_nav_surface));
        paint.setAlpha(190);
        setWillNotDraw(false);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        float width = getWidth();
        float height = getHeight();
        float top = 34f * density;
        float corner = 28f * density;
        float notchHalfWidth = 70f * density;
        float notchDepth = 79f * density;
        float center = width / 2f;

        path.reset();
        path.moveTo(corner, top);
        path.lineTo(center - notchHalfWidth, top);
        path.cubicTo(center - 35f * density, top,
                center - 45f * density, notchDepth,
                center, notchDepth);
        path.cubicTo(center + 45f * density, notchDepth,
                center + 35f * density, top,
                center + notchHalfWidth, top);
        path.lineTo(width - corner, top);
        path.quadTo(width, top, width, top + corner);
        path.lineTo(width, height - corner);
        path.quadTo(width, height, width - corner, height);
        path.lineTo(corner, height);
        path.quadTo(0f, height, 0f, height - corner);
        path.lineTo(0f, top + corner);
        path.quadTo(0f, top, corner, top);
        path.close();
        canvas.drawPath(path, paint);
    }
}
