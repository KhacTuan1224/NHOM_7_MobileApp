package com.rescuefarm.ui.rescue;

import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.rescuefarm.R;
import com.rescuefarm.ui.common.AppTypography;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import static com.rescuefarm.ui.rescue.RescueMarketUi.*;

public final class FlashSaleFragment extends RescueMarketFragment {
    private LinearLayout list;
    private TextView heroClock, count;
    private MaterialButton soon, cheap;
    private boolean priceSort;
    @Override protected String offerType() { return "FLASH"; }
    @Override protected String pageTitle() { return "FLASH SALE"; }
    @Override protected void buildPage() {
        android.content.Context c = requireContext();
        LinearLayout hero = panel(body, RED, 22);
        GradientDrawable gradient = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xFFE2472C, 0xFFFF8A49});
        gradient.setCornerRadius(dp(c, 22)); hero.setBackground(gradient);
//        label(hero, "GIẢI CỨU GIỜ CHÓT", 11, 0xFFFFE7D8, true).setLetterSpacing(.16f);
        LinearLayout headline = row(c); hero.addView(headline);
        LinearLayout words = col(c); headline.addView(words, new LinearLayout.LayoutParams(0, -2, 1));
        heading(words, "Săn giá tốt.\nCứu nông sản ngon.", 29, 0xFFFFFFFF);
        ImageView bolt = new ImageView(c); bolt.setImageResource(R.drawable.ic_rescue_flash);
        headline.addView(bolt, new LinearLayout.LayoutParams(dp(c, 64), dp(c, 80)));
//        label(hero, "Mua vừa đủ • Nhận gần bạn • Bớt lãng phí", 12, 0xFFFFEEE4, false);
        LinearLayout timer = row(c); timer.setPadding(dp(c, 12), dp(c, 10), dp(c, 12), dp(c, 10));
        timer.setBackground(background(0xFF9E2B1C, 13, c));
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-1, -2); tp.topMargin = dp(c, 18); hero.addView(timer, tp);
        TextView caption = new TextView(c); caption.setText("Gói gần hết giờ"); caption.setTextColor(0xFFFFFFFF); caption.setTextSize(12);
        timer.addView(caption, new LinearLayout.LayoutParams(0, -2, 1));
        AppTypography.body(caption);
        heroClock = new TextView(c); heroClock.setText("— : — : —"); heroClock.setTextSize(19);
        heroClock.setTextColor(0xFFFFFFFF); heroClock.setTypeface(AppTypography.bodyTypeface(c), Typeface.BOLD);
        heroClock.setFontFeatureSettings("'tnum' 1"); timer.addView(heroClock);
        LinearLayout options = row(c); LinearLayout.LayoutParams op = new LinearLayout.LayoutParams(-1, -2); op.topMargin = dp(c, 18); body.addView(options, op);
        soon = action(options, "Sắp hết giờ", RED, () -> { priceSort = false; renderOffers(); });
        soon.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
        cheap = action(options, "Giá thấp", RED, () -> { priceSort = true; renderOffers(); });
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, -2, 1); cp.leftMargin = dp(c, 10); cheap.setLayoutParams(cp);
        count = label(body, "ĐANG TÌM DEAL TƯƠI NGON", 12, MUTED, true); count.setLetterSpacing(.09f);
        list = col(c); body.addView(list);
    }
    @Override protected void renderOffers() {
        list.removeAllViews(); clearCountdowns();
        soon.setAlpha(priceSort ? .45f : 1); cheap.setAlpha(priceSort ? 1 : .45f);
        List<JSONObject> values = new ArrayList<>();
        for (int i = 0; i < offers.length(); i++) { JSONObject o = offers.optJSONObject(i); if (o != null && "FLASH".equals(o.optString("type"))) values.add(o); }
        values.sort(priceSort ? Comparator.comparingDouble(o -> o.optDouble("unitPrice")) : Comparator.comparingLong(o -> o.optLong("closesAt")));
        count.setText(values.size() + " GÓI ĐANG DIỄN RA");
        if (values.isEmpty()) {
            LinearLayout empty = col(requireContext());
            int pad = dp(requireContext(), 22); empty.setPadding(pad, pad, pad, pad); list.addView(empty);
            heading(empty, "Siêu sale sắp mở", 22, INK);
            label(empty, "Chưa có đợt giờ flash sale. Quay lại sau để săn nông sản mới từ cửa hàng nhé!", 15, MUTED, false);
            heroClock.setText("Chưa có gói");
        } else {
            long nearest = values.stream().mapToLong(o -> o.optLong("closesAt")).min().orElse(0);
            countdown(heroClock, nearest, null);
        }
        for (JSONObject offer : values) renderCard(offer);
    }
    private void renderCard(JSONObject offer) {
        android.content.Context c = requireContext(); LinearLayout card = panel(list, 0xFFFFFFFF, 14);
        LinearLayout main = row(c); main.setGravity(android.view.Gravity.TOP); card.addView(main);
        FrameLayout imageFrame = new FrameLayout(c); main.addView(imageFrame, new LinearLayout.LayoutParams(dp(c, 100), dp(c, 122)));
        imageFrame.setBackground(background(CREAM, 16, c)); imageFrame.setClipToOutline(true);
        ImageView image = new ImageView(c); image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setImageResource(R.drawable.ic_rescue_flash); image.setPadding(dp(c, 16), dp(c, 16), dp(c, 16), dp(c, 16));
        imageFrame.addView(image, new FrameLayout.LayoutParams(-1, -1));
        if (!offer.optString("imageUrl").isEmpty()) { image.setPadding(0, 0, 0, 0);
            Glide.with(this).load(offer.optString("imageUrl")).centerCrop().placeholder(R.drawable.ic_rescue_flash).error(R.drawable.ic_rescue_flash).into(image); }
        int reduction = discount(offer.optDouble("originalUnitPrice"), offer.optDouble("unitPrice"));
        TextView badge = new TextView(c); badge.setText("−" + reduction + "%"); badge.setTextSize(12); badge.setTextColor(0xFFFFFFFF);
        badge.setTypeface(null, Typeface.BOLD); badge.setPadding(dp(c, 7), dp(c, 4), dp(c, 7), dp(c, 4)); badge.setBackground(background(RED, 8, c));
        AppTypography.body(badge);
        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(-2, -2, android.view.Gravity.TOP | android.view.Gravity.START); bp.setMargins(dp(c, 6), dp(c, 6), 0, 0); imageFrame.addView(badge, bp);
        LinearLayout detail = col(c); LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(0, -2, 1); dp.leftMargin = dp(c, 13); main.addView(detail, dp);
        TextView title = heading(detail, offer.optString("title"), 17, INK); title.setMaxLines(2); title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        label(detail, offer.optString("sellerName"), 12, MUTED, false);
        label(detail, RescueUi.money(offer.optDouble("unitPrice")), 24, RED, true);
        TextView original = label(detail, RescueUi.money(offer.optDouble("originalUnitPrice")), 12, MUTED, false);
        original.setPaintFlags(original.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
        label(card, "Còn " + offer.optInt("availableUnits") + " / " + offer.optInt("totalUnits") + " suất  •  " + offer.optString("unitLabel"), 12, RED, true);
        ProgressBar stock = new ProgressBar(c, null, android.R.attr.progressBarStyleHorizontal);
        stock.setMax(Math.max(1, offer.optInt("totalUnits"))); stock.setProgress(Math.max(0, offer.optInt("totalUnits") - offer.optInt("availableUnits")));
        stock.setProgressTintList(android.content.res.ColorStateList.valueOf(RED)); stock.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFFFE2D6));
        card.addView(stock, new LinearLayout.LayoutParams(-1, dp(c, 6)));
        LinearLayout bottom = row(c); card.addView(bottom);
        LinearLayout time = col(c); bottom.addView(time, new LinearLayout.LayoutParams(0, -2, 1));
        label(time, "KẾT THÚC SAU", 9, MUTED, true);
        TextView clock = label(time, "", 15, INK, true); clock.setFontFeatureSettings("'tnum' 1");
        MaterialButton claim = action(bottom, "Chớp ngay  ›", RED, () -> openOffer(offer));
        claim.setLayoutParams(new LinearLayout.LayoutParams(dp(c, 135), -2));
        countdown(clock, offer.optLong("closesAt"), claim);
        label(card, "Nhận " + RescueUi.date(offer.optLong("pickupStartAt")) + " – " + RescueUi.date(offer.optLong("pickupEndAt")), 11, MUTED, false);
    }
}
