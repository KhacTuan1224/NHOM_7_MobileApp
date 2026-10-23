package com.rescuefarm.ui.common;

import static org.junit.Assert.assertEquals;
import com.rescuefarm.R;
import org.junit.Test;

public class RescueProgressUtilsTest {

    @Test
    public void testProgressColorThresholds() {
        // Mốc 1: Dưới 30% -> Màu Đỏ (rescue_progress_low)
        assertEquals(R.color.rescue_progress_low, RescueProgressUtils.getProgressColorRes(0.0));
        assertEquals(R.color.rescue_progress_low, RescueProgressUtils.getProgressColorRes(10.0));
        assertEquals(R.color.rescue_progress_low, RescueProgressUtils.getProgressColorRes(29.9));

        // Mốc 2: Từ 30% đến dưới 70% -> Màu Vàng/Cam (rescue_progress_medium)
        assertEquals(R.color.rescue_progress_medium, RescueProgressUtils.getProgressColorRes(30.0));
        assertEquals(R.color.rescue_progress_medium, RescueProgressUtils.getProgressColorRes(50.0));
        assertEquals(R.color.rescue_progress_medium, RescueProgressUtils.getProgressColorRes(69.9));

        // Mốc 3: Từ 70% trở lên -> Màu Xanh (rescue_progress_high)
        assertEquals(R.color.rescue_progress_high, RescueProgressUtils.getProgressColorRes(70.0));
        assertEquals(R.color.rescue_progress_high, RescueProgressUtils.getProgressColorRes(85.0));
        assertEquals(R.color.rescue_progress_high, RescueProgressUtils.getProgressColorRes(100.0));
        assertEquals(R.color.rescue_progress_high, RescueProgressUtils.getProgressColorRes(120.0));
    }
}
