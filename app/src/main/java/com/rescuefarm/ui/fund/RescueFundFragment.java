package com.rescuefarm.ui.fund;

import android.os.Bundle;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import androidx.annotation.*;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.rescuefarm.R;
import com.rescuefarm.service.backend.RenderBackendClient;
import com.rescuefarm.ui.common.AppTypography;
import com.rescuefarm.ui.profile.*;
import com.rescuefarm.service.media.CloudinaryUploadUi;
import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import org.json.*;
import java.util.UUID;
import java.util.Locale;
import java.util.function.Consumer;
import static com.rescuefarm.ui.common.CustomerUi.*;

/** Public donations and role-scoped charity deliveries, backed by the fund API. */
public final class RescueFundFragment extends Fragment {
    private LinearLayout body, jobs;
    private TextView status;
    private View progress, refresh;
    private androidx.core.widget.NestedScrollView scroll;
    private boolean receiptVisible;
    private String role = "GUEST", focus = "", uploadResource = "", uploadType = "", proofUrl = "";
    private JSONObject fund = new JSONObject();
    private SharedPreferences prefs;
    private boolean busy;
    private boolean showingReceipt;
    private final android.os.Handler poller = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable poll = new Runnable() {
        @Override public void run() {
            if (!showingReceipt || getView() == null || !isResumed()) return;
            if (!busy) receipt();
            poller.postDelayed(this, 30000);
        }
    };
    private int generation;
    private View uploadButton;
    private ImageView preview;
    private TextView uploadStatus;
    private Uri cameraUri;
    private final ActivityResultLauncher<Uri> camera = registerForActivityResult(new ActivityResultContracts.TakePicture(), success -> {
        if (success && cameraUri != null) uploadEvidence(cameraUri);
    });
    private void uploadEvidence(Uri uri) {
        if (uri != null && uploadButton != null) CloudinaryUploadUi.upload(this, uri, uploadType,
                uploadResource, 0, uploadButton, uploadStatus, preview, url -> proofUrl = url);
    }
    private final ActivityResultLauncher<String> picker = registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
        uploadEvidence(uri);
    });
    private static final int NUMBER = InputType.TYPE_CLASS_NUMBER;
    private static final int TEXT = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES;
    @Nullable @Override public View onCreateView(@NonNull android.view.LayoutInflater inflater,
            @Nullable ViewGroup parent, @Nullable Bundle saved) {
        View root = inflater.inflate(R.layout.fragment_rescue_fund, parent, false);
        root.findViewById(R.id.fundBack).setOnClickListener(this::navigateBack);
        status = root.findViewById(R.id.fundStatus);
        progress = root.findViewById(R.id.fundProgress);
        refresh = root.findViewById(R.id.fundRefresh);
        refresh.setOnClickListener(this::refreshPage);
        scroll = root.findViewById(R.id.fundScroll);
        body = root.findViewById(R.id.fundBody);
        return root;
    }
    private void navigateBack(View view) {
        if (receiptVisible || body.getTag() != null) load();
        else androidx.navigation.fragment.NavHostFragment.findNavController(this).popBackStack();
    }
    private void refreshPage(View view) { if (receiptVisible) receipt(); else load(); }
    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle saved) {
        busy = false; proofUrl = "";
        prefs = requireContext().getSharedPreferences("rescue_fund_receipts", 0);
        focus = getArguments() == null ? "" : getArguments().getString("deliveryId", "");
        if (FirebaseAuth.getInstance().getCurrentUser() == null) { role = "GUEST"; view.post(this::load); return; }
        ProfileViewModel vm = new ViewModelProvider(this, new ProfileViewModelFactory(requireContext())).get(ProfileViewModel.class);
        vm.getState().observe(getViewLifecycleOwner(), this::profileState); vm.loadProfile();
    }
    private void profileState(ProfileScreenState state) {
        if (state.getStatus() == ProfileScreenState.Status.PROFILE && state.getUser() != null) {
            role = state.getUser().getRole().name(); load();
        } else if (state.getStatus() == ProfileScreenState.Status.ERROR) {
            progress.setVisibility(View.GONE); showStatus(state.getMessage());
        }
    }
    @Override public void onResume() { super.onResume(); poller.removeCallbacks(poll); if (showingReceipt) poller.postDelayed(poll, 30000); }
    @Override public void onPause() { poller.removeCallbacks(poll); super.onPause(); }
    @Override public void onDestroyView() {
        generation++; busy = false; showingReceipt = false; receiptVisible = false; poller.removeCallbacks(poll);
        uploadButton = null; preview = null; uploadStatus = null;
        body = null; jobs = null; status = null; progress = null; refresh = null; scroll = null;
        super.onDestroyView();
    }
    private JSONObject json(Object... pairs) {
        JSONObject value = new JSONObject(); try { for (int i=0; i<pairs.length; i+=2) value.put((String)pairs[i], pairs[i+1]); }
        catch (JSONException error) { throw new IllegalArgumentException(error); } return value;
    }
    private void request(String path, JSONObject payload, Consumer<JSONObject> done) {
        if (busy) return; busy = true; int expected = generation;
        progress.setVisibility(View.VISIBLE); refresh.setEnabled(false); showStatus("");
        RenderBackendClient.Callback callback = new RenderBackendClient.Callback() {
            public void onSuccess(JSONObject value) {
                if (expected != generation || getView() == null) return;
                busy = false; progress.setVisibility(View.GONE); refresh.setEnabled(true);
                showStatus(""); done.accept(value);
            }
            public void onError(String error) {
                if (expected != generation || getView() == null) return;
                busy = false; progress.setVisibility(View.GONE); refresh.setEnabled(true); showStatus(error);
            }
        };
        if (path.startsWith("admin/") || path.equals("deliveries") || path.equals("delivery/action"))
            RenderBackendClient.post("/rescue-fund/" + path, payload, callback);
        else RenderBackendClient.postGuest("/rescue-fund/" + path, payload, callback);
    }
    private String money(double amount) { return String.format(Locale.forLanguageTag("vi-VN"), "%,.0f đ", amount); }
    private String date(long value) { return new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.forLanguageTag("vi-VN")).format(new java.util.Date(value)); }
    private void showStatus(String message) {
        status.setText(message); status.setVisibility(message == null || message.isEmpty() ? View.GONE : View.VISIBLE);
    }
    private void heading(LinearLayout target, String value) { RescueFundUi.section(target, value); }
    private MaterialButton button(LinearLayout target, String value, View.OnClickListener click) {
        return RescueFundUi.action(target, value, false, click);
    }
    private EditText input(LinearLayout target, String hint, int type) {
        TextInputLayout field = new TextInputLayout(requireContext(), null,
                com.google.android.material.R.attr.textInputOutlinedStyle);
        field.setHint(hint);
        field.setBoxCornerRadii(dp(requireContext(), 14), dp(requireContext(), 14),
                dp(requireContext(), 14), dp(requireContext(), 14));
        TextInputEditText edit = new TextInputEditText(field.getContext());
        edit.setInputType(type); edit.setTextSize(16); AppTypography.body(edit);
        edit.setMinHeight(dp(requireContext(), 56));
        field.addView(edit, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(requireContext(), 12); params.bottomMargin = dp(requireContext(), 8);
        target.addView(field, params);
        return edit;
    }
    private void clearPage(boolean detail) {
        showingReceipt = false; receiptVisible = false; poller.removeCallbacks(poll);
        body.removeAllViews(); body.setTag(detail ? Boolean.TRUE : null);
        scroll.scrollTo(0, 0);
    }
    private void load() {
        if (getView() == null) return;
        showingReceipt = false; poller.removeCallbacks(poll);
        request(role.equals("ADMIN") ? "admin/get" : "public", json(), value -> {
            fund = value.optJSONObject("fund"); if (fund == null) fund = new JSONObject();
            clearPage(false); renderOverview();
            if (role.equals("ADMIN")) {
                heading(body, getString(R.string.fund_admin_title));
                LinearLayout admin = RescueFundUi.card(body);
                button(admin, "Cấu hình hũ và điểm thiện nguyện", v -> config());
                button(admin, "Lịch sử biến động", v -> ledger(""));
                button(admin, "Giao dịch SePay cần đối soát", v -> events());
                button(admin, "Điều chỉnh sau đối soát ngân hàng", v -> adjust());
            }
            if (role.equals("ADMIN") || role.equals("SELLER")) {
                heading(body, getString(R.string.fund_delivery_title)); jobs = column(requireContext()); body.addView(jobs);
                if (!focus.isEmpty()) button(body, "Xem tất cả chuyến giao", v -> { focus = ""; load(); });
                loadJobs("");
            }
        });
    }
    private void renderOverview() {
        LinearLayout introduction = RescueFundUi.row(body);
        LinearLayout copy = column(requireContext());
        introduction.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        TextView intro = RescueFundUi.label(copy, getString(R.string.fund_intro), 26, R.color.rescue_on_primary_container, true);
        androidx.core.view.ViewCompat.setAccessibilityHeading(intro, true);
        RescueFundUi.icon(introduction, R.drawable.ic_rescue_fund, 80);
        TextView description = RescueFundUi.label(body, getString(R.string.fund_intro_description), 15,
                R.color.rescue_on_surface_variant, false);
        description.setPadding(0, dp(requireContext(), 4), 0, dp(requireContext(), 20));

        LinearLayout balance = RescueFundUi.card(body);
        balance.setBackground(RescueFundUi.background(requireContext(), R.color.rescue_primary_container, 24, false));
        TextView fundState = RescueFundUi.badge(balance, getString(fund.optBoolean("active") ? R.string.fund_active : R.string.fund_paused), fund.optBoolean("active"));
        fundState.setBackground(RescueFundUi.background(requireContext(), R.color.rescue_surface, 20, false));
        RescueFundUi.label(balance, fund.optString("name", "Hũ giải cứu nông sản"), 18, R.color.rescue_on_primary_container, true);
        RescueFundUi.label(balance, getString(R.string.fund_available), 14, R.color.rescue_primary, false);
        RescueFundUi.label(balance, money(fund.optDouble("available")), 34, R.color.rescue_on_primary_container, true);
        if (!role.equals("ADMIN")) {
            MaterialButton donate = RescueFundUi.action(balance, getString(R.string.fund_donate), true, v -> donateForm());
            donate.setIconResource(R.drawable.ic_heart);
            donate.setEnabled(fund.optBoolean("active"));
            if (!fund.optBoolean("active")) RescueFundUi.label(balance, getString(R.string.fund_paused_description), 14,
                    R.color.rescue_on_surface_variant, false);
        }

        LinearLayout accounting = RescueFundUi.card(body);
        // Stack figures so full VND values and large accessibility fonts always fit.
        RescueFundUi.detail(accounting, R.drawable.ic_nav_orders, getString(R.string.fund_reserved), money(fund.optDouble("reserved")));
        View divider = new View(requireContext()); divider.setBackgroundColor(requireContext().getColor(R.color.rescue_outline));
        accounting.addView(divider, new LinearLayout.LayoutParams(-1, dp(requireContext(), 1)));
        RescueFundUi.detail(accounting, R.drawable.ic_order_step_check, getString(R.string.fund_spent), money(fund.optDouble("spent")));
        if (!role.equals("ADMIN") && !prefs.getString("receipt", "").isEmpty())
            button(accounting, getString(R.string.fund_receipt), v -> receipt());

        heading(body, getString(R.string.fund_destination));
        LinearLayout destination = RescueFundUi.card(body);
        String charity = fund.optString("charityName").trim(), address = fund.optString("address").trim();
        RescueFundUi.detail(destination, R.drawable.ic_home_location,
                charity.isEmpty() ? getString(R.string.fund_destination_empty) : charity,
                address.isEmpty() ? getString(R.string.fund_address_empty) : address);

        renderRanking();
        if (!role.equals("ADMIN")) return;
        heading(body, getString(R.string.fund_how_title));
        LinearLayout steps = RescueFundUi.card(body);
        RescueFundUi.step(steps, "1", getString(R.string.fund_step_donate), getString(R.string.fund_step_donate_description));
        RescueFundUi.step(steps, "2", getString(R.string.fund_step_rescue), getString(R.string.fund_step_rescue_description));
        RescueFundUi.step(steps, "3", getString(R.string.fund_step_deliver), getString(R.string.fund_step_deliver_description));
    }
    private String token() {
        String token = prefs.getString("token", "");
        if (token.isEmpty()) { token = (UUID.randomUUID().toString() + UUID.randomUUID()).replace("-", ""); prefs.edit().putString("token", token).apply(); }
        return token;
    }
    private void donateForm() {
        if (busy) return;
        clearPage(true); heading(body, getString(R.string.fund_amount_title));
        RescueFundUi.label(body, getString(R.string.fund_amount_description), 15, R.color.rescue_on_surface_variant, false);
        LinearLayout contribution = RescueFundUi.card(body);
        EditText amount = input(contribution, getString(R.string.fund_amount_hint), NUMBER);
        ((TextInputLayout) amount.getParent().getParent()).setHelperText(getString(R.string.fund_amount_range));
        ChipGroup presets = new ChipGroup(requireContext());
        presets.setSingleSelection(true);
        contribution.addView(presets, new LinearLayout.LayoutParams(-1, -2));
        for (long preset : new long[]{20000, 50000, 100000, 200000}) {
            Chip chip = new Chip(requireContext()); chip.setId(View.generateViewId());
            chip.setText(money(preset)); chip.setCheckable(true); chip.setEnsureMinTouchTargetSize(true);
            chip.setOnClickListener(v -> { amount.setText(String.valueOf(preset)); amount.setSelection(amount.length()); });
            presets.addView(chip);
        }
        amount.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                int selected = presets.getCheckedChipId();
                if (selected != View.NO_ID) {
                    Chip chip = presets.findViewById(selected);
                    try { if (!chip.getText().toString().equals(money(Long.parseLong(s.toString())))) presets.clearCheck(); }
                    catch (NumberFormatException ignored) { presets.clearCheck(); }
                }
            }
            public void afterTextChanged(android.text.Editable s) { }
        });
        LinearLayout privacy = RescueFundUi.card(body);
        heading(privacy, getString(R.string.fund_privacy_title));
        CheckBox shareName = new com.google.android.material.checkbox.MaterialCheckBox(requireContext());
        shareName.setText("Hiển thị tên trên bảng xếp hạng sẻ chia");
        AppTypography.body(shareName); shareName.setTextSize(15); shareName.setSingleLine(false);
        shareName.setMinHeight(dp(requireContext(), 48));
        privacy.addView(shareName, new LinearLayout.LayoutParams(-1, -2));
        EditText supporterName = input(privacy, "Tên hiển thị", TEXT);
        View nameField = (View) supporterName.getParent().getParent();
        nameField.setVisibility(View.GONE);
        shareName.setOnCheckedChangeListener((buttonView, checked) ->
                nameField.setVisibility(checked ? View.VISIBLE : View.GONE));
        RescueFundUi.label(privacy, getString(R.string.fund_privacy_description), 14, R.color.rescue_on_surface_variant, false);
        RescueFundUi.label(body, getString(R.string.fund_qr_note), 14, R.color.rescue_on_surface_variant, false);
        String key = UUID.randomUUID().toString();
        RescueFundUi.action(body, "Xác nhận và xem QR", true, v -> {
            try { long total = Long.parseLong(value(amount));
                if (total < 10000 || total > 10000000) {
                    amount.setError(getString(R.string.fund_amount_error)); amount.requestFocus(); return;
                }
                if (shareName.isChecked() && value(supporterName).length() < 2) {
                    supporterName.setError("Nhập tên hiển thị hoặc bỏ chọn công khai tên."); return;
                }
                request("donate", json("amount", total, "requestId", key, "accessToken", token(),
                        "shareName", shareName.isChecked(), "supporterName", value(supporterName)), result -> {
                    JSONObject d = result.optJSONObject("donation"); if (d == null) return;
                    prefs.edit().putString("receipt", d.optString("id")).apply(); showReceipt(d);
                });
            } catch (NumberFormatException error) { amount.setError("Nhập số tiền nguyên hợp lệ."); }
        }); button(body, "Quay về hũ", v -> load());
    }

    private void renderRanking() {
        heading(body, "Bảng xếp hạng sẻ chia");
        LinearLayout ranking = RescueFundUi.card(body);
        ranking.setPadding(0, 0, 0, dp(requireContext(), 12));
        RescueFundUi.label(body, "Xếp theo số tiền từng lượt ủng hộ · tối đa 50 lượt", 12,
                R.color.rescue_on_surface_variant, false);
        LinearLayout content = column(requireContext());
        ranking.addView(content, new LinearLayout.LayoutParams(-1, -2));
        RescueFundUi.label(content, "Đang tải bảng xếp hạng…", 14, R.color.rescue_on_surface_variant, false);
        loadRanking(ranking, content);
    }
    private void loadRanking(LinearLayout ranking, LinearLayout content) {
        int expected = generation;
        RenderBackendClient.postGuest("/rescue-fund/ranking", json(), new RenderBackendClient.Callback() {
            public void onSuccess(JSONObject response) {
                if (getView() == null || expected != generation || ranking.getParent() != body) return;
                content.removeAllViews();
                JSONArray items = response.optJSONArray("items");
                if (items == null || items.length() == 0) {
                    RescueFundUi.label(content, "Chưa có lượt ủng hộ đã xác nhận.", 14, R.color.rescue_on_surface_variant, false);
                    return;
                }
                LinearLayout podium = RescueFundUi.rankingTop(content);
                for (int position : new int[]{1, 0, 2}) {
                    JSONObject item = items.optJSONObject(position);
                    String name = item == null ? null : item.optString("name", "").trim();
                    RescueFundUi.podium(podium, position + 1,
                            item == null ? null : name.isEmpty() ? "Người ủng hộ ẩn danh" : name,
                            item == null ? null : money(item.optDouble("amount")));
                }
                for (int i = 3; i < items.length(); i++) {
                    JSONObject item = items.optJSONObject(i);
                    if (item != null) RescueFundUi.rankRow(content, i + 1,
                            item.optString("name", "Người ủng hộ ẩn danh"), money(item.optDouble("amount")));
                }
                TextView thanks = RescueFundUi.label(content, "Cảm ơn bạn đã cùng sẻ chia", 13, R.color.rescue_on_surface_variant, false);
                thanks.setGravity(Gravity.CENTER);
                thanks.setPadding(dp(requireContext(), 12), dp(requireContext(), 16), dp(requireContext(), 12), dp(requireContext(), 8));
            }
            public void onError(String error) {
                if (getView() == null || expected != generation || ranking.getParent() != body) return;
                content.removeAllViews();
                RescueFundUi.label(content, "Chưa tải được bảng xếp hạng. Vui lòng thử lại.", 14,
                        R.color.rescue_on_surface_variant, false);
                button(content, "Thử lại", v -> { v.setEnabled(false); loadRanking(ranking, content); });
            }
        });
    }
    private void receipt() {
        request("receipt", json("donationId", prefs.getString("receipt", ""), "accessToken", token()), result -> showReceipt(result.optJSONObject("donation")));
    }
    private void showReceipt(JSONObject d) {
        if (d == null) return;
        int previousScroll = receiptVisible ? scroll.getScrollY() : 0;
        boolean paid = d.optString("status").equals("PAID");
        boolean pending = !paid && d.optLong("expiresAt") > System.currentTimeMillis();
        clearPage(true); receiptVisible = true; showingReceipt = pending;
        poller.removeCallbacks(poll); if (showingReceipt && isResumed()) poller.postDelayed(poll, 30000);
        heading(body, getString(paid ? R.string.fund_paid_title : pending ? R.string.fund_qr_title : R.string.fund_receipt_expired));
        if (paid || pending) RescueFundUi.label(body, getString(paid ? R.string.fund_paid_description : R.string.fund_qr_description),
                15, R.color.rescue_on_surface_variant, false);
        LinearLayout receipt = RescueFundUi.card(body);
        RescueFundUi.badge(receipt, paid ? "Đã nhận ủng hộ" : pending ? "Chờ xác nhận chuyển khoản" : "Đã hết thời gian sử dụng", paid);
        TextView total = RescueFundUi.label(receipt, money(d.optDouble("amount")), 32, R.color.rescue_primary, true);
        total.setGravity(Gravity.CENTER);
        if (pending) {
            ImageView qr = new ImageView(requireContext());
            qr.setBackgroundColor(requireContext().getColor(R.color.rescue_surface));
            receipt.addView(qr, new LinearLayout.LayoutParams(-1, dp(requireContext(), 280)));
            qr.setContentDescription("QR chuyển khoản ủng hộ"); Glide.with(this).load(d.optString("qrUrl")).fitCenter().into(qr);
            RescueFundUi.label(receipt, "QR dùng đến " + date(d.optLong("expiresAt")), 14, R.color.rescue_on_surface_variant, false);
        }
        LinearLayout details = RescueFundUi.card(body);
        heading(details, getString(R.string.fund_transfer_details));
        receiptField(details, getString(R.string.fund_account_name), d.optString("accountName"));
        receiptField(details, getString(R.string.fund_account_number), d.optString("accountNumber"));
        receiptField(details, getString(R.string.fund_transfer_content), d.optString("transferContent"));
        if (pending) RescueFundUi.label(body, "Trạng thái tự cập nhật mỗi 15 giây. Nếu đã chuyển tiền, vui lòng chờ xác nhận và không chuyển lại.",
                14, R.color.rescue_on_surface_variant, false);
        else if (!paid) text(body, "QR đã hết thời gian sử dụng. Nếu đã chuyển, giữ mã giao dịch để đối soát, không chuyển lần nữa.");
        button(body, "Làm mới trạng thái", v -> receipt()); button(body, "Về hũ", v -> load());
        if (previousScroll > 0) scroll.post(() -> { if (getView() != null && receiptVisible) scroll.scrollTo(0, previousScroll); });
    }
    private void receiptField(LinearLayout parent, String label, String value) {
        RescueFundUi.label(parent, label, 13, R.color.rescue_on_surface_variant, false);
        TextView content = RescueFundUi.label(parent, value, 17, R.color.rescue_on_surface, true);
        content.setTextIsSelectable(true);
    }
    private void config() {
        clearPage(true); heading(body, "Cấu hình hũ");
        EditText name = field("Tên hũ", "name", TEXT), charity = field("Tổ chức nhận", "charityName", TEXT);
        text(body, "Địa chỉ nơi nhận");
        com.rescuefarm.ui.common.VietnamAddressView address = new com.rescuefarm.ui.common.VietnamAddressView(requireContext());
        body.addView(address, new LinearLayout.LayoutParams(-1, -2));
        address.setFormattedAddress(fund.optString("address", ""));
        EditText phone = field("Điện thoại nơi nhận", "phone", InputType.TYPE_CLASS_PHONE);
        EditText daily = field("Ngân sách mỗi ngày (VND)", "dailyBudget", NUMBER), seller = field("Tối đa mỗi Seller/ngày (VND)", "perSellerBudget", NUMBER);
        if (value(daily).isEmpty()) daily.setText("500000"); if (value(seller).isEmpty()) seller.setText("100000");
        Spinner idle = hours("Không có giao dịch mua thành công trong", fund.optInt("idleHours", 12));
        Spinner expiry = hours("Còn hạn sử dụng tối đa", fund.optInt("expiryHours", 24));
        Switch active = new Switch(requireContext()); active.setText("Mở hũ nhận ủng hộ và phân bổ"); active.setChecked(fund.optBoolean("active")); body.addView(active);
        text(body, "Tài khoản nhận và kết nối SePay được cấu hình bảo mật trên backend. Thay đổi địa điểm không sửa chuyến đã phân bổ.");
        button(body, "Lưu cấu hình", v -> {
            if (!address.validate()) return;
            // Compatibility with deployed backends requiring numeric coordinates.
            // Zero means unspecified; map navigation uses the formatted address.
            try { request("admin/save", json("name", value(name), "charityName", value(charity), "address", address.formattedAddress(), "phone", value(phone),
                    "latitude", 0, "longitude", 0,
                    "dailyBudget", Long.parseLong(value(daily)), "perSellerBudget", Long.parseLong(value(seller)),
                    "idleHours", idle.getSelectedItemPosition() == 0 ? 12 : 24, "expiryHours", expiry.getSelectedItemPosition() == 0 ? 12 : 24,
                    "active", active.isChecked()), result -> load());
            } catch (NumberFormatException error) { showStatus("Kiểm tra số tiền."); }
        }); button(body, "Hủy chỉnh sửa", v -> load());
    }
    private EditText field(String hint, String key, int type) { EditText field = input(body, hint, type); field.setText(fund.optString(key, "")); return field; }
    private Spinner hours(String label, int selected) {
        text(body, label); Spinner spinner = new Spinner(requireContext()); spinner.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_dropdown_item, new String[]{"12 giờ", "24 giờ"}));
        spinner.setSelection(selected == 24 ? 1 : 0); body.addView(spinner); return spinner;
    }
    private void ledger(String cursor) {
        request("admin/ledger", json("cursor", cursor), result -> {
            if (cursor.isEmpty()) { clearPage(true); heading(body, "Sổ biến động hũ"); }
            JSONArray rows = result.optJSONArray("items");
            if (rows != null) for (int i=0;i<rows.length();i++) { JSONObject row=rows.optJSONObject(i); if(row == null) continue;
                LinearLayout entry = RescueFundUi.card(body);
                heading(entry, ledgerLabel(row.optString("kind")));
                RescueFundUi.label(entry, money(row.optDouble("amount")), 22, R.color.rescue_primary, true);
                text(entry, date(row.optLong("createdAt")) + "\n" + row.optString("reason")); }
            if (!result.optString("cursor").isEmpty()) button(body, "Xem thêm biến động", v -> {v.setEnabled(false); ledger(result.optString("cursor"));});
            button(body, "Về hũ", v -> load());
        });
    }
    private void events() { events(""); }
    private void events(String cursor) {
        request("admin/events", json("cursor", cursor), result -> { if(cursor.isEmpty()){ clearPage(true); heading(body, "Giao dịch cần đối soát"); }
            text(body, "Không tự cộng vào hũ khi sai số tiền, nội dung hoặc thời gian. Đối chiếu sao kê; nếu hoàn trả hoặc điều chỉnh thì lưu bằng chứng ngân hàng.");
            JSONArray rows = result.optJSONArray("items"); if (rows != null) for (int i=0;i<rows.length();i++) { JSONObject row=rows.optJSONObject(i); if(row!=null) text(body, "SePay #"+row.optString("id")+" · "+money(row.optDouble("amount"))+"\n"+row.optString("reason")+"\nMã ngân hàng: "+row.optString("bankReference")+"\nNội dung: "+row.optString("content")+"\n"+row.optString("transactionDate")); }
            if(!result.optString("cursor").isEmpty())button(body,"Xem thêm giao dịch",v->{v.setEnabled(false);events(result.optString("cursor"));});
            button(body, "Về hũ", v -> load()); });
    }
    private void upload(String type, String resource) {
        proofUrl = ""; uploadType = type; uploadResource = resource;
        uploadStatus = text(body, "Chưa có ảnh bằng chứng"); preview = new ImageView(requireContext());
        body.addView(preview, new LinearLayout.LayoutParams(-1, dp(requireContext(),180)));
        uploadButton = button(body, "Chọn ảnh bằng chứng", v -> picker.launch("image/*"));
        if (type.equals("fundDelivery")) button(body, "Chụp ảnh bàn giao", v -> {
            try {
                java.io.File folder = new java.io.File(requireContext().getCacheDir(), "fund-evidence");
                if (!folder.exists() && !folder.mkdirs()) throw new java.io.IOException();
                java.io.File photo = java.io.File.createTempFile("handover-", ".jpg", folder);
                cameraUri = androidx.core.content.FileProvider.getUriForFile(requireContext(), requireContext().getPackageName() + ".fund-evidence", photo);
                camera.launch(cameraUri);
            } catch (java.io.IOException | android.content.ActivityNotFoundException error) {
                showStatus("Không mở được máy ảnh. Bạn có thể chọn ảnh đã chụp từ thư viện.");
            }
        });
    }
    private void adjust() {
        clearPage(true); heading(body, "Đối soát số dư thực tế"); String key=UUID.randomUUID().toString();
        text(body, "Đây là bút toán đối soát với ngân hàng, không phải xác nhận tiền ủng hộ thủ công. Số tiền dương để tăng, âm để giảm số dư có thể phân bổ.");
        EditText amount=input(body,"Chênh lệch (VND)",NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED), reason=input(body,"Lý do đối soát (ít nhất 10 ký tự)",TEXT);
        upload("fundReceipt",key);
        button(body,"Ghi bút toán đối soát",v -> confirm("Ghi điều chỉnh đúng với sao kê ngân hàng?", () -> {
            try { request("admin/adjust",json("requestId",key,"amount",Long.parseLong(value(amount)),"reason",value(reason),"proofUrl",proofUrl), r->load()); }
            catch(NumberFormatException error){amount.setError("Nhập số tiền nguyên.");}
        })); button(body,"Hủy",v->load());
    }
    private void loadJobs(String cursor) {
        request("deliveries", json("cursor",cursor,"deliveryId",focus), result -> {
            JSONArray rows=result.optJSONArray("items"); if(rows!=null) for(int i=0;i<rows.length();i++){JSONObject j=rows.optJSONObject(i);if(j!=null)job(j);}
            if((rows==null||rows.length()==0) && cursor.isEmpty()) {
                LinearLayout empty = RescueFundUi.card(jobs);
                RescueFundUi.detail(empty, R.drawable.ic_admin_shipping, getString(R.string.fund_delivery_empty),
                        getString(R.string.fund_delivery_empty_description));
            }
            if(!result.optString("cursor").isEmpty())button(jobs,"Xem thêm chuyến",v->{v.setEnabled(false);loadJobs(result.optString("cursor"));});
        });
    }
    private String state(String state) {
        switch(state){case "RESERVED":return "Đang giữ tiền và hàng";case "ACCEPTED":return "Seller đã nhận chuyến";
            case "DELIVERED":return "Chờ kiểm tra ảnh giao";case "VERIFIED":return "Đã xác minh giao — chờ chi tiền";
            case "PAID":return "Admin đã ghi chuyển tiền";case "CANCELLED":return "Đã hủy và giải phóng tiền";default:return state;}
    }
    private String ledgerLabel(String kind) {
        switch(kind){case "SETTINGS":return "Đổi cấu hình hũ";case "DONATION":return "Nhận ủng hộ qua SePay";
            case "RESERVE":return "Giữ tiền cho chuyến";case "RELEASE":return "Hoàn phần tiền đang giữ";
            case "ACCEPT":return "Seller nhận chuyến";case "DELIVER":return "Seller gửi ảnh giao hàng";
            case "VERIFY_DELIVERY":return "Đã xác minh giao";case "PAYOUT":return "Ghi khoản đã chuyển cho Seller";
            case "BANK_RECONCILIATION":return "Điều chỉnh sau đối soát ngân hàng";default:return "Biến động hũ";}
    }
    private void job(JSONObject j) {
        LinearLayout box=RescueFundUi.card(jobs);
        RescueFundUi.badge(box, state(j.optString("status")), !j.optString("status").equals("CANCELLED"));
        heading(box,j.optString("productName"));
        RescueFundUi.label(box, j.optDouble("quantity")+" "+j.optString("unit")+" · "+money(j.optDouble("amount")),
                20, R.color.rescue_primary, true);
        text(box,j.optString("fundName")+"\n"+j.optString("campaignTitle"));
        RescueFundUi.detail(box, R.drawable.ic_admin_shipping, "Giao trước", date(j.optLong("deadline")));
        RescueFundUi.detail(box, R.drawable.ic_home_location, j.optString("charityName"), j.optString("address")+"\n"+j.optString("phone"));
        button(box,"Xem vị trí nhận",v->{Intent map=new Intent(Intent.ACTION_VIEW,Uri.parse("geo:0,0?q="+Uri.encode(j.optString("address"))));
            try{startActivity(map);}catch(android.content.ActivityNotFoundException error){showStatus("Thiết bị chưa có ứng dụng bản đồ.");}});
        if(!j.optString("proofUrl").isEmpty()){ImageView image=new ImageView(requireContext());box.addView(image,new LinearLayout.LayoutParams(-1,dp(requireContext(),180)));Glide.with(this).load(j.optString("proofUrl")).fitCenter().into(image);}
        String s=j.optString("status");
        if(role.equals("SELLER")) {
            if(s.equals("RESERVED"))button(box,"Nhận chuyến giao",v->act(j,"ACCEPT",json()));
            if(s.equals("ACCEPTED"))button(box,"Gửi ảnh xác nhận giao",v->{clearPage(true);heading(body,"Bằng chứng giao hàng");upload("fundDelivery",j.optString("id"));EditText note=input(body,"Ghi chú bàn giao",TEXT);
                button(body,"Gửi bằng chứng",x->act(j,"DELIVER",json("proofUrl",proofUrl,"note",value(note))));button(body,"Hủy",x->load());});
        } else if(role.equals("ADMIN")) {
            if(s.equals("DELIVERED"))button(box,"Xác minh đã giao trước hạn",v->confirm("Đã kiểm tra ảnh và xác nhận với nơi nhận?",()->act(j,"VERIFY",json())));
            if(s.equals("VERIFIED"))button(box,"Ghi tiền đã chuyển cho Seller",v->payout(j));
        }
        if(s.equals("RESERVED")||s.equals("ACCEPTED")||role.equals("ADMIN")&&s.equals("DELIVERED"))button(box,"Hủy chuyến có lý do",v->{EditText reason=new EditText(requireContext());
            new MaterialAlertDialogBuilder(requireContext()).setTitle("Lý do hủy chuyến").setView(reason).setNegativeButton("Đóng",null)
                .setPositiveButton("Hủy chuyến",(d,w)->act(j,"CANCEL",json("note",value(reason)))).show();});
        if(s.equals("VERIFIED")||s.equals("PAID"))text(box,"TK Seller: "+j.optString("sellerBankCode")+" · "+j.optString("sellerBankAccountNumber")+"\n"+j.optString("sellerBankAccountName"));
        if(s.equals("PAID")){text(box,"Mã chi: "+j.optString("bankReference"));ImageView receipt=new ImageView(requireContext());box.addView(receipt,new LinearLayout.LayoutParams(-1,dp(requireContext(),180)));Glide.with(this).load(j.optString("payoutProofUrl")).fitCenter().into(receipt);}
    }
    private void act(JSONObject j,String action,JSONObject b){try{b.put("deliveryId",j.optString("id")).put("action",action);}catch(JSONException ignored){}request("delivery/action",b,r->load());}
    private void payout(JSONObject j){clearPage(true);heading(body,"Ghi khoản đã chuyển ngân hàng");text(body,"Chuyển ngoài app đúng "+money(j.optDouble("amount"))+" tới "+j.optString("sellerBankCode")+" · "+j.optString("sellerBankAccountNumber")+" · "+j.optString("sellerBankAccountName")+". Chỉ ghi sau khi ngân hàng báo thành công.");
        EditText reference=input(body,"Mã giao dịch ngân hàng",TEXT);upload("fundReceipt",j.optString("id"));
        button(body,"Lưu bằng chứng đã chuyển",v->confirm("Xác nhận đã chuyển đúng số tiền cho Seller?",()->act(j,"PAY",json("amount",j.optLong("amount"),"bankReference",value(reference),"proofUrl",proofUrl))));button(body,"Hủy",v->load());}
    private void confirm(String question,Runnable action){new MaterialAlertDialogBuilder(requireContext()).setMessage(question).setNegativeButton("Đóng",null).setPositiveButton("Xác nhận",(d,w)->action.run()).show();}
}
