package com.rescuefarm.ui.product;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.Toast;
import com.google.android.material.button.MaterialButton;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import com.bumptech.glide.Glide;
import com.rescuefarm.R;
import com.rescuefarm.domain.model.Product;
import com.rescuefarm.domain.model.ProductBatch;
import com.rescuefarm.service.pricing.PriceBreakdown;
import com.rescuefarm.ui.cart.CartScreenState;
import com.rescuefarm.ui.cart.CartViewModel;
import com.rescuefarm.ui.cart.CartViewModelFactory;
import com.rescuefarm.ui.navigation.ActorUiPolicy;
import com.rescuefarm.MainActivity;
import com.rescuefarm.ui.engagement.*;
import com.google.firebase.auth.FirebaseAuth;
import java.util.ArrayList;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

public class ProductDetailFragment extends Fragment {
    private ProductViewModel viewModel; private String productId; private TextView stock;
    private Product currentProduct; private EditText quantityInput;
    private CartViewModel cartViewModel; private PriceBreakdown currentPrice;
    private List<ProductBatch> currentBatches = new ArrayList<>();
    private List<ProductBatch> visibleBatches = new ArrayList<>();
    private Spinner batchSpinner;
    private TextView batchHint;
    private String requestedBatchId = "";
    private String campaignId = "";
    private Set<String> campaignBatchIds = new HashSet<>();
    private boolean bindingBatches;
    private EngagementViewModel engagementViewModel;
    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater,
            @Nullable ViewGroup parent, @Nullable Bundle state) {
        return inflater.inflate(R.layout.fragment_product_detail, parent, false);
    }
    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        productId = getArguments() == null ? "" : getArguments().getString("productId", "");
        Bundle arguments = getArguments();
        requestedBatchId = arguments == null ? "" : clean(arguments.getString("batchId"));
        campaignId = arguments == null ? "" : clean(arguments.getString("campaignId"));
        if (arguments != null && arguments.getStringArrayList("campaignBatchIds") != null)
            campaignBatchIds.addAll(arguments.getStringArrayList("campaignBatchIds"));
        stock = view.findViewById(R.id.productStock);
        batchSpinner = view.findViewById(R.id.productBatchSpinner);
        batchHint = view.findViewById(R.id.productBatchHint);
        quantityInput = view.findViewById(R.id.pricingQuantityInput);
        double advertisedQuantity=arguments==null?1D:arguments.getDouble("pricingQuantity",1D);
        if(state==null && Double.isFinite(advertisedQuantity) && advertisedQuantity>0)
            quantityInput.setText(java.math.BigDecimal.valueOf(advertisedQuantity).stripTrailingZeros().toPlainString());
        viewModel = new ViewModelProvider(this, new ProductViewModelFactory(requireContext()))
                .get(ProductViewModel.class);
        cartViewModel = new ViewModelProvider(this, new CartViewModelFactory(requireContext()))
                .get(CartViewModel.class);
        engagementViewModel = new ViewModelProvider(this, new EngagementViewModelFactory(requireContext()))
                .get(EngagementViewModel.class);
        MaterialButton favoriteButton = view.findViewById(R.id.favoriteProductButton);
        String currentUserId = FirebaseAuth.getInstance().getCurrentUser() == null ? ""
                : FirebaseAuth.getInstance().getCurrentUser().getUid();
        Boolean[] favoriteState = {null};
        engagementViewModel.getState().observe(getViewLifecycleOwner(), value -> {
            if (value.getStatus() == EngagementState.Status.SUCCESS || value.getStatus() == EngagementState.Status.ERROR)
                Toast.makeText(requireContext(), value.getMessage(), Toast.LENGTH_SHORT).show();
            favoriteButton.setEnabled(currentUserId.isEmpty()
                    || (value.getStatus() != EngagementState.Status.LOADING && favoriteState[0] != null));
        });
        if (currentUserId.isEmpty()) {
            favoriteButton.setText(R.string.sign_in_to_favorite_action);
            favoriteButton.setOnClickListener(v -> Toast.makeText(requireContext(),
                    R.string.sign_in_to_favorite_action, Toast.LENGTH_SHORT).show());
        } else {
            favoriteButton.setEnabled(false);
            engagementViewModel.observeFavorite(currentUserId, productId).observe(
                    getViewLifecycleOwner(), isFavorite -> {
                        favoriteState[0] = isFavorite;
                        EngagementState currentState = engagementViewModel.getState().getValue();
                        boolean busy = currentState != null
                                && currentState.getStatus() == EngagementState.Status.LOADING;
                        favoriteButton.setEnabled(isFavorite != null && !busy);
                        favoriteButton.setText(isFavorite == null
                                ? R.string.favorite_state_unavailable
                                : Boolean.TRUE.equals(isFavorite)
                                        ? R.string.remove_favorite_action : R.string.add_favorite_action);
                    });
            favoriteButton.setOnClickListener(v -> engagementViewModel.toggleFavorite(
                    currentUserId, productId));
        }
        viewModel.getState().observe(getViewLifecycleOwner(), value -> {
            if (value.getStatus() == ProductScreenState.Status.PRODUCT) render(view, value.getProduct());
            else if (value.getStatus() == ProductScreenState.Status.ERROR)
                Toast.makeText(requireContext(), value.getMessage(), Toast.LENGTH_LONG).show();
        });
        viewModel.getBatches(productId).observe(getViewLifecycleOwner(), this::renderStock);
        batchSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View row,
                    int position, long id) {
                if (!bindingBatches && position >= 0 && position < visibleBatches.size()) {
                    ProductBatch selected = visibleBatches.get(position);
                    requestedBatchId = selected.getId();
                    renderBatchHint(selected);
                }
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        });
        viewModel.getPriceBreakdown().observe(getViewLifecycleOwner(), value -> renderPrice(view, value));
        view.findViewById(R.id.recalculatePriceButton).setOnClickListener(v -> recalculate());
        cartViewModel.getState().observe(getViewLifecycleOwner(), value -> {
            if (value.getStatus() == CartScreenState.Status.SAVED
                    || value.getStatus() == CartScreenState.Status.ERROR
                    || value.getStatus() == CartScreenState.Status.OFFLINE) {
                Toast.makeText(requireContext(), value.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
        view.findViewById(R.id.addToCartButton).setOnClickListener(v -> addToCart());
        view.findViewById(R.id.openCartButton).setOnClickListener(Navigation.createNavigateOnClickListener(
                R.id.action_productDetailFragment_to_cartFragment));
        view.findViewById(R.id.openReviewsButton).setOnClickListener(v -> { Bundle b=new Bundle();b.putString("productId",productId);Navigation.findNavController(v).navigate(R.id.action_productDetailFragment_to_reviewFragment,b); });
        view.findViewById(R.id.openPublicSellerButton).setOnClickListener(v -> {
            if (currentProduct == null || currentProduct.getSellerId() == null
                    || currentProduct.getSellerId().trim().isEmpty()) {
                Toast.makeText(requireContext(), "Chưa có hồ sơ công khai của cửa hàng.", Toast.LENGTH_SHORT).show();
                return;
            }
            Bundle args = new Bundle(); args.putString("sellerId", currentProduct.getSellerId());
            Navigation.findNavController(v).navigate(R.id.action_productDetailFragment_to_publicSellerProfileFragment, args);
        });
        view.findViewById(R.id.reportProductButton).setOnClickListener(v -> { Bundle b=new Bundle();b.putString("targetType","PRODUCT");b.putString("targetId",productId);Navigation.findNavController(v).navigate(R.id.action_productDetailFragment_to_reportFragment,b); });
        applyActorActions(view);
        if (requireActivity() instanceof MainActivity) {
            ((MainActivity) requireActivity()).observeActorRole().observe(getViewLifecycleOwner(), role ->
                    applyActorActions(view));
        }
        viewModel.loadProduct(productId); viewModel.refreshBatches(productId);
    }

    private void applyActorActions(View root) {
        boolean readOnly = ActorUiPolicy.isSellerReadOnly(this);
        int visibility = readOnly ? View.GONE : View.VISIBLE;
        root.findViewById(R.id.addToCartButton).setVisibility(visibility);
        root.findViewById(R.id.openCartButton).setVisibility(visibility);
        root.findViewById(R.id.favoriteProductButton).setVisibility(visibility);
        root.findViewById(R.id.reportProductButton).setVisibility(visibility);
        root.findViewById(R.id.pricingQuantityInput).setVisibility(visibility);
        root.findViewById(R.id.recalculatePriceButton).setVisibility(visibility);
        root.findViewById(R.id.priceBreakdownText).setVisibility(visibility);
    }
    private void render(View view, Product product) {
        currentProduct = product;
        if (!currentBatches.isEmpty()) renderStock(currentBatches);
        ((TextView) view.findViewById(R.id.productDetailName)).setText(product.getName());
        ((TextView) view.findViewById(R.id.productDetailDescription)).setText(product.getDescription());
        ((TextView) view.findViewById(R.id.productDetailPrice)).setText(
                NumberFormat.getCurrencyInstance(new Locale("vi", "VN")).format(product.getRescuePrice())
                        + " / " + product.getUnit());
        ((TextView) view.findViewById(R.id.productDetailOrigin)).setText(product.getOrigin() + " • " + product.getProvince());
        ((TextView) view.findViewById(R.id.productDiscount)).setText(
                getString(R.string.discount_format, product.calculateDiscountPercent()));
        if (!product.getImageUrls().isEmpty()) Glide.with(this).load(product.getImageUrls().get(0))
                .centerCrop().into((ImageView) view.findViewById(R.id.productDetailImage));
        recalculate();
    }
    private void recalculate() {
        if (currentProduct == null) return;
        try {
            double quantity = Double.parseDouble(quantityInput.getText().toString().trim());
            viewModel.calculatePrice(currentProduct, quantity);
        } catch (NumberFormatException error) {
            Toast.makeText(requireContext(), R.string.invalid_pricing_quantity, Toast.LENGTH_SHORT).show();
        }
    }
    private void renderPrice(View view, PriceBreakdown value) {
        if (value == null) return;
        currentPrice = value;
        NumberFormat money = NumberFormat.getCurrencyInstance(new Locale("vi", "VN"));
        String text = getString(R.string.price_breakdown_format,
                money.format(value.getOriginalPrice()), money.format(value.getRescuePrice()),
                money.format(value.getPromotionDiscount()), value.getVolumeDiscountPercent(),
                money.format(value.getFinalUnitPrice()), money.format(value.getLineSubtotal()));
        ((TextView) view.findViewById(R.id.priceBreakdownText)).setText(text);
    }
    private void renderStock(List<ProductBatch> batches) {
        currentBatches = batches == null ? new ArrayList<>() : new ArrayList<>(batches);
        String previous = requestedBatchId;
        visibleBatches.clear();
        double available = 0D; int sellable = 0;
        Date now = new Date();
        if (batches != null) for (ProductBatch batch : batches) {
            if (!campaignBatchIds.isEmpty() && !campaignBatchIds.contains(batch.getId())) continue;
            if (!campaignId.isEmpty() && !campaignId.equals(batch.getActiveCampaignId())) continue;
            if (!batch.isSellable(now)) continue;
            visibleBatches.add(batch);
            available += batch.getAvailableQuantity(); sellable++;
        }
        stock.setText(getString(R.string.product_stock_format, available, sellable));
        List<String> labels = new ArrayList<>();
        SimpleDateFormat date = new SimpleDateFormat("dd/MM/yyyy", Locale.forLanguageTag("vi-VN"));
        for (ProductBatch batch : visibleBatches) labels.add(getString(R.string.product_batch_choice_format,
                batch.getHarvestDate() == null ? "—" : date.format(batch.getHarvestDate()),
                batch.getExpiryDate() == null ? "—" : date.format(batch.getExpiryDate()),
                batch.getAvailableQuantity(), currentProduct == null ? "" : currentProduct.getUnit()));
        bindingBatches = true;
        batchSpinner.setAdapter(new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_dropdown_item, labels));
        int selected = 0;
        for (int i = 0; i < visibleBatches.size(); i++) {
            if (visibleBatches.get(i).getId().equals(previous)) { selected = i; break; }
        }
        if (!visibleBatches.isEmpty()) {
            batchSpinner.setSelection(selected);
            requestedBatchId = visibleBatches.get(selected).getId();
            renderBatchHint(visibleBatches.get(selected));
        } else {
            requestedBatchId = "";
            batchHint.setText(campaignId.isEmpty()
                    ? getString(R.string.product_no_sellable_batches)
                    : getString(R.string.product_campaign_batches_unavailable));
        }
        batchSpinner.setEnabled(visibleBatches.size() > 1);
        bindingBatches = false;
    }

    private void renderBatchHint(ProductBatch batch) {
        if (batchHint == null || batch == null) return;
        batchHint.setText(getString(R.string.product_batch_stock_format,
                batch.getAvailableQuantity(), batch.getReservedQuantity(), batch.getSoldQuantity()));
    }
    private void addToCart() {
        if (ActorUiPolicy.isSellerReadOnly(this)) return;
        if (currentProduct == null || currentPrice == null) return;
        double quantity;
        try { quantity = Double.parseDouble(quantityInput.getText().toString().trim()); }
        catch (NumberFormatException error) {
            Toast.makeText(requireContext(), R.string.invalid_pricing_quantity, Toast.LENGTH_SHORT).show(); return;
        }
        ProductBatch selected = null; java.util.Date now = new java.util.Date();
        for (ProductBatch batch : visibleBatches) if (batch.getId().equals(requestedBatchId)
                && batch.isSellable(now) && batch.hasAvailableStock(quantity)) { selected = batch; break; }
        if (selected == null) {
            Toast.makeText(requireContext(), R.string.cart_no_sellable_batch, Toast.LENGTH_LONG).show(); return;
        }
        cartViewModel.add(currentProduct, selected, quantity, currentPrice.getFinalUnitPrice());
    }
    private static String clean(String value) { return value == null ? "" : value.trim(); }
}
