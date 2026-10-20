package com.rescuefarm.ui.rescue;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
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
import androidx.navigation.fragment.NavHostFragment;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.tabs.TabLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.rescuefarm.MainActivity;
import com.rescuefarm.R;
import com.rescuefarm.domain.enums.UserRole;
import com.rescuefarm.service.location.LocationProvider;
import com.rescuefarm.service.location.LocationProviderFactory;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import static com.rescuefarm.ui.rescue.RescueUi.*;

public final class RescueHubFragment extends Fragment {
    private RescueProgramsViewModel model;
    private LinearLayout content;
    private Spinner type;
    private TabLayout tabs;
    private LocationProvider location;
    private Double latitude, longitude;
    private String subscriptionId = "", actor = "";
    private boolean nearby, enablingAlerts;
    private boolean configuringTabs;
    private UserRole configuredRole;
    private final Handler clock = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (getView() != null && model != null && model.state().getValue() != null && !model.state().getValue().loading) model.refresh();
            clock.postDelayed(this, 60000);
        }
    };
    private final FirebaseAuth.AuthStateListener auth = firebase -> {
        String current = firebase.getCurrentUser() == null ? "" : firebase.getCurrentUser().getUid();
        if (!actor.equals(current) && model != null) {
            actor = current; model.invalidate(); if (getView() != null) browse("catalog");
        }
    };
    private final ActivityResultLauncher<String[]> permissions = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                if (Boolean.TRUE.equals(result.get(Manifest.permission.ACCESS_FINE_LOCATION))
                        || Boolean.TRUE.equals(result.get(Manifest.permission.ACCESS_COARSE_LOCATION))) locate();
                else tell("Chưa có quyền vị trí. Bạn vẫn có thể khám phá tất cả gói.");
            });
    private final ActivityResultLauncher<String> notifications = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), result -> {
                if (result) enableAlerts(); else tell("Chưa bật thông báo của hệ điều hành.");
            });

    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup parent, @Nullable Bundle state) {
        return inflater.inflate(R.layout.fragment_rescue_hub, parent, false);
    }
    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle saved) {
        super.onViewCreated(view, saved);
        actor = uid(); content = view.findViewById(R.id.rescueContent); type = view.findViewById(R.id.rescueType); tabs = view.findViewById(R.id.rescueTabs);
        model = new ViewModelProvider(this).get(RescueProgramsViewModel.class);
        location = LocationProviderFactory.create(requireContext());
        if (saved != null) {
            nearby = saved.getBoolean("nearby"); subscriptionId = saved.getString("subscriptionId", "");
            if (saved.containsKey("latitude")) { latitude = saved.getDouble("latitude"); longitude = saved.getDouble("longitude"); }
        }
        view.findViewById(R.id.rescueBack).setOnClickListener(v -> {
            if (model.state().getValue() != null && "detail".equals(model.state().getValue().screen)) browse("catalog");
            else NavHostFragment.findNavController(this).navigateUp();
        });
        view.findViewById(R.id.rescueRefresh).setOnClickListener(v -> model.refresh());
        view.findViewById(R.id.rescueCreate).setVisibility(seller() ? View.VISIBLE : View.GONE);
        view.findViewById(R.id.rescueCreate).setOnClickListener(v -> NavHostFragment.findNavController(this).navigate(R.id.rescueOfferEditorFragment));
        view.findViewById(R.id.rescueNearby).setOnClickListener(v -> {
            enablingAlerts = false;
            if (nearby) { nearby = false; browse("catalog"); } else requestLocation();
        });
        view.findViewById(R.id.rescueAlerts).setOnClickListener(v -> alertsDialog());
        configureTabs();
        tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            public void onTabSelected(TabLayout.Tab tab) { if (!configuringTabs) selectTab(tab.getPosition()); }
            public void onTabUnselected(TabLayout.Tab tab) { }
            public void onTabReselected(TabLayout.Tab tab) { if (!configuringTabs) selectTab(tab.getPosition()); }
        });
        type.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            public void onItemSelected(android.widget.AdapterView<?> parent, View selected, int position, long id) {
                if (model.state().getValue() != null && "catalog".equals(model.state().getValue().screen)) browse("catalog");
            }
            public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        });
        model.state().observe(getViewLifecycleOwner(), this::render);
        if (requireActivity() instanceof MainActivity) ((MainActivity) requireActivity()).observeActorRole().observe(getViewLifecycleOwner(), value -> {
            if (configuredRole != value) { configureTabs(); view.findViewById(R.id.rescueCreate).setVisibility(seller() ? View.VISIBLE : View.GONE); model.refresh(); }
        });
        String offerId = getArguments() == null ? "" : getArguments().getString("offerId", "");
        if (saved == null && !offerId.isEmpty()) openOffer(offerId, "");
        else if (saved == null && getArguments() != null && "subscriptions".equals(getArguments().getString("tab")) && !seller() && !admin()) {
            tabs.selectTab(tabs.getTabAt(2));
        } else model.restore();
    }
    private void configureTabs() {
        configuredRole = role(); configuringTabs = true;
        int position = Math.max(0, tabs.getSelectedTabPosition());
        tabs.removeAllTabs();
        String[] names = admin() ? new String[]{"Khám phá", "Xác minh tổ chức"}
                : seller() ? new String[]{"Khám phá", "Gói cửa hàng", "Tác động"}
                : new String[]{"Khám phá", "Suất của tôi", "Gói định kỳ", "Tổ chức từ thiện"};
        for (String name : names) tabs.addTab(tabs.newTab().setText(name));
        tabs.selectTab(tabs.getTabAt(Math.min(position, names.length - 1))); configuringTabs = false;
    }
    private String uid() { return FirebaseAuth.getInstance().getCurrentUser() == null ? "" : FirebaseAuth.getInstance().getCurrentUser().getUid(); }
    private UserRole role() { return requireActivity() instanceof MainActivity ? ((MainActivity) requireActivity()).getResolvedActorRole() : null; }
    private boolean seller() { return role() == UserRole.SELLER; }
    private boolean admin() { return role() == UserRole.ADMIN; }
    private boolean customer() { return role() == UserRole.CUSTOMER && !uid().isEmpty(); }
    private boolean login() {
        if (!uid().isEmpty()) return true;
        new MaterialAlertDialogBuilder(requireContext()).setTitle("Đăng nhập để tham gia giải cứu")
                .setMessage("Guest có thể khám phá gói; đăng nhập Customer để giữ suất và quản lý đăng ký.")
                .setPositiveButton("Đăng nhập", (d, w) -> NavHostFragment.findNavController(this).navigate(R.id.loginFragment))
                .setNegativeButton("Đóng", null).show(); return false;
    }
    private void selectTab(int position) {
        if (position == 0) browse("catalog");
        else if (login()) {
            if (admin()) browse("organizations");
            else if (position == 1) browse("mine");
            else if (seller()) browse("impact");
            else browse(position == 2 ? "subscriptions" : "organizations");
        }
    }
    private void browse(String screen) {
        String path = screen.equals("subscriptions") ? "subscriptions/list" : screen.equals("organizations") ? "organizations/list" : screen;
        JSONObject query = body();
        if (screen.equals("catalog")) {
            query = body("type", TYPES[type.getSelectedItemPosition()]);
            if (nearby && latitude != null) query = body("type", TYPES[type.getSelectedItemPosition()], "nearby", true,
                    "latitude", latitude, "longitude", longitude, "radiusKm", 5);
        } else if (screen.equals("impact")) query = body("sellerId", uid());
        model.load(screen, path, query);
    }
    private void openOffer(String id, String subscription) {
        subscriptionId = subscription; model.load("detail", "detail", body("offerId", id));
    }
    private void render(RescueProgramsViewModel.State state) {
        View root = getView(); if (root == null) return;
        root.findViewById(R.id.rescueProgress).setVisibility(state.loading ? View.VISIBLE : View.GONE);
        root.findViewById(R.id.rescueRefresh).setEnabled(!state.loading);
        TextView message = root.findViewById(R.id.rescueMessage);
        message.setText(state.message); message.setVisibility(state.message.isEmpty() ? View.GONE : View.VISIBLE);
        boolean catalog = state.screen.equals("catalog");
        type.setVisibility(catalog ? View.VISIBLE : View.GONE);
        root.findViewById(R.id.rescueActions).setVisibility(catalog ? View.VISIBLE : View.GONE);
        ((TextView) root.findViewById(R.id.rescueNearby)).setText(nearby ? "Xem tất cả khu vực" : getString(R.string.rescue_nearby));
        content.removeAllViews();
        if (state.screen.equals("detail")) {
            JSONObject offer = state.value.optJSONObject("offer"); if (offer != null) detail(offer, state.value);
        } else if (state.screen.equals("impact")) {
            JSONObject impact = state.value.optJSONObject("impact"); if (impact != null) impact(impact);
        } else {
            if (state.screen.equals("organizations") && admin()) {
                for (String filter : new String[]{"PENDING", "VERIFIED", "REJECTED"}) button(content, status(filter), () ->
                        model.load("organizations", "organizations/list", body("status", filter)));
            }
            JSONArray items = state.value.optJSONArray("items");
            if (items != null) for (int i = 0; i < items.length(); i++) {
                JSONObject value = items.optJSONObject(i); if (value == null) continue;
                if (state.screen.equals("subscriptions")) subscription(value);
                else if (state.screen.equals("organizations")) organization(value);
                else if (state.screen.equals("mine") && !seller()) claim(value, value.optJSONObject("offer"), false);
                else offerCard(value);
            }
            if (state.screen.equals("organizations") && customer()) button(content, "Gửi hồ sơ tổ chức / cập nhật hồ sơ", this::applyOrganization);
            if (state.value.optBoolean("hasMore")) button(content, "Xem thêm", model::more).setEnabled(!state.loading);
        }
        if (!state.loading && content.getChildCount() == 0 && state.message.isEmpty()) text(content, getString(R.string.rescue_empty), false);
    }
    private void offerCard(JSONObject offer) {
        LinearLayout card = card(content);
        text(card, type(offer.optString("type")) + " · " + status(offer.optString("status")), false);
        text(card, offer.optString("title"), true);
        text(card, offer.optString("categoryLabel") + " · " + offer.optString("sellerName"), false);
        text(card, money(offer.optDouble("unitPrice")) + " / " + offer.optString("unitLabel")
                + " · còn " + offer.optInt("availableUnits") + " suất", false);
        text(card, "Nhận: " + date(offer.optLong("pickupStartAt")) + " – " + date(offer.optLong("pickupEndAt")), false);
        if (offer.has("distanceKm")) text(card, String.format(java.util.Locale.forLanguageTag("vi-VN"), "Cách bạn %.1f km", offer.optDouble("distanceKm")), false);
        button(card, "Xem gói giải cứu", () -> openOffer(offer.optString("id"), ""));
    }
    private void detail(JSONObject offer, JSONObject response) {
        LinearLayout card = card(content);
        text(card, type(offer.optString("type")) + " · " + status(offer.optString("status")), false);
        text(card, offer.optString("title"), true); text(card, offer.optString("description"), false);
        text(card, "Phân loại: " + offer.optString("categoryLabel") + "\nThành phần / lưu ý: " + offer.optString("contentsDisclosure"), false);
        text(card, "Giá trị gốc: " + money(offer.optDouble("originalUnitPrice")) + "\nGiá giải cứu: "
                + money(offer.optDouble("unitPrice")) + " / " + offer.optString("unitLabel"), false);
        text(card, "Còn " + offer.optInt("availableUnits") + " / " + offer.optInt("totalUnits") + " suất · giới hạn " + offer.optInt("maxUnitsPerClaim") + " suất/khách", false);
        text(card, "Hạn đăng ký: " + date(offer.optLong("closesAt")) + "\nNhận từ " + date(offer.optLong("pickupStartAt"))
                + " đến " + date(offer.optLong("pickupEndAt")) + "\n" + offer.optString("pickupAddress"), false);
        if (offer.optString("type").equals("GROUP_BUY")) text(card,
                (offer.optBoolean("groupSucceeded") ? "Đã đạt ngưỡng!" : "Đang gom nhóm") + "\n"
                        + offer.optInt("participantCount") + " / " + offer.optInt("minPeople") + " người · "
                        + (offer.optInt("claimedUnits") + offer.optInt("completedUnits")) + " / " + offer.optInt("minUnits")
                        + " suất\nKhi nhóm đạt ngưỡng, cam kết không thể tự rút. Chưa thu tiền trước.", false);
        button(card, "Mở điểm nhận trên bản đồ", () -> map(offer));
        button(card, "Chia sẻ gói giải cứu", () -> {
            Intent share = new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,
                    offer.optString("title") + "\n" + offer.optString("pickupAddress") + "\nrescuefarm://rescue/" + offer.optString("id"));
            startActivity(Intent.createChooser(share, "Rủ bạn cùng giải cứu"));
        });
        boolean owner = seller() && uid().equals(offer.optString("sellerId"));
        JSONArray claims = response.optJSONArray("claims");
        boolean registered = claims != null && claims.length() > 0;
        if (owner || admin()) button(card, "Dừng gói và trả tồn kho chưa nhận", () -> confirm("Dừng gói?",
                "Các suất chưa nhận sẽ bị hủy và trả tồn kho. Suất đã nhận được giữ trong lịch sử.",
                () -> model.action("close", body("offerId", offer.optString("id")), model::refresh)));
        if (!owner && !admin() && "OPEN".equals(offer.optString("status")) && offer.optLong("closesAt") > System.currentTimeMillis() && !registered) {
            button(card, offer.optString("type").equals("BULK") ? "Yêu cầu báo giá sỉ" : "Đăng ký nhận nông sản", () -> {
                if (login()) quantity(offer, offer.optString("type").equals("BULK"));
            });
        }
        if (customer() && offer.optString("type").equals("SUBSCRIPTION")) button(card, "Theo dõi gói định kỳ", () ->
                new MaterialAlertDialogBuilder(requireContext()).setTitle("Chu kỳ nhắc nhận nông sản")
                        .setMessage("Mỗi kỳ bạn xác nhận suất và giá mới trước khi đặt. Không tự tạo đơn hoặc trừ tiền.")
                        .setPositiveButton("Mỗi 7 ngày", (d, w) -> subscribe(offer, 7))
                        .setNeutralButton("Mỗi 30 ngày", (d, w) -> subscribe(offer, 30)).setNegativeButton("Đóng", null).show());
        if (claims != null) for (int i = 0; i < claims.length(); i++) if (claims.optJSONObject(i) != null) claim(claims.optJSONObject(i), offer, true);
        JSONArray quotes = response.optJSONArray("quotes");
        if (quotes != null) for (int i = 0; i < quotes.length(); i++) if (quotes.optJSONObject(i) != null) quote(quotes.optJSONObject(i), offer, owner);
        JSONObject impact = response.optJSONObject("impact");
        JSONArray received = response.optJSONArray("receivedContents");
        if (received != null && received.length() > 0) {
            text(content, "Thành phần gói đã nhận (mỗi suất)", true);
            for (int i = 0; i < received.length(); i++) {
                JSONObject item = received.optJSONObject(i); if (item != null)
                    text(content, item.optString("productName") + " · " + item.optDouble("quantityPerUnit") + " " + item.optString("unit"), false);
            }
        }
        if (impact != null) text(content, "Cửa hàng: " + impact.optInt("completedPickups") + " lượt nhận giải cứu, " + impact.optInt("donationPickups") + " lượt quyên góp đã hoàn tất.", false);
    }
    private void quantity(JSONObject offer, boolean quote) {
        LinearLayout form = form(); EditText quantity = field(form, "Số suất (số nguyên)", true); quantity.setText("1");
        EditText note = quote ? field(form, "Lời nhắn cho người bán", false) : null;
        text(form, "Giá hiện tại: " + money(offer.optDouble("unitPrice")) + " / " + offer.optString("unitLabel")
                + ". Thanh toán tại điểm khi nhận. " + (offer.optString("type").equals("GROUP_BUY") ? "Cam kết chỉ có hiệu lực nhận hàng khi nhóm đạt ngưỡng." : ""), false);
        android.app.Dialog dialog = new MaterialAlertDialogBuilder(requireContext()).setTitle(quote ? "Thương lượng sỉ" : "Đăng ký giải cứu")
                .setView(scroll(form)).setNegativeButton("Đóng", null).setPositiveButton("Xác nhận", null).create();
        dialog.setOnShowListener(d -> ((androidx.appcompat.app.AlertDialog) dialog).getButton(-1).setOnClickListener(v -> {
            try {
                int units = Integer.parseInt(quantity.getText().toString().trim());
                JSONObject body = body("offerId", offer.optString("id"), "units", units);
                if (quote) body.put("note", note.getText().toString());
                else if (!subscriptionId.isEmpty()) body.put("subscriptionId", subscriptionId);
                dialog.dismiss(); model.action(quote ? "quotes/request" : "join", body, model::refresh);
            } catch (Exception e) { quantity.setError("Nhập số suất nguyên hợp lệ."); }
        })); dialog.show();
    }
    private void claim(JSONObject claim, JSONObject offer, boolean inDetail) {
        if (offer == null) return;
        LinearLayout card = card(content); text(card, inDetail ? claim.optString("customerName") : offer.optString("title"), true);
        text(card, status(claim.optString("status")) + " · " + claim.optInt("units") + " suất · " + money(claim.optDouble("totalAmount")), false);
        text(card, "Mã đăng ký: " + claim.optString("id"), false);
        String state = claim.optString("status");
        if (seller() && state.equals("RESERVED")) button(card, "Đã chuẩn bị, mời khách đến nhận", () -> model.action("claims/action", body("claimId", claim.optString("id"), "action", "READY"), model::refresh));
        if (customer() && state.equals("READY")) button(card, "Xác nhận đã nhận nông sản", () -> confirm("Bạn đã nhận nông sản?",
                "Chỉ xác nhận sau khi đã nhận hàng thực tế" + (claim.optDouble("totalAmount") > 0 ? " và thanh toán " + money(claim.optDouble("totalAmount")) + " cho cửa hàng." : ". Gói quyên góp không thu tiền."),
                () -> model.action("claims/action", body("claimId", claim.optString("id"), "action", "COMPLETE", "confirmReceived", true, "confirmPaid", true), model::refresh)));
        if (customer() && (state.equals("RESERVED") || state.equals("PLEDGED") || state.equals("READY"))
                && (!offer.optString("type").equals("GROUP_BUY") || !offer.optBoolean("groupSucceeded"))) button(card, "Hủy đăng ký / rút cam kết", () -> confirm("Hủy đăng ký?", "Suất sẽ được trả lại cho người cần giải cứu khác.",
                () -> model.action("claims/action", body("claimId", claim.optString("id"), "action", "CANCEL"), model::refresh)));
        if (!inDetail) button(card, "Mở gói và quản lý nhận hàng", () -> openOffer(offer.optString("id"), ""));
        if (state.equals("COMPLETED")) text(card, "Đã ghi nhận nhận hàng" + (claim.optDouble("totalAmount") > 0 ? " và khách xác nhận trả tiền tại điểm." : "."), false);
    }
    private void quote(JSONObject quote, JSONObject offer, boolean owner) {
        LinearLayout card = card(content); text(card, "Báo giá · " + status(quote.optString("status")), true);
        text(card, quote.optString("companyName") + " · " + quote.optInt("units") + " suất\n" + quote.optString("buyerNote"), false);
        if (quote.has("unitPrice")) text(card, money(quote.optDouble("unitPrice")) + " / suất · tổng "
                + money(quote.optDouble("unitPrice") * quote.optInt("units")) + "\nHiệu lực đến " + date(quote.optLong("validUntil")), false);
        if (owner && !quote.optString("status").equals("ACCEPTED")) button(card, "Gửi / cập nhật giá đề nghị", () -> {
            LinearLayout form = form(); EditText price = field(form, "Giá mỗi suất (VND, số nguyên)", true);
            new MaterialAlertDialogBuilder(requireContext()).setTitle("Báo giá riêng cho đối tác").setView(scroll(form))
                    .setPositiveButton("Gửi giá", (d, w) -> {
                        try { model.action("quotes/propose", body("quoteId", quote.optString("id"), "unitPrice", Long.parseLong(price.getText().toString().trim())), model::refresh); }
                        catch (NumberFormatException e) { tell("Giá không hợp lệ."); }
                    }).setNegativeButton("Đóng", null).show();
        });
        if (customer() && quote.optString("status").equals("PROPOSED")) button(card, "Chấp nhận báo giá này", () -> confirm("Xác nhận báo giá?",
                quote.optInt("units") + " suất · " + money(quote.optDouble("unitPrice") * quote.optInt("units")) + ". Thanh toán tại điểm nhận.",
                () -> model.action("quotes/accept", body("offerId", offer.optString("id"), "units", quote.optInt("units"), "revision", quote.optInt("revision")), model::refresh)));
    }
    private void subscribe(JSONObject offer, int days) { model.action("subscriptions/save", body("offerId", offer.optString("id"), "cadenceDays", days), () -> browse("subscriptions")); }
    private void subscription(JSONObject value) {
        LinearLayout card = card(content); text(card, value.optString("title"), true);
        text(card, status(value.optString("status")) + " · mỗi " + value.optInt("cadenceDays") + " ngày\nKỳ tiếp theo: " + date(value.optLong("nextDueAt")), false);
        text(card, "Mỗi kỳ chọn gói còn hàng và xác nhận lại giá. Cửa hàng cần bổ sung gói mới theo cùng mã định kỳ.", false);
        JSONObject offer = value.optJSONObject("currentOffer");
        if (offer != null && value.optString("status").equals("ACTIVE") && value.optLong("nextDueAt") <= System.currentTimeMillis()) button(card,
                "Xem và đặt suất kỳ này", () -> openOffer(offer.optString("id"), value.optString("id")));
        else text(card, "Chưa đến kỳ hoặc cửa hàng chưa có gói mới phù hợp.", false);
        String next = value.optString("status").equals("ACTIVE") ? "PAUSED" : "ACTIVE";
        button(card, next.equals("PAUSED") ? "Tạm dừng nhắc định kỳ" : "Tiếp tục đăng ký", () -> model.action("subscriptions/pause",
                body("subscriptionId", value.optString("id"), "status", next), model::refresh));
    }
    private void organization(JSONObject value) {
        LinearLayout card = card(content); text(card, value.optString("name"), true);
        text(card, status(value.optString("status")) + "\nMã đăng ký: " + value.optString("registration") + "\nLiên hệ: " + value.optString("contact")
                + "\n" + value.optString("evidence") + "\n" + value.optString("reason"), false);
        if (admin()) for (String decision : new String[]{"VERIFIED", "REJECTED"}) button(card, decision.equals("VERIFIED") ? "Xác thực tổ chức" : "Từ chối hồ sơ", () -> {
            LinearLayout form = form(); EditText reason = field(form, "Kết quả kiểm tra hồ sơ thực tế", false);
            new MaterialAlertDialogBuilder(requireContext()).setTitle("Duyệt tổ chức").setView(scroll(form)).setPositiveButton("Lưu quyết định", (d, w) -> model.action("organizations/review",
                    body("organizationId", value.optString("id"), "status", decision, "reason", reason.getText().toString()), model::refresh)).setNegativeButton("Đóng", null).show();
        });
    }
    private void applyOrganization() {
        LinearLayout form = form(); EditText name = field(form, "Tên tổ chức", false), registration = field(form, "Mã đăng ký tổ chức", false),
                contact = field(form, "Thông tin liên hệ", false), evidence = field(form, "Thông tin để Admin đối chiếu hồ sơ", false);
        new MaterialAlertDialogBuilder(requireContext()).setTitle("Đăng ký nhận quyên góp").setView(scroll(form))
                .setPositiveButton("Gửi hồ sơ", (d, w) -> model.action("organizations/apply", body("name", name.getText().toString(), "registration", registration.getText().toString(),
                        "contact", contact.getText().toString(), "evidence", evidence.getText().toString()), model::refresh))
                .setNegativeButton("Đóng", null).show();
    }
    private void impact(JSONObject value) {
        LinearLayout card = card(content); text(card, "Tác động đã được xác nhận", true);
        text(card, value.optInt("completedPickups") + " lượt nhận hoàn tất · " + value.optInt("donationPickups") + " lượt quyên góp", false);
        for (String key : new String[]{"quantityByUnit", "donatedByUnit"}) {
            JSONObject quantities = value.optJSONObject(key); text(card, key.equals("quantityByUnit") ? "Tổng nông sản đã nhận" : "Trong đó quyên góp", true);
            if (quantities != null) { java.util.Iterator<String> keys = quantities.keys(); while (keys.hasNext()) { String unit = keys.next(); text(card, quantities.optDouble(unit) + " " + unit, false); } }
        }
        text(card, "Thống kê riêng các gói Giải cứu mở rộng. Không cộng lẫn kg, hộp, quả; không tự quy đổi thành giảm phí hoặc chứng từ thuế.", false);
    }
    private void map(JSONObject offer) {
        String point = offer.optDouble("latitude") + "," + offer.optDouble("longitude");
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("geo:" + point + "?q=" + Uri.encode(point + " (" + offer.optString("title") + ")")));
        try { startActivity(intent); } catch (android.content.ActivityNotFoundException e) {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/search/?api=1&query=" + point)));
        }
    }
    private void requestLocation() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) locate();
        else permissions.launch(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION});
    }
    private void locate() {
        tell("Đang xác định vị trí…");
        location.getCurrentLocation(new LocationProvider.LocationCallback() {
            public void onLocationAvailable(double lat, double lon) {
                if (getView() == null) return; latitude = lat; longitude = lon;
                if (enablingAlerts) enableAlerts(); else { nearby = true; browse("catalog"); }
            }
            public void onLocationUnavailable(String message) { if (getView() != null) tell(message); }
        });
    }
    private void alertsDialog() {
        if (!login()) return;
        if (!customer()) { tell("Tính năng thông báo giờ chót dành cho Customer."); return; }
        new MaterialAlertDialogBuilder(requireContext()).setTitle("Nhắc giải cứu giờ chót trong 5 km")
                .setMessage("Vị trí hiện tại có hiệu lực tìm gói giờ chót trong 24 giờ. Bản ghi được dọn khi backend xử lý, hoặc xóa ngay khi bạn tắt. Không theo dõi vị trí nền.")
                .setPositiveButton("Bật trong 24 giờ", (d, w) -> { enablingAlerts = true; requestLocation(); })
                .setNeutralButton("Tắt và xóa vị trí", (d, w) -> model.action("nearby/preferences", body("enabled", false), () -> tell("Đã tắt nhắc và xóa vị trí.")))
                .setNegativeButton("Đóng", null).show();
    }
    private void enableAlerts() {
        if (android.os.Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notifications.launch(Manifest.permission.POST_NOTIFICATIONS); return;
        }
        if (latitude == null) return;
        com.rescuefarm.service.notification.FcmDeviceSyncWorker.refreshNow(requireContext());
        model.action("nearby/preferences", body("enabled", true, "latitude", latitude, "longitude", longitude, "radiusKm", 5), () -> tell("Đã bật nhắc trong 24 giờ quanh vị trí vừa chọn."));
    }
    private View scroll(LinearLayout form) { android.widget.ScrollView scroll = new android.widget.ScrollView(requireContext()); scroll.addView(form); return scroll; }
    private LinearLayout form() { LinearLayout form = column(requireContext()); int pad = dp(requireContext(), 20); form.setPadding(pad, 0, pad, 0); return form; }
    private void confirm(String title, String message, Runnable action) { new MaterialAlertDialogBuilder(requireContext()).setTitle(title).setMessage(message).setPositiveButton("Xác nhận", (d, w) -> action.run()).setNegativeButton("Đóng", null).show(); }
    private void tell(String message) { if (isAdded()) Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show(); }
    @Override public void onStart() { super.onStart(); FirebaseAuth.getInstance().addAuthStateListener(auth); clock.postDelayed(tick, 60000); }
    @Override public void onStop() { clock.removeCallbacks(tick); FirebaseAuth.getInstance().removeAuthStateListener(auth); super.onStop(); }
    @Override public void onSaveInstanceState(@NonNull Bundle out) {
        super.onSaveInstanceState(out); out.putBoolean("nearby", nearby); out.putString("subscriptionId", subscriptionId);
        if (latitude != null) { out.putDouble("latitude", latitude); out.putDouble("longitude", longitude); }
    }
    @Override public void onDestroyView() { if (location != null) location.close(); location = null; content = null; super.onDestroyView(); }
}
