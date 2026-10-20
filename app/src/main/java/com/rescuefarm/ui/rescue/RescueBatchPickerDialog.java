package com.rescuefarm.ui.rescue;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.Normalizer;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Predicate;
import static com.rescuefarm.ui.rescue.RescueUi.*;

/** Searchable, live-updating picker. Unavailable stock stays visible with a reason. */
final class RescueBatchPickerDialog {
    private final LinearLayout rows;
    private final TextView status;
    private final EditText search;
    private final AlertDialog dialog;
    private final View retry;
    private final Predicate<String> selected;
    private final Consumer<JSONObject> choose;
    private final long pickupEndAt;
    private JSONArray items = new JSONArray();
    private boolean loading, hasMore;
    private String error = "";

    RescueBatchPickerDialog(Context context, long pickupEndAt, Predicate<String> selected,
            Consumer<JSONObject> choose, Runnable refresh, Runnable dismissed) {
        this.pickupEndAt = pickupEndAt; this.selected = selected; this.choose = choose;
        LinearLayout root = column(context);
        int padding = dp(context, 20); root.setPadding(padding, 0, padding, 0);
        text(root, "Tìm theo tên nông sản hoặc mã lô. Lô không chọn được sẽ có lý do bên dưới.", false);
        search = new EditText(context);
        search.setHint("Tìm tên nông sản hoặc mã lô"); search.setSingleLine(true);
        search.setTextSize(16); search.setMinHeight(dp(context, 48));
        root.addView(search, new LinearLayout.LayoutParams(-1, -2));
        status = text(root, "Đang tải lô của cửa hàng…", false);
        ScrollView scroll = new ScrollView(context); scroll.setFillViewport(true);
        int height = Math.min(dp(context, 380), (int) (context.getResources().getDisplayMetrics().heightPixels * .42f));
        root.addView(scroll, new LinearLayout.LayoutParams(-1, height));
        rows = column(context); scroll.addView(rows);
        retry = button(root, "Tải lại danh sách lô", refresh);
        dialog = new MaterialAlertDialogBuilder(context).setTitle("Chọn lô nông sản")
                .setView(root).setNegativeButton("Đóng", null).create();
        dialog.setOnDismissListener(d -> dismissed.run());
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            public void onTextChanged(CharSequence s, int start, int before, int count) { render(); }
            public void afterTextChanged(Editable s) { }
        });
    }
    void show() { dialog.show(); }
    void dismiss() { dialog.dismiss(); }
    void update(JSONArray items, boolean loading, boolean hasMore, String error) {
        this.items = items; this.loading = loading; this.hasMore = hasMore; this.error = error; render();
    }
    private static String normalized(String value) {
        return Normalizer.normalize(value.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").replace('đ', 'd').trim();
    }
    private String reason(JSONObject batch) {
        if (selected.test(batch.optString("batchId"))) return "Đã thêm vào gói này.";
        if (!batch.optBoolean("selectable", true)) return batch.optString("unavailableReason", "Lô chưa đủ điều kiện.");
        if (batch.optLong("expiryDate") <= pickupEndAt) return "Hạn dùng phải sau giờ kết thúc nhận. Hãy đổi lịch nhận hoặc chọn lô khác.";
        return "";
    }
    private void render() {
        rows.removeAllViews(); String keyword = normalized(search.getText().toString()); int matching = 0;
        NumberFormat quantity = NumberFormat.getNumberInstance(Locale.forLanguageTag("vi-VN"));
        quantity.setMaximumFractionDigits(6);
        for (int i = 0; i < items.length(); i++) {
            JSONObject batch = items.optJSONObject(i); if (batch == null) continue;
            String name = batch.optString("productName"), id = batch.optString("batchId");
            if (!normalized(name + " " + id).contains(keyword)) continue;
            matching++;
            LinearLayout row = card(rows);
            text(row, name, true);
            text(row, "Khả dụng: " + quantity.format(batch.optDouble("availableQuantity")) + " " + batch.optString("unit"), false);
            text(row, "Hạn dùng: " + date(batch.optLong("expiryDate")), false);
            TextView code = text(row, "Mã lô: " + id, false); code.setTextSize(12);
            String reason = reason(batch);
            if (reason.isEmpty()) button(row, "Chọn lô này", () -> { dialog.dismiss(); choose.accept(batch); });
            else {
                TextView explanation = text(row, reason, false);
                explanation.setTextColor(row.getContext().getColor(com.rescuefarm.R.color.rescue_critical));
            }
        }
        status.setText(!error.isEmpty() ? "Chưa tải hết danh sách: " + error
                : loading || hasMore ? "Đã tải " + items.length() + " lô · đang tải tiếp…"
                : "Đã tải đủ " + items.length() + " lô · tìm thấy " + matching);
        retry.setEnabled(!loading);
        if (matching == 0) text(rows, loading || hasMore ? "Đang tìm trong các trang tiếp theo…"
                : keyword.isEmpty() ? "Cửa hàng chưa có lô hàng."
                : "Không tìm thấy lô phù hợp. Kiểm tra tên, mã lô hoặc tải lại sau khi lưu lô mới.", false);
    }
}
