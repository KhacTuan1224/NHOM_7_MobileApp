package com.rescuefarm.ui.product;

import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.annotation.*;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import com.rescuefarm.R;
import com.rescuefarm.domain.enums.PromotionType;
import com.rescuefarm.domain.model.Product;
import com.rescuefarm.domain.model.Promotion;
import com.rescuefarm.ui.common.CustomerUi;
import com.rescuefarm.ui.seller.SellerUiStyles;
import java.text.*;
import java.util.*;

/** Dedicated promotion overview; edits reuse the ownership-checked promotion repository. */
public final class SellerPromotionListFragment extends Fragment {
    private final com.rescuefarm.ui.common.ListPagination pagination = new com.rescuefarm.ui.common.ListPagination();
    private ProductViewModel viewModel;
    private LinearLayout list, chooser, choices;
    private TextView message;
    private EditText search;
    private List<Product> products = Collections.emptyList();
    private List<Promotion> promotions = Collections.emptyList();
    private final Map<String, Product> knownProducts = new HashMap<>();
    private final Set<String> watching = new HashSet<>();
    private final Locale locale = Locale.forLanguageTag("vi-VN");
    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup parent, @Nullable Bundle state) {
        LinearLayout root = CustomerUi.column(requireContext());
        LinearLayout header = new LinearLayout(requireContext());
        header.setGravity(Gravity.CENTER_VERTICAL); root.addView(header);
        com.rescuefarm.ui.common.AppBackButton back = new com.rescuefarm.ui.common.AppBackButton(requireContext());
        int target = Math.round(48 * getResources().getDisplayMetrics().density);
        header.addView(back, new LinearLayout.LayoutParams(target, target));
        back.setOnClickListener(v -> NavHostFragment.findNavController(this).popBackStack());
        TextView title = com.rescuefarm.ui.common.AppTypography.title(CustomerUi.text(header, "Khuyến mãi và giá sỉ"));
        title.setTextSize(24); title.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
        CustomerUi.text(root, "Quản lý mức giảm giá, bậc số lượng và thời gian áp dụng theo từng sản phẩm.");
        com.google.android.material.button.MaterialButton create = SellerUiStyles.button(requireContext(), SellerUiStyles.Section.PROMOTIONS, "Tạo ưu đãi", "Chọn sản phẩm để tạo hoặc sửa ưu đãi");
        root.addView(create);
        com.google.android.material.button.MaterialButton refresh = SellerUiStyles.button(requireContext(), SellerUiStyles.Section.PROMOTIONS, "Làm mới", "Tải lại ưu đãi");
        root.addView(refresh); refresh.setOnClickListener(v -> { viewModel.loadSellerPromotions(); viewModel.refreshSellerProductPage(true); });
        message = CustomerUi.text(root, "Đang tải ưu đãi…");
        chooser = CustomerUi.column(requireContext()); chooser.setVisibility(View.GONE); root.addView(chooser);
        search = new EditText(requireContext()); search.setHint("Tìm sản phẩm để áp dụng"); search.setSingleLine(true); chooser.addView(search);
        choices = CustomerUi.column(requireContext()); chooser.addView(choices);
        create.setOnClickListener(v -> { chooser.setVisibility(chooser.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE); renderChoices(); });
        search.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            public void onTextChanged(CharSequence s, int start, int before, int count) { renderChoices(); }
            public void afterTextChanged(android.text.Editable s) { }
        });
        list = CustomerUi.column(requireContext()); root.addView(list);
        return CustomerUi.screen(this, root);
    }
    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        viewModel = new ViewModelProvider(this, new ProductViewModelFactory(requireContext())).get(ProductViewModel.class);
        watching.clear();
        viewModel.getSellerProducts().observe(getViewLifecycleOwner(), values -> {
            products = values == null ? Collections.emptyList() : values;
            for (Product product : products) knownProducts.put(product.getId(), product);
            render(); renderChoices();
        });
        viewModel.getSellerProductsHaveMore().observe(getViewLifecycleOwner(), unused -> renderChoices());
        viewModel.getSellerPromotionsState().observe(getViewLifecycleOwner(), values -> {
            promotions = values == null ? Collections.emptyList() : values;
            for (Promotion promotion : promotions) if (watching.add(promotion.getProductId())) {
                String id = promotion.getProductId();
                viewModel.observeProduct(id).observe(getViewLifecycleOwner(), product -> {
                    if (product != null) { knownProducts.put(id, product); render(); }
                });
                viewModel.loadProduct(id);
            }
            render();
        });
        viewModel.getState().observe(getViewLifecycleOwner(), stateValue -> {
            if (stateValue.getStatus() == ProductScreenState.Status.ERROR) message.setText(stateValue.getMessage());
        });
    }
    @Override public void onResume() {
        super.onResume();
        if (viewModel != null) { viewModel.loadSellerPromotions(); viewModel.refreshSellerProductPage(true); }
    }
    private void renderChoices() {
        if (choices == null || chooser.getVisibility() != View.VISIBLE) return;
        choices.removeAllViews(); String term = search.getText().toString().trim().toLowerCase(locale);
        for (Product product : products) if (product.getName().toLowerCase(locale).contains(term)) {
            SellerUiStyles.Row row = SellerUiStyles.row(requireContext(), SellerUiStyles.Section.PROMOTIONS);
            row.title(product.getName());
            row.action("Chọn", "Thiết lập ưu đãi cho " + product.getName(), v -> edit(product.getId())); choices.addView(row.root);
        }
        if (choices.getChildCount() == 0) CustomerUi.text(choices, "Chưa tìm thấy sản phẩm phù hợp trong danh sách đã tải.");
        if (viewModel != null && Boolean.TRUE.equals(viewModel.getSellerProductsHaveMore().getValue())) {
            com.google.android.material.button.MaterialButton more = SellerUiStyles.button(requireContext(), SellerUiStyles.Section.PROMOTIONS, "Xem thêm", "Tải thêm sản phẩm để chọn");
            more.setOnClickListener(v -> viewModel.refreshSellerProductPage(false)); choices.addView(more);
        }
    }
    private void render() {
        pagination.render(list, promotions, this::renderPage);
        message.setText(promotions.isEmpty() ? "Chưa có ưu đãi. Chọn Tạo ưu đãi để bắt đầu." : promotions.size() + " ưu đãi đã cấu hình");
    }
    private void renderPage(List<Promotion> promotions) {
        if (list == null) return;
        list.removeAllViews();
        Date now = new Date(); SimpleDateFormat date = new SimpleDateFormat("dd/MM/yyyy", locale);
        for (Promotion promotion : promotions) {
            Product product = knownProducts.get(promotion.getProductId());
            SellerUiStyles.Row row = SellerUiStyles.row(requireContext(), SellerUiStyles.Section.PROMOTIONS);
            row.title(product == null ? "Đang tải tên sản phẩm…" : product.getName());
            row.body(!promotion.isActive() ? "Đã tắt" : promotion.isValid(now) ? "Đang áp dụng"
                    : now.before(promotion.getStartDate()) ? "Sắp bắt đầu" : "Đã hết hạn");
            row.body(promotion.getType() == PromotionType.PERCENT ? "Giảm " + promotion.getValue() + "%"
                    : promotion.getType() == PromotionType.FIXED ? "Giảm " + NumberFormat.getCurrencyInstance(locale).format(promotion.getValue()) : "Giá theo số lượng mua");
            if (!promotion.getQuantityDiscountTiers().isEmpty()) {
                List<Map.Entry<String, Double>> tiers = new ArrayList<>(promotion.getQuantityDiscountTiers().entrySet());
                tiers.sort(Comparator.comparingDouble(entry -> Double.parseDouble(entry.getKey())));
                for (Map.Entry<String, Double> tier : tiers)
                    row.body("Từ " + tier.getKey() + (product == null ? "" : " " + product.getUnit()) + ": giảm " + tier.getValue() + "%");
            }
            row.metadata(date.format(promotion.getStartDate()) + " – " + date.format(promotion.getEndDate()));
            row.action("Sửa", "Chỉnh sửa ưu đãi và giá sỉ", v -> edit(promotion.getProductId())); list.addView(row.root);
        }
    }
    private void edit(String productId) {
        Product product = knownProducts.get(productId);
        Bundle args = new Bundle(); args.putString("productId", productId);
        args.putString("productName", product == null ? "Ưu đãi sản phẩm" : product.getName());
        NavHostFragment.findNavController(this).navigate(R.id.action_sellerPromotionListFragment_to_promotionEditorFragment, args);
    }
    @Override public void onDestroyView() { list = null; chooser = null; choices = null; search = null; message = null; watching.clear(); super.onDestroyView(); }
}
