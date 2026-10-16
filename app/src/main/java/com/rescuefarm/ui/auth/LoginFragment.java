package com.rescuefarm.ui.auth;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ProgressBar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import com.google.firebase.FirebaseException;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.PhoneAuthCredential;
import com.google.firebase.auth.PhoneAuthOptions;
import com.google.firebase.auth.PhoneAuthProvider;
import com.google.android.material.snackbar.Snackbar;
import com.rescuefarm.R;
import com.rescuefarm.service.auth.GoogleCredentialService;

import java.util.concurrent.TimeUnit;

public class LoginFragment extends Fragment {
    private static final String TAG = "LoginFragment";
    private AuthViewModel viewModel;
    private ProgressBar progressBar;
    private String phoneVerificationId;
    private String verifiedPhoneNumber;
    private PhoneAuthProvider.ForceResendingToken resendToken;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_login, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(
                requireActivity(),
                new AuthViewModelFactory(requireContext())
        ).get(AuthViewModel.class);
        progressBar = view.findViewById(R.id.authProgress);
        if (savedInstanceState != null) {
            phoneVerificationId = savedInstanceState.getString("phoneVerificationId");
            verifiedPhoneNumber = savedInstanceState.getString("verifiedPhoneNumber");
            if (phoneVerificationId != null) {
                view.findViewById(R.id.phoneAuthCodeLayout).setVisibility(View.VISIBLE);
                view.findViewById(R.id.verifyPhoneOtpButton).setVisibility(View.VISIBLE);
            }
        }

        View firebaseSetupNotice = view.findViewById(R.id.firebaseSetupNotice);
        firebaseSetupNotice.setVisibility(viewModel.isFirebaseAvailable() ? View.GONE : View.VISIBLE);

        EditText emailInput = view.findViewById(R.id.emailInput);
        EditText passwordInput = view.findViewById(R.id.passwordInput);
        view.findViewById(R.id.loginButton).setOnClickListener(unused ->
                viewModel.signIn(textOf(emailInput), textOf(passwordInput))
        );
        EditText phoneInput = view.findViewById(R.id.phoneAuthPhoneInput);
        EditText codeInput = view.findViewById(R.id.phoneAuthCodeInput);
        if (verifiedPhoneNumber != null) phoneInput.setText(verifiedPhoneNumber);
        View codeLayout = view.findViewById(R.id.phoneAuthCodeLayout);
        View sendCodeButton = view.findViewById(R.id.sendPhoneOtpButton);
        View verifyCodeButton = view.findViewById(R.id.verifyPhoneOtpButton);
        sendCodeButton.setOnClickListener(unused -> sendPhoneVerification(
                view, textOf(phoneInput), sendCodeButton, codeLayout, verifyCodeButton));
        verifyCodeButton.setOnClickListener(unused -> {
            if (phoneVerificationId == null) {
                Snackbar.make(view, R.string.phone_auth_code_expired, Snackbar.LENGTH_LONG).show();
                return;
            }
            String code = textOf(codeInput).trim();
            if (!code.matches("\\d{6}")) {
                codeInput.setError(getString(R.string.phone_auth_code_hint));
                return;
            }
            viewModel.signInWithPhoneCredential(
                    PhoneAuthProvider.getCredential(phoneVerificationId, code));
        });
        view.findViewById(R.id.googleSignInButton).setOnClickListener(unused ->
                requestGoogleSignIn(view)
        );
        view.findViewById(R.id.registerButton).setOnClickListener(unused ->
                NavHostFragment.findNavController(this).navigate(
                        R.id.action_loginFragment_to_registrationChoice
                )
        );
        view.findViewById(R.id.forgotPasswordButton).setOnClickListener(unused ->
                NavHostFragment.findNavController(this).navigate(
                        R.id.action_loginFragment_to_forgotPasswordFragment
                )
        );
        view.findViewById(R.id.continueAsGuestButton).setOnClickListener(unused ->
                viewModel.continueAsGuest()
        );
        viewModel.getScreenState().observe(getViewLifecycleOwner(), state -> renderState(view, state));
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString("phoneVerificationId", phoneVerificationId);
        outState.putString("verifiedPhoneNumber", verifiedPhoneNumber);
    }

    private void sendPhoneVerification(View anchor, String rawPhone, View sendButton,
            View codeLayout, View verifyButton) {
        String phoneNumber = normalizePhoneNumber(rawPhone);
        if (phoneNumber == null) {
            Snackbar.make(anchor, R.string.phone_auth_invalid_number, Snackbar.LENGTH_LONG).show();
            return;
        }
        if (!viewModel.isFirebaseAvailable()) {
            Snackbar.make(anchor, R.string.firebase_setup_notice, Snackbar.LENGTH_LONG).show();
            return;
        }

        if (!phoneNumber.equals(verifiedPhoneNumber)) {
            phoneVerificationId = null;
            resendToken = null;
            codeLayout.setVisibility(View.GONE);
            verifyButton.setVisibility(View.GONE);
        }
        verifiedPhoneNumber = phoneNumber;
        sendButton.setEnabled(false);
        ((com.google.android.material.button.MaterialButton) sendButton)
                .setText("Đang gửi mã…");
        FirebaseAuth auth = FirebaseAuth.getInstance();
        auth.setLanguageCode("vi");
        PhoneAuthOptions.Builder options = PhoneAuthOptions.newBuilder(auth)
                .setPhoneNumber(phoneNumber)
                .setTimeout(60L, TimeUnit.SECONDS)
                .setActivity(requireActivity())
                .setCallbacks(new PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                    @Override
                    public void onVerificationCompleted(@NonNull PhoneAuthCredential credential) {
                        if (!isAdded()) return;
                        viewModel.signInWithPhoneCredential(credential);
                    }

                    @Override
                    public void onVerificationFailed(@NonNull FirebaseException error) {
                        if (!isAdded() || getView() == null) return;
                        Log.e(TAG, "Phone verification failed: " + firebasePhoneErrorCode(error), error);
                        sendButton.setEnabled(true);
                        ((com.google.android.material.button.MaterialButton) sendButton)
                                .setText(R.string.phone_auth_send_code);
                        Snackbar.make(anchor, firebasePhoneErrorMessage(error), Snackbar.LENGTH_LONG).show();
                    }

                    @Override
                    public void onCodeSent(@NonNull String id,
                            @NonNull PhoneAuthProvider.ForceResendingToken token) {
                        if (!isAdded() || getView() == null) return;
                        phoneVerificationId = id;
                        resendToken = token;
                        codeLayout.setVisibility(View.VISIBLE);
                        verifyButton.setVisibility(View.VISIBLE);
                        sendButton.setEnabled(true);
                        ((com.google.android.material.button.MaterialButton) sendButton)
                                .setText(R.string.phone_auth_send_code);
                        Snackbar.make(anchor, R.string.phone_auth_code_sent, Snackbar.LENGTH_LONG).show();
                    }

                    @Override
                    public void onCodeAutoRetrievalTimeOut(@NonNull String id) {
                        phoneVerificationId = id;
                    }
                });
        if (resendToken != null) options.setForceResendingToken(resendToken);
        try {
            PhoneAuthProvider.verifyPhoneNumber(options.build());
        } catch (RuntimeException error) {
            Log.e(TAG, "Could not start phone verification", error);
            sendButton.setEnabled(true);
            ((com.google.android.material.button.MaterialButton) sendButton)
                    .setText(R.string.phone_auth_send_code);
            Snackbar.make(anchor, "Không thể bắt đầu xác minh: " + safeErrorMessage(error),
                    Snackbar.LENGTH_LONG).show();
        }
    }

    private static String firebasePhoneErrorCode(Exception error) {
        if (error instanceof FirebaseAuthException) {
            return ((FirebaseAuthException) error).getErrorCode();
        }
        return error.getClass().getSimpleName();
    }

    private static String firebasePhoneErrorMessage(FirebaseException error) {
        String code = firebasePhoneErrorCode(error);
        String normalized = code == null ? "" : code.toUpperCase(java.util.Locale.ROOT);
        String explanation;
        if (error instanceof FirebaseAuthInvalidCredentialsException
                || normalized.contains("INVALID_PHONE_NUMBER")) {
            explanation = "Số điện thoại không hợp lệ. Dùng đúng số test đã khai báo, dạng +84…";
        } else if (normalized.contains("OPERATION_NOT_ALLOWED")) {
            explanation = "Firebase chưa bật Phone provider cho project đang dùng.";
        } else if (normalized.contains("TOO_MANY_REQUESTS") || normalized.contains("QUOTA_EXCEEDED")) {
            explanation = "Firebase đang giới hạn yêu cầu. Chờ rồi thử lại hoặc kiểm tra quota.";
        } else if (normalized.contains("APP_NOT_AUTHORIZED")
                || normalized.contains("INVALID_APP_CREDENTIAL")) {
            explanation = "Ứng dụng chưa được Firebase xác thực. Kiểm tra package name và SHA-1/SHA-256.";
        } else if (normalized.contains("NETWORK")) {
            explanation = "Không kết nối được Firebase. Kiểm tra mạng rồi thử lại.";
        } else {
            explanation = "Firebase từ chối xác minh. Kiểm tra Phone provider, số test và cấu hình ứng dụng.";
        }
        return explanation + " (" + (code == null || code.isEmpty() ? "UNKNOWN" : code) + ")";
    }

    private static String safeErrorMessage(Exception error) {
        String code = firebasePhoneErrorCode(error);
        return code == null || code.isEmpty() ? error.getClass().getSimpleName() : code;
    }

    private static String normalizePhoneNumber(String raw) {
        if (raw == null) return null;
        String value = raw.trim().replaceAll("[\\s()\\-]", "");
        if (value.startsWith("0")) value = "+84" + value.substring(1);
        else if (value.startsWith("84")) value = "+" + value;
        return value.matches("\\+[1-9]\\d{7,14}") ? value : null;
    }

    private void requestGoogleSignIn(View anchorView) {
        if (!viewModel.isFirebaseAvailable()) {
            Snackbar.make(anchorView, R.string.firebase_setup_notice, Snackbar.LENGTH_LONG).show();
            return;
        }
        new GoogleCredentialService().requestGoogleIdToken(
                requireActivity(),
                new GoogleCredentialService.Callback() {
                    @Override
                    public void onIdToken(String idToken) { viewModel.signInWithGoogleIdToken(idToken); }

                    @Override
                    public void onError(String message) {
                        Snackbar.make(anchorView, message, Snackbar.LENGTH_LONG).show();
                    }
                }
        );
    }

    private void renderState(View view, AuthScreenState state) {
        progressBar.setVisibility(
                state.getStatus() == AuthScreenState.Status.LOADING ? View.VISIBLE : View.GONE
        );
        if (state.getStatus() == AuthScreenState.Status.ERROR && state.getMessage() != null) {
            Snackbar.make(view, state.getMessage(), Snackbar.LENGTH_LONG).show();
            viewModel.clearTransientState();
        } else if (state.getStatus() == AuthScreenState.Status.AUTHENTICATED
                || state.getStatus() == AuthScreenState.Status.GUEST) {
            viewModel.clearTransientState();
            NavHostFragment.findNavController(this).navigate(
                    R.id.action_loginFragment_to_guestHomeFragment
            );
        }
    }

    private static String textOf(EditText editText) {
        return editText.getText() == null ? "" : editText.getText().toString();
    }
}
