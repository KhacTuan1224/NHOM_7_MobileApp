package com.rescuefarm.data.repository;

import com.rescuefarm.domain.enums.UserRole;
import com.rescuefarm.domain.enums.CustomerType;

public class UnavailableAuthRepository implements AuthRepository {
    private static final String SETUP_MESSAGE =
            "Firebase chưa được cấu hình. Hãy thêm app/google-services.json và bật Authentication.";

    @Override
    public boolean isAvailable() { return false; }

    @Override
    public boolean isAuthenticated() { return false; }

    @Override
    public String getCurrentUserId() { return null; }

    @Override
    public void restoreSession(AuthCallback callback) {
        callback.onError(ErrorCode.NOT_CONFIGURED, SETUP_MESSAGE);
    }

    @Override
    public void signIn(String email, String password, AuthCallback callback) {
        callback.onError(ErrorCode.NOT_CONFIGURED, SETUP_MESSAGE);
    }

    @Override
    public void register(String email, String password, String fullName, String phone,
                         UserRole role, AuthCallback callback) {
        callback.onError(ErrorCode.NOT_CONFIGURED, SETUP_MESSAGE);
    }

    @Override
    public void register(String email, String password, String fullName, String phone, UserRole role,
            CustomerType customerType, String companyName, String taxCode, AuthCallback callback) {
        callback.onError(ErrorCode.NOT_CONFIGURED, SETUP_MESSAGE);
    }

    @Override
    public void signInWithGoogleIdToken(String idToken, AuthCallback callback) {
        callback.onError(ErrorCode.NOT_CONFIGURED, SETUP_MESSAGE);
    }

    @Override
    public void sendPasswordResetEmail(String email, ActionCallback callback) {
        callback.onError(ErrorCode.NOT_CONFIGURED, SETUP_MESSAGE);
    }

    @Override
    public void signOut() { }
}
