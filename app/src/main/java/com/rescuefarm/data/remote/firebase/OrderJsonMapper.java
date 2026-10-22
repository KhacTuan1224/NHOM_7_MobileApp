package com.rescuefarm.data.remote.firebase;

import com.rescuefarm.domain.enums.*;
import com.rescuefarm.domain.model.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/** Maps committed backend snapshots; timestamps are epoch milliseconds. */
final class OrderJsonMapper {
    private OrderJsonMapper() { }
    static Order order(JSONObject d) {
        if (d == null) throw new IllegalArgumentException("Máy chủ chưa trả về đơn hàng.");
        List<OrderItem> items = new ArrayList<>(); JSONArray lines = d.optJSONArray("items");
        if (lines != null) for (int i = 0; i < lines.length(); i++) {
            JSONObject row = lines.optJSONObject(i); if (row == null) continue;
            items.add(OrderItem.snapshot(row.optString("id"), d.optString("id"), row.optString("productId"),
                    row.optString("batchId"), row.optString("productNameSnapshot"), row.optString("productImageSnapshot"),
                    row.optString("unitSnapshot"), row.optDouble("originalPriceSnapshot"), row.optDouble("rescuePriceSnapshot"),
                    row.optDouble("finalPriceSnapshot"), row.optDouble("quantity")));
        }
        return Order.restore(d.optString("id"), OrderOwnerType.valueOf(d.optString("ownerType")),
                d.optString("ownerId"), d.optString("orderCode"), d.optString("sellerId"), d.optString("campaignId"),
                FulfillmentType.valueOf(d.optString("fulfillmentType")), d.optString("receiverName"),
                d.optString("receiverPhone"), d.optString("receiverAddress"), d.optDouble("receiverLatitude"),
                d.optDouble("receiverLongitude"), d.optDouble("subtotal"), d.optDouble("quantityDiscount"),
                d.optDouble("shippingFee"), d.optDouble("totalAmount"), PaymentMethod.valueOf(d.optString("paymentMethod")),
                PaymentStatus.valueOf(d.optString("paymentStatus")), OrderStatus.valueOf(d.optString("status")),
                d.optString("note"), date(d, "createdAt"), date(d, "updatedAt"), items).withVolumeDiscounts(d.optDouble("sellerVolumeDiscount"), d.optDouble("platformVolumeDiscount"))
                .withExpiryReview(d.optBoolean("expiryReviewRequired"), d.optString("cancellationReason"));
    }
    static Payment payment(JSONObject d) {
        if (d == null) throw new IllegalArgumentException("Không tải được thanh toán của đơn.");
        Payment p = Payment.restore(d.optString("id"), d.optString("orderId"), PaymentMethod.valueOf(d.optString("method")),
                PaymentStatus.valueOf(d.optString("status")), d.optDouble("amount"), d.optString("referenceCode"), date(d, "paidAt"));
        p.setBankDetails(d.optString("bankCode"), d.optString("bankAccountName"), d.optString("bankAccountNumber"));
        p.setQrUrl(d.optString("qrUrl"));
        p.setVerificationDetails(d.optString("verificationProvider"), d.optString("transferContent")); return p;
    }
    static Shipment shipment(JSONObject d) {
        return d == null ? null : Shipment.restore(d.optString("id"), d.optString("orderId"), d.optString("carrierName"),
                d.optString("trackingCode"), ShipmentStatus.valueOf(d.optString("status")), d.optDouble("distanceKm"), d.optDouble("shippingFee"));
    }
    private static Date date(JSONObject d, String field) {
        return d.isNull(field) || !d.has(field) ? null : new Date(d.optLong(field));
    }
}
