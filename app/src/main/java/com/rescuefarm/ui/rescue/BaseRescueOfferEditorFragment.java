package com.rescuefarm.ui.rescue;

import android.Manifest;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.rescuefarm.R;
import com.rescuefarm.service.location.LocationProvider;
import com.rescuefarm.service.location.LocationProviderFactory;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.Map;
import static com.rescuefarm.ui.rescue.RescueUi.*;

public abstract class BaseRescueOfferEditorFragment extends Fragment {
    private RescueProgramsViewModel model;
    private LinearLayout selected;
    protected LinearLayout form;
    private TextView message;
    private final Map<String, EditText> fields = new LinkedHashMap<>();
    private final Map<String, Long> times = new LinkedHashMap<>();
    private JSONArray choices = new JSONArray(), components = new JSONArray();
    private com.google.android.material.button.MaterialButton submit;
    private LocationProvider locationProvider;
    private Double pickupLatitude, pickupLongitude;
    private TextView locationStatus;
    private MaterialButton gpsButton;
    private boolean locating;
    private int locationRequest;
    private DatePickerDialog datePicker;
    private TimePickerDialog timePicker;
    private final ActivityResultLauncher<String[]> locationPermissions = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                if (Boolean.TRUE.equals(result.get(Manifest.permission.ACCESS_FINE_LOCATION))
                        || Boolean.TRUE.equals(result.get(Manifest.permission.ACCESS_COARSE_LOCATION))) locatePickup();
                else if (getView() != null) {
                    locationStatus.setText("Chưa cấp quyền vị trí. Hãy cho phép vị trí khi dùng ứng dụng rồi thử lại.");
                    new MaterialAlertDialogBuilder(requireContext()).setTitle("Cho phép lấy vị trí điểm nhận")
                            .setMessage("Ứng dụng cần vị trí để đánh dấu điểm nhận trên bản đồ cho khách.")
                            .setPositiveButton("Mở cài đặt ứng dụng", (d, w) -> startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    android.net.Uri.parse("package:" + requireContext().getPackageName()))))
                            .setNegativeButton("Đóng", null).show();
                }
            });

    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup parent, @Nullable Bundle state) {
        return inflater.inflate(editorLayout(), parent, false);
    }
    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        super.onViewCreated(view, state);
        model = new ViewModelProvider(this).get(RescueProgramsViewModel.class);
        locationProvider = LocationProviderFactory.create(requireContext());
        form = view.findViewById(R.id.rescueEditorForm); message = view.findViewById(R.id.rescueEditorMessage);
        view.findViewById(R.id.rescueEditorBack).setOnClickListener(v -> NavHostFragment.findNavController(this).navigateUp());
        if (state != null) try { components = new JSONArray(state.getString("components", "[]")); } catch (org.json.JSONException ignored) { }
        buildFields(state);
        section(R.id.rescueEditorActions);
        submit = button(form, createLabel(), this::submit);
        model.state().observe(getViewLifecycleOwner(), value -> {
            view.findViewById(R.id.rescueEditorProgress).setVisibility(value.loading ? View.VISIBLE : View.GONE);
            submit.setEnabled(!value.loading && !locating); message.setText(value.message);
            JSONArray values = value.value.optJSONArray("items"); if (values != null) choices = values;
        });
        model.load("batches", "batches", body());
    }
    protected abstract int editorLayout();
    protected abstract String offerType();
    protected abstract String createLabel();
    protected abstract void buildFields(@Nullable Bundle state);
    protected final void section(int id) { form = requireView().findViewById(id); }
    protected final void setDateTime(String key, long value) {
        times.put(key, value); fields.get(key).setText(date(value));
    }
    protected final void addPickup(@Nullable Bundle state) {
        text(form, "Lấy GPS tại nơi giao nông sản cho khách. Địa chỉ tự điền và có thể bổ sung số nhà, tên cửa hàng hoặc hướng dẫn.", false);
        gpsButton = button(form, "Lấy vị trí hiện tại bằng GPS", this::requestPickupLocation);
        gpsButton.setIconResource(R.drawable.ic_home_location);
        locationStatus = text(form, "Chưa lấy vị trí điểm nhận.", false);
        locationStatus.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        if (state != null && state.containsKey("pickupLatitude") && state.containsKey("pickupLongitude")) {
            pickupLatitude = state.getDouble("pickupLatitude"); pickupLongitude = state.getDouble("pickupLongitude");
            locationStatus.setText("Đã lưu vị trí điểm nhận. Có thể lấy GPS lại nếu đổi điểm nhận.");
        }
        add("pickupAddress", "Địa chỉ / hướng dẫn đến điểm nhận", false, "", state);
    }
    protected final void addStockPicker() {
        selected = column(requireContext()); form.addView(selected); renderComponents();
        button(form, "Chọn lô nông sản (tối đa 12)", this::chooseBatch);
        button(form, "Tải thêm lô của cửa hàng", model::more);
    }
    protected final void add(String key, String label, boolean numeric, String initial, Bundle state) {
        EditText input = field(form, label, numeric); input.setText(state == null ? initial : state.getString(key, initial)); fields.put(key, input);
    }
    protected final void addDateTime(String key, String label, long initial, Bundle state) {
        long selectedTime = state == null ? initial : state.getLong("time." + key, initial);
        times.put(key, selectedTime);
        TextInputLayout layout = new TextInputLayout(requireContext()); layout.setHint(label);
        TextInputEditText input = new TextInputEditText(requireContext());
        input.setText(date(selectedTime)); input.setKeyListener(null); input.setFocusable(false);
        input.setClickable(true); input.setOnClickListener(v -> chooseDateTime(key, label));
        layout.addView(input, new LinearLayout.LayoutParams(-1, -2));
        layout.setEndIconMode(TextInputLayout.END_ICON_CUSTOM);
        layout.setEndIconDrawable(android.R.drawable.ic_menu_my_calendar);
        layout.setEndIconContentDescription("Chọn ngày và giờ: " + label);
        layout.setEndIconOnClickListener(v -> chooseDateTime(key, label));
        form.addView(layout, new LinearLayout.LayoutParams(-1, -2)); fields.put(key, input);
    }
    private void chooseDateTime(String key, String label) {
        if (datePicker != null && datePicker.isShowing() || timePicker != null && timePicker.isShowing()) return;
        Calendar selectedTime = Calendar.getInstance(); selectedTime.setTimeInMillis(times.get(key));
        datePicker = new DatePickerDialog(requireContext(), (picker, year, month, day) -> {
            if (getView() == null) return;
            timePicker = new TimePickerDialog(requireContext(), (clock, hour, minute) -> {
                if (getView() == null) return;
                selectedTime.set(year, month, day, hour, minute, 0); selectedTime.set(Calendar.MILLISECOND, 0);
                times.put(key, selectedTime.getTimeInMillis()); fields.get(key).setText(date(selectedTime.getTimeInMillis()));
            }, selectedTime.get(Calendar.HOUR_OF_DAY), selectedTime.get(Calendar.MINUTE), true);
            timePicker.setTitle(label + " · Chọn giờ"); timePicker.show();
        }, selectedTime.get(Calendar.YEAR), selectedTime.get(Calendar.MONTH), selectedTime.get(Calendar.DAY_OF_MONTH));
        Calendar today = Calendar.getInstance(); today.set(Calendar.HOUR_OF_DAY, 0); today.set(Calendar.MINUTE, 0);
        today.set(Calendar.SECOND, 0); today.set(Calendar.MILLISECOND, 0);
        datePicker.getDatePicker().setMinDate(today.getTimeInMillis());
        datePicker.setTitle(label + " · Chọn ngày"); datePicker.show();
    }
    private void requestPickupLocation() {
        if (locating) return;
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) locatePickup();
        else locationPermissions.launch(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION});
    }
    private boolean currentLocationRequest(int request) { return getView() != null && request == locationRequest; }
    private void finishLocating() {
        locating = false; gpsButton.setEnabled(true); gpsButton.setText("Lấy lại vị trí bằng GPS");
        RescueProgramsViewModel.State state = model.state().getValue(); submit.setEnabled(state == null || !state.loading);
    }
    private void locatePickup() {
        if (getView() == null || locating) return;
        final int request = ++locationRequest;
        final String previousAddress = value("pickupAddress");
        locating = true; gpsButton.setEnabled(false); gpsButton.setText("Đang lấy vị trí…"); submit.setEnabled(false);
        locationStatus.setText("Đang xác định điểm nhận và tìm địa chỉ…");
        locationProvider.getCurrentLocation(new LocationProvider.LocationCallback() {
            @Override public void onLocationAvailable(double latitude, double longitude) {
                if (!currentLocationRequest(request)) return;
                pickupLatitude = latitude; pickupLongitude = longitude;
                // Preserve text edited while the location request was in flight.
                if (value("pickupAddress").equals(previousAddress)) fields.get("pickupAddress").setText("");
                final String addressAtLookup = value("pickupAddress");
                finishLocating(); locationStatus.setText("Đã lấy GPS. Đang tìm địa chỉ điểm nhận…");
                locationProvider.reverseGeocode(latitude, longitude, new LocationProvider.GeocodeCallback() {
                    @Override public void onAddressAvailable(LocationProvider.GeocodedAddress address) {
                        if (!currentLocationRequest(request)) return;
                        String label = address.getFullAddress().trim();
                        if (label.isEmpty()) label = android.text.TextUtils.join(", ", java.util.Arrays.stream(new String[]{
                                address.getStreet(), address.getWard(), address.getDistrict(), address.getProvince()})
                                .filter(part -> part != null && !part.trim().isEmpty()).distinct().toArray(String[]::new));
                        if (value("pickupAddress").equals(addressAtLookup) && addressAtLookup.isEmpty()) fields.get("pickupAddress").setText(label);
                        finishLocating();
                        locationStatus.setText(label.isEmpty() ? "Đã lấy GPS. Hãy bổ sung địa chỉ hoặc hướng dẫn để khách tìm điểm nhận."
                                : "Đã lấy GPS và tìm được địa chỉ. Kiểm tra, bổ sung hướng dẫn nếu cần.");
                    }
                    @Override public void onGeocodeUnavailable(String error) {
                        if (!currentLocationRequest(request)) return;
                        finishLocating(); locationStatus.setText("Đã lấy GPS nhưng chưa tìm được địa chỉ. Bạn có thể điền địa chỉ hoặc hướng dẫn đến điểm nhận.");
                    }
                });
            }
            @Override public void onLocationUnavailable(String error) {
                if (!currentLocationRequest(request)) return;
                finishLocating(); locationStatus.setText(error);
                new MaterialAlertDialogBuilder(requireContext()).setTitle("Chưa lấy được vị trí điểm nhận")
                        .setMessage(error).setPositiveButton("Cài đặt vị trí", (d, w) -> startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)))
                        .setNegativeButton("Đóng", null).show();
            }
        });
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
                    .setMessage("Ví dụ 0.5 kg trong mỗi suất × 10 suất sẽ giữ 5 kg từ lô này.")
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
    private void submit() {
        try {
            if (locating) { message.setText("Đang lấy vị trí điểm nhận, vui lòng chờ."); return; }
            if (pickupLatitude == null || pickupLongitude == null) { message.setText("Bấm lấy GPS tại điểm nhận trước khi tạo gói."); return; }
            if (value("pickupAddress").length() < 5) { fields.get("pickupAddress").setError("Bổ sung địa chỉ hoặc hướng dẫn để khách tìm được điểm nhận."); return; }
            JSONObject input = body("requestId", model.requestId(), "type", offerType());
            for (String key : new String[]{"title", "description", "categoryLabel", "contentsDisclosure", "unitLabel", "pickupAddress"}) input.put(key, value(key));
            for (String key : new String[]{"totalUnits", "maxUnitsPerClaim", "unitPrice", "originalUnitPrice"}) input.put(key, Integer.parseInt(value(key)));
            input.put("planKey", fields.containsKey("planKey") ? value("planKey") : "");
            input.put("latitude", pickupLatitude);
            input.put("longitude", pickupLongitude);
            for (String key : new String[]{"closesAt", "pickupStartAt", "pickupEndAt"}) input.put(key, times.get(key));
            JSONArray selection = new JSONArray();
            for (int i = 0; i < components.length(); i++) { JSONObject component = components.optJSONObject(i); selection.put(body("batchId", component.optString("batchId"), "quantityPerUnit", component.optDouble("quantityPerUnit"))); }
            input.put("components", selection);
            new MaterialAlertDialogBuilder(requireContext()).setTitle("Giữ tồn kho cho gói mới?")
                    .setMessage("Gói sẽ công khai với giá và giờ nhận đã chọn. Kho sẽ giữ lượng nông sản của tất cả suất; có thể dừng gói để trả phần chưa nhận. Nếu mất kết nối, giữ nguyên nội dung và gửi lại để tránh tạo trùng.")
                    .setPositiveButton("Tạo gói", (d, w) -> model.action("create", input, () -> {
                        if (!NavHostFragment.findNavController(this).popBackStack(R.id.rescueHubFragment, false)) NavHostFragment.findNavController(this).navigateUp();
                    }))
                    .setNegativeButton("Kiểm tra lại", null).show();
        } catch (Exception error) { message.setText("Kiểm tra các trường số, vị trí và ngày giờ: " + error.getMessage()); }
    }
    @Override public void onSaveInstanceState(@NonNull Bundle out) {
        super.onSaveInstanceState(out); for (String key : fields.keySet()) out.putString(key, value(key));
        for (String key : times.keySet()) out.putLong("time." + key, times.get(key));
        if (pickupLatitude != null && pickupLongitude != null) {
            out.putDouble("pickupLatitude", pickupLatitude); out.putDouble("pickupLongitude", pickupLongitude);
        }
        out.putString("components", components.toString());
    }
    @Override public void onDestroyView() {
        locationRequest++; locating = false;
        if (locationProvider != null) { locationProvider.close(); locationProvider = null; }
        if (datePicker != null) { datePicker.dismiss(); datePicker = null; }
        if (timePicker != null) { timePicker.dismiss(); timePicker = null; }
        fields.clear(); times.clear(); form = null; selected = null; gpsButton = null; locationStatus = null;
        super.onDestroyView();
    }
}
