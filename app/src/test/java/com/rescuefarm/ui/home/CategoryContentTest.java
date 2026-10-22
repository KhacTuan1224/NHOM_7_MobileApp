package com.rescuefarm.ui.home;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.MutableLiveData;
import com.rescuefarm.data.repository.AuthRepository;
import com.rescuefarm.data.repository.CampaignRepository;
import com.rescuefarm.data.repository.ProductRepository;
import com.rescuefarm.domain.enums.BatchStatus;
import com.rescuefarm.domain.enums.CampaignStatus;
import com.rescuefarm.domain.enums.ProductStatus;
import com.rescuefarm.domain.enums.RescueMode;
import com.rescuefarm.domain.enums.RescueReason;
import com.rescuefarm.domain.enums.UrgencyLevel;
import com.rescuefarm.domain.model.Product;
import com.rescuefarm.domain.model.ProductBatch;
import com.rescuefarm.domain.model.RescueCampaign;
import com.rescuefarm.service.location.LocationProvider;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import static org.junit.Assert.*;

public class CategoryContentTest {
    @Rule public InstantTaskExecutorRule rule = new InstantTaskExecutorRule();

    @Test public void categoryIgnoresSearchFiltersAndDoesNotChangeThem() {
        HomeViewModel model = model();
        DiscoveryQuery filter = new DiscoveryQuery("không khớp", "vegetable", "Hà Nội",
                null, null, null, DiscoveryQuery.Sort.DISTANCE);
        model.setQuery(filter);
        List<DiscoveryResult> content = model.getCategoryContent("fruit");
        assertEquals(2, content.size());
        assertTrue(content.stream().anyMatch(r -> r.getProduct() != null && "mango".equals(r.getProduct().getId())));
        assertTrue(content.stream().anyMatch(r -> r.getCampaign() != null && "fruit-story".equals(r.getCampaign().getId())));
        assertSame(filter, model.getQuery());
        assertTrue(model.getSearchResults().getValue().isEmpty());
    }

    @Test public void articlesOnlyAppearForTheirProductsCategory() {
        HomeViewModel model = model();
        List<DiscoveryResult> content = model.getCategoryContent("vegetable");
        assertEquals(1, content.size());
        assertEquals("carrot", content.get(0).getProduct().getId());
        RescueCampaign article = model.getCategoryContent("fruit").stream()
                .filter(r -> r.getCampaign() != null).findFirst().get().getCampaign();
        List<Product> related = model.getCategoryCampaignProducts(article, model.getProductsSnapshot());
        assertEquals(1, related.size());
        assertEquals("mango", related.get(0).getId());
    }

    @Test public void missingCategoryNeverShowsEntireCatalog() {
        HomeViewModel model = model();
        assertTrue(model.getCategoryContent(null).isEmpty());
        assertTrue(model.getCategoryContent(" ").isEmpty());
        assertTrue(model.getCategoryContent("unknown").isEmpty());
    }

    private HomeViewModel model() {
        long now = System.currentTimeMillis();
        RescueCampaign story = RescueCampaign.restore("fruit-story", "s", "Giải cứu xoài", "Câu chuyện",
                RescueReason.NEAR_EXPIRY, UrgencyLevel.HIGH, RescueMode.FIXED_POINT,
                Collections.singletonMap("mango-batch", 10D), 10D, 0D, 0D,
                new Date(now - 60000), new Date(now + 3600000), 10D, 106D, 10D, 106D,
                new Date(now), false, "Đồng Tháp", CampaignStatus.ACTIVE);
        story.setQuantityUnit("kg", "VERIFIED");
        ProductBatch batch = ProductBatch.restore("mango-batch", "mango", "fruit-story",
                new Date(now - 60000), new Date(now + 3600000), 10, 10, 0, 0, BatchStatus.AVAILABLE, 1);
        List<Product> catalog = Arrays.asList(product("mango", "fruit", ProductStatus.ACTIVE),
                product("carrot", "vegetable", ProductStatus.ACTIVE),
                product("hidden", "fruit", ProductStatus.INACTIVE));
        ProductRepository products = (ProductRepository) Proxy.newProxyInstance(
                ProductRepository.class.getClassLoader(), new Class[]{ProductRepository.class}, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "observePublicProductPage": return new MutableLiveData<>(catalog);
                        case "observeCategories": return new MutableLiveData<>(Collections.emptyList());
                        case "observeBatches": return new MutableLiveData<>("mango".equals(args[0])
                                ? Collections.singletonList(batch) : "carrot".equals(args[0])
                                ? Collections.singletonList(ProductBatch.restore("carrot-batch", "carrot", null,
                                    new Date(now - 60000), new Date(now + 3600000), 10, 10, 0, 0, BatchStatus.AVAILABLE, 1))
                                : Collections.emptyList());
                        case "close": return null;
                        default: throw new AssertionError(method.getName());
                    }
                });
        CampaignRepository campaigns = (CampaignRepository) Proxy.newProxyInstance(
                CampaignRepository.class.getClassLoader(), new Class[]{CampaignRepository.class}, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "observePublicCampaignPage": return new MutableLiveData<>(Collections.singletonList(story));
                        case "observeActiveBanners": return new MutableLiveData<>(Collections.emptyList());
                        case "close": return null;
                        default: throw new AssertionError(method.getName());
                    }
                });
        AuthRepository auth = (AuthRepository) Proxy.newProxyInstance(AuthRepository.class.getClassLoader(),
                new Class[]{AuthRepository.class}, (proxy, method, args) -> {
                    if (method.getName().equals("isAuthenticated")) return false;
                    return null;
                });
        HomeViewModel model = new HomeViewModel(products, campaigns, auth, new LocationProvider() {
            public void getCurrentLocation(LocationCallback callback) { }
            public void reverseGeocode(double latitude, double longitude, GeocodeCallback callback) { }
        });
        model.getHomeState().observeForever(value -> { });
        return model;
    }

    private Product product(String id, String category, ProductStatus status) {
        return Product.restore(id, "s", category, id, "Nông sản", 50D, 30D, "kg",
                "Vườn nhà", "Đồng Tháp", Collections.emptyList(), 0D, 0, status);
    }
}
