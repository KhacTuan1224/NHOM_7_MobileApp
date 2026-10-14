package com.rescuefarm.ui.auth;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;

import com.rescuefarm.R;
import com.rescuefarm.domain.enums.UserRole;

public class LaunchLoadingFragment extends Fragment {
    private static final long MINIMUM_LOADING_MILLIS = 900L;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private long loadingStartedAt;
    private int pendingActionId;
    private boolean navigationStarted;
    private final Runnable navigationRunnable = this::navigateWhenReady;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_launch_loading, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        loadingStartedAt = SystemClock.elapsedRealtime();
        AuthViewModel viewModel = new ViewModelProvider(
                requireActivity(), new AuthViewModelFactory(requireContext())
        ).get(AuthViewModel.class);
        viewModel.getScreenState().observe(getViewLifecycleOwner(), this::routeForState);

        AuthScreenState currentState = viewModel.getScreenState().getValue();
        if (currentState == null || currentState.getStatus() == AuthScreenState.Status.IDLE) {
            viewModel.determineLaunchRoute();
        } else {
            routeForState(currentState);
        }
    }

    private void routeForState(AuthScreenState state) {
        switch (state.getStatus()) {
            case ONBOARDING_REQUIRED:
            case LOGIN_REQUIRED:
            case ERROR:
                pendingActionId = R.id.action_launchLoading_to_splash;
                break;
            case GUEST:
                pendingActionId = R.id.action_launchLoading_to_guestHome;
                break;
            case AUTHENTICATED:
                UserRole role = state.getUser() == null ? UserRole.CUSTOMER : state.getUser().getRole();
                if (role == UserRole.ADMIN) {
                    pendingActionId = R.id.action_launchLoading_to_adminHome;
                } else if (role == UserRole.SELLER) {
                    pendingActionId = R.id.action_launchLoading_to_guestHome;
                } else {
                    pendingActionId = R.id.action_launchLoading_to_guestHome;
                }
                break;
            case IDLE:
            case LOADING:
            case RESET_EMAIL_SENT:
            default:
                return;
        }

        handler.removeCallbacks(navigationRunnable);
        long elapsed = SystemClock.elapsedRealtime() - loadingStartedAt;
        long remaining = Math.max(0L, MINIMUM_LOADING_MILLIS - elapsed);
        handler.postDelayed(navigationRunnable, remaining);
    }

    private void navigateWhenReady() {
        if (navigationStarted || pendingActionId == 0 || !isAdded()) return;
        NavController navController = NavHostFragment.findNavController(this);
        if (navController.getCurrentDestination() == null
                || navController.getCurrentDestination().getId() != R.id.launchLoadingFragment) {
            return;
        }
        navigationStarted = true;
        navController.navigate(pendingActionId);
    }

    @Override
    public void onDestroyView() {
        handler.removeCallbacks(navigationRunnable);
        super.onDestroyView();
    }
}
