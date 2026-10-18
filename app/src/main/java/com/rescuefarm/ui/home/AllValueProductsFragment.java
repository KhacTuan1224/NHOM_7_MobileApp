package com.rescuefarm.ui.home;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import com.rescuefarm.R;
import com.rescuefarm.domain.model.Product;
import java.util.List;

public class AllValueProductsFragment extends Fragment {
    private final com.rescuefarm.ui.common.ListPagination pagination = new com.rescuefarm.ui.common.ListPagination();
    private HomeViewModel viewModel;
    private HomeCardRenderer renderer;
    private LinearLayout productContainer;
    private TextView messageView;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_all_value_products, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        renderer = new HomeCardRenderer(this);
        productContainer = view.findViewById(R.id.productContainer);
        messageView = view.findViewById(R.id.messageView);

        viewModel = new ViewModelProvider(requireActivity(), new HomeViewModelFactory(requireContext()))
                .get(HomeViewModel.class);
        viewModel.ensureLoaded();
        
        viewModel.getHomeState().observe(getViewLifecycleOwner(), state -> 
                renderProducts(state.getValueProducts()));
    }

    private void renderProducts(List<Product> products) {
        pagination.remote(viewModel::hasMoreProducts, viewModel::isPageLoading, () -> viewModel.loadNextPage(true, false)).render(productContainer, products, this::renderProductsPage);
    }
    private void renderProductsPage(List<Product> products) {
        productContainer.removeAllViews();
        
        if (products == null || products.isEmpty()) {
            messageView.setVisibility(View.VISIBLE);
            messageView.setText("Chưa có sản phẩm giá tốt.");
            productContainer.setVisibility(View.GONE);
            return;
        }
        
        messageView.setVisibility(View.GONE);
        productContainer.setVisibility(View.VISIBLE);
        
        for (Product product : products) {
            productContainer.addView(renderer.product(product, v -> openProduct(product.getId())),
                    new LinearLayout.LayoutParams(-1, -2));
        }
    }

    private void openProduct(String id) {
        Bundle args = new Bundle();
        args.putString("productId", id);
        Navigation.findNavController(requireView()).navigate(
                R.id.action_allValueProductsFragment_to_productDetailFragment, args);
    }

}
