package com.rescuefarm.ui.auth;

import com.rescuefarm.domain.enums.UserRole;
import com.rescuefarm.domain.enums.CustomerType;

import java.util.regex.Pattern;

public final class AuthValidator {
    private static final int MINIMUM_PASSWORD_LENGTH = 8;
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern PHONE_PATTERN = Pattern.compile("^\\+?[0-9]{9,12}$");

    private AuthValidator() { }

    public static String validateLogin(String email, String password) {
        String emailError = validateEmail(email);
        if (emailError != null) { return emailError; }
        if (password == null || password.isEmpty()) { return "Vui lòng nhập mật khẩu."; }
        return null;
    }

    public static String validateRegistration(
            String email,
            String password,
            String fullName,
            String phone,
            UserRole role
    ) {
        return validateRegistration(email, password, fullName, phone, role,
                CustomerType.INDIVIDUAL, "", "");
    }

    public static String validateRegistration(
            String email,
            String password,
            String fullName,
            String phone,
            UserRole role,
            CustomerType customerType,
            String companyName,
            String taxCode
    ) {
        if (fullName == null || fullName.trim().length() < 2) {
            return "Họ tên phải có ít nhất 2 ký tự.";
        }
        String emailError = validateEmail(email);
        if (emailError != null) { return emailError; }
        if (password == null || password.length() < MINIMUM_PASSWORD_LENGTH) {
            return "Mật khẩu phải có ít nhất 8 ký tự.";
        }
        String normalizedPhone = phone == null ? "" : phone.replace(" ", "");
        if (!PHONE_PATTERN.matcher(normalizedPhone).matches()) {
            return "Số điện thoại phải có từ 9 đến 12 chữ số.";
        }
        if (role != UserRole.CUSTOMER && role != UserRole.SELLER) {
            return "Chỉ có thể đăng ký tài khoản Customer hoặc Seller.";
        }
        if (role == UserRole.CUSTOMER && customerType == CustomerType.BUSINESS) {
            if (companyName == null || companyName.trim().length() < 2) {
                return "Vui lòng nhập tên doanh nghiệp.";
            }
            if (taxCode == null || taxCode.trim().isEmpty()) {
                return "Vui lòng nhập mã số thuế.";
            }
            if (companyName.trim().length() > 160) {
                return "Tên doanh nghiệp không được vượt quá 160 ký tự.";
            }
            if (taxCode.trim().length() > 40) {
                return "Mã số thuế không được vượt quá 40 ký tự.";
            }
        }
        return null;
    }

    public static String validateEmail(String email) {
        if (email == null || email.trim().length() > 254 || !EMAIL_PATTERN.matcher(email.trim()).matches()) {
            return "Email không hợp lệ.";
        }
        return null;
    }
}
