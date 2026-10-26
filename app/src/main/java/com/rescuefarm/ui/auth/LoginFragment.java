package com.rescuefarm.ui.auth;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import com.google.android.material.snackbar.Snackbar;
import com.rescuefarm.R;
import com.rescuefarm.service.auth.GoogleCredentialService;


public class LoginFragment extends Fragment {
    private AuthViewModel viewModel;
    private ProgressBar progressBar;
    private InlinePasswordCaptcha inlineCaptcha;
    private boolean captchaSubmissionPending;
    private final com.rescuefarm.service.auth.PasswordCaptchaToken captcha = new com.rescuefarm.service.auth.PasswordCaptchaToken();
    private final Handler captchaClock = new Handler(Looper.getMainLooper());
    private final Runnable expireCaptcha = () -> { captcha.clear(); if(inlineCaptcha!=null)inlineCaptcha.reset();if(getView()!=null)renderCaptcha(getView()); };

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
        View firebaseSetupNotice = view.findViewById(R.id.firebaseSetupNotice);
        firebaseSetupNotice.setVisibility(viewModel.isFirebaseAvailable() ? View.GONE : View.VISIBLE);

        EditText emailInput = view.findViewById(R.id.emailInput);
        EditText passwordInput = view.findViewById(R.id.passwordInput);
        captcha.clear();captchaSubmissionPending=false;renderCaptcha(view);
        inlineCaptcha=new InlinePasswordCaptcha(view.findViewById(R.id.passwordCaptchaWebView),view.findViewById(R.id.passwordCaptchaStatus),new InlinePasswordCaptcha.Listener(){
            public void onToken(String token){
                captcha.accept(token,textOf(emailInput),SystemClock.elapsedRealtime());
                renderCaptcha(view);captchaClock.removeCallbacks(expireCaptcha);
                captchaClock.postDelayed(expireCaptcha,com.rescuefarm.service.auth.PasswordCaptchaToken.MAX_AGE_MS);
            }
            public void onInvalidated(){captcha.clear();captchaClock.removeCallbacks(expireCaptcha);renderCaptcha(view);}
        });
        emailInput.addTextChangedListener(new android.text.TextWatcher(){
            public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            public void onTextChanged(CharSequence s,int start,int before,int count){captchaClock.removeCallbacks(expireCaptcha);captcha.clear();if(inlineCaptcha!=null)inlineCaptcha.reset();renderCaptcha(view);}
            public void afterTextChanged(android.text.Editable s){}
        });
        view.findViewById(R.id.loginButton).setOnClickListener(unused -> {
            String email=textOf(emailInput),password=textOf(passwordInput);
            if(AuthValidator.validateLogin(email,password)!=null){viewModel.signIn(email,password,"");return;}
            String token=captcha.consume(email,SystemClock.elapsedRealtime());captchaClock.removeCallbacks(expireCaptcha);
            captchaSubmissionPending=!token.isEmpty();renderCaptcha(view);
            viewModel.signIn(email,password,token);
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
        boolean loading=state.getStatus()==AuthScreenState.Status.LOADING;
        // Keep the solved widget until its response has been checked on the server.
        if(!loading&&captchaSubmissionPending){captchaSubmissionPending=false;if(inlineCaptcha!=null)inlineCaptcha.reset();renderCaptcha(view);}
        if(inlineCaptcha!=null)inlineCaptcha.setBusy(loading);
        for(int id:new int[]{R.id.loginButton,R.id.googleSignInButton,R.id.registerButton,
                R.id.forgotPasswordButton,R.id.continueAsGuestButton,R.id.emailInput,R.id.passwordInput})view.findViewById(id).setEnabled(!loading);
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

    private void renderCaptcha(View view){
        boolean ready=captcha.isReady(textOf(view.findViewById(R.id.emailInput)),SystemClock.elapsedRealtime());
        TextView status=view.findViewById(R.id.passwordCaptchaStatus);
        status.setVisibility(ready?View.GONE:View.VISIBLE);
        if(!ready)status.setText(R.string.password_captcha_hint);
    }
    @Override public void onDestroyView(){captchaClock.removeCallbacks(expireCaptcha);captcha.clear();if(inlineCaptcha!=null){inlineCaptcha.destroy();inlineCaptcha=null;}super.onDestroyView();}
}
