package com.rescuefarm.ui.profile;

public final class ProfileValidator {
    private ProfileValidator() { }

    public static String validateProfile(String fullName, String phone) {
        return com.rescuefarm.service.profile.ProfileInputPolicy.profile(fullName, phone);
    }

    public static String validateBusiness(String companyName, String taxCode) {
        return com.rescuefarm.service.profile.ProfileInputPolicy.business(companyName, taxCode);
    }

    public static String validateAddress(String receiverName, String phone, String province,
            String district, String ward, String street) {
        if (clean(receiverName).length() < 2) return "Vui lòng nhập tên người nhận.";
        if (!isPhone(phone)) return "Số điện thoại người nhận không hợp lệ.";
        if (clean(province).isEmpty()) return "Vui lòng nhập tỉnh/thành phố.";
        if (clean(district).isEmpty()) return "Vui lòng nhập quận/huyện.";
        if (clean(ward).isEmpty()) return "Vui lòng nhập phường/xã.";
        if (clean(street).length() < 3) return "Vui lòng nhập số nhà, tên đường.";
        return null;
    }

    public static String validateSellerApplication(String representativeName, String shopName,
            String address, String proofImageUrl) {
        if (clean(representativeName).length() < 2) return "Vui lòng nhập người đại diện.";
        if (clean(shopName).length() < 2) return "Vui lòng nhập tên cửa hàng.";
        if (clean(address).length() < 5) return "Vui lòng nhập địa chỉ kinh doanh.";
        String proof = clean(proofImageUrl);
        if (!proof.startsWith("https://")) return "Ảnh minh chứng phải là URL HTTPS hợp lệ.";
        return null;
    }

    private static boolean isPhone(String phone) { return clean(phone).matches("[0-9]{9,11}"); }
    private static String clean(String value) { return value == null ? "" : value.trim(); }
}
