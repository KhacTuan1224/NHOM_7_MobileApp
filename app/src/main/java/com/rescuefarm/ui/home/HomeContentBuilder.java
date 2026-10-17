package com.rescuefarm.ui.home;

import com.rescuefarm.domain.enums.CampaignStatus;
import com.rescuefarm.domain.enums.RescueMode;
import com.rescuefarm.domain.enums.UrgencyLevel;
import com.rescuefarm.domain.model.Banner;
import com.rescuefarm.domain.model.Category;
import com.rescuefarm.domain.model.Product;
import com.rescuefarm.domain.model.ProductBatch;
import com.rescuefarm.domain.model.RescueCampaign;
import com.rescuefarm.service.location.CampaignLocationService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public final class HomeContentBuilder {
    private static final long ENDING_SOON_MILLIS = TimeUnit.DAYS.toMillis(3);
    private final CampaignLocationService locationService = new CampaignLocationService();

    public HomeViewState build(List<Banner> bannerSource, List<Product> productSource,
            List<RescueCampaign> campaignSource, List<Category> categorySource,
            Double latitude, Double longitude, Date now, HomeViewState.LocationState locationState,
            boolean refreshing, String message) {
        return build(bannerSource, productSource, campaignSource, categorySource, latitude,
                longitude, now, locationState, refreshing, message,
                HomeViewState.DataFreshness.STALE);
    }

    public HomeViewState build(List<Banner> bannerSource, List<Product> productSource,
            List<RescueCampaign> campaignSource, List<Category> categorySource,
            Double latitude, Double longitude, Date now, HomeViewState.LocationState locationState,
            boolean refreshing, String message, HomeViewState.DataFreshness freshness) {
        return build(bannerSource, productSource, campaignSource, categorySource,
                java.util.Collections.emptyMap(), latitude, longitude, now, locationState,
                refreshing, message, freshness);
    }

    public HomeViewState build(List<Banner> bannerSource, List<Product> productSource,
            List<RescueCampaign> campaignSource, List<Category> categorySource,
            Map<String, List<ProductBatch>> batchesByProduct, Double latitude, Double longitude,
            Date now, HomeViewState.LocationState locationState, boolean refreshing,
            String message, HomeViewState.DataFreshness freshness) {
        return build(bannerSource, productSource, campaignSource, categorySource,
                batchesByProduct, latitude, longitude, now, locationState, refreshing,
                message, freshness, "");
    }

    public HomeViewState build(List<Banner> bannerSource, List<Product> productSource,
            List<RescueCampaign> campaignSource, List<Category> categorySource,
            Map<String, List<ProductBatch>> batchesByProduct, Double latitude, Double longitude,
            Date now, HomeViewState.LocationState locationState, boolean refreshing,
            String message, HomeViewState.DataFreshness freshness, String locationLabel) {
        Date safeNow = now == null ? new Date() : now;
        List<Banner> banners = new ArrayList<>();
        if (bannerSource != null) for (Banner value : bannerSource) {
            if (value != null && value.isVisibleAt(safeNow)) banners.add(value);
        }
        banners.sort(Comparator.comparingInt(Banner::getDisplayOrder).thenComparing(Banner::getId));

        Map<String, List<ProductBatch>> publicBatches = new LinkedHashMap<>();
        if (productSource != null && batchesByProduct != null) for (Product product : productSource)
            if (product != null && product.isAvailable()) publicBatches.put(product.getId(), batchesByProduct.get(product.getId()));
        List<RescueCampaign> active = new ArrayList<>();
        if (campaignSource != null) for (RescueCampaign value : campaignSource) {
            if (value != null && value.getStatus() == CampaignStatus.ACTIVE
                    && value.getEndDate() != null && value.getEndDate().after(safeNow)
                    && (value.getStartDate() == null || !value.getStartDate().after(safeNow))
                    && value.calculateRemainingQuantity() > 0D) {
                refreshUrgency(value, publicBatches, safeNow);
                active.add(value);
            }
        }
        active.sort(campaignComparator());
        List<RescueCampaign> critical = new ArrayList<>();
        List<RescueCampaign> mobile = new ArrayList<>();
        List<RescueCampaign> fixed = new ArrayList<>();
        List<RescueCampaign> endingSoon = new ArrayList<>();
        Map<String, Double> distances = new LinkedHashMap<>();
        for (RescueCampaign value : active) {
            if (value.getUrgencyLevel() == UrgencyLevel.CRITICAL) critical.add(value);
            long remaining = value.getEndDate().getTime() - safeNow.getTime();
            if (remaining > 0L && remaining <= ENDING_SOON_MILLIS) endingSoon.add(value);
            if (latitude == null || longitude == null) continue;
            if (value.getRescueMode() == RescueMode.MOBILE_POINT) {
                double distance = locationService.distanceToCampaign(latitude, longitude, value, safeNow);
                if (Double.isFinite(distance)) { mobile.add(value); distances.put(value.getId(), distance); }
            } else {
                double distance = locationService.distanceKilometers(latitude, longitude,
                        value.getLatitude(), value.getLongitude());
                fixed.add(value); distances.put(value.getId(), distance);
            }
        }
        Comparator<RescueCampaign> nearest = Comparator.comparingDouble(
                value -> distances.getOrDefault(value.getId(), Double.MAX_VALUE));
        mobile.sort(nearest); fixed.sort(nearest);
        endingSoon.sort(Comparator.comparing(RescueCampaign::getEndDate));

        List<Product> valueProducts = new ArrayList<>();
        if (productSource != null) for (Product value : productSource) {
            if (value != null && value.isAvailable() && value.calculateDiscountPercent() > 0D) {
                valueProducts.add(value);
            }
        }
        valueProducts.sort(Comparator.comparingDouble(Product::calculateDiscountPercent).reversed());

        List<ExpiringProduct> nearExpiry = new ArrayList<>();
        long expiryLimit = safeNow.getTime() + TimeUnit.DAYS.toMillis(3);
        if (productSource != null && batchesByProduct != null) for (Product product : productSource) {
            if (product == null || !product.isAvailable()) continue;
            List<ProductBatch> batches = batchesByProduct.get(product.getId());
            ProductBatch earliest = null;
            if (batches != null) for (ProductBatch batch : batches) {
                if (batch != null && product.getId().equals(batch.getProductId()) && batch.isSellable(safeNow)
                        && batch.getExpiryDate() != null && batch.getExpiryDate().getTime() <= expiryLimit
                        && (earliest == null || batch.getExpiryDate().before(earliest.getExpiryDate()))) earliest = batch;
            }
            if (earliest != null) nearExpiry.add(new ExpiringProduct(product, earliest));
        }
        nearExpiry.sort(Comparator.comparing(value -> value.getBatch().getExpiryDate()));

        List<Category> categories = new ArrayList<>();
        if (categorySource != null) for (Category value : categorySource) {
            if (value != null && value.isActive()) categories.add(value);
        }
        categories.sort(Comparator.comparingInt(Category::getDisplayOrder).thenComparing(Category::getName));
        return new HomeViewState(banners, critical, mobile, fixed, endingSoon, valueProducts,
                active, categories, distances, locationState, refreshing, message, freshness,
                nearExpiry, locationLabel);
    }

    public List<VolumeOffer> volumeOffers(List<Product> products, Map<String, com.rescuefarm.domain.model.Promotion> promotions,
            Map<String,List<ProductBatch>> batches, List<RescueCampaign> campaigns, Date now) {
        List<VolumeOffer> offers=new ArrayList<>(); if(products==null||promotions==null||batches==null) return offers;
        Map<String,RescueCampaign> byId=new LinkedHashMap<>(); if(campaigns!=null) for(RescueCampaign c:campaigns) byId.put(c.getId(),c);
        for(Product p:products) {
            if(p==null||!p.isAvailable()) continue;
            com.rescuefarm.domain.model.Promotion promo=promotions.get(p.getId());
            if(promo==null||!promo.isValid(now)||!p.getSellerId().equals(promo.getSellerId())||!p.getId().equals(promo.getProductId())) continue;
            List<ProductBatch> stock=batches.get(p.getId()); if(stock==null) continue;
            VolumeOffer best=null;
            for(ProductBatch batch:stock) {
                if(batch==null||!batch.isSellable(now)) continue;
                double available=batch.getAvailableQuantity(); String cid=batch.getActiveCampaignId();
                if(cid!=null&&!cid.isEmpty()) {
                    RescueCampaign c=byId.get(cid); if(!com.rescuefarm.service.campaign.CampaignTimeFormatter.canPurchase(c,now)) continue;
                    available=Math.min(available,c.calculateRemainingForBatch(batch.getId()));
                }
                for(Map.Entry<String,Double> tier:promo.getQuantityDiscountTiers().entrySet()) {
                    double threshold; try{threshold=Double.parseDouble(tier.getKey());}catch(NumberFormatException e){continue;}
                    if(!Double.isFinite(threshold)||threshold<=0||available+.000001<threshold||tier.getValue()<=0) continue;
                    double percent=promo.calculateDiscount(threshold);
                    double price=new com.rescuefarm.service.pricing.PricingService().calculatePrice(p.getOriginalPrice(),p.getRescuePrice(),threshold,promo,now).getFinalUnitPrice();
                    VolumeOffer candidate=new VolumeOffer(p,batch.getId(),cid,threshold,percent,price);
                    if(best==null||threshold<best.threshold||(threshold==best.threshold&&batch.getId().compareTo(best.batchId)<0)) best=candidate;
                }
            }
            if(best!=null) offers.add(best);
        }
        offers.sort(java.util.Comparator.comparingDouble((VolumeOffer o)->o.percent).reversed().thenComparing(o->o.product.getId()));
        return offers;
    }

    private void refreshUrgency(RescueCampaign campaign,
            Map<String, List<ProductBatch>> batchesByProduct, Date now) {
        if (batchesByProduct == null) batchesByProduct = java.util.Collections.emptyMap();
        Date earliest = null;
        for (List<ProductBatch> values : batchesByProduct.values()) if (values != null) {
            for (ProductBatch batch : values) if (batch != null
                    && campaign.getBatchTargets().containsKey(batch.getId())
                    && batch.isSellable(now)
                    && batch.getExpiryDate() != null
                    && (earliest == null || batch.getExpiryDate().before(earliest))) {
                earliest = batch.getExpiryDate();
            }
        }
        if (campaign.getEndDate() != null) campaign.calculateUrgency(now, earliest);
    }

    private static Comparator<RescueCampaign> campaignComparator() {
        return Comparator.comparingInt(HomeContentBuilder::urgencyRank)
                .thenComparing(RescueCampaign::getEndDate)
                .thenComparing(RescueCampaign::getTitle);
    }
    private static int urgencyRank(RescueCampaign value) {
        return value.getUrgencyLevel() == UrgencyLevel.CRITICAL ? 0
                : value.getUrgencyLevel() == UrgencyLevel.HIGH ? 1 : 2;
    }
}
