package com.rescuefarm.ui.order;

import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.rescuefarm.R;
import com.rescuefarm.domain.enums.OrderStatus;
import com.rescuefarm.domain.enums.PaymentMethod;
import com.rescuefarm.domain.enums.PaymentStatus;
import com.rescuefarm.domain.model.Order;
import com.rescuefarm.domain.model.OrderItem;
import com.rescuefarm.domain.model.Payment;
import com.rescuefarm.domain.model.Shipment;
import com.rescuefarm.service.backend.RenderBackendClient;
import com.rescuefarm.service.order.OrderLifecycleService;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.Arrays;
import java.util.List;
import java.util.Collections;
import org.json.JSONObject;

public class OrderDetailFragment extends Fragment {
    private OrderViewModel vm;
    private TextView detail, instructions, message, bankInfo;
    private ImageView qrImage;
    private LinearLayout items;
    private LinearLayout timeline;
    private Spinner target;
    private EditText reference;
    private EditText shipmentCarrier, shipmentTracking;
    private View shipmentTitle, saveShipment;
    private View transitionButton, confirmButton;
    private String orderId = "";
    private String lastObservedEvent = "";
    private final OrderLifecycleService lifecycleService = new OrderLifecycleService();
    private List<OrderStatus> displayedTargets = Collections.emptyList();

    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater,
            @Nullable ViewGroup parent, @Nullable Bundle state) {
        return inflater.inflate(R.layout.fragment_order_detail, parent, false);
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        detail = view.findViewById(R.id.orderDetailText); instructions = view.findViewById(R.id.paymentInstructionsText);
        message = view.findViewById(R.id.orderLifecycleMessage); bankInfo = view.findViewById(R.id.orderBankInfo);
        qrImage = view.findViewById(R.id.orderBankQr); items = view.findViewById(R.id.orderItemsContainer);
        target = view.findViewById(R.id.orderStatusTarget); reference = view.findViewById(R.id.bankReferenceInput);
        timeline = view.findViewById(R.id.orderStatusTimeline);
        transitionButton = view.findViewById(R.id.transitionOrderButton);
        confirmButton = view.findViewById(R.id.confirmBankTransferButton);
        shipmentTitle = view.findViewById(R.id.shipmentManagementTitle);
        shipmentCarrier = view.findViewById(R.id.shipmentCarrierInput);
        shipmentTracking = view.findViewById(R.id.shipmentTrackingInput);
        saveShipment = view.findViewById(R.id.saveShipmentDetailsButton);
        Bundle args = getArguments(); orderId = args == null ? "" : args.getString("orderId", "");
        vm = new ViewModelProvider(this, new OrderViewModelFactory(requireContext())).get(OrderViewModel.class);
        vm.getState().observe(getViewLifecycleOwner(), this::render);
        transitionButton.setOnClickListener(unused -> {
            Object selected = target.getSelectedItem();
            if (selected instanceof OrderStatus && displayedTargets.contains(selected))
                vm.transition(orderId, (OrderStatus) selected);
        });
        confirmButton.setOnClickListener(unused -> vm.confirmTransfer(orderId, reference.getText().toString().trim()));
        saveShipment.setOnClickListener(unused -> vm.updateShipmentDetails(orderId,
                shipmentCarrier.getText().toString(), shipmentTracking.getText().toString(),
                new com.rescuefarm.data.repository.OrderRepository.ActionCallback() {
                    @Override public void onSuccess() {
                        if (isAdded()) Toast.makeText(requireContext(), R.string.shipment_details_saved,
                                Toast.LENGTH_SHORT).show();
                    }
                    @Override public void onError(com.rescuefarm.data.repository.OrderRepository.ErrorCode error,
                            String text) {
                        if (isAdded()) Toast.makeText(requireContext(), text, Toast.LENGTH_LONG).show();
                    }
                }));
        if (orderId.isEmpty()) message.setText(R.string.order_id_missing);
    }

    @Override public void onStart() {
        super.onStart();
        if (vm != null && !orderId.isEmpty()) vm.observeOrder(orderId);
    }

    @Override public void onStop() {
        if (vm != null && !orderId.isEmpty()) vm.stopObservingOrder(orderId);
        super.onStop();
    }

    private void render(OrderLifecycleState state) {
        message.setText(state.getMessage()); boolean loading = state.getStatus() == OrderLifecycleState.Status.LOADING;
        transitionButton.setEnabled(!loading); confirmButton.setEnabled(!loading);
        if (state.getStatus() == OrderLifecycleState.Status.ERROR) {
            Toast.makeText(requireContext(), state.getMessage(), Toast.LENGTH_LONG).show(); return;
        }
        if (state.getStatus() != OrderLifecycleState.Status.SUCCESS) return;
        Order order = state.getOrder(); Payment payment = state.getPayment(); Shipment shipment = state.getShipment();
        String eventKey = order.getStatus().name() + "_" + payment.getStatus().name();
        if (!lastObservedEvent.isEmpty() && !lastObservedEvent.equals(eventKey)) notifyOrderEvent();
        lastObservedEvent = eventKey;
        String shipmentStatus = order.getFulfillmentType() != com.rescuefarm.domain.enums.FulfillmentType.DELIVERY
                ? getString(R.string.shipment_not_required)
                : shipment == null ? "Đang chờ thông tin vận chuyển" : shipment.getStatus().name();
        detail.setText(getString(R.string.order_detail_format, order.getOrderCode(), order.getStatus().name(),
                order.getFulfillmentType().name(), payment.getMethod().name(), payment.getStatus().name(), shipmentStatus,
                NumberFormat.getCurrencyInstance(new Locale("vi", "VN")).format(order.getTotalAmount())));
        String distanceLabel = shipment == null ? "" : String.format(Locale.getDefault(),
                " · %.1f km đường chim bay", shipment.getDistanceKm());
        detail.append("\nPhí giao hàng: " + NumberFormat.getCurrencyInstance(new Locale("vi", "VN"))
                .format(order.getShippingFee()) + distanceLabel + ".");
        renderTimeline(order);
        instructions.setText(paymentInstructions(payment));
        String uid = FirebaseAuth.getInstance().getCurrentUser() == null ? ""
                : FirebaseAuth.getInstance().getCurrentUser().getUid();
        boolean seller = uid.equals(order.getSellerId());
        List<OrderStatus> nextStatuses = seller ? lifecycleService.getAllowedNextStatuses(
                order.getFulfillmentType(), order.getStatus(), payment.getMethod(), payment.getStatus())
                : Collections.emptyList();
        if (!displayedTargets.equals(nextStatuses)) {
            displayedTargets = nextStatuses;
            target.setAdapter(new ArrayAdapter<>(requireContext(),
                    android.R.layout.simple_spinner_dropdown_item, nextStatuses));
        }
        boolean canTransition = seller && !nextStatuses.isEmpty();
        transitionButton.setVisibility(canTransition ? View.VISIBLE : View.GONE);
        target.setVisibility(canTransition ? View.VISIBLE : View.GONE);
        boolean bank = payment.getMethod() == PaymentMethod.BANK_TRANSFER;
        boolean pendingSellerBankConfirmation = seller && bank && payment.getStatus() == PaymentStatus.PENDING;
        reference.setVisibility(pendingSellerBankConfirmation ? View.VISIBLE : View.GONE);
        confirmButton.setVisibility(pendingSellerBankConfirmation ? View.VISIBLE : View.GONE);
        boolean delivery = order.getFulfillmentType() == com.rescuefarm.domain.enums.FulfillmentType.DELIVERY;
        boolean shipmentEditable = seller && delivery && shipment != null
                && (order.getStatus() == OrderStatus.CONFIRMED || order.getStatus() == OrderStatus.PREPARING
                    || order.getStatus() == OrderStatus.SHIPPING);
        shipmentTitle.setVisibility(seller && delivery ? View.VISIBLE : View.GONE);
        shipmentCarrier.setVisibility(shipmentEditable ? View.VISIBLE : View.GONE);
        shipmentTracking.setVisibility(shipmentEditable ? View.VISIBLE : View.GONE);
        saveShipment.setVisibility(shipmentEditable ? View.VISIBLE : View.GONE);
        if (shipment != null) {
            if (!shipmentCarrier.hasFocus()) shipmentCarrier.setText(shipment.getCarrierName());
            if (!shipmentTracking.hasFocus()) shipmentTracking.setText(shipment.getTrackingCode());
        }
        boolean customer = uid.equals(order.getOwnerId()) && order.getStatus() == OrderStatus.DELIVERED;
        renderItems(order, customer);
        if (bank && !seller) showSellerBankInfo(order, payment);
        else { bankInfo.setVisibility(View.GONE); qrImage.setVisibility(View.GONE); }
    }

    private void notifyOrderEvent() {
        JSONObject request = new JSONObject();
        try { request.put("type", "ORDER"); request.put("referenceId", orderId); }
        catch (org.json.JSONException ignored) { return; }
        RenderBackendClient.post("/send-notification", request, new RenderBackendClient.Callback() {
            @Override public void onSuccess(JSONObject response) { }
            @Override public void onError(String error) {
                // Order state remains visible in Firestore; push/inbox delivery is best-effort on Render free tier.
            }
        });
    }

    private void renderTimeline(Order order) {
        timeline.removeAllViews();
        if (order.getStatus() == OrderStatus.CANCELLED) {
            addTimelineRow("Đơn hàng đã hủy · phần tồn giữ chỗ đã được hoàn trả.", true, false);
            return;
        }
        List<OrderStatus> steps;
        switch (order.getFulfillmentType()) {
            case DELIVERY:
                steps = Arrays.asList(OrderStatus.PENDING, OrderStatus.CONFIRMED,
                        OrderStatus.PREPARING, OrderStatus.SHIPPING, OrderStatus.DELIVERED); break;
            case PICKUP:
                steps = Arrays.asList(OrderStatus.PENDING, OrderStatus.CONFIRMED,
                        OrderStatus.PREPARING, OrderStatus.READY_FOR_PICKUP, OrderStatus.DELIVERED); break;
            case ON_SITE_RESCUE:
            default:
                steps = Arrays.asList(OrderStatus.PENDING, OrderStatus.CONFIRMED,
                        OrderStatus.PREPARING, OrderStatus.DELIVERED); break;
        }
        int current = steps.indexOf(order.getStatus());
        for (int i = 0; i < steps.size(); i++) {
            boolean complete = current >= 0 && i < current;
            boolean active = i == current;
            addTimelineRow((complete ? "✓ " : active ? "● " : "○ ")
                    + statusLabel(steps.get(i), order.getFulfillmentType()), complete || active, active);
        }
    }

    private void addTimelineRow(String label, boolean emphasized, boolean current) {
        TextView row = new TextView(requireContext());
        row.setText(label); row.setTextSize(14);
        row.setTextColor(requireContext().getColor(current ? R.color.rescue_primary
                : emphasized ? R.color.rescue_on_surface : R.color.rescue_on_surface_variant));
        if (current) row.setTypeface(null, android.graphics.Typeface.BOLD);
        row.setPadding(0, 6, 0, 6); timeline.addView(row);
    }

    private String statusLabel(OrderStatus status, com.rescuefarm.domain.enums.FulfillmentType fulfillment) {
        switch (status) {
            case PENDING: return "Cửa hàng xác nhận đơn";
            case CONFIRMED: return "Đơn đã được xác nhận";
            case PREPARING: return "Đang chuẩn bị nông sản";
            case SHIPPING: return "Đang giao hàng";
            case READY_FOR_PICKUP: return "Sẵn sàng để nhận tại cửa hàng";
            case DELIVERED: return fulfillment == com.rescuefarm.domain.enums.FulfillmentType.DELIVERY
                    ? "Đã giao hàng" : "Đã nhận nông sản";
            case CANCELLED: return "Đã hủy";
            default: return status.name();
        }
    }

    private void renderItems(Order order, boolean canReview) {
        items.removeAllViews();
        for (OrderItem item : order.getItems()) {
            TextView row = new TextView(requireContext());
            row.setText(item.getProductNameSnapshot() + " · " + item.getQuantity() + " "
                    + item.getUnitSnapshot() + " · " + NumberFormat.getCurrencyInstance(new Locale("vi", "VN"))
                    .format(item.getSubtotal()));
            row.setPadding(0, 12, 0, 12); items.addView(row);
            if (canReview) {
                com.google.android.material.button.MaterialButton button = new com.google.android.material.button.MaterialButton(
                        requireContext(), null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
                button.setText("Đánh giá sản phẩm");
                button.setOnClickListener(view -> {
                    Bundle args = new Bundle(); args.putString("productId", item.getProductId());
                    args.putString("orderId", order.getId()); args.putString("orderItemId", item.getId());
                    Navigation.findNavController(view).navigate(
                            R.id.action_orderDetailFragment_to_reviewFragment, args);
                });
                items.addView(button);
            }
        }
        if (order.getItems().isEmpty()) {
            TextView empty = new TextView(requireContext()); empty.setText("Không tải được danh sách mặt hàng của đơn.");
            items.addView(empty);
        }
    }

    private void showSellerBankInfo(Order order, Payment payment) {
        String code = payment.getBankCode(), account = payment.getBankAccountNumber();
        String name = payment.getBankAccountName();
        bankInfo.setVisibility(View.VISIBLE); qrImage.setVisibility(View.GONE);
        if (blank(code) || blank(account) || blank(name)) {
            bankInfo.setText("Đơn chưa lưu thông tin tài khoản nhận chuyển khoản."); return;
        }
        bankInfo.setText("Chuyển khoản đến " + name + " · " + code + " · " + account
                + "\nNội dung: " + order.getOrderCode() + " · Số tiền: "
                + NumberFormat.getCurrencyInstance(new Locale("vi", "VN")).format(order.getTotalAmount()));
        String url = "https://img.vietqr.io/image/" + Uri.encode(code) + "-" + Uri.encode(account)
                + "-compact.png?amount=" + Math.round(order.getTotalAmount()) + "&addInfo="
                + Uri.encode(order.getOrderCode()) + "&accountName=" + Uri.encode(name);
        qrImage.setVisibility(View.VISIBLE); Glide.with(this).load(payment.getQrUrl().isEmpty() ? url : payment.getQrUrl()).into(qrImage);
    }

    private String paymentInstructions(Payment payment) {
        if (payment.getMethod() == PaymentMethod.BANK_TRANSFER)
            return getString(R.string.bank_transfer_manual_instruction, payment.getOrderId());
        if (payment.getMethod() == PaymentMethod.CASH_ON_SITE) return getString(R.string.cash_onsite_instruction);
        return getString(R.string.cod_instruction);
    }
    private boolean blank(String value) { return value == null || value.trim().isEmpty(); }
}
