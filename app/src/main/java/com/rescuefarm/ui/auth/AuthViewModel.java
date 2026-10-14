package com.rescuefarm.ui.auth;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.rescuefarm.data.repository.AuthRepository;
import com.rescuefarm.data.repository.GuestSessionRepository;
import com.rescuefarm.domain.enums.UserRole;
import com.rescuefarm.domain.enums.CustomerType;
import com.rescuefarm.domain.model.User;

public class AuthViewModel extends ViewModel {
    private final AuthRepository authRepository;
    private final GuestSessionRepository guestSessionRepository;
    private final com.rescuefarm.service.cart.CartMigrationService migrationService;
    private final MutableLiveData<AuthScreenState> screenState =
            new MutableLiveData<>(AuthScreenState.idle());

    public AuthViewModel(
            AuthRepository authRepository,
            GuestSessionRepository guestSessionRepository
    ) {
        this(authRepository, guestSessionRepository, null);
    }

    public AuthViewModel(
            AuthRepository authRepository,
            GuestSessionRepository guestSessionRepository,
            com.rescuefarm.service.cart.CartMigrationService migrationService
    ) {
        this.authRepository = authRepository;
        this.guestSessionRepository = guestSessionRepository;
        this.migrationService = migrationService;
    }

    public LiveData<AuthScreenState> getScreenState() { return screenState; }

    public boolean isFirebaseAvailable() { return authRepository.isAvailable(); }

    public void determineLaunchRoute() {
        if (authRepository.isAuthenticated()) {
            screenState.setValue(AuthScreenState.loading());
            authRepository.restoreSession(createAuthCallback());
            return;
        }
        if (!guestSessionRepository.hasCompletedOnboarding()) {
            screenState.setValue(AuthScreenState.onboardingRequired());
            return;
        }
        screenState.setValue(AuthScreenState.guest(guestSessionRepository.getOrCreateGuestId()));
    }

    public void completeOnboarding() {
        guestSessionRepository.markOnboardingCompleted();
        screenState.setValue(AuthScreenState.loginRequired());
    }

    public void continueAsGuest() {
        guestSessionRepository.markOnboardingCompleted();
        String guestId = guestSessionRepository.getOrCreateGuestId();
        screenState.setValue(AuthScreenState.guest(guestId));
    }

    public void signIn(String email, String password) {
        String validationError = AuthValidator.validateLogin(email, password);
        if (validationError != null) {
            screenState.setValue(AuthScreenState.error(validationError));
            return;
        }
        screenState.setValue(AuthScreenState.loading());
        authRepository.signIn(email.trim(), password, createAuthCallback());
    }

    public void register(
            String email,
            String password,
            String fullName,
            String phone,
            UserRole role
    ) {
        register(email, password, fullName, phone, role, CustomerType.INDIVIDUAL, "", "");
    }

    public void register(
            String email,
            String password,
            String fullName,
            String phone,
            UserRole role,
            CustomerType customerType,
            String companyName,
            String taxCode
    ) {
        String validationError = AuthValidator.validateRegistration(
                email,
                password,
                fullName,
                phone,
                role,
                customerType,
                companyName,
                taxCode
        );
        if (validationError != null) {
            screenState.setValue(AuthScreenState.error(validationError));
            return;
        }
        screenState.setValue(AuthScreenState.loading());
        authRepository.register(
                email.trim(),
                password,
                fullName.trim(),
                phone.replace(" ", ""),
                role,
                customerType,
                companyName == null ? "" : companyName.trim(),
                taxCode == null ? "" : taxCode.trim(),
                createAuthCallback()
        );
    }

    public void signInWithGoogleIdToken(String idToken) {
        if (idToken == null || idToken.trim().isEmpty()) {
            screenState.setValue(AuthScreenState.error("Google không trả về ID token hợp lệ."));
            return;
        }
        screenState.setValue(AuthScreenState.loading());
        authRepository.signInWithGoogleIdToken(idToken, createAuthCallback());
    }

    public void sendPasswordResetEmail(String email) {
        String validationError = AuthValidator.validateEmail(email);
        if (validationError != null) {
            screenState.setValue(AuthScreenState.error(validationError));
            return;
        }
        screenState.setValue(AuthScreenState.loading());
        authRepository.sendPasswordResetEmail(email.trim(), new AuthRepository.ActionCallback() {
            @Override
            public void onSuccess() {
                screenState.postValue(AuthScreenState.resetEmailSent(
                        "Email đặt lại mật khẩu đã được gửi."
                ));
            }

            @Override
            public void onError(AuthRepository.ErrorCode errorCode, String message) {
                screenState.postValue(AuthScreenState.error(message));
            }
        });
    }

    public void clearTransientState() { screenState.setValue(AuthScreenState.idle()); }

    private AuthRepository.AuthCallback createAuthCallback() {
        return new AuthRepository.AuthCallback() {
            @Override
            public void onSuccess(User user) {
                String currentGuestId = guestSessionRepository.getOrCreateGuestId();
                if (migrationService != null && currentGuestId != null && !currentGuestId.trim().isEmpty() && user != null) {
                    migrationService.migrateGuestCartToUser(currentGuestId, user.getId(), new com.rescuefarm.data.repository.CartRepository.ActionCallback() {
                        @Override
                        public void onSuccess() {
                            screenState.postValue(AuthScreenState.authenticated(user));
                        }

                        @Override
                        public void onError(com.rescuefarm.data.repository.CartRepository.ErrorCode error, String message) {
                            screenState.postValue(AuthScreenState.authenticated(user));
                        }
                    });
                } else {
                    screenState.postValue(AuthScreenState.authenticated(user));
                }
            }

            @Override
            public void onError(AuthRepository.ErrorCode errorCode, String message) {
                screenState.postValue(AuthScreenState.error(message));
            }
        };
    }
}
