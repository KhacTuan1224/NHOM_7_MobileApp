package com.rescuefarm.ui.order;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import com.google.android.material.button.MaterialButton;
import com.rescuefarm.R;
import com.rescuefarm.data.repository.AuthRepository;
import com.rescuefarm.data.repository.AuthRepositoryFactory;
import com.rescuefarm.data.repository.OrderRepository;
import com.rescuefarm.domain.model.Order;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

public class CustomerOrderListFragment extends Fragment {
    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater,
            @Nullable ViewGroup parent, @Nullable Bundle state) {
        return inflater.inflate(R.layout.fragment_customer_order_list, parent, false);
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle savedState) {
        AuthRepository auth = AuthRepositoryFactory.create(requireContext());
        String ownerId = auth.isAuthenticated() ? auth.getCurrentUserId() : "";
        boolean signedIn = ownerId != null && !ownerId.trim().isEmpty();
        TextView empty = view.findViewById(R.id.customerOrdersEmpty);
        TextView message = view.findViewById(R.id.customerOrdersMessage);
        View refresh = view.findViewById(R.id.refreshCustomerOrdersButton);
        View login = view.findViewById(R.id.customerOrdersLoginButton);
        View guestLookupCard = view.findViewById(R.id.guestLookupCard);
        EditText codeInput = view.findViewById(R.id.lookupOrderCodeInput);
        EditText phoneInput = view.findViewById(R.id.lookupReceiverPhoneInput);
        View lookupButton = view.findViewById(R.id.lookupOrderSubmitButton);

        OrderViewModel vm = new ViewModelProvider(this,
                new OrderViewModelFactory(requireContext())).get(OrderViewModel.class);

        login.setVisibility(signedIn ? View.GONE : View.VISIBLE);
        guestLookupCard.setVisibility(signedIn ? View.GONE : View.VISIBLE);
        refresh.setVisibility(signedIn ? View.VISIBLE : View.GONE);
        login.setOnClickListener(v -> Navigation.findNavController(view).navigate(R.id.loginFragment));

        lookupButton.setOnClickListener(v -> {
            String code = codeInput.getText() == null ? "" : codeInput.getText().toString().trim();
            String phone = phoneInput.getText() == null ? "" : phoneInput.getText().toString().trim();
            if (code.isEmpty() || phone.isEmpty()) {
                message.setVisibility(View.VISIBLE);
                message.setText("Vui lòng nhập đầy đủ mã đơn và số điện thoại.");
                return;
            }
            message.setVisibility(View.GONE);
            lookupButton.setEnabled(false);
            vm.findGuestOrder(code, phone, new OrderRepository.OrderCallback() {
                @Override
                public void onSuccess(Order order, boolean idempotentReplay) {
                    lookupButton.setEnabled(true);
                    Bundle args = new Bundle();
                    args.putString("orderId", order.getId());
                    Navigation.findNavController(view).navigate(
                            R.id.action_customerOrderListFragment_to_orderDetailFragment, args);
                }

                @Override
                public void onError(OrderRepository.ErrorCode error, String errorMsg) {
                    lookupButton.setEnabled(true);
                    message.setVisibility(View.VISIBLE);
                    message.setText(errorMsg);
                }
            });
        });

        if (!signedIn) {
            empty.setText(R.string.customer_orders_login_required);
            return;
        }
        vm.observeCustomerOrders(ownerId).observe(getViewLifecycleOwner(), orders -> {
            renderOrders(view, orders);
            empty.setVisibility(orders == null || orders.isEmpty() ? View.VISIBLE : View.GONE);
        });
        vm.getState().observe(getViewLifecycleOwner(), state -> {
            boolean loading = state.getStatus() == OrderLifecycleState.Status.LOADING;
            view.findViewById(R.id.customerOrdersProgress).setVisibility(loading ? View.VISIBLE : View.GONE);
            refresh.setEnabled(!loading);
            message.setText(state.getMessage());
            message.setVisibility(state.getMessage().isEmpty() ? View.GONE : View.VISIBLE);
        });
        refresh.setOnClickListener(v -> vm.refreshCustomerOrders(ownerId));
        vm.refreshCustomerOrders(ownerId);
    }

    private void renderOrders(View view, List<Order> orders) {
        LinearLayout container = view.findViewById(R.id.customerOrdersContainer);
        container.removeAllViews();
        if (orders == null) return;
        NumberFormat money = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("vi-VN"));
        String[] statuses = getResources().getStringArray(R.array.customer_order_status_labels);
        for (Order order : orders) {
            MaterialButton row = new MaterialButton(requireContext());
            row.setAllCaps(false);
            row.setGravity(android.view.Gravity.START | android.view.Gravity.CENTER_VERTICAL);
            row.setText(getString(R.string.customer_order_row_format, order.getOrderCode(),
                    statuses[order.getStatus().ordinal()], money.format(order.getTotalAmount())));
            row.setOnClickListener(v -> {
                Bundle args = new Bundle();
                args.putString("orderId", order.getId());
                Navigation.findNavController(view).navigate(
                        R.id.action_customerOrderListFragment_to_orderDetailFragment, args);
            });
            container.addView(row, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
    }
}
