package com.rescuefarm.ui.order;

import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.annotation.*;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import com.google.firebase.auth.FirebaseAuth;
import com.rescuefarm.R;
import com.rescuefarm.ui.common.*;
import com.rescuefarm.service.backend.RenderBackendClient;
import com.rescuefarm.data.local.preferences.SharedPreferencesGuestSessionRepository;
import org.json.*;
import java.util.UUID;

/** Evidence and administrator resolution; never confirms incoming bank transfers. */
public final class OrderReconciliationFragment extends Fragment {
    private LinearLayout content;
    private TextView message;
    private String orderId = "", requestId = UUID.randomUUID().toString();
    private int generation;
    private boolean busy, realtimePending;
    private View listReload;
    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup parent, @Nullable Bundle state) {
        orderId = state == null ? getArguments() == null ? "" : getArguments().getString("orderId", "") : state.getString("orderId", "");
        if (state != null) requestId = state.getString("requestId", requestId);
        LinearLayout root = CustomerUi.column(requireContext()), bar = CustomerUi.column(requireContext());
        bar.setOrientation(LinearLayout.HORIZONTAL); bar.setGravity(Gravity.CENTER_VERTICAL); root.addView(bar);
        AppBackButton back = new AppBackButton(requireContext()); bar.addView(back, new LinearLayout.LayoutParams(CustomerUi.dp(requireContext(),48),CustomerUi.dp(requireContext(),48)));
        back.setOnClickListener(v -> NavHostFragment.findNavController(this).navigateUp());
        TextView title = AppTypography.title(CustomerUi.text(bar, "Đối soát đơn hàng")); title.setTextSize(22);
        title.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
        message = CustomerUi.text(root, "");
        content = CustomerUi.column(requireContext()); root.addView(content);
        return CustomerUi.screen(this, root);
    }
    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        load(); bindRealtime();
    }
    private void bindRealtime() {
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            if (orderId.isEmpty()) return;
            String token = new SharedPreferencesGuestSessionRepository(requireContext()).getOrderAccessToken(orderId);
            com.rescuefarm.service.realtime.BackendSnapshotWatch watch = new com.rescuefarm.service.realtime.BackendSnapshotWatch("guest_order");
            try { watch.configure("guest_order", new JSONObject().put("orderId", orderId).put("accessToken", token)); } catch (JSONException ignored) { }
            watch.observe(getViewLifecycleOwner(), version -> refreshRealtime());
        } else {
            com.google.firebase.firestore.FirebaseFirestore db = com.google.firebase.firestore.FirebaseFirestore.getInstance();
            if (orderId.isEmpty()) com.rescuefarm.service.realtime.FirebaseRealtime.bind(getViewLifecycleOwner(), this::refreshRealtime,
                    db.collection("orders").whereEqualTo("expiryReviewRequired", true));
            else com.rescuefarm.service.realtime.FirebaseRealtime.bind(getViewLifecycleOwner(), this::refreshRealtime,
                    db.collection("orders").document(orderId), db.collection("payments").document(orderId));
        }
    }
    private void refreshRealtime() { if (busy) realtimePending = true; else { realtimePending = false; load(); } }
    @Override public void onSaveInstanceState(@NonNull Bundle state) {
        super.onSaveInstanceState(state); state.putString("orderId", orderId); state.putString("requestId", requestId);
    }
    @Override public void onDestroyView() { generation++; busy = false; content = null; super.onDestroyView(); }
    public static void addEntry(Fragment fragment, View anchor, String label, String orderId) {
        if (!(anchor.getParent() instanceof LinearLayout)) return;
        LinearLayout parent = (LinearLayout) anchor.getParent();
        String tag = "order-reconciliation-" + orderId;
        if (parent.findViewWithTag(tag) != null) return;
        com.google.android.material.button.MaterialButton button = new com.google.android.material.button.MaterialButton(
            fragment.requireContext(), null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        button.setTag(tag); button.setText(label); button.setAllCaps(false); button.setIconResource(R.drawable.ic_nav_orders); AppTypography.body(button);
        parent.addView(button, parent.indexOfChild(anchor), new LinearLayout.LayoutParams(-1,-2));
        button.setOnClickListener(v -> {
            Bundle args = new Bundle(); args.putString("orderId", orderId);
            NavHostFragment.findNavController(fragment).navigate(R.id.orderReconciliationFragment, args);
        });
    }
    private JSONObject body() { JSONObject b = new JSONObject(); try { b.put("orderId", orderId); } catch (JSONException ignored) { } return b; }
    private void request(String path, JSONObject body, java.util.function.Consumer<JSONObject> done) {
        if (busy) return;
        busy = true; final int expected = ++generation;
        message.setText("Đang xử lý…");
        RenderBackendClient.Callback callback = new RenderBackendClient.Callback() {
            public void onSuccess(JSONObject value) {
                if (expected != generation || getView() == null) return;
                busy = false; message.setText(""); done.accept(value);
                if (realtimePending) refreshRealtime();
            }
            public void onError(String error) {
                if (expected != generation || getView() == null) return;
                busy = false; message.setText(error);
                if (content != null && content.getChildCount() == 0) CustomerUi.button(content, "Thử lại", v -> load());
            }
        };
        SharedPreferencesGuestSessionRepository session = new SharedPreferencesGuestSessionRepository(requireContext());
        String token = session.getOrderAccessToken(orderId);
        try { body.put("accessToken", token.isEmpty() ? session.getOrCreateGuestToken() : token); } catch (JSONException ignored) { }
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            RenderBackendClient.postGuest("/orders/reconciliation/" + path, body, callback);
        } else RenderBackendClient.post("/orders/reconciliation/" + path, body, callback);
    }
    private void load() {
        if (busy || content == null) return;
        content.removeAllViews();
        request(orderId.isEmpty() ? "list" : "get", body(), value -> {
            if (orderId.isEmpty()) {
                renderList(value);
            } else { detail(value.optString("role"), value.optJSONObject("order")); CustomerUi.button(content, "Tải lại", v -> load()); }
        });
    }
    private void renderList(JSONObject value) {
        if (listReload != null) content.removeView(listReload);
        JSONArray items = value.optJSONArray("items");
        if ((items == null || items.length() == 0) && content.getChildCount() == 0) CustomerUi.text(content, "Không có đơn đang chờ đối soát.");
        if (items != null) for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i); if (item == null) continue;
            CustomerUi.button(content, item.optString("orderCode") + " • " + EnumLabels.label(item.optString("paymentStatus")), v -> {
                orderId = item.optString("id"); requestId = UUID.randomUUID().toString(); load(); bindRealtime();
            });
        }
        if (value.optBoolean("hasMore")) {
            View more = CustomerUi.button(content, "Xem thêm", null);
            more.setOnClickListener(v -> {
                JSONObject b = body(); try { b.put("cursor", value.optString("cursor")); } catch (JSONException ignored) { }
                request("list", b, next -> { content.removeView(more); renderList(next); });
            });
        }
        listReload = CustomerUi.button(content, "Tải lại", v -> load());
    }
    private void evidence(String action, EditText input) {
        JSONObject b = body();
        try { b.put("action", action); if (action.equals("ACK_RECEIVED")) b.put("confirmPaid", true); if (input != null) b.put("evidenceUrl", CustomerUi.value(input)); }
        catch (JSONException ignored) { }
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle("Xác nhận bằng chứng thực tế")
            .setMessage(action.equals("ACK_RECEIVED") ? "Tôi đã nhận đủ hàng và đã thanh toán tiền mặt khi nhận (nếu có)." : "Chỉ xác nhận sau khi đã bàn giao hoặc thu hồi hàng thực tế. Thao tác sẽ được ghi lịch sử.")
            .setNegativeButton("Hủy", null).setPositiveButton("Xác nhận", (d, w) -> request("evidence", b, result -> load())).show();
    }
    private void detail(String role, JSONObject order) {
        if (order == null) return;
        AppTypography.title(CustomerUi.text(content, order.optString("orderCode"))).setTextSize(20);
        CustomerUi.text(content, "Trạng thái: " + EnumLabels.label(order.optString("status")) + " • " + EnumLabels.label(order.optString("paymentStatus")));
        CustomerUi.text(content, "Tổng tiền: " + java.text.NumberFormat.getCurrencyInstance(java.util.Locale.forLanguageTag("vi-VN")).format(order.optDouble("totalAmount")));
        CustomerUi.text(content, "Bản ghi bàn giao: " + (order.optLong("handoverRecordedAt") > 0 ? new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.forLanguageTag("vi-VN")).format(new java.util.Date(order.optLong("handoverRecordedAt"))) : "Chưa có"));
        proofLink("Bằng chứng bàn giao", order.optString("handoverEvidenceUrl"));
        proofLink("Bằng chứng thu hồi", order.optString("returnEvidenceUrl"));
        CustomerUi.text(content, "Khách xác nhận đã nhận: " + (order.optLong("buyerReceiptAcknowledgedAt") > 0 ? "Đã xác nhận" : "Chưa xác nhận"));
        if (order.optJSONObject("reconciliationResolution") != null || "DELIVERED".equals(order.optString("status")) || "CANCELLED".equals(order.optString("status"))) {
            JSONObject result = order.optJSONObject("reconciliationResolution");
            CustomerUi.text(content, "Đơn đã kết thúc.");
            if (result != null) {
                String mode = result.optString("mode");
                CustomerUi.text(content, "Kết quả: " + (mode.equals("REFUND_CANCEL") ? "Đã ghi nhận hoàn đủ tiền và hủy đơn"
                    : mode.equals("LATE_RECEIPT") ? "Đã nhận hàng, bàn giao trước hạn" : "Đã hủy đơn chưa thanh toán"));
                CustomerUi.text(content, "Kết luận: " + result.optString("reason"));
                proofLink("Bằng chứng đã đối chiếu", result.optString("evidenceUrl"));
                if (mode.equals("REFUND_CANCEL")) CustomerUi.text(content, "Mã hoàn tiền: " + result.optString("refundReference")
                    + " • Số tiền: " + java.text.NumberFormat.getCurrencyInstance(java.util.Locale.forLanguageTag("vi-VN")).format(result.optDouble("refundAmount")));
            }
            return;
        }
        CustomerUi.text(content, "Đối soát lưu bằng chứng thực tế. Hoàn tiền được thực hiện ngoài app rồi Admin mới ghi nhận; trang này không xác nhận tiền chuyển khoản vào.");
        if (role.equals("SELLER")) {
            EditText proof = CustomerUi.input(content, "Đường dẫn HTTPS tới bằng chứng bàn giao/thu hồi", android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI);
            CustomerUi.button(content, "Ghi nhận đã bàn giao trước hạn", v -> evidence("HANDOVER", proof));
            CustomerUi.button(content, "Ghi nhận đã thu hồi hàng", v -> evidence("RETURNED", proof));
        }
        if (!role.equals("ADMIN")) {
            if (!role.equals("SELLER") && order.optLong("handoverRecordedAt") > 0 && order.optLong("buyerReceiptAcknowledgedAt") == 0)
                CustomerUi.button(content, "Xác nhận tôi đã nhận hàng thực tế", v -> evidence("ACK_RECEIVED", null));
            CustomerUi.button(content, "Yêu cầu Admin đối soát", v -> evidence("REQUEST", null));
        } else {
            Spinner mode = new Spinner(requireContext()); String[] labels = {"Đã hoàn đủ tiền — hủy đơn", "Chưa thanh toán — hủy đơn", "Đã bàn giao trước hạn — hoàn tất nhận hàng"};
            mode.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_dropdown_item, labels)); content.addView(mode);
            EditText reason = CustomerUi.input(content, "Kết luận đối soát", android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
            EditText proof = CustomerUi.input(content, "Đường dẫn HTTPS tới bằng chứng đã đối chiếu", android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI);
            EditText reference = CustomerUi.input(content, "Mã giao dịch hoàn tiền (khi hoàn tiền)", android.text.InputType.TYPE_CLASS_TEXT);
            EditText amount = CustomerUi.input(content, "Số tiền đã hoàn thực tế", android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
            CheckBox paid = new CheckBox(requireContext()); paid.setText("Đã đối chiếu bằng chứng hoàn đủ tiền thực tế"); content.addView(paid);
            CheckBox returned = new CheckBox(requireContext()); returned.setText("Đã đối chiếu bằng chứng hàng đã thu hồi về kho"); content.addView(returned);
            CheckBox noTransfer = new CheckBox(requireContext()); noTransfer.setText("Đã đối chiếu sao kê: chưa có tiền chuyển khoản vào (khi hủy đơn chờ chuyển khoản)"); content.addView(noTransfer);
            CustomerUi.button(content, "Chốt đối soát và ghi lịch sử", v -> {
                JSONObject b = body();
                try { b.put("requestId", requestId).put("mode", new String[]{"REFUND_CANCEL", "CANCEL_UNPAID", "LATE_RECEIPT"}[mode.getSelectedItemPosition()])
                    .put("reason", CustomerUi.value(reason)).put("evidenceUrl", CustomerUi.value(proof)).put("refundReference", CustomerUi.value(reference))
                    .put("refundAmount", CustomerUi.value(amount).isEmpty() ? 0 : Double.parseDouble(CustomerUi.value(amount)))
                    .put("confirmRefunded", paid.isChecked()).put("confirmReturned", returned.isChecked()).put("confirmNoTransfer", noTransfer.isChecked()); }
                catch (Exception error) { message.setText("Kiểm tra số tiền hoàn."); return; }
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext()).setTitle("Chốt kết quả đối soát?")
                    .setMessage("Chỉ tiếp tục sau khi đã đối chiếu bằng chứng. Kết quả tiền và tồn kho sẽ được chốt cùng lịch sử Admin.")
                    .setNegativeButton("Hủy", null).setPositiveButton("Chốt kết quả", (d,w) -> request("resolve", b, result -> load())).show();
            });
        }
    }
    private void proofLink(String label, String url) {
        if (!url.startsWith("https://")) return;
        CustomerUi.button(content, "Mở " + label.toLowerCase(java.util.Locale.forLanguageTag("vi-VN")), v -> {
            try { startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))); }
            catch (android.content.ActivityNotFoundException error) { message.setText("Thiết bị chưa có ứng dụng mở đường dẫn."); }
        });
    }
}
