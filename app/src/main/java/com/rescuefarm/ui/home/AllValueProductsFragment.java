package com.rescuefarm.ui.home;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.Space;
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
        
        viewModel.getHomeState().observe(getViewLifecycleOwner(), state -> 
                renderProducts(state.getValueProducts()));
    }

    private void renderProducts(List<Product> products) {
        productContainer.removeAllViews();
        
        if (products == null || products.isEmpty()) {
            messageView.setVisibility(View.VISIBLE);
            messageView.setText("Chưa có sản phẩm giá tốt.");
            productContainer.setVisibility(View.GONE);
            return;
        }
        
        messageView.setVisibility(View.GONE);
        productContainer.setVisibility(View.VISIBLE);
        
        // Render in 2 columns
        for (int index = 0; index < products.size(); index += 2) {
            LinearLayout row = new LinearLayout(requireContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setClipChildren(false);
            row.setClipToPadding(false);
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, -2);
            rowParams.setMargins(0, dp(2), 0, dp(5));
            productContainer.addView(row, rowParams);

            Product firstProduct = products.get(index);
            View first = renderer.product(firstProduct, 
                    v -> openProduct(firstProduct.getId()));
            LinearLayout.LayoutParams firstParams = new LinearLayout.LayoutParams(0, -2, 1f);
            firstParams.setMargins(0, 0, dp(6), 0);
            row.addView(first, firstParams);
            
            if (index + 1 < products.size()) {
                Product secondProduct = products.get(index + 1);
                View second = renderer.product(secondProduct, 
                        v -> openProduct(secondProduct.getId()));
                row.addView(second, new LinearLayout.LayoutParams(0, -2, 1f));
            } else {
                row.addView(new Space(requireContext()), 
                        new LinearLayout.LayoutParams(0, 1, 1f));
            }
        }
    }

    private void openProduct(String id) {
        Bundle args = new Bundle();
        args.putString("productId", id);
        Navigation.findNavController(requireView()).navigate(
                R.id.action_allValueProductsFragment_to_productDetailFragment, args);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
