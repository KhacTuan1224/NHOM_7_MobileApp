package com.rescuefarm.service.cart;

import com.rescuefarm.domain.model.CartItem;
import java.util.*;

/** Removes purchased quantities while preserving other items and concurrent additions. */
public final class CheckoutCartCleanup {
    private CheckoutCartCleanup() { }
    public static List<Map<String, Object>> remaining(Object raw, List<CartItem> purchased) {
        Map<String, Double> quantities = new HashMap<>();
        for (CartItem item : purchased) quantities.merge(item.getId(), item.getQuantity(), Double::sum);
        List<Map<String, Object>> result = new ArrayList<>();
        if (!(raw instanceof List<?>)) return result;
        for (Object value : (List<?>) raw) {
            if (!(value instanceof Map<?, ?>)) throw new IllegalArgumentException("Cart item is invalid");
            Map<String, Object> item = new HashMap<>();
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet())
                if (entry.getKey() instanceof String) item.put((String) entry.getKey(), entry.getValue());
            Double bought = quantities.get(item.get("id"));
            if (bought != null) {
                Object qty = item.get("quantity");
                if (!(qty instanceof Number) || ((Number) qty).doubleValue() < bought)
                    throw new IllegalStateException("QUOTE_CHANGED");
                double left = ((Number) qty).doubleValue() - bought;
                if (left <= 0.000001) continue;
                item.put("quantity", left);
            }
            result.add(item);
        }
        return result;
    }
}
