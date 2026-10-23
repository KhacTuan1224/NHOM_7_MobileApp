package com.rescuefarm.ui.common;

import android.content.Context;
import android.content.res.ColorStateList;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.annotation.ColorInt;
import androidx.annotation.ColorRes;
import androidx.core.content.ContextCompat;
import com.rescuefarm.R;

public final class RescueProgressUtils {
    public static final double THRESHOLD_LOW = 30.0;     // Dưới 30%: Đỏ (Tiến độ thấp / Cần cứu gấp)
    public static final double THRESHOLD_MEDIUM = 70.0;  // 30% - 69%: Vàng (Đang tiến triển), Từ 70% trở lên: Xanh (Khả quan / Đạt mục tiêu)

    private RescueProgressUtils() { }

    @ColorRes
    public static int getProgressColorRes(double progressPercent) {
        if (progressPercent < THRESHOLD_LOW) {
            return R.color.rescue_progress_low;
        } else if (progressPercent < THRESHOLD_MEDIUM) {
            return R.color.rescue_progress_medium;
        } else {
            return R.color.rescue_progress_high;
        }
    }

    @ColorInt
    public static int getProgressColor(Context context, double progressPercent) {
        return ContextCompat.getColor(context, getProgressColorRes(progressPercent));
    }

    public static ColorStateList getProgressColorStateList(Context context, double progressPercent) {
        return ColorStateList.valueOf(getProgressColor(context, progressPercent));
    }

    public static void applyProgress(ProgressBar progressBar, double progressPercent) {
        if (progressBar == null) return;
        int progress = (int) Math.max(0, Math.min(100, Math.round(progressPercent)));
        progressBar.setMax(100);
        progressBar.setProgress(progress);
        progressBar.setProgressTintList(getProgressColorStateList(progressBar.getContext(), progressPercent));
    }

    public static void applyProgressWithText(ProgressBar progressBar, TextView percentOrStatusText, double progressPercent) {
        applyProgress(progressBar, progressPercent);
        if (percentOrStatusText != null) {
            percentOrStatusText.setTextColor(getProgressColor(percentOrStatusText.getContext(), progressPercent));
        }
    }

    public static String getProgressStageLabel(Context context, double progressPercent) {
        if (progressPercent >= 100.0) {
            return context.getString(R.string.rescue_progress_stage_completed);
        } else if (progressPercent >= THRESHOLD_MEDIUM) {
            return context.getString(R.string.rescue_progress_stage_high);
        } else if (progressPercent >= THRESHOLD_LOW) {
            return context.getString(R.string.rescue_progress_stage_medium);
        } else {
            return context.getString(R.string.rescue_progress_stage_low);
        }
    }
}
