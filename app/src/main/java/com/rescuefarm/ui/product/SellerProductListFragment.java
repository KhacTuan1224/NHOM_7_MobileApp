package com.rescuefarm.ui.product;
import com.rescuefarm.ui.common.EnumLabels;

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
    private ProductViewModel viewModel; private LinearLayout container; private List<Product> currentProducts;
    private boolean batchSelectionMode;
    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater,
            @Nullable ViewGroup parent, @Nullable Bundle state) {
        return inflater.inflate(R.layout.fragment_seller_product_list, parent, false);
    }
    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        Bundle args = getArguments();
        batchSelectionMode = args != null && args.getBoolean("batchSelectionMode", false);
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
        container.removeAllViews(); if (products == null) return;
        for (Product product : products) container.addView(card(product));
        if (viewModel != null && Boolean.TRUE.equals(viewModel.getSellerProductsHaveMore().getValue())) {
            Button more = new Button(requireContext()); more.setAllCaps(false); more.setText("Tải thêm sản phẩm");
            more.setOnClickListener(v -> viewModel.refreshSellerProductPage(false)); container.addView(more);
        }
    }
    private View card(Product product) {
        MaterialCardView card = new MaterialCardView(requireContext());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2); params.setMargins(0, 0, 0, dp(12)); card.setLayoutParams(params);
        card.setRadius(dp(16)); card.setStrokeWidth(dp(1));
        LinearLayout body = new LinearLayout(requireContext()); body.setOrientation(LinearLayout.VERTICAL); body.setPadding(dp(14), dp(14), dp(14), dp(14));
        TextView title = new TextView(requireContext()); title.setText(product.getName() + " • " + EnumLabels.label(product.getStatus())); title.setTextSize(17); title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        LinearLayout actions = new LinearLayout(requireContext());
        Button batches = new Button(requireContext()); batches.setText(R.string.manage_batches_action); batches.setOnClickListener(v -> navigateBatches(product));
        actions.setOrientation(LinearLayout.VERTICAL);
        if (!batchSelectionMode) {
            Button edit = new Button(requireContext()); edit.setText(R.string.edit_action); edit.setOnClickListener(v -> navigateEditor(product));
            Button promotion = new Button(requireContext()); promotion.setText(R.string.manage_promotion_action); promotion.setOnClickListener(v -> navigatePromotion(product));
            Button hide = new Button(requireContext()); hide.setText(R.string.hide_product_action); hide.setOnClickListener(v -> viewModel.hideProduct(product.getId()));
            actions.addView(edit); actions.addView(batches); actions.addView(promotion); actions.addView(hide);
        } else {
            batches.setText(R.string.open_product_batches_action);
            actions.addView(batches);
        }
        body.addView(title); body.addView(actions); card.addView(body); return card;
    }
    private void navigateEditor(Product product) {
        Bundle args = productBundle(product);
        Navigation.findNavController(requireView()).navigate(R.id.action_sellerProductListFragment_to_productEditorFragment, args);
    }
    private void navigateBatches(Product product) {
        Bundle args = new Bundle(); args.putString("productId", product.getId()); args.putString("productName", product.getName());
        Navigation.findNavController(requireView()).navigate(R.id.action_sellerProductListFragment_to_batchManagerFragment, args);
    }
    private void navigatePromotion(Product product) {
        Bundle args = new Bundle(); args.putString("productId", product.getId());
        args.putString("productName", product.getName());
        args.putDouble("originalPrice", product.getOriginalPrice());
        args.putDouble("rescuePrice", product.getRescuePrice());
        Navigation.findNavController(requireView()).navigate(
                R.id.action_sellerProductListFragment_to_promotionEditorFragment, args);
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
