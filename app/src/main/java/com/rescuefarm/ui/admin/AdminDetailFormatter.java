package com.rescuefarm.ui.admin;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Presentation only. Never normalizes or writes values back into a document. */
public final class AdminDetailFormatter {
    private static final Locale VI = new Locale("vi", "VN");
    private static final Map<String, String> LABELS = new LinkedHashMap<>();
    private static final Map<String, String> STATES = new LinkedHashMap<>();
    private static final List<String> PRIORITY = Arrays.asList("title", "name", "shopName", "fullName",
            "representativeName", "orderCode", "status", "sellerStatus", "applicationStatus", "role",
            "phone", "email", "receiverName", "receiverPhone", "receiverAddress", "address",
            "description", "shopDescription", "content", "reason", "note");
    static {
        labels("title", "Tiêu đề", "name", "Tên", "fullName", "Họ và tên", "email", "Email",
                "phone", "Số điện thoại", "role", "Vai trò", "status", "Trạng thái",
                "sellerStatus", "Trạng thái người bán", "applicationStatus", "Trạng thái hồ sơ đăng ký",
                "representativeName", "Người đại diện", "shopName", "Tên cửa hàng",
                "shopDescription", "Giới thiệu cửa hàng", "description", "Mô tả", "content", "Nội dung",
                "customerType", "Loại khách hàng", "companyName", "Tên doanh nghiệp", "taxCode", "Mã số thuế",
                "address", "Địa chỉ", "province", "Tỉnh / thành phố", "district", "Quận / huyện",
                "ward", "Xã / phường", "street", "Số nhà, tên đường", "latitude", "Vĩ độ",
                "longitude", "Kinh độ", "locationName", "Địa điểm", "type", "Loại",
                "bankCode", "Mã ngân hàng", "bankName", "Ngân hàng", "bankAccountName", "Chủ tài khoản",
                "bankAccountNumber", "Số tài khoản", "bankAccount", "Số tài khoản", "accountName", "Chủ tài khoản",
                "avatarUrl", "Ảnh đại diện", "shopAvatarUrl", "Ảnh cửa hàng", "proofImageUrl", "Ảnh minh chứng",
                "imageUrl", "Hình ảnh", "imageUrls", "Hình ảnh", "images", "Hình ảnh", "qrUrl", "Ảnh mã QR",
                "originalPrice", "Giá gốc", "rescuePrice", "Giá giải cứu", "unitPrice", "Đơn giá",
                "finalPriceSnapshot", "Đơn giá khi đặt", "originalPriceSnapshot", "Giá gốc khi đặt",
                "rescuePriceSnapshot", "Giá giải cứu khi đặt", "subtotal", "Tiền hàng",
                "totalAmount", "Tổng thanh toán", "amount", "Số tiền", "shippingFee", "Phí giao hàng",
                "quantityDiscount", "Giảm giá số lượng", "totalDiscount", "Tổng giảm giá", "discount", "Giảm giá",
                "baseFeeVnd", "Phí giao hàng cơ bản", "perKmFeeVnd", "Phí mỗi km", "freeDistanceKm", "Khoảng cách miễn phí",
                "quantity", "Số lượng", "initialQuantity", "Số lượng ban đầu", "availableQuantity", "Số lượng còn lại",
                "reservedQuantity", "Số lượng đã giữ", "soldQuantity", "Số lượng đã bán", "unit", "Đơn vị tính",
                "targetQuantity", "Mục tiêu giải cứu", "rescuedQuantity", "Đã giải cứu", "targetTotalQuantity", "Tổng mục tiêu",
                "targetRescueQuantity", "Mục tiêu giải cứu", "rescuedTotalQuantity", "Tổng đã giải cứu",
                "targetAmount", "Mục tiêu", "averageRating", "Điểm đánh giá trung bình", "rating", "Điểm đánh giá",
                "reviewCount", "Số lượt đánh giá", "viewCount", "Lượt xem", "reactionCount", "Lượt tương tác",
                "commentCount", "Số bình luận", "origin", "Xuất xứ", "urgencyLevel", "Mức độ khẩn cấp",
                "rescueReason", "Lý do giải cứu", "reason", "Lý do", "rescueMode", "Hình thức giải cứu", "mode", "Hình thức",
                "orderCode", "Mã đơn hàng", "ownerType", "Loại người đặt", "fulfillmentType", "Hình thức nhận hàng",
                "receiverName", "Người nhận", "receiverPhone", "Điện thoại người nhận", "receiverAddress", "Địa chỉ nhận hàng",
                "receiverLatitude", "Vĩ độ nhận hàng", "receiverLongitude", "Kinh độ nhận hàng", "note", "Ghi chú",
                "paymentMethod", "Phương thức thanh toán", "paymentStatus", "Trạng thái thanh toán", "method", "Phương thức",
                "referenceCode", "Mã tham chiếu", "trackingCode", "Mã vận đơn", "carrierName", "Đơn vị vận chuyển",
                "shippingAddress", "Địa chỉ giao hàng", "distanceKm", "Khoảng cách giao hàng",
                "productNameSnapshot", "Tên sản phẩm khi đặt", "productImageSnapshot", "Ảnh sản phẩm khi đặt",
                "unitSnapshot", "Đơn vị tính khi đặt", "startDate", "Ngày bắt đầu", "endDate", "Ngày kết thúc",
                "harvestDate", "Ngày thu hoạch", "expiryDate", "Hạn sử dụng", "createdAt", "Ngày tạo",
                "updatedAt", "Cập nhật lần cuối", "submittedAt", "Ngày gửi hồ sơ", "moderatedAt", "Thời điểm xét duyệt",
                "resolvedAt", "Thời điểm giải quyết", "paidAt", "Thời điểm thanh toán", "deliveredAt", "Thời điểm giao hàng",
                "cancelledAt", "Thời điểm hủy", "moderationReason", "Lý do xét duyệt", "rejectionReason", "Lý do từ chối",
                "resolutionNote", "Ghi chú xử lý", "moderationPreviousStatus", "Trạng thái trước kiểm duyệt",
                "active", "Hiển thị", "isDefault", "Địa chỉ mặc định", "isRead", "Đã đọc", "isSharingLocation", "Chia sẻ vị trí",
                "sharingLocation", "Chia sẻ vị trí", "displayOrder", "Thứ tự hiển thị", "inventoryFinalized", "Đã chốt tồn kho",
                "finalizedStatus", "Trạng thái chốt", "targetType", "Loại nội dung bị báo cáo", "resourceType", "Loại tài nguyên",
                "fromState", "Trạng thái trước", "toState", "Trạng thái sau", "body", "Nội dung thông báo",
                "sellerProfile", "Hồ sơ người bán", "customerProfile", "Hồ sơ khách hàng", "targetOwnerProfile", "Người tạo nội dung",
                "categoryDetail", "Danh mục sản phẩm", "campaignDetail", "Chiến dịch liên quan", "productDetail", "Sản phẩm liên quan",
                "orderDetail", "Đơn hàng liên quan", "orderItem", "Mặt hàng trong đơn", "linkedProducts", "Sản phẩm liên kết",
                "items", "Sản phẩm trong đơn", "batches", "Các lô hàng", "payment", "Thông tin thanh toán",
                "shipment", "Thông tin giao hàng", "orderTimeline", "Lịch sử đơn hàng", "target", "Nội dung bị báo cáo",
                "batchTargets", "Mục tiêu theo lô hàng", "batchReservations", "Số lượng giữ theo lô",
                "linkedProductIds", "Mã sản phẩm liên kết", "imagePublicIds", "Mã tài nguyên hình ảnh",
                "id", "Mã bản ghi", "sellerId", "Mã người bán", "customerId", "Mã khách hàng", "userId", "Mã tài khoản",
                "ownerId", "Mã người đặt", "productId", "Mã sản phẩm", "categoryId", "Mã danh mục", "campaignId", "Mã chiến dịch",
                "batchId", "Mã lô hàng", "orderId", "Mã đơn hàng", "orderItemId", "Mã mặt hàng trong đơn",
                "postId", "Mã bài viết", "targetId", "Mã nội dung bị báo cáo", "referenceId", "Mã tham chiếu",
                "resourceId", "Mã tài nguyên", "requestId", "Mã yêu cầu", "reportId", "Mã báo cáo",
                "defaultAddressId", "Mã địa chỉ mặc định", "activeCampaignId", "Mã chiến dịch đang hoạt động",
                "moderatedBy", "Người xét duyệt", "resolvedBy", "Người xử lý", "updatedBy", "Người cập nhật",
                "inventoryVersion", "Phiên bản tồn kho", "avatarPublicId", "Mã ảnh đại diện", "shopAvatarPublicId", "Mã ảnh cửa hàng",
                "imagePublicId", "Mã hình ảnh", "proofImagePublicId", "Mã ảnh minh chứng",
                "mobileLatitude", "Vĩ độ điểm bán di động", "mobileLongitude", "Kinh độ điểm bán di động",
                "lastLocationUpdate", "Cập nhật vị trí lần cuối", "locationUpdatedAt", "Cập nhật vị trí lần cuối",
                "campaignReservedQuantity", "Số lượng đã giữ của chiến dịch",
                "currentLatitude", "Vĩ độ hiện tại", "currentLongitude", "Kinh độ hiện tại",
                "locationSharingEnabled", "Chia sẻ vị trí", "carrier", "Đơn vị vận chuyển",
                "scheduledDeliveryAt", "Dự kiến giao hàng", "shippedAt", "Thời điểm bắt đầu giao",
                "shippingFeeSnapshot", "Phí giao hàng khi đặt", "contactPhone", "Điện thoại liên hệ");
        states("ACTIVE", "Đang hoạt động", "INACTIVE", "Ngừng hoạt động", "APPROVED", "Đã phê duyệt",
                "PENDING_APPROVAL", "Chờ phê duyệt", "PENDING", "Chờ xử lý", "REJECTED", "Đã từ chối",
                "BLOCKED", "Đã khóa", "HIDDEN", "Đã ẩn", "PUBLISHED", "Đã đăng", "DRAFT", "Bản nháp",
                "SOLD_OUT", "Đã hết hàng", "AVAILABLE", "Còn hàng", "EXPIRED", "Đã hết hạn", "ARCHIVED", "Đã lưu trữ",
                "COMPLETED", "Đã hoàn tất", "STOPPED", "Đã dừng", "CONFIRMED", "Đã xác nhận",
                "SHIPPING", "Đang giao hàng", "DELIVERED", "Đã giao hàng", "CANCELLED", "Đã hủy",
                "PROCESSING", "Đang xử lý", "PACKING", "Đang đóng gói", "READY", "Sẵn sàng",
                "PAID", "Đã thanh toán", "UNPAID", "Chưa thanh toán", "REFUNDED", "Đã hoàn tiền",
                "FAILED", "Thất bại", "BANK_TRANSFER", "Chuyển khoản ngân hàng", "COD", "Thanh toán khi nhận hàng",
                "CASH_ON_SITE", "Tiền mặt tại điểm nhận", "CASH", "Tiền mặt", "DELIVERY", "Giao hàng tận nơi",
                "PICKUP", "Nhận tại cửa hàng", "ON_SITE_RESCUE", "Nhận tại điểm giải cứu",
                "SELLER", "Người bán", "CUSTOMER", "Khách hàng", "ADMIN", "Quản trị viên", "GUEST", "Khách vãng lai",
                "INDIVIDUAL", "Cá nhân", "BUSINESS", "Doanh nghiệp", "HOME", "Nhà riêng", "WORK", "Cơ quan", "OTHER", "Khác",
                "FIXED_POINT", "Điểm bán cố định", "MOBILE_POINT", "Điểm bán di động", "NORMAL", "Bình thường",
                "HIGH", "Cao", "CRITICAL", "Khẩn cấp", "LOW", "Thấp", "OVER_SUPPLY", "Dư nguồn cung",
                "NEAR_EXPIRY", "Sắp hết hạn", "APPEARANCE", "Hình thức không đạt chuẩn", "SEASONAL", "Nông sản theo mùa",
                "WEATHER", "Ảnh hưởng thời tiết", "IN_REVIEW", "Đang xem xét", "RESOLVED", "Đã giải quyết",
                "DISMISSED", "Đã bác bỏ", "EDITED", "Đã chỉnh sửa", "DELETED", "Đã xóa",
                "PRODUCT", "Sản phẩm", "CAMPAIGN", "Chiến dịch", "POST", "Bài viết", "COMMENT", "Bình luận",
                "REVIEW", "Đánh giá", "USER", "Tài khoản", "ORDER", "Đơn hàng", "CREATED", "Đã tạo",
                "UPDATED", "Đã cập nhật", "UNKNOWN", "Chưa xác định", "UNCONFIGURED", "Chưa cấu hình",
                "PREPARING", "Đang chuẩn bị", "READY_FOR_PICKUP", "Sẵn sàng nhận hàng",
                "SEASONAL_HARVEST", "Thu hoạch theo mùa", "WEATHER_IMPACT", "Ảnh hưởng thời tiết",
                "ORDER_CANCELLED", "Đơn hàng bị hủy", "LOGISTICS_DISRUPTION", "Gián đoạn vận chuyển",
                "DEMAND_DROP", "Nhu cầu giảm", "COSMETIC_GRADE", "Hình thức không đạt chuẩn",
                "CHƯA NỘP HỒ SƠ CHI TIẾT", "Chưa nộp hồ sơ chi tiết");
    }
    private AdminDetailFormatter() { }
    private static void labels(String... pairs) { for (int i = 0; i < pairs.length; i += 2) LABELS.put(pairs[i], pairs[i + 1]); }
    private static void states(String... pairs) { for (int i = 0; i < pairs.length; i += 2) STATES.put(pairs[i], pairs[i + 1]); }

    public static String label(String key) {
        if (LABELS.containsKey(key)) return LABELS.get(key);
        String text = key.replace('_', ' ').replaceAll("([a-z0-9])([A-Z])", "$1 $2");
        return text.isEmpty() ? "Thông tin" : text.substring(0, 1).toUpperCase(VI) + text.substring(1);
    }
    public static String status(String value) { return STATES.getOrDefault(value, value); }
    public static boolean isEnum(String key) {
        return Arrays.asList("status", "sellerStatus", "applicationStatus", "role", "customerType", "ownerType",
                "paymentMethod", "paymentStatus", "fulfillmentType", "type", "targetType", "resourceType", "method",
                "urgencyLevel", "rescueReason", "rescueMode", "mode", "finalizedStatus", "moderationPreviousStatus",
                "fromState", "toState").contains(key);
    }
    public static String value(String key, Object value) {
        if (value == null || value instanceof String && ((String) value).trim().isEmpty()) return "Chưa cập nhật";
        if (value instanceof Boolean) return (Boolean) value ? "Có" : "Không";
        if (value instanceof Date) return new SimpleDateFormat("dd/MM/yyyy · HH:mm", VI).format((Date) value);
        if (value instanceof Number) {
            Number number = (Number) value;
            if (isMoney(key)) return NumberFormat.getCurrencyInstance(VI).format(number);
            if (key.toLowerCase(Locale.ROOT).contains("latitude") || key.toLowerCase(Locale.ROOT).contains("longitude"))
                return String.valueOf(value); // Retain the full GPS precision.
            if (technical(key) || key.toLowerCase(Locale.ROOT).contains("phone")
                    || key.startsWith("bankAccount") || "taxCode".equals(key)) return String.valueOf(value);
            NumberFormat format = NumberFormat.getNumberInstance(VI);
            format.setMaximumFractionDigits(value instanceof BigDecimal ? Math.max(0, ((BigDecimal) value).scale()) : 10);
            String result = format.format(number);
            if ("distanceKm".equals(key) || "freeDistanceKm".equals(key)) result += " km";
            if ("rating".equals(key) || "averageRating".equals(key)) result += " / 5";
            return result;
        }
        String text = String.valueOf(value);
        return isEnum(key) ? status(text) : text;
    }
    public static boolean isMoney(String key) {
        return Arrays.asList("originalPrice", "rescuePrice", "unitPrice", "subtotal", "totalAmount", "amount", "shippingFee",
                "quantityDiscount", "totalDiscount", "discount", "baseFeeVnd", "perKmFeeVnd", "finalPriceSnapshot",
                "originalPriceSnapshot", "rescuePriceSnapshot", "expectedSubtotal", "expectedShippingFee", "shippingFeeSnapshot").contains(key);
    }
    public static boolean technical(String key) {
        return "id".equals(key) || key.endsWith("Id") || key.endsWith("Ids") || key.endsWith("Version")
                || Arrays.asList("moderatedBy", "resolvedBy", "updatedBy", "adminId").contains(key);
    }
    public static boolean hidden(String key) {
        return "password".equalsIgnoreCase(key) || "passwordHash".equalsIgnoreCase(key);
    }
    public static int order(String key) {
        int index = PRIORITY.indexOf(key); return index < 0 ? PRIORITY.size() : index;
    }
    public static String group(String key) {
        if (technical(key)) return "Thông tin tham chiếu";
        if (key.endsWith("At") || Arrays.asList("moderationReason", "rejectionReason", "resolutionNote", "moderationPreviousStatus").contains(key))
            return "Lịch sử và xét duyệt";
        if (key.startsWith("bank") || "accountName".equals(key) || "qrUrl".equals(key)) return "Thông tin ngân hàng";
        if (key.startsWith("receiver")) return "Thông tin người nhận";
        if (Arrays.asList("address", "province", "district", "ward", "street", "latitude", "longitude", "locationName",
                "mobileLatitude", "mobileLongitude", "currentLatitude", "currentLongitude", "locationSharingEnabled",
                "sharingLocation", "isSharingLocation", "shippingAddress").contains(key)) return "Địa chỉ và vị trí";
        if (key.endsWith("Date")) return "Thời gian";
        if (isMoney(key) || key.startsWith("payment") || "method".equals(key)) return "Giá và thanh toán";
        if (key.toLowerCase(Locale.ROOT).contains("quantity") || "unit".equals(key) || "unitSnapshot".equals(key)) return "Số lượng và tồn kho";
        if (key.toLowerCase(Locale.ROOT).contains("image") || key.toLowerCase(Locale.ROOT).contains("avatar")) return "Hình ảnh";
        return "Thông tin chung";
    }
    public static List<String> groups() { return Collections.unmodifiableList(Arrays.asList("Thông tin chung", "Thông tin người nhận",
            "Địa chỉ và vị trí", "Giá và thanh toán", "Số lượng và tồn kho", "Thông tin ngân hàng", "Thời gian", "Hình ảnh",
            "Lịch sử và xét duyệt", "Thông tin tham chiếu")); }
}
