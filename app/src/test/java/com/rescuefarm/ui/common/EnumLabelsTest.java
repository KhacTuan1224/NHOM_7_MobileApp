package com.rescuefarm.ui.common;

import com.rescuefarm.domain.enums.*;
import com.rescuefarm.ui.home.DiscoveryQuery;
import com.rescuefarm.data.repository.AdminRepository;
import org.junit.Test;
import static org.junit.Assert.*;

public class EnumLabelsTest {
    @Test public void everyDomainEnumAndFilterOptionHasAVietnameseLabel() {
        Class<?>[] types = {AddressType.class, ApplicationStatus.class, BatchStatus.class, CampaignStatus.class, CartOwnerType.class, CommentStatus.class, CustomerType.class, FulfillmentType.class, NotificationType.class, OrderOwnerType.class, OrderStatus.class, PaymentMethod.class, PaymentStatus.class, PostStatus.class, ProductStatus.class, PromotionType.class, ReactionType.class, ReportStatus.class, ReportTargetType.class, RescueMode.class, RescueReason.class, ReviewStatus.class, SellerStatus.class, ShipmentStatus.class, UrgencyLevel.class, UserRole.class, UserStatus.class, DiscoveryQuery.Sort.class, AdminRepository.Section.class};
        for (Class<?> type : types) for (Object constant : type.getEnumConstants()) {
            Enum<?> value = (Enum<?>) constant;
            assertTrue(type.getSimpleName() + "." + value.name(), EnumLabels.hasLabel(value.name()));
            assertNotEquals(value.name(), EnumLabels.label(value));
            assertNotEquals("Chưa xác định", EnumLabels.label(value));
        }
    }
    @Test public void labelsDoNotChangePersistedCodes() {
        assertEquals("Giao hàng tận nơi", EnumLabels.label(FulfillmentType.DELIVERY));
        assertEquals("Nhận tại cửa hàng", EnumLabels.label(FulfillmentType.PICKUP));
        assertEquals("Nhận tại điểm giải cứu", EnumLabels.label(FulfillmentType.ON_SITE_RESCUE));
        assertEquals("DELIVERY", FulfillmentType.DELIVERY.name());
        assertEquals("DELIVERY", FulfillmentType.DELIVERY.toString());
        assertEquals(PaymentMethod.BANK_TRANSFER, PaymentMethod.valueOf("BANK_TRANSFER"));
    }
    @Test public void pendingAndFailedStatesDescribeTheirOwnContext() {
        assertEquals("Chờ xác nhận thanh toán", EnumLabels.label(PaymentStatus.PENDING));
        assertEquals("Chờ xác nhận", EnumLabels.label(OrderStatus.PENDING));
        assertEquals("Thanh toán thất bại", EnumLabels.label(PaymentStatus.FAILED));
        assertEquals("Giao hàng thất bại", EnumLabels.label(ShipmentStatus.FAILED));
    }
}
