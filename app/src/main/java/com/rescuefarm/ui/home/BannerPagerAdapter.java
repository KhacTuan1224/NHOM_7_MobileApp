package com.rescuefarm.ui.home;

import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.google.android.material.card.MaterialCardView;
import com.rescuefarm.domain.model.Banner;
import java.util.ArrayList;
import java.util.List;

/** Full-bleed, rounded home banner carousel pages. */
public final class BannerPagerAdapter extends RecyclerView.Adapter<BannerPagerAdapter.Holder> {
    public interface Listener { void onBannerClick(Banner banner); }

    private final List<Banner> items = new ArrayList<>();
    private final Listener listener;

    public BannerPagerAdapter(Listener listener) { this.listener = listener; }

    public void submit(List<Banner> values) {
        items.clear();
        if (values != null) items.addAll(values);
        notifyDataSetChanged();
    }

    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        MaterialCardView card = new MaterialCardView(parent.getContext());
        card.setRadius(dp(parent, 14));
        card.setStrokeWidth(dp(parent, 1));
        card.setStrokeColor(parent.getContext().getColor(com.rescuefarm.R.color.rescue_outline));
        card.setCardElevation(0);
        card.setMaxCardElevation(0);
        card.setUseCompatPadding(false);
        FrameLayout frame = new FrameLayout(parent.getContext());
        ImageView image = new ImageView(parent.getContext());
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        frame.addView(image, new FrameLayout.LayoutParams(-1, -1));

        TextView title = new TextView(parent.getContext());
        title.setTextColor(Color.WHITE);
        title.setTextSize(16);
        title.setTypeface(title.getTypeface(), Typeface.BOLD); com.rescuefarm.ui.common.AppTypography.title(title);
        title.setMaxLines(2);
        title.setPadding(dp(parent, 12), dp(parent, 8), dp(parent, 12), dp(parent, 8));
        title.setBackground(new ColorDrawable(0xB31A3424));
        FrameLayout.LayoutParams titleParams = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        frame.addView(title, titleParams);
        card.addView(frame, new FrameLayout.LayoutParams(-1, -1));
        card.setLayoutParams(new RecyclerView.LayoutParams(-1, -1));
        return new Holder(card, image, title);
    }

    @Override public void onBindViewHolder(@NonNull Holder holder, int position) {
        Banner banner = items.get(position);
        holder.title.setText(banner.getTitle());
        Glide.with(holder.image).load(banner.getImageUrl()).centerCrop().into(holder.image);
        holder.itemView.setOnClickListener(view -> listener.onBannerClick(banner));
    }

    @Override public int getItemCount() { return items.size(); }

    private static int dp(ViewGroup parent, int value) {
        return Math.round(value * parent.getResources().getDisplayMetrics().density);
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final ImageView image;
        final TextView title;
        Holder(@NonNull View itemView, ImageView image, TextView title) {
            super(itemView); this.image = image; this.title = title;
        }
    }
}
