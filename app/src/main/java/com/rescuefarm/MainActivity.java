package com.rescuefarm;

import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowCompat;
import androidx.navigation.NavController;
import androidx.navigation.NavDestination;
import androidx.navigation.fragment.NavHostFragment;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.android.material.button.MaterialButton;
import com.rescuefarm.R;
import com.rescuefarm.data.repository.AuthRepositoryFactory;
import com.rescuefarm.domain.enums.UserRole;
import com.rescuefarm.domain.model.User;
import com.rescuefarm.service.sync.OfflineSyncScheduler;
import com.rescuefarm.ui.common.NavigationScrollListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuth.AuthStateListener;

public class MainActivity extends AppCompatActivity implements NavigationScrollListener {
    private AuthStateListener authStateListener;
    private UserRole currentRole;
    private final MutableLiveData<UserRole> actorRole = new MutableLiveData<>();
    private NavController navController;
    private MaterialButton centerActionButton;
    private String pendingNotification = "";
    private boolean openingNotification;
    private int actorRequest;
    private boolean bottomNavigationHidden;
    private boolean bottomNavigationAvailable;
    private boolean keyboardVisible;
    private int scrollDirectionDistance;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        OfflineSyncScheduler.schedule(getApplicationContext());
        com.rescuefarm.service.notification.FcmDeviceSyncWorker.schedule(getApplicationContext());
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        getWindow().setNavigationBarColor(ContextCompat.getColor(this, R.color.rescue_nav_surface));
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView())
                .setAppearanceLightNavigationBars(true);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q)
            getWindow().setNavigationBarContrastEnforced(false);
        View navigationBackground = findViewById(R.id.systemNavigationBackground);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.mainRoot), (view, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            keyboardVisible = insets.isVisible(WindowInsetsCompat.Type.ime());
            View navigation = findViewById(R.id.customerBottomNavigationContainer);
            navigation.setVisibility(bottomNavigationAvailable && !keyboardVisible
                    ? View.VISIBLE : View.GONE);
            int bottomInset = Math.max(systemBars.bottom, ime.bottom);
            view.setPadding(systemBars.left, systemBars.top, systemBars.right,
                    bottomInset);
            FrameLayout.LayoutParams backgroundParams =
                    (FrameLayout.LayoutParams) navigationBackground.getLayoutParams();
            if (backgroundParams.height != bottomInset) {
                backgroundParams.height = bottomInset;
                navigationBackground.setLayoutParams(backgroundParams);
            }
            navigationBackground.setTranslationY(bottomInset);
            navigationBackground.setVisibility(bottomInset > 0 ? View.VISIBLE : View.GONE);
            return insets;
        });

        navController = ((NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.main)).getNavController();
        ImageView homeButton = findViewById(R.id.navHomeButton);
        ImageView communityButton = findViewById(R.id.navCommunityButton);
        ImageView ordersButton = findViewById(R.id.navOrdersButton);
        ImageView profileButton = findViewById(R.id.navProfileButton);
        centerActionButton = findViewById(R.id.centerActionButton);
        centerActionButton.setEnabled(false);
        centerActionButton.setOnClickListener(unused -> {
            if (currentRole == UserRole.SELLER) navigateTo(navController, R.id.sellerDashboardFragment);
            else if (currentRole == UserRole.ADMIN) navigateTo(navController, R.id.adminDashboardFragment);
            else navigateTo(navController, R.id.cartFragment);
        });
        homeButton.setOnClickListener(unused -> navigateTo(navController, R.id.guestHomeFragment));
        communityButton.setOnClickListener(unused -> navigateTo(navController, R.id.feedFragment));
        ordersButton.setOnClickListener(unused -> {
            if (currentRole == UserRole.ADMIN) {
                Bundle args = new Bundle(); args.putString("section", "ORDERS");
                if (navController.getCurrentDestination() != null
                        && navController.getCurrentDestination().getId() == R.id.adminResourceFragment) {
                    navController.popBackStack(R.id.adminDashboardFragment, false);
                }
                navigateTo(navController, R.id.adminResourceFragment, args);
            } else if (currentRole == UserRole.SELLER) navigateTo(navController, R.id.sellerOrderListFragment);
            else navigateTo(navController, AuthRepositoryFactory.create(this).isAuthenticated()
                    ? R.id.customerOrderListFragment : R.id.orderConfirmationFragment);
        });
        profileButton.setOnClickListener(unused -> {
            if (!AuthRepositoryFactory.create(this).isAuthenticated()) {
                navigateTo(navController, R.id.guestProfileFragment);
            } else {
                navigateTo(navController, R.id.profileFragment);
            }
        });
        navController.addOnDestinationChangedListener((controller, destination, arguments) ->
                updateBottomNavigation(centerActionButton, homeButton, communityButton,
                        ordersButton, profileButton, destination)
        );
        authStateListener = firebaseAuth -> restoreActorRole();
        pendingNotification = getPreferences(MODE_PRIVATE).getString("pendingNotification", "");
        captureNotificationIntent(getIntent());
    }

    @Override protected void onStart() {
        super.onStart();
        new androidx.lifecycle.ViewModelProvider(this)
                .get(com.rescuefarm.ui.engagement.UnreadNotificationsViewModel.class).start();
        if (authStateListener != null) FirebaseAuth.getInstance().addAuthStateListener(authStateListener);
    }

    @Override protected void onStop() {
        new androidx.lifecycle.ViewModelProvider(this)
                .get(com.rescuefarm.ui.engagement.UnreadNotificationsViewModel.class).stop();
        if (authStateListener != null) FirebaseAuth.getInstance().removeAuthStateListener(authStateListener);
        super.onStop();
    }

    private void restoreActorRole() {
        final int request = ++actorRequest;
        final String sessionUid = FirebaseAuth.getInstance().getCurrentUser() == null ? "" : FirebaseAuth.getInstance().getCurrentUser().getUid();
        boolean hasSession = FirebaseAuth.getInstance().getCurrentUser() != null;
        currentRole = null;
        actorRole.setValue(null);
        updateCenterAction(hasSession);
        if (!hasSession) { openPendingNotification(); return; }
        AuthRepositoryFactory.create(this).restoreSession(
                new com.rescuefarm.data.repository.AuthRepository.AuthCallback() {
                    @Override public void onSuccess(User user) {
                        if (request != actorRequest || FirebaseAuth.getInstance().getCurrentUser() == null
                                || !sessionUid.equals(FirebaseAuth.getInstance().getCurrentUser().getUid())) return;
                        currentRole = user == null ? null : user.getRole();
                        actorRole.setValue(currentRole);
                        updateCenterAction();
                        getWindow().getDecorView().post(MainActivity.this::openPendingNotification);
                    }
                    @Override public void onError(com.rescuefarm.data.repository.AuthRepository.ErrorCode error,
                            String message) {
                        if (request != actorRequest) return;
                        updateCenterAction(true);
                        actorRole.setValue(null);
                        if (!pendingNotification.isEmpty()) android.widget.Toast.makeText(MainActivity.this,
                                "Chưa xác định được quyền tài khoản. Kiểm tra kết nối và thử mở thông báo lại.", android.widget.Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void updateCenterAction() {
        updateCenterAction(false);
    }

    private void updateCenterAction(boolean roleUnresolved) {
        if (centerActionButton == null) return;
        boolean seller = currentRole == UserRole.SELLER;
        boolean admin = currentRole == UserRole.ADMIN;
        centerActionButton.setEnabled(!roleUnresolved);
        centerActionButton.setIconResource(seller || admin ? R.drawable.ic_nav_dashboard : R.drawable.ic_nav_cart);
        centerActionButton.setContentDescription(getString(roleUnresolved
                ? R.string.nav_action_loading : admin ? R.string.nav_admin_dashboard
                : seller ? R.string.nav_seller_dashboard : R.string.nav_cart));
        if (navController != null && navController.getCurrentDestination() != null) {
            updateBottomNavigation(centerActionButton, findViewById(R.id.navHomeButton),
                    findViewById(R.id.navCommunityButton), findViewById(R.id.navOrdersButton),
                    findViewById(R.id.navProfileButton), navController.getCurrentDestination());
        }
    }

    public UserRole getResolvedActorRole() { return currentRole; }
    @Override protected void onNewIntent(android.content.Intent intent) {
        super.onNewIntent(intent); setIntent(intent); captureNotificationIntent(intent);
        if (intent.getData() != null && "rescuefarm".equals(intent.getData().getScheme())
                && "rescue".equals(intent.getData().getHost())) navController.handleDeepLink(intent);
        if (currentRole == null && FirebaseAuth.getInstance().getCurrentUser() != null) restoreActorRole(); else openPendingNotification();
    }
    private void captureNotificationIntent(android.content.Intent intent) {
        android.net.Uri uri = intent == null ? null : intent.getData();
        if (uri != null && "rescuefarm".equals(uri.getScheme()) && "notification".equals(uri.getHost()))
            queueNotification(uri.getLastPathSegment());
    }
    private void queueNotification(String id) {
        if (id == null || !id.matches("[A-Za-z0-9_-]{1,128}")) return;
        pendingNotification = id; getPreferences(MODE_PRIVATE).edit().putString("pendingNotification", id).apply();
    }
    public void openNotification(String id) {
        queueNotification(id);
        if (currentRole == null && FirebaseAuth.getInstance().getCurrentUser() != null) restoreActorRole(); else openPendingNotification();
    }
    private void openPendingNotification() {
        if (pendingNotification.isEmpty() || openingNotification || navController == null) return;
        if (FirebaseAuth.getInstance().getCurrentUser() == null) { navigateTo(navController, R.id.loginFragment); return; }
        if (currentRole == null) return;
        final String id = pendingNotification, uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        openingNotification = true;
        org.json.JSONObject request = new org.json.JSONObject();
        try { request.put("notificationId", id); } catch (org.json.JSONException ignored) { return; }
        com.rescuefarm.service.backend.RenderBackendClient.post("/notifications/resolve", request,
            new com.rescuefarm.service.backend.RenderBackendClient.Callback() {
                public void onSuccess(org.json.JSONObject response) {
                    openingNotification = false;
                    if (isFinishing() || FirebaseAuth.getInstance().getCurrentUser() == null
                            || !uid.equals(FirebaseAuth.getInstance().getCurrentUser().getUid())) return;
                    if (currentRole == null) return; // Keep the pending ID while onStart restores the actor.
                    if (!pendingNotification.equals(id)) { openPendingNotification(); return; }
                    int destination = notificationDestination(response.optString("destination"));
                    if (destination == 0 || destination == R.id.adminDetailFragment && currentRole != UserRole.ADMIN) {
                        notificationFailure("Nội dung không khả dụng với tài khoản hiện tại."); return;
                    }
                    try {
                        Bundle args = new Bundle(); org.json.JSONObject values = response.optJSONObject("args");
                        if (values != null) {
                            java.util.Iterator<String> keys = values.keys();
                            while (keys.hasNext()) { String key = keys.next(); Object value = values.opt(key);
                                if (value instanceof Boolean) args.putBoolean(key, (Boolean)value); else args.putString(key, String.valueOf(value)); }
                        }
                        navController.navigate(destination, args);
                        if (!response.optString("message").isEmpty()) android.widget.Toast.makeText(MainActivity.this, response.optString("message"), android.widget.Toast.LENGTH_LONG).show();
                        if (pendingNotification.equals(id)) clearPendingNotification();
                        com.rescuefarm.service.backend.RenderBackendClient.post("/notifications/read", request,
                            new com.rescuefarm.service.backend.RenderBackendClient.Callback() {
                                public void onSuccess(org.json.JSONObject value) { }
                                public void onError(String message) { android.widget.Toast.makeText(MainActivity.this, "Đã mở nội dung; chưa cập nhật được trạng thái đã đọc.", android.widget.Toast.LENGTH_SHORT).show(); }
                            });
                    } catch (RuntimeException error) { notificationFailure("Không mở được nội dung. Bạn có thể thử lại từ Thông báo."); }
                }
                public void onError(String message) { openingNotification = false; if (pendingNotification.equals(id)) notificationFailure(message); else openPendingNotification(); }
            });
    }
    private int notificationDestination(String name) {
        switch (name) {
            case "adminDetailFragment": return R.id.adminDetailFragment;
            case "reportResultFragment": return R.id.reportResultFragment;
            case "sellerApplicationFragment": return R.id.sellerApplicationFragment;
            case "sellerProfileFragment": return R.id.sellerProfileFragment;
            case "orderDetailFragment": return R.id.orderDetailFragment;
            case "campaignDetailFragment": return R.id.campaignDetailFragment;
            case "productDetailFragment": return R.id.productDetailFragment;
            case "reviewFragment": return R.id.reviewFragment;
            case "profileFragment": return R.id.profileFragment;
            case "notificationFragment": return R.id.notificationFragment;
            case "rescueHubFragment": return R.id.rescueHubFragment;
            default: return 0;
        }
    }
    private void clearPendingNotification() { pendingNotification = ""; getPreferences(MODE_PRIVATE).edit().remove("pendingNotification").apply(); }
    private void notificationFailure(String message) {
        clearPendingNotification(); android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_LONG).show();
    }
    public LiveData<UserRole> observeActorRole() { return actorRole; }

    @Override public void onContentScrolled(int deltaY, boolean atTop) {
        if (deltaY == 0) return;
        if (atTop && deltaY < 0) {
            scrollDirectionDistance = 0;
            setBottomNavigationHidden(false);
            return;
        }
        int direction = deltaY > 0 ? 1 : -1;
        if (scrollDirectionDistance != 0 && Integer.signum(scrollDirectionDistance) != direction)
            scrollDirectionDistance = 0;
        scrollDirectionDistance += deltaY;
        int threshold = Math.round(12 * getResources().getDisplayMetrics().density);
        if (Math.abs(scrollDirectionDistance) >= threshold) {
            setBottomNavigationHidden(direction > 0);
            scrollDirectionDistance = 0;
        }
    }

    private void setBottomNavigationHidden(boolean hidden) {
        View navigation = findViewById(R.id.customerBottomNavigationContainer);
        if (navigation == null || navigation.getVisibility() != View.VISIBLE
                || bottomNavigationHidden == hidden) return;
        bottomNavigationHidden = hidden;
        float density = getResources().getDisplayMetrics().density;
        float distance = (navigation.getHeight() > 0 ? navigation.getHeight()
                : getResources().getDimensionPixelSize(R.dimen.customer_bottom_navigation_height))
                + 12 * density;
        navigation.animate().cancel();
        navigation.animate()
                .translationY(hidden ? distance : 0f)
                .alpha(hidden ? 0f : 1f)
                .setDuration(220)
                .setInterpolator(new android.view.animation.DecelerateInterpolator())
                .start();
    }

    private void navigateTo(NavController navController, int destinationId) {
        if (navController.getCurrentDestination() != null
                && navController.getCurrentDestination().getId() != destinationId) {
            navController.navigate(destinationId);
        }
    }

    private void navigateTo(NavController navController, int destinationId, Bundle arguments) {
        if (navController.getCurrentDestination() != null
                && navController.getCurrentDestination().getId() != destinationId) {
            navController.navigate(destinationId, arguments);
        }
    }

    private void updateBottomNavigation(MaterialButton centerActionButton,
                                        ImageView homeButton,
                                        ImageView communityButton,
                                        ImageView ordersButton,
                                        ImageView profileButton,
                                        NavDestination destination) {
        int destinationId = destination.getId();
        
        boolean sellerSurface = currentRole == UserRole.SELLER && isSellerSurface(destinationId);
        boolean adminSurface = currentRole == UserRole.ADMIN
                && (destinationId == R.id.adminDashboardFragment || destinationId == R.id.adminResourceFragment);
        // Keep role-specific bottom navigation available throughout Seller workflows.
        boolean show = sellerSurface || adminSurface || destinationId == R.id.guestHomeFragment
                || destinationId == R.id.feedFragment
                || destinationId == R.id.cartFragment
                || destinationId == R.id.orderConfirmationFragment
                || destinationId == R.id.customerOrderListFragment
                || destinationId == R.id.profileFragment
                || destinationId == R.id.guestProfileFragment
                || destinationId == R.id.sellerProfileFragment
                // Home related
                || destinationId == R.id.allCategoriesFragment
                || destinationId == R.id.categoryDetailFragment
                || destinationId == R.id.rescueHubFragment
                || destinationId == R.id.flashSaleFragment
                || destinationId == R.id.mysteryBoxFragment
                || destinationId == R.id.rescueOfferEditorFragment
                || destinationId == R.id.mysteryBoxEditorFragment
                || destinationId == R.id.flashRescueEditorFragment
                || destinationId == R.id.allCriticalCampaignsFragment
                || destinationId == R.id.allValueProductsFragment
                || destinationId == R.id.allActiveCampaignsFragment
                || destinationId == R.id.discoveryFragment
                // Profile related
                || destinationId == R.id.editProfileFragment
                || destinationId == R.id.addressListFragment
                || destinationId == R.id.addressEditorFragment
                || destinationId == R.id.favoriteFragment
                || destinationId == R.id.promotionWalletFragment
                // Product/Campaign
                || destinationId == R.id.productDetailFragment
                || destinationId == R.id.publicSellerProfileFragment
                || destinationId == R.id.campaignDetailFragment
                || destinationId == R.id.productListFragment
                || destinationId == R.id.campaignListFragment
                // Feed related
                // Engagement
                || destinationId == R.id.reviewFragment
                || destinationId == R.id.reportFragment
                || destinationId == R.id.notificationFragment
                // Order
                || destinationId == R.id.orderDetailFragment
                // Checkout
                || destinationId == R.id.checkoutFragment;

        View navHost = findViewById(R.id.main);
        FrameLayout.LayoutParams navHostParams = (FrameLayout.LayoutParams) navHost.getLayoutParams();
        // Keep the content viewport beneath the floating navigation surface. Each scroll
        // container reserves its own scroll range for the visible navigation bar.
        int desiredBottomMargin = 0;
        if (navHostParams.bottomMargin != desiredBottomMargin) {
            navHostParams.bottomMargin = desiredBottomMargin;
            navHost.setLayoutParams(navHostParams);
        }
        View bottomNavigation = findViewById(R.id.customerBottomNavigationContainer);
        bottomNavigation.animate().cancel();
        bottomNavigationAvailable = show;
        bottomNavigation.setVisibility(show && !keyboardVisible ? View.VISIBLE : View.GONE);
        bottomNavigation.setTranslationY(0f);
        bottomNavigation.setAlpha(1f);
        bottomNavigationHidden = false;
        scrollDirectionDistance = 0;
        ViewCompat.requestApplyInsets(findViewById(R.id.mainRoot));
        
        int inactive = ContextCompat.getColor(this, R.color.rescue_on_surface_variant);
        int active = ContextCompat.getColor(this, R.color.rescue_splash_green);
        
        // Highlight home button for home and related fragments
        boolean isHomeActive = destinationId == R.id.guestHomeFragment
                || destinationId == R.id.sellerProductListFragment
                || destinationId == R.id.productEditorFragment
                || destinationId == R.id.batchManagerFragment
                || destinationId == R.id.promotionEditorFragment
                || destinationId == R.id.sellerCampaignListFragment
                || destinationId == R.id.campaignEditorFragment
                || destinationId == R.id.mobileRescueFragment
                || destinationId == R.id.allCategoriesFragment
                || destinationId == R.id.categoryDetailFragment
                || destinationId == R.id.rescueHubFragment
                || destinationId == R.id.flashSaleFragment
                || destinationId == R.id.mysteryBoxFragment
                || destinationId == R.id.rescueOfferEditorFragment
                || destinationId == R.id.mysteryBoxEditorFragment
                || destinationId == R.id.flashRescueEditorFragment
                || destinationId == R.id.allCriticalCampaignsFragment
                || destinationId == R.id.allValueProductsFragment
                || destinationId == R.id.allActiveCampaignsFragment
                || destinationId == R.id.discoveryFragment
                || destinationId == R.id.productDetailFragment
                || destinationId == R.id.publicSellerProfileFragment
                || destinationId == R.id.campaignDetailFragment
                || destinationId == R.id.productListFragment
                || destinationId == R.id.campaignListFragment;
        
        // Highlight community button for feed related
        boolean isCommunityActive = destinationId == R.id.feedFragment;
        
        // Highlight orders button for order related
        boolean isOrdersActive = destinationId == R.id.orderConfirmationFragment
                || destinationId == R.id.sellerOrderListFragment
                || destinationId == R.id.customerOrderListFragment
                || destinationId == R.id.orderDetailFragment
                || destinationId == R.id.checkoutFragment;
        
        // Highlight profile button for profile related
        boolean isProfileActive = destinationId == R.id.profileFragment
                || destinationId == R.id.guestProfileFragment
                || destinationId == R.id.editProfileFragment
                || destinationId == R.id.addressListFragment
                || destinationId == R.id.addressEditorFragment
                || destinationId == R.id.favoriteFragment
                || destinationId == R.id.promotionWalletFragment
                || destinationId == R.id.notificationFragment
                || destinationId == R.id.reviewFragment
                || destinationId == R.id.reportFragment;

        boolean isCenterActive = sellerSurface && (destinationId == R.id.sellerDashboardFragment
                    || destinationId == R.id.sellerProfileFragment)
                || adminSurface;
        
        tintNavigationIcon(homeButton, isHomeActive, active, inactive);
        tintNavigationIcon(communityButton, isCommunityActive, active, inactive);
        tintNavigationIcon(ordersButton, isOrdersActive, active, inactive);
        tintNavigationIcon(profileButton, isProfileActive, active, inactive);
        centerActionButton.setVisibility(show ? android.view.View.VISIBLE : android.view.View.GONE);
        centerActionButton.setAlpha(isCenterActive ? 1f : 0.88f);
    }

    private boolean isSellerSurface(int destinationId) {
        return destinationId == R.id.sellerProfileFragment
                || destinationId == R.id.sellerApplicationFragment
                || destinationId == R.id.sellerDashboardFragment
                || destinationId == R.id.sellerProductListFragment
                || destinationId == R.id.productEditorFragment
                || destinationId == R.id.batchManagerFragment
                || destinationId == R.id.promotionEditorFragment
                || destinationId == R.id.sellerCampaignListFragment
                || destinationId == R.id.campaignEditorFragment
                || destinationId == R.id.mobileRescueFragment
                || destinationId == R.id.sellerOrderListFragment
                || destinationId == R.id.storefrontFragment;
    }

    private void tintNavigationIcon(ImageView icon, boolean selected, int active, int inactive) {
        icon.setImageTintList(android.content.res.ColorStateList.valueOf(
                selected ? active : inactive
        ));
    }
}
