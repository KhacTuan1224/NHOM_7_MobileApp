package com.rescuefarm.ui.profile;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.navigation.NavController;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.bumptech.glide.Glide;
import com.rescuefarm.R;
import com.rescuefarm.domain.enums.UserRole;
import com.rescuefarm.domain.model.Seller;
import com.rescuefarm.domain.model.User;

public class ProfileFragment extends Fragment {
    private ProfileViewModel viewModel;
    private TextView nameView, emailView, roleView, statusView;
    private ImageView avatarView;
    private View progress, addressButton, sellerButton, favoritesButton;

    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater,
            @Nullable ViewGroup container, @Nullable Bundle state) {
        return inflater.inflate(R.layout.fragment_profile, container, false);
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        nameView = view.findViewById(R.id.profileName);
        emailView = view.findViewById(R.id.profileEmail);
        roleView = view.findViewById(R.id.profileRole);
        statusView = view.findViewById(R.id.profileStatus);
        avatarView = view.findViewById(R.id.profileAvatar);
        progress = view.findViewById(R.id.profileProgress);
        addressButton = view.findViewById(R.id.addressesButton);
        sellerButton = view.findViewById(R.id.sellerProfileButton);
        favoritesButton = view.findViewById(R.id.favoritesButton);
        view.findViewById(R.id.editProfileButton).setOnClickListener(
                Navigation.createNavigateOnClickListener(R.id.action_profileFragment_to_editProfileFragment));
        addressButton.setOnClickListener(Navigation.createNavigateOnClickListener(
                R.id.action_profileFragment_to_addressListFragment));
        sellerButton.setOnClickListener(Navigation.createNavigateOnClickListener(
                R.id.action_profileFragment_to_sellerProfileFragment));
        favoritesButton.setOnClickListener(Navigation.createNavigateOnClickListener(
                R.id.action_profileFragment_to_favoriteFragment));
        view.findViewById(R.id.notificationsButton).setOnClickListener(
                Navigation.createNavigateOnClickListener(R.id.action_profileFragment_to_notificationFragment));
        viewModel = new ViewModelProvider(this, new ProfileViewModelFactory(requireContext()))
                .get(ProfileViewModel.class);
        view.findViewById(R.id.logoutButton).setOnClickListener(unused -> confirmSignOut());
        viewModel.getState().observe(getViewLifecycleOwner(), value -> render(value));
        viewModel.observeProfile().observe(getViewLifecycleOwner(), user -> {
            if (user != null) renderProfile(user);
        });
        viewModel.loadProfile();
    }

    private void confirmSignOut() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.logout_title)
                .setMessage(R.string.logout_confirmation)
                .setNegativeButton(R.string.cancel_action, null)
                .setPositiveButton(R.string.logout_action, (dialog, which) -> {
                    viewModel.signOut(new com.rescuefarm.data.repository.AuthRepository.ActionCallback() {
                        @Override public void onSuccess() { if (isAdded()) navigateToLogin(); }
                        @Override public void onError(com.rescuefarm.data.repository.AuthRepository.ErrorCode error,
                                String message) { if (isAdded()) navigateToLogin(); }
                    });
                })
                .show();
    }

    private void navigateToLogin() {
        NavController navController = NavHostFragment.findNavController(this);
        NavOptions options = new NavOptions.Builder().setPopUpTo(R.id.main_navigation, true).build();
        navController.navigate(R.id.loginFragment, null, options);
    }

    private void render(ProfileScreenState state) {
        progress.setVisibility(state.getStatus() == ProfileScreenState.Status.LOADING ? View.VISIBLE : View.GONE);
        if (state.getStatus() == ProfileScreenState.Status.ERROR) {
            Toast.makeText(requireContext(), state.getMessage(), Toast.LENGTH_LONG).show(); return;
        }
        if (state.getStatus() != ProfileScreenState.Status.PROFILE) return;
        renderProfile(state.getUser());
    }

    private void renderProfile(User user) {
        if (user == null) return;
        nameView.setText(user.getFullName()); emailView.setText(user.getEmail());
        String avatarUrl = user.getAvatarUrl();
        if (avatarUrl != null && !avatarUrl.trim().isEmpty()) {
            avatarView.setPadding(0, 0, 0, 0);
            avatarView.setImageTintList(null);
            Glide.with(this).load(avatarUrl.trim()).circleCrop()
                    .placeholder(R.drawable.ic_nav_profile).error(R.drawable.ic_nav_profile)
                    .into(avatarView);
        } else {
            Glide.with(this).clear(avatarView);
            avatarView.setPadding(16, 16, 16, 16);
            avatarView.setImageResource(R.drawable.ic_nav_profile);
            avatarView.setImageTintList(android.content.res.ColorStateList.valueOf(
                    requireContext().getColor(R.color.rescue_primary)));
        }
        roleView.setText(user.getRole().name());
        addressButton.setVisibility(View.VISIBLE);
        sellerButton.setVisibility(user.getRole() == UserRole.SELLER ? View.VISIBLE : View.GONE);
        favoritesButton.setVisibility(user.getRole() == UserRole.CUSTOMER ? View.VISIBLE : View.GONE);
        if (user instanceof Seller) {
            Seller seller = (Seller) user;
            statusView.setText(seller.canSell()
                    ? R.string.seller_approved_message : R.string.seller_pending_message);
        } else statusView.setText(user.getRole() == UserRole.ADMIN
                ? R.string.admin_access_message : R.string.customer_profile_message);
    }
}
