package com.rescuefarm;

import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ScrollView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowCompat;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.Fragment;
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
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuth.AuthStateListener;

public class MainActivity extends AppCompatActivity {
    private AuthStateListener authStateListener;
    private UserRole currentRole;
    private final MutableLiveData<UserRole> actorRole = new MutableLiveData<>();
    private NavController navController;
    private MaterialButton centerActionButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        OfflineSyncScheduler.schedule(getApplicationContext());
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        getWindow().setNavigationBarColor(ContextCompat.getColor(this, R.color.rescue_nav_surface));
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView())
                .setAppearanceLightNavigationBars(true);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q)
            getWindow().setNavigationBarContrastEnforced(false);
        View navigationBackground = findViewById(R.id.systemNavigationBackground);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.mainRoot), (view, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
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
                navController.navigate(R.id.loginFragment);
            } else {
                navigateTo(navController, R.id.profileFragment);
            }
        });
        navController.addOnDestinationChangedListener((controller, destination, arguments) ->
                updateBottomNavigation(centerActionButton, homeButton, communityButton,
                        ordersButton, profileButton, destination)
        );
        authStateListener = firebaseAuth -> restoreActorRole();
    }

    @Override protected void onStart() {
        super.onStart();
        if (authStateListener != null) FirebaseAuth.getInstance().addAuthStateListener(authStateListener);
    }

    @Override protected void onStop() {
        if (authStateListener != null) FirebaseAuth.getInstance().removeAuthStateListener(authStateListener);
        super.onStop();
    }

    private void restoreActorRole() {
        boolean hasSession = FirebaseAuth.getInstance().getCurrentUser() != null;
        currentRole = null;
        actorRole.setValue(null);
        updateCenterAction(hasSession);
        if (!hasSession) return;
        AuthRepositoryFactory.create(this).restoreSession(
                new com.rescuefarm.data.repository.AuthRepository.AuthCallback() {
                    @Override public void onSuccess(User user) {
                        currentRole = user == null ? null : user.getRole();
                        actorRole.setValue(currentRole);
                        updateCenterAction();
                    }
                    @Override public void onError(com.rescuefarm.data.repository.AuthRepository.ErrorCode error,
                            String message) {
                        updateCenterAction(true);
                        actorRole.setValue(null);
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
    public LiveData<UserRole> observeActorRole() { return actorRole; }

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
                || destinationId == R.id.sellerProfileFragment
                // Home related
                || destinationId == R.id.allCategoriesFragment
                || destinationId == R.id.allCriticalCampaignsFragment
                || destinationId == R.id.allValueProductsFragment
                || destinationId == R.id.allActiveCampaignsFragment
                || destinationId == R.id.discoveryFragment
                // Profile related
                || destinationId == R.id.editProfileFragment
                || destinationId == R.id.addressListFragment
                || destinationId == R.id.addressEditorFragment
                || destinationId == R.id.favoriteFragment
                // Product/Campaign
                || destinationId == R.id.productDetailFragment
                || destinationId == R.id.campaignDetailFragment
                || destinationId == R.id.productListFragment
                || destinationId == R.id.campaignListFragment
                // Feed related
                || destinationId == R.id.postDetailFragment
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
        // Draw the page beneath the floating navigation surface so its translucency shows content.
        // Scroll pages receive matching bottom clearance so the final action remains reachable.
        int navigationOverlayHeight = show
                ? getResources().getDimensionPixelSize(R.dimen.customer_bottom_navigation_height) : 0;
        if (navHostParams.bottomMargin != 0) {
            navHostParams.bottomMargin = 0;
            navHost.setLayoutParams(navHostParams);
        }
        if (show) applyNavigationScrollClearance(navigationOverlayHeight);
                
        findViewById(R.id.customerBottomNavigationContainer).setVisibility(
                show ? android.view.View.VISIBLE : android.view.View.GONE
        );
        
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
                || destinationId == R.id.sellerPostListFragment
                || destinationId == R.id.allCategoriesFragment
                || destinationId == R.id.allCriticalCampaignsFragment
                || destinationId == R.id.allValueProductsFragment
                || destinationId == R.id.allActiveCampaignsFragment
                || destinationId == R.id.discoveryFragment
                || destinationId == R.id.productDetailFragment
                || destinationId == R.id.campaignDetailFragment
                || destinationId == R.id.productListFragment
                || destinationId == R.id.campaignListFragment;
        
        // Highlight community button for feed related
        boolean isCommunityActive = destinationId == R.id.feedFragment
                || destinationId == R.id.postDetailFragment;
        
        // Highlight orders button for order related
        boolean isOrdersActive = destinationId == R.id.orderConfirmationFragment
                || destinationId == R.id.sellerOrderListFragment
                || destinationId == R.id.customerOrderListFragment
                || destinationId == R.id.orderDetailFragment
                || destinationId == R.id.checkoutFragment;
        
        // Highlight profile button for profile related
        boolean isProfileActive = destinationId == R.id.profileFragment
                || destinationId == R.id.editProfileFragment
                || destinationId == R.id.addressListFragment
                || destinationId == R.id.addressEditorFragment
                || destinationId == R.id.favoriteFragment
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

    private void applyNavigationScrollClearance(int navigationHeight) {
        NavHostFragment host = (NavHostFragment) getSupportFragmentManager().findFragmentById(R.id.main);
        if (host == null) return;
        Fragment current = host.getChildFragmentManager().getPrimaryNavigationFragment();
        View root = current == null ? null : current.getView();
        View scroll = findVerticalScroll(root);
        if (scroll == null || !(scroll instanceof ViewGroup)) return;
        ViewGroup scrollGroup = (ViewGroup) scroll;
        int contentPaddingBottom = scrollGroup.getChildCount() == 0 ? 0
                : scrollGroup.getChildAt(0).getPaddingBottom();
        int currentPaddingBottom = scroll.getPaddingBottom();
        int extra = Math.max(0, navigationHeight - contentPaddingBottom - currentPaddingBottom);
        if (extra > 0) scroll.setPadding(scroll.getPaddingLeft(), scroll.getPaddingTop(),
                scroll.getPaddingRight(), currentPaddingBottom + extra);
        scrollGroup.setClipToPadding(false);
    }

    private View findVerticalScroll(View view) {
        if (view == null) return null;
        if (view instanceof NestedScrollView || view instanceof ScrollView) return view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                View match = findVerticalScroll(group.getChildAt(index));
                if (match != null) return match;
            }
        }
        return null;
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
                || destinationId == R.id.sellerPostListFragment
                || destinationId == R.id.postEditorFragment
                || destinationId == R.id.sellerOrderListFragment
                || destinationId == R.id.storefrontFragment;
    }

    private void tintNavigationIcon(ImageView icon, boolean selected, int active, int inactive) {
        icon.setImageTintList(android.content.res.ColorStateList.valueOf(
                selected ? active : inactive
        ));
    }
}
