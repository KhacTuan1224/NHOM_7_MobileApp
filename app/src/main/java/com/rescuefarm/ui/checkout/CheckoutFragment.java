package com.rescuefarm.ui.checkout;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import com.rescuefarm.R;
import com.rescuefarm.domain.enums.FulfillmentType;
import com.rescuefarm.domain.enums.PaymentMethod;
import com.rescuefarm.domain.model.Address;
import com.rescuefarm.domain.model.Cart;
import com.rescuefarm.data.repository.SellerStorefront;
import com.rescuefarm.data.repository.StorefrontRepository;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.HashMap;
import java.util.Map;

public class CheckoutFragment extends Fragment {
    private CheckoutViewModel vm;
    private EditText name, phone, address, lat, lng, note;
    private Spinner fulfillment, payment, savedAddressSpinner;
    private TextView summary, message;
    private final List<Address> savedAddresses = new ArrayList<>();
    private boolean bindingAddresses;
    private boolean bindingPaymentMethods;
    private boolean bankTransferAvailable;
    private int bankLoadVersion;
    private boolean quoteReady;
    private double quotedSubtotal;
    private double quotedShippingFee;
    private Cart currentCart;
    private final Handler quoteHandler = new Handler(Looper.getMainLooper());
    private int quoteVersion;
    private final Runnable quoteRunnable = this::refreshShippingEstimate;

    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater,
            @Nullable ViewGroup parent, @Nullable Bundle state) {
        return inflater.inflate(R.layout.fragment_checkout, parent, false);
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        name = view.findViewById(R.id.checkoutName); phone = view.findViewById(R.id.checkoutPhone);
        address = view.findViewById(R.id.checkoutAddress); lat = view.findViewById(R.id.checkoutLatitude);
        lng = view.findViewById(R.id.checkoutLongitude); note = view.findViewById(R.id.checkoutNote);
        fulfillment = view.findViewById(R.id.checkoutFulfillment); payment = view.findViewById(R.id.checkoutPayment);
        savedAddressSpinner = view.findViewById(R.id.checkoutSavedAddressSpinner);
        summary = view.findViewById(R.id.checkoutSummary); message = view.findViewById(R.id.checkoutMessage);
        fulfillment.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_dropdown_item,
                FulfillmentType.values()));
        updateFulfillmentFields();
        bindPaymentMethods();
        vm = new ViewModelProvider(this, new CheckoutViewModelFactory(requireContext()))
                .get(CheckoutViewModel.class);
        ((TextView) view.findViewById(R.id.checkoutOwnerText)).setText(
                vm.isGuest() ? R.string.guest_checkout_notice : R.string.customer_checkout_notice);
        if (!vm.isGuest()) {
            savedAddressSpinner.setVisibility(View.VISIBLE);
            vm.observeAddresses().observe(getViewLifecycleOwner(), this::renderAddresses);
        }
        savedAddressSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View row, int position, long id) {
                if (!bindingAddresses && position >= 0 && position < savedAddresses.size())
                    applyAddress(savedAddresses.get(position));
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        });
        fulfillment.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View row, int position, long id) {
                updateFulfillmentFields(); refreshPaymentMethods(); scheduleShippingEstimate();
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        });
        payment.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View row, int position, long id) {
                if (!bindingPaymentMethods) scheduleShippingEstimate();
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        });
        TextWatcher locationWatcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { scheduleShippingEstimate(); }
            @Override public void afterTextChanged(Editable s) { }
        };
        lat.addTextChangedListener(locationWatcher); lng.addTextChangedListener(locationWatcher);
        vm.getCart().observe(getViewLifecycleOwner(), cart -> {
            renderCart(cart); refreshPaymentMethods();
        });
        vm.getState().observe(getViewLifecycleOwner(), result -> {
            message.setText(result.getMessage());
            view.findViewById(R.id.submitCheckoutButton).setEnabled(
                    result.getStatus() != CheckoutState.Status.LOADING);
            if (result.getStatus() == CheckoutState.Status.SUCCESS && vm.consumeCheckoutSuccessNavigation()) {
                Bundle args = new Bundle(); args.putString("orderId", result.getOrder().getId());
                args.putString("orderCode", result.getOrder().getOrderCode());
                args.putString("phone", result.getOrder().getReceiverPhone());
                args.putString("status", result.getOrder().getStatus().name());
                Navigation.findNavController(view).navigate(R.id.action_checkoutFragment_to_orderConfirmationFragment, args);
            } else if (result.getStatus() == CheckoutState.Status.ERROR) {
                Toast.makeText(requireContext(), result.getMessage(), Toast.LENGTH_LONG).show();
                if (result.getMessage() != null && result.getMessage().contains("QUOTE_CHANGED"))
                    scheduleShippingEstimate();
            }
        });
        View submit = view.findViewById(R.id.submitCheckoutButton);
        submit.setOnClickListener(unused -> {
            if (!vm.isGuest() && !quoteReady) {
                message.setText("Đang chờ báo giá mới nhất từ máy chủ. Hãy kiểm tra kết nối rồi thử lại.");
                scheduleShippingEstimate(); return;
            }
            vm.submit(text(name), text(phone), text(address), text(lat), text(lng),
                    (FulfillmentType) fulfillment.getSelectedItem(), (PaymentMethod) payment.getSelectedItem(),
                    text(note), quoteReady ? quotedSubtotal : Double.NaN,
                    quoteReady ? quotedShippingFee : Double.NaN);
        });
        if (vm.isGuest()) {
            submit.setEnabled(false);
            message.setText("Checkout cần tài khoản Customer để máy chủ xác minh và giữ tồn kho.");
        }
        refreshPaymentMethods();
    }

    private void renderAddresses(List<Address> values) {
        savedAddresses.clear(); if (values != null) savedAddresses.addAll(values);
        List<String> labels = new ArrayList<>();
        for (Address saved : savedAddresses) labels.add((saved.isDefault() ? "Mặc định · " : "")
                + saved.getReceiverName() + " · " + saved.getFormattedAddress());
        bindingAddresses = true;
        savedAddressSpinner.setAdapter(new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_dropdown_item, labels));
        if (!savedAddresses.isEmpty()) {
            int selected = 0;
            for (int i = 0; i < savedAddresses.size(); i++) if (savedAddresses.get(i).isDefault()) { selected = i; break; }
            savedAddressSpinner.setSelection(selected); applyAddress(savedAddresses.get(selected));
        }
        bindingAddresses = false;
    }

    private void applyAddress(Address saved) {
        name.setText(saved.getReceiverName()); phone.setText(saved.getReceiverPhone());
        address.setText(saved.getFormattedAddress()); lat.setText(String.valueOf(saved.getLatitude()));
        lng.setText(String.valueOf(saved.getLongitude()));
        scheduleShippingEstimate();
    }

    private void renderCart(Cart cart) {
        if (cart == null) return;
        currentCart = cart;
        int count = 0; Set<String> sellers = new HashSet<>();
        for (com.rescuefarm.domain.model.CartItem item : cart.getItems()) if (item.isSelected()) {
            count++; sellers.add(item.getSellerId());
        }
        summary.setText(getString(R.string.checkout_summary_format, count, sellers.size(),
                NumberFormat.getCurrencyInstance(new Locale("vi", "VN")).format(cart.calculateSubtotal())));
        scheduleShippingEstimate();
    }

    private void scheduleShippingEstimate() {
        quoteReady = false;
        quoteVersion++;
        View submit = getView() == null ? null : getView().findViewById(R.id.submitCheckoutButton);
        if (submit != null && !vm.isGuest()) submit.setEnabled(false);
        quoteHandler.removeCallbacks(quoteRunnable); quoteHandler.postDelayed(quoteRunnable, 400L);
    }

    private void refreshShippingEstimate() {
        TextView estimate = getView() == null ? null : getView().findViewById(R.id.checkoutShippingEstimate);
        if (estimate == null) return;
        int version = quoteVersion;
        if (vm.isGuest()) { estimate.setText("Checkout chỉ dành cho Customer đăng nhập."); return; }
        if (currentCart == null) { estimate.setText("Đang tải giỏ hàng…"); return; }
        double subtotal = 0D; int count = 0; String sellerId = "";
        StringBuilder lines = new StringBuilder();
        NumberFormat money = NumberFormat.getCurrencyInstance(new Locale("vi", "VN"));
        for (com.rescuefarm.domain.model.CartItem item : currentCart.getItems()) if (item.isSelected()) {
            if (!sellerId.isEmpty() && !sellerId.equals(item.getSellerId())) {
                estimate.setText("Mỗi đơn chỉ gồm sản phẩm của một cửa hàng. Hãy tách giỏ theo Seller."); return;
            }
            sellerId = item.getSellerId(); count++;
            double line = Math.round(item.calculateTotal()); subtotal += line;
            lines.append("• Batch ").append(item.getBatchId()).append(" · ")
                    .append(item.getQuantity()).append(" × ").append(money.format(item.getUnitPrice()))
                    .append(" = ").append(money.format(line)).append('\n');
        }
        if (count == 0) { estimate.setText("Chọn mặt hàng để xem tạm tính."); return; }
        final double itemSubtotal = subtotal;
        quoteReady = true; quotedSubtotal = itemSubtotal;
        estimate.setText("ĐANG TẠM TÍNH — transaction checkout sẽ xác minh lại giá và tồn.\n" + lines);
        if (fulfillment.getSelectedItem() != FulfillmentType.DELIVERY) {
            quotedShippingFee = 0D;
            estimate.setText("ĐANG TẠM TÍNH — transaction checkout sẽ xác minh lại giá và tồn.\n" + lines
                    + "Tiền hàng: " + money.format(itemSubtotal) + "\nPhí giao: " + money.format(0D)
                    + "\nTổng tạm tính: " + money.format(itemSubtotal));
            enableCheckoutSubmit();
            return;
        }
        final int requestVersion = version; final String shopId = sellerId;
        quoteReady = false;
        vm.loadStorefront(shopId, new StorefrontRepository.Callback() {
            public void success(SellerStorefront profile) {
                if (requestVersion != quoteVersion || getView() == null) return;
                if (!profile.shippingConfigured) { estimate.setText("Người bán chưa cấu hình phí giao hàng cố định."); return; }
                quotedShippingFee = profile.shippingFee; quoteReady = true;
                estimate.setText(lines + "Tiền hàng: " + money.format(itemSubtotal)
                    + "\nPhí giao cố định: " + money.format(quotedShippingFee)
                    + "\nTổng tạm tính: " + money.format(itemSubtotal + quotedShippingFee));
                enableCheckoutSubmit();
            }
            public void error(String message) {
                if (requestVersion != quoteVersion || getView() == null) return;
                quoteReady = false; estimate.setText(message);
            }
        });
    }

    private void enableCheckoutSubmit() {
        View submit = getView() == null ? null : getView().findViewById(R.id.submitCheckoutButton);
        if (submit != null && quoteReady) submit.setEnabled(true);
    }

    private void updateFulfillmentFields() {
        if (fulfillment == null) return;
        boolean delivery = fulfillment.getSelectedItem() == FulfillmentType.DELIVERY;
        if (address != null) address.setVisibility(delivery ? View.VISIBLE : View.GONE);
        if (lat != null) lat.setVisibility(delivery ? View.VISIBLE : View.GONE);
        if (lng != null) lng.setVisibility(delivery ? View.VISIBLE : View.GONE);
    }

    private void bindPaymentMethods() {
        payment.setAdapter(new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_dropdown_item, new PaymentMethod[] {PaymentMethod.CASH_ON_SITE}));
    }

    private void refreshPaymentMethods() {
        if (payment == null || fulfillment == null || !isAdded()) return;
        String sellerId = "";
        if (currentCart != null) for (com.rescuefarm.domain.model.CartItem item : currentCart.getItems())
            if (item.isSelected()) { sellerId = item.getSellerId(); break; }
        final String selectedSeller = sellerId;
        int version = ++bankLoadVersion;
        bankTransferAvailable = false;
        applyPaymentMethods();
        if (selectedSeller.isEmpty()) return;
        vm.loadStorefront(selectedSeller, new StorefrontRepository.Callback() {
            public void success(SellerStorefront profile) {
                if (version != bankLoadVersion || getView() == null) return;
                bankTransferAvailable = profile.acceptsTransfer(); applyPaymentMethods();
            }
            public void error(String message) {
                if (version != bankLoadVersion || getView() == null) return;
                bankTransferAvailable = false; applyPaymentMethods();
            }
        });
    }

    private void applyPaymentMethods() {
        PaymentMethod previous = payment.getSelectedItem() instanceof PaymentMethod
                ? (PaymentMethod) payment.getSelectedItem() : null;
        List<PaymentMethod> allowed = new ArrayList<>();
        if (fulfillment.getSelectedItem() == FulfillmentType.DELIVERY) allowed.add(PaymentMethod.COD);
        else allowed.add(PaymentMethod.CASH_ON_SITE);
        if (bankTransferAvailable) allowed.add(PaymentMethod.BANK_TRANSFER);
        bindingPaymentMethods = true;
        payment.setAdapter(new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_dropdown_item, allowed));
        int selected = previous == null ? -1 : allowed.indexOf(previous);
        payment.setSelection(selected < 0 ? 0 : selected);
        bindingPaymentMethods = false;
    }

    private boolean blank(String value) { return value == null || value.trim().isEmpty(); }

    private double number(Object value) { return value instanceof Number ? ((Number) value).doubleValue() : 0D; }
    private String text(EditText field) { return field.getText().toString().trim(); }
    @Override public void onDestroyView() { quoteHandler.removeCallbacks(quoteRunnable); quoteVersion++; super.onDestroyView(); }
}
