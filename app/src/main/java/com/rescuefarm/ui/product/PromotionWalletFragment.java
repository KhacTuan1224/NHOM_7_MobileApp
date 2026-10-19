package com.rescuefarm.ui.product;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ImageView;
import android.widget.Toast;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.util.TypedValue;
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
                    MaterialButton button = button(getString(R.string.pagination_more), false); button.setTag("more");
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
        card.setCardBackgroundColor(requireContext().getColor(R.color.rescue_surface));
        card.setCardElevation(dp(2)); card.setRadius(dp(12)); card.setStrokeWidth(dp(1));
        card.setStrokeColor(requireContext().getColor(R.color.rescue_outline)); card.setClipToOutline(true);
        LinearLayout.LayoutParams space = new LinearLayout.LayoutParams(-1, -2);
        space.bottomMargin = dp(14); card.setLayoutParams(space);

        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(row);

        LinearLayout coupon = new LinearLayout(requireContext());
        coupon.setOrientation(LinearLayout.VERTICAL); coupon.setGravity(Gravity.CENTER);
        coupon.setPadding(dp(6), dp(12), dp(6), dp(12)); coupon.setMinimumHeight(dp(190));
        coupon.setBackground(greenCouponBackground());
        LinearLayout.LayoutParams couponSize = new LinearLayout.LayoutParams(dp(78), -1);
        couponSize.setMargins(dp(5), dp(5), dp(8), dp(5)); row.addView(coupon, couponSize);
        TextView brand = text("RESCUEFARM", 8, true); brand.setTextColor(requireContext().getColor(R.color.rescue_on_primary));
        brand.setGravity(Gravity.CENTER); coupon.addView(brand);
        View perforation = new View(requireContext());
        perforation.setBackgroundColor(0x99FFFFFF);
        LinearLayout.LayoutParams perforationSize = new LinearLayout.LayoutParams(dp(34), dp(1));
        perforationSize.topMargin = dp(8); perforationSize.bottomMargin = dp(8);
        coupon.addView(perforation, perforationSize);
        String shortDiscount = shortDiscount(value);
        TextView valueLabel = text(shortDiscount, 20, true);
        valueLabel.setMaxLines(2);
        valueLabel.setAutoSizeTextTypeUniformWithConfiguration(12, 20, 1, TypedValue.COMPLEX_UNIT_SP);
        valueLabel.setTextColor(requireContext().getColor(R.color.rescue_on_primary)); valueLabel.setGravity(Gravity.CENTER);
        coupon.addView(valueLabel, new LinearLayout.LayoutParams(-1, -2));
        TextView couponCaption = text("ƯU ĐÃI", 8, true); couponCaption.setTextColor(requireContext().getColor(R.color.rescue_on_primary));
        couponCaption.setGravity(Gravity.CENTER); coupon.addView(couponCaption);

        LinearLayout body = new LinearLayout(requireContext());
        body.setOrientation(LinearLayout.VERTICAL); body.setPadding(0, dp(12), dp(11), dp(12));
        row.addView(body, new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout header = new LinearLayout(requireContext()); header.setGravity(Gravity.CENTER_VERTICAL);
        if (!value.optString("imageUrl").isEmpty()) {
            ImageView image = new ImageView(requireContext()); image.setContentDescription(value.optString("productName"));
            image.setBackground(greenSoftBackground()); image.setClipToOutline(true);
            LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(dp(50), dp(50)); size.setMarginEnd(dp(8)); header.addView(image, size);
            Glide.with(this).load(com.rescuefarm.service.media.SecureMediaUrl.from(value.optString("imageUrl"))).centerCrop().into(image);
        }
        TextView title = text(value.optString("productName"), 14, true);
        title.setTextColor(requireContext().getColor(R.color.rescue_on_surface));
        title.setMaxLines(2); title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1)); body.addView(header);
        if (value.optString("productId").equals(productFilter)) {
            TextView match = text("Ưu đãi cho sản phẩm đang xem", 12, true);
            match.setTextColor(requireContext().getColor(R.color.rescue_primary)); body.addView(match);
        }
        TextView details = text(discount(value), 14, true);
        details.setTextColor(requireContext().getColor(R.color.rescue_primary));
        body.addView(details);
        if (!value.optString("sellerName").isEmpty()) body.addView(text("Người bán: " + value.optString("sellerName"), 13, false));
        SimpleDateFormat date = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.forLanguageTag("vi-VN"));
        if (value.optLong("endDate") > 0) body.addView(text(date.format(new Date(value.optLong("startDate"))) + " – " + date.format(new Date(value.optLong("endDate"))), 12, false));
        if (!value.optBoolean("eligible")) {
            TextView unavailable = text(getString(R.string.promotion_wallet_unavailable), 12, true);
            unavailable.setTextColor(requireContext().getColor(R.color.rescue_critical)); body.addView(unavailable);
        } else if (value.optBoolean("saved")) {
            TextView saved = text("Đã lưu · Tự động áp dụng khi thanh toán", 12, true);
            saved.setTextColor(requireContext().getColor(R.color.rescue_primary)); body.addView(saved);
        }
        // ShopeeFood-style voucher action: light surface, slim green outline, green label.
        MaterialButton save = button(getString(value.optBoolean("saved") ? R.string.promotion_wallet_remove : R.string.promotion_wallet_save), false);
        save.setMinHeight(dp(38));
        save.setTextSize(13);
        save.setStrokeWidth(dp(1));
        save.setStrokeColor(android.content.res.ColorStateList.valueOf(
                requireContext().getColor(R.color.rescue_primary)));
        save.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                requireContext().getColor(R.color.rescue_surface)));
        save.setTextColor(requireContext().getColor(R.color.rescue_primary));
        save.setPadding(dp(14), 0, dp(14), 0);
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
        LinearLayout.LayoutParams saveSpace = new LinearLayout.LayoutParams(-2, -2);
        saveSpace.gravity = Gravity.END;
        saveSpace.topMargin = dp(4); saveSpace.bottomMargin = dp(2); body.addView(save, saveSpace);
        if (value.optBoolean("productAvailable")) {
            MaterialButton product = button(getString(R.string.promotion_wallet_view_product), false);
            product.setOnClickListener(v -> { Bundle args = new Bundle(); args.putString("productId", value.optString("productId")); NavHostFragment.findNavController(this).navigate(R.id.productDetailFragment, args); });
            body.addView(product, new LinearLayout.LayoutParams(-1, -2));
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
    private String shortDiscount(JSONObject value) {
        if ("PERCENT".equals(value.optString("type"))) return "−" + format(value.optDouble("value")) + "%";
        if ("FIXED".equals(value.optString("type")))
            return "−" + NumberFormat.getCurrencyInstance(Locale.forLanguageTag("vi-VN"))
                    .format(value.optDouble("value"));
        return "MUA NHIỀU";
    }
    private GradientDrawable greenCouponBackground() {
        GradientDrawable background = new GradientDrawable();
        background.setColor(requireContext().getColor(R.color.rescue_primary));
        background.setCornerRadius(dp(10));
        return background;
    }
    private GradientDrawable greenSoftBackground() {
        GradientDrawable background = new GradientDrawable();
        background.setColor(requireContext().getColor(R.color.rescue_primary_container));
        background.setCornerRadius(dp(8)); return background;
    }
    private MaterialButton button(String value, boolean filled) {
        int style = filled ? com.google.android.material.R.attr.materialButtonStyle
                : com.google.android.material.R.attr.materialButtonOutlinedStyle;
        MaterialButton button = new MaterialButton(requireContext(), null, style);
        button.setText(value); button.setAllCaps(false); button.setTextSize(14);
        button.setMinHeight(dp(42)); button.setInsetTop(0); button.setInsetBottom(0);
        button.setCornerRadius(dp(22));
        if (filled) {
            button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                    requireContext().getColor(R.color.rescue_primary)));
            button.setTextColor(requireContext().getColor(R.color.rescue_on_primary));
        } else {
            button.setStrokeColor(android.content.res.ColorStateList.valueOf(
                    requireContext().getColor(R.color.rescue_outline)));
            button.setTextColor(requireContext().getColor(R.color.rescue_primary));
        }
        return button;
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private void show(String value) { message.setText(value); message.setVisibility(View.VISIBLE); }
    @Override public void onDestroyView() { generation++; loading = false; list = null; super.onDestroyView(); }
}
