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
import com.rescuefarm.ui.common.EnumLabels;
import com.rescuefarm.ui.common.OrderLabels;

public class OrderConfirmationFragment extends Fragment {
    private OrderViewModel viewModel;
    private TextView output;
    private EditText code, phone;
    private View lookupButton, detailButton;
    private String orderId = "";
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

        SharedPreferencesGuestSessionRepository sessionRepo =
                new SharedPreferencesGuestSessionRepository(requireContext());

        Bundle args = getArguments();
        if (args != null && args.getString("orderId") != null && !args.getString("orderId").isEmpty()) {
            orderId = args.getString("orderId", "");
            binding = true;
            code.setText(args.getString("orderCode", ""));
            phone.setText(args.getString("phone", ""));
            binding = false;

            String statusStr = args.getString("status", "PENDING");
            String methodStr = args.getString("paymentMethod", "COD");
            OrderStatus orderStatus = parseStatus(statusStr);
            PaymentMethod paymentMethod = parsePaymentMethod(methodStr);

            output.setText(getString(R.string.order_created_format,
                    args.getString("orderCode", ""),
                    OrderLabels.status(orderStatus, paymentMethod)));
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
                render(result.getOrder());
            } else if (result.getStatus() == CheckoutState.Status.ERROR) {
                output.setText(result.getMessage());
                Toast.makeText(requireContext(), result.getMessage(), Toast.LENGTH_LONG).show();
            } else if (busy) {
                output.setText("Đang tra cứu đơn hàng…");
            }
            detailButton.setEnabled(!busy && !orderId.isEmpty());
        });

        lookupButton.setOnClickListener(unused -> {
            orderId = "";
            viewModel.findGuest(code.getText().toString().trim(), phone.getText().toString().trim());
        });

        detailButton.setOnClickListener(unused -> {
            if (orderId.isEmpty()) {
                Toast.makeText(requireContext(), R.string.track_before_open_detail, Toast.LENGTH_SHORT).show();
                return;
            }
            Bundle detailArgs = new Bundle();
            detailArgs.putString("orderId", orderId);
            Navigation.findNavController(view).navigate(
                    R.id.action_orderConfirmationFragment_to_orderDetailFragment, detailArgs);
        });
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
