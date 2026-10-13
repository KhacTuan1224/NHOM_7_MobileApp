package com.rescuefarm.ui.home;

import com.rescuefarm.domain.enums.CampaignStatus;
import com.rescuefarm.domain.enums.RescueMode;
import com.rescuefarm.domain.enums.UrgencyLevel;
import com.rescuefarm.domain.model.Product;
import com.rescuefarm.domain.model.ProductBatch;
import com.rescuefarm.domain.model.RescueCampaign;
import com.rescuefarm.service.location.CampaignLocationService;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Collections;

public final class DiscoveryEngine {
    private final CampaignLocationService locationService = new CampaignLocationService();

    public List<DiscoveryResult> search(List<Product> products, List<RescueCampaign> campaigns,
            DiscoveryQuery query, Double latitude, Double longitude, Date now) {
        return search(products, campaigns, Collections.emptyMap(), query, latitude, longitude, now);
    }

    public List<DiscoveryResult> search(List<Product> products, List<RescueCampaign> campaigns,
            Map<String, List<ProductBatch>> batchesByProduct, DiscoveryQuery query,
            Double latitude, Double longitude, Date now) {
        return search(products, campaigns, batchesByProduct, Collections.emptyMap(), query,
                latitude, longitude, now);
    }

    public List<DiscoveryResult> search(List<Product> products, List<RescueCampaign> campaigns,
            Map<String, List<ProductBatch>> batchesByProduct, Map<String, double[]> sellerCoordinates,
            DiscoveryQuery query, Double latitude, Double longitude, Date now) {
        DiscoveryQuery q = query == null ? DiscoveryQuery.empty() : query;
        Date at = now == null ? new Date() : now;
        Map<String, List<ProductBatch>> batches = batchesByProduct == null
                ? Collections.emptyMap() : batchesByProduct;
        Map<String, RescueCampaign> campaignById = new HashMap<>();
        if (campaigns != null) for (RescueCampaign campaign : campaigns) {
            if (campaign != null && campaign.getId() != null) {
                refreshUrgency(campaign, batches, at);
                campaignById.put(campaign.getId(), campaign);
            }
        }

        List<DiscoveryResult> result = new ArrayList<>();
        if (products != null) for (Product product : products) {
            if (product == null || !matchesProductTextAndPrice(product, q)) continue;
            List<ProductBatch> productBatches = batches.get(product.getId());
            ProductBatch candidate = selectBatch(product, productBatches, q, at, campaignById);
            boolean needsBatch = q.needsBatchData();
            if (needsBatch && candidate == null) continue;
            if (needsBatch && q.getMinStock() > 0D
                    && available(productBatches, at, q, campaignById) < q.getMinStock()) continue;

            RescueCampaign matchedCampaign = candidate == null ? null
                    : campaignById.get(candidate.getActiveCampaignId());
            if (!q.getProvince().isEmpty() && !contains(product.getProvince(), q.getProvince())
                    && (matchedCampaign == null || !contains(matchedCampaign.getLocationName(), q.getProvince()))) continue;
            double distance = matchedCampaign == null
                    ? productDistance(product, sellerCoordinates, latitude, longitude)
                    : distance(matchedCampaign, latitude, longitude, at);
            boolean needsDistance = q.getSort() == DiscoveryQuery.Sort.DISTANCE || q.getRadiusKm() > 0D;
            if (needsDistance && !Double.isFinite(distance)) continue;
            if (q.getRadiusKm() > 0D && distance > q.getRadiusKm()) continue;
            result.add(DiscoveryResult.product(product, distance,
                    candidate == null ? "" : candidate.getId(), matchedCampaign,
                    hasCampaignFilter(q) && matchedCampaign != null ? matchedCampaign.getId() : ""));
        }

        if (campaigns != null) for (RescueCampaign campaign : campaigns) {
            if (!matchesCampaign(campaign, q, at)
                    || !campaignHasMatchingStock(campaign, q, batches, at)) continue;
            double distance = distance(campaign, latitude, longitude, at);
            if ((q.getSort() == DiscoveryQuery.Sort.DISTANCE && !Double.isFinite(distance))
                    || (q.getRadiusKm() > 0D && (!Double.isFinite(distance)
                    || distance > q.getRadiusKm()))) continue;
            result.add(DiscoveryResult.campaign(campaign, distance));
        }
        result.sort(comparator(q.getSort(), batches));
        return result;
    }

    private boolean matchesProductTextAndPrice(Product value, DiscoveryQuery query) {
        if (!value.isAvailable()) return false;
        if (!query.getCategoryId().isEmpty() && !query.getCategoryId().equals(value.getCategoryId())) return false;
        if (value.getRescuePrice() < query.getMinPrice() || value.getRescuePrice() > query.getMaxPrice()) return false;
        return query.getText().isEmpty() || contains(value.getName(), query.getText())
                || contains(value.getDescription(), query.getText())
                || contains(value.getOrigin(), query.getText())
                || contains(value.getProvince(), query.getText());
    }

    private ProductBatch selectBatch(Product product, List<ProductBatch> values, DiscoveryQuery q, Date now,
            Map<String, RescueCampaign> campaigns) {
        if (values == null) return null;
        List<ProductBatch> candidates = new ArrayList<>();
        long expiryLimit = q.getExpiryWithinDays() == 0 ? Long.MAX_VALUE
                : now.getTime() + q.getExpiryWithinDays() * 86400000L;
        for (ProductBatch batch : values) {
            if (batch == null || !batch.isSellable(now)) continue;
            if (q.getMinStock() > 0D && batch.getAvailableQuantity() < q.getMinStock()) continue;
            if (q.getExpiryWithinDays() > 0 && (batch.getExpiryDate() == null
                    || batch.getExpiryDate().getTime() > expiryLimit)) continue;
            String campaignId = clean(batch.getActiveCampaignId());
            RescueCampaign campaign = campaignId.isEmpty() ? null : campaigns.get(campaignId);
            if (hasCampaignFilter(q) && (campaign == null || !matchesCampaign(campaign, q, now))) continue;
            if (!q.getProvince().isEmpty() && !contains(product.getProvince(), q.getProvince())
                    && (campaign == null || !contains(campaign.getLocationName(), q.getProvince()))) continue;
            candidates.add(batch);
        }
        candidates.sort(Comparator
                .comparingInt((ProductBatch batch) -> urgencyRank(campaigns.get(batch.getActiveCampaignId())))
                .thenComparingLong(batch -> batch.getExpiryDate() == null
                        ? Long.MAX_VALUE : batch.getExpiryDate().getTime())
                .thenComparing(ProductBatch::getId));
        return candidates.isEmpty() ? null : candidates.get(0);
    }

    private double available(List<ProductBatch> values, Date now, DiscoveryQuery q,
            Map<String, RescueCampaign> campaigns) {
        if (values == null) return 0D;
        double total = 0D;
        long expiryLimit = q.getExpiryWithinDays() == 0 ? Long.MAX_VALUE
                : now.getTime() + q.getExpiryWithinDays() * 86400000L;
        for (ProductBatch batch : values) {
            if (batch == null || !batch.isSellable(now)) continue;
            if (q.getExpiryWithinDays() > 0 && (batch.getExpiryDate() == null
                    || batch.getExpiryDate().getTime() > expiryLimit)) continue;
            RescueCampaign campaign = campaigns.get(clean(batch.getActiveCampaignId()));
            if (hasCampaignFilter(q) && (campaign == null || !matchesCampaign(campaign, q, now))) continue;
            total += batch.getAvailableQuantity();
        }
        return total;
    }

    private void refreshUrgency(RescueCampaign campaign, Map<String, List<ProductBatch>> batches, Date now) {
        Date earliest = null;
        for (List<ProductBatch> values : batches.values()) if (values != null) {
            for (ProductBatch batch : values) if (batch != null
                    && campaign.getBatchTargets().containsKey(batch.getId())
                    && batch.isSellable(now)
                    && batch.getExpiryDate() != null
                    && (earliest == null || batch.getExpiryDate().before(earliest))) {
                earliest = batch.getExpiryDate();
            }
        }
        if (earliest != null && campaign.getEndDate() != null) campaign.calculateUrgency(now, earliest);
    }

    private boolean matchesCampaign(RescueCampaign value, DiscoveryQuery q, Date now) {
        if (value == null || value.getStatus() != CampaignStatus.ACTIVE || value.getEndDate() == null
                || !value.getEndDate().after(now) || value.calculateRemainingQuantity() <= 0D) return false;
        if (q.getReason() != null && q.getReason() != value.getRescueReason()) return false;
        if (q.getUrgency() != null && q.getUrgency() != value.getUrgencyLevel()) return false;
        if (q.getMode() != null && q.getMode() != value.getRescueMode()) return false;
        if (!q.getProvince().isEmpty() && !contains(value.getLocationName(), q.getProvince())) return false;
        return q.getText().isEmpty() || contains(value.getTitle(), q.getText())
                || contains(value.getDescription(), q.getText())
                || contains(value.getLocationName(), q.getText())
                || contains(value.getRescueReason().name(), q.getText());
    }

    private boolean campaignHasMatchingStock(RescueCampaign campaign, DiscoveryQuery q,
            Map<String, List<ProductBatch>> byProduct, Date now) {
        long expiryLimit = q.getExpiryWithinDays() == 0 ? Long.MAX_VALUE
                : now.getTime() + q.getExpiryWithinDays() * 86400000L;
        double available = 0D;
        boolean expiryMatch = q.getExpiryWithinDays() == 0;
        for (List<ProductBatch> values : byProduct.values()) if (values != null) {
            for (ProductBatch batch : values) if (batch != null
                    && campaign.getBatchTargets().containsKey(batch.getId()) && batch.isSellable(now)) {
                available += batch.getAvailableQuantity();
                if (batch.getExpiryDate() != null && batch.getExpiryDate().getTime() <= expiryLimit) {
                    expiryMatch = true;
                }
            }
        }
        return available > 0D && available >= q.getMinStock() && expiryMatch;
    }

    private boolean hasCampaignFilter(DiscoveryQuery q) {
        return q.getReason() != null || q.getUrgency() != null || q.getMode() != null;
    }

    private double productDistance(Product product, Map<String, double[]> coordinates,
            Double latitude, Double longitude) {
        if (latitude == null || longitude == null || coordinates == null) return Double.NaN;
        double[] seller = coordinates.get(product.getSellerId());
        return seller == null || seller.length < 2 ? Double.NaN
                : locationService.distanceKilometers(latitude, longitude, seller[0], seller[1]);
    }

    private double distance(RescueCampaign campaign, Double latitude, Double longitude, Date now) {
        if (latitude == null || longitude == null) return Double.NaN;
        if (campaign.getRescueMode() == RescueMode.MOBILE_POINT) {
            return locationService.distanceToCampaign(latitude, longitude, campaign, now);
        }
        return locationService.distanceKilometers(latitude, longitude,
                campaign.getLatitude(), campaign.getLongitude());
    }

    private Comparator<DiscoveryResult> comparator(DiscoveryQuery.Sort sort,
            Map<String, List<ProductBatch>> batches) {
        Comparator<DiscoveryResult> urgent = Comparator.comparingInt(this::urgencyRank);
        if (sort == DiscoveryQuery.Sort.DISCOUNT_DESC) return Comparator.comparingDouble(this::discount)
                .reversed().thenComparing(urgent).thenComparing(this::stableTitle, String.CASE_INSENSITIVE_ORDER);
        if (sort == DiscoveryQuery.Sort.PRICE_ASC) return Comparator.comparingDouble(this::price)
                .thenComparing(urgent).thenComparing(this::stableTitle, String.CASE_INSENSITIVE_ORDER);
        if (sort == DiscoveryQuery.Sort.DISTANCE) return Comparator.comparingDouble(this::distanceOrMax)
                .thenComparing(urgent).thenComparing(this::stableTitle, String.CASE_INSENSITIVE_ORDER);
        if (sort == DiscoveryQuery.Sort.URGENCY) return urgent.thenComparing(this::stableTitle,
                String.CASE_INSENSITIVE_ORDER);
        if (sort == DiscoveryQuery.Sort.NEAREST_DEADLINE) return Comparator
                .comparingLong((DiscoveryResult value) -> deadline(value, batches)).thenComparing(urgent)
                .thenComparing(this::stableTitle, String.CASE_INSENSITIVE_ORDER);
        if (sort == DiscoveryQuery.Sort.BEST_SELLING) return Comparator
                .comparingDouble((DiscoveryResult value) -> sold(value, batches)).reversed()
                .thenComparing(urgent).thenComparing(this::stableTitle, String.CASE_INSENSITIVE_ORDER);
        return urgent.thenComparingInt(value -> value.getType() == DiscoveryResult.Type.CAMPAIGN ? 0 : 1)
                .thenComparing(this::stableTitle, String.CASE_INSENSITIVE_ORDER);
    }

    private int urgencyRank(DiscoveryResult value) { return urgencyRank(value.getCampaign()); }
    private int urgencyRank(RescueCampaign campaign) {
        if (campaign == null) return 3;
        UrgencyLevel level = campaign.getUrgencyLevel();
        return level == UrgencyLevel.CRITICAL ? 0 : level == UrgencyLevel.HIGH ? 1 : 2;
    }
    private double discount(DiscoveryResult value) {
        return value.getProduct() == null ? -1D : value.getProduct().calculateDiscountPercent();
    }
    private double price(DiscoveryResult value) {
        return value.getProduct() == null ? Double.MAX_VALUE : value.getProduct().getRescuePrice();
    }
    private double distanceOrMax(DiscoveryResult value) {
        return Double.isFinite(value.getDistanceKilometers()) ? value.getDistanceKilometers() : Double.MAX_VALUE;
    }
    private long deadline(DiscoveryResult value, Map<String, List<ProductBatch>> batches) {
        if (value.getCampaign() != null) {
            long earliest = value.getCampaign().getEndDate() == null
                    ? Long.MAX_VALUE : value.getCampaign().getEndDate().getTime();
            for (List<ProductBatch> values : batches.values()) if (values != null) {
                for (ProductBatch batch : values) if (batch != null
                        && value.getCampaign().getBatchTargets().containsKey(batch.getId())
                        && batch.getAvailableQuantity() > 0D && batch.getExpiryDate() != null) {
                    earliest = Math.min(earliest, batch.getExpiryDate().getTime());
                }
            }
            return earliest;
        }
        long earliest = Long.MAX_VALUE;
        List<ProductBatch> values = batches.get(value.getProduct().getId());
        if (values != null) for (ProductBatch batch : values) if (batch != null
                && batch.getExpiryDate() != null && batch.getAvailableQuantity() > 0D) {
            earliest = Math.min(earliest, batch.getExpiryDate().getTime());
        }
        return earliest;
    }
    private double sold(DiscoveryResult value, Map<String, List<ProductBatch>> batches) {
        if (value.getProduct() == null) return -1D;
        double total = 0D;
        List<ProductBatch> values = batches.get(value.getProduct().getId());
        if (values != null) for (ProductBatch batch : values) if (batch != null) total += batch.getSoldQuantity();
        return total;
    }
    private String stableTitle(DiscoveryResult value) {
        return value.getProduct() == null ? value.getCampaign().getTitle() : value.getProduct().getName();
    }
    private static String clean(String value) { return value == null ? "" : value.trim(); }
    private static boolean contains(String source, String expected) { return normalize(source).contains(normalize(expected)); }
    private static String normalize(String value) {
        String clean = value == null ? "" : value.toLowerCase(Locale.ROOT).trim();
        return Normalizer.normalize(clean, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
    }
}
