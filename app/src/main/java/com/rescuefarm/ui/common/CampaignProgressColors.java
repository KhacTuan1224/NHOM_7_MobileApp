package com.rescuefarm.ui.common;
import androidx.annotation.ColorRes;
import com.rescuefarm.R;

public final class CampaignProgressColors {
    private CampaignProgressColors() { }
    @ColorRes public static int forPercent(double percent) {
        return Double.isFinite(percent) && percent >= 50 ? R.color.rescue_primary : R.color.rescue_critical;
    }
}
