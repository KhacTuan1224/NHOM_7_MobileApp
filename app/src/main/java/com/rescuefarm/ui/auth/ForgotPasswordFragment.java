package com.rescuefarm.ui.auth;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;
import com.rescuefarm.R;

public class ForgotPasswordFragment extends Fragment {
    private PasswordResetViewModel viewModel;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable countdown = new Runnable() {
        @Override public void run() {
            if (getView() == null) return;
            renderButtons(getView()); handler.postDelayed(this, 1000);
        }
    };

    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater,
            @Nullable ViewGroup container, @Nullable Bundle state) {
        return inflater.inflate(R.layout.fragment_forgot_password, container, false);
    }
    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        viewModel = new ViewModelProvider(this, new PasswordResetViewModelFactory(requireContext()))
                .get(PasswordResetViewModel.class);
        EditText email = view.findViewById(R.id.emailInput);
        email.setText(viewModel.getEmail());
        View.OnClickListener send = unused -> viewModel.send(email.getText() == null ? "" : email.getText().toString());
        view.findViewById(R.id.sendResetEmailButton).setOnClickListener(send);
        email.setOnEditorActionListener((field, action, event) -> {
            if (action != EditorInfo.IME_ACTION_SEND) return false;
            viewModel.send(email.getText() == null ? "" : email.getText().toString()); return true;
        });
        view.findViewById(R.id.cancelResetButton).setOnClickListener(unused -> backToLogin());
        view.findViewById(R.id.successBackToLoginButton).setOnClickListener(unused -> backToLogin());
        view.findViewById(R.id.resetResendButton).setOnClickListener(unused -> viewModel.send(viewModel.getEmail()));
        view.findViewById(R.id.resetChangeEmailButton).setOnClickListener(unused -> {
            viewModel.changeEmail(); email.requestFocus();
        });
        view.findViewById(R.id.resetOpenEmailButton).setOnClickListener(unused -> {
            try {
                Intent inbox = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_EMAIL);
                startActivity(Intent.createChooser(inbox, getString(R.string.reset_open_email)));
            } catch (ActivityNotFoundException | SecurityException error) {
                Snackbar.make(view, R.string.reset_no_email_app, Snackbar.LENGTH_LONG).show();
            }
        });
        viewModel.getState().observe(getViewLifecycleOwner(), value -> {
            view.findViewById(R.id.resetRequestContainer).setVisibility(value.sent ? View.GONE : View.VISIBLE);
            view.findViewById(R.id.resetSuccessContainer).setVisibility(value.sent ? View.VISIBLE : View.GONE);
            view.findViewById(R.id.authProgress).setVisibility(value.sending && !value.sent ? View.VISIBLE : View.GONE);
            view.findViewById(R.id.resetResendProgress).setVisibility(value.sending && value.sent ? View.VISIBLE : View.GONE);
            ((TextView) view.findViewById(R.id.resetSentEmail)).setText(value.email);
            TextView error = view.findViewById(value.sent ? R.id.resetSuccessError : R.id.resetRequestError);
            error.setText(value.error); error.setVisibility(value.error.isEmpty() ? View.GONE : View.VISIBLE);
            view.findViewById(value.sent ? R.id.resetRequestError : R.id.resetSuccessError).setVisibility(View.GONE);
            email.setEnabled(!value.sending); renderButtons(view);
        });
    }
    private void renderButtons(View view) {
        PasswordResetViewModel.State state = viewModel.getState().getValue();
        if (state == null) return;
        long remaining = viewModel.remainingSeconds();
        boolean enabled = !state.sending && remaining == 0;
        MaterialButton send = view.findViewById(R.id.sendResetEmailButton);
        MaterialButton resend = view.findViewById(R.id.resetResendButton);
        send.setEnabled(enabled); resend.setEnabled(enabled);
        send.setText(state.sending ? getString(R.string.reset_sending) : remaining > 0
                ? getString(R.string.reset_wait_seconds, remaining) : getString(R.string.send_reset_email_action));
        resend.setText(state.sending ? getString(R.string.reset_sending) : remaining > 0
                ? getString(R.string.reset_resend_seconds, remaining) : getString(R.string.reset_resend));
        view.findViewById(R.id.resetChangeEmailButton).setEnabled(!state.sending);
    }
    private void backToLogin() {
        if (!NavHostFragment.findNavController(this).navigateUp())
            NavHostFragment.findNavController(this).navigate(R.id.loginFragment);
    }
    @Override public void onStart() { super.onStart(); handler.removeCallbacks(countdown); handler.post(countdown); }
    @Override public void onStop() { handler.removeCallbacks(countdown); super.onStop(); }
    @Override public void onDestroyView() { handler.removeCallbacks(countdown); super.onDestroyView(); }
}
