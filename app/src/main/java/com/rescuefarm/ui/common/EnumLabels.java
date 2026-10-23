package com.rescuefarm.ui.common;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Vietnamese presentation labels. Enum names remain the persistence/API contract. */
public final class EnumLabels {
    private static final Map<String, String> LABELS;
    static {
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put("ACTIVE", "Đang hoạt động");
        labels.put("INACTIVE", "Ngừng hoạt động");
        labels.put("APPROVED", "Đã phê duyệt");
        labels.put("PENDING_APPROVAL", "Chờ phê duyệt");
        labels.put("PENDING", "Chờ xử lý");
        labels.put("REJECTED", "Đã từ chối");
        labels.put("BLOCKED", "Đã khóa");
        labels.put("HIDDEN", "Đã ẩn");
        labels.put("PUBLISHED", "Đã đăng");
        labels.put("DRAFT", "Bản nháp");
        labels.put("SOLD_OUT", "Đã hết hàng");
        labels.put("AVAILABLE", "Còn hàng");
        labels.put("EXPIRED", "Đã hết hạn");
        labels.put("ARCHIVED", "Đã lưu trữ");
        labels.put("COMPLETED", "Đã hoàn tất");
        labels.put("STOPPED", "Đã dừng");
        labels.put("CONFIRMED", "Đã xác nhận");
        labels.put("SHIPPING", "Đang giao hàng");
        labels.put("DELIVERED", "Đã giao hàng");
        labels.put("CANCELLED", "Đã hủy");
        labels.put("PROCESSING", "Đang xử lý");
        labels.put("PACKING", "Đang đóng gói");
        labels.put("READY", "Sẵn sàng");
        labels.put("PAID", "Đã thanh toán");
        labels.put("UNPAID", "Chưa thanh toán");
        labels.put("REFUNDED", "Đã hoàn tiền");
        labels.put("FAILED", "Thất bại");
        labels.put("BANK_TRANSFER", "Chuyển khoản ngân hàng");
        labels.put("COD", "Thanh toán khi nhận hàng");
        labels.put("CASH_ON_SITE", "Tiền mặt tại điểm nhận");
        labels.put("CASH", "Tiền mặt");
        labels.put("DELIVERY", "Giao hàng tận nơi");
        labels.put("PICKUP", "Nhận tại cửa hàng");
        labels.put("ON_SITE_RESCUE", "Nhận tại điểm giải cứu");
        labels.put("SELLER", "Người bán");
        labels.put("CUSTOMER", "Khách hàng");
        labels.put("ADMIN", "Quản trị viên");
        labels.put("GUEST", "Khách vãng lai");
        labels.put("INDIVIDUAL", "Cá nhân");
        labels.put("BUSINESS", "Doanh nghiệp");
        labels.put("HOME", "Nhà riêng");
        labels.put("WORK", "Cơ quan");
        labels.put("OTHER", "Khác");
        labels.put("FIXED_POINT", "Điểm bán cố định");
        labels.put("MOBILE_POINT", "Điểm bán di động");
        labels.put("NORMAL", "Bình thường");
        labels.put("HIGH", "Khẩn cấp");
        labels.put("CRITICAL", "Rất khẩn cấp");
        labels.put("LOW", "Thấp");
        labels.put("OVER_SUPPLY", "Dư nguồn cung");
        labels.put("NEAR_EXPIRY", "Sắp hết hạn");
        labels.put("APPEARANCE", "Hình thức không đạt chuẩn");
        labels.put("SEASONAL", "Nông sản theo mùa");
        labels.put("WEATHER", "Ảnh hưởng thời tiết");
        labels.put("IN_REVIEW", "Đang xem xét");
        labels.put("RESOLVED", "Đã giải quyết");
        labels.put("DISMISSED", "Đã bác bỏ");
        labels.put("EDITED", "Đã chỉnh sửa");
        labels.put("DELETED", "Đã xóa");
        labels.put("PRODUCT", "Sản phẩm");
        labels.put("CAMPAIGN", "Chiến dịch");
        labels.put("COMMENT", "Bình luận");
        labels.put("REVIEW", "Đánh giá");
        labels.put("USER", "Tài khoản");
        labels.put("ORDER", "Đơn hàng");
        labels.put("CREATED", "Đã tạo");
        labels.put("UPDATED", "Đã cập nhật");
        labels.put("UNKNOWN", "Chưa xác định");
        labels.put("UNCONFIGURED", "Chưa cấu hình");
        labels.put("PREPARING", "Đang chuẩn bị");
        labels.put("READY_FOR_PICKUP", "Sẵn sàng nhận hàng");
        labels.put("SEASONAL_HARVEST", "Thu hoạch theo mùa");
        labels.put("WEATHER_IMPACT", "Ảnh hưởng thời tiết");
        labels.put("ORDER_CANCELLED", "Đơn hàng bị hủy");
        labels.put("LOGISTICS_DISRUPTION", "Gián đoạn vận chuyển");
        labels.put("DEMAND_DROP", "Nhu cầu giảm");
        labels.put("COSMETIC_GRADE", "Hình thức không đạt chuẩn");
        labels.put("CHƯA NỘP HỒ SƠ CHI TIẾT", "Chưa nộp hồ sơ chi tiết");
        labels.put("RESERVED_PARTIAL", "Đã giữ một phần");
        labels.put("SUSPENDED", "Tạm đình chỉ");
        labels.put("MODERATION", "Kiểm duyệt");
        labels.put("SYSTEM", "Hệ thống");
        labels.put("LIKE", "Thích");
        labels.put("SUPPORT", "Ủng hộ");
        labels.put("CARE", "Quan tâm");
        labels.put("RELEVANCE", "Phù hợp nhất");
        labels.put("URGENCY", "Mức độ khẩn cấp");
        labels.put("DISCOUNT_DESC", "Giảm giá nhiều nhất");
        labels.put("PRICE_ASC", "Giá tăng dần");
        labels.put("DISTANCE", "Gần bạn nhất");
        labels.put("NEAREST_DEADLINE", "Sắp kết thúc");
        labels.put("BEST_SELLING", "Bán chạy nhất");
        labels.put("SELLER_APPLICATIONS", "Hồ sơ đăng ký người bán");
        labels.put("USERS", "Tài khoản");
        labels.put("PRODUCTS", "Sản phẩm");
        labels.put("CAMPAIGNS", "Chiến dịch");
        labels.put("CATEGORIES", "Danh mục");
        labels.put("BANNERS", "Ảnh quảng bá");
        labels.put("REVIEWS", "Đánh giá");
        labels.put("REPORTS", "Báo cáo");
        labels.put("ORDERS", "Đơn hàng");
        labels.put("SHIPPING_SETTINGS", "Cấu hình phí giao hàng");
        labels.put("ANALYTICS", "Thống kê");
        labels.put("ALL", "Tất cả");
        labels.put("VERIFIED", "Đã xác minh");
        labels.put("UNVERIFIED", "Chưa xác minh");
        labels.put("REVIEW_REQUIRED", "Cần kiểm tra đơn vị");
        labels.put("NEEDS_REVIEW", "Cần kiểm tra");
        labels.put("LEGACY", "Dữ liệu cũ");
        labels.put("MISSING", "Chưa cập nhật");
        LABELS = Collections.unmodifiableMap(labels);
    }
    private EnumLabels() { }
    public static boolean hasLabel(String code) { return LABELS.containsKey(code); }
    public static String label(String code) { return LABELS.getOrDefault(code, "Chưa xác định"); }
    public static String label(Enum<?> value) {
        if (value == null) return "Chưa cập nhật";
        String type = value.getDeclaringClass().getSimpleName();
        String code = value.name();
        if ("OrderStatus".equals(type) && "DELIVERED".equals(code)) return "Đã nhận hàng";
        if ("PENDING".equals(code)) {
            if ("OrderStatus".equals(type)) return "Chờ xác nhận";
            if ("PaymentStatus".equals(type)) return "Chờ xác nhận thanh toán";
            if ("ApplicationStatus".equals(type)) return "Chờ xét duyệt";
        }
        if ("FAILED".equals(code)) {
            if ("PaymentStatus".equals(type)) return "Thanh toán thất bại";
            if ("ShipmentStatus".equals(type)) return "Giao hàng thất bại";
        }
        return label(code);
    }
}
