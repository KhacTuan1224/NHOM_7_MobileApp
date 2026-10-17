package com.rescuefarm.domain;

import com.rescuefarm.domain.model.RescueCampaign;
import com.rescuefarm.domain.enums.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class CampaignAllocationTest {
    private RescueCampaign campaign() {
        Map<String, Double> targets = new LinkedHashMap<>(); targets.put("a", 10D); targets.put("b", 90D);
        return RescueCampaign.restore("c", "seller", "Campaign", "", RescueReason.OVER_SUPPLY,
                UrgencyLevel.NORMAL, RescueMode.FIXED_POINT, targets, 100D, 3D, 5D,
                new Date(1), new Date(100000), 0, 0, 0, 0, null, false, "", CampaignStatus.ACTIVE);
    }
    @Test public void remainingUsesBothHeldAndDeliveredForTheSelectedBatch() {
        RescueCampaign c = campaign();
        c.setAllocationProgress(Collections.singletonMap("a", 3D), Collections.singletonMap("a", 5D), 1);
        assertEquals(2D, c.calculateRemainingForBatch("a"), .000001);
        assertEquals(90D, c.calculateRemainingForBatch("b"), .000001);
        assertEquals(0D, c.calculateRemainingForBatch("missing"), .000001);
    }
    @Test public void inconsistentTotalsAndOverAllocationAreRejected() {
        RescueCampaign c = campaign();
        assertThrows(IllegalStateException.class, () -> c.setAllocationProgress(
                Collections.singletonMap("a", 2D), Collections.singletonMap("a", 5D), 1));
        assertThrows(IllegalArgumentException.class, () -> c.setAllocationProgress(
                Collections.singletonMap("outside", 3D), Collections.singletonMap("a", 5D), 1));
        assertThrows(IllegalArgumentException.class, () -> c.setAllocationProgress(
                Collections.singletonMap("a", Double.NaN), Collections.singletonMap("a", 5D), 1));
    }
    @Test public void progressSurvivesRoomMappingAndCannotBeMutatedFromOutside() {
        RescueCampaign c = campaign();
        c.setAllocationProgress(Collections.singletonMap("a", 3D), Collections.singletonMap("a", 5D), 1);
        RescueCampaign cached = com.rescuefarm.data.local.mapper.CampaignCacheMapper.toDomain(
                com.rescuefarm.data.local.mapper.CampaignCacheMapper.toEntity(c, 100));
        assertEquals(1, cached.getAllocationVersion());
        assertEquals(2D, cached.calculateRemainingForBatch("a"), .000001);
        assertThrows(UnsupportedOperationException.class, () -> cached.getReservedByBatch().put("a", 0D));
    }
}
