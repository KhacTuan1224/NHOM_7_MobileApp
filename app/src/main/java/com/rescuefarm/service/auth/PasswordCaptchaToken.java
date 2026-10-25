package com.rescuefarm.service.auth;

/** Short-lived, in-memory response only. Never persist or log a CAPTCHA response. */
public final class PasswordCaptchaToken {
    public static final long MAX_AGE_MS = 110_000L;
    private String token = "", email = "";
    private long receivedAt;
    public void accept(String value, String ownerEmail, long elapsed) {
        clear();
        if (value == null || value.length() < 10 || value.length() > 8192) return;
        token = value; email = clean(ownerEmail); receivedAt = elapsed;
    }
    public boolean isReady(String ownerEmail, long elapsed) {
        long age = elapsed - receivedAt;
        return !token.isEmpty() && email.equals(clean(ownerEmail)) && age >= 0 && age < MAX_AGE_MS;
    }
    public String consume(String ownerEmail, long elapsed) {
        String result = isReady(ownerEmail, elapsed) ? token : ""; clear(); return result;
    }
    public void clear() { token = ""; email = ""; receivedAt = 0; }
    private static String clean(String value) { return value == null ? "" : value.trim(); }
}
