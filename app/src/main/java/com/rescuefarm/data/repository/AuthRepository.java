package com.rescuefarm.data.repository;

import com.rescuefarm.domain.enums.UserRole;
import com.rescuefarm.domain.enums.CustomerType;
import com.rescuefarm.domain.model.User;

public interface AuthRepository {
    enum ErrorCode {
        NOT_CONFIGURED,
        INVALID_CREDENTIALS,
        EMAIL_ALREADY_IN_USE,
        ACCOUNT_DISABLED,
        NETWORK,
        TOO_MANY_REQUESTS,
        WEAK_PASSWORD,
        PROFILE_UNAVAILABLE,
        UNKNOWN
    }

    interface AuthCallback {
        void onSuccess(User user);
        void onError(ErrorCode errorCode, String message);
    }

    interface ActionCallback {
        void onSuccess();
        void onError(ErrorCode errorCode, String message);
    }

    boolean isAvailable();
    boolean isAuthenticated();
    String getCurrentUserId();
    void restoreSession(AuthCallback callback);
    void signIn(String email, String password, AuthCallback callback);
    void register(
            String email,
            String password,
            String fullName,
            String phone,
            UserRole role,
            AuthCallback callback
    );
    default void register(
            String email,
            String password,
            String fullName,
            String phone,
            UserRole role,
            CustomerType customerType,
            String companyName,
            String taxCode,
            AuthCallback callback
    ) {
        register(email, password, fullName, phone, role, callback);
    }
    void signInWithGoogleIdToken(String idToken, AuthCallback callback);
    void sendPasswordResetEmail(String email, ActionCallback callback);
    void signOut();
    default void signOut(ActionCallback callback) {
        signOut(); if (callback != null) callback.onSuccess();
    }
}
