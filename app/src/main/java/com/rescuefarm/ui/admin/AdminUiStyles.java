package com.rescuefarm.ui.admin;

import android.content.Context;
import android.content.res.ColorStateList;
import com.google.android.material.button.MaterialButton;
import com.rescuefarm.R;
import androidx.core.content.ContextCompat;
import com.rescuefarm.data.repository.AdminRepository.Section;

/** Shared Admin button appearance for programmatically-created actions. */
final class AdminUiStyles {
    private AdminUiStyles() { }

    static void outlinedButton(Context context, MaterialButton button) {
        outlinedButton(context, button, R.color.rescue_primary, R.color.rescue_primary_container);
    }

    static void sectionButton(Context context, MaterialButton button, Section section) {
        int color;
        int soft;
        switch (section) {
            case REPORTS: color = R.color.admin_reports; soft = R.color.admin_reports_soft; break;
            case CAMPAIGNS: case SELLER_APPLICATIONS:
                color = section == Section.CAMPAIGNS ? R.color.admin_campaigns : R.color.admin_seller;
                soft = section == Section.CAMPAIGNS ? R.color.admin_campaigns_soft : R.color.admin_seller_soft; break;
            case REVIEWS: color = R.color.admin_reviews; soft = R.color.admin_reviews_soft; break;
            case PRODUCTS: color = R.color.admin_products; soft = R.color.admin_products_soft; break;
            case CATEGORIES: color = R.color.admin_categories; soft = R.color.admin_categories_soft; break;
            case BANNERS: color = R.color.admin_banners; soft = R.color.admin_banners_soft; break;
            case ORDERS: color = R.color.admin_orders; soft = R.color.admin_orders_soft; break;
            case SHIPPING_SETTINGS: color = R.color.admin_shipping; soft = R.color.admin_shipping_soft; break;
            case ANALYTICS: color = R.color.admin_analytics; soft = R.color.admin_analytics_soft; break;
            default: color = R.color.admin_users; soft = R.color.admin_users_soft; break;
        }
        outlinedButton(context, button, color, soft);
    }

    private static void outlinedButton(Context context, MaterialButton button, int color, int soft) {
        float density = context.getResources().getDisplayMetrics().density;
        button.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(context, soft)));
        button.setStrokeColor(ColorStateList.valueOf(ContextCompat.getColor(context, color)));
        button.setStrokeWidth(Math.round(density));
        button.setTextColor(ContextCompat.getColor(context, color));
        button.setCornerRadius(Math.round(14 * density));
        button.setElevation(0);
        button.setInsetTop(0);
        button.setInsetBottom(0);
        button.setAllCaps(false);
    }
}
