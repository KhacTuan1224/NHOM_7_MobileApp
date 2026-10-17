package com.rescuefarm.domain;
import org.junit.Test;
import static org.junit.Assert.*;
import com.rescuefarm.domain.model.RescueCampaign;
import com.rescuefarm.service.campaign.QuantityUnitPolicy;
import com.rescuefarm.data.local.mapper.CampaignCacheMapper;
import com.rescuefarm.domain.enums.*;
import java.util.*;
public class CampaignUnitTest {
    private RescueCampaign campaign() {
        return RescueCampaign.restore("c", "s", "Campaign", "", RescueReason.OVER_SUPPLY, UrgencyLevel.NORMAL,
                RescueMode.FIXED_POINT, Collections.singletonMap("b", 10D), 10, 0, 2,
                new Date(1), new Date(1000), 0, 0, 0, 0, null, false, "", CampaignStatus.ACTIVE);
    }
    @Test public void bundlesNeverBecomeKilogramsAndUnverifiedCampaignsAreExcludedFromKgAnalytics() {
        RescueCampaign c = campaign(); assertFalse(c.isVerifiedKilograms());
        c.setQuantityUnit("BÓ", "VERIFIED"); assertEquals("bó", c.getQuantityUnitLabel()); assertFalse(c.isVerifiedKilograms());
        c.setQuantityUnit("kilogram", "VERIFIED"); assertTrue(c.isVerifiedKilograms());
        c.setQuantityUnit("kg", "REVIEW_REQUIRED"); assertFalse(c.isVerifiedKilograms());
        assertEquals("đơn vị chưa xác minh", c.getQuantityUnitLabel()); assertEquals("bao", QuantityUnitPolicy.normalize(" Bao "));
    }
    @Test public void unitVerificationSurvivesRoomCache() {
        RescueCampaign c = campaign(); c.setQuantityUnit("BÓ", "VERIFIED");
        RescueCampaign cached = CampaignCacheMapper.toDomain(CampaignCacheMapper.toEntity(c, 10));
        assertEquals("bó", cached.getQuantityUnit()); assertEquals("VERIFIED", cached.getUnitStatus());
    }
}
