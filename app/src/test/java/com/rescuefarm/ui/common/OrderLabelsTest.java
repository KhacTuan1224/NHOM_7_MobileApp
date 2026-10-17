package com.rescuefarm.ui.common;

import com.rescuefarm.domain.enums.OrderStatus;
import com.rescuefarm.domain.enums.PaymentMethod;
import org.junit.Test;
import static org.junit.Assert.*;

public class OrderLabelsTest {
    @Test public void transferAndCashDescribeTheNewConfirmationSteps() {
        assertEquals("Đã nhận tiền và xác nhận đơn", OrderLabels.status(OrderStatus.CONFIRMED, PaymentMethod.BANK_TRANSFER));
        assertEquals("Đã nhận hàng", OrderLabels.status(OrderStatus.DELIVERED, PaymentMethod.BANK_TRANSFER));
        assertEquals("Đã thanh toán và nhận đơn", OrderLabels.status(OrderStatus.DELIVERED, PaymentMethod.COD));
    }
    @Test public void legacyOrderNotificationsTranslateEnumValues() {
        assertEquals("Trạng thái: Đang giao hàng", OrderLabels.notificationBody("Trạng thái: SHIPPING"));
        assertEquals("Trạng thái: Đã nhận hàng", OrderLabels.notificationBody("Trạng thái: DELIVERED"));
        assertEquals("Trạng thái đơn hàng: Đang chuẩn bị nông sản",
                OrderLabels.notificationBody("Trạng thái đơn hàng: PREPARING"));
        assertEquals("Nội dung khác CONFIRMED", OrderLabels.notificationBody("Nội dung khác CONFIRMED"));
    }
}
