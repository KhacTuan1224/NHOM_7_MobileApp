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
        String seller = required(sellerId);
        List<Product> ps = filterProducts(seller, products);
        List<RescueCampaign> cs = filterCampaigns(seller, campaigns);
        List<Post> xs = filterPosts(seller, posts);
        List<Order> os = filterOrders(seller, orders);
        Map<String, String> productNames = new HashMap<>();
        for (Product product : ps) productNames.put(product.getId(), product.getName());

        int activeProducts = 0, activeCampaigns = 0, completedCampaigns = 0;
        int mobile = 0, pendingPosts = 0, pendingOrders = 0, delivery = 0;
        double revenue = 0, rescued = 0, target = 0;
        EnumMap<RescueReason, Double> reasonQuantities = new EnumMap<>(RescueReason.class);
        for (Product product : ps) if (product.getStatus() == ProductStatus.ACTIVE) activeProducts++;
        for (RescueCampaign campaign : cs) {
            if (campaign.getStatus() == CampaignStatus.ACTIVE) activeCampaigns++;
            if (campaign.getStatus() == CampaignStatus.COMPLETED) completedCampaigns++;
            if (campaign.getRescueMode() == RescueMode.MOBILE_POINT) mobile++;
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
        for (Order order : os) {
            if (order.getStatus() != OrderStatus.DELIVERED && order.getStatus() != OrderStatus.CANCELLED)
                pendingOrders++;
            if (order.getFulfillmentType() == FulfillmentType.DELIVERY) delivery++;
            if (order.getStatus() == OrderStatus.DELIVERED)
                revenue += Math.max(0D, order.getSubtotal() - order.getQuantityDiscount());
        }

        Map<String, Double> productQuantities = new HashMap<>();
        if (deliveredItems != null) for (OrderItem item : deliveredItems) {
            if (item == null || item.getProductId() == null) continue;
            String name = productNames.getOrDefault(item.getProductId(), item.getProductNameSnapshot());
            if (name == null || name.trim().isEmpty()) name = "Sản phẩm đã ẩn";
            productQuantities.put(name, productQuantities.getOrDefault(name, 0D) + item.getQuantity());
        }
        List<SellerDashboardSnapshot.RankedMetric> topProducts = ranked(productQuantities);
        Map<String, Double> reasons = new HashMap<>();
        for (Map.Entry<RescueReason, Double> entry : reasonQuantities.entrySet())
            reasons.put(entry.getKey().name(), entry.getValue());
        List<SellerDashboardSnapshot.RankedMetric> topReasons = ranked(reasons);

        os.sort((a, b) -> Long.compare(time(b.getCreatedAt()), time(a.getCreatedAt())));
        List<Order> recent = new ArrayList<>(os.subList(0, Math.min(5, os.size())));
        double rescueRate = target <= 0D ? 0D : Math.min(100D, rescued / target * 100D);
        return new SellerDashboardSnapshot(ps.size(), activeProducts, cs.size(), activeCampaigns,
                completedCampaigns, mobile, xs.size(), pendingPosts, os.size(), pendingOrders,
                delivery, Math.max(0, promotions), revenue, rescued, rescueRate, topProducts,
                topReasons, recent);
    }

    private static List<SellerDashboardSnapshot.RankedMetric> ranked(Map<String, Double> values) {
        List<SellerDashboardSnapshot.RankedMetric> result = new ArrayList<>();
        for (Map.Entry<String, Double> entry : values.entrySet())
            result.add(new SellerDashboardSnapshot.RankedMetric(entry.getKey(), entry.getValue()));
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
