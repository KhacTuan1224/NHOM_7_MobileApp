package com.rescuefarm.service.order;

import com.rescuefarm.domain.model.CartItem;
import java.util.List;
import java.util.Locale;

public final class CheckoutPolicy {
    private CheckoutPolicy() { }
    public static void requireBasket(java.util.List<CartItem> items) {
        if (items == null || items.isEmpty()) throw new IllegalArgumentException("Vui lòng chọn sản phẩm.");
        if (items.size() > 20) throw new IllegalArgumentException("Mỗi lần thanh toán tối đa 20 lô hàng.");
        java.util.Set<String> sellers = new java.util.HashSet<>();
        java.util.Set<String> batches = new java.util.HashSet<>();
        for (CartItem item : items) {
            if (!batches.add(item.getBatchId())) throw new IllegalArgumentException("Lô hàng bị trùng.");
            sellers.add(item.getSellerId());
        }
        if (sellers.size() > 5) throw new IllegalArgumentException("Mỗi lần thanh toán tối đa 5 cửa hàng.");
    }
    public static String requireSingleSeller(List<CartItem> items) {
        if (items == null || items.isEmpty()) throw new IllegalArgumentException("Checkout requires selected items");
        if (items.size() > 20) throw new IllegalArgumentException("Checkout supports at most 20 batches");
        String seller = items.get(0).getSellerId();
        for (CartItem item : items) if (!seller.equals(item.getSellerId())) {
            throw new IllegalArgumentException("MULTI_SELLER");
        }
        return seller;
    }
    public static String orderCode(String requestId) {
        String compact = requestId == null ? "" : requestId.replace("-", "").toUpperCase(Locale.US);
        if (compact.length() < 12) throw new IllegalArgumentException("Request id is invalid");
        return "RF-" + compact.substring(0, 12);
    }
}
