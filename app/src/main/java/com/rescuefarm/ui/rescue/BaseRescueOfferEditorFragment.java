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
    private RescueBatchPickerDialog batchPicker;
    private TextView stockStatus;
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
            JSONArray values = value.value.optJSONArray("items"); choices = values == null ? new JSONArray() : values;
            boolean more = value.value.optBoolean("hasMore");
            stockStatus.setText(!value.message.isEmpty() ? "Chưa tải đủ lô: " + value.message
                    : value.loading || more ? "Đã tải " + choices.length() + " lô · đang tải tiếp…"
                    : "Đã tải đủ " + choices.length() + " lô của cửa hàng.");
            if (batchPicker != null) batchPicker.update(choices, value.loading, more, value.message);
            if (!value.loading && value.message.isEmpty() && more) model.more();
        });
        com.rescuefarm.ui.campaign.CampaignViewModel inventory = new ViewModelProvider(this,
                new com.rescuefarm.ui.campaign.CampaignViewModelFactory(requireContext())).get(com.rescuefarm.ui.campaign.CampaignViewModel.class);
        inventory.getBatchOptions().observe(getViewLifecycleOwner(), options -> model.refreshRealtime());
        model.load("batches", "batches", body("includeUnavailable", true));
    }
    protected abstract int editorLayout();
    protected abstract String offerType();
    protected abstract String createLabel();
    protected abstract void buildFields(@Nullable Bundle state);
    protected final void section(int id) { form = requireView().findViewById(id); }
    protected final void setDateTime(String key, long value) {
        times.put(key, value); fields.get(key).setText(date(value));
        fields.get(key).setError(null);
        message.setText("");
        model.clearMessage();
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
        // Keep the long address label outside the input so GPS text cannot overlap it.
        TextView addressLabel = text(form, "Địa chỉ / hướng dẫn đến điểm nhận", false);
        TextInputLayout addressLayout = new TextInputLayout(requireContext());
        addressLayout.setHintEnabled(false);
        addressLayout.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        TextInputEditText address = new TextInputEditText(addressLayout.getContext());
        address.setId(View.generateViewId());
        addressLabel.setLabelFor(address.getId());
        address.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_VARIATION_POSTAL_ADDRESS
                | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        address.setGravity(android.view.Gravity.TOP | android.view.Gravity.START);
        address.setMinLines(2);
        address.setHorizontallyScrolling(false);
        int padding = dp(requireContext(), 16);
        address.setPadding(padding, padding, padding, padding);
        address.setText(state == null ? "" : state.getString("pickupAddress", ""));
        addressLayout.addView(address, new LinearLayout.LayoutParams(-1, -2));
        form.addView(addressLayout, new LinearLayout.LayoutParams(-1, -2));
        fields.put("pickupAddress", address);
    }
    protected final void addStockPicker() {
        selected = column(requireContext()); form.addView(selected); renderComponents();
        stockStatus = text(form, "Đang tải lô của cửa hàng…", false);
        button(form, "Chọn lô nông sản (tối đa 12)", this::chooseBatch);
        button(form, "Tải lại danh sách lô", model::refresh);
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
    protected final void addQuickPickupTimes() {
        text(form, "Thiết lập nhanh khung bán và nhận", false);
        text(form, "Các nút bên dưới tính từ thời điểm bấm và cập nhật cả ba mốc giờ. Khách có thể nhận sau 1 phút; giờ nhận cuối muộn hơn giờ đóng đặt 30 phút.", false);
        com.rescuefarm.ui.common.ResponsiveActionLayout durations = new com.rescuefarm.ui.common.ResponsiveActionLayout(requireContext());
        form.addView(durations, new LinearLayout.LayoutParams(-1, -2));
        for (int minutes : new int[]{15, 30, 60}) {
            MaterialButton option = new MaterialButton(requireContext(), null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
            option.setText(minutes + " phút"); option.setAllCaps(false);
            durations.addView(option, new LinearLayout.LayoutParams(0, -2, 1));
            option.setOnClickListener(v -> {
                RescueTime.PickupWindow window = RescueTime.quickWindow(System.currentTimeMillis(), minutes);
                setDateTime("closesAt", window.closesAt);
                setDateTime("pickupStartAt", window.pickupStartAt);
                setDateTime("pickupEndAt", window.pickupEndAt);
            });
        }
    }
    private void chooseDateTime(String key, String label) {
        if (datePicker != null && datePicker.isShowing() || timePicker != null && timePicker.isShowing()) return;
        Calendar selectedTime = RescueTime.calendar(times.get(key));
        datePicker = new DatePickerDialog(requireContext(), (picker, year, month, day) -> {
            if (getView() == null) return;
            timePicker = new TimePickerDialog(requireContext(), (clock, hour, minute) -> {
                if (getView() == null) return;
                selectedTime.set(year, month, day, hour, minute, 0); selectedTime.set(Calendar.MILLISECOND, 0);
                setDateTime(key, selectedTime.getTimeInMillis());
            }, selectedTime.get(Calendar.HOUR_OF_DAY), selectedTime.get(Calendar.MINUTE), true);
            timePicker.setTitle(label + " · Giờ Việt Nam"); timePicker.show();
        }, selectedTime.get(Calendar.YEAR), selectedTime.get(Calendar.MONTH), selectedTime.get(Calendar.DAY_OF_MONTH));
        datePicker.getDatePicker().setMinDate(RescueTime.pickerMinDate(System.currentTimeMillis()));
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
        if (batchPicker != null) return;
        batchPicker = new RescueBatchPickerDialog(requireContext(), times.get("pickupEndAt"), id -> {
            for (int i = 0; i < components.length(); i++)
                if (components.optJSONObject(i).optString("batchId").equals(id)) return true;
            return false;
        }, this::chooseAmount, model::refresh, () -> batchPicker = null);
        batchPicker.show();
        RescueProgramsViewModel.State state = model.state().getValue();
        if (state != null) batchPicker.update(choices, state.loading, state.value.optBoolean("hasMore"), state.message);
    }
    private void chooseAmount(JSONObject batch) {
            LinearLayout form = column(requireContext()); int pad = dp(requireContext(), 20); form.setPadding(pad, 0, pad, 0);
            TextView amountLabel = text(form, "Lượng trong MỖI suất (" + batch.optString("unit") + ")", false);
            EditText amount = new EditText(requireContext());
            amount.setId(View.generateViewId()); amountLabel.setLabelFor(amount.getId());
            amount.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
            amount.setSingleLine(true); amount.setTextSize(16); amount.setMinHeight(dp(requireContext(), 48));
            form.addView(amount, new LinearLayout.LayoutParams(-1, -2));
            text(form, "Khả dụng: " + batch.optDouble("availableQuantity") + " " + batch.optString("unit")
                    + ". Lượng giữ kho = lượng mỗi suất × số suất của gói.", false);
            androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                    .setTitle(batch.optString("productName")).setView(form)
                    .setPositiveButton("Thêm lô", null).setNegativeButton("Đóng", null).create();
            dialog.setOnShowListener(unused -> dialog.getButton(-1).setOnClickListener(v -> {
                        try {
                            double quantity = Double.parseDouble(amount.getText().toString().trim().replace(',', '.'));
                            if (!Double.isFinite(quantity) || quantity <= 0) throw new NumberFormatException();
                            int units = Integer.parseInt(value("totalUnits"));
                            if (units <= 0) throw new NumberFormatException();
                            if (quantity * units > batch.optDouble("availableQuantity") + 0.000001) {
                                amount.setError("Không đủ tồn cho " + units + " suất. Giảm lượng mỗi suất hoặc số suất của gói."); return;
                            }
                            components.put(body("batchId", batch.optString("batchId"), "quantityPerUnit", quantity,
                                    "productName", batch.optString("productName"), "unit", batch.optString("unit")));
                            renderComponents(); dialog.dismiss();
                        } catch (NumberFormatException e) { amount.setError("Nhập lượng dương và kiểm tra số suất của gói."); }
                    }));
            dialog.show();
    }
    private void renderComponents() {
        selected.removeAllViews();
        if (components.length() == 0) text(selected, "Chưa chọn lô nông sản.", false);
        for (int i = 0; i < components.length(); i++) {
            final int index = i; JSONObject component = components.optJSONObject(i);
            LinearLayout row = card(selected);
            text(row, component.optString("productName"), true);
            text(row, component.optDouble("quantityPerUnit") + " " + component.optString("unit") + " / suất", false);
            button(row, "Bỏ lô", () -> { components.remove(index); renderComponents(); });
        }
    }
    private void submit() {
        try {
            if ("FLASH".equals(offerType()) && times.get("closesAt") - System.currentTimeMillis() > 2 * 3600000L) {
                fields.get("closesAt").setError("Chọn hạn đóng đặt trong 2 giờ tới.");
                message.setText("Hạn đóng nhận đặt vượt 2 giờ tính từ hiện tại. Giờ hiển thị là giờ Việt Nam (UTC+7); bấm 15/30/60 phút để đặt nhanh.");
                return;
            }
            if (locating) { message.setText("Đang lấy vị trí điểm nhận, vui lòng chờ."); return; }
            if (pickupLatitude == null || pickupLongitude == null) { message.setText("Bấm lấy GPS tại điểm nhận trước khi tạo gói."); return; }
            if (value("pickupAddress").length() < 5) { fields.get("pickupAddress").setError("Bổ sung địa chỉ hoặc hướng dẫn để khách tìm được điểm nhận."); return; }
            JSONObject input = body("requestId", model.requestId(), "type", offerType());
            for (String key : new String[]{"title", "description", "categoryLabel", "contentsDisclosure", "unitLabel", "pickupAddress"}) input.put(key, value(key));
            for (String key : new String[]{"totalUnits", "maxUnitsPerClaim", "unitPrice", "originalUnitPrice"}) input.put(key, Integer.parseInt(value(key)));
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
        if (batchPicker != null) { batchPicker.dismiss(); batchPicker = null; }
        fields.clear(); times.clear(); form = null; selected = null; gpsButton = null; locationStatus = null;
        super.onDestroyView();
    }
}
