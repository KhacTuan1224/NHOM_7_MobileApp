package com.rescuefarm.ui.rescue;

import android.widget.LinearLayout;
import com.google.android.material.button.MaterialButton;
import com.rescuefarm.ui.common.ResponsiveActionLayout;
import com.rescuefarm.ui.seller.SellerStatisticsChart;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.IntConsumer;
import static com.rescuefarm.ui.rescue.RescueUi.*;

/** Confirmed receipt totals and period charts. Quantities retain their original units. */
final class RescueImpactPanel {
    private RescueImpactPanel() { }
    static void render(LinearLayout content, JSONObject impact, int days, IntConsumer selectPeriod) {
        NumberFormat number = NumberFormat.getNumberInstance(Locale.forLanguageTag("vi-VN"));
        number.setMaximumFractionDigits(2);
        LinearLayout summary = card(content);
        text(summary, "Tác động đã được xác nhận", true);
        text(summary, number.format(impact.optLong("completedPickups")) + " lượt nhận hoàn tất · toàn thời gian", false);
        text(summary, "Nông sản đã trao đến tay khách", true);
        JSONObject quantities = impact.optJSONObject("quantityByUnit");
        List<String> units = new ArrayList<>();
        if (quantities != null) quantities.keys().forEachRemaining(units::add);
        Collections.sort(units);
        if (units.isEmpty()) text(summary, "Chưa có lượng nông sản được xác nhận.", false);
        for (String unit : units) text(summary, number.format(quantities.optDouble(unit)) + " " + unit, false);
        text(summary, "Chỉ tính khi khách xác nhận đã nhận hàng. Mỗi đơn vị được thống kê riêng.", false);

        LinearLayout period = card(content);
        text(period, "Nhịp giải cứu của cửa hàng", true);
        ResponsiveActionLayout controls = new ResponsiveActionLayout(content.getContext());
        period.addView(controls, new LinearLayout.LayoutParams(-1, -2));
        for (int choice : new int[]{7, 30, 90}) {
            MaterialButton button = new MaterialButton(content.getContext(), null,
                    com.google.android.material.R.attr.materialButtonOutlinedStyle);
            button.setText(choice + " ngày"); button.setAllCaps(false);
            button.setSelected(choice == days); button.setEnabled(choice != days);
            button.setContentDescription(choice + " ngày" + (choice == days ? ", đang chọn" : ""));
            button.setOnClickListener(v -> selectPeriod.accept(choice));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1);
            params.setMarginEnd(dp(content.getContext(), 4)); controls.addView(button, params);
        }
        JSONObject charts = impact.optJSONObject("charts");
        if (charts == null) { text(period, "Chưa có dữ liệu biểu đồ. Hãy thử làm mới sau.", false); return; }
        JSONArray daily = charts.optJSONArray("daily");
        if (daily == null || daily.length() == 0) return;
        String[] labels = new String[daily.length()];
        double[] counts = new double[daily.length()], cumulative = new double[daily.length()];
        for (int i = 0; i < daily.length(); i++) {
            JSONObject day = daily.optJSONObject(i); if (day == null) continue;
            String date = day.optString("date");
            labels[i] = date.length() == 10 ? date.substring(8) + "/" + date.substring(5, 7) : date;
            counts[i] = day.optDouble("completedPickups"); cumulative[i] = day.optDouble("cumulativePickups");
        }
        text(period, number.format(charts.optLong("completedPickups")) + " lượt nhận trong " + days + " ngày gần nhất · giờ Việt Nam (UTC+7)", false);
        if (charts.optLong("completedPickups") == 0) text(period, "Chưa có lượt nhận hoàn tất trong kỳ này. Biểu đồ sẽ cập nhật sau khi khách xác nhận nhận hàng.", false);
        chart(content, "Lượt nhận theo ngày", "Mỗi cột là số lượt nhận hoàn tất trong ngày.", labels, counts, SellerStatisticsChart.Kind.COLUMN);
        chart(content, "Lũy kế lượt nhận trong kỳ", "Đường biểu diễn bắt đầu từ đầu kỳ đã chọn, không phải tổng toàn thời gian.", labels, cumulative, SellerStatisticsChart.Kind.LINE);
        JSONObject byType = charts.optJSONObject("byType");
        List<String> methods = new ArrayList<>(); List<Double> shares = new ArrayList<>();
        if (byType != null) for (String method : new String[]{"FLASH", "MYSTERY_BOX", "OTHER"}) {
            double count = byType.optDouble(method);
            if (count > 0) {
                String name = method.equals("FLASH") ? "Flash sale" : method.equals("MYSTERY_BOX") ? "Hộp bí ẩn" : "Chưa phân loại";
                methods.add(name + " (" + number.format(count * 100 / Math.max(1, charts.optDouble("completedPickups"))) + "%)");
                shares.add(count);
            }
        }
        double[] values = new double[shares.size()]; for (int i = 0; i < values.length; i++) values[i] = shares.get(i);
        chart(content, "Tỷ trọng theo phương thức", "Tính theo lượt nhận hoàn tất trong kỳ, không theo kg hay số suất.",
                methods.toArray(new String[0]), values, SellerStatisticsChart.Kind.RING);
    }
    private static void chart(LinearLayout content, String title, String description, String[] labels,
                              double[] values, SellerStatisticsChart.Kind kind) {
        LinearLayout panel = card(content); text(panel, title, true); text(panel, description, false);
        SellerStatisticsChart view = new SellerStatisticsChart(content.getContext(), labels, values, kind);
        view.setContentDescription(title + ". " + view.getContentDescription());
        panel.addView(view, new LinearLayout.LayoutParams(-1, -2));
    }
}
