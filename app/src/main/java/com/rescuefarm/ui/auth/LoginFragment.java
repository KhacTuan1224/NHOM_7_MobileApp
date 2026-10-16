package com.rescuefarm.ui.auth;

import android.os.Bundle;
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

import com.google.android.material.snackbar.Snackbar;
import com.rescuefarm.R;
import com.rescuefarm.service.auth.GoogleCredentialService;


public class LoginFragment extends Fragment {
    private AuthViewModel viewModel;
    private ProgressBar progressBar;

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
        view.findViewById(R.id.loginButton).setOnClickListener(unused ->
                viewModel.signIn(textOf(emailInput), textOf(passwordInput))
        );
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
