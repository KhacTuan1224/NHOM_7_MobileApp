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
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.mainRoot), (view, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            view.setPadding(systemBars.left, systemBars.top, systemBars.right,
                    Math.max(systemBars.bottom, ime.bottom));
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
        centerActionButton.setOnClickListener(unused -> navigateTo(navController,
                currentRole == UserRole.SELLER ? R.id.sellerDashboardFragment : R.id.cartFragment));
        homeButton.setOnClickListener(unused -> navigateTo(navController, R.id.guestHomeFragment));
        communityButton.setOnClickListener(unused -> navigateTo(navController, R.id.feedFragment));
        ordersButton.setOnClickListener(unused -> navigateTo(navController,
                currentRole == UserRole.SELLER ? R.id.sellerOrderListFragment : R.id.customerOrderListFragment));
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
        centerActionButton.setEnabled(!roleUnresolved);
        centerActionButton.setIconResource(seller ? R.drawable.ic_nav_dashboard : R.drawable.ic_nav_cart);
        centerActionButton.setContentDescription(getString(roleUnresolved
                ? R.string.nav_action_loading : seller ? R.string.nav_seller_dashboard : R.string.nav_cart));
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

    private void updateBottomNavigation(MaterialButton centerActionButton,
                                        ImageView homeButton,
                                        ImageView communityButton,
                                        ImageView ordersButton,
                                        ImageView profileButton,
                                        NavDestination destination) {
        int destinationId = destination.getId();
        
        boolean sellerSurface = currentRole == UserRole.SELLER && isSellerSurface(destinationId);
        // Keep role-specific bottom navigation available throughout Seller workflows.
        boolean show = sellerSurface || destinationId == R.id.guestHomeFragment
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
        int reservedNavigationHeight = show
                ? getResources().getDimensionPixelSize(R.dimen.customer_bottom_navigation_height) : 0;
        if (navHostParams.bottomMargin != reservedNavigationHeight) {
            navHostParams.bottomMargin = reservedNavigationHeight;
            navHost.setLayoutParams(navHostParams);
        }
                
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
        
        tintNavigationIcon(homeButton, isHomeActive, active, inactive);
        tintNavigationIcon(communityButton, isCommunityActive, active, inactive);
        tintNavigationIcon(ordersButton, isOrdersActive, active, inactive);
        tintNavigationIcon(profileButton, isProfileActive, active, inactive);
        centerActionButton.setVisibility(show ? android.view.View.VISIBLE : android.view.View.GONE);
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
