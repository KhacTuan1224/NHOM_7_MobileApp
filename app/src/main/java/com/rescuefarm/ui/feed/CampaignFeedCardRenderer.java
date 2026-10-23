package com.rescuefarm.ui.feed;

import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.fragment.app.Fragment;
import com.bumptech.glide.Glide;
import com.rescuefarm.R;
import com.rescuefarm.domain.model.Product;
import com.rescuefarm.domain.model.RescueCampaign;
import com.rescuefarm.service.backend.RenderBackendClient;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;

/** Compact public campaign article. Private account profiles are never queried. */
public final class CampaignFeedCardRenderer {
    private final Fragment fragment;
    private final Map<String, View> visible = new HashMap<>();
    private final Map<String, JSONObject> cached = new HashMap<>();
    private final Map<String, Long> loadedAt = new HashMap<>(), attemptedAt = new HashMap<>();
    private final java.util.Set<String> inFlight = new java.util.HashSet<>();
    private int generation;
    public CampaignFeedCardRenderer(Fragment fragment) { this.fragment = fragment; }

    /** One bounded request per visible page; callbacks cannot bind views from an old page. */
    public void beginPage(List<RescueCampaign> campaigns) {
        visible.clear();
        int requestGeneration = generation;
        long now = android.os.SystemClock.elapsedRealtime();
        List<String> ids = new ArrayList<>();
        for (RescueCampaign campaign : campaigns) {
            String id = campaign.getId();
            if (!inFlight.contains(id) && now - attemptedAt.getOrDefault(id, -60000L) >= 60000L && !ids.contains(id)) ids.add(id);
        }
        for (int offset = 0; offset < ids.size(); offset += 12) {
            JSONArray batch = new JSONArray(ids.subList(offset, Math.min(ids.size(), offset + 12)));
            JSONObject request = new JSONObject();
            try { request.put("campaignIds", batch); } catch (org.json.JSONException ignored) { continue; }
            List<String> requested = new ArrayList<>(ids.subList(offset, Math.min(ids.size(), offset + 12)));
            for (String id : requested) { inFlight.add(id); attemptedAt.put(id, now); }
            RenderBackendClient.postGuest("/campaigns/cards", request, new RenderBackendClient.Callback() {
                public void onSuccess(JSONObject response) {
                    if (requestGeneration != generation || !fragment.isAdded() || fragment.getView() == null) return;
                    inFlight.removeAll(requested);
                    JSONObject cards = response.optJSONObject("cards"); if (cards == null) return;
                    java.util.Iterator<String> keys = cards.keys();
                    while (keys.hasNext()) {
                        String id = keys.next(); JSONObject value = cards.optJSONObject(id); if (value == null) continue;
                        cached.put(id, value); loadedAt.put(id, android.os.SystemClock.elapsedRealtime());
                        View root = visible.get(id);
                        if (root != null) bindMetadata(root, value);
                    }
                }
                public void onError(String message) {
                    if (requestGeneration == generation) inFlight.removeAll(requested);
                    // Keep safe defaults, never infer approval from a campaign or account avatar.
                }
            });
        }
    }

    public View card(RescueCampaign campaign, List<Product> products, View.OnClickListener listener) {
        View root = LayoutInflater.from(fragment.requireContext()).inflate(R.layout.item_campaign_article, null, false);
        root.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        ((LinearLayout.LayoutParams) root.getLayoutParams()).bottomMargin = dp(14);
        ((TextView) root.findViewById(R.id.articleTitle)).setText(campaign.getTitle());
        TextView story = root.findViewById(R.id.articleStory);
        story.setText(campaign.getDescription());
        story.setVisibility(clean(campaign.getDescription()).isEmpty() ? View.GONE : View.VISIBLE);
        TextView productLine = root.findViewById(R.id.articleProducts);
        StringBuilder names = new StringBuilder();
        if (products != null) for (int i = 0; i < Math.min(products.size(), 2); i++) {
            if (i > 0) names.append(" · "); names.append(products.get(i).getName());
        }
        productLine.setText(names.length() == 0 ? fragment.getString(R.string.article_batch_summary,
                campaign.getBatchTargets().size()) : names.toString());
        ProgressBar progress = root.findViewById(R.id.articleProgress);
        TextView progressText = root.findViewById(R.id.articleProgressText);
        double progressPercent = campaign.calculateProgress();
        com.rescuefarm.ui.common.RescueProgressUtils.applyProgress(progress, progressPercent);
        if ("REVIEW_REQUIRED".equals(campaign.getUnitStatus())) {
            progress.setVisibility(View.GONE); progressText.setText("Đang đối soát đơn vị giải cứu");
        } else {
            NumberFormat quantity = NumberFormat.getNumberInstance(Locale.forLanguageTag("vi-VN"));
            quantity.setMaximumFractionDigits(2);
            progressText.setText("Đã cứu " + quantity.format(campaign.getRescuedQuantity()) + " / "
                    + quantity.format(campaign.getTargetQuantity()) + " " + campaign.getQuantityUnitLabel()
                    + " · " + Math.round(progressPercent) + "%");
            progressText.setTextColor(com.rescuefarm.ui.common.RescueProgressUtils.getProgressColor(fragment.requireContext(), progressPercent));
        }
        ImageView image = root.findViewById(R.id.articleImage);
        String url = campaign.getImageUrls().isEmpty() ? "" : campaign.getImageUrls().get(0);
        if (url.isEmpty() && products != null && !products.isEmpty() && !products.get(0).getImageUrls().isEmpty())
            url = products.get(0).getImageUrls().get(0);
        if (url.isEmpty()) image.setVisibility(View.GONE);
        else Glide.with(fragment).load(url).centerCrop().into(image);
        root.findViewById(R.id.articleOpenButton).setOnClickListener(listener);
        root.setOnClickListener(listener);
        JSONObject initial = new JSONObject();
        try { initial.put("location", clean(campaign.getLocationName())); } catch (org.json.JSONException ignored) { }
        bindMetadata(root, initial);
        if (cached.containsKey(campaign.getId()) && android.os.SystemClock.elapsedRealtime()
                - loadedAt.getOrDefault(campaign.getId(), 0L) < 60000L) bindMetadata(root, cached.get(campaign.getId()));
        visible.put(campaign.getId(), root);
        return root;
    }

    private void bindMetadata(View root, JSONObject value) {
        TextView shop = root.findViewById(R.id.articleShopName);
        String name = value.optString("shopName").trim();
        shop.setText(name.isEmpty() ? fragment.getString(R.string.article_shop_default) : name);
        ImageView avatar = root.findViewById(R.id.articleShopAvatar);
        String avatarUrl = value.optString("avatarUrl");
        Glide.with(fragment).load(avatarUrl.startsWith("https://") ? avatarUrl : null)
                .placeholder(R.drawable.ic_store).error(R.drawable.ic_store).fallback(R.drawable.ic_store).circleCrop().into(avatar);
        boolean verified = value.optBoolean("verified", false);
        Drawable tick = androidx.core.content.ContextCompat.getDrawable(fragment.requireContext(), R.drawable.ic_shop_verified);
        if (tick != null) {
            tick = tick.mutate();
            tick.setTint(color(verified ? R.color.rescue_primary : R.color.rescue_on_surface_variant));
            tick.setBounds(0, 0, dp(16), dp(16));
        }
        shop.setCompoundDrawablesRelative(null, null, tick, null);
        shop.setContentDescription(fragment.getString(verified ? R.string.article_verified_description
                : R.string.article_unverified_description, shop.getText()));
        long postedAt = value.optLong("postedAt", 0);
        String when = postedAt > 0 ? fragment.getString(R.string.article_posted_at,
                new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.forLanguageTag("vi-VN")).format(new Date(postedAt)))
                : fragment.getString(R.string.article_unknown_time);
        String location = value.optString("location").trim();
        TextView details = root.findViewById(R.id.articleMetadata);
        String distance = details.getTag() instanceof String ? (String) details.getTag() : "";
        details.setText(when + (location.isEmpty() ? "" : " · " + location) + (distance.isEmpty() ? "" : " · " + distance));
        JSONArray products = value.optJSONArray("products");
        if (products != null && products.length() > 0) {
            StringBuilder line = new StringBuilder();
            NumberFormat money = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("vi-VN"));
            for (int i = 0; i < Math.min(2, products.length()); i++) {
                JSONObject product = products.optJSONObject(i); if (product == null) continue;
                if (line.length() > 0) line.append(" · "); line.append(product.optString("name"));
                if (i == 0 && product.optDouble("rescuePrice", 0) > 0)
                    line.append(" — ").append(money.format(product.optDouble("rescuePrice"))).append("/").append(product.optString("unit"));
            }
            ((TextView) root.findViewById(R.id.articleProducts)).setText(line.toString());
        }
    }
    public void clear() {
        ++generation; visible.clear(); cached.clear(); loadedAt.clear(); attemptedAt.clear(); inFlight.clear();
    }
    public View message(String value) {
        TextView text = new TextView(fragment.requireContext()); text.setText(value);
        com.rescuefarm.ui.common.AppTypography.body(text);
        text.setPadding(dp(16), dp(18), dp(16), dp(18)); text.setTextColor(color(R.color.rescue_on_surface_variant)); return text;
    }
    private int color(int id) { return androidx.core.content.ContextCompat.getColor(fragment.requireContext(), id); }
    private String clean(String value) { return value == null ? "" : value.trim(); }
    private int dp(int value) { return Math.round(value * fragment.getResources().getDisplayMetrics().density); }
}
