package com.rescuefarm.ui.rescue;

import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.google.android.material.button.MaterialButton;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import static com.rescuefarm.ui.rescue.RescueMarketUi.*;

public final class MysteryBoxFragment extends RescueMarketFragment {
    private GiftRevealView scene;
    private LinearLayout result;
    private TextView hint;
    private MaterialButton change;
    private final MaterialButton[] choices = new MaterialButton[3];
    private final JSONObject[] slots = new JSONObject[3];
    private int offset;
    @Override protected String offerType() { return "MYSTERY_BOX"; }
    @Override protected String pageTitle() { return "Hộp bí ẩn"; }
    @Override protected void buildPage() {
        android.content.Context c = requireContext();
        ((com.rescuefarm.ui.common.ResponsiveContentLayout) body).setMaxContentWidth(dp(c, 560));
        TextView tag = label(body, "MỘT CHÚT BẤT NGỜ, THÊM MỘT ĐIỀU TỐT", 10, PURPLE, true);
        tag.setLetterSpacing(.11f); tag.setGravity(android.view.Gravity.CENTER);
        TextView title = label(body, "Hộp nhỏ.\nNiềm vui lớn.", 35, INK, true); title.setGravity(android.view.Gravity.CENTER);
        TextView subtitle = label(body, "Chọn một hộp, chạm mở điều bất ngờ\nvà cho nông sản thêm một cơ hội.", 14, MUTED, false);
        subtitle.setGravity(android.view.Gravity.CENTER);
        scene = new GiftRevealView(c);
        body.addView(scene, new LinearLayout.LayoutParams(-1, -2));
        scene.setOnOpened(this::reveal);
        LinearLayout controls = row(c); body.addView(controls);
        for (int i = 0; i < 3; i++) {
            final int index = i;
            choices[i] = action(controls, "Hộp " + (i + 1), PURPLE, () -> {
                if (scene.open(index) && scene.isOpening()) showOpening();
            });
            choices[i].setTextSize(13);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1); if (i > 0) p.leftMargin = dp(c, 8);
            choices[i].setLayoutParams(p);
        }
        hint = label(body, "Đang tìm những hộp nông sản mới…", 13, MUTED, false); hint.setGravity(android.view.Gravity.CENTER);
        hint.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        result = col(c); body.addView(result); result.setVisibility(View.GONE);
        change = action(body, "Đổi bộ hộp  ↻", PURPLE, () -> { offset += 3; renderOffers(); });
        LinearLayout note = panel(body, 0xFFF8F6FC, 18);
        label(note, "Bất ngờ bên trong. Rõ ràng khi mua.", 16, INK, true);
        label(note, "Mở hộp miễn phí để khám phá gói. Bạn xem giá, giờ nhận và xác nhận giữ suất trước khi mua. Thành phần cụ thể được giữ bí mật đến khi nhận hàng.", 13, MUTED, false);
    }
    @Override protected void renderOffers() {
        if (scene == null) return;
        scene.reset(); clearCountdowns(); result.setVisibility(View.GONE); result.removeAllViews();
        List<JSONObject> available = new ArrayList<>(); long now = System.currentTimeMillis();
        for (int i = 0; i < offers.length(); i++) {
            JSONObject o = offers.optJSONObject(i);
            if (o != null && "MYSTERY_BOX".equals(o.optString("type")) && o.optLong("closesAt") > now && o.optInt("availableUnits") > 0) available.add(o);
        }
        int count = Math.min(3, available.size()); scene.setAvailable(count);
        for (int i = 0; i < 3; i++) {
            slots[i] = i < count ? available.get((offset + i) % available.size()) : null;
            scene.setPreview(i, i >= count);
            choices[i].setEnabled(true); choices[i].setAlpha(i < count ? 1 : .65f);
            choices[i].setText((i < count ? "Hộp " : "Mở thử ") + (i + 1));
            choices[i].setContentDescription(i < count ? "Mở hộp " + (i + 1) : "Mở thử animation hộp " + (i + 1) + ", chưa có gói đang bán");
        }
        hint.setText(count == 0 ? "Hộp mới sắp lên kệ. Chạm một hộp để mở thử nhé!"
                : count < 3 ? "Có " + count + " hộp đang bán. Các hộp còn lại cho phép mở thử."
                : "Chạm một hộp bất kỳ. Điều bất ngờ đang chờ bạn!");
        change.setVisibility(available.size() > 3 ? View.VISIBLE : View.GONE);
    }
    private void showOpening() {
        hint.setText("Một điều tốt lành đang được mở ra…"); for (MaterialButton choice : choices) choice.setEnabled(false);
    }
    @Override protected void onTimeChanged(long now) {
        if (scene != null && scene.isOpening()) showOpening();
        for (int i = 0; i < 3; i++) if (slots[i] != null && slots[i].optLong("closesAt") <= now) {
            choices[i].setEnabled(false); scene.setSlotAvailable(i, false);
        }
    }
    private void reveal(int index) {
        JSONObject offer = slots[index]; if (getView() == null) return;
        for (MaterialButton choice : choices) choice.setEnabled(false);
        if (offer == null || offer.optLong("closesAt") <= System.currentTimeMillis()) {
            hint.setText("Một bất ngờ mới đang được chuẩn bị."); result.removeAllViews();
            LinearLayout preview = panel(result, 0xFFF8F4FF, 20);
            label(preview, "Hộp mới sắp lên kệ", 25, PURPLE, true);
            label(preview, "Bạn vừa mở thử hộp quà. Cửa hàng đang chuẩn bị thêm nông sản; hãy quay lại sau hoặc chọn hộp đang bán khác nhé.", 15, MUTED, false);
            action(preview, "Mở hộp khác", PURPLE, this::renderOffers);
            showResult(); return;
        }
        hint.setText("Bạn đã tìm thấy một cơ hội giải cứu!"); result.removeAllViews();
        LinearLayout reward = panel(result, 0xFFFFFAED, 20);
        label(reward, "DÀNH CHO BẠN", 10, PURPLE, true).setLetterSpacing(.16f);
        label(reward, offer.optString("title"), 25, INK, true);
        label(reward, offer.optString("categoryLabel") + " • " + offer.optString("sellerName"), 13, MUTED, false);
        label(reward, RescueUi.money(offer.optDouble("unitPrice")) + " / " + offer.optString("unitLabel"), 27, PURPLE, true);
        label(reward, "Giá trị gốc " + RescueUi.money(offer.optDouble("originalUnitPrice")) + " • Còn " + offer.optInt("availableUnits") + " suất", 13, MUTED, false);
        label(reward, offer.optString("contentsDisclosure"), 14, INK, false);
        label(reward, "Nhận từ " + RescueUi.date(offer.optLong("pickupStartAt")) + "\n" + offer.optString("pickupAddress"), 13, MUTED, false);
        TextView clock = label(reward, "", 15, PURPLE, true);
        MaterialButton claim = action(reward, "Xem hộp & giữ suất  ›", PURPLE, () -> openOffer(offer));
        countdown(clock, offer.optLong("closesAt"), claim);
        action(reward, "Mở hộp khác", 0xFF9B8AAF, () -> { countdowns.clear(); renderOffers(); });
        showResult();
    }
    private void showResult() {
        result.setVisibility(View.VISIBLE); result.setAlpha(0); result.setTranslationY(dp(requireContext(), 20));
        result.animate().alpha(1).translationY(0).setDuration(450).start();
        result.post(() -> { if (body != null && body.getParent() instanceof androidx.core.widget.NestedScrollView)
            ((androidx.core.widget.NestedScrollView) body.getParent()).smoothScrollTo(0, Math.max(0, result.getTop() - dp(requireContext(), 180))); });
    }
    @Override public void onDestroyView() {
        if (scene != null) { scene.setOnOpened(null); scene.reset(); scene = null; }
        super.onDestroyView();
    }
}
