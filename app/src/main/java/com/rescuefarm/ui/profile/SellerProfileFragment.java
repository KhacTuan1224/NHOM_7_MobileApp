package com.rescuefarm.ui.profile;

import android.os.Bundle;
import android.Manifest;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.text.Editable;
import android.text.TextWatcher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.navigation.NavController;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;
import com.rescuefarm.R;
import com.rescuefarm.domain.model.Seller;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.bumptech.glide.Glide;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
import com.rescuefarm.ui.common.VietnamAddressView;
import java.util.Map;
import com.rescuefarm.service.media.CloudinaryUploadUi;

public class SellerProfileFragment extends Fragment {
    private TextInputEditText representativeInput, shopNameInput, descriptionInput, avatarInput,
            bankCodeInput, bankAccountNameInput, bankAccountNumberInput, latitudeInput, longitudeInput;
    private VietnamAddressView addressInput;
    private ImageView sellerAvatarView;
    private ProfileViewModel viewModel;
    private TextView locationStatus;
    private final ActivityResultLauncher<String[]> locationPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                if (Boolean.TRUE.equals(result.get(Manifest.permission.ACCESS_FINE_LOCATION))
                        || Boolean.TRUE.equals(result.get(Manifest.permission.ACCESS_COARSE_LOCATION))) {
                    viewModel.requestCurrentLocation(true);
                } else {
                    Toast.makeText(requireContext(), "Bạn đã từ chối quyền vị trí. Có thể nhập địa chỉ thủ công.",
                            Toast.LENGTH_LONG).show();
                }
            });
    private boolean dirty, binding;
    private TextView avatarUploadStatus;
    private View uploadAvatarButton;
    private final ActivityResultLauncher<String> avatarPicker = registerForActivityResult(
            new ActivityResultContracts.GetContent(), this::uploadShopAvatar);
    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater,
            @Nullable ViewGroup container, @Nullable Bundle state) {
        return inflater.inflate(R.layout.fragment_seller_profile, container, false);
    }
    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        viewModel = new ViewModelProvider(this,
                new ProfileViewModelFactory(requireContext())).get(ProfileViewModel.class);
        representativeInput = view.findViewById(R.id.sellerRepresentativeInput);
        shopNameInput = view.findViewById(R.id.sellerShopNameInput);
        descriptionInput = view.findViewById(R.id.sellerDescriptionInput);
        avatarInput = view.findViewById(R.id.sellerAvatarInput);
        addressInput = view.findViewById(R.id.sellerAddressInput);
        bankCodeInput = view.findViewById(R.id.sellerBankCodeInput);
        bankAccountNameInput = view.findViewById(R.id.sellerBankAccountNameInput);
        bankAccountNumberInput = view.findViewById(R.id.sellerBankAccountNumberInput);
        latitudeInput = view.findViewById(R.id.sellerLatitudeInput);
        longitudeInput = view.findViewById(R.id.sellerLongitudeInput);
        sellerAvatarView = view.findViewById(R.id.sellerProfileAvatar);
        avatarUploadStatus = view.findViewById(R.id.sellerAvatarUploadStatus);
        uploadAvatarButton = view.findViewById(R.id.uploadSellerAvatarButton);
        uploadAvatarButton.setOnClickListener(v -> avatarPicker.launch("image/*"));
        locationStatus = view.findViewById(R.id.sellerLocationStatus);
        view.findViewById(R.id.useSellerLocationButton).setOnClickListener(v -> requestSellerLocation());
        view.findViewById(R.id.openSellerApplicationButton).setOnClickListener(
                Navigation.createNavigateOnClickListener(
                        R.id.action_sellerProfileFragment_to_sellerApplicationFragment));
        view.findViewById(R.id.openSellerDashboardButton).setOnClickListener(
                Navigation.createNavigateOnClickListener(
                        R.id.action_sellerProfileFragment_to_sellerDashboardFragment));
        view.findViewById(R.id.openSellerProductsButton).setOnClickListener(
                Navigation.createNavigateOnClickListener(
                        R.id.action_sellerProfileFragment_to_sellerProductListFragment));
        view.findViewById(R.id.openSellerCampaignsButton).setOnClickListener(
                Navigation.createNavigateOnClickListener(
                        R.id.action_sellerProfileFragment_to_sellerCampaignListFragment));
        view.findViewById(R.id.openSellerOrdersButton).setOnClickListener(
                Navigation.createNavigateOnClickListener(
                        R.id.action_sellerProfileFragment_to_sellerOrderListFragment));
        view.findViewById(R.id.saveSellerProfileButton).setOnClickListener(v -> {
            if (!addressInput.validate()) return;
            try {
                viewModel.updateSellerProfile(text(representativeInput), text(shopNameInput),
                        text(descriptionInput), text(avatarInput), addressInput.formattedAddress(),
                        text(bankCodeInput), text(bankAccountNameInput), text(bankAccountNumberInput),
                        coordinate(latitudeInput, true), coordinate(longitudeInput, false));
            } catch (IllegalArgumentException error) {
                Toast.makeText(requireContext(), error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
        android.view.ViewGroup parent = (android.view.ViewGroup)view.findViewById(R.id.saveSellerProfileButton).getParent();
        com.google.android.material.button.MaterialButton settings = new com.google.android.material.button.MaterialButton(requireContext());
        settings.setText("Phí giao hàng cố định và ảnh QR");
        settings.setOnClickListener(v -> { Bundle args = new Bundle(); args.putBoolean("edit",true);
            Navigation.findNavController(view).navigate(R.id.storefrontFragment,args); });
        parent.addView(settings, parent.indexOfChild(view.findViewById(R.id.saveSellerProfileButton))+1);
        view.findViewById(R.id.logoutButton).setOnClickListener(unused ->
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
                        .show());
        viewModel.getState().observe(getViewLifecycleOwner(), value -> {
            if (value.getStatus() == ProfileScreenState.Status.ERROR) {
                Toast.makeText(requireContext(), value.getMessage(), Toast.LENGTH_LONG).show();
            } else if (value.getStatus() == ProfileScreenState.Status.LOCATION) {
                renderSellerLocation(value);
            } else if (value.getStatus() == ProfileScreenState.Status.PROFILE
                    && value.getUser() instanceof Seller) {
                Seller seller = (Seller) value.getUser();
                ((TextView) view.findViewById(R.id.sellerShopName)).setText(
                        seller.getShopName() == null || seller.getShopName().isEmpty()
                                ? getString(R.string.shop_not_updated) : seller.getShopName());
                ((TextView) view.findViewById(R.id.sellerStatus)).setText(seller.getSellerStatus().name());
                ((TextView) view.findViewById(R.id.sellerBusinessRule)).setText(seller.canSell()
                        ? R.string.seller_approved_message : R.string.seller_pending_message);
                if (!dirty) populate(seller);
                renderAvatar(seller.getShopAvatarUrl());
            } else if (value.getStatus() == ProfileScreenState.Status.SAVED) {
                Toast.makeText(requireContext(), value.getMessage(), Toast.LENGTH_SHORT).show();
                dirty = false;
                viewModel.loadProfile();
            }
        });
        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (!binding) dirty = true;
            }
            @Override public void afterTextChanged(Editable s) { }
        };
        representativeInput.addTextChangedListener(watcher);
        shopNameInput.addTextChangedListener(watcher);
        descriptionInput.addTextChangedListener(watcher);
        avatarInput.addTextChangedListener(watcher);
        addressInput.setOnAddressChangedListener(() -> {
            if (!binding) { dirty = true; latitudeInput.setText(""); longitudeInput.setText(""); }
        });
        bankCodeInput.addTextChangedListener(watcher); bankAccountNameInput.addTextChangedListener(watcher);
        bankAccountNumberInput.addTextChangedListener(watcher); latitudeInput.addTextChangedListener(watcher);
        longitudeInput.addTextChangedListener(watcher);
        viewModel.observeProfile().observe(getViewLifecycleOwner(), user -> {
            if (user instanceof Seller) {
                if (!dirty) populate((Seller) user);
                renderAvatar(((Seller) user).getShopAvatarUrl());
            }
        });
        viewModel.loadProfile();
    }

    private void requestSellerLocation() {
        boolean hasFine = ContextCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        boolean hasCoarse = ContextCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        if (hasFine || hasCoarse) {
            viewModel.requestCurrentLocation(true);
        } else {
            locationPermissionLauncher.launch(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION});
        }
    }

    private void renderSellerLocation(ProfileScreenState value) {
        binding = true;
        latitudeInput.setText(String.valueOf(value.getLatitude()));
        longitudeInput.setText(String.valueOf(value.getLongitude()));
        if (value.getGeocodedAddress() != null) {
            String street = value.getGeocodedAddress().getStreet();
            String ward = value.getGeocodedAddress().getWard();
            String district = value.getGeocodedAddress().getDistrict();
            String province = value.getGeocodedAddress().getProvince();
            StringBuilder address = new StringBuilder();
            for (String part : new String[]{street, ward, district, province}) {
                if (part != null && !part.trim().isEmpty()) {
                    if (address.length() > 0) address.append(", ");
                    address.append(part.trim());
                }
            }
            if (address.length() > 0) addressInput.setAddress(province, district, ward, street);
        }
        binding = false;
        dirty = true;
        String message = value.getMessage();
        locationStatus.setText(message == null || message.trim().isEmpty()
                ? "Đã lấy vị trí hiện tại và điền địa chỉ nếu có."
                : message);
    }
    private void navigateToLogin() {
        NavController navController = NavHostFragment.findNavController(this);
        NavOptions options = new NavOptions.Builder().setPopUpTo(R.id.main_navigation, true).build();
        navController.navigate(R.id.loginFragment, null, options);
    }

    private void populate(Seller seller) {
        binding = true;
        representativeInput.setText(seller.getRepresentativeName());
        shopNameInput.setText(seller.getShopName());
        descriptionInput.setText(seller.getShopDescription());
        avatarInput.setText(seller.getShopAvatarUrl());
        avatarUploadStatus.setText(seller.getShopAvatarUrl() == null || seller.getShopAvatarUrl().trim().isEmpty()
                ? R.string.no_shop_avatar_selected : R.string.shop_avatar_saved_status);
        addressInput.setFormattedAddress(seller.getAddress());
        bankCodeInput.setText(seller.getBankCode()); bankAccountNameInput.setText(seller.getBankAccountName());
        bankAccountNumberInput.setText(seller.getBankAccountNumber());
        latitudeInput.setText(seller.getLatitude() == 0D ? "" : String.valueOf(seller.getLatitude()));
        longitudeInput.setText(seller.getLongitude() == 0D ? "" : String.valueOf(seller.getLongitude()));
        binding = false;
    }

    private void renderAvatar(String shopAvatarUrl) {
        if (shopAvatarUrl != null && !shopAvatarUrl.trim().isEmpty()) {
            sellerAvatarView.setPadding(0, 0, 0, 0);
            sellerAvatarView.setImageTintList(null);
            Glide.with(this).load(shopAvatarUrl.trim()).circleCrop()
                    .placeholder(R.drawable.ic_nav_profile).error(R.drawable.ic_nav_profile)
                    .into(sellerAvatarView);
        } else {
            Glide.with(this).clear(sellerAvatarView);
            sellerAvatarView.setPadding(16, 16, 16, 16);
            sellerAvatarView.setImageResource(R.drawable.ic_nav_profile);
            sellerAvatarView.setImageTintList(android.content.res.ColorStateList.valueOf(
                    requireContext().getColor(R.color.rescue_primary)));
        }
    }

    private void uploadShopAvatar(Uri uri) {
        com.google.firebase.auth.FirebaseUser user = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Toast.makeText(requireContext(), "Phiên đăng nhập đã hết hạn. Hãy đăng nhập lại.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        CloudinaryUploadUi.upload(this, uri, "shopAvatar", user.getUid(), 0, uploadAvatarButton,
                avatarUploadStatus, sellerAvatarView, url -> avatarInput.setText(url));
    }

    private static String text(TextInputEditText input) {
        return input.getText() == null ? "" : input.getText().toString();
    }
    private double coordinate(TextInputEditText input, boolean latitude) {
        String value = text(input).trim(); if (value.isEmpty()) return 0D;
        try {
            double parsed = Double.parseDouble(value.replace(',', '.'));
            if (!Double.isFinite(parsed) || (latitude ? parsed < -90D || parsed > 90D
                    : parsed < -180D || parsed > 180D)) throw new NumberFormatException();
            return parsed;
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException(latitude ? "Vĩ độ không hợp lệ." : "Kinh độ không hợp lệ.");
        }
    }
}
