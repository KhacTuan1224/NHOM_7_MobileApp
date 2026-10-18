package com.rescuefarm.ui.seller;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.core.content.ContextCompat;
import com.google.android.material.button.MaterialButton;
import com.rescuefarm.R;

/** Seller management surfaces follow the Admin list and drawer presentation. */
public final class SellerUiStyles {
    public enum Section { PRODUCTS, BATCHES, PROMOTIONS, CAMPAIGNS, MOBILE, ORDERS, SHIPMENTS, ANALYTICS, PROFILE, POSTS }
    private SellerUiStyles() { }

    public static Section forRoute(String route) {
        switch (route) {
            case "products": return Section.PRODUCTS;
            case "batches": return Section.BATCHES;
            case "promotions": return Section.PROMOTIONS;
            case "campaigns": return Section.CAMPAIGNS;
            case "mobile": return Section.MOBILE;
            case "orders": return Section.ORDERS;
            case "shipments": return Section.SHIPMENTS;
            case "statistics": return Section.ANALYTICS;
            default: return Section.PROFILE;
        }
    }
    public static int icon(Section section) {
        switch (section) {
            case PRODUCTS: return R.drawable.ic_store;
            case BATCHES: case PROMOTIONS: return R.drawable.ic_admin_categories;
            case CAMPAIGNS: return R.drawable.ic_nav_community;
            case MOBILE: return R.drawable.ic_home_location;
            case ORDERS: return R.drawable.ic_nav_orders;
            case SHIPMENTS: return R.drawable.ic_admin_shipping;
            case ANALYTICS: return R.drawable.ic_nav_dashboard;
            default: return R.drawable.ic_nav_profile;
        }
    }

    private static int foreground(Section section) {
        switch (section) {
            case PRODUCTS: return R.color.admin_products;
            case BATCHES: return R.color.admin_categories;
            case PROMOTIONS: return R.color.admin_reviews;
            case CAMPAIGNS: return R.color.admin_campaigns;
            case MOBILE: return R.color.admin_categories;
            case ORDERS: return R.color.admin_orders;
            case SHIPMENTS: return R.color.admin_shipping;
            case ANALYTICS: return R.color.admin_analytics;
            case PROFILE: return R.color.admin_seller;
            default: return R.color.admin_users;
        }
    }
    private static int background(Section section) {
        switch (section) {
            case PRODUCTS: return R.color.admin_products_soft;
            case BATCHES: return R.color.admin_categories_soft;
            case PROMOTIONS: return R.color.admin_reviews_soft;
            case CAMPAIGNS: return R.color.admin_campaigns_soft;
            case MOBILE: return R.color.admin_categories_soft;
            case ORDERS: return R.color.admin_orders_soft;
            case SHIPMENTS: return R.color.admin_shipping_soft;
            case ANALYTICS: return R.color.admin_analytics_soft;
            case PROFILE: return R.color.admin_seller_soft;
            default: return R.color.admin_users_soft;
        }
    }
    public static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
    public static void styleButton(MaterialButton button, Section section) {
        Context context = button.getContext();
        int color = ContextCompat.getColor(context, foreground(section));
        ColorStateList ink = new ColorStateList(new int[][]{new int[]{-android.R.attr.state_enabled}, new int[]{}},
                new int[]{androidx.core.graphics.ColorUtils.setAlphaComponent(color, 100), color});
        button.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(context, background(section))));
        button.setTextColor(ink); button.setStrokeColor(ink); button.setIconTint(ink);
        button.setStrokeWidth(dp(context, 1)); button.setCornerRadius(dp(context, 14));
        button.setElevation(0); button.setInsetTop(0); button.setInsetBottom(0);
        button.setAllCaps(false);
        button.setMinWidth(0); button.setMinimumWidth(0);
        button.setMinHeight(dp(context, 40)); button.setMinimumHeight(dp(context, 40));
        button.setPaddingRelative(dp(context, 10), dp(context, 4), dp(context, 10), dp(context, 4));
    }
    public static MaterialButton button(Context context, Section section, String label, String description) {
        MaterialButton button = new MaterialButton(context);
        styleButton(button, section); button.setText(label); button.setContentDescription(description);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.END; params.topMargin = dp(context, 3); params.bottomMargin = dp(context, 3);
        button.setLayoutParams(params);
        return button;
    }
    public static void styleActions(View view, Section section) {
        if (view instanceof MaterialButton) {
            MaterialButton button = (MaterialButton) view;
            styleButton(button, section);
            ViewGroup.LayoutParams params = button.getLayoutParams();
            if (params != null && params.width == ViewGroup.LayoutParams.MATCH_PARENT) {
                params.width = ViewGroup.LayoutParams.WRAP_CONTENT; button.setLayoutParams(params);
            }
        } else if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) styleActions(group.getChildAt(i), section);
        }
    }
    public static void drawerButton(MaterialButton button, Section section, int icon) {
        styleButton(button, section);
        button.setIconResource(icon); button.setIconSize(dp(button.getContext(), 18));
        button.setIconPadding(dp(button.getContext(), 8));
        button.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(button.getContext(), 220),
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMarginStart(dp(button.getContext(), 8)); params.setMarginEnd(dp(button.getContext(), 8));
        params.bottomMargin = dp(button.getContext(), 6); button.setLayoutParams(params);
    }
    public static Row row(Context context, Section section) { return new Row(context, section); }

    public static final class Row {
        public final LinearLayout root, information, actions;
        private final Context context;
        private final Section section;
        private Row(Context context, Section section) {
            this.context = context; this.section = section;
            root = new LinearLayout(context); root.setOrientation(LinearLayout.HORIZONTAL);
            root.setGravity(Gravity.CENTER_VERTICAL);
            int padding = dp(context, 14); root.setPadding(padding, padding, padding, padding);
            GradientDrawable outline = new GradientDrawable(); outline.setColor(Color.TRANSPARENT);
            outline.setCornerRadius(dp(context, 16)); outline.setStroke(dp(context, 1), ContextCompat.getColor(context, foreground(section)));
            root.setBackground(outline);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
            params.topMargin = dp(context, 6); params.bottomMargin = dp(context, 6); root.setLayoutParams(params);
            information = new LinearLayout(context); information.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams info = new LinearLayout.LayoutParams(0, -2, 1f); info.setMarginEnd(dp(context, 12));
            root.addView(information, info);
            actions = new LinearLayout(context); actions.setOrientation(LinearLayout.VERTICAL);
            root.addView(actions, new LinearLayout.LayoutParams(-2, -2));
        }
        public TextView title(String value) { return text(value, 18, true); }
        public TextView body(String value) { return text(value, 16, false); }
        public TextView metadata(String value) { return text(value, 14, false); }
        private TextView text(String value, int size, boolean bold) {
            TextView text = new TextView(context); text.setText(value); text.setTextSize(size);
            text.setTextColor(ContextCompat.getColor(context, bold ? R.color.rescue_on_surface : R.color.rescue_on_surface_variant));
            if (bold) text.setTypeface(null, Typeface.BOLD);
            text.setPadding(0, 0, 0, dp(context, 5)); information.addView(text, new LinearLayout.LayoutParams(-1, -2));
            return text;
        }
        public MaterialButton action(String label, String description, View.OnClickListener listener) {
            MaterialButton button = button(context, section, label, description);
            button.setOnClickListener(listener); actions.addView(button); return button;
        }
    }
}
