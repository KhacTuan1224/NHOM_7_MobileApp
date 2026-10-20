package com.rescuefarm.ui.rescue;

import android.content.Context;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.rescuefarm.R;
import org.json.JSONObject;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

final class RescueUi {
    static final String[] TYPES = {"", "MYSTERY_BOX", "GROUP_BUY", "SUBSCRIPTION", "BULK", "DONATION", "FLASH"};
    static JSONObject body(Object... values) {
        JSONObject result = new JSONObject();
        try { for (int i = 0; i < values.length; i += 2) result.put((String) values[i], values[i + 1]); }
        catch (org.json.JSONException error) { throw new IllegalArgumentException(error); }
        return result;
    }
    static String type(String type) {
        switch (type) {
            case "MYSTERY_BOX": return "Hộp bí ẩn"; case "GROUP_BUY": return "Mua chung";
            case "SUBSCRIPTION": return "Gói định kỳ"; case "BULK": return "Thương lượng sỉ";
            case "DONATION": return "Quyên góp 0 đồng"; case "FLASH": return "Giải cứu giờ chót";
            default: return type;
        }
    }
    static String status(String value) {
        switch (value) {
            case "OPEN": return "Đang nhận đăng ký"; case "LOCKED": return "Đã chốt đăng ký";
            case "FAILED": return "Nhóm chưa đạt ngưỡng"; case "EXPIRED": return "Đã hết giờ nhận";
            case "CANCELLED": return "Đã hủy"; case "PLEDGED": return "Đang chờ đủ nhóm";
            case "RESERVED": return "Đã giữ suất"; case "READY": return "Sẵn sàng nhận";
            case "COMPLETED": return "Đã nhận nông sản"; case "REQUESTED": return "Đang chờ báo giá";
            case "PROPOSED": return "Có báo giá mới"; case "ACCEPTED": return "Đã chấp nhận";
            case "ACTIVE": return "Đang đăng ký"; case "PAUSED": return "Đã tạm dừng";
            case "PENDING": return "Chờ xác minh"; case "VERIFIED": return "Đã xác thực";
            case "REJECTED": return "Chưa được xác thực"; default: return value;
        }
    }
    static String money(double amount) { return NumberFormat.getCurrencyInstance(Locale.forLanguageTag("vi-VN")).format(amount); }
    static String date(long value) { return new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.forLanguageTag("vi-VN")).format(new Date(value)); }
    static int dp(Context context, int value) { return Math.round(value * context.getResources().getDisplayMetrics().density); }
    static LinearLayout column(Context context) {
        LinearLayout result = new LinearLayout(context); result.setOrientation(LinearLayout.VERTICAL); return result;
    }
    static LinearLayout card(LinearLayout parent) {
        Context context = parent.getContext();
        MaterialCardView card = new MaterialCardView(context); card.setRadius(dp(context, 18)); card.setCardElevation(0);
        card.setStrokeWidth(dp(context, 1)); card.setStrokeColor(context.getColor(R.color.rescue_outline));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2); params.topMargin = dp(context, 12);
        parent.addView(card, params);
        LinearLayout content = column(context); content.setPadding(dp(context, 16), dp(context, 16), dp(context, 16), dp(context, 16));
        card.addView(content); return content;
    }
    static TextView text(LinearLayout parent, String value, boolean title) {
        TextView view = new TextView(parent.getContext()); view.setText(value); view.setTextSize(title ? 20 : 15);
        view.setTextColor(parent.getContext().getColor(title ? R.color.rescue_on_surface : R.color.rescue_on_surface_variant));
        view.setPadding(0, dp(parent.getContext(), 6), 0, dp(parent.getContext(), 6));
        if (title) { com.rescuefarm.ui.common.AppTypography.title(view); view.setAccessibilityHeading(true); }
        parent.addView(view, new LinearLayout.LayoutParams(-1, -2)); return view;
    }
    static MaterialButton button(LinearLayout parent, String label, Runnable action) {
        MaterialButton button = new MaterialButton(parent.getContext(), null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle);
        button.setText(label); button.setAllCaps(false); button.setOnClickListener(v -> action.run());
        parent.addView(button, new LinearLayout.LayoutParams(-1, -2)); return button;
    }
    static EditText field(LinearLayout parent, String label, boolean decimal) {
        TextInputLayout layout = new TextInputLayout(parent.getContext()); layout.setHint(label);
        TextInputEditText edit = new TextInputEditText(parent.getContext());
        edit.setInputType(decimal ? InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        layout.addView(edit, new LinearLayout.LayoutParams(-1, -2)); parent.addView(layout, new LinearLayout.LayoutParams(-1, -2)); return edit;
    }
}
