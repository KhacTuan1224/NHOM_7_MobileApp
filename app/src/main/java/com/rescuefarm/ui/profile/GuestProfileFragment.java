package com.rescuefarm.ui.profile;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.rescuefarm.R;
import com.rescuefarm.data.local.preferences.SharedPreferencesGuestSessionRepository;
import com.rescuefarm.domain.model.RecentGuestOrder;
import com.rescuefarm.service.location.AndroidLocationProvider;
import com.rescuefarm.service.location.LocationProvider;

import java.util.List;

public class GuestProfileFragment extends Fragment {
    private SharedPreferencesGuestSessionRepository sessionRepo;
    private TextView recentOrdersCountBadge;
    private AndroidLocationProvider locationProvider;

    private final ActivityResultLauncher<String[]> locationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                boolean fine = Boolean.TRUE.equals(result.get(Manifest.permission.ACCESS_FINE_LOCATION));
                boolean coarse = Boolean.TRUE.equals(result.get(Manifest.permission.ACCESS_COARSE_LOCATION));
                if (fine || coarse) {
                    Toast.makeText(requireContext(), "Đã cấp quyền vị trí thành công.", Toast.LENGTH_SHORT).show();
                    requestCurrentGps();
                } else {
                    Toast.makeText(requireContext(), "Chưa cấp quyền vị trí. Bạn có thể bật trong Cài đặt.", Toast.LENGTH_LONG).show();
                }
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle state) {
        return inflater.inflate(R.layout.fragment_guest_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        sessionRepo = new SharedPreferencesGuestSessionRepository(requireContext());
        locationProvider = new AndroidLocationProvider(requireContext());

        recentOrdersCountBadge = view.findViewById(R.id.guestRecentOrdersCountBadge);

        // Nút nổi bật [Đăng nhập / Đăng ký]
        view.findViewById(R.id.guestLoginRegisterButton).setOnClickListener(v ->
                Navigation.findNavController(v).navigate(R.id.action_guestProfileFragment_to_loginFragment));

        // Menu 1: Tra cứu đơn hàng
        view.findViewById(R.id.guestMenuTrackOrder).setOnClickListener(v ->
                Navigation.findNavController(v).navigate(R.id.action_guestProfileFragment_to_orderConfirmationFragment));

        // Menu 2: Danh sách đơn đã đặt trên máy
        view.findViewById(R.id.guestMenuRecentOrders).setOnClickListener(v ->
                Navigation.findNavController(v).navigate(R.id.action_guestProfileFragment_to_orderConfirmationFragment));

        // Menu 3: Cài đặt vị trí
        view.findViewById(R.id.guestMenuLocationSettings).setOnClickListener(v -> showLocationSettingsDialog());

        // Menu 4: Trung tâm trợ giúp / Liên hệ cứu trợ
        view.findViewById(R.id.guestMenuHelpCenter).setOnClickListener(v -> showHelpCenterDialog());

        refreshRecentOrdersBadge();
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshRecentOrdersBadge();
    }

    @Override
    public void onDestroyView() {
        if (locationProvider != null) {
            locationProvider.close();
        }
        super.onDestroyView();
    }

    private void refreshRecentOrdersBadge() {
        if (recentOrdersCountBadge == null || sessionRepo == null) return;
        List<RecentGuestOrder> recents = sessionRepo.getRecentGuestOrders();
        int count = recents == null ? 0 : recents.size();
        if (count > 0) {
            recentOrdersCountBadge.setText(getString(R.string.guest_recent_orders_count_format, count));
            recentOrdersCountBadge.setVisibility(View.VISIBLE);
        } else {
            recentOrdersCountBadge.setVisibility(View.GONE);
        }
    }

    private void showLocationSettingsDialog() {
        boolean hasPermission = ContextCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;

        String statusText = hasPermission
                ? getString(R.string.guest_location_permission_granted)
                : getString(R.string.guest_location_permission_denied);

        String message = statusText + "\n\n" + getString(R.string.guest_location_desc);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.guest_location_dialog_title)
                .setMessage(message)
                .setPositiveButton(R.string.guest_location_refresh_action, (dialog, which) -> {
                    if (hasPermission) {
                        requestCurrentGps();
                    } else {
                        locationPermissionLauncher.launch(new String[]{
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                        });
                    }
                })
                .setNeutralButton(R.string.guest_location_open_settings_action, (dialog, which) -> {
                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    intent.setData(Uri.fromParts("package", requireContext().getPackageName(), null));
                    startActivity(intent);
                })
                .setNegativeButton(R.string.close_action, null)
                .show();
    }

    private void requestCurrentGps() {
        Toast.makeText(requireContext(), "Đang xác định tọa độ GPS…", Toast.LENGTH_SHORT).show();
        locationProvider.getCurrentLocation(new LocationProvider.LocationCallback() {
            @Override
            public void onLocationAvailable(double latitude, double longitude) {
                locationProvider.reverseGeocode(latitude, longitude, new LocationProvider.GeocodeCallback() {
                    @Override
                    public void onAddressAvailable(LocationProvider.GeocodedAddress address) {
                        if (isAdded()) {
                            String displayAddress = address != null && !address.getFullAddress().isEmpty()
                                    ? address.getFullAddress()
                                    : String.format(java.util.Locale.ROOT, "%.5f, %.5f", latitude, longitude);
                            new MaterialAlertDialogBuilder(requireContext())
                                    .setTitle("Vị trí GPS hiện tại")
                                    .setMessage("Địa chỉ: " + displayAddress + "\n\nTọa độ: (" + latitude + ", " + longitude + ")")
                                    .setPositiveButton("Xác nhận", null)
                                    .show();
                        }
                    }

                    @Override
                    public void onGeocodeUnavailable(String message) {
                        if (isAdded()) {
                            Toast.makeText(requireContext(), "Tọa độ: (" + latitude + ", " + longitude + ")", Toast.LENGTH_LONG).show();
                        }
                    }
                });
            }

            @Override
            public void onLocationUnavailable(String reason) {
                if (isAdded()) {
                    Toast.makeText(requireContext(), reason, Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    private void showHelpCenterDialog() {
        String content = getString(R.string.guest_help_guide_summary) + "\n\n"
                + "📞 " + getString(R.string.guest_help_hotline_label) + " " + getString(R.string.guest_help_hotline_val) + "\n"
                + "✉️ " + getString(R.string.guest_help_email_label) + " " + getString(R.string.guest_help_email_val);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.guest_help_dialog_title)
                .setMessage(content)
                .setPositiveButton(R.string.guest_help_call_action, (dialog, which) -> {
                    Intent intent = new Intent(Intent.ACTION_DIAL);
                    intent.setData(Uri.parse("tel:19006868"));
                    startActivity(intent);
                })
                .setNeutralButton(R.string.guest_help_email_action, (dialog, which) -> {
                    Intent intent = new Intent(Intent.ACTION_SENDTO);
                    intent.setData(Uri.parse("mailto:cuutro@rescuefarm.vn"));
                    intent.putExtra(Intent.EXTRA_SUBJECT, "[Hỗ trợ khách vãng lai] Cần giải đáp về nông sản giải cứu");
                    startActivity(intent);
                })
                .setNegativeButton(R.string.close_action, null)
                .show();
    }
}
