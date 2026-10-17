package com.rescuefarm.ui.checkout;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.Manifest;
import android.content.pm.PackageManager;
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
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import com.rescuefarm.R;
import com.rescuefarm.MainActivity;
import com.rescuefarm.domain.enums.UserRole;
import com.rescuefarm.data.repository.SellerStorefront;
import com.rescuefarm.data.repository.StorefrontRepository;
import com.rescuefarm.domain.enums.FulfillmentType;
import com.rescuefarm.domain.enums.PaymentMethod;
import com.rescuefarm.domain.model.Address;
import com.rescuefarm.domain.model.Cart;
import com.rescuefarm.ui.common.VietnamAddressView;
import com.rescuefarm.ui.profile.ProfileViewModel;
import com.rescuefarm.ui.profile.ProfileViewModelFactory;
import com.rescuefarm.ui.profile.ProfileScreenState;
import com.rescuefarm.service.backend.RenderBackendClient;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class CheckoutFragment extends Fragment {
    private CheckoutViewModel vm;
    private EditText name, phone, note;
    private double receiverLatitude, receiverLongitude;
    private TextView gpsStatus, sellerAddress;
    private VietnamAddressView address;
    private ProfileViewModel locationModel;
    private TextView summary, message;
    private String selectedAddressId;
    private boolean addressEdited, restoringAddress, bindingAddressDetails;
    private int locationRequestVersion = -1, destinationVersion;
    private final ActivityResultLauncher<String[]> locationPermission = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                if (Boolean.TRUE.equals(result.get(Manifest.permission.ACCESS_FINE_LOCATION))
                        || Boolean.TRUE.equals(result.get(Manifest.permission.ACCESS_COARSE_LOCATION))) takeLocation();
                else if (message != null) message.setText("Cần cấp quyền vị trí để lấy GPS nơi nhận, hoặc chọn địa chỉ đã lưu có GPS.");
            });
    private Spinner fulfillment, payment, savedAddressSpinner;
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
        if (requireActivity() instanceof MainActivity
                && ((MainActivity) requireActivity()).getResolvedActorRole() == UserRole.SELLER) {
            Navigation.findNavController(view).navigate(R.id.sellerDashboardFragment);
            return;
        }
        name = view.findViewById(R.id.checkoutName); phone = view.findViewById(R.id.checkoutPhone);
        address = view.findViewById(R.id.checkoutAddress); note = view.findViewById(R.id.checkoutNote);
        gpsStatus = view.findViewById(R.id.checkoutGpsStatus);
        sellerAddress = view.findViewById(R.id.checkoutSellerAddress);
        if (state != null) {
            receiverLatitude = state.getDouble("receiverLatitude");
            receiverLongitude = state.getDouble("receiverLongitude");
        }
        showReceiverGps();
        fulfillment = view.findViewById(R.id.checkoutFulfillment); payment = view.findViewById(R.id.checkoutPayment);
        savedAddressSpinner = view.findViewById(R.id.checkoutSavedAddressSpinner);
        summary = view.findViewById(R.id.checkoutSummary); message = view.findViewById(R.id.checkoutMessage);
        if (state != null) selectedAddressId = state.getString("selectedAddressId", "");
        restoringAddress = state != null || addressEdited;
        addressEdited = addressEdited || state != null;
        locationModel = new ViewModelProvider(this, new ProfileViewModelFactory(requireContext()))
                .get(ProfileViewModel.class);
        view.findViewById(R.id.checkoutUseLocation).setOnClickListener(v -> requestLocation());
        locationModel.getState().observe(getViewLifecycleOwner(), value -> {
            if (value.getStatus() == ProfileScreenState.Status.LOCATION) {
                if (locationRequestVersion != destinationVersion) { locationModel.clearTransientState(); return; }
                bindingAddressDetails = true;
                if (value.getGeocodedAddress() != null) address.applyGeocodedAddress(value.getGeocodedAddress());
                receiverLatitude = value.getLatitude(); receiverLongitude = value.getLongitude();
                bindingAddressDetails = false;
                addressEdited = true;
                selectedAddressId = "";
                bindingAddresses = true; savedAddressSpinner.setSelection(0); bindingAddresses = false;
                showReceiverGps(); scheduleShippingEstimate();
                message.setText("Đã lấy GPS hiện tại. Hãy kiểm tra GPS có đúng địa chỉ nhận hàng.");
                locationModel.clearTransientState();
            } else if (value.getStatus() == ProfileScreenState.Status.ERROR) {
                message.setText(value.getMessage()); locationModel.clearTransientState();
            }
        });
        address.setOnAddressChangedListener(() -> {
            if (bindingAddressDetails) return;
            destinationVersion++;
            addressEdited = true; selectedAddressId = "";
            bindingAddresses = true; savedAddressSpinner.setSelection(0); bindingAddresses = false;
            receiverLatitude = 0; receiverLongitude = 0; showReceiverGps(); scheduleShippingEstimate();
        });
        fulfillment.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_dropdown_item,
                FulfillmentType.values()));
        updateFulfillmentFields();
        bindPaymentMethods();
        vm = new ViewModelProvider(this, new CheckoutViewModelFactory(requireContext()))
                .get(CheckoutViewModel.class);
        ((TextView) view.findViewById(R.id.checkoutOwnerText)).setText(
                vm.isGuest() ? R.string.guest_checkout_notice : R.string.customer_checkout_notice);
        if (!vm.isGuest()) {
            vm.observeAddresses().observe(getViewLifecycleOwner(), this::renderAddresses);
        }
        updateFulfillmentFields();
        savedAddressSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View row, int position, long id) {
                if (bindingAddresses || position < 0 || position > savedAddresses.size()) return;
                String next = position == 0 ? "" : savedAddresses.get(position - 1).getId();
                if (next.equals(selectedAddressId)) return;
                selectedAddressId = next; addressEdited = true; restoringAddress = false;
                if (position == 0) {
                    destinationVersion++;
                    address.clear(); receiverLatitude = 0; receiverLongitude = 0; showReceiverGps(); scheduleShippingEstimate();
                } else applyAddress(savedAddresses.get(position - 1));
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
        TextWatcher recipientWatcher = new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (!bindingAddressDetails) addressEdited = true;
            }
            public void afterTextChanged(Editable s) { }
        };
        name.addTextChangedListener(recipientWatcher); phone.addTextChangedListener(recipientWatcher);
        vm.getCart().observe(getViewLifecycleOwner(), cart -> {
            renderCart(cart); refreshPaymentMethods();
        });
        vm.getState().observe(getViewLifecycleOwner(), result -> {
            message.setText(result.getMessage());
            // Keep the action tappable before a quote is ready so the user receives
            // an explanation and can explicitly request a fresh quote.
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
            if (fulfillment.getSelectedItem() == FulfillmentType.DELIVERY && !address.validate()) {
                String validationMessage = "Vui lòng hoàn thiện địa chỉ nhận hàng trước khi đặt đơn.";
                message.setText(validationMessage);
                message.setVisibility(View.VISIBLE);
                Toast.makeText(requireContext(), validationMessage, Toast.LENGTH_LONG).show();
                view.findViewById(R.id.checkoutAddress).requestFocus();
                return;
            }
            if (!quoteReady) {
                message.setText("Đang chờ báo giá mới nhất từ máy chủ. Hãy kiểm tra kết nối rồi thử lại.");
                message.setVisibility(View.VISIBLE);
                scheduleShippingEstimate(); return;
            }
            message.setText("Đang tạo đơn hàng và giữ tồn kho…");
            message.setVisibility(View.VISIBLE);
            vm.submit(text(name), text(phone), address.formattedAddress(), String.valueOf(receiverLatitude), String.valueOf(receiverLongitude),
                    (FulfillmentType) fulfillment.getSelectedItem(), (PaymentMethod) payment.getSelectedItem(),
                    text(note), quoteReady ? quotedSubtotal : Double.NaN,
                    quoteReady ? quotedShippingFee : Double.NaN);
        });
        submit.setEnabled(true);
        refreshPaymentMethods();
    }

    private void renderAddresses(List<Address> values) {
        savedAddresses.clear(); if (values != null) savedAddresses.addAll(values);
        List<String> labels = new ArrayList<>();
        labels.add(getString(R.string.checkout_other_address));
        for (Address saved : savedAddresses) labels.add((saved.isDefault() ? "Mặc định · " : "")
                + saved.getReceiverName() + " · " + saved.getFormattedAddress());
        bindingAddresses = true;
        savedAddressSpinner.setAdapter(new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_dropdown_item, labels));
        int selected = 0;
        boolean initial = selectedAddressId == null && !addressEdited && !restoringAddress;
        if (initial && !savedAddresses.isEmpty()) {
            selected = 1;
            for (int i = 0; i < savedAddresses.size(); i++) if (savedAddresses.get(i).isDefault()) { selected = i + 1; break; }
            selectedAddressId = savedAddresses.get(selected - 1).getId();
        } else {
            for (int i = 0; i < savedAddresses.size(); i++)
                if (savedAddresses.get(i).getId().equals(selectedAddressId)) { selected = i + 1; break; }
        }
        if (selected == 0) selectedAddressId = "";
        savedAddressSpinner.setSelection(selected);
        if (initial && selected > 0) applyAddress(savedAddresses.get(selected - 1));
        bindingAddresses = false;
    }

    private void applyAddress(Address saved) {
        destinationVersion++;
        bindingAddressDetails = true;
        name.setText(saved.getReceiverName()); phone.setText(saved.getReceiverPhone());
        address.setAddress(saved.getProvince(), saved.getDistrict(), saved.getWard(), saved.getStreet());
        boolean hasLocation = (saved.getLatitude() != 0D || saved.getLongitude() != 0D)
                && Double.isFinite(saved.getLatitude()) && Double.isFinite(saved.getLongitude());
        receiverLatitude = hasLocation ? saved.getLatitude() : 0;
        receiverLongitude = hasLocation ? saved.getLongitude() : 0;
        showReceiverGps();
        bindingAddressDetails = false;
        scheduleShippingEstimate();
    }

    private void requestLocation() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) takeLocation();
        else locationPermission.launch(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION});
    }
    private void takeLocation() {
        locationRequestVersion = destinationVersion;
        locationModel.requestCurrentLocation(true);
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
        quoteHandler.removeCallbacks(quoteRunnable); quoteHandler.postDelayed(quoteRunnable, 400L);
    }

    private void refreshShippingEstimate() {
        TextView estimate = getView() == null ? null : getView().findViewById(R.id.checkoutShippingEstimate);
        if (estimate == null) return;
        int version = quoteVersion;
        if (currentCart == null) { estimate.setText("Đang tải giỏ hàng…"); return; }
        JSONArray items = new JSONArray(); int count = 0;
        for (com.rescuefarm.domain.model.CartItem item : currentCart.getItems()) if (item.isSelected()) {
            JSONObject line = new JSONObject();
            try { line.put("productId", item.getProductId()); line.put("batchId", item.getBatchId());
                line.put("sellerId", item.getSellerId()); line.put("quantity", item.getQuantity()); }
            catch (org.json.JSONException ignored) { }
            items.put(line); count++;
        }
        if (count == 0) { estimate.setText("Chọn mặt hàng để xem tạm tính."); return; }
        double customerLat = 0D, customerLng = 0D;
        if (fulfillment.getSelectedItem() == FulfillmentType.DELIVERY) {
            customerLat = receiverLatitude; customerLng = receiverLongitude;
            if (!hasReceiverGps()) { estimate.setText(R.string.delivery_gps_required); return; }
        }
        JSONObject request = new JSONObject();
        try { request.put("items", items); request.put("fulfillmentType", fulfillment.getSelectedItem().toString());
            request.put("paymentMethod", payment.getSelectedItem().toString()); request.put("latitude", customerLat);
            request.put("longitude", customerLng); }
        catch (org.json.JSONException error) { quoteReady = false; estimate.setText("Không tạo được request báo giá."); return; }
        estimate.setText("Đang cập nhật giá và phí giao hàng…");
        RenderBackendClient.Callback quoteCallback = new RenderBackendClient.Callback() {
            @Override public void onSuccess(JSONObject quote) {
                if (version != quoteVersion || !isAdded()) return;
                quotedSubtotal = quote.optDouble("subtotal", Double.NaN);
                quotedShippingFee = quote.optDouble("shippingFee", Double.NaN);
                NumberFormat money = NumberFormat.getCurrencyInstance(new Locale("vi", "VN"));
                StringBuilder summary = new StringBuilder("Tạm tính đơn hàng\n");
                JSONArray lines = quote.optJSONArray("lines");
                if (lines != null) for (int i = 0; i < lines.length(); i++) {
                    JSONObject line = lines.optJSONObject(i); if (line == null) continue;
                    summary.append("• ").append(line.optString("productName")).append(" · ")
                            .append(line.optDouble("quantity")).append(' ').append(line.optString("unit"))
                            .append(" · ").append(money.format(line.optDouble("subtotal"))).append('\n');
                }
                summary.append("Tiền hàng: ").append(money.format(quotedSubtotal))
                        .append("\nGiảm giá: ").append(money.format(quote.optDouble("totalDiscount")))
                        .append("\nPhí giao: ").append(money.format(quotedShippingFee))
                        .append("\nTổng: ").append(money.format(quote.optDouble("total")));
                if (fulfillment.getSelectedItem() == FulfillmentType.DELIVERY)
                    summary.append("\nKhoảng cách GPS đường chim bay: ")
                            .append(String.format(Locale.US, "%.2f km", quote.optDouble("distanceKm")));
                String campaignId = quote.optString("campaignId", "");
                if (!campaignId.isEmpty()) summary.append("\nChiến dịch: ").append(quote.optString("campaignTitle"));
                if (fulfillment.getSelectedItem() == FulfillmentType.ON_SITE_RESCUE)
                    summary.append("\nĐiểm nhận: ").append(quote.optString("campaignLocationName"));
                sellerAddress.setText(fulfillment.getSelectedItem() == FulfillmentType.ON_SITE_RESCUE
                        ? "Điểm nhận tại chiến dịch: " + quote.optString("campaignLocationName")
                        : "Cửa hàng: " + quote.optString("sellerName") + "\nĐiểm gửi / nhận tại cửa hàng: "
                                + quote.optString("sellerAddress", "Chưa cập nhật"));
                estimate.setText(summary.toString());
                message.setText("");
                quoteReady = Double.isFinite(quotedSubtotal)
                        && Double.isFinite(quotedShippingFee); enableCheckoutSubmit();
            }
            @Override public void onError(String error) {
                if (version != quoteVersion || !isAdded()) return;
                quoteReady = false;
                String explanation = "Không lấy được báo giá: " + explainQuoteError(error);
                estimate.setText(explanation);
                message.setText(explanation);
                message.setVisibility(View.VISIBLE);
                enableCheckoutSubmit();
            }
        };
        if (vm.isGuest()) RenderBackendClient.postGuest("/quoteGuestCheckout", request, quoteCallback);
        else RenderBackendClient.post("/quoteCustomerCheckout", request, quoteCallback);
    }

    private String explainQuoteError(String error) {
        if (error == null) return "Không kết nối được máy chủ báo giá.";
        if (error.contains("Global shipping rate"))
            return "Admin chưa cấu hình hợp lệ appConfig/shipping trong Firestore.";
        if (error.contains("Seller GPS") || error.contains("Seller public profile"))
            return "Người bán cần cập nhật GPS trong Hồ sơ người bán trước khi giao hàng.";
        if (error.contains("Delivery address coordinates"))
            return "Hãy lấy GPS tại nơi nhận hàng hoặc chọn địa chỉ đã lưu có GPS.";
        if (error.contains("Shipping configuration unavailable"))
            return "Thiếu appConfig/shipping hoặc hồ sơ vị trí công khai của cửa hàng.";
        return error;
    }

    private void enableCheckoutSubmit() {
        View submit = getView() == null ? null : getView().findViewById(R.id.submitCheckoutButton);
        if (submit != null && quoteReady && vm.getState().getValue() != null
                && vm.getState().getValue().getStatus() != CheckoutState.Status.LOADING) submit.setEnabled(true);
    }

    private void updateFulfillmentFields() {
        if (fulfillment == null) return;
        boolean delivery = fulfillment.getSelectedItem() == FulfillmentType.DELIVERY;
        if (address != null) address.setVisibility(delivery ? View.VISIBLE : View.GONE);
        View view = getView();
        if (view != null) {
            gpsStatus.setVisibility(delivery ? View.VISIBLE : View.GONE);
            view.findViewById(R.id.checkoutReceiverAddressTitle).setVisibility(delivery ? View.VISIBLE : View.GONE);
            view.findViewById(R.id.checkoutUseLocation).setVisibility(delivery ? View.VISIBLE : View.GONE);
            view.findViewById(R.id.checkoutLocationHint).setVisibility(delivery ? View.VISIBLE : View.GONE);
            savedAddressSpinner.setVisibility(delivery && vm != null && !vm.isGuest() ? View.VISIBLE : View.GONE);
        }
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
        if (selectedSeller.isEmpty()) { sellerAddress.setText("Chọn sản phẩm để xem địa chỉ cửa hàng."); return; }
        vm.loadStorefront(selectedSeller, new StorefrontRepository.Callback() {
            public void success(SellerStorefront profile) {
                if (version != bankLoadVersion || getView() == null) return;
                bankTransferAvailable = profile.acceptsTransfer();
                if (!quoteReady && fulfillment.getSelectedItem() != FulfillmentType.ON_SITE_RESCUE) sellerAddress.setText(profile.address.trim().isEmpty()
                        ? "Cửa hàng chưa cập nhật địa chỉ gửi hàng."
                        : "Điểm gửi / nhận tại cửa hàng: " + profile.shopName + "\n" + profile.address);
                applyPaymentMethods();
            }
            public void error(String message) {
                if (version != bankLoadVersion || getView() == null) return;
                bankTransferAvailable = false; sellerAddress.setText("Chưa tải được địa chỉ cửa hàng. Hãy thử lại."); applyPaymentMethods();
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

    private boolean hasReceiverGps() {
        return com.rescuefarm.service.location.GpsCoordinates.isValid(receiverLatitude, receiverLongitude);
    }
    private void showReceiverGps() {
        gpsStatus.setText(hasReceiverGps() ? R.string.gps_selected : R.string.delivery_gps_required);
    }

    private String text(EditText field) { return field.getText().toString().trim(); }
    @Override public void onViewStateRestored(@Nullable Bundle state) {
        super.onViewStateRestored(state);
        if (gpsStatus != null && state != null) {
            receiverLatitude = state.getDouble("receiverLatitude");
            receiverLongitude = state.getDouble("receiverLongitude"); showReceiverGps();
            scheduleShippingEstimate();
        }
    }
    @Override public void onSaveInstanceState(@NonNull Bundle state) {
        super.onSaveInstanceState(state); state.putString("selectedAddressId", selectedAddressId);
        state.putDouble("receiverLatitude", receiverLatitude); state.putDouble("receiverLongitude", receiverLongitude);
    }
    @Override public void onDestroyView() {
        quoteHandler.removeCallbacks(quoteRunnable); quoteVersion++; bankLoadVersion++; locationRequestVersion = -1;
        super.onDestroyView();
    }
}
