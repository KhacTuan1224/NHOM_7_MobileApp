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
import com.google.firebase.firestore.FirebaseFirestore;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class CheckoutFragment extends Fragment {
    private CheckoutViewModel vm;
    private EditText name, phone, address, lat, lng, note;
    private Spinner fulfillment, payment, savedAddressSpinner;
    private TextView summary, message;
    private final List<Address> savedAddresses = new ArrayList<>();
    private boolean bindingAddresses;
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
        payment.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_dropdown_item,
                PaymentMethod.values()));
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
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View row, int position, long id) { scheduleShippingEstimate(); }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        });
        TextWatcher locationWatcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { scheduleShippingEstimate(); }
            @Override public void afterTextChanged(Editable s) { }
        };
        lat.addTextChangedListener(locationWatcher); lng.addTextChangedListener(locationWatcher);
        vm.getCart().observe(getViewLifecycleOwner(), this::renderCart);
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
            }
        });
        view.findViewById(R.id.submitCheckoutButton).setOnClickListener(unused -> vm.submit(
                text(name), text(phone), text(address), text(lat), text(lng),
                (FulfillmentType) fulfillment.getSelectedItem(), (PaymentMethod) payment.getSelectedItem(), text(note)));
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
        quoteHandler.removeCallbacks(quoteRunnable); quoteHandler.postDelayed(quoteRunnable, 400L);
    }

    private void refreshShippingEstimate() {
        TextView estimate = getView() == null ? null : getView().findViewById(R.id.checkoutShippingEstimate);
        if (estimate == null) return;
        int version = ++quoteVersion;
        if (fulfillment.getSelectedItem() != FulfillmentType.DELIVERY) {
            estimate.setText("Phí giao hàng: 0 ₫ (nhận tại chỗ)."); return;
        }
        String sellerId = "";
        if (currentCart != null) for (com.rescuefarm.domain.model.CartItem item : currentCart.getItems())
            if (item.isSelected()) { sellerId = item.getSellerId(); break; }
        if (sellerId.isEmpty()) { estimate.setText("Chọn sản phẩm để tính phí giao hàng."); return; }
        double customerLat, customerLng;
        try { customerLat = Double.parseDouble(text(lat)); customerLng = Double.parseDouble(text(lng)); }
        catch (NumberFormatException error) { estimate.setText("Chọn địa chỉ có tọa độ để xem phí giao hàng."); return; }
        final String selectedSeller = sellerId;
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("sellerPublicProfiles").document(selectedSeller).get().addOnSuccessListener(seller -> {
            if (version != quoteVersion || !isAdded() || !seller.exists()) return;
            db.collection("appConfig").document("shipping").get().addOnSuccessListener(config -> {
                if (version != quoteVersion || !isAdded()) return;
                if (!config.exists()) { estimate.setText("Chưa cấu hình biểu phí giao hàng."); return; }
                double sellerLat = number(seller.get("latitude")), sellerLng = number(seller.get("longitude"));
                if (sellerLat == 0D && sellerLng == 0D) { estimate.setText("Seller chưa cập nhật tọa độ cửa hàng."); return; }
                double distance = distanceKm(sellerLat, sellerLng, customerLat, customerLng);
                double base = number(config.get("baseFeeVnd")), perKm = number(config.get("perKmFeeVnd"));
                double freeKm = number(config.get("freeDistanceKm"));
                long fee = Math.round(base + Math.ceil(Math.max(0D, distance - freeKm) * perKm));
                String feeText = NumberFormat.getCurrencyInstance(new Locale("vi", "VN")).format(fee);
                String totalText = NumberFormat.getCurrencyInstance(new Locale("vi", "VN"))
                        .format((currentCart == null ? 0D : currentCart.calculateSubtotal()) + fee);
                estimate.setText(String.format(Locale.getDefault(),
                        "Khoảng cách đường chim bay %.1f km · Phí ước tính %s · Tổng tạm tính %s (trước khuyến mãi server)",
                        distance, feeText, totalText));
            }).addOnFailureListener(error -> { if (version == quoteVersion && isAdded()) estimate.setText("Không tải được biểu phí giao hàng."); });
        }).addOnFailureListener(error -> { if (version == quoteVersion && isAdded()) estimate.setText("Không tải được tọa độ seller."); });
    }

    private double number(Object value) { return value instanceof Number ? ((Number) value).doubleValue() : 0D; }
    private double distanceKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1), dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2D) * Math.sin(dLat / 2D)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2D) * Math.sin(dLon / 2D);
        return 6371D * 2D * Math.atan2(Math.sqrt(a), Math.sqrt(1D - a));
    }

    private String text(EditText field) { return field.getText().toString().trim(); }
    @Override public void onDestroyView() { quoteHandler.removeCallbacks(quoteRunnable); quoteVersion++; super.onDestroyView(); }
}
