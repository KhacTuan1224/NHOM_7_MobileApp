package com.rescuefarm.service.seller;

import com.rescuefarm.domain.enums.CampaignStatus;
import com.rescuefarm.domain.enums.FulfillmentType;
import com.rescuefarm.domain.enums.OrderStatus;
import com.rescuefarm.domain.enums.PostStatus;
import com.rescuefarm.domain.enums.ProductStatus;
import com.rescuefarm.domain.enums.RescueMode;
import com.rescuefarm.domain.enums.RescueReason;
import com.rescuefarm.domain.model.Order;
import com.rescuefarm.domain.model.OrderItem;
import com.rescuefarm.domain.model.Post;
import com.rescuefarm.domain.model.Product;
import com.rescuefarm.domain.model.RescueCampaign;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.text.SimpleDateFormat;
import java.util.Locale;

public final class SellerDashboardAggregator {
    /** Compatibility overload for callers that only have the pre-analytics dashboard inputs. */
    public SellerDashboardSnapshot aggregate(String sellerId, List<Product> products,
            List<RescueCampaign> campaigns, List<Post> posts, List<Order> orders, int promotions) {
        return aggregate(sellerId, products, campaigns, posts, orders,
                Collections.emptyList(), promotions);
    }

    public SellerDashboardSnapshot aggregate(String sellerId, List<Product> products,
            List<RescueCampaign> campaigns, List<Post> posts, List<Order> orders,
            List<OrderItem> deliveredItems, int promotions) {
        return aggregate(sellerId, products, campaigns, posts, orders, deliveredItems, promotions, 30);
    }

    public SellerDashboardSnapshot aggregate(String sellerId, List<Product> products,
            List<RescueCampaign> campaigns, List<Post> posts, List<Order> orders,
            List<OrderItem> deliveredItems, int promotions, int periodDays) {
        String seller = required(sellerId);
        List<Product> ps = filterProducts(seller, products);
        List<RescueCampaign> cs = filterCampaigns(seller, campaigns);
        List<Post> xs = filterPosts(seller, posts);
        List<Order> os = filterOrders(seller, orders);
        long cutoff = periodDays <= 0 ? Long.MIN_VALUE
                : System.currentTimeMillis() - periodDays * 24L * 60L * 60L * 1000L;
        List<Order> periodOrders = new ArrayList<>();
        for (Order order : os) if (time(order.getCreatedAt()) >= cutoff) periodOrders.add(order);
        Map<String, String> productNames = new HashMap<>();
        for (Product product : ps) productNames.put(product.getId(), product.getName());

        int activeProducts = 0, activeCampaigns = 0, completedCampaigns = 0;
        int mobile = 0, pendingPosts = 0, pendingOrders = 0, delivery = 0;
        double revenue = 0, rescued = 0, target = 0;
        double[] trendValues = new double[7];
        long trendEnd = System.currentTimeMillis();
        long trendStart = periodDays > 0 ? trendEnd - periodDays * 24L * 60L * 60L * 1000L
                : trendEnd - 30L * 24L * 60L * 60L * 1000L;
        if (periodDays <= 0 && !periodOrders.isEmpty()) {
            for (Order order : periodOrders) trendStart = Math.min(trendStart, time(order.getCreatedAt()));
        }
        long trendSpan = Math.max(7L, trendEnd - trendStart);
        EnumMap<RescueReason, Double> reasonQuantities = new EnumMap<>(RescueReason.class);
        for (Product product : ps) if (product.getStatus() == ProductStatus.ACTIVE) activeProducts++;
        for (RescueCampaign campaign : cs) {
            if (campaign.getStatus() == CampaignStatus.ACTIVE) activeCampaigns++;
            if (campaign.getStatus() == CampaignStatus.COMPLETED) completedCampaigns++;
            if (campaign.getRescueMode() == RescueMode.MOBILE_POINT) mobile++;
            if (!campaign.isVerifiedKilograms()) continue;
            rescued += campaign.getRescuedQuantity();
            if (campaign.getStatus() == CampaignStatus.ACTIVE
                    || campaign.getStatus() == CampaignStatus.COMPLETED
                    || campaign.getStatus() == CampaignStatus.EXPIRED
                    || campaign.getStatus() == CampaignStatus.STOPPED) target += campaign.getTargetQuantity();
            if (campaign.getRescueReason() != null) {
                RescueReason reason = campaign.getRescueReason();
                reasonQuantities.put(reason, reasonQuantities.getOrDefault(reason, 0D)
                        + campaign.getRescuedQuantity());
            }
        }
        for (Post post : xs) if (post.getStatus() == PostStatus.PENDING_APPROVAL) pendingPosts++;
        for (Order order : periodOrders) {
            if (order.getStatus() != OrderStatus.DELIVERED && order.getStatus() != OrderStatus.CANCELLED)
                pendingOrders++;
            if (order.getFulfillmentType() == FulfillmentType.DELIVERY) delivery++;
            if (order.getStatus() == OrderStatus.DELIVERED)
            {
                double amount = Math.max(0D, order.getSubtotal() - order.getQuantityDiscount());
                revenue += amount;
                int bucket = (int) Math.min(6, Math.max(0, (time(order.getCreatedAt()) - trendStart) * 7 / trendSpan));
                trendValues[bucket] += amount;
            }
        }

        Map<String, Double> productQuantities = new HashMap<>();
        Map<String, Order> orderById = new HashMap<>();
        for (Order order : periodOrders) if (order.getStatus() == OrderStatus.DELIVERED) orderById.put(order.getId(), order);
        if (deliveredItems != null) for (OrderItem item : deliveredItems) {
            if (item == null || item.getProductId() == null) continue;
            if (!orderById.containsKey(item.getOrderId())) continue;
            String productId = item.getProductId();
            productQuantities.put(productId, productQuantities.getOrDefault(productId, 0D) + item.getQuantity());
        }
        List<SellerDashboardSnapshot.RankedMetric> topProducts = rankedProducts(productQuantities, productNames, deliveredItems);
        List<SellerDashboardSnapshot.RankedMetric> revenueTrend = new ArrayList<>();
        SimpleDateFormat trendDate = new SimpleDateFormat("dd/MM", Locale.getDefault());
        for (int index = 0; index < trendValues.length; index++) {
            long bucketStart = trendStart + trendSpan * index / trendValues.length;
            revenueTrend.add(new SellerDashboardSnapshot.RankedMetric(
                    trendDate.format(new Date(bucketStart)), trendValues[index]));
        }
        Map<String, Double> reasons = new HashMap<>();
        for (Map.Entry<RescueReason, Double> entry : reasonQuantities.entrySet())
            reasons.put(entry.getKey().name(), entry.getValue());
        List<SellerDashboardSnapshot.RankedMetric> topReasons = ranked(reasons);

        periodOrders.sort((a, b) -> Long.compare(time(b.getCreatedAt()), time(a.getCreatedAt())));
        List<Order> recent = new ArrayList<>(periodOrders.subList(0, Math.min(5, periodOrders.size())));
        double rescueRate = target <= 0D ? 0D : Math.min(100D, rescued / target * 100D);
        return new SellerDashboardSnapshot(ps.size(), activeProducts, cs.size(), activeCampaigns,
                completedCampaigns, mobile, xs.size(), pendingPosts, os.size(), pendingOrders,
                delivery, Math.max(0, promotions), revenue, rescued, rescueRate, topProducts,
                topReasons, recent, revenueTrend);
    }

    private static List<SellerDashboardSnapshot.RankedMetric> ranked(Map<String, Double> values) {
        List<SellerDashboardSnapshot.RankedMetric> result = new ArrayList<>();
        for (Map.Entry<String, Double> entry : values.entrySet())
            result.add(new SellerDashboardSnapshot.RankedMetric(entry.getKey(), entry.getValue()));
        result.sort(Comparator.comparingDouble(SellerDashboardSnapshot.RankedMetric::getQuantity).reversed());
        if (result.size() > 5) result = new ArrayList<>(result.subList(0, 5));
        return Collections.unmodifiableList(result);
    }
    private static List<SellerDashboardSnapshot.RankedMetric> rankedProducts(Map<String, Double> quantities,
            Map<String, String> names, List<OrderItem> items) {
        Map<String, String> labels = new HashMap<>();
        if (items != null) for (OrderItem item : items) {
            if (item == null || item.getProductId() == null || labels.containsKey(item.getProductId())) continue;
            String label = names.getOrDefault(item.getProductId(), item.getProductNameSnapshot());
            labels.put(item.getProductId(), label == null || label.trim().isEmpty() ? "Sản phẩm đã ẩn" : label);
        }
        List<SellerDashboardSnapshot.RankedMetric> result = new ArrayList<>();
        for (Map.Entry<String, Double> entry : quantities.entrySet())
            result.add(new SellerDashboardSnapshot.RankedMetric(labels.getOrDefault(entry.getKey(), "Sản phẩm đã ẩn"), entry.getValue()));
        result.sort(Comparator.comparingDouble(SellerDashboardSnapshot.RankedMetric::getQuantity).reversed());
        if (result.size() > 5) result = new ArrayList<>(result.subList(0, 5));
        return Collections.unmodifiableList(result);
    }
    private List<Product> filterProducts(String seller, List<Product> input) {
        List<Product> result = new ArrayList<>();
        if (input != null) for (Product value : input) if (seller.equals(value.getSellerId())) result.add(value);
        return result;
    }
    private List<RescueCampaign> filterCampaigns(String seller, List<RescueCampaign> input) {
        List<RescueCampaign> result = new ArrayList<>();
        if (input != null) for (RescueCampaign value : input) if (seller.equals(value.getSellerId())) result.add(value);
        return result;
    }
    private List<Post> filterPosts(String seller, List<Post> input) {
        List<Post> result = new ArrayList<>();
        if (input != null) for (Post value : input) if (seller.equals(value.getSellerId())) result.add(value);
        return result;
    }
    private List<Order> filterOrders(String seller, List<Order> input) {
        List<Order> result = new ArrayList<>();
        if (input != null) for (Order value : input) if (seller.equals(value.getSellerId())) result.add(value);
        return result;
    }
    private static String required(String value) {
        if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException("Seller id is required");
        return value.trim();
    }
    private static long time(Date value) { return value == null ? 0 : value.getTime(); }
}
