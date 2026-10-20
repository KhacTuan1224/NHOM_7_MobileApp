package com.rescuefarm.ui.rescue;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.rescuefarm.R;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import static com.rescuefarm.ui.rescue.RescueUi.*;

public final class RescueOfferEditorFragment extends Fragment {
    private RescueProgramsViewModel model;
    private Spinner method;
    private LinearLayout selected, form;
    private TextView message;
    private final Map<String, EditText> fields = new LinkedHashMap<>();
    private JSONArray choices = new JSONArray(), components = new JSONArray();
    private com.google.android.material.button.MaterialButton submit;

    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup parent, @Nullable Bundle state) {
        return inflater.inflate(R.layout.fragment_rescue_offer_editor, parent, false);
    }
    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        super.onViewCreated(view, state);
        model = new ViewModelProvider(this).get(RescueProgramsViewModel.class);
        form = view.findViewById(R.id.rescueEditorForm); message = view.findViewById(R.id.rescueEditorMessage);
        view.findViewById(R.id.rescueEditorBack).setOnClickListener(v -> NavHostFragment.findNavController(this).navigateUp());
        text(form, "Phương thức", true); method = new Spinner(requireContext());
        String[] labels = getResources().getStringArray(R.array.rescue_type_labels);
        method.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_dropdown_item,
                java.util.Arrays.copyOfRange(labels, 1, labels.length)));
        form.addView(method, new LinearLayout.LayoutParams(-1, dp(requireContext(), 48)));
        if (state != null) method.setSelection(state.getInt("method"));
        add("title", "Tên gói", false, "", state);
        add("description", "Mô tả gói và chất lượng nông sản", false, "", state);
        add("categoryLabel", "Phân loại chung (ví dụ rau củ / trái cây)", false, "", state);
        add("contentsDisclosure", "Thành phần có thể có, dị ứng, lưu ý sử dụng", false, "", state);
        add("unitLabel", "Đơn vị suất (hộp / túi / phần 5 kg...)", false, "hộp", state);
        add("totalUnits", "Tổng số suất (nguyên)", true, "10", state);
        add("maxUnitsPerClaim", "Số suất tối đa mỗi khách", true, "1", state);
        add("originalUnitPrice", "Giá trị gốc mỗi suất (VND)", true, "100000", state);
        add("unitPrice", "Giá giải cứu mỗi suất (VND)", true, "20000", state);
        text(form, "Giờ nhận theo múi giờ của thiết bị. Định dạng ngày/tháng/năm giờ:phút (24 giờ). Flash tối đa 2 giờ; mua chung bắt đầu nhận sau hạn gom.", false);
        long now = System.currentTimeMillis();
        add("closesAt", "Hạn đăng ký (dd/MM/yyyy HH:mm)", false, date(now + 3600000), state);
        add("pickupStartAt", "Bắt đầu nhận (dd/MM/yyyy HH:mm)", false, date(now + 3600000), state);
        add("pickupEndAt", "Kết thúc nhận (dd/MM/yyyy HH:mm)", false, date(now + 7200000), state);
        add("pickupAddress", "Địa chỉ điểm nhận chung", false, "", state);
        add("latitude", "Vĩ độ điểm nhận (có thể nhập dấu âm)", false, "", state);
        add("longitude", "Kinh độ điểm nhận", false, "", state);
        text(form, "Riêng mua chung: ngưỡng người và suất phải cùng đạt. Tối đa 50 người/nhóm. Mỗi gói hỗ trợ tối đa 100 đăng ký.", false);
        add("minPeople", "Số người tối thiểu (mua chung)", true, "2", state);
        add("minUnits", "Số suất tối thiểu (mua chung)", true, "5", state);
        text(form, "Riêng định kỳ: dùng cùng mã gói khi bổ sung lô mới mỗi tuần/tháng. Không tự tạo đơn hay trừ tiền.", false);
        add("planKey", "Mã định kỳ (ví dụ rau-tuan)", false, "", state);
        text(form, "Lô nông sản trong MỖI suất", true);
        selected = column(requireContext()); form.addView(selected);
        if (state != null) try { components = new JSONArray(state.getString("components", "[]")); } catch (org.json.JSONException ignored) { }
        renderComponents();
        button(form, "Thêm lô hàng vào gói (tối đa 12)", this::chooseBatch);
        button(form, "Tải thêm danh sách lô của cửa hàng", model::more);
        submit = button(form, "Tạo gói và giữ tồn kho", this::submit);
        model.state().observe(getViewLifecycleOwner(), value -> {
            view.findViewById(R.id.rescueEditorProgress).setVisibility(value.loading ? View.VISIBLE : View.GONE);
            submit.setEnabled(!value.loading); message.setText(value.message);
            JSONArray values = value.value.optJSONArray("items"); if (values != null) choices = values;
        });
        model.load("batches", "batches", body());
    }
    private void add(String key, String label, boolean numeric, String initial, Bundle state) {
        EditText input = field(form, label, numeric); input.setText(state == null ? initial : state.getString(key, initial)); fields.put(key, input);
    }
    private String value(String key) { return fields.get(key).getText().toString().trim(); }
    private void chooseBatch() {
        if (components.length() >= 12) { message.setText("Mỗi gói tối đa 12 lô."); return; }
        if (choices.length() == 0) { message.setText("Chưa có lô đủ điều kiện trong phần đã tải. Thêm lô còn hạn, chưa gắn chiến dịch hoặc tải thêm danh sách."); return; }
        String[] names = new String[choices.length()];
        for (int i = 0; i < choices.length(); i++) {
            JSONObject batch = choices.optJSONObject(i);
            names[i] = batch.optString("productName") + " · " + batch.optDouble("availableQuantity") + " " + batch.optString("unit")
                    + "\nHSD: " + date(batch.optLong("expiryDate")) + " · " + batch.optString("batchId");
        }
        new MaterialAlertDialogBuilder(requireContext()).setTitle("Chọn lô có tồn kho thật").setItems(names, (dialog, index) -> {
            JSONObject batch = choices.optJSONObject(index);
            for (int i = 0; i < components.length(); i++) if (components.optJSONObject(i).optString("batchId").equals(batch.optString("batchId"))) {
                message.setText("Lô đã được thêm. Xóa rồi thêm lại để đổi lượng mỗi suất."); return;
            }
            LinearLayout form = column(requireContext()); int pad = dp(requireContext(), 20); form.setPadding(pad, 0, pad, 0);
            EditText amount = field(form, "Lượng trong MỖI suất (" + batch.optString("unit") + ")", true);
            new MaterialAlertDialogBuilder(requireContext()).setTitle(batch.optString("productName")).setView(form)
                    .setMessage("Ví dụ 0.5 kg mỗi hộp × 10 hộp sẽ giữ 5 kg từ lô này.")
                    .setPositiveButton("Thêm lô", (d, w) -> {
                        try {
                            double quantity = Double.parseDouble(amount.getText().toString().trim().replace(',', '.'));
                            if (!Double.isFinite(quantity) || quantity <= 0) throw new NumberFormatException();
                            components.put(body("batchId", batch.optString("batchId"), "quantityPerUnit", quantity,
                                    "productName", batch.optString("productName"), "unit", batch.optString("unit")));
                            renderComponents();
                        } catch (NumberFormatException e) { message.setText("Lượng mỗi suất phải là số dương hợp lệ."); }
                    }).setNegativeButton("Đóng", null).show();
        }).setNegativeButton("Đóng", null).show();
    }
    private void renderComponents() {
        selected.removeAllViews();
        for (int i = 0; i < components.length(); i++) {
            final int index = i; JSONObject component = components.optJSONObject(i);
            text(selected, component.optString("productName") + ": " + component.optDouble("quantityPerUnit") + " " + component.optString("unit") + " / suất", false);
            button(selected, "Bỏ lô " + component.optString("productName"), () -> { components.remove(index); renderComponents(); });
        }
    }
    private long parseDate(String key) {
        String value = value(key); SimpleDateFormat format = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.forLanguageTag("vi-VN"));
        format.setLenient(false); ParsePosition position = new ParsePosition(0); Date parsed = format.parse(value, position);
        if (parsed == null || position.getIndex() != value.length()) throw new IllegalArgumentException("Thời gian phải đúng dd/MM/yyyy HH:mm.");
        return parsed.getTime();
    }
    private void submit() {
        try {
            JSONObject input = body("requestId", model.requestId(), "type", TYPES[method.getSelectedItemPosition() + 1]);
            for (String key : new String[]{"title", "description", "categoryLabel", "contentsDisclosure", "unitLabel", "pickupAddress", "planKey"}) input.put(key, value(key));
            for (String key : new String[]{"totalUnits", "maxUnitsPerClaim", "unitPrice", "originalUnitPrice", "minPeople", "minUnits"}) input.put(key, Integer.parseInt(value(key)));
            input.put("latitude", Double.parseDouble(value("latitude").replace(',', '.')));
            input.put("longitude", Double.parseDouble(value("longitude").replace(',', '.')));
            for (String key : new String[]{"closesAt", "pickupStartAt", "pickupEndAt"}) input.put(key, parseDate(key));
            JSONArray selection = new JSONArray();
            for (int i = 0; i < components.length(); i++) { JSONObject component = components.optJSONObject(i); selection.put(body("batchId", component.optString("batchId"), "quantityPerUnit", component.optDouble("quantityPerUnit"))); }
            input.put("components", selection);
            new MaterialAlertDialogBuilder(requireContext()).setTitle("Giữ tồn kho cho gói mới?")
                    .setMessage("Gói sẽ công khai với giá và giờ nhận đã chọn. Kho sẽ giữ lượng nông sản của tất cả suất; có thể dừng gói để trả phần chưa nhận. Nếu mất kết nối, giữ nguyên nội dung và gửi lại để tránh tạo trùng.")
                    .setPositiveButton("Tạo gói", (d, w) -> model.action("create", input, () -> NavHostFragment.findNavController(this).navigateUp()))
                    .setNegativeButton("Kiểm tra lại", null).show();
        } catch (Exception error) { message.setText("Kiểm tra các trường số, vị trí và ngày giờ: " + error.getMessage()); }
    }
    @Override public void onSaveInstanceState(@NonNull Bundle out) {
        super.onSaveInstanceState(out); for (String key : fields.keySet()) out.putString(key, value(key));
        out.putString("components", components.toString()); out.putInt("method", method.getSelectedItemPosition());
    }
    @Override public void onDestroyView() { fields.clear(); form = null; selected = null; super.onDestroyView(); }
}
