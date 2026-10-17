package com.rescuefarm.ui.home;
import org.junit.Test;
import static org.junit.Assert.*;
import com.rescuefarm.domain.model.*;
import com.rescuefarm.domain.enums.*;
import java.util.*;
public class DiscoveryCampaignProjectionTest {
    private final Date now = new Date(100L * 86400000L);
    private Product product(String id, String category, double price) {
        return Product.restore(id,"seller",category,"Product " + id,"description",100,price,"kg","origin","province",Collections.emptyList(),0,0,ProductStatus.ACTIVE);
    }
    private ProductBatch batch(String id, String product, String campaign, double stock) {
        return ProductBatch.restore(id,product,campaign,new Date(now.getTime()-86400000L),new Date(now.getTime()+10*86400000L),stock,stock,0,0,BatchStatus.AVAILABLE,1);
    }
    private RescueCampaign campaign(String id, Map<String,Double> targets) {
        RescueCampaign c = RescueCampaign.restore(id,"seller","Campaign " + id,"description",RescueReason.OVER_SUPPLY,UrgencyLevel.NORMAL,
            RescueMode.FIXED_POINT,targets,100,0,0,new Date(now.getTime()-1000),new Date(now.getTime()+10*86400000L),10,106,0,0,null,false,"province",CampaignStatus.ACTIVE);
        c.setQuantityUnit("kg","VERIFIED"); return c;
    }
    private List<DiscoveryResult> campaigns(List<Product> products, List<RescueCampaign> campaigns, Map<String,List<ProductBatch>> batches, DiscoveryQuery q) {
        List<DiscoveryResult> result = new ArrayList<>();
        for (DiscoveryResult r : new DiscoveryEngine().search(products,campaigns,batches,q,null,null,now))
            if (r.getType() == DiscoveryResult.Type.CAMPAIGN) result.add(r);
        return result;
    }
    @Test public void categoryPriceAndStockMustMatchTheSameBatch() {
        List<Product> products=Arrays.asList(product("veg","vegetables",80), product("fruit","fruits",10));
        Map<String,List<ProductBatch>> batches=new HashMap<>();
        batches.put("veg",Collections.singletonList(batch("a","veg","c",2)));
        batches.put("fruit",Collections.singletonList(batch("b","fruit","c",50)));
        RescueCampaign c=campaign("c",new HashMap<String,Double>(){{put("a",50D);put("b",50D);}});
        DiscoveryQuery lowPrice=new DiscoveryQuery("","vegetables","",null,null,null,DiscoveryQuery.Sort.PRICE_ASC,0,20,0,0,0);
        assertTrue(campaigns(products,Collections.singletonList(c),batches,lowPrice).isEmpty());
        DiscoveryQuery highStock=new DiscoveryQuery("","vegetables","",null,null,null,DiscoveryQuery.Sort.PRICE_ASC,0,100,0,0,3);
        assertTrue(campaigns(products,Collections.singletonList(c),batches,highStock).isEmpty());
        DiscoveryQuery valid=new DiscoveryQuery("","vegetables","",null,null,null,DiscoveryQuery.Sort.PRICE_ASC,0,100,0,0,1);
        List<DiscoveryResult> found=campaigns(products,Collections.singletonList(c),batches,valid);
        assertEquals(1,found.size()); assertEquals(80,found.get(0).getMinimumPrice(),.001);
        assertEquals(20,found.get(0).getMaximumDiscount(),.001);
    }
    @Test public void sortingUsesActualCampaignPriceDiscountAndDeliveredAllocation() {
        Product cheap=product("cheap","fruit",10), expensive=product("expensive","fruit",60);
        RescueCampaign a=campaign("a",Collections.singletonMap("ba",100D)), b=campaign("b",Collections.singletonMap("bb",100D));
        Map<String,List<ProductBatch>> batches=new HashMap<>();
        batches.put("cheap",Collections.singletonList(batch("ba","cheap","a",50)));
        batches.put("expensive",Collections.singletonList(batch("bb","expensive","b",50)));
        List<Product> products=Arrays.asList(expensive,cheap); List<RescueCampaign> cs=Arrays.asList(b,a);
        for (DiscoveryQuery.Sort sort : Arrays.asList(DiscoveryQuery.Sort.PRICE_ASC,DiscoveryQuery.Sort.DISCOUNT_DESC)) {
            DiscoveryQuery q=new DiscoveryQuery("","","",null,null,null,sort);
            assertEquals("a",campaigns(products,cs,batches,q).get(0).getCampaign().getId());
        }
        b.setAllocationProgress(Collections.emptyMap(),Collections.singletonMap("bb",20D),0);
        DiscoveryQuery best=new DiscoveryQuery("","","",null,null,null,DiscoveryQuery.Sort.BEST_SELLING);
        assertEquals("b",campaigns(products,cs,batches,best).get(0).getCampaign().getId());
    }
    @Test public void unrelatedCategoryAndUnverifiedUnitAreExcluded() {
        Product p=product("p","fruit",10); RescueCampaign c=campaign("c",Collections.singletonMap("b",100D));
        Map<String,List<ProductBatch>> batches=Collections.singletonMap("p",Collections.singletonList(batch("b","p","c",10)));
        DiscoveryQuery q=new DiscoveryQuery("","vegetables","",null,null,null,DiscoveryQuery.Sort.RELEVANCE);
        assertTrue(campaigns(Collections.singletonList(p),Collections.singletonList(c),batches,q).isEmpty());
        c.setQuantityUnit("kg","REVIEW_REQUIRED");
        assertTrue(campaigns(Collections.singletonList(p),Collections.singletonList(c),batches,DiscoveryQuery.empty()).isEmpty());
    }
}
