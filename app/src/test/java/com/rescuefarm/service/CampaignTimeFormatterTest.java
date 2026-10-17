package com.rescuefarm.service;
import org.junit.Test;import static org.junit.Assert.*;
import com.rescuefarm.service.campaign.CampaignTimeFormatter;
import com.rescuefarm.domain.model.RescueCampaign;import com.rescuefarm.domain.enums.*;
import java.util.*;
public class CampaignTimeFormatterTest {
    private final long at=100L*86400000L;
    private RescueCampaign campaign(RescueMode mode,Date location) {
        RescueCampaign c=RescueCampaign.restore("campaign","seller","Campaign","Story",RescueReason.OVER_SUPPLY,UrgencyLevel.NORMAL,mode,
            Collections.singletonMap("batch",10D),10,0,0,new Date(at-1000),new Date(at+86400000L),10,106,10,106,location,mode==RescueMode.MOBILE_POINT,"Farm",CampaignStatus.ACTIVE);
        c.setQuantityUnit("kg","VERIFIED");return c;
    }
    @Test public void countdownChangesWithoutMutationAndExpiresExactlyAtDeadline() {
        RescueCampaign c=campaign(RescueMode.FIXED_POINT,null);
        assertTrue(CampaignTimeFormatter.remaining(c,new Date(at)).contains("1 ngày"));
        assertTrue(CampaignTimeFormatter.remaining(c,new Date(at+3600000)).contains("23 giờ"));
        assertFalse(CampaignTimeFormatter.open(c,new Date(at+86400000L)));
        assertTrue(CampaignTimeFormatter.remaining(c,new Date(at+86400000L)).contains("kết thúc"));
    }
    @Test public void mobileActionChangesAfterTenMinuteFreshnessBoundary() {
        RescueCampaign c=campaign(RescueMode.MOBILE_POINT,new Date(at));
        assertTrue(CampaignTimeFormatter.canPurchase(c,new Date(at+600000)));
        assertFalse(CampaignTimeFormatter.canPurchase(c,new Date(at+600001)));
        assertTrue(CampaignTimeFormatter.locationUpdated(new Date(at),new Date(at+600001)).contains("10 phút trước"));
    }
    @Test public void reservedOutCampaignKeepsLocationUpdatesForExistingOrders() {
        RescueCampaign c=RescueCampaign.restore("campaign","seller","Campaign","Story",RescueReason.OVER_SUPPLY,UrgencyLevel.NORMAL,RescueMode.MOBILE_POINT,
            Collections.singletonMap("batch",10D),10,10,0,new Date(at-1000),new Date(at+86400000L),10,106,10,106,new Date(at),true,"Farm",CampaignStatus.ACTIVE);
        c.setQuantityUnit("kg","VERIFIED");
        assertFalse(CampaignTimeFormatter.canPurchase(c,new Date(at)));
        assertTrue(CampaignTimeFormatter.withinSchedule(c,new Date(at)));
        assertTrue(CampaignTimeFormatter.canUpdateLocation(c,new Date(at)));
    }
    @Test public void futureLocationAndUnverifiedUnitsAreNotPurchasable() {
        RescueCampaign c=campaign(RescueMode.MOBILE_POINT,new Date(at+1));
        assertFalse(CampaignTimeFormatter.canPurchase(c,new Date(at)));
        c=campaign(RescueMode.FIXED_POINT,null);c.setQuantityUnit("kg","REVIEW_REQUIRED");
        assertFalse(CampaignTimeFormatter.canPurchase(c,new Date(at)));
    }
}
