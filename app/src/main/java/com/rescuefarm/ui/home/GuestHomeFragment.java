package com.rescuefarm.ui.home;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Space;
import android.widget.TextView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.viewpager2.widget.ViewPager2;
import com.rescuefarm.R;
import com.rescuefarm.domain.model.Banner;
import com.rescuefarm.domain.model.Category;
import com.rescuefarm.domain.model.Product;
import com.rescuefarm.domain.model.RescueCampaign;
import com.rescuefarm.domain.model.Post;
import com.rescuefarm.ui.feed.PostCardRenderer;
import com.rescuefarm.ui.feed.PostViewModel;
import com.rescuefarm.ui.feed.PostViewModelFactory;
import com.google.android.material.button.MaterialButton;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class GuestHomeFragment extends Fragment {
    private HomeViewModel viewModel;
    private PostViewModel postViewModel;
    private HomeCardRenderer renderer;
    private PostCardRenderer postRenderer;
    private TextView message;
    private TextView greeting, locationLabel;
    private ProgressBar progress;
    private TextView feedTitle;
    private ViewPager2 bannerPager;
    private LinearLayout bannerDots, criticalSection, nearbyMobileSection, nearbyFixedSection,
            endingSoonSection, nearExpirySection, valueSection, campaignSection, categorySection, feedSection;
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
        postRenderer = new PostCardRenderer(this);
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
        feedTitle = view.findViewById(R.id.feedTitle);
        feedSection = view.findViewById(R.id.feedSection);
        viewModel = new ViewModelProvider(this, new HomeViewModelFactory(requireContext()))
                .get(HomeViewModel.class);
        postViewModel = new ViewModelProvider(this, new PostViewModelFactory(requireContext()))
                .get(PostViewModel.class);
        viewModel.getHomeState().observe(getViewLifecycleOwner(), this::render);
        postViewModel.getFeed().observe(getViewLifecycleOwner(), this::renderFeed);
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
        locationLabel.setOnClickListener(v -> requestLocation());
        
        // See more buttons
        view.findViewById(R.id.seeMoreCategoriesButton).setOnClickListener(v -> 
                Navigation.findNavController(view).navigate(R.id.action_guestHomeFragment_to_allCategoriesFragment));
        view.findViewById(R.id.seeMoreCriticalButton).setOnClickListener(v -> 
                Navigation.findNavController(view).navigate(R.id.action_guestHomeFragment_to_allCriticalCampaignsFragment));
        view.findViewById(R.id.seeMoreValueButton).setOnClickListener(v -> 
                Navigation.findNavController(view).navigate(R.id.action_guestHomeFragment_to_allValueProductsFragment));
        view.findViewById(R.id.seeMoreCampaignsButton).setOnClickListener(v -> 
                Navigation.findNavController(view).navigate(R.id.action_guestHomeFragment_to_allActiveCampaignsFragment));
        
        viewModel.refresh(); postViewModel.refreshFeed();
    }

    private void requestLocation() {
        boolean fine = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        boolean coarse = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        if (fine || coarse) viewModel.requestNearbyLocation();
        else permissionLauncher.launch(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION});
    }
    private void onPermissions(Map<String, Boolean> values) {
        if (Boolean.TRUE.equals(values.get(Manifest.permission.ACCESS_FINE_LOCATION))
                || Boolean.TRUE.equals(values.get(Manifest.permission.ACCESS_COARSE_LOCATION))) {
            viewModel.requestNearbyLocation();
        } else viewModel.onLocationPermissionDenied();
    }

    private void render(HomeViewState state) {
        String prefix = state.getDataFreshness() == HomeViewState.DataFreshness.OFFLINE ? "OFFLINE • "
                : state.getDataFreshness() == HomeViewState.DataFreshness.STALE ? "CACHE CŨ • " : "";
        message.setText(prefix + state.getMessage()); progress.setVisibility(state.isRefreshing() ? View.VISIBLE : View.GONE);
        locationLabel.setText(state.getLocationState() == HomeViewState.LocationState.AVAILABLE
                ? R.string.home_location_active : state.getLocationState() == HomeViewState.LocationState.LOADING
                ? R.string.home_location_loading : R.string.home_location_default);
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
    private void renderFeed(List<Post> values) {
        feedSection.removeAllViews();
        List<Post> safe = values == null ? new ArrayList<>() : values;
        feedTitle.setVisibility(safe.isEmpty() ? View.GONE : View.VISIBLE);
        feedSection.setVisibility(safe.isEmpty() ? View.GONE : View.VISIBLE);
        int count = Math.min(4, safe.size());
        addGrid(feedSection, safe.subList(0, count), post -> postRenderer.homeCard(post,
                v -> openPost(post.getId())));
        if (!safe.isEmpty()) {
            MaterialButton all = new MaterialButton(requireContext());
            all.setText(R.string.open_feed_action);
            all.setTextColor(requireContext().getColor(R.color.rescue_primary));
            all.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                    requireContext().getColor(R.color.rescue_primary_container)));
            all.setElevation(0);
            all.setOnClickListener(v -> Navigation.findNavController(requireView()).navigate(
                    R.id.action_guestHomeFragment_to_feedFragment));
            feedSection.addView(all, new LinearLayout.LayoutParams(-1, dp(40)));
        }
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
        for (int index = 0; index < values.size(); index += 2) {
            LinearLayout column = new LinearLayout(requireContext());
            column.setOrientation(LinearLayout.VERTICAL);
            column.setClipChildren(false);
            column.setClipToPadding(false);
            LinearLayout.LayoutParams columnParams = new LinearLayout.LayoutParams(dp(76), -2);
            columnParams.setMargins(0, 0, dp(4), 0);
            categorySection.addView(column, columnParams);
            for (int row = 0; row < 2 && index + row < values.size(); row++) {
                Category category = values.get(index + row);
                View tile = renderer.category(category, v -> {
                    Bundle args = new Bundle();
                    args.putString("categoryId", category.getId());
                    DiscoveryBottomSheet bottomSheet = new DiscoveryBottomSheet();
                    bottomSheet.setArguments(args);
                    bottomSheet.show(getChildFragmentManager(), "DiscoveryBottomSheet");
                });
                column.addView(tile, new LinearLayout.LayoutParams(-1, dp(86)));
            }
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
    private void openPost(String id) {
        Bundle args = new Bundle(); args.putString("postId", id);
        Navigation.findNavController(requireView()).navigate(
                R.id.action_guestHomeFragment_to_postDetailFragment, args);
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
