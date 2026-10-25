package com.rescuefarm.ui.auth;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;

import com.rescuefarm.data.repository.AuthRepository;
import com.rescuefarm.data.repository.GuestSessionRepository;
import com.rescuefarm.domain.enums.CustomerType;
import com.rescuefarm.domain.enums.UserRole;
import com.rescuefarm.domain.model.Customer;
import com.rescuefarm.domain.model.User;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class AuthViewModelTest {
    @Rule
    public final InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    private FakeAuthRepository authRepository;
    private FakeGuestSessionRepository guestSessionRepository;
    private AuthViewModel viewModel;

    @Before
    public void setUp() {
        authRepository = new FakeAuthRepository();
        guestSessionRepository = new FakeGuestSessionRepository();
        viewModel = new AuthViewModel(authRepository, guestSessionRepository);
    }

    @Test
    public void firstLaunch_routesToOnboarding() {
        viewModel.determineLaunchRoute();
        assertEquals(
                AuthScreenState.Status.ONBOARDING_REQUIRED,
                viewModel.getScreenState().getValue().getStatus()
        );
    }

    @Test
    public void guestMode_createsLocalGuestIdAndCompletesOnboarding() {
        viewModel.continueAsGuest();
        AuthScreenState state = viewModel.getScreenState().getValue();

        assertEquals(AuthScreenState.Status.GUEST, state.getStatus());
        assertEquals("guest-test-id", state.getGuestId());
        assertTrue(guestSessionRepository.onboardingCompleted);
    }

    @Test public void guestModeEndsAnyExistingAuthenticatedSession() {
        authRepository.authenticated = true;
        viewModel.continueAsGuest();
        org.junit.Assert.assertFalse(authRepository.isAuthenticated());
        assertEquals(AuthScreenState.Status.GUEST, viewModel.getScreenState().getValue().getStatus());
    }

    @Test
    public void validLogin_usesRepositoryAndEmitsAuthenticatedState() {
        viewModel.signIn("customer@example.com", "strongPass123", "synthetic-response");
        assertTrue(authRepository.signInCalled);
        assertEquals(
                AuthScreenState.Status.AUTHENTICATED,
                viewModel.getScreenState().getValue().getStatus()
        );
    }

    @Test public void passwordLoginWithoutCaptchaNeverCallsRepository(){
        viewModel.signIn("customer@example.com","strongPass123");
        org.junit.Assert.assertFalse(authRepository.signInCalled);
        assertEquals(AuthScreenState.Status.ERROR,viewModel.getScreenState().getValue().getStatus());
        assertTrue(viewModel.getScreenState().getValue().getMessage().contains("reCAPTCHA"));
    }

    @Test public void googleLoginDoesNotRequirePasswordCaptcha(){
        viewModel.signInWithGoogleIdToken("synthetic-google-token");
        assertEquals(AuthScreenState.Status.AUTHENTICATED,viewModel.getScreenState().getValue().getStatus());
        org.junit.Assert.assertFalse(authRepository.signInCalled);
    }

    @Test
    public void sellerRegistration_preservesSelectedRole() {
        viewModel.register(
                "seller@example.com",
                "strongPass123",
                "Seller Name",
                "0912345678",
                UserRole.SELLER
        );

        assertEquals(UserRole.SELLER, authRepository.registeredRole);
        assertEquals(
                AuthScreenState.Status.AUTHENTICATED,
                viewModel.getScreenState().getValue().getStatus()
        );
    }

    private static final class FakeGuestSessionRepository implements GuestSessionRepository {
        private boolean onboardingCompleted;

        @Override
        public String getOrCreateGuestId() { return "guest-test-id"; }

        @Override
        public boolean hasCompletedOnboarding() { return onboardingCompleted; }

        @Override
        public void markOnboardingCompleted() { onboardingCompleted = true; }
    }

    private static final class FakeAuthRepository implements AuthRepository {
        @Override public void signInWithCaptcha(String email,String password,String responseToken,AuthCallback callback){
            assertEquals("synthetic-response",responseToken);signIn(email,password,callback);
        }
        private boolean authenticated;
        private boolean signInCalled;
        private UserRole registeredRole;

        @Override
        public boolean isAvailable() { return true; }

        @Override
        public boolean isAuthenticated() { return authenticated; }

        @Override
        public String getCurrentUserId() { return null; }

        @Override
        public void restoreSession(AuthCallback callback) { }

        @Override
        public void signIn(String email, String password, AuthCallback callback) {
            signInCalled = true;
            callback.onSuccess(customer());
        }

        @Override
        public void register(String email, String password, String fullName, String phone, UserRole role, AuthCallback callback) {
            registeredRole = role;
            callback.onSuccess(customer());
        }

        @Override
        public void signInWithGoogleIdToken(String idToken, AuthCallback callback) {
            callback.onSuccess(customer());
        }

        @Override
        public void sendPasswordResetEmail(String email, ActionCallback callback) {
            callback.onSuccess();
        }

        @Override
        public void signOut() { authenticated = false; }

        private User customer() {
            return new Customer(
                    "customer-1",
                    "customer@example.com",
                    "Customer",
                    CustomerType.INDIVIDUAL
            );
        }
    }
}
