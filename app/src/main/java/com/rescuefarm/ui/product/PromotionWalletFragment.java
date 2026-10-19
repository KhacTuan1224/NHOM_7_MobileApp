package com.rescuefarm.ui.product;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ImageView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.tabs.TabLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.bumptech.glide.Glide;
import com.rescuefarm.MainActivity;
import com.rescuefarm.R;
import com.rescuefarm.domain.enums.UserRole;
import com.rescuefarm.service.backend.RenderBackendClient;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class PromotionWalletFragment extends Fragment {
    private LinearLayout list;
    private TextView message;
    private View progress, login, refresh;
    private boolean mine, loading, more;
    private String cursor = "", productFilter = "";
    private int generation;

    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup parent, @Nullable Bundle state) {
        return inflater.inflate(R.layout.fragment_promotion_wallet, parent, false);
    }
    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        list = view.findViewById(R.id.promotionWalletList);
        message = view.findViewById(R.id.promotionWalletMessage);
        progress = view.findViewById(R.id.promotionWalletProgress);
        login = view.findViewById(R.id.promotionWalletLogin);
        refresh = view.findViewById(R.id.promotionWalletRefresh);
        productFilter = getArguments() == null ? "" : getArguments().getString("productId", "");
        mine = state != null ? state.getBoolean("mine") : customer() && productFilter.isEmpty();
        view.findViewById(R.id.promotionWalletBack).setOnClickListener(v -> NavHostFragment.findNavController(this).navigateUp());
        login.setOnClickListener(v -> NavHostFragment.findNavController(this).navigate(R.id.loginFragment));
        refresh.setOnClickListener(v -> load(true));
        TabLayout tabs = view.findViewById(R.id.promotionWalletTabs);
        tabs.addTab(tabs.newTab().setText(R.string.promotion_wallet_mine), mine);
        tabs.addTab(tabs.newTab().setText(R.string.promotion_wallet_all), !mine);
        tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab tab) { mine = tab.getPosition() == 0; load(true); }
            @Override public void onTabUnselected(TabLayout.Tab tab) { }
            @Override public void onTabReselected(TabLayout.Tab tab) { }
        });
    }
    @Override public void onResume() { super.onResume(); load(true); }
    @Override public void onSaveInstanceState(@NonNull Bundle state) { super.onSaveInstanceState(state); state.putBoolean("mine", mine); }
    private boolean customer() {
        return FirebaseAuth.getInstance().getCurrentUser() != null && requireActivity() instanceof MainActivity
                && ((MainActivity) requireActivity()).getResolvedActorRole() == UserRole.CUSTOMER;
    }
    private void load(boolean reset) {
        if (getView() == null || (!reset && loading)) return;
        if (reset) { generation++; cursor = ""; more = false; list.removeAllViews(); }
        final int request = generation;
        if (mine && !customer()) {
            loading = false; progress.setVisibility(View.GONE); refresh.setEnabled(true);
            show(getString(R.string.promotion_wallet_login)); login.setVisibility(View.VISIBLE); return;
        }
        login.setVisibility(View.GONE); loading = true; refresh.setEnabled(false);
        progress.setVisibility(View.VISIBLE); message.setVisibility(View.GONE);
        JSONObject body = new JSONObject();
        try { body.put("mine", mine).put("cursor", cursor); }
        catch (org.json.JSONException ignored) { }
        RenderBackendClient.Callback callback = new RenderBackendClient.Callback() {
            @Override public void onSuccess(JSONObject result) {
                if (getView() == null || request != generation) return;
                loading = false; progress.setVisibility(View.GONE); refresh.setEnabled(true);
                View oldMore = list.findViewWithTag("more"); if (oldMore != null) list.removeView(oldMore);
                JSONArray values = result.optJSONArray("items");
                if (values != null) for (int i = 0; i < values.length(); i++) {
                    JSONObject value = values.optJSONObject(i); if (value != null) addCard(value);
                }
                cursor = result.optString("cursor"); more = result.optBoolean("hasMore");
                if (more) {
                    MaterialButton button = button(getString(R.string.pagination_more)); button.setTag("more");
                    button.setOnClickListener(v -> { button.setEnabled(false); load(false); }); list.addView(button);
                }
                if (list.getChildCount() == 0) show(getString(mine ? R.string.promotion_wallet_empty : R.string.promotion_wallet_no_offers));
            }
            @Override public void onError(String error) {
                if (getView() == null || request != generation) return;
                loading = false; progress.setVisibility(View.GONE); refresh.setEnabled(true);
                View button = list.findViewWithTag("more"); if (button != null) button.setEnabled(true);
                show(error);
            }
        };
        if (FirebaseAuth.getInstance().getCurrentUser() == null) RenderBackendClient.postGuest("/promotions/catalog", body, callback);
        else RenderBackendClient.post("/promotions/catalog", body, callback);
    }
    private void addCard(JSONObject value) {
        MaterialCardView card = new MaterialCardView(requireContext());
        card.setCardBackgroundColor(android.graphics.Color.TRANSPARENT); card.setCardElevation(0);
        card.setRadius(dp(16)); card.setStrokeWidth(dp(1)); card.setStrokeColor(requireContext().getColor(R.color.rescue_outline));
        LinearLayout.LayoutParams space = new LinearLayout.LayoutParams(-1, -2); space.bottomMargin = dp(12); card.setLayoutParams(space);
        LinearLayout body = new LinearLayout(requireContext()); body.setOrientation(LinearLayout.VERTICAL); body.setPadding(dp(14), dp(14), dp(14), dp(14));
        card.addView(body);
        LinearLayout header = new LinearLayout(requireContext()); header.setGravity(android.view.Gravity.CENTER_VERTICAL);
        if (!value.optString("imageUrl").isEmpty()) {
            ImageView image = new ImageView(requireContext()); image.setContentDescription(value.optString("productName"));
            LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(dp(64), dp(64)); size.setMarginEnd(dp(12)); header.addView(image, size);
            Glide.with(this).load(value.optString("imageUrl")).centerCrop().into(image);
        }
        TextView title = text(value.optString("productName"), 18, true); header.addView(title, new LinearLayout.LayoutParams(0, -2, 1)); body.addView(header);
        if (value.optString("productId").equals(productFilter)) body.addView(text("Ưu đãi cho sản phẩm bạn đang xem", 14, false));
        body.addView(text(discount(value), 18, true));
        if (!value.optString("sellerName").isEmpty()) body.addView(text("Người bán: " + value.optString("sellerName"), 16, false));
        SimpleDateFormat date = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.forLanguageTag("vi-VN"));
        if (value.optLong("endDate") > 0) body.addView(text(date.format(new Date(value.optLong("startDate"))) + " – " + date.format(new Date(value.optLong("endDate"))), 14, false));
        if (!value.optBoolean("eligible")) body.addView(text(getString(R.string.promotion_wallet_unavailable), 16, true));
        else if (value.optBoolean("saved")) body.addView(text("Đã lưu · Tự động áp dụng khi thanh toán", 14, false));
        MaterialButton save = button(getString(value.optBoolean("saved") ? R.string.promotion_wallet_remove : R.string.promotion_wallet_save));
        save.setEnabled(value.optBoolean("saved") || value.optBoolean("eligible"));
        save.setOnClickListener(v -> {
            if (FirebaseAuth.getInstance().getCurrentUser() == null) { NavHostFragment.findNavController(this).navigate(R.id.loginFragment); return; }
            if (!customer()) { show("Chỉ tài khoản khách hàng có thể lưu khuyến mãi."); return; }
            save.setEnabled(false);
            boolean remove = value.optBoolean("saved"); final int request = generation;
            JSONObject bodyRequest = new JSONObject(); try { bodyRequest.put("productId", value.optString("productId")); } catch (org.json.JSONException ignored) { }
            RenderBackendClient.post(remove ? "/promotions/remove" : "/promotions/save", bodyRequest, new RenderBackendClient.Callback() {
                @Override public void onSuccess(JSONObject result) {
                    if (getView() == null || request != generation) return;
                    Toast.makeText(requireContext(), remove ? R.string.promotion_wallet_removed : R.string.promotion_wallet_saved, Toast.LENGTH_LONG).show(); load(true);
                }
                @Override public void onError(String error) { if (getView() != null && request == generation) { save.setEnabled(true); show(error); } }
            });
        });
        body.addView(save);
        if (value.optBoolean("productAvailable")) {
            MaterialButton product = button(getString(R.string.promotion_wallet_view_product));
            product.setOnClickListener(v -> { Bundle args = new Bundle(); args.putString("productId", value.optString("productId")); NavHostFragment.findNavController(this).navigate(R.id.productDetailFragment, args); });
            body.addView(product);
        }
        list.addView(card);
    }
    private String discount(JSONObject value) {
        if ("PERCENT".equals(value.optString("type"))) return "Giảm " + format(value.optDouble("value")) + "%";
        if ("FIXED".equals(value.optString("type"))) return "Giảm " + NumberFormat.getCurrencyInstance(Locale.forLanguageTag("vi-VN")).format(value.optDouble("value")) + " / " + value.optString("unit", "sản phẩm");
        JSONObject tiers = value.optJSONObject("quantityDiscountTiers");
        StringBuilder label = new StringBuilder("Ưu đãi theo số lượng");
        if (tiers != null) {
            java.util.List<String> keys = new java.util.ArrayList<>();
            java.util.Iterator<String> iterator = tiers.keys();
            while (iterator.hasNext()) {
                String key = iterator.next();
                try { if (Double.isFinite(Double.parseDouble(key))) keys.add(key); } catch (NumberFormatException ignored) { }
            }
            keys.sort((a,b) -> Double.compare(Double.parseDouble(a), Double.parseDouble(b)));
            for (String key : keys) label.append("\nTừ ").append(key).append(": giảm ").append(format(tiers.optDouble(key))).append('%');
        }
        return label.toString();
    }
    private String format(double value) { return NumberFormat.getNumberInstance(Locale.forLanguageTag("vi-VN")).format(value); }
    private TextView text(String value, int size, boolean bold) {
        TextView text = new TextView(requireContext()); text.setText(value); text.setTextSize(size); text.setPadding(0, dp(4), 0, dp(4));
        text.setTextColor(requireContext().getColor(R.color.rescue_on_surface));
        if (bold) { text.setTypeface(text.getTypeface(), android.graphics.Typeface.BOLD); com.rescuefarm.ui.common.AppTypography.title(text); }
        return text;
    }
    private MaterialButton button(String value) {
        MaterialButton button = new MaterialButton(requireContext(), null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        button.setText(value); button.setAllCaps(false); button.setTextSize(15); return button;
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private void show(String value) { message.setText(value); message.setVisibility(View.VISIBLE); }
    @Override public void onDestroyView() { generation++; loading = false; list = null; super.onDestroyView(); }
}
