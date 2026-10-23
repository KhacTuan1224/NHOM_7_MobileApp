package com.rescuefarm.ui.rescue;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.MotionEvent;
import android.view.KeyEvent;
import android.view.View;
import android.graphics.Rect;
import android.os.Bundle;
import androidx.core.view.ViewCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.customview.widget.ExploreByTouchHelper;
import java.util.List;
import android.view.animation.LinearInterpolator;
import java.util.function.IntConsumer;

/** Native gift scene: float, approach, anticipation shake, lid burst and confetti. */
public final class GiftRevealView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final ValueAnimator idle;
    private ValueAnimator reveal;
    private float phase, progress;
    private int selected = -1;
    private final boolean[] available = new boolean[3];
    private final boolean[] preview = new boolean[3];
    private IntConsumer listener;
    private int pressed = -1;
    private float downX, downY;
    private final ExploreByTouchHelper boxAccessibility;
    private final int[][] colors = {{0xFF9F7ADD, 0xFF6840AB}, {0xFFF5B76C, 0xFFDF7E45}, {0xFF71C5AD, 0xFF348B79}};
    public GiftRevealView(Context c) {
        super(c); setClickable(true);
        setContentDescription("Ba hộp quà đang chuyển động. Chạm trực tiếp vào một hộp để mở.");
        boxAccessibility = new ExploreByTouchHelper(this) {
            @Override protected int getVirtualViewAt(float x, float y) {
                if (getWidth() == 0) return INVALID_ID;
                float scale = getWidth() / 360f;
                if (y / scale < 105 || y / scale > 285) return INVALID_ID;
                int index = Math.max(0, Math.min(2, (int) ((x / scale - 15) / 110)));
                return available[index] || preview[index] ? index : INVALID_ID;
            }
            @Override protected void getVisibleVirtualViews(List<Integer> ids) {
                for (int i = 0; i < 3; i++) if (available[i] || preview[i]) ids.add(i);
            }
            @Override protected void onPopulateNodeForVirtualView(int id, AccessibilityNodeInfoCompat node) {
                float scale = getWidth() / 360f;
                node.setBoundsInParent(new Rect(Math.round((15 + id * 110) * scale), Math.round(105 * scale),
                        Math.round((125 + id * 110) * scale), Math.round(285 * scale)));
                node.setContentDescription((available[id] ? "Mở hộp " : "Mở thử hộp ") + (id + 1));
                node.setClassName("android.widget.Button");
                node.setEnabled(selected < 0);
                node.setClickable(selected < 0);
                if (selected < 0) node.addAction(AccessibilityNodeInfoCompat.ACTION_CLICK);
            }
            @Override protected boolean onPerformActionForVirtualView(int id, int action, Bundle args) {
                return action == AccessibilityNodeInfoCompat.ACTION_CLICK && open(id);
            }
        };
        ViewCompat.setAccessibilityDelegate(this, boxAccessibility);
        setFocusable(true);
        idle = ValueAnimator.ofFloat(0, (float) (Math.PI * 2)); idle.setDuration(3400); idle.setRepeatCount(ValueAnimator.INFINITE);
        idle.setInterpolator(new LinearInterpolator()); idle.addUpdateListener(a -> { phase = (float) a.getAnimatedValue(); invalidate(); });
    }
    void setAvailable(int count) { for (int i = 0; i < 3; i++) available[i] = i < count; boxAccessibility.invalidateRoot(); invalidate(); }
    void setPreview(int index, boolean enabled) { preview[index] = enabled; boxAccessibility.invalidateRoot(); }
    void setSlotAvailable(int index, boolean enabled) { if (available[index] != enabled) { available[index] = enabled; boxAccessibility.invalidateRoot(); invalidate(); } }
    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int width = MeasureSpec.getSize(widthSpec);
        setMeasuredDimension(width, resolveSize(Math.round(width * 330f / 360f), heightSpec));
    }
    void setOnOpened(IntConsumer listener) { this.listener = listener; }
    boolean isOpening() { return reveal != null && reveal.isRunning(); }
    void reset() { if (reveal != null) reveal.cancel(); selected = -1; progress = 0; boxAccessibility.invalidateRoot(); invalidate(); }
    boolean open(int index) {
        if (index < 0 || index >= 3 || !available[index] && !preview[index] || selected != -1) return false;
        selected = index; progress = 0;
        boxAccessibility.invalidateRoot();
        if (!ValueAnimator.areAnimatorsEnabled()) { progress = 1; invalidate(); if (listener != null) listener.accept(index); return true; }
        reveal = ValueAnimator.ofFloat(0, 1); reveal.setDuration(2250); reveal.setInterpolator(new LinearInterpolator());
        reveal.addUpdateListener(a -> { progress = (float) a.getAnimatedValue(); invalidate(); });
        reveal.addListener(new AnimatorListenerAdapter() {
            boolean cancelled;
            @Override public void onAnimationCancel(Animator animation) { cancelled = true; }
            @Override public void onAnimationEnd(Animator animation) {
                if (!cancelled && listener != null && isAttachedToWindow()) listener.accept(index);
            }
        });
        reveal.start(); performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY); return true;
    }
    @Override public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            float scale = getWidth() / 360f, x = event.getX() / scale, y = event.getY() / scale;
            if (y >= 105 && y <= 285 && selected < 0) {
                int index = Math.max(0, Math.min(2, (int) ((x - 15) / 110)));
                if (available[index] || preview[index]) { pressed = index; downX = event.getX(); downY = event.getY(); return true; }
            }
            return false;
        }
        if (event.getAction() == MotionEvent.ACTION_MOVE && Math.hypot(event.getX() - downX, event.getY() - downY)
                > android.view.ViewConfiguration.get(getContext()).getScaledTouchSlop()) pressed = -1;
        if (event.getAction() == MotionEvent.ACTION_UP) {
            int index = pressed; pressed = -1;
            if (index >= 0 && open(index)) performClick(); return true;
        }
        if (event.getAction() == MotionEvent.ACTION_CANCEL) pressed = -1;
        return true;
    }
    @Override public boolean performClick() { super.performClick(); return true; }
    @Override public boolean dispatchHoverEvent(MotionEvent event) {
        return boxAccessibility.dispatchHoverEvent(event) || super.dispatchHoverEvent(event);
    }
    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        return boxAccessibility.dispatchKeyEvent(event) || super.dispatchKeyEvent(event);
    }
    @Override protected void onFocusChanged(boolean focused, int direction, Rect previous) {
        super.onFocusChanged(focused, direction, previous);
        boxAccessibility.onFocusChanged(focused, direction, previous);
    }
    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); updateAnimation(); }
    @Override protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility); updateAnimation();
        if (reveal != null && reveal.isStarted()) { if (visibility == VISIBLE) reveal.resume(); else reveal.pause(); }
    }
    private void updateAnimation() {
        if (idle == null) return;
        if (isAttachedToWindow() && getWindowVisibility() == VISIBLE && ValueAnimator.areAnimatorsEnabled()) {
            if (!idle.isStarted()) idle.start(); else idle.resume();
        } else if (idle.isStarted()) idle.pause();
    }
    @Override protected void onDetachedFromWindow() {
        idle.cancel(); if (reveal != null) reveal.cancel(); super.onDetachedFromWindow();
    }
    @Override protected void onDraw(Canvas c) {
        super.onDraw(c); float scale = getWidth() / 360f; c.save(); c.scale(scale, scale);
        paint.setShader(new RadialGradient(180, 178, 155, new int[]{0x26D5B5FF, 0x00FFFFFF}, null, Shader.TileMode.CLAMP));
        c.drawCircle(180, 178, 155, paint); paint.setShader(null);
        for (int i = 0; i < 14; i++) {
            float x = 22 + i * 24, y = 40 + (i * 71 % 190) + (float) Math.sin(phase + i) * 6;
            star(c, x, y, 2 + i % 3, 0x99C9B0E7);
        }
        if (selected >= 0 && progress > .45f) {
            float burst = Math.min(1, (progress - .45f) / .3f);
            c.save(); c.translate(180, 155); c.rotate(progress * 30);
            paint.setColor(0x22FFD36C);
            for (int i = 0; i < 12; i++) {
                c.rotate(30); path.reset(); path.moveTo(0, 0); path.lineTo(-12 * burst, -145 * burst); path.lineTo(12 * burst, -145 * burst); path.close(); c.drawPath(path, paint);
            }
            c.restore();
            paint.setShader(new RadialGradient(180, 155, 90, 0xB3FFE7A3, 0x00FFFFFF, Shader.TileMode.CLAMP)); c.drawCircle(180, 155, 90, paint); paint.setShader(null);
        }
        for (int i = 0; i < 3; i++) if (i != selected) drawGift(c, i);
        if (selected >= 0) drawGift(c, selected);
        if (progress > .58f) {
            float flight = (progress - .58f) / .42f;
            int[] confetti = {0xFFF4B654, 0xFF9D70DB, 0xFF6CBCA4, 0xFFF18D83};
            for (int i = 0; i < 56; i++) {
                double angle = i * 2.39996; float radius = (30 + i % 6 * 17) * flight;
                float x = 180 + (float) Math.cos(angle) * radius, y = 128 + (float) Math.sin(angle) * radius - 30 * flight + 70 * flight * flight;
                c.save(); c.translate(x, y); c.rotate(i * 31 + flight * 220);
                paint.setColor(confetti[i % 4]); paint.setAlpha((int) (255 * Math.min(1, (1.2f - flight) * 3)));
                c.drawRoundRect(-2, -5, 2, 5, 1, 1, paint); c.restore();
            }
        }
        paint.setAlpha(255); c.restore();
    }
    private void drawGift(Canvas c, int index) {
        float x = 70 + index * 110, y = 220 + (float) Math.sin(phase + index * 1.7) * 7;
        float travel = selected >= 0 ? Math.min(1, progress / .28f) : 0;
        float alpha = (selected < 0 || selected == index ? 1 : 1 - travel * .8f) * (available[index] || selected == index ? 1 : .65f);
        if (selected == index) { x += (180 - x) * travel; y += (213 - y) * travel; }
        else if (selected >= 0) x += (index < selected ? -18 : 18) * travel;
        c.saveLayerAlpha(0, 0, 360, 330, (int) (alpha * 255)); c.translate(x, y);
        paint.setColor(0x120E1232); c.drawOval(-40, 12, 40, 25, paint);
        float size = selected == index ? 1 + travel * .23f : 1;
        c.scale(size, size);
        float shake = selected == index && progress > .25f && progress < .57f ? (float) Math.sin(progress * 180) * 9 : (float) Math.sin(phase + index) * 2;
        c.rotate(shake, 0, -35);
        paint.setAlpha(255); paint.setShader(new LinearGradient(-35, -72, 36, 10, colors[index][0], colors[index][1], Shader.TileMode.CLAMP));
        c.drawRoundRect(-35, -64, 35, 9, 6, 6, paint); paint.setShader(null);
        paint.setColor(0x18FFFFFF); c.drawRoundRect(-30, -58, -25, -4, 2, 2, paint);
        paint.setColor(0xFFFFD98A); c.drawRect(-6, -64, 6, 9, paint);
        paint.setColor(0xFFFFF0C4); c.drawRect(-3, -64, 0, 9, paint);
        star(c, 0, -29, 14, 0xFFFFEAB4); star(c, 0, -29, 7, colors[index][1]);
        float lid = selected == index ? Math.max(0, Math.min(1, (progress - .5f) / .28f)) : 0;
        c.save(); c.translate(-lid * 16, -lid * 80); c.rotate(-lid * 24, -25, -65);
        paint.setColor(colors[index][1]); c.drawRoundRect(-40, -77, 40, -59, 5, 5, paint);
        paint.setColor(colors[index][0]); path.reset(); path.moveTo(-40, -77); path.lineTo(-25, -90); path.lineTo(27, -90); path.lineTo(40, -77); path.close(); c.drawPath(path, paint);
        paint.setColor(0xFFFFE5A4); c.drawRect(-7, -89, 7, -59, paint);
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(5); paint.setColor(0xFFFFD98A);
        path.reset(); path.moveTo(0, -86); path.cubicTo(-39, -86, -29, -117, -13, -102); path.lineTo(0, -86);
        path.cubicTo(39, -86, 29, -117, 13, -102); path.close(); c.drawPath(path, paint); paint.setStyle(Paint.Style.FILL);
        c.restore(); c.restore();
    }
    private void star(Canvas c, float x, float y, float radius, int color) {
        paint.setColor(color); paint.setAlpha(Color.alpha(color)); path.reset();
        path.moveTo(x, y - radius); path.lineTo(x + radius * .28f, y - radius * .28f);
        path.lineTo(x + radius, y); path.lineTo(x + radius * .28f, y + radius * .28f);
        path.lineTo(x, y + radius); path.lineTo(x - radius * .28f, y + radius * .28f);
        path.lineTo(x - radius, y); path.lineTo(x - radius * .28f, y - radius * .28f); path.close(); c.drawPath(path, paint);
    }
}
