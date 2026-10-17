package com.rescuefarm.ui.home;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import com.rescuefarm.domain.enums.CampaignStatus;
import com.rescuefarm.domain.enums.ProductStatus;
import com.rescuefarm.domain.enums.RescueMode;
import com.rescuefarm.domain.enums.RescueReason;
import com.rescuefarm.domain.enums.UrgencyLevel;
import com.rescuefarm.domain.model.Product;
import com.rescuefarm.domain.model.RescueCampaign;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.Test;

public class DiscoveryEngineTest {
    @Test public void catalogProvinceNames_matchShortSavedNames_andStayAppliedWhileTyping() {
        Product mango = Product.restore("mango", "s", "fruit", "Xoài cát", "Nông sản",
                50D, 30D, "kg", "Vườn nhà", "Đồng Tháp", Collections.emptyList(),
                0D, 0, ProductStatus.ACTIVE);
        Product otherProvince = Product.restore("other", "s", "fruit", "Xoài cát", "Nông sản",
                50D, 30D, "kg", "Vườn nhà", "Tiền Giang", Collections.emptyList(),
                0D, 0, ProductStatus.ACTIVE);
        DiscoveryQuery filter = new DiscoveryQuery("", "fruit", "Tỉnh Đồng Tháp", null, null, null,
                DiscoveryQuery.Sort.RELEVANCE);
        DiscoveryEngine engine = new DiscoveryEngine();
        List<Product> products = Arrays.asList(mango, otherProvince);
        List<DiscoveryResult> result = engine.search(products, Collections.emptyList(),
                filter.withText("xoai"), null, null, new Date());
        assertEquals(1, result.size());
        assertEquals("mango", result.get(0).getProduct().getId());
        assertTrue(engine.search(products, Collections.emptyList(), filter.withText("bo"),
                null, null, new Date()).isEmpty());
        assertEquals(2, engine.search(products, Collections.emptyList(), DiscoveryQuery.empty(),
                null, null, new Date()).size());
    }

    @Test public void catalogCityName_matchesAbbreviatedLocation() {
        Product product = Product.restore("p", "s", "fruit", "Xoài", "Nông sản",
                50D, 30D, "kg", "Vườn nhà", "TP. HCM", Collections.emptyList(),
                0D, 0, ProductStatus.ACTIVE);
        DiscoveryQuery query = new DiscoveryQuery("", "", "Thành phố Hồ Chí Minh", null, null, null,
                DiscoveryQuery.Sort.RELEVANCE);
        assertEquals(1, new DiscoveryEngine().search(Collections.singletonList(product),
                Collections.emptyList(), query, null, null, new Date()).size());
    }

    @Test public void textSearch_isAccentInsensitive_andCriticalComesFirst() {
        Product mango = Product.restore("p", "s", "fruit", "Xoài cát", "Nông sản",
                50D, 30D, "kg", "Vườn nhà", "Đồng Tháp", Collections.emptyList(),
                0D, 0, ProductStatus.ACTIVE);
        Date now = new Date(2_000_000L);
        RescueCampaign critical = campaign("c", UrgencyLevel.CRITICAL, now);
        critical.setQuantityUnit("kg", "VERIFIED");
        DiscoveryQuery query = new DiscoveryQuery("xoai", "", "", null, null, null,
                DiscoveryQuery.Sort.RELEVANCE);
        List<DiscoveryResult> onlyProduct = new DiscoveryEngine().search(
                Collections.singletonList(mango), Collections.emptyList(), query, null, null, now);
        assertEquals("p", onlyProduct.get(0).getProduct().getId());

        List<DiscoveryResult> all = new DiscoveryEngine().search(Collections.singletonList(mango),
                Collections.singletonList(critical), Collections.singletonMap("p", Collections.singletonList(
                    com.rescuefarm.domain.model.ProductBatch.restore("b", "p", "c", new Date(1), new Date(4_000_000L),
                    10, 10, 0, 0, com.rescuefarm.domain.enums.BatchStatus.AVAILABLE, 1))), DiscoveryQuery.empty(), null, null, now);
        assertEquals("c", all.get(0).getCampaign().getId());
    }

    @Test public void campaignFilter_excludesProducts_andDistanceExcludesStaleMobile() {
        Date now = new Date(2_000_000L);
        RescueCampaign stale = RescueCampaign.restore("stale", "s", "Mobile stale", "Mô tả",
                RescueReason.NEAR_EXPIRY, UrgencyLevel.HIGH, RescueMode.MOBILE_POINT,
                Collections.singletonMap("b", 10D), 10D, 0D, 0D,
                new Date(1_000_000L), new Date(3_000_000L), 10D, 106D, 10D, 106D,
                new Date(now.getTime() - TimeUnit.MINUTES.toMillis(11)), true,
                "TP HCM", CampaignStatus.ACTIVE);
        DiscoveryQuery query = new DiscoveryQuery("", "", "", RescueReason.NEAR_EXPIRY,
                null, null, DiscoveryQuery.Sort.DISTANCE);
        assertTrue(new DiscoveryEngine().search(Collections.emptyList(),
                Collections.singletonList(stale), query, 10D, 106D, now).isEmpty());
    }

    private RescueCampaign campaign(String id, UrgencyLevel urgency, Date now) {
        return RescueCampaign.restore(id, "s", "Giải cứu xoài", "Mô tả",
                RescueReason.OVER_SUPPLY, urgency, RescueMode.FIXED_POINT,
                Collections.singletonMap("b", 10D), 10D, 0D, 0D,
                new Date(now.getTime() - 1000L), new Date(now.getTime() + 10_000L),
                10D, 106D, 0D, 0D, null, false, "TP HCM", CampaignStatus.ACTIVE);
    }
}
