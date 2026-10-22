package com.rescuefarm.ui.product;
import com.rescuefarm.ui.common.EnumLabels;
import com.rescuefarm.ui.seller.SellerUiStyles;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import com.google.android.material.card.MaterialCardView;
import com.rescuefarm.R;
import com.rescuefarm.domain.model.Product;
import java.util.List;

public class SellerProductListFragment extends Fragment {
    private final com.rescuefarm.ui.common.ListPagination pagination = new com.rescuefarm.ui.common.ListPagination();
    private ProductViewModel viewModel; private LinearLayout container; private List<Product> currentProducts;
    private boolean batchSelectionMode;
    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater,
            @Nullable ViewGroup parent, @Nullable Bundle state) {
        return inflater.inflate(R.layout.fragment_seller_product_list, parent, false);
    }
    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        Bundle args = getArguments();
        batchSelectionMode = args != null && args.getBoolean("batchSelectionMode", false);
        SellerUiStyles.styleActions(view, batchSelectionMode ? SellerUiStyles.Section.BATCHES : SellerUiStyles.Section.PRODUCTS);
        TextView title = view.findViewById(R.id.sellerProductListTitle);
        TextView notice = view.findViewById(R.id.sellerProductListNotice);
        if (batchSelectionMode) {
            title.setText(R.string.seller_batch_products_title);
            notice.setText(R.string.seller_batch_products_notice);
        }
        container = view.findViewById(R.id.sellerProductContainer);
        View addProduct = view.findViewById(R.id.addProductButton);
        addProduct.setVisibility(batchSelectionMode ? View.GONE : View.VISIBLE);
        addProduct.setOnClickListener(
                Navigation.createNavigateOnClickListener(R.id.action_sellerProductListFragment_to_productEditorFragment));
        viewModel = new ViewModelProvider(this, new ProductViewModelFactory(requireContext()))
                .get(ProductViewModel.class);
        viewModel.getSellerProducts().observe(getViewLifecycleOwner(), this::render);
        viewModel.getSellerProductsHaveMore().observe(getViewLifecycleOwner(), unused -> {
            if (currentProducts != null) render(currentProducts);
        });
        viewModel.getState().observe(getViewLifecycleOwner(), value -> {
            if (currentProducts != null) render(currentProducts);
            if (value.getStatus() == ProductScreenState.Status.ERROR)
                Toast.makeText(requireContext(), value.getMessage(), Toast.LENGTH_LONG).show();
            else if (value.getStatus() == ProductScreenState.Status.SAVED) {
                Toast.makeText(requireContext(), value.getMessage(), Toast.LENGTH_SHORT).show();
                viewModel.refreshSellerProductPage(true);
            }
        });
    }
    @Override public void onResume() { super.onResume(); if (viewModel != null) viewModel.refreshSellerProductPage(true); }
    private void render(List<Product> products) {
        currentProducts = products;
        pagination.remote(() -> Boolean.TRUE.equals(viewModel.getSellerProductsHaveMore().getValue()),
                viewModel::isSellerPageLoading, () -> viewModel.refreshSellerProductPage(false))
                .render(container, products, this::renderPage);
    }
    private void renderPage(List<Product> products) {
        container.removeAllViews(); if (products == null) return;
        for (Product product : products) container.addView(card(product));

    }
    private View card(Product product) {
        SellerUiStyles.Row row = SellerUiStyles.row(requireContext(), batchSelectionMode
                ? SellerUiStyles.Section.BATCHES : SellerUiStyles.Section.PRODUCTS);
        row.title(product.getName());
        row.body("Trạng thái: " + EnumLabels.label(product.getStatus()));
        row.body(java.text.NumberFormat.getCurrencyInstance(java.util.Locale.forLanguageTag("vi-VN"))
                .format(product.getRescuePrice()) + " / " + product.getUnit());
        row.metadata("ID: " + product.getId());
        if (!batchSelectionMode) {
            row.action("Sửa", "Chỉnh sửa sản phẩm", v -> navigateEditor(product));
            row.action("Lô hàng", getString(R.string.manage_batches_action), v -> navigateBatches(product));
            boolean hidden = product.getStatus() == com.rescuefarm.domain.enums.ProductStatus.HIDDEN;
            row.action(hidden ? "Hiện" : "Ẩn", hidden ? "Hiện lại sản phẩm" : getString(R.string.hide_product_action),
                    v -> { if (hidden) viewModel.showProduct(product.getId());
                        else viewModel.hideProduct(product.getId()); });
        } else {
            row.action("Lô hàng", getString(R.string.open_product_batches_action), v -> navigateBatches(product));
        }
        return row.root;
    }
    private void navigateEditor(Product product) {
        Bundle args = productBundle(product);
        Navigation.findNavController(requireView()).navigate(R.id.action_sellerProductListFragment_to_productEditorFragment, args);
    }
    private void navigateBatches(Product product) {
        Bundle args = new Bundle(); args.putString("productId", product.getId()); args.putString("productName", product.getName());
        Navigation.findNavController(requireView()).navigate(R.id.action_sellerProductListFragment_to_batchManagerFragment, args);
    }


    private Bundle productBundle(Product value) {
        Bundle b = new Bundle(); b.putString("id", value.getId()); b.putString("categoryId", value.getCategoryId());
        b.putString("name", value.getName()); b.putString("description", value.getDescription());
        b.putDouble("originalPrice", value.getOriginalPrice()); b.putDouble("rescuePrice", value.getRescuePrice());
        b.putString("unit", value.getUnit()); b.putString("origin", value.getOrigin()); b.putString("province", value.getProvince());
        b.putString("imageUrls", String.join("\n", value.getImageUrls())); b.putString("status", value.getStatus().name()); return b;
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
