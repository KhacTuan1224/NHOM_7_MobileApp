package com.rescuefarm.service;
import com.rescuefarm.domain.enums.*;import com.rescuefarm.domain.model.*;import com.rescuefarm.service.engagement.*;import java.util.*;import org.junit.Test;import static org.junit.Assert.*;
public class EngagementSafetyTest{
 @Test public void favoriteKeyIsUniquePerCustomerProduct(){assertEquals(Favorite.key("u1","p1"),Favorite.create("u1","p1",new Date()).getId());}
 @Test public void reviewKeyIsUniquePerOrderItem(){assertEquals("u1_order1_item1",Review.key("u1","order1","item1"));}
 @Test public void deliveredOwnedItemCanBeReviewed(){new ReviewEligibilityService().requireEligible("u","u",OrderOwnerType.CUSTOMER,OrderStatus.DELIVERED,"p","p");}
 @Test(expected=IllegalStateException.class) public void pendingOrderCannotBeReviewed(){new ReviewEligibilityService().requireEligible("u","u",OrderOwnerType.CUSTOMER,OrderStatus.PENDING,"p","p");}
 @Test(expected=IllegalStateException.class) public void anotherCustomersItemCannotBeReviewed(){new ReviewEligibilityService().requireEligible("x","u",OrderOwnerType.CUSTOMER,OrderStatus.DELIVERED,"p","p");}
 @Test public void reportRequiresReviewBeforeResolution(){Report r=Report.create("r","u",ReportTargetType.CAMPAIGN,"p","spam","",new Date());try{r.resolve("a","done");fail();}catch(IllegalStateException expected){}r.startReview("a");r.resolve("a","done");assertEquals(ReportStatus.RESOLVED,r.getStatus());}
 @Test public void notificationDeepLinkRoutesKnownTypes(){assertEquals("rescuefarm://orders/o1",new NotificationDeepLinkService().build(NotificationType.ORDER,"o1"));assertEquals("rescuefarm://notifications",new NotificationDeepLinkService().build(NotificationType.SYSTEM,""));}
 @Test public void reviewValidationRejectsOversizedMedia(){List<String> images=Arrays.asList("1","2","3","4","5","6");try{Review.create("u","p","o","i",5,"ok",images,new Date());fail();}catch(IllegalArgumentException expected){}}
}
