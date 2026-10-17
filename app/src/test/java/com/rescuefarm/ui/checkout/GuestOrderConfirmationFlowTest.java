package com.rescuefarm.ui.checkout;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import com.rescuefarm.data.repository.OrderRepository;
import com.rescuefarm.domain.enums.FulfillmentType;
import com.rescuefarm.domain.enums.OrderOwnerType;
import com.rescuefarm.domain.enums.OrderStatus;
import com.rescuefarm.domain.enums.PaymentMethod;
import com.rescuefarm.domain.enums.PaymentStatus;
import com.rescuefarm.domain.model.Order;
import com.rescuefarm.domain.model.OrderItem;
import com.rescuefarm.ui.common.OrderLabels;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.Date;
import org.junit.Rule;
import org.junit.Test;
import static org.junit.Assert.*;

public class GuestOrderConfirmationFlowTest {
    @Rule
    public final InstantTaskExecutorRule executor = new InstantTaskExecutorRule();

    private String searchedCode;
    private String searchedPhone;
    private OrderRepository.OrderCallback callbackPassed;

    private OrderRepository mockRepo(boolean canGuestConfirm) {
        return (OrderRepository) Proxy.newProxyInstance(
                OrderRepository.class.getClassLoader(),
                new Class<?>[]{OrderRepository.class},
                (proxy, method, args) -> {
                    if ("findGuestOrder".equals(method.getName())) {
                        searchedCode = (String) args[0];
                        searchedPhone = (String) args[1];
                        callbackPassed = (OrderRepository.OrderCallback) args[2];
                        return null;
                    }
                    if ("canConfirmGuestReceipt".equals(method.getName())) {
                        return canGuestConfirm;
                    }
                    if ("close".equals(method.getName())) {
                        return null;
                    }
                    throw new AssertionError("Unexpected call: " + method.getName());
                });
    }

    @Test
    public void findGuestTransitionsThroughLoadingAndSuccess() {
        OrderRepository repo = mockRepo(true);
        OrderViewModel vm = new OrderViewModel(repo);

        assertEquals(CheckoutState.Status.IDLE, vm.getState().getValue().getStatus());

        vm.findGuest("RF-123456", "0901234567");

        assertEquals("RF-123456", searchedCode);
        assertEquals("0901234567", searchedPhone);
        assertEquals(CheckoutState.Status.LOADING, vm.getState().getValue().getStatus());

        OrderItem item = OrderItem.snapshot("item-1", "order-1", "product-1", "batch-1",
                "Gạo cứu trợ", "http://image", "kg", 20000, 15000, 15000, 2);
        Order mockOrder = Order.create("order-1", OrderOwnerType.GUEST, "guest-123",
                "RF-123456", "seller-1", null, FulfillmentType.DELIVERY,
                "Nguyễn Văn A", "0901234567", "123 Đường A, Quận 1", 10.7, 106.6,
                30000, 0, 15000, PaymentMethod.COD, PaymentStatus.UNPAID,
                "Giao nhanh giúp tôi", Collections.singletonList(item), new Date());

        callbackPassed.onSuccess(mockOrder, false);

        assertEquals(CheckoutState.Status.SUCCESS, vm.getState().getValue().getStatus());
        assertSame(mockOrder, vm.getState().getValue().getOrder());
        assertEquals("RF-123456", vm.getState().getValue().getOrder().getOrderCode());
        assertEquals("Nguyễn Văn A", vm.getState().getValue().getOrder().getReceiverName());
    }

    @Test
    public void findGuestReportsErrorOnFailure() {
        OrderRepository repo = mockRepo(false);
        OrderViewModel vm = new OrderViewModel(repo);

        vm.findGuest("RF-INVALID", "0900000000");
        assertEquals(CheckoutState.Status.LOADING, vm.getState().getValue().getStatus());

        callbackPassed.onError(OrderRepository.ErrorCode.NOT_FOUND, "Không tìm thấy đơn hàng.");

        assertEquals(CheckoutState.Status.ERROR, vm.getState().getValue().getStatus());
        assertEquals("Không tìm thấy đơn hàng.", vm.getState().getValue().getMessage());
    }

    @Test
    public void guestOrderLabelsMatchCustomerStatusLabels() {
        assertEquals("Cửa hàng xác nhận đơn", OrderLabels.status(OrderStatus.PENDING, PaymentMethod.COD));
        assertEquals("Chờ cửa hàng nhận tiền và xác nhận đơn", OrderLabels.status(OrderStatus.PENDING, PaymentMethod.BANK_TRANSFER));
        assertEquals("Đơn đã được xác nhận", OrderLabels.status(OrderStatus.CONFIRMED, PaymentMethod.COD));
        assertEquals("Đã nhận tiền và xác nhận đơn", OrderLabels.status(OrderStatus.CONFIRMED, PaymentMethod.BANK_TRANSFER));
        assertEquals("Đang chuẩn bị nông sản", OrderLabels.status(OrderStatus.PREPARING, PaymentMethod.COD));
        assertEquals("Đang giao hàng", OrderLabels.status(OrderStatus.SHIPPING, PaymentMethod.COD));
        assertEquals("Sẵn sàng nhận tại cửa hàng", OrderLabels.status(OrderStatus.READY_FOR_PICKUP, PaymentMethod.COD));
        assertEquals("Đã thanh toán và nhận đơn", OrderLabels.status(OrderStatus.DELIVERED, PaymentMethod.COD));
        assertEquals("Đã nhận hàng", OrderLabels.status(OrderStatus.DELIVERED, PaymentMethod.BANK_TRANSFER));
        assertEquals("Đã hủy", OrderLabels.status(OrderStatus.CANCELLED, PaymentMethod.COD));
    }
}
