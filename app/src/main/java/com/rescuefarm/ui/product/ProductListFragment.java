package com.rescuefarm.ui.product;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import com.bumptech.glide.Glide;
import com.google.android.material.card.MaterialCardView;
import com.rescuefarm.R;
import com.rescuefarm.domain.model.Product;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

public class ProductListFragment extends Fragment {
    private ProductViewModel viewModel; private LinearLayout container; private TextView empty;
    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater,
            @Nullable ViewGroup parent, @Nullable Bundle state) {
        return inflater.inflate(R.layout.fragment_product_list, parent, false);
    }
    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        container = view.findViewById(R.id.productContainer); empty = view.findViewById(R.id.emptyProductText);
        viewModel = new ViewModelProvider(this, new ProductViewModelFactory(requireContext()))
                .get(ProductViewModel.class);
        viewModel.getProducts().observe(getViewLifecycleOwner(), this::renderProducts);
        viewModel.getState().observe(getViewLifecycleOwner(), value -> {
            if (value.getStatus() == ProductScreenState.Status.ERROR) {
                Toast.makeText(requireContext(), value.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
        view.findViewById(R.id.refreshCatalogButton).setOnClickListener(v -> viewModel.refreshCatalog());
        viewModel.refreshCatalog();
    }
    private void renderProducts(List<Product> products) {
        container.removeAllViews(); boolean isEmpty = products == null || products.isEmpty();
        empty.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        if (isEmpty) return;
        for (Product product : products) container.addView(card(product));
    }
    private View card(Product product) {
        MaterialCardView card = new MaterialCardView(requireContext());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2); params.setMargins(0, 0, 0, dp(12));
        card.setLayoutParams(params); card.setRadius(dp(16)); card.setStrokeWidth(dp(1)); card.setCardElevation(0);
        LinearLayout row = new LinearLayout(requireContext()); row.setOrientation(LinearLayout.HORIZONTAL); row.setPadding(dp(12), dp(12), dp(12), dp(12));
        ImageView image = new ImageView(requireContext()); image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        row.addView(image, new LinearLayout.LayoutParams(dp(96), dp(96)));
        if (!product.getImageUrls().isEmpty()) Glide.with(this).load(product.getImageUrls().get(0)).centerCrop().into(image);
        LinearLayout text = new LinearLayout(requireContext()); text.setOrientation(LinearLayout.VERTICAL); text.setPadding(dp(12), 0, 0, 0);
        TextView name = new TextView(requireContext()); name.setText(product.getName()); name.setTextSize(18); name.setTypeface(name.getTypeface(), android.graphics.Typeface.BOLD);
        TextView price = new TextView(requireContext()); price.setText(money(product.getRescuePrice()) + " / " + product.getUnit());
        TextView origin = new TextView(requireContext()); origin.setText(product.getOrigin() + " • " + product.getProvince());
        text.addView(name); text.addView(price); text.addView(origin); row.addView(text, new LinearLayout.LayoutParams(0, -2, 1)); card.addView(row);
        card.setOnClickListener(v -> {
            Bundle args = new Bundle(); args.putString("productId", product.getId());
            Navigation.findNavController(requireView()).navigate(R.id.action_productListFragment_to_productDetailFragment, args);
        });
        return card;
    }
    private String money(double value) { return NumberFormat.getCurrencyInstance(new Locale("vi", "VN")).format(value); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
