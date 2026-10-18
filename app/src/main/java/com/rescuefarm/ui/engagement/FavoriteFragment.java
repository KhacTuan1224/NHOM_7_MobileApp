package com.rescuefarm.ui.engagement;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.Space;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import com.google.firebase.auth.FirebaseAuth;
import com.rescuefarm.R;
import com.rescuefarm.domain.model.Favorite;
import com.rescuefarm.domain.model.Product;
import com.rescuefarm.ui.home.HomeCardRenderer;
import com.rescuefarm.ui.product.ProductViewModel;
import com.rescuefarm.ui.product.ProductViewModelFactory;
import java.util.ArrayList;
import java.util.List;

public class FavoriteFragment extends Fragment {
    private final com.rescuefarm.ui.common.ListPagination pagination = new com.rescuefarm.ui.common.ListPagination();
    private EngagementViewModel engagementViewModel;
    private ProductViewModel productViewModel;
    private HomeCardRenderer renderer;
    private LinearLayout container;
    private TextView emptyText;
    private String userId = "";
    private List<Product> products = new ArrayList<>();

    @Nullable
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup parent,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_favorites, parent, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        renderer = new HomeCardRenderer(this);
        container = view.findViewById(R.id.favoriteContainer);
        emptyText = view.findViewById(R.id.emptyFavoriteText);
        
        userId = FirebaseAuth.getInstance().getCurrentUser() == null 
                ? "" 
                : FirebaseAuth.getInstance().getCurrentUser().getUid();
        
        engagementViewModel = new ViewModelProvider(this, 
                new EngagementViewModelFactory(requireContext()))
                .get(EngagementViewModel.class);
        
        productViewModel = new ViewModelProvider(this,
                new ProductViewModelFactory(requireContext()))
                .get(ProductViewModel.class);
        
        engagementViewModel.favorites().observe(getViewLifecycleOwner(), 
                this::onFavoritesLoaded);
        
        productViewModel.getProducts().observe(getViewLifecycleOwner(), 
                this::onProductsLoaded);
        
        engagementViewModel.getState().observe(getViewLifecycleOwner(), state -> {
            if (state.getStatus() == EngagementState.Status.ERROR) {
                Toast.makeText(requireContext(), state.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
        
        engagementViewModel.refreshFavorites(userId);
        productViewModel.refreshCatalog();
    }

    private void onFavoritesLoaded(List<Favorite> favorites) {
        if (products.isEmpty()) {
            // Wait for products to load
            return;
        }
        renderFavorites(favorites);
    }

    private void onProductsLoaded(List<Product> loadedProducts) {
        this.products = loadedProducts == null ? new ArrayList<>() : loadedProducts;
        List<Favorite> favorites = engagementViewModel.favorites().getValue();
        if (favorites != null) {
            renderFavorites(favorites);
        }
    }

    private void renderFavorites(List<Favorite> favorites) {
        container.removeAllViews();
        
        if (favorites == null || favorites.isEmpty()) {
            emptyText.setVisibility(View.VISIBLE);
            container.setVisibility(View.GONE);
            return;
        }
        
        emptyText.setVisibility(View.GONE);
        container.setVisibility(View.VISIBLE);
        
        // Filter products that are favorited
        List<Product> favoriteProducts = new ArrayList<>();
        for (Favorite favorite : favorites) {
            for (Product product : products) {
                if (product.getId().equals(favorite.getProductId())) {
                    favoriteProducts.add(product);
                    break;
                }
            }
        }
        
        if (favoriteProducts.isEmpty()) {
            emptyText.setVisibility(View.VISIBLE);
            container.setVisibility(View.GONE);
            return;
        }
        
        pagination.render(container, favoriteProducts, page -> {
            container.removeAllViews();
        // Render in 2 columns like AllValueProductsFragment
        for (int index = 0; index < page.size(); index += 2) {
            LinearLayout row = new LinearLayout(requireContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setClipChildren(false);
            row.setClipToPadding(false);
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, -2);
            rowParams.setMargins(0, dp(2), 0, dp(5));
            container.addView(row, rowParams);

            Product firstProduct = page.get(index);
            View first = renderer.product(firstProduct, 
                    v -> openProduct(firstProduct.getId()));
            LinearLayout.LayoutParams firstParams = new LinearLayout.LayoutParams(0, -2, 1f);
            firstParams.setMargins(0, 0, dp(6), 0);
            row.addView(first, firstParams);
            
            if (index + 1 < page.size()) {
                Product secondProduct = page.get(index + 1);
                View second = renderer.product(secondProduct, 
                        v -> openProduct(secondProduct.getId()));
                row.addView(second, new LinearLayout.LayoutParams(0, -2, 1f));
            } else {
                row.addView(new Space(requireContext()), 
                        new LinearLayout.LayoutParams(0, 1, 1f));
            }
        }
        });
    }

    private void openProduct(String productId) {
        Bundle args = new Bundle();
        args.putString("productId", productId);
        Navigation.findNavController(requireView()).navigate(
                R.id.action_favoriteFragment_to_productDetailFragment, args);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
