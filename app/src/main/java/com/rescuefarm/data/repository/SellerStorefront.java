package com.rescuefarm.data.repository;

import java.util.HashMap;
import java.util.Map;

/** Public seller projection; deliberately excludes private account/profile fields. */
public final class SellerStorefront {
    public String sellerId = "", shopName = "", description = "", address = "";
    public String bankName = "", bankAccount = "", accountName = "", qrUrl = "";
    public String avatarUrl = "";
    public double shippingFee;
    public double latitude, longitude;
    public boolean shippingConfigured;
    public boolean configured;

    public boolean acceptsTransfer() {
        return !bankName.isEmpty() && !bankAccount.isEmpty() && !accountName.isEmpty();
    }
    public Map<String, Object> toMap() {
        Map<String, Object> m = new HashMap<>();
        m.put("sellerId", sellerId); m.put("shopName", shopName); m.put("shopDescription", description);
        m.put("address", address); m.put("shippingFee", shippingFee);
        m.put("latitude", latitude); m.put("longitude", longitude);
        m.put("shopAvatarUrl", avatarUrl);
        return m;
    }
    public static SellerStorefront from(String id, Map<String, Object> data) {
        SellerStorefront value = new SellerStorefront(); value.sellerId = id;
        if (data == null) return value;
        value.configured = true;
        value.shopName = text(data, "shopName"); value.description = text(data, "shopDescription");
        value.address = text(data, "address"); value.bankName = text(data, "bankCode");
        value.bankAccount = text(data, "bankAccountNumber"); value.accountName = text(data, "bankAccountName");
        value.qrUrl = text(data, "qrUrl");
        value.avatarUrl = text(data, "shopAvatarUrl");
        Object fee = data.get("shippingFee"); value.shippingFee = fee instanceof Number ? ((Number) fee).doubleValue() : 0;
        value.shippingConfigured = fee instanceof Number && Double.isFinite(value.shippingFee) && value.shippingFee >= 0;
        Object lat = data.get("latitude"), lng = data.get("longitude");
        value.latitude = lat instanceof Number ? ((Number) lat).doubleValue() : 0;
        value.longitude = lng instanceof Number ? ((Number) lng).doubleValue() : 0;
        return value;
    }
    private static String text(Map<String, Object> data, String key) {
        Object v = data.get(key); return v instanceof String ? ((String) v).trim() : "";
    }
    public void validate() {
        if (shopName.isEmpty() || shopName.length() > 120 || description.length() > 2000
                || address.isEmpty() || address.length() > 500 || !Double.isFinite(shippingFee)
                || shippingFee < 0 || shippingFee > 1000000 || shippingFee != Math.rint(shippingFee))
            throw new IllegalArgumentException("Nhập tên cửa hàng, địa chỉ và phí giao hàng nguyên từ 0 đến 1.000.000 đ.");
        boolean bankEntered = !bankName.isEmpty() || !bankAccount.isEmpty() || !accountName.isEmpty() || !qrUrl.isEmpty();
        if (bankName.length() > 24 || bankAccount.length() > 40 || accountName.length() > 120 || qrUrl.length() > 2048
                || bankEntered && (!acceptsTransfer() || (!qrUrl.isEmpty() && !qrUrl.startsWith("https://"))))
            throw new IllegalArgumentException("Nhập đủ mã ngân hàng, số tài khoản và chủ tài khoản. Ảnh QR tùy chọn phải là HTTPS.");
    }
}
