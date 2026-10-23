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
    private String actor = "";
    private boolean nearby, enablingAlerts;
    private boolean configuringTabs;
    private int impactDays = 30;
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
        if (getArguments() != null && getArguments().getBoolean("standalone")) {
            view.setBackgroundColor(0xFFFFFFFF);
            ((TextView) view.findViewById(R.id.rescueHeading)).setText("Chi tiết gói");
            view.findViewById(R.id.rescueIntroduction).setVisibility(View.GONE);
        }
        model = new ViewModelProvider(this).get(RescueProgramsViewModel.class);
        location = LocationProviderFactory.create(requireContext());
        if (saved != null) {
            impactDays = saved.getInt("impactDays", 30);
            nearby = saved.getBoolean("nearby");
            if (saved.containsKey("latitude")) { latitude = saved.getDouble("latitude"); longitude = saved.getDouble("longitude"); }
        }
        view.findViewById(R.id.rescueBack).setOnClickListener(v -> {
            if (getArguments() != null && getArguments().getBoolean("standalone")) NavHostFragment.findNavController(this).navigateUp();
            else if (model.state().getValue() != null && "detail".equals(model.state().getValue().screen)) browse("catalog");
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
        if (saved == null && !offerId.isEmpty()) openOffer(offerId);
        else if (saved == null && getArguments() != null && !getArguments().getString("initialTab", "").isEmpty()) {
            String initial = getArguments().getString("initialTab", "");
            int target = "impact".equals(initial) && seller() ? 2 : "mine".equals(initial) ? 1 : 0;
            if (tabs.getTabCount() > target) tabs.selectTab(tabs.getTabAt(target)); else browse("catalog");
        }
        else model.restore();
    }
    private void configureTabs() {
        configuredRole = role(); configuringTabs = true;
        int position = Math.max(0, tabs.getSelectedTabPosition());
        tabs.removeAllTabs();
        String[] names = admin() ? new String[]{"Khám phá"}
                : seller() ? new String[]{"Khám phá", "Gói cửa hàng", "Tác động"}
                : new String[]{"Khám phá", "Suất của tôi"};
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
            if (position == 1) browse("mine");
            else if (seller()) browse("impact");
        }
    }
    private void browse(String screen) {
        JSONObject query = body();
        if (screen.equals("catalog")) {
            query = body("type", TYPES[type.getSelectedItemPosition()]);
            if (nearby && latitude != null) query = body("type", TYPES[type.getSelectedItemPosition()], "nearby", true,
                    "latitude", latitude, "longitude", longitude, "radiusKm", 5);
        } else if (screen.equals("impact")) query = body("sellerId", uid(), "days", impactDays);
        model.load(screen, screen, query);
    }
    private void openOffer(String id) {
        model.load("detail", "detail", body("offerId", id));
    }
    private void render(RescueProgramsViewModel.State state) {
        View root = getView(); if (root == null) return;
        root.findViewById(R.id.rescueProgress).setVisibility(state.loading ? View.VISIBLE : View.GONE);
        root.findViewById(R.id.rescueRefresh).setEnabled(!state.loading);
        TextView message = root.findViewById(R.id.rescueMessage);
        message.setText(state.message); message.setVisibility(state.message.isEmpty() ? View.GONE : View.VISIBLE);
        boolean catalog = state.screen.equals("catalog");
        ((TextView) root.findViewById(R.id.rescueHeading)).setText(state.screen.equals("detail") ? "Chi tiết gói" : getString(R.string.rescue_hub_title));
        if (!state.screen.equals("detail")) {
            int selected = state.screen.equals("impact") && seller() ? 2 : state.screen.equals("mine") ? 1 : 0;
            if (selected < tabs.getTabCount() && tabs.getSelectedTabPosition() != selected) {
                configuringTabs = true; tabs.selectTab(tabs.getTabAt(selected)); configuringTabs = false;
            }
        }
        tabs.setVisibility(getArguments() != null && getArguments().getBoolean("standalone") ? View.GONE : View.VISIBLE);
        type.setVisibility(catalog ? View.VISIBLE : View.GONE);
        root.findViewById(R.id.rescueActions).setVisibility(catalog ? View.VISIBLE : View.GONE);
        ((TextView) root.findViewById(R.id.rescueNearby)).setText(nearby ? "Xem tất cả khu vực" : getString(R.string.rescue_nearby));
        content.removeAllViews();
        if (state.screen.equals("detail")) {
            JSONObject offer = state.value.optJSONObject("offer"); if (offer != null) detail(offer, state.value);
        } else if (state.screen.equals("impact")) {
            JSONObject impact = state.value.optJSONObject("impact"); if (impact != null) impact(impact);
        } else {
            JSONArray items = state.value.optJSONArray("items");
            if (items != null) for (int i = 0; i < items.length(); i++) {
                JSONObject value = items.optJSONObject(i); if (value == null) continue;
                if (state.screen.equals("mine") && !seller()) claim(value, value.optJSONObject("offer"), false);
                else offerCard(value);
            }
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
        button(card, "Xem gói giải cứu", () -> openOffer(offer.optString("id")));
    }
    private void detail(JSONObject offer, JSONObject response) {
        LinearLayout card = card(content);
        com.rescuefarm.ui.common.DetailTypography.meta(text(card, type(offer.optString("type")) + " · " + status(offer.optString("status")), false));
        text(card, offer.optString("title"), true).setTextAppearance(R.style.TextAppearance_RescueFarm_Detail_Name); text(card, offer.optString("description"), false);
        text(card, "Phân loại: " + offer.optString("categoryLabel") + "\nThành phần / lưu ý: " + offer.optString("contentsDisclosure"), false);
        text(card, "Giá trị gốc: " + money(offer.optDouble("originalUnitPrice")) + "\nGiá giải cứu: "
                + money(offer.optDouble("unitPrice")) + " / " + offer.optString("unitLabel"), false);
        text(card, "Còn " + offer.optInt("availableUnits") + " / " + offer.optInt("totalUnits") + " suất · giới hạn " + offer.optInt("maxUnitsPerClaim") + " suất/khách", false);
        text(card, "Hạn đăng ký: " + date(offer.optLong("closesAt")) + "\nNhận từ " + date(offer.optLong("pickupStartAt"))
                + " đến " + date(offer.optLong("pickupEndAt")) + "\n" + offer.optString("pickupAddress"), false);
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
            button(card, "Đăng ký nhận nông sản", () -> {
                if (login()) quantity(offer);
            });
        }
        if (claims != null) for (int i = 0; i < claims.length(); i++) if (claims.optJSONObject(i) != null) claim(claims.optJSONObject(i), offer, true);
        JSONObject impact = response.optJSONObject("impact");
        JSONArray received = response.optJSONArray("receivedContents");
        if (received != null && received.length() > 0) {
            text(content, "Thành phần gói đã nhận (mỗi suất)", true);
            for (int i = 0; i < received.length(); i++) {
                JSONObject item = received.optJSONObject(i); if (item != null)
                    text(content, item.optString("productName") + " · " + item.optDouble("quantityPerUnit") + " " + item.optString("unit"), false);
            }
        }
        if (impact != null) text(content, "Cửa hàng: " + impact.optInt("completedPickups") + " lượt nhận giải cứu đã hoàn tất.", false);
    }
    private void quantity(JSONObject offer) {
        LinearLayout form = form(); EditText quantity = field(form, "Số suất (số nguyên)", true); quantity.setText("1");
        text(form, "Giá hiện tại: " + money(offer.optDouble("unitPrice")) + " / " + offer.optString("unitLabel")
                + ". Thanh toán tại điểm khi nhận. ", false);
        android.app.Dialog dialog = new MaterialAlertDialogBuilder(requireContext()).setTitle("Đăng ký giải cứu")
                .setView(scroll(form)).setNegativeButton("Đóng", null).setPositiveButton("Xác nhận", null).create();
        dialog.setOnShowListener(d -> ((androidx.appcompat.app.AlertDialog) dialog).getButton(-1).setOnClickListener(v -> {
            try {
                int units = Integer.parseInt(quantity.getText().toString().trim());
                JSONObject body = body("offerId", offer.optString("id"), "units", units);
                dialog.dismiss(); model.action("join", body, model::refresh);
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
                "Chỉ xác nhận sau khi đã nhận hàng thực tế" + " và thanh toán " + money(claim.optDouble("totalAmount")) + " cho cửa hàng.",
                () -> model.action("claims/action", body("claimId", claim.optString("id"), "action", "COMPLETE", "confirmReceived", true, "confirmPaid", true), model::refresh)));
        if (customer() && (state.equals("RESERVED") || state.equals("READY"))) button(card, "Hủy đăng ký", () -> confirm("Hủy đăng ký?", "Suất sẽ được trả lại cho người cần giải cứu khác.",
                () -> model.action("claims/action", body("claimId", claim.optString("id"), "action", "CANCEL"), model::refresh)));
        if (!inDetail) button(card, "Mở gói và quản lý nhận hàng", () -> openOffer(offer.optString("id")));
        if (state.equals("COMPLETED")) text(card, "Đã ghi nhận nhận hàng" + (claim.optDouble("totalAmount") > 0 ? " và khách xác nhận trả tiền tại điểm." : "."), false);
    }
    private void impact(JSONObject value) {
        RescueImpactPanel.render(content, value, impactDays, days -> {
            impactDays = days; browse("impact");
        });
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
        super.onSaveInstanceState(out); out.putBoolean("nearby", nearby);
        out.putInt("impactDays", impactDays);
        if (latitude != null) { out.putDouble("latitude", latitude); out.putDouble("longitude", longitude); }
    }
    @Override public void onDestroyView() { if (location != null) location.close(); location = null; content = null; super.onDestroyView(); }
}
