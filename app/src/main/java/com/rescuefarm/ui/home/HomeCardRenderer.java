package com.rescuefarm.ui.home;

import android.content.res.ColorStateList;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.fragment.app.Fragment;
import com.bumptech.glide.Glide;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.imageview.ShapeableImageView;
import com.rescuefarm.R;
import com.rescuefarm.domain.enums.UrgencyLevel;
import com.rescuefarm.domain.model.Category;
import com.rescuefarm.domain.model.Product;
import com.rescuefarm.domain.model.RescueCampaign;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

public final class HomeCardRenderer {
    private final Fragment fragment;

    public HomeCardRenderer(Fragment fragment) { this.fragment = fragment; }

    public View campaign(RescueCampaign campaign, double distance, View.OnClickListener listener) {
        return campaign(campaign, distance, java.util.Collections.emptyList(), listener);
    }

    public View campaign(RescueCampaign campaign, double distance, List<Product> products,
                         View.OnClickListener listener) {
        MaterialCardView card = card(14);
        LinearLayout body = vertical();
        body.setPadding(dp(9), dp(9), dp(9), dp(10));

        ImageView image = imageView();
        Product related = findRelatedProduct(campaign, products);
        if (related != null && !related.getImageUrls().isEmpty()) {
            Glide.with(fragment).load(related.getImageUrls().get(0)).centerCrop().into(image);
        } else {
            image.setImageResource(R.drawable.splash_rescue_garden);
        }
        body.addView(imageFrame(image, dp(112), 12));

        TextView title = text(campaign.getTitle(), 16, true, R.color.rescue_on_surface);
        com.rescuefarm.ui.common.AppTypography.title(title);
        title.setMinLines(2); title.setMaxLines(2);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        title.setPadding(0, dp(8), 0, dp(3)); body.addView(title);

        TextView urgency = text(campaign.getUrgencyLevel() == UrgencyLevel.CRITICAL
                ? "CẦN GIẢI CỨU GẤP" : campaign.getHighlightLabel(), 13, true,
                campaign.getUrgencyLevel() == UrgencyLevel.CRITICAL
                        ? R.color.rescue_critical : R.color.rescue_primary);
        urgency.setMaxLines(1); body.addView(urgency);

        ProgressBar progress = new ProgressBar(fragment.requireContext(), null,
                android.R.attr.progressBarStyleHorizontal);
        double progressPercent = campaign.calculateProgress();
        com.rescuefarm.ui.common.RescueProgressUtils.applyProgress(progress, progressPercent);
        progress.setProgressBackgroundTintList(ColorStateList.valueOf(color(R.color.rescue_primary_container)));
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(-1, dp(6));
        progressParams.setMargins(0, dp(6), 0, dp(4)); body.addView(progress, progressParams);

        if ("REVIEW_REQUIRED".equals(campaign.getUnitStatus())) {
            progress.setVisibility(View.GONE);
            body.addView(text("Cần đối soát đơn vị chiến dịch", 14, true, R.color.rescue_on_surface_variant));
        } else {
        String unit = campaign.getQuantityUnitLabel();
        body.addView(text(String.format(Locale.forLanguageTag("vi-VN"),
                "%.0f%% hoàn thành · mục tiêu %.2f %s", progressPercent,
                campaign.getTargetQuantity(), unit), 13, true,
                com.rescuefarm.ui.common.RescueProgressUtils.getProgressColorRes(progressPercent)));
        body.addView(text(String.format(Locale.forLanguageTag("vi-VN"),
                "Đã giải cứu %.2f · đang giữ %.2f · còn khả dụng %.2f %s",
                campaign.getRescuedQuantity(), campaign.getReservedQuantity(),
                campaign.calculateRemainingQuantity(), unit), 13, false, R.color.rescue_on_surface_variant));
        }
        if (Double.isFinite(distance)) {
            body.addView(text(String.format(Locale.forLanguageTag("vi-VN"), "Cách bạn %.1f km", distance), 13, false, R.color.rescue_on_surface_variant));
        }
        card.addView(body, new FrameLayout.LayoutParams(-1, -2));
        card.setOnClickListener(listener);
        return card;
    }

    public View product(Product product, View.OnClickListener listener) {
        MaterialCardView card = card(12);
        LinearLayout content = vertical();

        ImageView image = new com.rescuefarm.ui.common.ProductTileImageView(fragment.requireContext());
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setContentDescription(product.getName());
        if (!product.getImageUrls().isEmpty()) {
            Glide.with(fragment).load(product.getImageUrls().get(0)).centerCrop().into(image);
        } else {
            image.setImageResource(R.drawable.splash_rescue_garden);
        }
        FrameLayout imageArea = imageFrame(image, -2, 12);
        TextView badge = text("-" + Math.round(product.calculateDiscountPercent()) + "%",
                10, true, android.R.color.white);
        GradientDrawable badgeBackground = new GradientDrawable();
        badgeBackground.setColor(color(R.color.rescue_critical));
        badgeBackground.setCornerRadius(dp(10));
        badge.setBackground(badgeBackground);
        badge.setPadding(dp(8), dp(5), dp(8), dp(5));
        FrameLayout.LayoutParams badgeParams = new FrameLayout.LayoutParams(-2, -2,
                Gravity.TOP | Gravity.START);
        badgeParams.setMargins(dp(7), dp(7), 0, 0);
        imageArea.addView(badge, badgeParams);
        content.addView(imageArea);

        LinearLayout details = vertical();
        details.setPadding(dp(10), dp(8), dp(10), dp(10));
        TextView name = text(product.getName(), 15, true, R.color.rescue_on_surface);
        com.rescuefarm.ui.common.AppTypography.title(name);
        name.setMaxLines(2); name.setMinLines(2);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END); details.addView(name);

        LinearLayout priceRow = vertical();
        priceRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView salePrice = text(money(product.getRescuePrice()), 16, true, R.color.rescue_critical);
        priceRow.addView(salePrice);
        TextView oldPrice = text(money(product.getOriginalPrice()), 13, false,
                R.color.rescue_on_surface_variant);
        oldPrice.setPaintFlags(oldPrice.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
        LinearLayout.LayoutParams oldPriceParams = new LinearLayout.LayoutParams(-2, -2);
        oldPriceParams.topMargin = dp(2); priceRow.addView(oldPrice, oldPriceParams);
        details.addView(priceRow);

        String metadata = product.getAverageRating() > 0
                ? String.format(Locale.forLanguageTag("vi-VN"), "★ %.1f  ·  %d đánh giá",
                    product.getAverageRating(), product.getReviewCount())
                : product.getProvince();
        TextView foot = text(metadata, 13, false, R.color.rescue_on_surface_variant);
        foot.setMaxLines(1); foot.setEllipsize(android.text.TextUtils.TruncateAt.END); details.addView(foot);
        content.addView(details);
        card.addView(content, new FrameLayout.LayoutParams(-1, -2));
        card.setOnClickListener(listener);
        return card;
    }

    public View category(Category category, View.OnClickListener listener) {
        LinearLayout tile = vertical();
        tile.setGravity(Gravity.CENTER);
        tile.setPadding(dp(3), dp(1), dp(3), dp(1));
        ShapeableImageView image = new ShapeableImageView(fragment.requireContext());
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setShapeAppearanceModel(image.getShapeAppearanceModel().toBuilder()
                .setAllCornerSizes(dp(25)).build());
        image.setBackgroundTintList(ColorStateList.valueOf(color(R.color.rescue_primary_container)));
        if (!category.getImageUrl().isEmpty()) Glide.with(fragment).load(category.getImageUrl())
                .centerCrop().into(image);
        LinearLayout.LayoutParams imageParams = new LinearLayout.LayoutParams(dp(48), dp(48));
        imageParams.gravity = Gravity.CENTER_HORIZONTAL;
        imageParams.topMargin = dp(4); tile.addView(image, imageParams);
        TextView name = text(category.getName(), 13, false, R.color.rescue_on_surface);
        name.setGravity(Gravity.CENTER); name.setMinLines(2);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(-1, -2);
        nameParams.setMargins(0, dp(3), 0, 0); tile.addView(name, nameParams);
        tile.setClickable(true); tile.setFocusable(true); tile.setOnClickListener(listener);
        return tile;
    }

    public TextView message(String value) {
        TextView message = text(value, 14, false, R.color.rescue_on_surface_variant);
        message.setPadding(dp(3), dp(8), dp(3), dp(8));
        return message;
    }

    private Product findRelatedProduct(RescueCampaign campaign, List<Product> products) {
        if (products == null || products.isEmpty()) return null;
        String campaignText = normalize(campaign.getTitle() + " " + campaign.getDescription());
        Product best = null;
        int bestScore = 0;
        for (Product product : products) {
            String normalizedName = normalize(product.getName());
            if (normalizedName.length() > 3 && campaignText.contains(normalizedName)) return product;
            int score = 0;
            for (String token : normalizedName.split(" ")) {
                if (token.length() > 3 && campaignText.contains(token)) score++;
            }
            if (score > bestScore) { best = product; bestScore = score; }
        }
        return bestScore > 0 ? best : null;
    }

    private String normalize(String value) {
        return java.text.Normalizer.normalize(value == null ? "" : value.toLowerCase(Locale.forLanguageTag("vi-VN")),
                java.text.Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
    }

    private MaterialCardView card(int radius) {
        MaterialCardView card = new MaterialCardView(fragment.requireContext());
        card.setRadius(dp(radius));
        card.setStrokeWidth(0); // Bỏ viền xám
        card.setCardBackgroundColor(color(R.color.rescue_surface));
        card.setCardElevation(dp(2)); // Thêm elevation nhẹ
        card.setMaxCardElevation(dp(2));
        return card;
    }

    private FrameLayout imageFrame(ImageView image, int height, int radius) {
        MaterialCardView clip = new MaterialCardView(fragment.requireContext());
        clip.setRadius(dp(radius)); clip.setStrokeWidth(0); clip.setCardElevation(0);
        clip.setCardBackgroundColor(color(R.color.rescue_primary_container));
        FrameLayout frame = new FrameLayout(fragment.requireContext());
        frame.addView(image, new FrameLayout.LayoutParams(-1, height));
        clip.addView(frame, new FrameLayout.LayoutParams(-1, height));
        FrameLayout wrapper = new FrameLayout(fragment.requireContext());
        wrapper.addView(clip, new FrameLayout.LayoutParams(-1, height));
        return wrapper;
    }

    private LinearLayout vertical() {
        LinearLayout view = new LinearLayout(fragment.requireContext());
        view.setOrientation(LinearLayout.VERTICAL); return view;
    }

    private ImageView imageView() {
        ImageView image = new ImageView(fragment.requireContext());
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setContentDescription("Ảnh nông sản giải cứu"); return image;
    }

    private TextView text(String value, int size, boolean bold, int colorResource) {
        TextView text = new TextView(fragment.requireContext()); text.setText(value);
        text.setTextSize(size); text.setTextColor(color(colorResource));
        if (bold) text.setTypeface(text.getTypeface(), Typeface.BOLD);
        return text;
    }

    private String money(double value) {
        return NumberFormat.getCurrencyInstance(new Locale("vi", "VN")).format(value);
    }

    private int color(int resource) {
        return fragment.requireContext().getColor(resource);
    }

    private int dp(int value) {
        return Math.round(value * fragment.getResources().getDisplayMetrics().density);
    }
}
