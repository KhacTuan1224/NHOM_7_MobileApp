package com.rescuefarm.ui.cart;

import org.json.JSONArray;
import org.json.JSONObject;
import java.text.NumberFormat;
import java.util.Locale;

/** Display server-owned thresholds; do not calculate payable discounts on the device. */
public final class BuyMoreUi {
    private BuyMoreUi() { }
    public static String explain(JSONObject quote) {
        JSONObject policy = quote.optJSONObject("buyMorePolicy");
        if (policy == null) return "Ưu đãi mua nhiều cần máy chủ cập nhật. Giá cuối được kiểm tra khi đặt hàng.";
        NumberFormat money = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("vi-VN"));
        StringBuilder out = new StringBuilder("Càng mua càng giảm · tính trên tiền hàng sau khuyến mãi sản phẩm, không gồm phí giao.");
        out.append("\nCùng Seller: ").append(tiers(policy.optJSONArray("seller"), money));
        out.append("\nÍt nhất 2 Seller: ").append(tiers(policy.optJSONArray("platform"), money));
        out.append(" (sau giảm cùng Seller).");
        JSONArray groups = quote.optJSONArray("groups");
        if (groups != null) for (int i = 0; i < groups.length(); i++) {
            JSONObject group = groups.optJSONObject(i); if (group == null) continue;
            Double next = next(group.optDouble("subtotal"), policy.optJSONArray("seller"));
            if (next != null) out.append("\n").append(group.optString("sellerName")).append(": thêm ").append(money.format(next)).append(" để lên bậc cùng Seller tiếp theo.");
        }
        if (quote.optInt("sellerCount") < 2) out.append("\nChọn thêm sản phẩm từ cửa hàng khác để xét ưu đãi nhiều Seller.");
        else if (groups != null) {
            double eligible = 0;
            for (int i = 0; i < groups.length(); i++) if (groups.optJSONObject(i) != null) eligible += groups.optJSONObject(i).optDouble("eligibleSubtotal");
            Double next = next(eligible, policy.optJSONArray("platform"));
            if (next != null) out.append("\nCòn ").append(money.format(next)).append(" tiền hàng đủ điều kiện để lên bậc nhiều Seller tiếp theo.");
        }
        return out.toString();
    }
    private static String tiers(JSONArray tiers, NumberFormat money) {
        StringBuilder text = new StringBuilder();
        if (tiers != null) for (int i = 0; i < tiers.length(); i++) {
            JSONObject tier = tiers.optJSONObject(i); if (tier == null) continue;
            if (text.length() > 0) text.append("; ");
            text.append("từ ").append(money.format(tier.optDouble("minimum"))).append(" giảm ").append(tier.optInt("percent")).append('%');
        }
        return text.toString();
    }
    private static Double next(double amount, JSONArray tiers) {
        if (tiers != null) for (int i = 0; i < tiers.length(); i++) {
            JSONObject tier = tiers.optJSONObject(i);
            if (tier != null && tier.optDouble("minimum") > amount) return tier.optDouble("minimum") - amount;
        }
        return null;
    }
}
