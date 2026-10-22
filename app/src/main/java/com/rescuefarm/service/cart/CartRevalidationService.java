package com.rescuefarm.service.cart;

import com.rescuefarm.domain.model.CartItem;
import com.rescuefarm.domain.model.Product;
import com.rescuefarm.domain.model.ProductBatch;
import com.rescuefarm.service.pricing.PriceBreakdown;
import com.rescuefarm.service.pricing.PricingService;
import java.util.Date;

public final class CartRevalidationService {
    private final PricingService pricingService;
    public CartRevalidationService(PricingService pricingService) { this.pricingService = pricingService; }

    public Result revalidate(CartItem item, Product product, ProductBatch batch, Date now) {
        if (item == null || product == null || batch == null || now == null
                || !item.getProductId().equals(product.getId())
                || !item.getBatchId().equals(batch.getId())
                || !batch.getProductId().equals(product.getId())) {
            return Result.invalid("Product/batch không khớp.");
        }
        if (!batch.isSellable(now)) return Result.invalid("Batch đã hết hạn hoặc không thể bán.");
        if (!item.validateStock(batch, now)) return Result.invalid("Không đủ tồn kho khả dụng.");
        try {
            PriceBreakdown quote = pricingService.calculatePrice(product.getOriginalPrice(),
                    product.getRescuePrice(), item.getQuantity());
            return Result.valid(quote, Math.abs(quote.getFinalUnitPrice() - item.getUnitPrice()) > 0.000001D);
        } catch (IllegalArgumentException error) { return Result.invalid(error.getMessage()); }
    }

    public static final class Result {
        private final boolean valid; private final boolean priceChanged;
        private final PriceBreakdown quote; private final String message;
        private Result(boolean valid, boolean priceChanged, PriceBreakdown quote, String message) {
            this.valid = valid; this.priceChanged = priceChanged; this.quote = quote; this.message = message;
        }
        public static Result valid(PriceBreakdown quote, boolean priceChanged) {
            return new Result(true, priceChanged, quote, "");
        }
        public static Result invalid(String message) { return new Result(false, false, null, message); }
        public boolean isValid() { return valid; }
        public boolean isPriceChanged() { return priceChanged; }
        public PriceBreakdown getQuote() { return quote; }
        public String getMessage() { return message; }
    }
}
