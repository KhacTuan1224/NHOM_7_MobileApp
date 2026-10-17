package com.rescuefarm.ui.feed;

import android.graphics.Typeface;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.fragment.app.Fragment;
import com.bumptech.glide.Glide;
import com.google.android.material.card.MaterialCardView;
import com.rescuefarm.R;
import com.rescuefarm.domain.enums.RescueMode;
import com.rescuefarm.domain.enums.UrgencyLevel;
import com.rescuefarm.domain.model.Product;
import com.rescuefarm.domain.model.RescueCampaign;
import java.util.List;
import java.util.Locale;

/** Public campaign article card; title, story and rescue operation are one object. */
final class CampaignFeedCardRenderer {
    private final Fragment fragment;
    CampaignFeedCardRenderer(Fragment fragment) { this.fragment = fragment; }

    View card(RescueCampaign campaign, List<Product> products, View.OnClickListener listener) {
        MaterialCardView card = new MaterialCardView(fragment.requireContext());
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(-1, -2);
        cardParams.bottomMargin = dp(12); card.setLayoutParams(cardParams);
        card.setRadius(dp(16)); card.setStrokeWidth(dp(1));
        card.setStrokeColor(fragment.getResources().getColor(R.color.rescue_outline,
                fragment.requireContext().getTheme()));
        card.setCardElevation(0);
        LinearLayout content = new LinearLayout(fragment.requireContext());
        content.setOrientation(LinearLayout.VERTICAL); content.setPadding(dp(14), dp(14), dp(14), dp(14));

        if (!campaign.getImageUrls().isEmpty()) {
            ImageView image = new ImageView(fragment.requireContext());
            image.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(190)));
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            image.setContentDescription("Ảnh nội dung chiến dịch giải cứu");
            Glide.with(fragment).load(campaign.getImageUrls().get(0)).centerCrop().into(image);
            content.addView(image);
        } else if (products != null && !products.isEmpty() && !products.get(0).getImageUrls().isEmpty()) {
            ImageView image = new ImageView(fragment.requireContext());
            image.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(170)));
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            image.setContentDescription("Ảnh sản phẩm trong chiến dịch");
            Glide.with(fragment).load(products.get(0).getImageUrls().get(0)).centerCrop().into(image);
            content.addView(image);
        }
        String urgency = campaign.getUrgencyLevel() == UrgencyLevel.CRITICAL
                ? "CẦN GIẢI CỨU GẤP" : campaign.getHighlightLabel();
        addText(content, urgency.isEmpty() ? "CHIẾN DỊCH GIẢI CỨU" : urgency,
                12, true, R.color.rescue_critical);
        addText(content, campaign.getTitle(), 19, true, R.color.rescue_on_surface);
        if (campaign.getDescription() != null && !campaign.getDescription().trim().isEmpty()) {
            String description = campaign.getDescription().trim();
            addText(content, description.length() > 190 ? description.substring(0, 190) + "…" : description,
                    14, false, R.color.rescue_on_surface_variant);
        }

        StringBuilder productsLine = new StringBuilder("Sản phẩm: ");
        if (products != null && !products.isEmpty()) {
            for (int i = 0; i < products.size(); i++) {
                if (i > 0) productsLine.append(", ");
                productsLine.append(products.get(i).getName());
                if (i == 2 && products.size() > 3) {
                    productsLine.append(" và ").append(products.size() - 3).append(" sản phẩm khác");
                    break;
                }
            }
        } else {
            productsLine.append(campaign.getBatchTargets().size()).append(" lô giải cứu");
        }
        addText(content, productsLine.toString(), 13, true, R.color.rescue_on_surface);
        String reason = campaign.getRescueReason() == null ? "" : campaign.getRescueReason().name();
        String mode = campaign.getRescueMode() == RescueMode.MOBILE_POINT
                ? "Điểm giải cứu di động" : "Điểm giải cứu cố định";
        addText(content, reason + " · " + mode, 12, false, R.color.rescue_on_surface_variant);

        ProgressBar progress = new ProgressBar(fragment.requireContext(), null,
                android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100); progress.setProgress((int) Math.round(campaign.calculateProgress()));
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(-1, dp(7));
        progressParams.topMargin = dp(10); content.addView(progress, progressParams);
        if ("REVIEW_REQUIRED".equals(campaign.getUnitStatus())) {
            progress.setVisibility(View.GONE);
            addText(content, "Cần đối soát đơn vị chiến dịch", 13, true, R.color.rescue_on_surface_variant);
        } else {
        String unit = campaign.getQuantityUnitLabel();
        addText(content, String.format(Locale.forLanguageTag("vi-VN"),
                "Đã giải cứu %.2f / %.2f %s (%.0f%%)", campaign.getRescuedQuantity(),
                campaign.getTargetQuantity(), unit, campaign.calculateProgress()),
                13, true, R.color.rescue_on_surface);
        addText(content, String.format(Locale.forLanguageTag("vi-VN"),
                "Đang giữ %.2f · còn khả dụng %.2f %s", campaign.getReservedQuantity(),
                campaign.calculateRemainingQuantity(), unit), 12, false,
                R.color.rescue_on_surface_variant);
        }
        if (campaign.getRescueMode() == RescueMode.MOBILE_POINT) {
            String location = campaign.isLocationFresh(new java.util.Date())
                    ? "Đang chia sẻ vị trí · " + safe(campaign.getLocationName())
                    : "Điểm mobile · vị trí cập nhật " + formatAge(campaign.getLocationUpdatedAt());
            addText(content, location, 12, false, R.color.rescue_on_surface_variant);
        }
        TextView cta = addText(content, "Xem chiến dịch và các lô hàng  →", 14, true,
                R.color.rescue_primary);
        cta.setPadding(0, dp(8), 0, 0);
        card.addView(content); card.setOnClickListener(listener);
        return card;
    }

    View message(String value) {
        TextView text = new TextView(fragment.requireContext());
        text.setText(value); text.setPadding(dp(16), dp(18), dp(16), dp(18));
        text.setTextColor(fragment.getResources().getColor(R.color.rescue_on_surface_variant,
                fragment.requireContext().getTheme()));
        return text;
    }

    private TextView addText(LinearLayout parent, String value, int size, boolean bold, int color) {
        TextView text = new TextView(fragment.requireContext()); text.setText(value);
        text.setTextSize(size); text.setTextColor(fragment.getResources().getColor(color,
                fragment.requireContext().getTheme()));
        if (bold) text.setTypeface(text.getTypeface(), Typeface.BOLD);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(5); parent.addView(text, params); return text;
    }
    private String formatAge(java.util.Date updated) {
        if (updated == null) return "chưa có vị trí mới";
        long minutes = Math.max(0L, (System.currentTimeMillis() - updated.getTime()) / 60000L);
        return minutes == 0 ? "vừa xong" : minutes + " phút trước";
    }
    private String safe(String value) { return value == null || value.trim().isEmpty() ? "chưa rõ địa điểm" : value.trim(); }
    private int dp(int value) { return Math.round(value * fragment.getResources().getDisplayMetrics().density); }
}
