package com.rescuefarm.ui.profile;
import com.rescuefarm.ui.common.EnumLabels;

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
    private TextView businessInfo;
    private ImageView avatarView;
    private View progress, addressButton, sellerButton, favoritesButton;
    private User profileUser;
    private com.rescuefarm.domain.model.SellerApplication sellerApplication;
    private boolean observingApplication;

    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater,
            @Nullable ViewGroup container, @Nullable Bundle state) {
        return inflater.inflate(R.layout.fragment_profile, container, false);
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        profileUser = null; sellerApplication = null; observingApplication = false;
        nameView = view.findViewById(R.id.profileName);
        emailView = view.findViewById(R.id.profileEmail);
        roleView = view.findViewById(R.id.profileRole);
        statusView = view.findViewById(R.id.profileStatus);
        avatarView = view.findViewById(R.id.profileAvatar);
        businessInfo = new TextView(requireContext()); businessInfo.setVisibility(View.GONE);
        businessInfo.setTextSize(14);
        businessInfo.setTextColor(requireContext().getColor(R.color.rescue_on_surface_variant));
        businessInfo.setPadding(0, Math.round(8 * getResources().getDisplayMetrics().density), 0, 0);
        android.view.ViewGroup form = (android.view.ViewGroup)statusView.getParent(); form.addView(businessInfo, form.indexOfChild(statusView) + 1);
        progress = view.findViewById(R.id.profileProgress);
        addressButton = view.findViewById(R.id.addressesButton);
        addressButton.setVisibility(View.GONE);
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
        com.rescuefarm.ui.common.NotificationBadge.bind(this,
                view.findViewById(R.id.notificationsButton), view.findViewById(R.id.profileNotificationBadge));
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
        profileUser = user;
        nameView.setText(user.getFullName()); emailView.setText(user.getEmail());
        renderVerification();
        if (user instanceof Seller && !observingApplication) {
            observingApplication = true;
            viewModel.observeSellerApplication().observe(getViewLifecycleOwner(), application -> {
                sellerApplication = application;
                renderVerification();
            });
        }
        String avatarUrl = user.getAvatarUrl();
        android.graphics.drawable.Drawable person = androidx.core.content.ContextCompat.getDrawable(
                requireContext(), R.drawable.ic_nav_profile).mutate();
        androidx.core.graphics.drawable.DrawableCompat.setTint(person, android.graphics.Color.WHITE);
        android.graphics.drawable.Drawable placeholder = new android.graphics.drawable.InsetDrawable(
                person, Math.round(16 * getResources().getDisplayMetrics().density));
        avatarView.setPadding(0, 0, 0, 0);
        avatarView.setImageTintList(null);
        if (avatarUrl != null && !avatarUrl.trim().isEmpty()) {
            Glide.with(this).load(avatarUrl.trim()).circleCrop()
                    .placeholder(placeholder).error(placeholder)
                    .into(avatarView);
        } else {
            Glide.with(this).clear(avatarView);
            avatarView.setImageDrawable(placeholder);
        }
        businessInfo.setVisibility(View.GONE);
        if (user instanceof com.rescuefarm.domain.model.Customer && ((com.rescuefarm.domain.model.Customer)user).getCustomerType()
                == com.rescuefarm.domain.enums.CustomerType.BUSINESS) {
            com.rescuefarm.domain.model.Customer customer = (com.rescuefarm.domain.model.Customer)user;
            businessInfo.setVisibility(View.VISIBLE); businessInfo.setText("Khách hàng doanh nghiệp\n" + customer.getCompanyName() + "\nMã số thuế: " + customer.getTaxCode());
        }
        roleView.setText(EnumLabels.label(user.getRole()));
        addressButton.setVisibility(user.getRole() == UserRole.CUSTOMER ? View.VISIBLE : View.GONE);
        sellerButton.setVisibility(user.getRole() == UserRole.SELLER ? View.VISIBLE : View.GONE);
        favoritesButton.setVisibility(user.getRole() == UserRole.CUSTOMER ? View.VISIBLE : View.GONE);
        if (user instanceof Seller) {
            Seller seller = (Seller) user;
            statusView.setText(seller.canSell()
                    ? R.string.seller_approved_message : R.string.seller_pending_message);
        } else statusView.setText(user.getRole() == UserRole.ADMIN
                ? R.string.admin_access_message : R.string.customer_profile_message);
    }

    private void renderVerification() {
        if (!(profileUser instanceof Seller)) {
            nameView.setCompoundDrawablesRelative(null, null, null, null);
            nameView.setContentDescription(null);
            return;
        }
        boolean verified = profileUser.isActive() && ((Seller) profileUser).canSell()
                && sellerApplication != null
                && profileUser.getId().equals(sellerApplication.getSellerId())
                && sellerApplication.getStatus() == com.rescuefarm.domain.enums.ApplicationStatus.APPROVED
                && sellerApplication.getProofImageUrl() != null
                && sellerApplication.getProofImageUrl().trim().toLowerCase(java.util.Locale.ROOT).startsWith("https://");
        android.graphics.drawable.Drawable tick = androidx.core.content.ContextCompat.getDrawable(
                requireContext(), R.drawable.ic_shop_verified);
        if (tick != null) {
            tick = tick.mutate();
            tick.setTint(requireContext().getColor(verified ? R.color.rescue_primary : R.color.rescue_on_surface_variant));
            int size = Math.round(16 * getResources().getDisplayMetrics().density);
            tick.setBounds(0, 0, size, size);
        }
        nameView.setCompoundDrawablesRelative(null, null, tick, null);
        nameView.setContentDescription(getString(verified ? R.string.article_verified_description
                : R.string.article_unverified_description, nameView.getText()));
    }
}
