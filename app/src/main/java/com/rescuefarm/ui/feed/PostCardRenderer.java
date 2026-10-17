package com.rescuefarm.ui.feed;
import com.rescuefarm.ui.common.EnumLabels;

import android.graphics.Typeface;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.FrameLayout;
import android.widget.TextView;
import androidx.fragment.app.Fragment;
import com.bumptech.glide.Glide;
import com.google.android.material.card.MaterialCardView;
import com.rescuefarm.domain.model.Post;
import java.text.DateFormat;
import java.util.Locale;

public final class PostCardRenderer {
    private final Fragment fragment;
    public PostCardRenderer(Fragment fragment) { this.fragment = fragment; }
    public View card(Post post, boolean showModerationStatus, View.OnClickListener listener) {
        MaterialCardView card = new MaterialCardView(fragment.requireContext());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, 0, 0, dp(12)); card.setLayoutParams(params);
        card.setRadius(dp(16)); card.setStrokeWidth(dp(1)); card.setCardElevation(0);
        LinearLayout body = new LinearLayout(fragment.requireContext()); body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(14), dp(14), dp(14), dp(14));
        if (!post.getImageUrls().isEmpty()) {
            ImageView image = new ImageView(fragment.requireContext()); image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            image.setContentDescription("Ảnh bài viết giải cứu"); body.addView(image, new LinearLayout.LayoutParams(-1, dp(170)));
            Glide.with(fragment).load(post.getImageUrls().get(0)).centerCrop().into(image);
        }
        body.addView(text(EnumLabels.label(post.getUrgencyLevel()), 12, true));
        body.addView(text(post.getTitle(), 19, true));
        String excerpt = post.getContent().length() > 180 ? post.getContent().substring(0, 180) + "…" : post.getContent();
        body.addView(text(excerpt, 14, false));
        if (showModerationStatus) body.addView(text("Trạng thái: " + EnumLabels.label(post.getStatus()), 13, true));
        if (post.getCreatedAt() != null) body.addView(text(DateFormat.getDateTimeInstance(
                DateFormat.SHORT, DateFormat.SHORT, Locale.forLanguageTag("vi-VN")).format(post.getCreatedAt()), 12, false));
        card.addView(body, new FrameLayout.LayoutParams(-1, -2)); card.setOnClickListener(listener); return card;
    }
    public View homeCard(Post post, View.OnClickListener listener) {
        MaterialCardView card = new MaterialCardView(fragment.requireContext());
        card.setRadius(dp(16)); card.setStrokeWidth(dp(1)); card.setStrokeColor(
                fragment.getResources().getColor(com.rescuefarm.R.color.rescue_outline,
                        fragment.requireContext().getTheme()));
        card.setCardBackgroundColor(fragment.getResources().getColor(
                com.rescuefarm.R.color.rescue_surface, fragment.requireContext().getTheme()));
        card.setCardElevation(0);
        LinearLayout body = new LinearLayout(fragment.requireContext());
        body.setOrientation(LinearLayout.VERTICAL); body.setPadding(dp(8), dp(8), dp(8), dp(9));
        if (!post.getImageUrls().isEmpty()) {
            ImageView image = new ImageView(fragment.requireContext());
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            image.setContentDescription("Ảnh bài viết giải cứu");
            MaterialCardView imageFrame = new MaterialCardView(fragment.requireContext());
            imageFrame.setRadius(dp(12)); imageFrame.setStrokeWidth(0); imageFrame.setCardElevation(0);
            imageFrame.setCardBackgroundColor(fragment.getResources().getColor(
                    com.rescuefarm.R.color.rescue_primary_container, fragment.requireContext().getTheme()));
            imageFrame.addView(image, new FrameLayout.LayoutParams(-1, dp(82)));
            body.addView(imageFrame);
            Glide.with(fragment).load(post.getImageUrls().get(0)).centerCrop().into(image);
        }
        TextView title = text(post.getTitle(), 13, true); title.setMaxLines(2);
        body.addView(title);
        TextView excerpt = text(post.getContent(), 11, false); excerpt.setMaxLines(2);
        body.addView(excerpt);
        card.addView(body, new FrameLayout.LayoutParams(-1, -2)); card.setOnClickListener(listener); return card;
    }
    public TextView message(String value) { return text(value, 14, false); }
    private TextView text(String value, int size, boolean bold) {
        TextView view = new TextView(fragment.requireContext()); view.setText(value); view.setTextSize(size);
        view.setPadding(0, dp(4), 0, dp(4)); if (bold) view.setTypeface(view.getTypeface(), Typeface.BOLD); return view;
    }
    private int dp(int value) { return Math.round(value * fragment.getResources().getDisplayMetrics().density); }
}
