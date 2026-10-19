package com.rescuefarm.ui.auth;

import static org.junit.Assert.*;
import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.SavedStateHandle;
import com.rescuefarm.data.repository.AuthRepository;
import com.rescuefarm.domain.enums.UserRole;
import com.rescuefarm.service.auth.PasswordResetPolicy;
import java.util.HashMap;
import org.junit.Rule;
import org.junit.Test;

public class PasswordResetViewModelTest {
    @Rule public final InstantTaskExecutorRule tasks = new InstantTaskExecutorRule();
    private final FakeAuth repository = new FakeAuth();
    private final SavedStateHandle saved = new SavedStateHandle();
    private long now = 100000;
    private PasswordResetViewModel model() { return new PasswordResetViewModel(repository, saved, () -> now); }

    @Test public void invalidOrOversizedEmailNeverSends() {
        PasswordResetViewModel vm = model();
        vm.send("bad"); vm.send("x".repeat(255) + "@example.com");
        assertEquals(0, repository.calls); assertFalse(vm.getState().getValue().error.isEmpty());
    }
    @Test public void doubleTapDoesNotSendTwiceAndSuccessIsNotPasswordConfirmation() {
        PasswordResetViewModel vm = model();
        vm.send(" Test@Example.com "); vm.send("test@example.com");
        assertEquals(1, repository.calls); assertEquals("test@example.com", repository.email);
        assertTrue(vm.getState().getValue().sending); assertFalse(vm.getState().getValue().sent);
        repository.pending.onSuccess();
        assertFalse(vm.getState().getValue().sending); assertTrue(vm.getState().getValue().sent);
        assertEquals(60, vm.remainingSeconds());
        vm.send(vm.getEmail()); assertEquals(1, repository.calls);
        now += 60000; vm.send(vm.getEmail()); assertEquals(2, repository.calls);
    }
    @Test public void sentEmailAndCooldownSurviveSavedStateRecreation() {
        PasswordResetViewModel vm = model(); vm.send("user@example.com"); repository.pending.onSuccess();
        HashMap<String, Object> snapshot = new HashMap<>();
        for (String key : saved.keys()) snapshot.put(key, saved.get(key));
        now += 21000;
        PasswordResetViewModel restored = new PasswordResetViewModel(repository, new SavedStateHandle(snapshot), () -> now);
        assertTrue(restored.getState().getValue().sent); assertEquals("user@example.com", restored.getEmail());
        assertEquals(39, restored.remainingSeconds()); assertFalse(restored.getState().getValue().sending);
        restored.send(restored.getEmail()); assertEquals(1, repository.calls);
    }
    @Test public void networkFailureDoesNotPretendEmailWasSentAndAllowsRetry() {
        PasswordResetViewModel vm = model(); vm.send("user@example.com");
        repository.pending.onError(AuthRepository.ErrorCode.NETWORK, "Mất mạng");
        assertFalse(vm.getState().getValue().sent); assertFalse(vm.getState().getValue().sending);
        assertEquals(5, vm.remainingSeconds()); now += 5000; vm.send(vm.getEmail()); assertEquals(2, repository.calls);
    }
    @Test public void throttledResendKeepsConfirmationAndPreservesEmail() {
        PasswordResetViewModel vm = model(); vm.send("user@example.com"); repository.pending.onSuccess();
        now += 60000; vm.send(vm.getEmail());
        repository.pending.onError(AuthRepository.ErrorCode.TOO_MANY_REQUESTS, "Đợi vài phút");
        assertTrue(vm.getState().getValue().sent); assertEquals(300, vm.remainingSeconds());
        vm.changeEmail(); assertFalse(vm.getState().getValue().sent);
        vm.send("another@example.com"); assertEquals(2, repository.calls);
    }
    @Test public void missingAndDisabledAccountHaveSamePublicPolicyButNetworkErrorsDoNot() {
        assertTrue(PasswordResetPolicy.hideAccountStatus("ERROR_USER_NOT_FOUND"));
        assertTrue(PasswordResetPolicy.hideAccountStatus("ERROR_USER_DISABLED"));
        assertFalse(PasswordResetPolicy.hideAccountStatus("ERROR_NETWORK_REQUEST_FAILED"));
        assertFalse(PasswordResetPolicy.hideAccountStatus("ERROR_INVALID_EMAIL"));
    }
    private static final class FakeAuth implements AuthRepository {
        int calls; String email; ActionCallback pending;
        @Override public boolean isAvailable() { return true; }
        @Override public boolean isAuthenticated() { return false; }
        @Override public String getCurrentUserId() { return null; }
        @Override public void restoreSession(AuthCallback callback) { }
        @Override public void signIn(String email, String password, AuthCallback callback) { }
        @Override public void register(String email, String password, String name, String phone, UserRole role, AuthCallback callback) { }
        @Override public void signInWithGoogleIdToken(String token, AuthCallback callback) { }
        @Override public void sendPasswordResetEmail(String email, ActionCallback callback) { calls++; this.email = email; pending = callback; }
        @Override public void signOut() { }
    }
}
