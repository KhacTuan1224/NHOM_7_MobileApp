package com.rescuefarm.ui.home;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import com.rescuefarm.domain.enums.CampaignStatus;
import com.rescuefarm.domain.enums.RescueMode;
import com.rescuefarm.domain.enums.RescueReason;
import com.rescuefarm.domain.enums.UrgencyLevel;
import com.rescuefarm.domain.model.RescueCampaign;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.concurrent.TimeUnit;
import org.junit.Test;

public class HomeContentBuilderTest {
    @Test public void sectionOrder_includesBatchExpiryBeforeValueProducts() {
        assertEquals(11, HomeViewState.SECTION_ORDER.size());
        assertEquals(HomeViewState.Section.NEAR_EXPIRY, HomeViewState.SECTION_ORDER.get(5));
        assertEquals(HomeViewState.Section.BANNERS, HomeViewState.SECTION_ORDER.get(0));
        assertEquals(HomeViewState.Section.RESCUE_FEED, HomeViewState.SECTION_ORDER.get(10));
    }

    @Test public void criticalIsFirst_andStaleMobileIsExcludedFromNearby() {
        Date now = new Date(2_000_000L);
        RescueCampaign normal = campaign("normal", UrgencyLevel.NORMAL, RescueMode.FIXED_POINT,
                now, null, false);
        RescueCampaign critical = campaign("critical", UrgencyLevel.CRITICAL, RescueMode.FIXED_POINT,
                now, null, false);
        RescueCampaign stale = campaign("stale", UrgencyLevel.HIGH, RescueMode.MOBILE_POINT,
                now, new Date(now.getTime() - TimeUnit.MINUTES.toMillis(11)), true);
        HomeViewState state = new HomeContentBuilder().build(Collections.emptyList(),
                Collections.emptyList(), Arrays.asList(normal, stale, critical),
                Collections.emptyList(), 10D, 106D, now,
                HomeViewState.LocationState.AVAILABLE, false, "");
        assertEquals("critical", state.getActiveCampaigns().get(0).getId());
        assertEquals(1, state.getCritical().size());
        assertTrue(state.getNearbyMobile().isEmpty());
        assertEquals(2, state.getNearbyFixed().size());
    }

    @Test public void cachedContentIsMarkedStaleUntilRefreshSucceeds() {
        HomeViewState state = new HomeContentBuilder().build(Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                null, null, new Date(), HomeViewState.LocationState.PERMISSION_REQUIRED,
                false, "cache");
        assertEquals(HomeViewState.DataFreshness.STALE, state.getDataFreshness());
    }

    @Test public void nearExpiryUsesSellableBatchExpiry_withoutAnyCampaign_andKeepsTheCorrectBatch() {
        Date now = new Date(TimeUnit.DAYS.toMillis(100));
        com.rescuefarm.domain.model.Product a = product("a"), b = product("b");
        java.util.Map<String, java.util.List<com.rescuefarm.domain.model.ProductBatch>> batches = new java.util.HashMap<>();
        batches.put("a", Arrays.asList(batch("a-far", "a", now, 7, com.rescuefarm.domain.enums.BatchStatus.AVAILABLE),
                batch("a-soon", "a", now, 2, com.rescuefarm.domain.enums.BatchStatus.AVAILABLE)));
        batches.put("b", Collections.singletonList(batch("b-soon", "b", now, 1, com.rescuefarm.domain.enums.BatchStatus.AVAILABLE)));
        HomeViewState state = new HomeContentBuilder().build(Collections.emptyList(), Arrays.asList(a, b),
                Collections.emptyList(), Collections.emptyList(), batches, null, null, now,
                HomeViewState.LocationState.PERMISSION_REQUIRED, false, "", HomeViewState.DataFreshness.FRESH);
        assertEquals(2, state.getNearExpiry().size());
        assertEquals("b-soon", state.getNearExpiry().get(0).getBatch().getId());
        assertEquals("a-soon", state.getNearExpiry().get(1).getBatch().getId());
    }

    @Test public void nearExpiryNeverListsExpiredArchivedOrSoldOutBatches() {
        Date now = new Date(TimeUnit.DAYS.toMillis(100));
        java.util.List<com.rescuefarm.domain.model.ProductBatch> batches = Arrays.asList(
                batch("expired", "a", now, -1, com.rescuefarm.domain.enums.BatchStatus.AVAILABLE),
                batch("flagged-expired", "a", now, 1, com.rescuefarm.domain.enums.BatchStatus.EXPIRED),
                batch("archived", "a", now, 1, com.rescuefarm.domain.enums.BatchStatus.ARCHIVED),
                batch("sold", "a", now, 1, com.rescuefarm.domain.enums.BatchStatus.SOLD_OUT));
        HomeViewState state = new HomeContentBuilder().build(Collections.emptyList(), Collections.singletonList(product("a")),
                Collections.emptyList(), Collections.emptyList(), Collections.singletonMap("a", batches), null, null, now,
                HomeViewState.LocationState.PERMISSION_REQUIRED, false, "", HomeViewState.DataFreshness.FRESH);
        assertTrue(state.getNearExpiry().isEmpty());
    }

    @Test public void customLocationLabelIsPreservedInViewState() {
        HomeViewState state = new HomeContentBuilder().build(Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                Collections.emptyMap(), 10.7769D, 106.7009D, new Date(),
                HomeViewState.LocationState.AVAILABLE, false, "OK",
                HomeViewState.DataFreshness.FRESH, "TP. Hồ Chí Minh");
        assertEquals("TP. Hồ Chí Minh", state.getLocationLabel());
        assertEquals(HomeViewState.LocationState.AVAILABLE, state.getLocationState());
    }

    @Test public void formatAddress_formatsStreetWardAndProvinceAccurately() {
        com.rescuefarm.service.location.LocationProvider.GeocodedAddress address =
                new com.rescuefarm.service.location.LocationProvider.GeocodedAddress(
                        "Thành phố Hồ Chí Minh", "Bình Thạnh", "Bình Lợi Trung", "111/8/8");
        String formatted = HomeViewModel.formatAddress(address);
        assertEquals("111/8/8, Bình Lợi Trung, Hồ Chí Minh", formatted);
    }

    @Test public void formatAddress_shortensLongAddressWithEllipsisOrCompact() {
        com.rescuefarm.service.location.LocationProvider.GeocodedAddress address =
                new com.rescuefarm.service.location.LocationProvider.GeocodedAddress(
                        "Hồ Chí Minh", "Bình Thạnh", "Phường 13", "111/8/2 Đ. Đặng Thuỳ Trâm");
        String formatted = HomeViewModel.formatAddress(address);
        assertEquals("111/8/2 Đ. Đặng Thuỳ Trâm, Hồ Chí Minh", formatted);
    }

    private com.rescuefarm.domain.model.Product product(String id) {
        return new com.rescuefarm.domain.model.Product(id, "seller", "category", "Rau " + id, "",
                40000, 30000, "kg", "", "", Collections.emptyList(), com.rescuefarm.domain.enums.ProductStatus.ACTIVE);
    }
    @Test public void urgencyAndVisibilityAdvanceWithoutDocumentChanges() {
        Date now = new Date(TimeUnit.DAYS.toMillis(100));
        RescueCampaign c = campaign("clock", UrgencyLevel.NORMAL, RescueMode.FIXED_POINT, now, null, false);
        java.util.Map<String, java.util.List<com.rescuefarm.domain.model.ProductBatch>> batches = Collections.singletonMap("a",
                Collections.singletonList(batch("batch", "a", now, 4, com.rescuefarm.domain.enums.BatchStatus.RESERVED_PARTIAL)));
        HomeContentBuilder builder = new HomeContentBuilder();
        HomeViewState first = builder.build(Collections.emptyList(), Collections.singletonList(product("a")), Collections.singletonList(c),
                Collections.emptyList(), batches, null, null, now, HomeViewState.LocationState.PERMISSION_REQUIRED, false, "", HomeViewState.DataFreshness.FRESH);
        assertEquals(UrgencyLevel.HIGH, first.getActiveCampaigns().get(0).getUrgencyLevel());
        HomeViewState later = builder.build(Collections.emptyList(), Collections.singletonList(product("a")), Collections.singletonList(c),
                Collections.emptyList(), batches, null, null, new Date(now.getTime() + TimeUnit.DAYS.toMillis(2)), HomeViewState.LocationState.PERMISSION_REQUIRED, false, "", HomeViewState.DataFreshness.FRESH);
        assertEquals(UrgencyLevel.CRITICAL, later.getActiveCampaigns().get(0).getUrgencyLevel());
        HomeViewState expired = builder.build(Collections.emptyList(), Collections.singletonList(product("a")), Collections.singletonList(c),
                Collections.emptyList(), batches, null, null, new Date(now.getTime() + TimeUnit.DAYS.toMillis(10)), HomeViewState.LocationState.PERMISSION_REQUIRED, false, "", HomeViewState.DataFreshness.FRESH);
        assertTrue(expired.getActiveCampaigns().isEmpty());
    }

    private com.rescuefarm.domain.model.ProductBatch batch(String id, String product, Date now, int days,
            com.rescuefarm.domain.enums.BatchStatus status) {
        boolean sold = status == com.rescuefarm.domain.enums.BatchStatus.SOLD_OUT;
        return com.rescuefarm.domain.model.ProductBatch.restore(id, product, "", new Date(now.getTime() - TimeUnit.DAYS.toMillis(10)),
                new Date(now.getTime() + TimeUnit.DAYS.toMillis(days)), 10, sold ? 0 : 10, 0, sold ? 10 : 0, status, 1);
    }

    private RescueCampaign campaign(String id, UrgencyLevel urgency, RescueMode mode,
            Date now, Date locationAt, boolean sharing) {
        return RescueCampaign.restore(id, "seller", "Chiến dịch " + id, "Mô tả",
                RescueReason.OVER_SUPPLY, urgency, mode, Collections.singletonMap("batch", 10D),
                10D, 0D, 0D, new Date(now.getTime() - 1000L),
                new Date(now.getTime() + TimeUnit.DAYS.toMillis(urgency == UrgencyLevel.CRITICAL ? 1 : urgency == UrgencyLevel.HIGH ? 3 : 10)), 10D, 106D,
                10D, 106D, locationAt, sharing, "TP HCM", CampaignStatus.ACTIVE);
    }
}
