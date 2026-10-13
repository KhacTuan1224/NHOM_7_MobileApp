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
import java.text.NumberFormat;
import java.util.Locale;

public class OrderDetailFragment extends Fragment {
    private OrderViewModel vm;
    private TextView detail, instructions, message, bankInfo;
    private ImageView qrImage;
    private LinearLayout items;
    private Spinner target;
    private EditText reference;
    private View transitionButton, confirmButton;
    private String orderId = "";

    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater,
            @Nullable ViewGroup parent, @Nullable Bundle state) {
        return inflater.inflate(R.layout.fragment_order_detail, parent, false);
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        detail = view.findViewById(R.id.orderDetailText); instructions = view.findViewById(R.id.paymentInstructionsText);
        message = view.findViewById(R.id.orderLifecycleMessage); bankInfo = view.findViewById(R.id.orderBankInfo);
        qrImage = view.findViewById(R.id.orderBankQr); items = view.findViewById(R.id.orderItemsContainer);
        target = view.findViewById(R.id.orderStatusTarget); reference = view.findViewById(R.id.bankReferenceInput);
        transitionButton = view.findViewById(R.id.transitionOrderButton);
        confirmButton = view.findViewById(R.id.confirmBankTransferButton);
        target.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_dropdown_item,
                OrderStatus.values()));
        Bundle args = getArguments(); orderId = args == null ? "" : args.getString("orderId", "");
        vm = new ViewModelProvider(this, new OrderViewModelFactory(requireContext())).get(OrderViewModel.class);
        vm.getState().observe(getViewLifecycleOwner(), this::render);
        transitionButton.setOnClickListener(unused -> vm.transition(orderId, (OrderStatus) target.getSelectedItem()));
        confirmButton.setOnClickListener(unused -> vm.confirmTransfer(orderId, reference.getText().toString().trim()));
        if (orderId.isEmpty()) message.setText(R.string.order_id_missing); else vm.load(orderId);
    }

    private void render(OrderLifecycleState state) {
        message.setText(state.getMessage()); boolean loading = state.getStatus() == OrderLifecycleState.Status.LOADING;
        transitionButton.setEnabled(!loading); confirmButton.setEnabled(!loading);
        if (state.getStatus() == OrderLifecycleState.Status.ERROR) {
            Toast.makeText(requireContext(), state.getMessage(), Toast.LENGTH_LONG).show(); return;
        }
        if (state.getStatus() != OrderLifecycleState.Status.SUCCESS) return;
        Order order = state.getOrder(); Payment payment = state.getPayment(); Shipment shipment = state.getShipment();
        String shipmentStatus = shipment == null ? getString(R.string.shipment_not_required) : shipment.getStatus().name();
        detail.setText(getString(R.string.order_detail_format, order.getOrderCode(), order.getStatus().name(),
                order.getFulfillmentType().name(), payment.getMethod().name(), payment.getStatus().name(), shipmentStatus,
                NumberFormat.getCurrencyInstance(new Locale("vi", "VN")).format(order.getTotalAmount())));
        String distanceLabel = shipment == null ? "" : String.format(Locale.getDefault(),
                " · %.1f km đường chim bay", shipment.getDistanceKm());
        detail.append("\nPhí giao hàng: " + NumberFormat.getCurrencyInstance(new Locale("vi", "VN"))
                .format(order.getShippingFee()) + distanceLabel + ".");
        instructions.setText(paymentInstructions(payment));
        String uid = FirebaseAuth.getInstance().getCurrentUser() == null ? ""
                : FirebaseAuth.getInstance().getCurrentUser().getUid();
        boolean seller = uid.equals(order.getSellerId());
        transitionButton.setVisibility(seller ? View.VISIBLE : View.GONE);
        target.setVisibility(seller ? View.VISIBLE : View.GONE);
        boolean bank = payment.getMethod() == PaymentMethod.BANK_TRANSFER;
        boolean pendingSellerBankConfirmation = seller && bank && payment.getStatus() == PaymentStatus.PENDING;
        reference.setVisibility(pendingSellerBankConfirmation ? View.VISIBLE : View.GONE);
        confirmButton.setVisibility(pendingSellerBankConfirmation ? View.VISIBLE : View.GONE);
        boolean customer = uid.equals(order.getOwnerId()) && order.getStatus() == OrderStatus.DELIVERED;
        renderItems(order, customer);
        if (bank && !seller) showSellerBankInfo(order, payment);
        else { bankInfo.setVisibility(View.GONE); qrImage.setVisibility(View.GONE); }
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
        qrImage.setVisibility(View.VISIBLE); Glide.with(this).load(url).into(qrImage);
    }

    private String paymentInstructions(Payment payment) {
        if (payment.getMethod() == PaymentMethod.BANK_TRANSFER)
            return getString(R.string.bank_transfer_manual_instruction, payment.getOrderId());
        if (payment.getMethod() == PaymentMethod.CASH_ON_SITE) return getString(R.string.cash_onsite_instruction);
        return getString(R.string.cod_instruction);
    }
    private boolean blank(String value) { return value == null || value.trim().isEmpty(); }
}
