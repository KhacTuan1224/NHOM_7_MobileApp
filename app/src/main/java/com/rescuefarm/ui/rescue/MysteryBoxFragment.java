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
    private final JSONObject[] slots = new JSONObject[3];
    private int offset;
    @Override protected String offerType() { return "MYSTERY_BOX"; }
    @Override protected String pageTitle() { return "Hộp bí ẩn"; }
    @Override protected void buildPage() {
        android.content.Context c = requireContext();
        ((com.rescuefarm.ui.common.ResponsiveContentLayout) body).setMaxContentWidth(dp(c, 560));
//        TextView tag = label(body, "MỘT CHÚT BẤT NGỜ, THÊM MỘT ĐIỀU TỐT", 10, PURPLE, true);
//        tag.setLetterSpacing(.11f); tag.setGravity(android.view.Gravity.CENTER);
//        TextView title = heading(body, "Hộp nhỏ.\nNiềm vui lớn.", 35, INK); title.setGravity(android.view.Gravity.CENTER);
        scene = new GiftRevealView(c);
        body.addView(scene, new LinearLayout.LayoutParams(-1, -2));
        scene.setOnOpened(this::reveal);
        hint = label(body, "Đang tìm những hộp nông sản mới…", 13, MUTED, false); hint.setGravity(android.view.Gravity.CENTER);
        hint.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        result = col(c); body.addView(result); result.setVisibility(View.GONE);
        change = action(body, "Đổi bộ hộp  ↻", PURPLE, () -> { offset += 3; renderOffers(); });
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
        }
        hint.setText(count == 0 ? "Hộp mới sắp lên kệ. Chạm một hộp để mở thử nhé!"
                : count < 3 ? "Có " + count + " hộp đang bán. Các hộp còn lại cho phép mở thử."
                : "Chạm một hộp bất kỳ. Điều bất ngờ đang chờ bạn!");
        change.setVisibility(available.size() > 3 ? View.VISIBLE : View.GONE);
    }
    private void showOpening() {
        hint.setText("Một điều tốt lành đang được mở ra…");
    }
    @Override protected void onTimeChanged(long now) {
        if (scene != null && scene.isOpening()) showOpening();
        for (int i = 0; i < 3; i++) if (slots[i] != null && slots[i].optLong("closesAt") <= now) {
            scene.setSlotAvailable(i, false);
        }
    }
    private void reveal(int index) {
        JSONObject offer = slots[index]; if (getView() == null) return;
        if (offer == null || offer.optLong("closesAt") <= System.currentTimeMillis()) {
            hint.setText("Một bất ngờ mới đang được chuẩn bị."); result.removeAllViews();
            LinearLayout preview = col(requireContext());
            preview.setPadding(dp(requireContext(), 20), dp(requireContext(), 20), dp(requireContext(), 20), dp(requireContext(), 20));
            result.addView(preview);
            heading(preview, "Hộp mới sắp lên kệ", 25, PURPLE);
            label(preview, "Bạn vừa mở thử hộp quà. Cửa hàng đang chuẩn bị thêm nông sản; hãy quay lại sau hoặc chọn hộp đang bán khác nhé.", 15, MUTED, false);
            action(preview, "Mở hộp khác", PURPLE, this::renderOffers);
            showResult(); return;
        }
        hint.setText("Bạn đã tìm thấy một cơ hội giải cứu!"); result.removeAllViews();
        LinearLayout reward = col(requireContext());
        int padding = dp(requireContext(), 20);
        reward.setPadding(padding, padding, padding, padding);
        LinearLayout.LayoutParams rewardParams = new LinearLayout.LayoutParams(-1, -2);
        rewardParams.topMargin = dp(requireContext(), 14);
        result.addView(reward, rewardParams);
        heading(reward, offer.optString("title"), 25, INK);
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
