package com.rescuefarm.data.remote.firebase;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseAuthWeakPasswordException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.messaging.FirebaseMessaging;
import com.rescuefarm.data.repository.AuthRepository;
import com.rescuefarm.data.repository.UserRepository;
import com.rescuefarm.domain.enums.UserRole;
import com.rescuefarm.domain.enums.CustomerType;
import com.rescuefarm.domain.model.User;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class FirebaseAuthRepository implements AuthRepository {
    private final FirebaseAuth firebaseAuth;
    private final UserRepository userRepository;

    public FirebaseAuthRepository() {
        firebaseAuth = FirebaseAuth.getInstance();
        userRepository = new FirebaseUserRepository();
    }

    @Override
    public boolean isAvailable() { return true; }

    @Override
    public boolean isAuthenticated() { return firebaseAuth.getCurrentUser() != null; }

    @Override
    public String getCurrentUserId() {
        FirebaseUser currentUser = firebaseAuth.getCurrentUser();
        return currentUser == null ? null : currentUser.getUid();
    }

    @Override
    public void restoreSession(AuthCallback callback) {
        FirebaseUser currentUser = firebaseAuth.getCurrentUser();
        if (currentUser == null) {
            callback.onError(ErrorCode.INVALID_CREDENTIALS, "Không tìm thấy phiên đăng nhập.");
            return;
        }
        loadRequiredProfile(currentUser, callback);
    }

    @Override
    public void signIn(String email, String password, AuthCallback callback) {
        firebaseAuth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(result -> loadRequiredProfile(result.getUser(), callback))
                .addOnFailureListener(exception -> notifyFailure(exception, callback));
    }

    @Override
    public void register(String email, String password, String fullName, String phone,
                         UserRole role, AuthCallback callback) {
        register(email, password, fullName, phone, role, CustomerType.INDIVIDUAL,
                "", "", callback);
    }

    @Override
    public void register(
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
        if (role != UserRole.CUSTOMER && role != UserRole.SELLER) {
            callback.onError(ErrorCode.UNKNOWN, "Ứng dụng không cho phép tự đăng ký tài khoản Admin.");
            return;
        }
        firebaseAuth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(result -> {
                    FirebaseUser firebaseUser = result.getUser();
                    if (firebaseUser == null) {
                        callback.onError(ErrorCode.UNKNOWN, "Firebase không trả về người dùng vừa tạo.");
                        return;
                    }
                    createProfile(firebaseUser, fullName, phone, role, customerType,
                            companyName, taxCode, callback);
                })
                .addOnFailureListener(exception -> notifyFailure(exception, callback));
    }

    @Override
    public void signInWithGoogleIdToken(String idToken, AuthCallback callback) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        firebaseAuth.signInWithCredential(credential)
                .addOnSuccessListener(result -> {
                    FirebaseUser firebaseUser = result.getUser();
                    if (firebaseUser == null) {
                        callback.onError(ErrorCode.UNKNOWN, "Google Sign-In không trả về người dùng.");
                        return;
                    }
                    loadOrCreateGoogleProfile(firebaseUser, callback);
                })
                .addOnFailureListener(exception -> notifyFailure(exception, callback));
    }

    @Override
    public void sendPasswordResetEmail(String email, ActionCallback callback) {
        firebaseAuth.sendPasswordResetEmail(email)
                .addOnSuccessListener(unused -> callback.onSuccess())
                .addOnFailureListener(exception -> {
                    ErrorCode errorCode = mapErrorCode(exception);
                    callback.onError(errorCode, userMessageFor(errorCode));
                });
    }

    @Override
    public void signOut() { signOut(null); }

    @Override
    public void signOut(ActionCallback callback) {
        FirebaseUser current = firebaseAuth.getCurrentUser();
        if (current == null) { if (callback != null) callback.onSuccess(); return; }
        final String uid = current.getUid();
        Handler handler = new Handler(Looper.getMainLooper());
        AtomicBoolean completed = new AtomicBoolean(false);
        // Device cleanup is best effort. Offline writes and token retrieval must never trap a session.
        Runnable finish = new Runnable() {
            @Override public void run() {
                if (!completed.compareAndSet(false, true)) return;
                handler.removeCallbacks(this);
                FirebaseUser active = firebaseAuth.getCurrentUser();
                if (active != null && uid.equals(active.getUid())) firebaseAuth.signOut();
                if (callback != null) callback.onSuccess();
            }
        };
        handler.postDelayed(finish, 1500L);
        try {
            FirebaseMessaging.getInstance().getToken().addOnCompleteListener(task -> {
                if (completed.get()) return;
                if (!task.isSuccessful() || task.getResult() == null || task.getResult().isEmpty()) {
                    finish.run();
                    return;
                }
                FirebaseFirestore.getInstance().collection("users").document(uid)
                        .collection("devices").document(hashToken(task.getResult())).delete()
                        .addOnCompleteListener(unused -> finish.run());
            });
        } catch (RuntimeException error) {
            Log.w("FirebaseAuthRepository", "Device cleanup failed during sign out", error);
            finish.run();
        }
    }

    private void loadRequiredProfile(FirebaseUser firebaseUser, AuthCallback callback) {
        if (firebaseUser == null) {
            callback.onError(ErrorCode.PROFILE_UNAVAILABLE, "Không thể đọc tài khoản Firebase hiện tại.");
            return;
        }
        userRepository.getUser(firebaseUser.getUid(), new UserRepository.UserCallback() {
            @Override
            public void onSuccess(User user) { registerUserDevice(user); callback.onSuccess(user); }

            @Override
            public void onError(UserRepository.ProfileError error, String message) {
                signOut();
                callback.onError(mapProfileError(error), message);
            }
        });
    }

    private void loadOrCreateGoogleProfile(FirebaseUser firebaseUser, AuthCallback callback) {
        userRepository.getUser(firebaseUser.getUid(), new UserRepository.UserCallback() {
            @Override
            public void onSuccess(User user) { registerUserDevice(user); callback.onSuccess(user); }

            @Override
            public void onError(UserRepository.ProfileError error, String message) {
                if (error != UserRepository.ProfileError.NOT_FOUND) {
                    if (error == UserRepository.ProfileError.DISABLED) { signOut(); }
                    callback.onError(mapProfileError(error), message);
                    return;
                }
                String displayName = firebaseUser.getDisplayName();
                if (displayName == null || displayName.trim().isEmpty()) {
                    displayName = "Khách hàng RescueFarm";
                }
                createProfile(
                        firebaseUser,
                        displayName,
                        firebaseUser.getPhoneNumber(),
                        UserRole.CUSTOMER,
                        CustomerType.INDIVIDUAL,
                        "",
                        "",
                        callback
                );
            }
        });
    }

    private void createProfile(
            FirebaseUser firebaseUser,
            String fullName,
            String phone,
            UserRole role,
            CustomerType customerType,
            String companyName,
            String taxCode,
            AuthCallback callback
    ) {
        String avatarUrl = firebaseUser.getPhotoUrl() == null
                ? ""
                : firebaseUser.getPhotoUrl().toString();
        userRepository.createRegistrationProfile(
                firebaseUser.getUid(),
                firebaseUser.getEmail(),
                fullName,
                phone,
                avatarUrl,
                role,
                customerType,
                companyName,
                taxCode,
                new UserRepository.UserCallback() {
                    @Override
                    public void onSuccess(User user) { registerUserDevice(user); callback.onSuccess(user); }

                    @Override
                    public void onError(UserRepository.ProfileError error, String message) {
                        firebaseUser.delete().addOnCompleteListener(unused ->
                                callback.onError(mapProfileError(error), message)
                        );
                    }
                }
        );
    }

    private ErrorCode mapProfileError(UserRepository.ProfileError error) {
        if (error == UserRepository.ProfileError.DISABLED) { return ErrorCode.ACCOUNT_DISABLED; }
        if (error == UserRepository.ProfileError.NETWORK) { return ErrorCode.NETWORK; }
        return ErrorCode.PROFILE_UNAVAILABLE;
    }

    private void registerUserDevice(User user) {
        if (user == null || !user.isActive()) return;
        com.rescuefarm.service.notification.FcmDeviceSyncWorker.refreshNow(
                com.google.firebase.FirebaseApp.getInstance().getApplicationContext());
    }

    private String hashToken(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder value = new StringBuilder();
            for (byte part : digest) value.append(String.format(java.util.Locale.ROOT, "%02x", part));
            return value.toString();
        } catch (Exception error) {
            return Integer.toHexString(token.hashCode());
        }
    }

    private void notifyFailure(Exception exception, AuthCallback callback) {
        ErrorCode errorCode = mapErrorCode(exception);
        callback.onError(errorCode, userMessageFor(errorCode));
    }

    private ErrorCode mapErrorCode(Exception exception) {
        if (exception instanceof FirebaseAuthUserCollisionException) { return ErrorCode.EMAIL_ALREADY_IN_USE; }
        if (exception instanceof FirebaseAuthWeakPasswordException) { return ErrorCode.WEAK_PASSWORD; }
        if (exception instanceof FirebaseAuthInvalidUserException) {
            String firebaseErrorCode = ((FirebaseAuthInvalidUserException) exception).getErrorCode();
            return "ERROR_USER_DISABLED".equals(firebaseErrorCode)
                    ? ErrorCode.ACCOUNT_DISABLED
                    : ErrorCode.INVALID_CREDENTIALS;
        }
        if (exception instanceof FirebaseAuthInvalidCredentialsException) { return ErrorCode.INVALID_CREDENTIALS; }
        if (exception instanceof FirebaseNetworkException) { return ErrorCode.NETWORK; }
        return ErrorCode.UNKNOWN;
    }

    private String userMessageFor(ErrorCode errorCode) {
        switch (errorCode) {
            case EMAIL_ALREADY_IN_USE:
                return "Email này đã được sử dụng.";
            case ACCOUNT_DISABLED:
                return "Tài khoản đã bị vô hiệu hóa.";
            case INVALID_CREDENTIALS:
                return "Email hoặc mật khẩu không chính xác.";
            case NETWORK:
                return "Không thể kết nối Firebase. Hãy kiểm tra mạng và thử lại.";
            case WEAK_PASSWORD:
                return "Mật khẩu chưa đủ mạnh.";
            case PROFILE_UNAVAILABLE:
                return "Không thể tải hồ sơ người dùng.";
            default:
                return "Đã xảy ra lỗi xác thực. Vui lòng thử lại.";
        }
    }
}
