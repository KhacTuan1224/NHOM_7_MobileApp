package com.rescuefarm.ui.common;
import com.rescuefarm.R;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
public class CampaignProgressColorsTest {
    @Test public void rescueProgressTurnsGreenAtFiftyPercent() {
        assertEquals(R.color.rescue_critical, CampaignProgressColors.forPercent(0));
        assertEquals(R.color.rescue_critical, CampaignProgressColors.forPercent(49.99));
        assertEquals(R.color.rescue_primary, CampaignProgressColors.forPercent(50));
        assertEquals(R.color.rescue_primary, CampaignProgressColors.forPercent(100));
        assertEquals(R.color.rescue_critical, CampaignProgressColors.forPercent(Double.NaN));
    }
}
