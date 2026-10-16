package com.rescuefarm.ui.admin;

import org.junit.Test;
import java.math.BigDecimal;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;
import static org.junit.Assert.*;

public class AdminDetailFormatterTest {
    @Test public void identifiersAndUserContentRemainExact() {
        assertEquals("00366523357", AdminDetailFormatter.value("bankAccountNumber", "00366523357"));
        assertEquals("0911223344", AdminDetailFormatter.value("phone", "0911223344"));
        assertEquals("doc-AbC_123", AdminDetailFormatter.value("id", "doc-AbC_123"));
        assertEquals("ACTIVE", AdminDetailFormatter.value("shopName", "ACTIVE"));
        assertEquals("OVER_SUPPLY", AdminDetailFormatter.value("reason", "OVER_SUPPLY"));
        String address = "273 Phạm Phú Thứ, Phường 11, Quận Tân Bình, Thành phố Hồ Chí Minh";
        assertEquals(address, AdminDetailFormatter.value("address", address));
    }

    @Test public void monetaryValuesUseVietnameseDisplayWithoutChangingAmounts() {
        BigDecimal original = new BigDecimal("1234500");
        String rendered = AdminDetailFormatter.value("totalAmount", original);
        assertTrue(rendered.contains("1.234.500")); assertTrue(rendered.contains("₫"));
        assertEquals(new BigDecimal("1234500"), original);
        assertEquals("1,125", AdminDetailFormatter.value("quantity", new BigDecimal("1.125")));
        assertEquals("10.7894883", AdminDetailFormatter.value("latitude", 10.7894883));
    }

    @Test public void datesUseReadableLocalCalendarValues() {
        Calendar calendar = Calendar.getInstance(TimeZone.getDefault(), new Locale("vi", "VN"));
        calendar.clear(); calendar.set(2026, Calendar.OCTOBER, 14, 10, 34);
        assertEquals("14/10/2026 · 10:34", AdminDetailFormatter.value("createdAt", calendar.getTime()));
    }

    @Test public void workflowStatesAreTranslatedOnlyForEnumFields() {
        assertEquals("Đã phê duyệt", AdminDetailFormatter.value("sellerStatus", "APPROVED"));
        assertEquals("Sẵn sàng nhận hàng", AdminDetailFormatter.value("status", "READY_FOR_PICKUP"));
        assertEquals("Thanh toán khi nhận hàng", AdminDetailFormatter.value("paymentMethod", "COD"));
        assertEquals("Ảnh hưởng thời tiết", AdminDetailFormatter.value("rescueReason", "WEATHER_IMPACT"));
        assertEquals("CUSTOM_FUTURE_STATE", AdminDetailFormatter.value("status", "CUSTOM_FUTURE_STATE"));
        assertEquals("Không", AdminDetailFormatter.value("active", false));
        assertEquals("Chưa cập nhật", AdminDetailFormatter.value("moderationReason", ""));
    }

    @Test public void financialContactAndReferenceFieldsStayInSeparateGroups() {
        assertEquals("Thông tin ngân hàng", AdminDetailFormatter.group("bankAccountNumber"));
        assertEquals("Địa chỉ và vị trí", AdminDetailFormatter.group("address"));
        assertEquals("Giá và thanh toán", AdminDetailFormatter.group("subtotal"));
        assertTrue(AdminDetailFormatter.technical("defaultAddressId"));
        assertTrue(AdminDetailFormatter.technical("imagePublicIds"));
        assertFalse(AdminDetailFormatter.hidden("imagePublicId"));
        assertEquals("Chủ tài khoản", AdminDetailFormatter.label("bankAccountName"));
        assertEquals("Hồ sơ người bán", AdminDetailFormatter.label("sellerProfile"));
        assertEquals("New Custom Field", AdminDetailFormatter.label("newCustomField"));
    }
}
