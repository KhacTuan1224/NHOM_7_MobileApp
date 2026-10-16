package com.rescuefarm;

import android.graphics.Bitmap;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.rescuefarm.data.repository.AdminRepository.Section;
import com.rescuefarm.data.repository.admin.AdminListItem;
import com.rescuefarm.ui.admin.AdminDetailRenderer;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.io.FileOutputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import static org.junit.Assert.*;

/** Synthetic, immutable documents only. No repository or mutation action is invoked. */
@RunWith(AndroidJUnit4.class)
public class AdminDetailPresentationTest {
    private static Map<String, Object> document(Object... pairs) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) result.put((String) pairs[i], pairs[i + 1]);
        return Collections.unmodifiableMap(result);
    }
    private Map<String, Object> seller() {
        return document("id", "seller-demo-001", "sellerId", "seller-demo-001",
                "applicationStatus", "CHƯA NỘP HỒ SƠ CHI TIẾT", "sellerProfile", document(
                        "id", "seller-demo-001", "shopName", "Nông Sản Xanh", "representativeName", "Nguyễn Văn An",
                        "fullName", "Nguyễn Văn An", "sellerStatus", "APPROVED", "role", "SELLER",
                        "phone", "0911223344", "email", "nong-san@example.com",
                        "shopDescription", "Nông sản tươi từ nhà vườn, cùng chung tay giảm lãng phí thực phẩm.",
                        "address", "273 Phạm Phú Thứ, Phường 11, Quận Tân Bình, Thành phố Hồ Chí Minh",
                        "bankCode", "MB", "bankAccountName", "NGUYEN VAN AN", "bankAccountNumber", "00366523357",
                        "latitude", 10.7894883, "longitude", 106.6547124,
                        "moderationReason", "", "createdAt", new com.google.firebase.Timestamp(new Date(1791948840000L))));
    }
    private Map<String, Object> order() {
        return document("id", "order-demo-001", "orderCode", "RF-20261016-001", "status", "DELIVERED",
                "receiverName", "Trần Thị Mai", "receiverPhone", "0901234567", "receiverAddress", "12 Nguyễn Huệ, Quận 1, TP. Hồ Chí Minh",
                "fulfillmentType", "DELIVERY", "subtotal", 125000, "shippingFee", 15000, "totalAmount", 140000,
                "paymentMethod", "COD", "paymentStatus", "PAID", "items", Arrays.asList(
                        document("productNameSnapshot", "Cam sành", "quantity", 2.5, "unitSnapshot", "kg", "subtotal", 75000),
                        document("productNameSnapshot", "Rau cải xanh", "quantity", 2, "unitSnapshot", "kg", "subtotal", 50000)),
                "payment", document("status", "PAID", "amount", 140000, "method", "COD"),
                "shipment", document("status", "DELIVERED", "trackingCode", "SHIP-00012"));
    }
    private Map<String, Object> fixture(Section section) {
        switch (section) {
            case SELLER_APPLICATIONS: return seller();
            case ORDERS: return order();
            case USERS: return document("fullName", "Nguyễn Văn An", "role", "CUSTOMER", "phone", "0911223344");
            case PRODUCTS: return document("name", "Cam sành", "rescuePrice", 30000, "unit", "kg",
                    "categoryDetail", document("name", "Trái cây"), "batches", Arrays.asList(
                            document("id", "batch-A", "availableQuantity", 2.5), document("id", "batch-B", "availableQuantity", 4)));
            case CAMPAIGNS: return document("title", "Giải cứu cam sành", "rescueMode", "FIXED_POINT",
                    "batchTargets", document("batch-A", 10, "batch-B", 20), "imageUrls", Collections.emptyList());
            case POSTS: return document("title", "Nông sản mùa thu", "content", "Nội dung câu chuyện\nDòng thứ hai.",
                    "linkedProducts", Arrays.asList(document("name", "Cam sành"), document("name", "Rau cải")));
            case CATEGORIES: return document("name", "Rau củ", "displayOrder", 2, "active", true);
            case BANNERS: return document("title", "Mùa thu xanh", "imageUrl", "https://example.com/banner.png", "startDate", new Date());
            case REVIEWS: return document("rating", 4.5, "content", "Rau tươi, giao hàng nhanh.", "customerProfile", document("fullName", "Trần Thị Mai"));
            case REPORTS: return document("targetType", "POST", "reason", "Nội dung chưa chính xác",
                    "target", document("title", "Bài viết cần kiểm tra", "sellerProfile", document("shopName", "Nông Sản Xanh")));
            default: return document("newCustomField", "Giá trị bổ sung vẫn được giữ", "optionalField", null);
        }
    }
    private AlertDialog show(MainActivity activity, String title, String status, Map<String, Object> data) {
        ScrollView scroll = new ScrollView(activity); scroll.setBackgroundColor(activity.getColor(R.color.rescue_background));
        LinearLayout body = new LinearLayout(activity); body.setOrientation(LinearLayout.VERTICAL);
        int padding = Math.round(20 * activity.getResources().getDisplayMetrics().density);
        body.setPadding(padding, padding, padding, padding); scroll.addView(body);
        new AdminDetailRenderer(activity).render(body, new AdminListItem("demo-id", title, "", status, true), data);
        AlertDialog dialog = new MaterialAlertDialogBuilder(activity, R.style.ThemeOverlay_RescueFarm_AdminDetail).setTitle(title).setView(scroll)
                .setNegativeButton("Đóng", null).create();
        dialog.show(); dialog.getWindow().setLayout(activity.getResources().getDisplayMetrics().widthPixels - padding * 2,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        return dialog;
    }
    private boolean hasText(View view, String expected) {
        if (view instanceof TextView && expected.contentEquals(((TextView) view).getText())) return true;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++)
            if (hasText(((ViewGroup) view).getChildAt(i), expected)) return true;
        return false;
    }
    private View findText(View view, String expected) {
        if (view instanceof TextView && expected.contentEquals(((TextView) view).getText())) return view;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
            View match = findText(((ViewGroup) view).getChildAt(i), expected); if (match != null) return match;
        }
        return null;
    }
    private void screenshot(String name) throws Exception {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        android.os.SystemClock.sleep(500); // Capture after the dialog's entrance animation finishes.
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        Bitmap bitmap = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
        assertNotNull(bitmap);
        File output = new File(InstrumentationRegistry.getInstrumentation().getTargetContext().getExternalFilesDir(null), name);
        try (FileOutputStream stream = new FileOutputStream(output)) { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)); }
        bitmap.recycle();
    }

    @Test public void sellerDetailIsReadableAndReferencesExpandWithoutChangingData() throws Exception {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            AlertDialog[] dialog = new AlertDialog[1]; Map<String, Object> fixture = seller(); String before = fixture.toString();
            scenario.onActivity(activity -> {
                dialog[0] = show(activity, "Nông Sản Xanh", "APPROVED", fixture);
                View content = dialog[0].getWindow().getDecorView();
                assertTrue(hasText(content, "Hồ sơ người bán")); assertTrue(hasText(content, "Tên cửa hàng"));
                assertTrue(hasText(content, "Đã phê duyệt")); assertTrue(hasText(content, "00366523357"));
                assertTrue(hasText(content, "Ngày tạo"));
                View references = findText(content, "Thông tin tham chiếu  +"); assertNotNull(references);
                references.performClick(); assertTrue(hasText(content, "Thông tin tham chiếu  −"));
                assertTrue(hasText(content, "seller-demo-001")); references.performClick();
            });
            screenshot("admin-seller-detail-preview.png");
            scenario.onActivity(activity -> dialog[0].dismiss()); assertEquals(before, fixture.toString());
        }
    }

    @Test public void everyAdminResourceUsesTheSameReadOnlyNestedFormatting() throws Exception {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                for (Section section : Section.values()) {
                    Map<String, Object> fixture = fixture(section);
                    String before = fixture.toString();
                    AlertDialog dialog = show(activity, "Chi tiết " + section.name(), "ACTIVE", fixture);
                    View content = dialog.getWindow().getDecorView();
                    if (section == Section.ORDERS) {
                        assertTrue(hasText(content, "Thông tin người nhận")); assertTrue(hasText(content, "Sản phẩm trong đơn"));
                        assertTrue(hasText(content, "Cam sành")); assertTrue(hasText(content, "Rau cải xanh"));
                        assertTrue(hasText(content, "Mục 1")); assertTrue(hasText(content, "Mục 2"));
                    }
                    assertEquals(before, fixture.toString()); dialog.dismiss();
                }
            });
        }
    }

    @Test public void orderDetailShowsPricesAndNestedRecords() throws Exception {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            AlertDialog[] dialog = new AlertDialog[1];
            scenario.onActivity(activity -> dialog[0] = show(activity, "Đơn hàng RF-20261016-001", "DELIVERED", order()));
            screenshot("admin-order-detail-preview.png"); scenario.onActivity(activity -> dialog[0].dismiss());
        }
    }
}
