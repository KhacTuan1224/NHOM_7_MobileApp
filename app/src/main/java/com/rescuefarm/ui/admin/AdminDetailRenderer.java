package com.rescuefarm.ui.admin;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.rescuefarm.R;
import com.rescuefarm.data.repository.admin.AdminListItem;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Read-only view builder shared by every admin detail dialog. */
public final class AdminDetailRenderer {
    private final Context context;
    public AdminDetailRenderer(Context context) { this.context = context; }

    public void render(LinearLayout body, AdminListItem item, Map<String, Object> detail) {
        LinearLayout heading = column();
        heading.setPadding(0, 0, 0, dp(16));
        if (!item.getStatus().isEmpty()) heading.addView(badge(item.getStatus()),
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        body.addView(heading);
        Map<String, Object> display = new LinkedHashMap<>(detail);
        if (!display.containsKey("id")) display.put("id", item.getId());
        renderMap(body, display);
    }

    private void renderMap(LinearLayout parent, Map<?, ?> data) {
        Map<String, List<Map.Entry<?, ?>>> groups = new LinkedHashMap<>();
        List<Map.Entry<?, ?>> nested = new ArrayList<>();
        List<Map.Entry<?, ?>> entries = new ArrayList<>(data.entrySet());
        entries.sort(Comparator.comparingInt((Map.Entry<?, ?> e) -> AdminDetailFormatter.order(String.valueOf(e.getKey())))
                .thenComparing(e -> String.valueOf(e.getKey())));
        for (Map.Entry<?, ?> entry : entries) {
            String key = String.valueOf(entry.getKey());
            if (AdminDetailFormatter.hidden(key)) continue;
            if (entry.getValue() instanceof Map<?, ?> || entry.getValue() instanceof List<?>) nested.add(entry);
            else groups.computeIfAbsent(AdminDetailFormatter.group(key), unused -> new ArrayList<>()).add(entry);
        }
        for (String group : AdminDetailFormatter.groups()) {
            List<Map.Entry<?, ?>> values = groups.get(group);
            if (values == null) continue;
            boolean references = "Thông tin tham chiếu".equals(group);
            if (references) continue;
            LinearLayout card = section(parent, group, references);
            for (Map.Entry<?, ?> entry : values) field(card, String.valueOf(entry.getKey()), entry.getValue());
        }
        for (Map.Entry<?, ?> entry : nested) {
            String key = String.valueOf(entry.getKey()); Object value = entry.getValue();
            LinearLayout content;
            if (AdminDetailFormatter.technical(key)) content = section(parent, AdminDetailFormatter.label(key), true);
            else { heading(parent, AdminDetailFormatter.label(key)); content = parent; }
            if (value instanceof Map<?, ?>) {
                if (((Map<?, ?>) value).isEmpty()) empty(content);
                else renderMap(content, (Map<?, ?>) value);
            } else renderList(content, key, (List<?>) value);
        }
        List<Map.Entry<?, ?>> references = groups.get("Thông tin tham chiếu");
        if (references != null) {
            LinearLayout card = section(parent, "Thông tin tham chiếu", true);
            for (Map.Entry<?, ?> entry : references) field(card, String.valueOf(entry.getKey()), entry.getValue());
        }
    }

    private void renderList(LinearLayout parent, String key, List<?> values) {
        if (values.isEmpty()) { empty(parent); return; }
        TextView count = text(values.size() + " mục", 12, R.color.rescue_on_surface_variant);
        parent.addView(count, params());
        for (int i = 0; i < values.size(); i++) {
            Object value = values.get(i);
            if (value instanceof Map<?, ?>) {
                TextView title = text("Mục " + (i + 1), 14, R.color.rescue_primary);
                title.setTypeface(null, Typeface.BOLD); title.setPadding(0, dp(12), 0, dp(4));
                parent.addView(title, params()); renderMap(parent, (Map<?, ?>) value);
            } else if (value instanceof List<?>) {
                LinearLayout child = section(parent, "Mục " + (i + 1), false);
                renderList(child, key, (List<?>) value);
            } else field(parent, key, value, "Mục " + (i + 1));
        }
    }

    private void field(LinearLayout parent, String key, Object raw) { field(parent, key, raw, AdminDetailFormatter.label(key)); }
    private void field(LinearLayout parent, String key, Object raw, String label) {
        LinearLayout row = column(); row.setPadding(0, dp(8), 0, dp(8));
        TextView caption = text(label, 12, R.color.rescue_on_surface_variant);
        row.addView(caption, params());
        Object value = raw instanceof com.google.firebase.Timestamp ? ((com.google.firebase.Timestamp) raw).toDate() : raw;
        boolean absent = value == null || value instanceof String && ((String) value).trim().isEmpty();
        TextView content = text(AdminDetailFormatter.value(key, value), 15,
                absent ? R.color.rescue_on_surface_variant : R.color.rescue_on_surface);
        content.setTextIsSelectable(true); content.setLineSpacing(dp(2), 1f);
        LinearLayout.LayoutParams contentParams = params(); contentParams.topMargin = dp(4);
        row.addView(content, contentParams);
        if (AdminDetailFormatter.isEnum(key) && !absent) {
            content.setTextColor(stateColor(String.valueOf(value))); content.setTypeface(null, Typeface.BOLD);
        }
        if (raw instanceof String && (((String) raw).startsWith("https://") || ((String) raw).startsWith("http://"))) {
            content.setVisibility(View.GONE);
            MaterialButton toggle = textButton();
            toggle.setText("Xem đường dẫn"); toggle.setAllCaps(false); toggle.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            toggle.setPadding(0, 0, 0, 0); row.addView(toggle, params());
            toggle.setOnClickListener(v -> {
                boolean expand = content.getVisibility() != View.VISIBLE;
                content.setVisibility(expand ? View.VISIBLE : View.GONE);
                toggle.setText(expand ? "Thu gọn đường dẫn" : "Xem đường dẫn");
            });
        }
        // Keep the source URL selectable, including when an existing Cloudinary image is previewed.
        if (raw instanceof String && ((String) raw).startsWith("https://res.cloudinary.com/")) {
            ImageView image = new ImageView(context); image.setAdjustViewBounds(true);
            image.setScaleType(ImageView.ScaleType.FIT_CENTER); image.setContentDescription(label);
            LinearLayout.LayoutParams imageParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(180));
            imageParams.topMargin = dp(8); row.addView(image, imageParams);
            Glide.with(image).load(raw).error(android.R.drawable.ic_menu_report_image).into(image);
        }
        parent.addView(row, params());
        View divider = new View(context); divider.setBackgroundColor(color(R.color.rescue_outline));
        parent.addView(divider, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));
    }

    private LinearLayout section(LinearLayout parent, String title, boolean collapsed) {
        LinearLayout card = column();
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        GradientDrawable background = new GradientDrawable(); background.setColor(color(R.color.rescue_surface));
        background.setCornerRadius(0); background.setStroke(dp(1), color(R.color.rescue_outline)); card.setBackground(background);
        LinearLayout.LayoutParams cardParams = params(); cardParams.bottomMargin = dp(12); parent.addView(card, cardParams);
        LinearLayout content = column();
        if (collapsed) {
            MaterialButton toggle = textButton();
            toggle.setText(title + "  +"); toggle.setAllCaps(false); toggle.setTextSize(14);
            toggle.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            toggle.setContentDescription(title + ", mở rộng"); toggle.setMinHeight(dp(48));
            toggle.setPadding(0, 0, 0, 0); card.addView(toggle, params()); content.setVisibility(View.GONE);
            toggle.setOnClickListener(v -> {
                boolean expand = content.getVisibility() != View.VISIBLE;
                content.setVisibility(expand ? View.VISIBLE : View.GONE);
                toggle.setText(title + (expand ? "  −" : "  +"));
                toggle.setContentDescription(title + (expand ? ", thu gọn" : ", mở rộng"));
            });
        } else {
            TextView heading = text(title, 14, R.color.rescue_primary); heading.setTypeface(null, Typeface.BOLD);
            heading.setAccessibilityHeading(true); heading.setPadding(0, 0, 0, dp(4)); card.addView(heading, params());
        }
        card.addView(content, params()); return content;
    }

    public void notice(LinearLayout parent, List<String> warnings) {
        LinearLayout card = section(parent, "Cần kiểm tra", false);
        for (String warning : warnings) {
            TextView text = text("• " + warning, 14, R.color.rescue_high);
            text.setPadding(0, dp(6), 0, dp(6)); card.addView(text, params());
        }
    }
    private TextView badge(String value) {
        TextView chip = text(AdminDetailFormatter.status(value), 13, R.color.rescue_on_primary_container);
        int foreground = stateColor(value); chip.setTextColor(foreground);
        chip.setTypeface(null, Typeface.BOLD); chip.setPadding(dp(12), dp(7), dp(12), dp(7));
        GradientDrawable background = new GradientDrawable(); background.setColor(ColorUtils.setAlphaComponent(foreground, 24));
        background.setCornerRadius(dp(24)); chip.setBackground(background); return chip;
    }
    private MaterialButton textButton() {
        MaterialButton button = new MaterialButton(context);
        button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.TRANSPARENT));
        button.setTextColor(color(R.color.rescue_primary)); button.setElevation(0); button.setMinHeight(dp(48));
        button.setInsetTop(0); button.setInsetBottom(0);
        return button;
    }
    private void heading(LinearLayout parent, String title) {
        TextView view = text(title, 18, R.color.rescue_on_surface);
        view.setTypeface(null, Typeface.BOLD); view.setAccessibilityHeading(true);
        view.setPadding(0, dp(8), 0, dp(12)); parent.addView(view, params());
    }
    private void empty(LinearLayout parent) { parent.addView(text("Chưa có thông tin", 14, R.color.rescue_on_surface_variant), params()); }
    private TextView text(String value, int size, int color) {
        TextView view = new TextView(context); view.setText(value); view.setTextSize(size); view.setTextColor(color(color)); return view;
    }
    private LinearLayout column() { LinearLayout view = new LinearLayout(context); view.setOrientation(LinearLayout.VERTICAL); return view; }
    private LinearLayout.LayoutParams params() { return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); }
    private int color(int id) { return ContextCompat.getColor(context, id); }
    private int stateColor(String value) {
        if (java.util.Arrays.asList("REJECTED", "BLOCKED", "FAILED", "CANCELLED", "CRITICAL", "DELETED").contains(value))
            return color(R.color.rescue_critical);
        if (java.util.Arrays.asList("PENDING", "PENDING_APPROVAL", "UNPAID", "IN_REVIEW", "HIGH", "EXPIRED").contains(value))
            return color(R.color.rescue_high);
        return color(R.color.rescue_primary);
    }
    private int dp(int value) { return Math.round(value * context.getResources().getDisplayMetrics().density); }
}
