package com.rescuefarm.ui.home;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.GradientDrawable;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Space;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.viewpager2.widget.ViewPager2;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.rescuefarm.R;
import com.rescuefarm.domain.model.Banner;
import com.rescuefarm.domain.model.Category;
import com.rescuefarm.domain.model.Product;
import com.rescuefarm.domain.model.RescueCampaign;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class GuestHomeFragment extends Fragment {
    private HomeViewModel viewModel;
    private HomeCardRenderer renderer;
    private TextView message;
    private TextView greeting, locationLabel;
    private ProgressBar progress;
    private ViewPager2 bannerPager;
    private LinearLayout bannerDots, criticalSection, nearbyMobileSection, nearbyFixedSection,
            endingSoonSection, nearExpirySection, valueSection, campaignSection, categorySection;
    private BannerPagerAdapter bannerAdapter;
    private ViewPager2.OnPageChangeCallback bannerPageCallback;
    private final Handler bannerHandler = new Handler(Looper.getMainLooper());
    private final Runnable freshnessTick = new Runnable() {
        @Override public void run() {
            if (viewModel != null) viewModel.refreshTimeSensitiveContent();
            bannerHandler.postDelayed(this, 60000L);
        }
    };
    private final Runnable bannerAutoScroll = new Runnable() {
        @Override public void run() {
            if (bannerAdapter != null && bannerAdapter.getItemCount() > 1 && bannerPager != null) {
                bannerPager.setCurrentItem((bannerPager.getCurrentItem() + 1) % bannerAdapter.getItemCount(), true);
                bannerHandler.postDelayed(this, 4500L);
            }
        }
    };
    private final ActivityResultLauncher<String[]> permissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(), this::onPermissions);

    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater,
            @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_guest_home, container, false);
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        super.onViewCreated(view, state); renderer = new HomeCardRenderer(this);
        message = view.findViewById(R.id.homeMessage); progress = view.findViewById(R.id.homeProgress);
        greeting = view.findViewById(R.id.homeGreeting);
        locationLabel = view.findViewById(R.id.homeLocationLabel);
        bannerPager = view.findViewById(R.id.bannerPager);
        bannerDots = view.findViewById(R.id.bannerDots);
        bannerAdapter = new BannerPagerAdapter(banner -> {
            if (!banner.getCampaignId().isEmpty()) openCampaign(banner.getCampaignId());
        });
        bannerPager.setAdapter(bannerAdapter);
        bannerPageCallback = new ViewPager2.OnPageChangeCallback() {
            @Override public void onPageSelected(int position) { renderBannerDots(bannerAdapter.getItemCount(), position); }
        };
        bannerPager.registerOnPageChangeCallback(bannerPageCallback);
        criticalSection = view.findViewById(R.id.criticalSection);
        nearbyMobileSection = view.findViewById(R.id.nearbyMobileSection);
        nearbyFixedSection = view.findViewById(R.id.nearbyFixedSection);
        endingSoonSection = view.findViewById(R.id.endingSoonSection);
        nearExpirySection = view.findViewById(R.id.nearExpirySection);
        valueSection = view.findViewById(R.id.valueSection);
        campaignSection = view.findViewById(R.id.campaignSection);
        categorySection = view.findViewById(R.id.categorySection);
        viewModel = new ViewModelProvider(this, new HomeViewModelFactory(requireContext()))
                .get(HomeViewModel.class);
        viewModel.getHomeState().observe(getViewLifecycleOwner(), this::render);
        viewModel.getGreetingName().observe(getViewLifecycleOwner(), name ->
                greeting.setText(getString(R.string.home_greeting_format,
                        name == null || name.trim().isEmpty() ? "bạn" : name.trim())));

        view.findViewById(R.id.searchButton).setOnClickListener(v -> {
            DiscoveryBottomSheet bottomSheet = new DiscoveryBottomSheet();
            bottomSheet.show(getChildFragmentManager(), "DiscoveryBottomSheet");
        });
        view.findViewById(R.id.notificationButton).setOnClickListener(unused ->
                Navigation.findNavController(view).navigate(viewModel.isAuthenticated()
                        ? R.id.action_guestHomeFragment_to_notificationFragment
                        : R.id.action_guestHomeFragment_to_loginFragment));
        view.findViewById(R.id.refreshHomeButton).setOnClickListener(v -> viewModel.refresh());
        View locationContainer = view.findViewById(R.id.homeLocationContainer);
        if (locationContainer != null) {
            locationContainer.setOnClickListener(v -> showLocationPicker());
        }
        locationLabel.setOnClickListener(v -> showLocationPicker());
        
        // See more buttons
        view.findViewById(R.id.seeMoreCategoriesButton).setOnClickListener(v -> 
                Navigation.findNavController(view).navigate(R.id.action_guestHomeFragment_to_allCategoriesFragment));
        view.findViewById(R.id.seeMoreCriticalButton).setOnClickListener(v -> 
                Navigation.findNavController(view).navigate(R.id.action_guestHomeFragment_to_allCriticalCampaignsFragment));
        view.findViewById(R.id.seeMoreValueButton).setOnClickListener(v -> 
                Navigation.findNavController(view).navigate(R.id.action_guestHomeFragment_to_allValueProductsFragment));
        view.findViewById(R.id.seeMoreCampaignsButton).setOnClickListener(v -> 
                Navigation.findNavController(view).navigate(R.id.action_guestHomeFragment_to_allActiveCampaignsFragment));
        
        viewModel.refresh();
    }

    private void showLocationPicker() {
        String[] options = new String[] {
            "📍 Lấy vị trí GPS hiện tại của bạn",
            "🏙️ TP. Hồ Chí Minh (Trung tâm)",
            "🏛️ Hà Nội (Trung tâm)",
            "🌸 Đà Lạt (Lâm Đồng)",
            "🌾 Cần Thơ (Miền Tây)",
            "🥭 Tiền Giang (Vựa trái cây)",
            "☕ Đắk Lắk (Tây Nguyên)"
        };
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Chọn vị trí xem nông sản")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        requestCurrentGpsLocation();
                    } else if (which == 1) {
                        viewModel.setCustomLocation(10.7769, 106.7009, "TP. Hồ Chí Minh");
                        Toast.makeText(requireContext(), "Đang xem nông sản quanh: TP. Hồ Chí Minh", Toast.LENGTH_SHORT).show();
                    } else if (which == 2) {
                        viewModel.setCustomLocation(21.0285, 105.8542, "Hà Nội");
                        Toast.makeText(requireContext(), "Đang xem nông sản quanh: Hà Nội", Toast.LENGTH_SHORT).show();
                    } else if (which == 3) {
                        viewModel.setCustomLocation(11.9404, 108.4583, "Đà Lạt, Lâm Đồng");
                        Toast.makeText(requireContext(), "Đang xem nông sản quanh: Đà Lạt", Toast.LENGTH_SHORT).show();
                    } else if (which == 4) {
                        viewModel.setCustomLocation(10.0452, 105.7469, "Cần Thơ");
                        Toast.makeText(requireContext(), "Đang xem nông sản quanh: Cần Thơ", Toast.LENGTH_SHORT).show();
                    } else if (which == 5) {
                        viewModel.setCustomLocation(10.3600, 106.3600, "Tiền Giang");
                        Toast.makeText(requireContext(), "Đang xem nông sản quanh: Tiền Giang", Toast.LENGTH_SHORT).show();
                    } else if (which == 6) {
                        viewModel.setCustomLocation(12.6667, 108.0383, "Đắk Lắk");
                        Toast.makeText(requireContext(), "Đang xem nông sản quanh: Đắk Lắk", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Đóng", null)
                .show();
    }

    private void requestCurrentGpsLocation() {
        LocationManager lm = (LocationManager) requireContext().getSystemService(Context.LOCATION_SERVICE);
        boolean isGpsEnabled = lm != null && (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
                || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER));
        if (!isGpsEnabled) {
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Dịch vụ định vị đang tắt")
                    .setMessage("Vui lòng bật dịch vụ vị trí (GPS) trên thiết bị để RescueFarm xác định nông sản gần bạn nhất.")
                    .setPositiveButton("Mở Cài đặt", (d, w) -> {
                        startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
                    })
                    .setNegativeButton("Hủy", null)
                    .show();
            return;
        }

        boolean fine = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        boolean coarse = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        if (fine || coarse) {
            Toast.makeText(requireContext(), "Đang xác định vị trí hiện tại…", Toast.LENGTH_SHORT).show();
            viewModel.requestNearbyLocation();
        } else {
            permissionLauncher.launch(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION});
        }
    }

    private void onPermissions(Map<String, Boolean> values) {
        if (Boolean.TRUE.equals(values.get(Manifest.permission.ACCESS_FINE_LOCATION))
                || Boolean.TRUE.equals(values.get(Manifest.permission.ACCESS_COARSE_LOCATION))) {
            Toast.makeText(requireContext(), "Đang xác định vị trí hiện tại…", Toast.LENGTH_SHORT).show();
            viewModel.requestNearbyLocation();
        } else {
            Toast.makeText(requireContext(), "Chưa cấp quyền vị trí. Bạn có thể chọn khu vực có sẵn.", Toast.LENGTH_LONG).show();
            viewModel.onLocationPermissionDenied();
        }
    }

    private void render(HomeViewState state) {
        String prefix = state.getDataFreshness() == HomeViewState.DataFreshness.OFFLINE ? "OFFLINE • "
                : state.getDataFreshness() == HomeViewState.DataFreshness.STALE ? "CACHE CŨ • " : "";
        message.setText(prefix + state.getMessage()); progress.setVisibility(state.isRefreshing() ? View.VISIBLE : View.GONE);
        if (state.getLocationState() == HomeViewState.LocationState.AVAILABLE) {
            String label = state.getLocationLabel();
            if (label != null && !label.trim().isEmpty()) {
                locationLabel.setText(label.trim());
            } else {
                locationLabel.setText(R.string.home_location_active);
            }
        } else if (state.getLocationState() == HomeViewState.LocationState.LOADING) {
            locationLabel.setText(R.string.home_location_loading);
        } else {
            locationLabel.setText(R.string.home_location_default);
        }
        renderBanners(state.getBanners());
        renderCampaigns(criticalSection, state.getCritical(), state, "Chưa có chiến dịch CRITICAL.");
        String locationEmpty = state.getLocationState() == HomeViewState.LocationState.AVAILABLE
                ? "Chưa có điểm giải cứu gần vị trí của bạn."
                : "Bật vị trí để xem các điểm giải cứu gần bạn.";
        renderCampaigns(nearbyMobileSection, state.getNearbyMobile(), state, locationEmpty);
        renderCampaigns(nearbyFixedSection, state.getNearbyFixed(), state, locationEmpty);
        renderCampaigns(endingSoonSection, state.getEndingSoon(), state,
                "Chưa có chiến dịch sắp kết thúc.");
        renderProducts(state.getValueProducts());
        renderNearExpiry(state.getNearExpiry());
        List<RescueCampaign> otherCampaigns = new ArrayList<>();
        for (RescueCampaign campaign : state.getActiveCampaigns()) {
            if (campaign.getUrgencyLevel() != com.rescuefarm.domain.enums.UrgencyLevel.CRITICAL) {
                otherCampaigns.add(campaign);
            }
        }
        renderCampaigns(campaignSection, otherCampaigns, state, "Chưa có chiến dịch khác đang diễn ra.");
        renderCategories(state.getCategories());
    }
    private void renderBanners(List<Banner> values) {
        bannerAdapter.submit(values);
        if (values.isEmpty() || bannerPager.getCurrentItem() >= values.size()) bannerPager.setCurrentItem(0, false);
        bannerPager.setVisibility(values.isEmpty() ? View.GONE : View.VISIBLE);
        bannerDots.setVisibility(values.size() < 2 ? View.GONE : View.VISIBLE);
        renderBannerDots(values.size(), bannerPager.getCurrentItem());
        bannerHandler.removeCallbacks(bannerAutoScroll);
        if (values.size() > 1 && getLifecycle().getCurrentState().isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) {
            bannerHandler.postDelayed(bannerAutoScroll, 4500L);
        }
    }
    private void renderCampaigns(LinearLayout container, List<RescueCampaign> values,
            HomeViewState state, String empty) {
        container.removeAllViews();
        if (values.isEmpty()) { container.addView(renderer.message(empty)); return; }
        addHorizontal(container, values, value -> renderer.campaign(value,
                state.distanceFor(value.getId()), state.getValueProducts(), v -> openCampaign(value.getId())));
    }
    private void renderProducts(List<Product> values) {
        valueSection.removeAllViews();
        if (values.isEmpty()) { valueSection.addView(renderer.message("Chưa có nông sản giảm giá trong cache.")); return; }
        addGrid(valueSection, values, value -> renderer.product(value, v -> openProduct(value.getId())));
    }
    private void renderNearExpiry(List<ExpiringProduct> values) {
        nearExpirySection.removeAllViews();
        if (values.isEmpty()) { nearExpirySection.addView(renderer.message("Chưa có lô còn bán được sẽ hết hạn trong 3 ngày tới.")); return; }
        java.text.SimpleDateFormat date = new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.forLanguageTag("vi-VN"));
        addGrid(nearExpirySection, values, value -> {
            LinearLayout column = new LinearLayout(requireContext()); column.setOrientation(LinearLayout.VERTICAL);
            column.addView(renderer.product(value.getProduct(), v -> {
                Bundle args = new Bundle(); args.putString("productId", value.getProduct().getId());
                args.putString("batchId", value.getBatch().getId());
                Navigation.findNavController(requireView()).navigate(R.id.action_guestHomeFragment_to_productDetailFragment, args);
            }));
            TextView expiry = renderer.message("Hạn dùng: " + date.format(value.getBatch().getExpiryDate())
                    + "\nCòn " + value.getBatch().getAvailableQuantity() + " " + value.getProduct().getUnit());
            column.addView(expiry); return column;
        });
    }
    private void renderCategories(List<Category> values) {
        categorySection.removeAllViews();
        categorySection.setClipChildren(false);
        categorySection.setClipToPadding(false);
        if (values.isEmpty()) { categorySection.addView(renderer.message("Chưa có danh mục trong cache.")); return; }
        for (Category category : values) {
            View tile = renderer.category(category, v -> {
                Bundle args = new Bundle();
                args.putString("categoryId", category.getId());
                DiscoveryBottomSheet bottomSheet = new DiscoveryBottomSheet();
                bottomSheet.setArguments(args);
                bottomSheet.show(getChildFragmentManager(), "DiscoveryBottomSheet");
            });
            LinearLayout.LayoutParams itemParams = new LinearLayout.LayoutParams(dp(76), -2);
            itemParams.setMargins(0, 0, dp(8), 0);
            categorySection.addView(tile, itemParams);
        }
    }

    private void renderBannerDots(int count, int selected) {
        if (bannerDots == null) return;
        bannerDots.removeAllViews();
        for (int index = 0; index < count; index++) {
            View dot = new View(requireContext());
            GradientDrawable background = new GradientDrawable();
            background.setShape(GradientDrawable.RECTANGLE);
            background.setCornerRadius(dp(8));
            background.setColor(requireContext().getColor(index == selected
                    ? R.color.rescue_primary : R.color.rescue_outline));
            dot.setBackground(background);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    dp(index == selected ? 16 : 6), dp(6));
            params.setMargins(dp(3), 0, dp(3), 0);
            bannerDots.addView(dot, params);
        }
    }

    private <T> void addHorizontal(LinearLayout container, List<T> values,
                                   Function<T, View> createView) {
        for (T value : values) {
            View item = createView.apply(value);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(248), -2);
            params.setMargins(0, dp(3), dp(10), dp(5));
            container.addView(item, params);
        }
    }

    private <T> void addGrid(LinearLayout container, List<T> values, Function<T, View> createView) {
        int horizontalGap = dp(6);
        container.setClipChildren(false);
        container.setClipToPadding(false);
        for (int index = 0; index < values.size(); index += 2) {
            LinearLayout row = new LinearLayout(requireContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setClipChildren(false);
            row.setClipToPadding(false);
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, -2);
            rowParams.setMargins(0, dp(2), 0, dp(5));
            container.addView(row, rowParams);

            View first = createView.apply(values.get(index));
            LinearLayout.LayoutParams firstParams = new LinearLayout.LayoutParams(0, -2, 1f);
            firstParams.setMargins(0, 0, horizontalGap, 0);
            row.addView(first, firstParams);
            if (index + 1 < values.size()) {
                View second = createView.apply(values.get(index + 1));
                row.addView(second, new LinearLayout.LayoutParams(0, -2, 1f));
            } else {
                row.addView(new Space(requireContext()), new LinearLayout.LayoutParams(0, 1, 1f));
            }
        }
    }
    private void openCampaign(String id) {
        Bundle args = new Bundle(); args.putString("campaignId", id);
        Navigation.findNavController(requireView()).navigate(
                R.id.action_guestHomeFragment_to_campaignDetailFragment, args);
    }
    private void openProduct(String id) {
        Bundle args = new Bundle(); args.putString("productId", id);
        Navigation.findNavController(requireView()).navigate(
                R.id.action_guestHomeFragment_to_productDetailFragment, args);
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    @Override public void onResume() {
        super.onResume();
        bannerHandler.removeCallbacks(freshnessTick);
        bannerHandler.post(freshnessTick);
        bannerHandler.removeCallbacks(bannerAutoScroll);
        bannerHandler.postDelayed(bannerAutoScroll, 4500L);
    }

    @Override public void onPause() {
        bannerHandler.removeCallbacks(freshnessTick);
        bannerHandler.removeCallbacks(bannerAutoScroll);
        super.onPause();
    }

    @Override public void onDestroyView() {
        bannerHandler.removeCallbacks(freshnessTick);
        bannerHandler.removeCallbacks(bannerAutoScroll);
        if (bannerPager != null && bannerPageCallback != null) {
            bannerPager.unregisterOnPageChangeCallback(bannerPageCallback);
        }
        bannerPager = null;
        super.onDestroyView();
    }
}
