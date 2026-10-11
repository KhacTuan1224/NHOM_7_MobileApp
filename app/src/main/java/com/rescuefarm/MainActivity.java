package com.rescuefarm;

import android.os.Bundle;
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

import com.google.android.material.button.MaterialButton;
import com.rescuefarm.R;
import com.rescuefarm.data.repository.AuthRepositoryFactory;
import com.rescuefarm.service.sync.OfflineSyncScheduler;

public class MainActivity extends AppCompatActivity {

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

        NavController navController = ((NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.main)).getNavController();
        ImageView homeButton = findViewById(R.id.navHomeButton);
        ImageView communityButton = findViewById(R.id.navCommunityButton);
        ImageView ordersButton = findViewById(R.id.navOrdersButton);
        ImageView profileButton = findViewById(R.id.navProfileButton);
        MaterialButton centerCartButton = findViewById(R.id.centerCartButton);
        centerCartButton.setOnClickListener(unused -> navController.navigate(R.id.cartFragment));
        homeButton.setOnClickListener(unused -> navigateTo(navController, R.id.guestHomeFragment));
        communityButton.setOnClickListener(unused -> navigateTo(navController, R.id.feedFragment));
        ordersButton.setOnClickListener(unused ->
                navigateTo(navController, R.id.orderConfirmationFragment));
        profileButton.setOnClickListener(unused -> {
            if (!AuthRepositoryFactory.create(this).isAuthenticated()) {
                navController.navigate(R.id.loginFragment);
            } else {
                navigateTo(navController, R.id.profileFragment);
            }
        });
        navController.addOnDestinationChangedListener((controller, destination, arguments) ->
                updateBottomNavigation(centerCartButton, homeButton, communityButton,
                        ordersButton, profileButton, destination)
        );
    }

    private void navigateTo(NavController navController, int destinationId) {
        if (navController.getCurrentDestination() != null
                && navController.getCurrentDestination().getId() != destinationId) {
            navController.navigate(destinationId);
        }
    }

    private void updateBottomNavigation(MaterialButton centerCartButton,
                                        ImageView homeButton,
                                        ImageView communityButton,
                                        ImageView ordersButton,
                                        ImageView profileButton,
                                        NavDestination destination) {
        int destinationId = destination.getId();
        
        // Show bottom nav for most customer-facing screens
        boolean show = destinationId == R.id.guestHomeFragment
                || destinationId == R.id.feedFragment
                || destinationId == R.id.cartFragment
                || destinationId == R.id.orderConfirmationFragment
                || destinationId == R.id.profileFragment
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
                
        findViewById(R.id.customerBottomNavigationContainer).setVisibility(
                show ? android.view.View.VISIBLE : android.view.View.GONE
        );
        
        int inactive = ContextCompat.getColor(this, R.color.rescue_on_surface_variant);
        int active = ContextCompat.getColor(this, R.color.rescue_splash_green);
        
        // Highlight home button for home and related fragments
        boolean isHomeActive = destinationId == R.id.guestHomeFragment
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
        centerCartButton.setVisibility(show ? android.view.View.VISIBLE : android.view.View.GONE);
    }

    private void tintNavigationIcon(ImageView icon, boolean selected, int active, int inactive) {
        icon.setImageTintList(android.content.res.ColorStateList.valueOf(
                selected ? active : inactive
        ));
    }
}
