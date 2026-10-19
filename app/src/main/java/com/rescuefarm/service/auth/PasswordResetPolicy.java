package com.rescuefarm.service.auth;

/** Public reset responses never disclose whether an account exists or is disabled. */
public final class PasswordResetPolicy {
    public static final long RESEND_DELAY_MS = 60_000L;
    public static final long RETRY_DELAY_MS = 5_000L;
    public static final long RATE_LIMIT_DELAY_MS = 300_000L;
    public static final String ACCEPTED_MESSAGE = "Nếu email này đã được đăng ký, bạn sẽ nhận được liên kết đặt lại mật khẩu. Hãy kiểm tra cả thư rác.";
    private PasswordResetPolicy() { }

    public static boolean hideAccountStatus(String code) {
        return "ERROR_USER_NOT_FOUND".equals(code) || "ERROR_USER_DISABLED".equals(code)
                || "EMAIL_NOT_FOUND".equals(code) || "USER_DISABLED".equals(code);
    }
}
