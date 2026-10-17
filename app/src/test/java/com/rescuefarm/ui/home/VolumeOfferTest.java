package com.rescuefarm.ui.home;
import org.junit.Test;import static org.junit.Assert.*;
import com.rescuefarm.domain.model.*;import com.rescuefarm.domain.enums.*;import java.util.*;
public class VolumeOfferTest {
    private final Date now=new Date(100L*86400000L);
    private Product product(){return Product.restore("product","seller","fruit","Product","Description",100000,80000,"kg","Farm","Province",Collections.emptyList(),0,0,ProductStatus.ACTIVE);}
    private Promotion promotion(Date start,Date end,boolean active){return new Promotion("product","seller","product",PromotionType.VOLUME,0,Collections.singletonMap("1.5",12.5),start,end,active);}
    private List<VolumeOffer> offers(Promotion promo,double stock) {
        ProductBatch batch=ProductBatch.restore("batch","product","",new Date(1),new Date(now.getTime()+86400000L),stock,stock,0,0,BatchStatus.AVAILABLE,0);
        return new HomeContentBuilder().volumeOffers(Collections.singletonList(product()),promo==null?Collections.emptyMap():Collections.singletonMap("product",promo),
            Collections.singletonMap("product",Collections.singletonList(batch)),Collections.emptyList(),now);
    }
    @Test public void rescuePriceAloneDoesNotBecomeAVolumeOffer(){assertTrue(offers(null,10).isEmpty());}
    @Test public void expiredFutureAndDisabledPromotionsDisappear() {
        assertTrue(offers(promotion(new Date(1),now,true),10).isEmpty());
        assertTrue(offers(promotion(new Date(now.getTime()+1),new Date(now.getTime()+1000),true),10).isEmpty());
        assertTrue(offers(promotion(new Date(1),new Date(now.getTime()+1000),false),10).isEmpty());
    }
    @Test public void advertisedTierIsAchievableAndUsesTheSamePricingContractAsCheckout() {
        Promotion p=promotion(new Date(1),new Date(now.getTime()+1000),true);
        assertTrue(offers(p,1).isEmpty());
        VolumeOffer offer=offers(p,2).get(0);assertEquals("batch",offer.batchId);assertEquals(1.5,offer.threshold,.000001);
        assertEquals(12.5,offer.percent,.000001);assertEquals(70000,offer.unitPrice,.001);assertEquals("kg",offer.product.getUnit());
    }
}
