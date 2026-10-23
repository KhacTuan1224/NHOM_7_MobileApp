package com.rescuefarm.ui.checkout;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import com.rescuefarm.R;
import com.rescuefarm.data.local.preferences.SharedPreferencesGuestSessionRepository;
import com.rescuefarm.domain.enums.OrderStatus;
import com.rescuefarm.domain.enums.PaymentMethod;
import com.rescuefarm.domain.model.Order;
import com.rescuefarm.domain.model.RecentGuestOrder;
import com.rescuefarm.ui.common.EnumLabels;
import com.rescuefarm.ui.common.OrderLabels;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

public class OrderConfirmationFragment extends Fragment {
    private OrderViewModel viewModel;
    private TextView output;
    private EditText code, phone;
    private View lookupButton, detailButton;
    private View recentOrdersContainer;
    private ViewGroup recentOrdersList;
    private SharedPreferencesGuestSessionRepository sessionRepo;
    private String orderId = "";
    private String pendingAutoOpenOrderId = null;
    private boolean binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup parent,
                             @Nullable Bundle state) {
        return inflater.inflate(R.layout.fragment_order_confirmation, parent, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        output = view.findViewById(R.id.orderConfirmationText);
        code = view.findViewById(R.id.trackingOrderCode);
        phone = view.findViewById(R.id.trackingPhone);
        lookupButton = view.findViewById(R.id.trackOrderButton);
        detailButton = view.findViewById(R.id.openOrderDetailButton);
        recentOrdersContainer = view.findViewById(R.id.recentGuestOrdersContainer);
        recentOrdersList = view.findViewById(R.id.recentGuestOrdersList);

        sessionRepo = new SharedPreferencesGuestSessionRepository(requireContext());

        Bundle args = getArguments();
        if (args != null && args.getString("orderId") != null && !args.getString("orderId").isEmpty()) {
            orderId = args.getString("orderId", "");
            String orderCode = args.getString("orderCode", "");
            String receiverPhone = args.getString("phone", "");
            String statusStr = args.getString("status", "PENDING");
            String methodStr = args.getString("paymentMethod", "COD");
            double totalAmount = args.getDouble("totalAmount", 0.0);

            binding = true;
            code.setText(orderCode);
            phone.setText(receiverPhone);
            binding = false;

            OrderStatus orderStatus = parseStatus(statusStr);
            PaymentMethod paymentMethod = parsePaymentMethod(methodStr);

            output.setText(getString(R.string.order_created_format,
                    orderCode,
                    OrderLabels.status(orderStatus, paymentMethod)));

            java.util.ArrayList<String> ids = args.getStringArrayList("checkoutOrderIds");
            java.util.ArrayList<String> codes = args.getStringArrayList("checkoutOrderCodes");
            if (ids != null && codes != null && ids.size() > 1) {
                output.append("\nĐã tạo " + ids.size() + " đơn cho các cửa hàng. Mỗi đơn được thanh toán và theo dõi riêng.");
                android.widget.LinearLayout links = new android.widget.LinearLayout(requireContext());
                links.setOrientation(android.widget.LinearLayout.VERTICAL);
                ViewGroup parent = (ViewGroup) output.getParent(); parent.addView(links, parent.indexOfChild(output) + 1);
                for (int i = 0; i < Math.min(ids.size(), codes.size()); i++) {
                    final String childId = ids.get(i);
                    com.google.android.material.button.MaterialButton button = new com.google.android.material.button.MaterialButton(requireContext());
                    button.setText("Xem đơn " + (i + 1) + " · " + codes.get(i)); button.setAllCaps(false); links.addView(button);
                    button.setOnClickListener(v -> { Bundle detail = new Bundle(); detail.putString("orderId", childId);
                        Navigation.findNavController(v).navigate(R.id.orderDetailFragment, detail); });
                }
            }
            sessionRepo.saveRecentGuestOrder(orderId, orderCode, receiverPhone,
                    totalAmount, statusStr, methodStr);
        } else {
            // Khi mở từ tab "Đơn hàng" (Guest): Tự động điền đơn hàng gần nhất nếu có
            String lastCode = sessionRepo.getLastGuestOrderCode();
            String lastPhone = sessionRepo.getLastGuestPhone();
            if (!lastCode.isEmpty() && !lastPhone.isEmpty()) {
                binding = true;
                code.setText(lastCode);
                phone.setText(lastPhone);
                binding = false;
            }
        }

        ((TextView) view.findViewById(R.id.orderConfirmationTitle)).setText(orderId.isEmpty()
                ? "Tra cứu đơn Guest" : getString(R.string.order_confirmation_title));
        detailButton.setEnabled(!orderId.isEmpty());

        renderRecentOrders();

        TextWatcher inputsChanged = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence value, int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence value, int start, int before, int count) {
                if (!binding) {
                    orderId = "";
                    detailButton.setEnabled(false);
                }
            }

            @Override
            public void afterTextChanged(Editable value) { }
        };
        code.addTextChangedListener(inputsChanged);
        phone.addTextChangedListener(inputsChanged);

        viewModel = new ViewModelProvider(this, new OrderViewModelFactory(requireContext()))
                .get(OrderViewModel.class);
        viewModel.getState().observe(getViewLifecycleOwner(), result -> {
            boolean busy = result.getStatus() == CheckoutState.Status.LOADING;
            lookupButton.setEnabled(!busy);
            code.setEnabled(!busy);
            phone.setEnabled(!busy);
            if (result.getStatus() == CheckoutState.Status.SUCCESS) {
                Order successOrder = result.getOrder();
                render(successOrder);
                if (pendingAutoOpenOrderId != null) {
                    if (pendingAutoOpenOrderId.equals(successOrder.getId())
                            || pendingAutoOpenOrderId.equalsIgnoreCase(successOrder.getOrderCode())) {
                        pendingAutoOpenOrderId = null;
                        openOrderDetail(successOrder.getId());
                    }
                }
            } else if (result.getStatus() == CheckoutState.Status.ERROR) {
                pendingAutoOpenOrderId = null;
                output.setText(result.getMessage());
                Toast.makeText(requireContext(), result.getMessage(), Toast.LENGTH_LONG).show();
            } else if (busy) {
                output.setText("Đang tra cứu đơn hàng…");
            }
            detailButton.setEnabled(!busy && !orderId.isEmpty());
        });

        lookupButton.setOnClickListener(unused -> {
            orderId = "";
            pendingAutoOpenOrderId = null;
            viewModel.findGuest(code.getText().toString().trim(), phone.getText().toString().trim());
        });

        detailButton.setOnClickListener(unused -> openOrderDetail(orderId));
    }

    private void openOrderDetail(String targetOrderId) {
        if (targetOrderId == null || targetOrderId.trim().isEmpty()) {
            Toast.makeText(requireContext(), R.string.track_before_open_detail, Toast.LENGTH_SHORT).show();
            return;
        }
        Bundle detailArgs = new Bundle();
        detailArgs.putString("orderId", targetOrderId.trim());
        Navigation.findNavController(requireView()).navigate(
                R.id.action_orderConfirmationFragment_to_orderDetailFragment, detailArgs);
    }

    private void renderRecentOrders() {
        if (recentOrdersContainer == null || recentOrdersList == null || !isAdded()) return;
        List<RecentGuestOrder> recents = sessionRepo.getRecentGuestOrders();
        if (recents == null || recents.isEmpty()) {
            recentOrdersContainer.setVisibility(View.GONE);
            recentOrdersList.removeAllViews();
            return;
        }

        recentOrdersContainer.setVisibility(View.VISIBLE);
        recentOrdersList.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        NumberFormat moneyFormat = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("vi-VN"));

        for (RecentGuestOrder item : recents) {
            View cardView = inflater.inflate(R.layout.item_recent_guest_order, recentOrdersList, false);

            TextView codeView = cardView.findViewById(R.id.recentOrderCode);
            TextView statusView = cardView.findViewById(R.id.recentOrderStatus);
            TextView phoneView = cardView.findViewById(R.id.recentOrderPhone);
            TextView amountView = cardView.findViewById(R.id.recentOrderAmount);

            codeView.setText(item.getOrderCode());

            OrderStatus statusEnum = parseStatus(item.getStatus());
            PaymentMethod methodEnum = parsePaymentMethod(item.getPaymentMethod());
            statusView.setText(OrderLabels.status(statusEnum, methodEnum));

            if (!item.getPhone().isEmpty()) {
                phoneView.setText(getString(R.string.recent_guest_order_phone_format, item.getPhone()));
                phoneView.setVisibility(View.VISIBLE);
            } else {
                phoneView.setVisibility(View.GONE);
            }

            if (item.getTotalAmount() > 0.0) {
                amountView.setText(moneyFormat.format(item.getTotalAmount()));
                amountView.setVisibility(View.VISIBLE);
            } else {
                amountView.setVisibility(View.GONE);
            }

            cardView.setOnClickListener(v -> onRecentOrderClicked(item));
            recentOrdersList.addView(cardView);
        }
    }

    private void onRecentOrderClicked(RecentGuestOrder item) {
        binding = true;
        code.setText(item.getOrderCode());
        phone.setText(item.getPhone());
        binding = false;

        String targetOrderId = item.getOrderId();
        // Nếu đã có accessToken trên thiết bị, mở ngay chi tiết đơn không cần chờ tra cứu
        if (!targetOrderId.isEmpty() && !sessionRepo.getOrderAccessToken(targetOrderId).isEmpty()) {
            orderId = targetOrderId;
            detailButton.setEnabled(true);
            openOrderDetail(targetOrderId);
            return;
        }

        // Nếu chưa có token hoặc chưa có id, tự động tra cứu và mở ngay khi có kết quả
        pendingAutoOpenOrderId = !targetOrderId.isEmpty() ? targetOrderId : item.getOrderCode();
        orderId = targetOrderId;
        viewModel.findGuest(item.getOrderCode(), item.getPhone());
    }

    private void render(Order order) {
        binding = true;
        code.setText(order.getOrderCode());
        phone.setText(order.getReceiverPhone());
        binding = false;
        orderId = order.getId();
        output.setText(getString(R.string.order_tracking_format,
                order.getOrderCode(),
                OrderLabels.status(order.getStatus(), order.getPaymentMethod()),
                EnumLabels.label(order.getPaymentStatus()),
                order.getTotalAmount()));
        sessionRepo.saveRecentGuestOrder(order.getId(), order.getOrderCode(), order.getReceiverPhone(),
                order.getTotalAmount(),
                order.getStatus() == null ? "PENDING" : order.getStatus().name(),
                order.getPaymentMethod() == null ? "COD" : order.getPaymentMethod().name());
        renderRecentOrders();
    }

    private OrderStatus parseStatus(String name) {
        try {
            return OrderStatus.valueOf(name);
        } catch (Exception e) {
            return OrderStatus.PENDING;
        }
    }

    private PaymentMethod parsePaymentMethod(String name) {
        try {
            return PaymentMethod.valueOf(name);
        } catch (Exception e) {
            return PaymentMethod.COD;
        }
    }
}
