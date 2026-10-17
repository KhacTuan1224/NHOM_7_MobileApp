package com.rescuefarm.ui.order;
import com.rescuefarm.ui.common.EnumLabels;
import com.rescuefarm.ui.common.EnumLabelAdapter;

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
    private TextView orderCodeView, orderStatusView, instructions, message, bankInfo;
    private ImageView qrImage;
    private LinearLayout items, orderInfo;
    private LinearLayout timeline;
    private Spinner target;
    private EditText reference;
    private EditText shipmentCarrier, shipmentTracking;
    private View shipmentTitle, saveShipment;
    private View statusActionTitle, bankTransferActionTitle;
    private View transitionButton, confirmButton;
    private com.google.android.material.button.MaterialButton receiptButton;
    private String orderId = "";
    private final OrderLifecycleService lifecycleService = new OrderLifecycleService();
    private List<OrderStatus> displayedTargets = Collections.emptyList();

    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater,
            @Nullable ViewGroup parent, @Nullable Bundle state) {
        return inflater.inflate(R.layout.fragment_order_detail, parent, false);
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        orderCodeView = view.findViewById(R.id.orderDetailCode);
        orderStatusView = view.findViewById(R.id.orderDetailStatus);
        orderInfo = view.findViewById(R.id.orderDetailInfoContainer);
        instructions = view.findViewById(R.id.paymentInstructionsText);
        message = view.findViewById(R.id.orderLifecycleMessage); bankInfo = view.findViewById(R.id.orderBankInfo);
        qrImage = view.findViewById(R.id.orderBankQr); items = view.findViewById(R.id.orderItemsContainer);
        target = view.findViewById(R.id.orderStatusTarget); reference = view.findViewById(R.id.bankReferenceInput);
        timeline = view.findViewById(R.id.orderStatusTimeline);
        transitionButton = view.findViewById(R.id.transitionOrderButton);
        confirmButton = view.findViewById(R.id.confirmBankTransferButton);
        receiptButton = view.findViewById(R.id.confirmReceiptButton);
        receiptButton.setOnClickListener(unused -> new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle("Xác nhận đã nhận hàng")
                .setMessage("Chỉ xác nhận khi bạn đã nhận đủ hàng và đã thanh toán nếu chọn trả tiền khi nhận hàng.")
                .setNegativeButton("Để sau", null)
                .setPositiveButton("Xác nhận", (dialog, which) -> vm.transition(orderId, OrderStatus.DELIVERED)).show());
        shipmentTitle = view.findViewById(R.id.shipmentManagementTitle);
        shipmentCarrier = view.findViewById(R.id.shipmentCarrierInput);
        shipmentTracking = view.findViewById(R.id.shipmentTrackingInput);
        saveShipment = view.findViewById(R.id.saveShipmentDetailsButton);
        statusActionTitle = view.findViewById(R.id.orderStatusActionTitle);
        bankTransferActionTitle = view.findViewById(R.id.bankTransferActionTitle);
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

    private void renderOrderInfo(Order order, Payment payment, Shipment shipment) {
        orderInfo.removeAllViews();
        NumberFormat money = NumberFormat.getCurrencyInstance(new Locale("vi", "VN"));
        addInfoRow("Trạng thái đơn", statusLabel(order.getStatus(), order.getPaymentMethod()), false);
        addInfoRow("Hình thức nhận", fulfillmentLabel(order.getFulfillmentType()), false);
        if (!blank(order.getReceiverName())) addInfoRow("Người nhận", order.getReceiverName(), false);
        if (!blank(order.getReceiverPhone())) addInfoRow("Số điện thoại", order.getReceiverPhone(), false);
        if (!blank(order.getReceiverAddress())) addInfoRow("Địa chỉ", order.getReceiverAddress(), false);
        addInfoRow("Thanh toán", paymentMethodLabel(payment.getMethod()), false);
        addInfoRow("Tình trạng thanh toán", paymentStatusLabel(payment.getStatus()), false);
        if (order.getFulfillmentType() == com.rescuefarm.domain.enums.FulfillmentType.DELIVERY) {
            addInfoRow("Vận chuyển", shipment == null ? "Chưa có thông tin" : shipmentStatusLabel(shipment.getStatus()), false);
            addInfoRow("Phí giao hàng", money.format(order.getShippingFee()), false);
            if (shipment != null && shipment.getDistanceKm() > 0D)
                addInfoRow("Khoảng cách", String.format(Locale.getDefault(), "%.1f km", shipment.getDistanceKm()), false);
        }
        addInfoRow("Tổng thanh toán", money.format(order.getTotalAmount()), true);
    }

    private void addInfoRow(String label, String value, boolean emphasized) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        int vertical = Math.round(8 * getResources().getDisplayMetrics().density);
        row.setPadding(0, vertical, 0, vertical);
        TextView labelView = new TextView(requireContext());
        labelView.setText(label);
        labelView.setTextSize(13);
        labelView.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(),
                R.color.rescue_on_surface_variant));
        TextView valueView = new TextView(requireContext());
        valueView.setText(value);
        valueView.setTextSize(emphasized ? 15 : 14);
        valueView.setGravity(android.view.Gravity.END);
        valueView.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(),
                emphasized ? R.color.rescue_primary : R.color.rescue_on_surface));
        if (emphasized) valueView.setTypeface(null, android.graphics.Typeface.BOLD);
        row.addView(labelView, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(valueView, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        orderInfo.addView(row);
        if (!emphasized) {
            View divider = new View(requireContext());
            divider.setBackgroundColor(androidx.core.content.ContextCompat.getColor(requireContext(),
                    R.color.rescue_outline));
            orderInfo.addView(divider, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1,
                    Math.round(getResources().getDisplayMetrics().density))));
        }
    }

    private String fulfillmentLabel(com.rescuefarm.domain.enums.FulfillmentType value) {
        return EnumLabels.label(value);
    }

    private String paymentMethodLabel(PaymentMethod value) {
        return EnumLabels.label(value);
    }

    private String paymentStatusLabel(PaymentStatus value) {
        return EnumLabels.label(value);
    }

    private String shipmentStatusLabel(com.rescuefarm.domain.enums.ShipmentStatus value) {
        return EnumLabels.label(value);
    }

    private void render(OrderLifecycleState state) {
        message.setText(state.getMessage());
        message.setVisibility(state.getMessage() == null || state.getMessage().trim().isEmpty()
                ? View.GONE : View.VISIBLE);
        boolean loading = state.getStatus() == OrderLifecycleState.Status.LOADING;
        transitionButton.setEnabled(!loading); confirmButton.setEnabled(!loading); receiptButton.setEnabled(!loading);
        if (state.getStatus() == OrderLifecycleState.Status.ERROR) {
            Toast.makeText(requireContext(), state.getMessage(), Toast.LENGTH_LONG).show(); return;
        }
        if (state.getStatus() != OrderLifecycleState.Status.SUCCESS) return;
        Order order = state.getOrder(); Payment payment = state.getPayment(); Shipment shipment = state.getShipment();
        orderCodeView.setText("Mã đơn " + order.getOrderCode());
        orderStatusView.setText(paymentStatusLabel(order.getStatus(), payment));
        renderOrderInfo(order, payment, shipment);
        renderTimeline(order, payment);
        instructions.setText(paymentInstructions(payment));
        String uid = FirebaseAuth.getInstance().getCurrentUser() == null ? ""
                : FirebaseAuth.getInstance().getCurrentUser().getUid();
        boolean seller = uid.equals(order.getSellerId());
        List<OrderStatus> nextStatuses = seller ? lifecycleService.getSellerNextStatuses(
                order.getFulfillmentType(), order.getStatus(), payment.getMethod(), payment.getStatus())
                : Collections.emptyList();
        if (!displayedTargets.equals(nextStatuses)) {
            displayedTargets = nextStatuses;
            target.setAdapter(new EnumLabelAdapter<>(requireContext(), nextStatuses));
        }
        boolean canTransition = seller && !nextStatuses.isEmpty();
        transitionButton.setVisibility(canTransition ? View.VISIBLE : View.GONE);
        target.setVisibility(canTransition ? View.VISIBLE : View.GONE);
        statusActionTitle.setVisibility(canTransition ? View.VISIBLE : View.GONE);
        boolean bank = payment.getMethod() == PaymentMethod.BANK_TRANSFER;
        boolean pendingSellerBankConfirmation = seller && bank && !payment.usesAutomaticVerification() && payment.getStatus() == PaymentStatus.PENDING
                && order.getStatus() != OrderStatus.CANCELLED && order.getStatus() != OrderStatus.DELIVERED;
        reference.setVisibility(pendingSellerBankConfirmation ? View.VISIBLE : View.GONE);
        confirmButton.setVisibility(pendingSellerBankConfirmation ? View.VISIBLE : View.GONE);
        bankTransferActionTitle.setVisibility(pendingSellerBankConfirmation ? View.VISIBLE : View.GONE);
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
        boolean buyer = (!uid.isEmpty() && uid.equals(order.getOwnerId())) || vm.canConfirmGuestReceipt(order.getId());
        boolean canReceive = buyer && !seller && lifecycleService.canConfirmReceipt(
                order.getFulfillmentType(), order.getStatus(), payment.getMethod(), payment.getStatus());
        receiptButton.setVisibility(canReceive ? View.VISIBLE : View.GONE);
        receiptButton.setText(payment.getMethod() == PaymentMethod.COD ? "Đã thanh toán và nhận đơn" : "Đã nhận hàng");
        boolean customer = !uid.isEmpty() && uid.equals(order.getOwnerId()) && order.getStatus() == OrderStatus.DELIVERED;
        renderItems(order, customer);
        if (bank && !seller && payment.getStatus() == PaymentStatus.PENDING
                && order.getStatus() != OrderStatus.CANCELLED) showSellerBankInfo(order, payment);
        else { bankInfo.setVisibility(View.GONE); qrImage.setVisibility(View.GONE); }
    }

    private String paymentStatusLabel(OrderStatus status, Payment payment) {
        if (status == OrderStatus.PENDING && payment.getMethod() == PaymentMethod.BANK_TRANSFER
                && payment.usesAutomaticVerification()) return "Chờ hệ thống xác minh chuyển khoản";
        return statusLabel(status, payment.getMethod());
    }

    private void renderTimeline(Order order, Payment payment) {
        timeline.removeAllViews();
        if (order.getStatus() == OrderStatus.CANCELLED) {
            addTimelineRow("Đơn hàng đã hủy", OrderStatus.CANCELLED, false, true, true);
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
            addTimelineRow(paymentStatusLabel(steps.get(i), payment),
                    steps.get(i), complete, active, i == steps.size() - 1);
        }
    }

    private void addTimelineRow(String label, OrderStatus status, boolean complete,
            boolean current, boolean last) {
        int colorRes, softRes, iconRes;
        switch (status) {
            case PENDING:
                colorRes = R.color.order_step_amber; softRes = R.color.order_step_amber_soft;
                iconRes = R.drawable.ic_nav_orders; break;
            case CONFIRMED:
                colorRes = R.color.order_step_blue; softRes = R.color.order_step_blue_soft;
                iconRes = R.drawable.ic_order_step_check; break;
            case PREPARING:
                colorRes = R.color.order_step_teal; softRes = R.color.order_step_teal_soft;
                iconRes = R.drawable.ic_splash_leaf; break;
            case SHIPPING:
                colorRes = R.color.order_step_purple; softRes = R.color.order_step_purple_soft;
                iconRes = R.drawable.ic_admin_shipping; break;
            case READY_FOR_PICKUP:
                colorRes = R.color.order_step_purple; softRes = R.color.order_step_purple_soft;
                iconRes = R.drawable.ic_store; break;
            case CANCELLED:
                colorRes = R.color.order_step_red; softRes = R.color.order_step_red_soft;
                iconRes = R.drawable.ic_close; break;
            default:
                colorRes = R.color.order_step_green; softRes = R.color.order_step_green_soft;
                iconRes = R.drawable.ic_order_step_check; break;
        }
        int color = requireContext().getColor(colorRes);
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(timelineDp(last ? 48 : 64));

        android.widget.FrameLayout rail = new android.widget.FrameLayout(requireContext());
        LinearLayout.LayoutParams railParams = new LinearLayout.LayoutParams(timelineDp(40),
                ViewGroup.LayoutParams.MATCH_PARENT);
        railParams.setMarginEnd(timelineDp(14));
        row.addView(rail, railParams);
        if (!last) {
            View connector = new View(requireContext());
            connector.setBackgroundColor(requireContext().getColor(
                    complete ? softRes : R.color.rescue_outline));
            android.widget.FrameLayout.LayoutParams lineParams = new android.widget.FrameLayout.LayoutParams(
                    timelineDp(2), ViewGroup.LayoutParams.MATCH_PARENT, android.view.Gravity.CENTER_HORIZONTAL);
            lineParams.topMargin = timelineDp(40);
            lineParams.bottomMargin = timelineDp(2);
            rail.addView(connector, lineParams);
        }
        ImageView icon = new ImageView(requireContext());
        icon.setImageResource(iconRes);
        icon.setImageTintList(android.content.res.ColorStateList.valueOf(color));
        icon.setPadding(timelineDp(9), timelineDp(9), timelineDp(9), timelineDp(9));
        icon.setAlpha(complete || current ? 1f : .45f);
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        rail.addView(icon, new android.widget.FrameLayout.LayoutParams(timelineDp(40), timelineDp(40),
                android.view.Gravity.TOP | android.view.Gravity.CENTER_HORIZONTAL));

        LinearLayout text = new LinearLayout(requireContext());
        text.setOrientation(LinearLayout.VERTICAL);
        text.setPadding(0, timelineDp(3), 0, timelineDp(last ? 3 : 15));
        TextView title = new TextView(requireContext());
        title.setText(label);
        title.setTextSize(14);
        title.setTextColor(requireContext().getColor(current ? colorRes
                : complete ? R.color.rescue_on_surface : R.color.rescue_on_surface_variant));
        if (current) title.setTypeface(null, android.graphics.Typeface.BOLD);
        text.addView(title);
        TextView caption = new TextView(requireContext());
        caption.setText(status == OrderStatus.CANCELLED ? R.string.order_timeline_cancelled
                : current ? (status == OrderStatus.DELIVERED ? R.string.order_timeline_finished
                : R.string.order_timeline_current) : complete ? R.string.order_timeline_complete
                : R.string.order_timeline_upcoming);
        caption.setTextSize(12);
        caption.setTextColor(requireContext().getColor(R.color.rescue_on_surface_variant));
        text.addView(caption);
        row.addView(text, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        row.setContentDescription(label + ". " + caption.getText());
        text.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        timeline.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private int timelineDp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private String statusLabel(OrderStatus status, PaymentMethod method) {
        return com.rescuefarm.ui.common.OrderLabels.status(status, method);
    }

    private void renderItems(Order order, boolean canReview) {
        items.removeAllViews();
        for (OrderItem item : order.getItems()) {
            LinearLayout row = new LinearLayout(requireContext());
            row.setOrientation(LinearLayout.VERTICAL);
            int vertical = Math.round(12 * getResources().getDisplayMetrics().density);
            row.setPadding(0, vertical, 0, vertical);
            TextView productName = new TextView(requireContext());
            productName.setText(item.getProductNameSnapshot());
            productName.setTextSize(15);
            productName.setTypeface(null, android.graphics.Typeface.BOLD);
            productName.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(),
                    R.color.rescue_on_surface));
            TextView productDetails = new TextView(requireContext());
            productDetails.setText(item.getQuantity() + " " + item.getUnitSnapshot() + "  ·  "
                    + NumberFormat.getCurrencyInstance(new Locale("vi", "VN")).format(item.getSubtotal()));
            productDetails.setTextSize(13);
            productDetails.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(),
                    R.color.rescue_on_surface_variant));
            row.addView(productName);
            row.addView(productDetails);
            com.google.android.material.imageview.ShapeableImageView productImage =
                    new com.google.android.material.imageview.ShapeableImageView(requireContext());
            productImage.setShapeAppearanceModel(productImage.getShapeAppearanceModel().toBuilder()
                    .setAllCornerSizes(timelineDp(12)).build());
            productImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
            productImage.setContentDescription("Ảnh sản phẩm " + item.getProductNameSnapshot());
            LinearLayout.LayoutParams imageParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, timelineDp(168));
            imageParams.topMargin = timelineDp(10);
            row.addView(productImage, imageParams);
            Glide.with(this).load(blank(item.getProductImageSnapshot()) ? null : item.getProductImageSnapshot())
                    .placeholder(android.R.drawable.ic_menu_gallery).error(android.R.drawable.ic_menu_gallery)
                    .centerCrop().into(productImage);
            items.addView(row);
            if (canReview) {
                com.google.android.material.button.MaterialButton button = new com.google.android.material.button.MaterialButton(
                        requireContext(), null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
                button.setText("Đánh giá sản phẩm");
                button.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.rescue_on_surface));
                button.setStrokeColor(android.content.res.ColorStateList.valueOf(
                        androidx.core.content.ContextCompat.getColor(requireContext(), R.color.rescue_primary)));
                button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.TRANSPARENT));
                button.setOnClickListener(view -> {
                    Bundle args = new Bundle(); args.putString("productId", item.getProductId());
                    args.putString("orderId", order.getId()); args.putString("orderItemId", item.getId());
                    Navigation.findNavController(view).navigate(
                            R.id.action_orderDetailFragment_to_reviewFragment, args);
                });
                items.addView(button);
            }
            View divider = new View(requireContext());
            divider.setBackgroundColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.rescue_outline));
            items.addView(divider, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    Math.max(1, Math.round(getResources().getDisplayMetrics().density))));
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
        String transferContent = payment.getTransferContent().isEmpty() ? order.getOrderCode() : payment.getTransferContent();
        bankInfo.setText("Chuyển khoản đến " + name + " · " + code + " · " + account
                + "\nNội dung: " + transferContent + " · Số tiền: "
                + NumberFormat.getCurrencyInstance(new Locale("vi", "VN")).format(order.getTotalAmount()));
        String url = "https://img.vietqr.io/image/" + Uri.encode(code) + "-" + Uri.encode(account)
                + "-compact.png?amount=" + Math.round(order.getTotalAmount()) + "&addInfo="
                + Uri.encode(transferContent) + "&accountName=" + Uri.encode(name);
        qrImage.setVisibility(View.VISIBLE); Glide.with(this).load(url).into(qrImage);
    }

    private String paymentInstructions(Payment payment) {
        if (payment.getMethod() == PaymentMethod.BANK_TRANSFER && payment.usesAutomaticVerification()) {
            if (payment.getStatus() == PaymentStatus.PAID)
                return "Hệ thống đã xác minh chuyển khoản thành công. Người mua xác nhận Đã nhận hàng sau khi nhận đủ hàng.";
            if (payment.getStatus() == PaymentStatus.FAILED)
                return "Đơn đã hủy hoặc không còn chờ thanh toán. Không chuyển khoản cho đơn này.";
            return "Thanh toán được SePay xác minh tự động. Quét đúng QR hoặc nhập chính xác nội dung "
                    + payment.getTransferContent() + ". Khi tiền về, trạng thái đơn sẽ tự cập nhật; không cần cửa hàng xác nhận thủ công.";
        }
        if (payment.getMethod() == PaymentMethod.BANK_TRANSFER)
            return getString(R.string.bank_transfer_manual_instruction, payment.getOrderId());
        if (payment.getMethod() == PaymentMethod.CASH_ON_SITE) return getString(R.string.cash_onsite_instruction);
        return getString(R.string.cod_instruction);
    }
    private boolean blank(String value) { return value == null || value.trim().isEmpty(); }
}
