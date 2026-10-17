package com.rescuefarm.ui.common;

import com.rescuefarm.domain.enums.OrderStatus;
import com.rescuefarm.domain.enums.PaymentMethod;

public final class OrderLabels {
    private OrderLabels() { }
    public static String status(OrderStatus status, PaymentMethod method) {
        if (status == OrderStatus.CONFIRMED && method == PaymentMethod.BANK_TRANSFER)
            return "Đã nhận tiền và xác nhận đơn";
        if (status == OrderStatus.DELIVERED)
            return method == PaymentMethod.COD ? "Đã thanh toán và nhận đơn" : "Đã nhận hàng";
        switch (status) {
            case PENDING: return method == PaymentMethod.BANK_TRANSFER
                    ? "Chờ cửa hàng nhận tiền và xác nhận đơn" : "Cửa hàng xác nhận đơn";
            case CONFIRMED: return "Đơn đã được xác nhận";
            case PREPARING: return "Đang chuẩn bị nông sản";
            case SHIPPING: return "Đang giao hàng";
            case READY_FOR_PICKUP: return "Sẵn sàng nhận tại cửa hàng";
            default: return EnumLabels.label(status);
        }
    }
    /** Translate only legacy system order messages; preserve arbitrary notification content. */
    public static String notificationBody(String body) {
        if (body == null) return "";
        for (String prefix : new String[]{"Trạng thái: ", "Trạng thái đơn hàng: "}) {
            if (!body.startsWith(prefix)) continue;
            try { return prefix + status(OrderStatus.valueOf(body.substring(prefix.length()).trim()), null); }
            catch (IllegalArgumentException ignored) { }
        }
        return body;
    }
}
