package com.rescuefarm.ui.checkout;
import com.rescuefarm.ui.common.EnumLabelAdapter;

import android.Manifest;
import android.content.pm.PackageManager;
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
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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
import com.rescuefarm.domain.model.Order;
import com.rescuefarm.domain.model.User;
import com.rescuefarm.service.location.LocationProvider;
import com.rescuefarm.service.location.LocationProviderFactory;
import com.rescuefarm.service.location.GpsCoordinates;
import com.rescuefarm.ui.common.VietnamAddressView;
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
    private TextView gpsStatus, sellerAddress, paymentStatus;
    private View useCurrentLocationButton;
    private boolean hasPromptedLocationPermission;
    private final ActivityResultLauncher<String[]> locationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                boolean fine = Boolean.TRUE.equals(result.get(Manifest.permission.ACCESS_FINE_LOCATION));
                boolean coarse = Boolean.TRUE.equals(result.get(Manifest.permission.ACCESS_COARSE_LOCATION));
                if (fine || coarse) {
                    fetchCurrentLocation(true);
                } else {
                    gpsStatus.setText("Cần cấp quyền định vị để tự động lấy vị trí hiện tại.");
                    Toast.makeText(requireContext(), R.string.location_permission_required, Toast.LENGTH_SHORT).show();
                }
            });
    private VietnamAddressView address;
    private static final String PROFILE_ADDRESS = "__customer_profile__";
    private LocationProvider locationProvider;
    private User customer;
    private TextView selectedAddressText;
    private String receiverAddressText = "", resolvingAddress, failedAddress;
    private String destinationError = "";
    private TextView summary, message;
    private String selectedAddressId;
    private boolean addressEdited, restoringAddress, bindingAddressDetails;
    private int destinationVersion;
    private Spinner fulfillment, payment, savedAddressSpinner;
    private final List<Address> savedAddresses = new ArrayList<>();
    private boolean bindingAddresses;
    private boolean bindingPaymentMethods;
    private boolean bankTransferAvailable;
    private String paymentSellerId = "";
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
        selectedAddressText = view.findViewById(R.id.checkoutSelectedAddress);
        locationProvider = LocationProviderFactory.create(requireContext());
        sellerAddress = view.findViewById(R.id.checkoutSellerAddress);
        paymentStatus = view.findViewById(R.id.checkoutPaymentStatus);
        useCurrentLocationButton = view.findViewById(R.id.checkoutUseCurrentLocationButton);
        if (useCurrentLocationButton != null) {
            useCurrentLocationButton.setOnClickListener(v -> requestLocationPermissionAndFetch(true));
        }
        if (state != null) {
            receiverLatitude = state.getDouble("receiverLatitude");
            receiverLongitude = state.getDouble("receiverLongitude");
            receiverAddressText = state.getString("receiverAddressText", "");
            hasPromptedLocationPermission = state.getBoolean("hasPromptedLocationPermission", false);
        }
        showReceiverGps();
        fulfillment = view.findViewById(R.id.checkoutFulfillment); payment = view.findViewById(R.id.checkoutPayment);
        savedAddressSpinner = view.findViewById(R.id.checkoutSavedAddressSpinner);
        summary = view.findViewById(R.id.checkoutSummary); message = view.findViewById(R.id.checkoutMessage);
        if (state != null) selectedAddressId = state.getString("selectedAddressId", "");
        restoringAddress = state != null || addressEdited;
        addressEdited = addressEdited || state != null;
        address.setOnAddressChangedListener(() -> {
            if (bindingAddressDetails) return;
            destinationVersion++; resolvingAddress = null;
            addressEdited = true; selectedAddressId = "";
            bindingAddresses = true; savedAddressSpinner.setSelection(0); bindingAddresses = false;
            receiverAddressText = ""; failedAddress = null; destinationError = "";
            receiverLatitude = 0; receiverLongitude = 0; showReceiverGps(); scheduleShippingEstimate();
        });
        fulfillment.setAdapter(new EnumLabelAdapter<>(requireContext(), FulfillmentType.values()));
        updateFulfillmentFields();
        bindPaymentMethods();
        vm = new ViewModelProvider(this, new CheckoutViewModelFactory(requireContext()))
                .get(CheckoutViewModel.class);
        ((TextView) view.findViewById(R.id.checkoutOwnerText)).setText(
                vm.isGuest() ? R.string.guest_checkout_notice : R.string.customer_checkout_notice);
        if (!vm.isGuest()) {
            vm.observeAddresses().observe(getViewLifecycleOwner(), this::renderAddresses);
            vm.observeCustomer().observe(getViewLifecycleOwner(), value -> {
                customer = value;
                if (PROFILE_ADDRESS.equals(selectedAddressId)) applyCustomerLocation();
                else if (selectedAddressId == null && value != null
                        && GpsCoordinates.isValid(value.getLatitude(), value.getLongitude())) {
                    selectedAddressId = PROFILE_ADDRESS;
                    renderAddresses(new ArrayList<>(savedAddresses));
                    applyCustomerLocation();
                }
            });
        } else if (state == null && !addressEdited && !restoringAddress) {
            if (hasLocationPermission()) {
                fetchCurrentLocation(false);
            } else if (!hasPromptedLocationPermission) {
                hasPromptedLocationPermission = true;
                requestLocationPermissionAndFetch(false);
            }
        }
        updateFulfillmentFields();
        savedAddressSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View row, int position, long id) {
                if (bindingAddresses || position < 0 || position > savedAddresses.size() + 1) return;
                String next = position == 0 ? "" : position == 1 ? PROFILE_ADDRESS : savedAddresses.get(position - 2).getId();
                if (next.equals(selectedAddressId)) return;
                selectedAddressId = next; addressEdited = true; restoringAddress = false;
                if (position == 0) {
                    destinationVersion++; resolvingAddress = null;
                    address.clear(); receiverAddressText = ""; failedAddress = null; destinationError = "";
                    receiverLatitude = 0; receiverLongitude = 0; showReceiverGps(); scheduleShippingEstimate();
                } else if (position == 1) applyCustomerLocation();
                else applyAddress(savedAddresses.get(position - 2));
                updateFulfillmentFields();
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
                Order order = result.getOrder();
                new com.rescuefarm.data.local.preferences.SharedPreferencesGuestSessionRepository(requireContext())
                        .saveRecentGuestOrder(order.getId(), order.getOrderCode(), order.getReceiverPhone());
                Bundle args = new Bundle();
                args.putString("orderId", order.getId());
                args.putString("orderCode", order.getOrderCode());
                args.putString("phone", order.getReceiverPhone());
                args.putString("status", order.getStatus() == null ? "PENDING" : order.getStatus().name());
                args.putString("paymentMethod", order.getPaymentMethod() == null ? "COD" : order.getPaymentMethod().name());
                Navigation.findNavController(view).navigate(R.id.action_checkoutFragment_to_orderConfirmationFragment, args);
            } else if (result.getStatus() == CheckoutState.Status.ERROR) {
                Toast.makeText(requireContext(), result.getMessage(), Toast.LENGTH_LONG).show();
                if (result.getMessage() != null && result.getMessage().contains("QUOTE_CHANGED"))
                    scheduleShippingEstimate();
            }
        });
        View submit = view.findViewById(R.id.submitCheckoutButton);
        submit.setOnClickListener(unused -> {
            if (fulfillment.getSelectedItem() == FulfillmentType.DELIVERY
                    && (hasSelectedAddress() ? receiverAddressText.trim().isEmpty() : !address.validate())) {
                String validationMessage = destinationError.isEmpty()
                        ? "Vui lòng hoàn thiện địa chỉ nhận hàng trước khi đặt đơn." : destinationError;
                message.setText(validationMessage);
                message.setVisibility(View.VISIBLE);
                Toast.makeText(requireContext(), validationMessage, Toast.LENGTH_LONG).show();
                view.findViewById(R.id.checkoutAddress).requestFocus();
                return;
            }
            if (!quoteReady) {
                message.setText(destinationError.isEmpty()
                        ? "Đang cập nhật địa chỉ và báo giá. Vui lòng đợi trong giây lát."
                        : destinationError);
                failedAddress = null;
                message.setVisibility(View.VISIBLE);
                scheduleShippingEstimate(); return;
            }
            message.setText("Đang tạo đơn hàng và giữ tồn kho…");
            message.setVisibility(View.VISIBLE);
            vm.submit(text(name), text(phone), deliveryAddress(), String.valueOf(receiverLatitude), String.valueOf(receiverLongitude),
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
        labels.add("Vị trí của tôi · Hồ sơ cá nhân");
        for (Address saved : savedAddresses) labels.add((saved.isDefault() ? "Mặc định · " : "")
                + saved.getReceiverName() + " · " + saved.getFormattedAddress());
        bindingAddresses = true;
        savedAddressSpinner.setAdapter(new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_dropdown_item, labels));
        int selected = 0;
        boolean initial = selectedAddressId == null && !addressEdited && !restoringAddress;
        if (initial && !savedAddresses.isEmpty()) {
            selected = 2;
            for (int i = 0; i < savedAddresses.size(); i++) if (savedAddresses.get(i).isDefault()) { selected = i + 2; break; }
            selectedAddressId = savedAddresses.get(selected - 2).getId();
        } else if (initial) {
            selected = 1;
            selectedAddressId = PROFILE_ADDRESS;
        } else {
            if (PROFILE_ADDRESS.equals(selectedAddressId)) selected = 1;
            else for (int i = 0; i < savedAddresses.size(); i++)
                if (savedAddresses.get(i).getId().equals(selectedAddressId)) { selected = i + 2; break; }
        }
        if (selected == 0 && hasSelectedAddress()) {
            destinationVersion++; resolvingAddress = null; failedAddress = null;
            receiverLatitude = 0; receiverLongitude = 0; receiverAddressText = "";
            destinationError = "Địa chỉ đã chọn không còn trong sổ địa chỉ. Vui lòng chọn lại điểm nhận.";
            address.clear(); showReceiverGps(); scheduleShippingEstimate();
        }
        if (selected == 0) selectedAddressId = "";
        savedAddressSpinner.setSelection(selected);
        if (initial && selected > 1) applyAddress(savedAddresses.get(selected - 2));
        else if (initial && selected == 1) applyCustomerLocation();
        else if (selected > 1) {
            Address saved = savedAddresses.get(selected - 2);
            if (!saved.getFormattedAddress().equals(receiverAddressText)
                    || (GpsCoordinates.isValid(saved.getLatitude(), saved.getLongitude())
                    && (saved.getLatitude() != receiverLatitude || saved.getLongitude() != receiverLongitude)))
                applyAddress(saved);
        }
        bindingAddresses = false;
        updateFulfillmentFields();
    }

    private void applyAddress(Address saved) {
        destinationVersion++;
        resolvingAddress = null;
        bindingAddressDetails = true;
        name.setText(saved.getReceiverName()); phone.setText(saved.getReceiverPhone());
        address.setAddress(saved.getProvince(), saved.getDistrict(), saved.getWard(), saved.getStreet());
        receiverAddressText = saved.getFormattedAddress(); failedAddress = null; destinationError = "";
        selectedAddressText.setText(receiverAddressText);
        boolean hasLocation = GpsCoordinates.isValid(saved.getLatitude(), saved.getLongitude());
        receiverLatitude = hasLocation ? saved.getLatitude() : 0;
        receiverLongitude = hasLocation ? saved.getLongitude() : 0;
        showReceiverGps();
        bindingAddressDetails = false;
        scheduleShippingEstimate();
    }

    private boolean hasSelectedAddress() {
        return selectedAddressId != null && !selectedAddressId.isEmpty();
    }

    private String deliveryAddress() {
        return hasSelectedAddress() ? receiverAddressText : address.formattedAddress();
    }

    private void applyCustomerLocation() {
        final int version = ++destinationVersion;
        resolvingAddress = null;
        receiverLatitude = 0; receiverLongitude = 0; receiverAddressText = "";
        destinationError = ""; failedAddress = null;
        if (customer == null) {
            destinationError = "Đang tải vị trí trong hồ sơ của bạn…";
        } else {
            bindingAddressDetails = true;
            name.setText(customer.getFullName()); phone.setText(customer.getPhone());
            bindingAddressDetails = false;
            if (GpsCoordinates.isValid(customer.getLatitude(), customer.getLongitude())) {
                receiverLatitude = customer.getLatitude(); receiverLongitude = customer.getLongitude();
                selectedAddressText.setText("Đang tải địa chỉ vị trí của bạn…");
                locationProvider.reverseGeocode(receiverLatitude, receiverLongitude, new LocationProvider.GeocodeCallback() {
                    public void onAddressAvailable(LocationProvider.GeocodedAddress value) {
                        quoteHandler.post(() -> {
                            if (getView() == null || version != destinationVersion) return;
                            receiverAddressText = value.getFullAddress().trim();
                            if (receiverAddressText.isEmpty()) receiverAddressText = String.join(", ",
                                    value.getStreet(), value.getWard(), value.getDistrict(), value.getProvince());
                            selectedAddressText.setText(receiverAddressText);
                            scheduleShippingEstimate();
                        });
                    }
                    public void onGeocodeUnavailable(String error) {
                        quoteHandler.post(() -> {
                            if (getView() == null || version != destinationVersion) return;
                            destinationError = "Chưa tải được địa chỉ vị trí trong hồ sơ. Vui lòng chọn địa chỉ nhận đã lưu.";
                            selectedAddressText.setText(destinationError);
                            scheduleShippingEstimate();
                        });
                    }
                });
            } else destinationError = "Hồ sơ của bạn chưa có vị trí. Vui lòng chọn địa chỉ nhận đã lưu hoặc cập nhật vị trí trong hồ sơ cá nhân.";
        }
        if (!destinationError.isEmpty()) selectedAddressText.setText(destinationError);
        showReceiverGps(); updateFulfillmentFields(); scheduleShippingEstimate();
    }

    private void resolveDeliveryAddress() {
        if (PROFILE_ADDRESS.equals(selectedAddressId)) return;
        final String target = deliveryAddress();
        if ((!hasSelectedAddress() && !address.isComplete()) || target.trim().isEmpty()) return;
        if (target.equals(resolvingAddress) || target.equals(failedAddress)) return;
        final int version = destinationVersion;
        resolvingAddress = target;
        gpsStatus.setText("Đang xác định vị trí địa chỉ nhận hàng…");
        locationProvider.geocodeAddress(target, new LocationProvider.LocationCallback() {
            public void onLocationAvailable(double latitude, double longitude) {
                if (getView() == null || version != destinationVersion) return;
                resolvingAddress = null;
                receiverLatitude = latitude; receiverLongitude = longitude; destinationError = "";
                showReceiverGps(); scheduleShippingEstimate();
            }
            public void onLocationUnavailable(String error) {
                if (getView() == null || version != destinationVersion) return;
                resolvingAddress = null; failedAddress = target; destinationError = error;
                gpsStatus.setText(error); scheduleShippingEstimate();
            }
        });
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
            if (!hasReceiverGps() || (hasSelectedAddress() && receiverAddressText.trim().isEmpty())) {
                resolveDeliveryAddress();
                estimate.setText(destinationError.isEmpty() ? "Đang chờ địa chỉ nhận hàng của bạn." : destinationError);
                return;
            }
        }
        JSONObject request = new JSONObject();
        try { request.put("items", items); request.put("fulfillmentType", ((FulfillmentType) fulfillment.getSelectedItem()).name());
            request.put("paymentMethod", ((PaymentMethod) payment.getSelectedItem()).name()); request.put("latitude", customerLat);
            request.put("longitude", customerLng); }
        catch (org.json.JSONException error) { quoteReady = false; estimate.setText("Không tạo được request báo giá."); return; }
        estimate.setText("Đang cập nhật giá và phí giao hàng…");
        RenderBackendClient.Callback quoteCallback = new RenderBackendClient.Callback() {
            @Override public void onSuccess(JSONObject quote) {
                if (version != quoteVersion || !isAdded()) return;
                JSONArray methods = quote.optJSONArray("availablePaymentMethods");
                if (methods != null) updatePaymentAvailability(methods);
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
                        : "Cửa hàng: " + quote.optString("sellerName") + (fulfillment.getSelectedItem() == FulfillmentType.DELIVERY
                                ? "\nĐiểm gửi: " : "\nĐiểm nhận tại cửa hàng: ")
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
            return "Chưa xác định được vị trí địa chỉ nhận hàng đã chọn.";
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
        if (address != null) address.setVisibility(delivery && !hasSelectedAddress() ? View.VISIBLE : View.GONE);
        if (selectedAddressText != null) {
            selectedAddressText.setVisibility(delivery && hasSelectedAddress() ? View.VISIBLE : View.GONE);
            if (!receiverAddressText.isEmpty()) selectedAddressText.setText(receiverAddressText);
        }
        View view = getView();
        if (view != null) {
            gpsStatus.setVisibility(delivery ? View.VISIBLE : View.GONE);
            view.findViewById(R.id.checkoutReceiverAddressTitle).setVisibility(delivery ? View.VISIBLE : View.GONE);
            if (useCurrentLocationButton != null) useCurrentLocationButton.setVisibility(delivery ? View.VISIBLE : View.GONE);
            view.findViewById(R.id.checkoutLocationHint).setVisibility(delivery ? View.VISIBLE : View.GONE);
            savedAddressSpinner.setVisibility(delivery && vm != null && !vm.isGuest() ? View.VISIBLE : View.GONE);
        }
    }

    private void bindPaymentMethods() {
        payment.setAdapter(new EnumLabelAdapter<>(requireContext(), new PaymentMethod[] {PaymentMethod.CASH_ON_SITE}));
    }

    private void refreshPaymentMethods() {
        if (payment == null || fulfillment == null || !isAdded()) return;
        String sellerId = "";
        if (currentCart != null) for (com.rescuefarm.domain.model.CartItem item : currentCart.getItems())
            if (item.isSelected()) { sellerId = item.getSellerId(); break; }
        final String selectedSeller = sellerId;
        int version = ++bankLoadVersion;
        if (!selectedSeller.equals(paymentSellerId)) {
            bankTransferAvailable = false;
            paymentSellerId = selectedSeller;
        }
        applyPaymentMethods();
        if (selectedSeller.isEmpty()) { sellerAddress.setText("Chọn sản phẩm để xem địa chỉ cửa hàng."); return; }
        paymentStatus.setText("Đang tải phương thức thanh toán của cửa hàng…");
        JSONObject optionsRequest = new JSONObject();
        try {
            optionsRequest.put("sellerId", selectedSeller);
            optionsRequest.put("fulfillmentType", ((FulfillmentType) fulfillment.getSelectedItem()).name());
        } catch (org.json.JSONException ignored) { }
        RenderBackendClient.postGuest("/sellers/payment-options", optionsRequest, new RenderBackendClient.Callback() {
            public void onSuccess(JSONObject body) {
                if (version != bankLoadVersion || getView() == null) return;
                updatePaymentAvailability(body.optJSONArray("availablePaymentMethods"));
            }
            public void onError(String error) {
                if (version != bankLoadVersion || getView() == null) return;
                paymentStatus.setText("Chưa tải được phương thức thanh toán. Vui lòng thử lại. "
                        + (error != null && error.contains("HTTP 404")
                        ? "Máy chủ cần cập nhật API thanh toán." : ""));
            }
        });
        vm.loadStorefront(selectedSeller, new StorefrontRepository.Callback() {
            public void success(SellerStorefront profile) {
                if (version != bankLoadVersion || getView() == null) return;
                if (!quoteReady && fulfillment.getSelectedItem() != FulfillmentType.ON_SITE_RESCUE) sellerAddress.setText(profile.address.trim().isEmpty()
                        ? "Cửa hàng chưa cập nhật địa chỉ gửi hàng."
                        : (fulfillment.getSelectedItem() == FulfillmentType.DELIVERY
                                ? "Điểm gửi từ cửa hàng: " : "Điểm nhận tại cửa hàng: ") + profile.shopName + "\n" + profile.address);
            }
            public void error(String message) {
                if (version != bankLoadVersion || getView() == null) return;
                sellerAddress.setText("Chưa tải được địa chỉ cửa hàng. Hãy thử lại.");
            }
        });
    }

    private void updatePaymentAvailability(JSONArray methods) {
        if (methods == null) {
            paymentStatus.setText("Máy chủ chưa trả về phương thức thanh toán. Vui lòng cập nhật backend.");
            return;
        }
        boolean available = false;
        for (int i = 0; i < methods.length(); i++)
            if (PaymentMethod.BANK_TRANSFER.name().equals(methods.optString(i))) available = true;
        if (bankTransferAvailable != available) {
            bankTransferAvailable = available;
            applyPaymentMethods();
        }
        paymentStatus.setText(available ? "Có thể thanh toán bằng chuyển khoản ngân hàng."
                : "Cửa hàng chưa cấu hình đủ thông tin nhận chuyển khoản.");
    }

    private void applyPaymentMethods() {
        PaymentMethod previous = payment.getSelectedItem() instanceof PaymentMethod
                ? (PaymentMethod) payment.getSelectedItem() : null;
        List<PaymentMethod> allowed = new ArrayList<>();
        if (fulfillment.getSelectedItem() == FulfillmentType.DELIVERY) allowed.add(PaymentMethod.COD);
        else allowed.add(PaymentMethod.CASH_ON_SITE);
        if (bankTransferAvailable) allowed.add(PaymentMethod.BANK_TRANSFER);
        boolean unchanged = payment.getCount() == allowed.size();
        for (int i = 0; unchanged && i < allowed.size(); i++)
            unchanged = allowed.get(i).equals(payment.getItemAtPosition(i));
        if (unchanged) return;
        bindingPaymentMethods = true;
        payment.setAdapter(new EnumLabelAdapter<>(requireContext(), allowed));
        int selected = previous == null ? -1 : allowed.indexOf(previous);
        payment.setSelection(selected < 0 ? 0 : selected);
        bindingPaymentMethods = false;
    }

    private boolean hasReceiverGps() {
        return com.rescuefarm.service.location.GpsCoordinates.isValid(receiverLatitude, receiverLongitude);
    }
    private void showReceiverGps() {
        if (hasReceiverGps()) {
            gpsStatus.setText(String.format(Locale.getDefault(),
                    "Đã xác định vị trí nhận hàng (%.4f, %.4f).", receiverLatitude, receiverLongitude));
        } else {
            gpsStatus.setText(destinationError.isEmpty() ? getString(R.string.delivery_gps_required) : destinationError);
        }
    }

    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestLocationPermissionAndFetch(boolean userTriggered) {
        if (hasLocationPermission()) {
            fetchCurrentLocation(userTriggered);
        } else {
            locationPermissionLauncher.launch(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            });
        }
    }

    private void fetchCurrentLocation(boolean userTriggered) {
        final int version = ++destinationVersion;
        resolvingAddress = null;
        failedAddress = null;
        destinationError = "";
        gpsStatus.setText(R.string.checkout_gps_fetching);
        if (useCurrentLocationButton != null) useCurrentLocationButton.setEnabled(false);

        locationProvider.getCurrentLocation(new LocationProvider.LocationCallback() {
            @Override public void onLocationAvailable(double latitude, double longitude) {
                quoteHandler.post(() -> {
                    if (getView() == null || version != destinationVersion) return;
                    if (useCurrentLocationButton != null) useCurrentLocationButton.setEnabled(true);
                    receiverLatitude = latitude;
                    receiverLongitude = longitude;
                    showReceiverGps();

                    gpsStatus.setText("Đang xác định địa chỉ từ toạ độ GPS…");
                    locationProvider.reverseGeocode(latitude, longitude, new LocationProvider.GeocodeCallback() {
                        @Override public void onAddressAvailable(LocationProvider.GeocodedAddress geocoded) {
                            quoteHandler.post(() -> {
                                if (getView() == null || version != destinationVersion) return;
                                bindingAddressDetails = true;
                                boolean applied = address.applyGeocodedAddress(geocoded);
                                bindingAddressDetails = false;
                                if (applied) {
                                    receiverAddressText = address.formattedAddress();
                                    selectedAddressText.setText(receiverAddressText);
                                    gpsStatus.setText(R.string.checkout_gps_success);
                                } else {
                                    if (geocoded.getFullAddress() != null && !geocoded.getFullAddress().trim().isEmpty()) {
                                        receiverAddressText = geocoded.getFullAddress().trim();
                                        selectedAddressText.setText(receiverAddressText);
                                    }
                                    if (address.isComplete()) {
                                        gpsStatus.setText(R.string.checkout_gps_success);
                                    } else {
                                        gpsStatus.setText("Đã nhận toạ độ GPS. Vui lòng kiểm tra/bổ sung số nhà, tên đường.");
                                    }
                                }
                                showReceiverGps();
                                scheduleShippingEstimate();
                            });
                        }

                        @Override public void onGeocodeUnavailable(String message) {
                            quoteHandler.post(() -> {
                                if (getView() == null || version != destinationVersion) return;
                                showReceiverGps();
                                scheduleShippingEstimate();
                            });
                        }
                    });
                });
            }

            @Override public void onLocationUnavailable(String message) {
                quoteHandler.post(() -> {
                    if (getView() == null || version != destinationVersion) return;
                    if (useCurrentLocationButton != null) useCurrentLocationButton.setEnabled(true);
                    destinationError = message;
                    gpsStatus.setText(message);
                    if (userTriggered && isAdded()) {
                        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
    }

    private String text(EditText field) { return field.getText().toString().trim(); }
    @Override public void onViewStateRestored(@Nullable Bundle state) {
        super.onViewStateRestored(state);
        if (gpsStatus != null && state != null) {
            showReceiverGps(); updateFulfillmentFields();
            scheduleShippingEstimate();
        }
    }
    @Override public void onSaveInstanceState(@NonNull Bundle state) {
        super.onSaveInstanceState(state); state.putString("selectedAddressId", selectedAddressId);
        state.putString("receiverAddressText", receiverAddressText);
        state.putDouble("receiverLatitude", receiverLatitude); state.putDouble("receiverLongitude", receiverLongitude);
        state.putBoolean("hasPromptedLocationPermission", hasPromptedLocationPermission);
    }
    @Override public void onDestroyView() {
        quoteHandler.removeCallbacks(quoteRunnable); quoteVersion++; bankLoadVersion++; destinationVersion++;
        resolvingAddress = null; failedAddress = null;
        if (locationProvider != null) locationProvider.close();
        super.onDestroyView();
    }
}
