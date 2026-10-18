package com.rescuefarm.ui.auth;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;
import com.rescuefarm.data.repository.AuthRepository;
import com.rescuefarm.service.auth.PasswordResetPolicy;
import java.util.Locale;
import java.util.function.LongSupplier;

/** Kept separate from login state so an auth event cannot hide or reset the email confirmation. */
public final class PasswordResetViewModel extends ViewModel {
    public static final class State {
        public final boolean sending, sent;
        public final String email, error;
        private State(boolean sending, boolean sent, String email, String error) {
            this.sending = sending; this.sent = sent; this.email = email; this.error = error;
        }
    }
    private final AuthRepository repository;
    private final SavedStateHandle saved;
    private final LongSupplier clock;
    private final MutableLiveData<State> state = new MutableLiveData<>();
    private boolean sending, closed;

    public PasswordResetViewModel(AuthRepository repository, SavedStateHandle saved) {
        this(repository, saved, System::currentTimeMillis);
    }
    public PasswordResetViewModel(AuthRepository repository, SavedStateHandle saved, LongSupplier clock) {
        this.repository = repository; this.saved = saved; this.clock = clock; emit("");
    }
    public LiveData<State> getState() { return state; }
    public String getEmail() { String email = saved.get("reset.email"); return email == null ? "" : email; }
    public long remainingSeconds() {
        Number next = saved.get("reset.nextAllowedAt");
        return next == null ? 0 : Math.max(0, Math.min(300, (next.longValue() - clock.getAsLong() + 999) / 1000));
    }
    private void emit(String error) {
        state.setValue(new State(sending, Boolean.TRUE.equals(saved.get("reset.sent")), getEmail(), error));
    }
    public void send(String input) {
        if (closed || sending) return;
        String error = AuthValidator.validateEmail(input);
        if (error != null) { emit(error); return; }
        if (remainingSeconds() > 0) { emit("Vui lòng đợi hết thời gian chờ trước khi gửi lại."); return; }
        String email = input.trim().toLowerCase(Locale.ROOT);
        saved.set("reset.email", email);
        sending = true; emit("");
        repository.sendPasswordResetEmail(email, new AuthRepository.ActionCallback() {
            @Override public void onSuccess() {
                if (closed) return;
                sending = false; saved.set("reset.sent", true);
                saved.set("reset.nextAllowedAt", clock.getAsLong() + PasswordResetPolicy.RESEND_DELAY_MS);
                emit("");
            }
            @Override public void onError(AuthRepository.ErrorCode code, String message) {
                if (closed) return;
                sending = false;
                saved.set("reset.nextAllowedAt", clock.getAsLong() + (code == AuthRepository.ErrorCode.TOO_MANY_REQUESTS
                        ? PasswordResetPolicy.RATE_LIMIT_DELAY_MS : PasswordResetPolicy.RETRY_DELAY_MS));
                emit(message == null || message.trim().isEmpty() ? "Không gửi được yêu cầu. Vui lòng thử lại." : message);
            }
        });
    }
    public void changeEmail() {
        if (sending || closed) return;
        saved.set("reset.sent", false); emit("");
    }
    @Override protected void onCleared() { closed = true; super.onCleared(); }
}
