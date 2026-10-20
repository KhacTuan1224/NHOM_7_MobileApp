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
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
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
        View card = getLayoutInflater().inflate(R.layout.item_promotion_voucher, list, false);
        boolean saved = value.optBoolean("saved");
        boolean eligible = value.optBoolean("eligible");
        boolean available = value.optBoolean("productAvailable");
        ImageView image = card.findViewById(R.id.voucherImage);
        Glide.with(this).load(value.optString("imageUrl"))
                .placeholder(R.drawable.ic_store).error(R.drawable.ic_store).centerCrop().into(image);
        TextView title = card.findViewById(R.id.voucherProduct);
        title.setText(value.optString("productName"));
        com.rescuefarm.ui.common.AppTypography.title(title);
        TextView amount = card.findViewById(R.id.voucherDiscount);
        amount.setText("VOLUME".equals(value.optString("type")) ? "Giảm theo số lượng" : discount(value));
        com.rescuefarm.ui.common.AppTypography.title(amount);
        TextView seller = card.findViewById(R.id.voucherSeller);
        seller.setText(value.optString("sellerName"));
        seller.setVisibility(value.optString("sellerName").isEmpty() ? View.GONE : View.VISIBLE);
        TextView status = card.findViewById(R.id.voucherStatus);
        status.setText(!eligible ? R.string.promotion_wallet_unavailable : saved ? R.string.voucher_saved_label
                : value.optString("productId").equals(productFilter) ? R.string.voucher_current_product : R.string.voucher_product_only);
        status.setTextColor(requireContext().getColor(eligible ? R.color.rescue_primary : R.color.rescue_on_surface_variant));
        SimpleDateFormat date = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.forLanguageTag("vi-VN"));
        TextView expiry = card.findViewById(R.id.voucherExpiry);
        expiry.setVisibility(value.optLong("endDate") > 0 ? View.VISIBLE : View.GONE);
        expiry.setText(getString(R.string.voucher_until, date.format(new Date(value.optLong("endDate")))));
        MaterialButton action = card.findViewById(R.id.voucherAction);
        action.setText(saved ? R.string.voucher_use : R.string.voucher_save);
        action.setEnabled(saved ? eligible && available : eligible);
        action.setOnClickListener(v -> { if (saved) openProduct(value); else changeSaved(value, action); });
        MaterialButton remove = card.findViewById(R.id.voucherRemove);
        remove.setVisibility(saved ? View.VISIBLE : View.GONE);
        remove.setOnClickListener(v -> changeSaved(value, remove));
        card.findViewById(R.id.voucherConditions).setOnClickListener(v -> {
            String details = value.optString("productName") + "\n\n" + discount(value);
            if (!value.optString("sellerName").isEmpty()) details += "\nNgười bán: " + value.optString("sellerName");
            if (value.optLong("startDate") > 0 && value.optLong("endDate") > 0) {
                details += "\n\n" + getString(R.string.voucher_period,
                        date.format(new Date(value.optLong("startDate"))), date.format(new Date(value.optLong("endDate"))));
            }
            details += "\n\n" + getString(R.string.voucher_usage);
            if (!eligible) details += "\n\n" + getString(R.string.promotion_wallet_unavailable);
            MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.voucher_conditions).setMessage(details).setNegativeButton(android.R.string.cancel, null);
            if (available) dialog.setPositiveButton(R.string.promotion_wallet_view_product, (d, which) -> openProduct(value));
            dialog.show();
        });
        if (available) {
            image.setOnClickListener(v -> openProduct(value));
            image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
            image.setContentDescription(getString(R.string.promotion_wallet_view_product) + ": " + value.optString("productName"));
            title.setOnClickListener(v -> openProduct(value));
        }
        list.addView(card);
    }
    private void openProduct(JSONObject value) {
        Bundle args = new Bundle(); args.putString("productId", value.optString("productId"));
        NavHostFragment.findNavController(this).navigate(R.id.productDetailFragment, args);
    }
    private void changeSaved(JSONObject value, MaterialButton save) {
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
    private MaterialButton button(String value) {
        MaterialButton button = new MaterialButton(requireContext(), null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        button.setText(value); button.setAllCaps(false); button.setTextSize(15); return button;
    }
    private void show(String value) { message.setText(value); message.setVisibility(View.VISIBLE); }
    @Override public void onDestroyView() { generation++; loading = false; list = null; super.onDestroyView(); }
}
