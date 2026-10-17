package com.rescuefarm.ui.cart;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
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
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.imageview.ShapeableImageView;
import com.rescuefarm.R;
import com.rescuefarm.MainActivity;
import com.rescuefarm.domain.enums.UserRole;
import com.rescuefarm.domain.model.Cart;
import com.rescuefarm.domain.model.CartItem;
import com.rescuefarm.domain.model.Product;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class CartFragment extends Fragment {
    private CartViewModel viewModel;
    private LinearLayout groups;
    private TextView summary;
    private View noticeCard;
    private TextView noticeText;
    private boolean initialRevalidation;
    private final Map<String, Product> productMap = new HashMap<>();
    private final Map<String, TextView> sellerTitles = new HashMap<>();
    private final NumberFormat money = NumberFormat.getCurrencyInstance(new Locale("vi", "VN"));

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup parent,
                             @Nullable Bundle state) {
        return inflater.inflate(R.layout.fragment_cart, parent, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        super.onViewCreated(view, state);
        if (requireActivity() instanceof MainActivity
                && ((MainActivity) requireActivity()).getResolvedActorRole() == UserRole.SELLER) {
            Navigation.findNavController(view).navigate(R.id.sellerDashboardFragment);
            return;
        }
        groups = view.findViewById(R.id.cartSellerGroups);
        summary = view.findViewById(R.id.cartSummaryText);
        noticeCard = view.findViewById(R.id.cartNoticeCard);
        noticeText = view.findViewById(R.id.cartNoticeText);

        viewModel = new ViewModelProvider(this, new CartViewModelFactory(requireContext()))
                .get(CartViewModel.class);

        viewModel.getSellerNames().observe(getViewLifecycleOwner(), names -> {
            for (Map.Entry<String, TextView> title : sellerTitles.entrySet())
                title.getValue().setText(sellerTitle(title.getKey()));
        });


        // Quan sát danh mục sản phẩm để lấy ảnh, tên, đơn vị tính
        viewModel.getProducts().observe(getViewLifecycleOwner(), products -> {
            if (products != null) {
                productMap.clear();
                for (Product p : products) {
                    if (p != null && p.getId() != null) {
                        productMap.put(p.getId(), p);
                    }
                }
                Cart currentCart = viewModel.getCart().getValue();
                if (currentCart != null) {
                    render(currentCart);
                }
            }
        });

        // Quan sát giỏ hàng
        viewModel.getCart().observe(getViewLifecycleOwner(), cart -> {
            render(cart);
            if (!initialRevalidation && cart != null && !cart.getItems().isEmpty()) {
                initialRevalidation = true;
                viewModel.revalidateCurrentCart();
            }
        });

        // Cập nhật trạng thái revalidate / cảnh báo ngầm
        viewModel.getState().observe(getViewLifecycleOwner(), value -> {
            if (value.getStatus() == CartScreenState.Status.ERROR) {
                Toast.makeText(requireContext(), value.getMessage(), Toast.LENGTH_LONG).show();
            } else if (value.getStatus() == CartScreenState.Status.REVALIDATED) {
                // Chỉ hiện banner nếu có biến động giá hoặc cảnh báo tồn kho
                if (value.getMessage() != null && !value.getMessage().contains("0 giá thay đổi, 0 item")) {
                    noticeCard.setVisibility(View.VISIBLE);
                    noticeText.setText(value.getMessage());
                } else {
                    noticeCard.setVisibility(View.GONE);
                }
            }
        });

        // Xóa toàn bộ giỏ hàng có hộp thoại xác nhận an toàn
        view.findViewById(R.id.cartClearButton).setOnClickListener(v -> {
            Cart currentCart = viewModel.getCart().getValue();
            if (currentCart == null || currentCart.getItems().isEmpty()) {
                Toast.makeText(requireContext(), "Giỏ hàng đang trống.", Toast.LENGTH_SHORT).show();
                return;
            }
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Xác nhận xóa")
                    .setMessage("Bạn có chắc chắn muốn làm trống toàn bộ giỏ hàng?")
                    .setNegativeButton("Hủy", null)
                    .setPositiveButton("Xóa tất cả", (dialog, which) -> viewModel.clear())
                    .show();
        });

        // Nút đặt hàng
        view.findViewById(R.id.cartCheckoutButton).setOnClickListener(v -> {
            Cart currentCart = viewModel.getCart().getValue();
            if (currentCart == null || currentCart.getItems().isEmpty()) {
                Toast.makeText(requireContext(), "Giỏ hàng đang trống.", Toast.LENGTH_SHORT).show();
                return;
            }
            boolean hasSelected = false;
            for (CartItem item : currentCart.getItems()) {
                if (item.isSelected()) {
                    hasSelected = true;
                    break;
                }
            }
            if (!hasSelected) {
                Toast.makeText(requireContext(), "Vui lòng chọn ít nhất 1 nông sản để thanh toán.", Toast.LENGTH_SHORT).show();
                return;
            }
            Navigation.findNavController(v).navigate(R.id.action_cartFragment_to_checkoutFragment);
        });

        // Tự động kiểm tra giá/tồn kho ngầm ngay khi mở giỏ hàng
        viewModel.refreshAndRevalidate();
    }

    private void render(Cart cart) {
        groups.removeAllViews();
        sellerTitles.clear();
        if (cart == null || cart.getItems().isEmpty()) {
            TextView empty = new TextView(requireContext());
            empty.setText(R.string.cart_empty);
            empty.setPadding(dp(16), dp(32), dp(16), dp(32));
            empty.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
            empty.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyLarge);
            empty.setTextColor(requireContext().getColor(R.color.rescue_on_surface_variant));
            groups.addView(empty);
            summary.setText(getString(R.string.cart_summary_format, 0, 0, money.format(0)));
            return;
        }

        Map<String, List<CartItem>> bySeller = new LinkedHashMap<>();
        for (CartItem item : cart.getItems()) {
            bySeller.computeIfAbsent(item.getSellerId(), ignored -> new ArrayList<>()).add(item);
        }
        viewModel.loadSellerNames(bySeller.keySet());

        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (Map.Entry<String, List<CartItem>> seller : bySeller.entrySet()) {
            groups.addView(sellerCard(inflater, seller));
        }

        int selected = 0;
        for (CartItem item : cart.getItems()) {
            if (item.isSelected()) selected++;
        }
        summary.setText(getString(R.string.cart_summary_format, selected,
                viewModel.groupSelectedBySeller(cart).size(), money.format(cart.calculateSubtotal())));
    }

    private View sellerCard(LayoutInflater inflater, Map.Entry<String, List<CartItem>> seller) {
        MaterialCardView card = new MaterialCardView(requireContext());
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(-1, -2);
        cardParams.setMargins(0, 0, 0, dp(14));
        card.setLayoutParams(cardParams);
        card.setRadius(dp(16));
        card.setCardElevation(dp(1));
        card.setStrokeColor(requireContext().getColor(R.color.rescue_outline));
        card.setStrokeWidth(dp(1));
        card.setCardBackgroundColor(requireContext().getColor(R.color.rescue_surface));

        LinearLayout body = new LinearLayout(requireContext());
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(14), dp(14), dp(14), dp(14));

        // Seller Header
        View headerView = inflater.inflate(R.layout.item_cart_seller_header, body, false);
        TextView title = headerView.findViewById(R.id.sellerHeaderTitle);
        title.setText(sellerTitle(seller.getKey()));
        sellerTitles.put(seller.getKey(), title);
        body.addView(headerView);

        // Item Rows
        for (CartItem item : seller.getValue()) {
            body.addView(itemRow(inflater, body, item));
        }

        card.addView(body);
        return card;
    }

    private View itemRow(LayoutInflater inflater, ViewGroup parent, CartItem item) {
        View row = inflater.inflate(R.layout.item_cart_product, parent, false);

        CheckBox check = row.findViewById(R.id.cartItemCheck);
        ShapeableImageView image = row.findViewById(R.id.cartItemImage);
        TextView title = row.findViewById(R.id.cartItemTitle);
        TextView batch = row.findViewById(R.id.cartItemBatch);
        TextView unitPrice = row.findViewById(R.id.cartItemUnitPrice);
        TextView total = row.findViewById(R.id.cartItemTotal);
        TextView quantity = row.findViewById(R.id.cartItemQuantity);
        ImageView btnDecrease = row.findViewById(R.id.cartItemDecrease);
        ImageView btnIncrease = row.findViewById(R.id.cartItemIncrease);
        ImageView btnDelete = row.findViewById(R.id.cartItemDelete);

        Product product = productMap.get(item.getProductId());
        String unit = (product != null && product.getUnit() != null && !product.getUnit().isEmpty())
                ? product.getUnit() : "kg";

        // Tên sản phẩm
        if (product != null && product.getName() != null && !product.getName().isEmpty()) {
            title.setText(product.getName());
        } else {
            title.setText(item.getProductId());
        }

        // Tải ảnh sản phẩm bằng Glide
        if (product != null && product.getImageUrls() != null && !product.getImageUrls().isEmpty()
                && product.getImageUrls().get(0) != null && !product.getImageUrls().get(0).trim().isEmpty()) {
            Glide.with(this)
                    .load(product.getImageUrls().get(0))
                    .placeholder(R.drawable.ic_placeholder_food)
                    .error(R.drawable.ic_placeholder_food)
                    .centerCrop()
                    .into(image);
        } else {
            image.setImageResource(R.drawable.ic_placeholder_food);
        }

        // Lô hàng
        batch.setText(String.format("Lô: %s", item.getBatchId()));

        // Đơn giá & Thành tiền
        unitPrice.setText(String.format("%s / %s", money.format(item.getUnitPrice()), unit));
        total.setText(money.format(item.calculateTotal()));

        // Checkbox chọn món
        check.setChecked(item.isSelected());
        check.setOnCheckedChangeListener((button, checked) -> viewModel.setSelected(item.getId(), checked));

        // Số lượng
        quantity.setText(String.format(Locale.US, "%.1f", item.getQuantity()));

        // Stepper nút giảm [-]
        btnDecrease.setOnClickListener(v -> viewModel.decreaseQuantity(item, 1.0));

        // Stepper nút tăng [+]
        btnIncrease.setOnClickListener(v -> viewModel.increaseQuantity(item, 1.0));

        // Nút xóa món
        btnDelete.setOnClickListener(v -> viewModel.remove(item.getId()));

        return row;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private String sellerTitle(String sellerId) {
        Map<String, String> names = viewModel.getSellerNames().getValue();
        String name = names == null ? null : names.get(sellerId);
        return name == null || name.trim().isEmpty() ? getString(R.string.cart_seller_name_unavailable)
                : getString(R.string.cart_seller_format, name);
    }

    @Override public void onDestroyView() {
        sellerTitles.clear();
        super.onDestroyView();
    }
}
