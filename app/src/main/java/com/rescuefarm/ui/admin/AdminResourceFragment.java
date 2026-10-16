package com.rescuefarm.ui.admin;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.CheckBox;
import android.widget.Toast;
import android.app.DatePickerDialog;
import androidx.appcompat.app.AlertDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.rescuefarm.R;
import com.rescuefarm.data.repository.AdminRepository.Section;
import com.rescuefarm.data.repository.admin.AdminDashboardSnapshot;
import com.rescuefarm.data.repository.admin.AdminListItem;
import com.rescuefarm.service.admin.AdminTransitionPolicy;
import com.rescuefarm.service.media.CloudinaryUploadUi;
import com.rescuefarm.data.repository.AdminRepository;
import com.bumptech.glide.Glide;
import java.util.Map;
import java.util.List;
import java.util.Arrays;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.Calendar;
import java.util.Date;
import java.text.SimpleDateFormat;
import java.text.ParseException;

public final class AdminResourceFragment extends Fragment {
    private final AdminTransitionPolicy policy = new AdminTransitionPolicy();
    private AdminViewModel viewModel;
    private Section section;
    private TextView title, message;
    private EditText reason;
    private ProgressBar progress;
    private LinearLayout container;
    private EditText searchInput;
    private Spinner statusFilter;
    private View loadMoreButton;
    private Button orderFromButton, orderThroughButton;
    private Date orderFromDate, orderThroughDate;
    private EditText shippingBaseFee, shippingFreeDistance, shippingPerKmFee;
    private TextView shippingPreview;
    private String searchTerm = "";
    private boolean bindingStatusFilter;
    private final Runnable pendingSearch = () -> {
        if (viewModel != null) viewModel.setSearchTerm(searchTerm);
    };
    private String selectedAssetId = "";
    private View pendingImageButton;
    private TextView pendingImageStatus;
    private final ActivityResultLauncher<String> imagePicker = registerForActivityResult(
            new ActivityResultContracts.GetContent(), uri -> {
                if (selectedAssetId.isEmpty()) return;
                String targetId = selectedAssetId;
                String assetType = section == Section.CATEGORIES ? "category" : "banner";
                CloudinaryUploadUi.upload(this, uri, assetType, targetId, 0, pendingImageButton, pendingImageStatus,
                        null, url -> viewModel.setResourceImage(targetId, url));
            });

    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater,
            @Nullable ViewGroup parent, @Nullable Bundle state) {
        return inflater.inflate(R.layout.fragment_admin_resource, parent, false);
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        String raw = getArguments() == null ? null : getArguments().getString("section");
        section = Section.from(raw);
        title = view.findViewById(R.id.adminResourceTitle);
        message = view.findViewById(R.id.adminResourceMessage);
        reason = view.findViewById(R.id.adminModerationReason);
        progress = view.findViewById(R.id.adminResourceProgress);
        container = view.findViewById(R.id.adminResourceContainer);
        searchInput = view.findViewById(R.id.adminSearchInput);
        statusFilter = view.findViewById(R.id.adminStatusFilter);
        loadMoreButton = view.findViewById(R.id.loadMoreAdminItemsButton);
        View dateFilters = view.findViewById(R.id.adminOrderDateFilters);
        orderFromButton = view.findViewById(R.id.adminOrderFromDate);
        orderThroughButton = view.findViewById(R.id.adminOrderThroughDate);
        title.setText(getString(R.string.admin_section_title_format, label(section)));
        View addCatalog = view.findViewById(R.id.adminAddCatalogResourceButton);
        if (section == Section.CATEGORIES || section == Section.BANNERS) {
            addCatalog.setVisibility(View.VISIBLE);
            addCatalog.setOnClickListener(v -> showCatalogEditor(null));
        }
        if (section == Section.SHIPPING_SETTINGS) {
            addCatalog.setVisibility(View.GONE); reason.setVisibility(View.GONE);
            searchInput.setVisibility(View.GONE); statusFilter.setVisibility(View.GONE);
            view.findViewById(R.id.adminOrderDateFilters).setVisibility(View.GONE);
            loadMoreButton.setVisibility(View.GONE);
        }
        reason.setVisibility(section == Section.CATEGORIES || section == Section.BANNERS
                ? View.VISIBLE : View.GONE);
        viewModel = new ViewModelProvider(this, new AdminViewModelFactory(requireContext()))
                .get(AdminViewModel.class);
        viewModel.getState().observe(getViewLifecycleOwner(), this::render);
        if (section != Section.ANALYTICS) {
            if (section == Section.ORDERS) {
                dateFilters.setVisibility(View.VISIBLE);
                orderFromDate = viewModel.getOrderFromDate(); orderThroughDate = viewModel.getOrderThroughDate();
                refreshDateLabels();
                orderFromButton.setOnClickListener(v -> pickOrderDate(true));
                orderThroughButton.setOnClickListener(v -> pickOrderDate(false));
                view.findViewById(R.id.adminOrderClearDates).setOnClickListener(v -> {
                    orderFromDate = null; orderThroughDate = null; refreshDateLabels();
                    viewModel.setOrderDateRange(null, null);
                });
            } else dateFilters.setVisibility(View.GONE);
            if (section == Section.ORDERS) searchInput.setHint("orderCode / code: / seller: / customer: / id:");
            else if (section == Section.REPORTS) searchInput.setHint("target ID hoặc target:...");
            else searchInput.setHint("Tìm theo tên hoặc nhập id:<mã>");
            statusFilter.setAdapter(new ArrayAdapter<>(requireContext(),
                    android.R.layout.simple_spinner_dropdown_item,
                    getResources().getStringArray(R.array.admin_status_filters)));
            statusFilter.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
                @Override public void onItemSelected(android.widget.AdapterView<?> parent, View row, int position, long id) {
                    if (!bindingStatusFilter && position >= 0)
                        viewModel.setStatusFilter(String.valueOf(parent.getItemAtPosition(position)));
                }
                @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
            });
            searchInput.addTextChangedListener(new android.text.TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                    searchTerm = s == null ? "" : s.toString().trim();
                    searchInput.removeCallbacks(pendingSearch);
                    searchInput.postDelayed(pendingSearch, 350L);
                }
                @Override public void afterTextChanged(android.text.Editable s) { }
            });
            loadMoreButton.setOnClickListener(v -> viewModel.loadNextPage());
        } else {
            searchInput.setVisibility(View.GONE); statusFilter.setVisibility(View.GONE);
            loadMoreButton.setVisibility(View.GONE);
        }
        view.findViewById(R.id.adminResourceBackButton).setOnClickListener(v -> androidx.navigation.fragment.NavHostFragment.findNavController(this).popBackStack());
        if (section != Section.ANALYTICS) viewModel.observeSection(section).observe(
                getViewLifecycleOwner(), this::renderItems);
        view.findViewById(R.id.refreshAdminResourceButton).setOnClickListener(v -> refresh());
        refresh();
    }

    private void refresh() {
        if (section == Section.ANALYTICS) viewModel.refreshDashboard();
        else if (section == Section.SHIPPING_SETTINGS) loadShippingConfig();
        else viewModel.refreshSection(section);
    }

    private void render(AdminScreenState state) {
        progress.setVisibility(state.getStatus() == AdminScreenState.Status.LOADING ? View.VISIBLE : View.GONE);
        message.setText(state.getMessage());
        if (section == Section.ANALYTICS) { renderAnalytics(state.getDashboard()); return; }
        if (section == Section.SHIPPING_SETTINGS) { renderShippingSettings(); return; }
        container.removeAllViews();
        renderItems(state.getItems());
        loadMoreButton.setVisibility(viewModel.hasMore() ? View.VISIBLE : View.GONE);
    }

    private void renderShippingSettings() {
        if (shippingBaseFee != null && shippingBaseFee.getParent() == container) return;
        addDetailText(container, "Phí giao hàng tính theo khoảng cách Haversine: phí cơ bản + ceil(max(0, km − km miễn phí) × phí/km). Pickup và On-site Rescue không tính phí giao hàng.");
        shippingBaseFee = shippingNumberField("Phí cơ bản (₫)"); container.addView(shippingBaseFee);
        shippingFreeDistance = shippingNumberField("Khoảng cách miễn phí (km)"); container.addView(shippingFreeDistance);
        shippingPerKmFee = shippingNumberField("Phí mỗi km vượt ngưỡng (₫/km)"); container.addView(shippingPerKmFee);
        shippingPreview = new TextView(requireContext()); container.addView(shippingPreview);
        android.text.TextWatcher watcher = new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { updateShippingPreview(); }
            @Override public void afterTextChanged(android.text.Editable s) { }
        };
        shippingBaseFee.addTextChangedListener(watcher); shippingFreeDistance.addTextChangedListener(watcher);
        shippingPerKmFee.addTextChangedListener(watcher);
        Button save = buttonText("Lưu cấu hình phí"); container.addView(save);
        save.setOnClickListener(v -> {
            try {
                double base = Double.parseDouble(shippingBaseFee.getText().toString().trim());
                double free = Double.parseDouble(shippingFreeDistance.getText().toString().trim());
                double perKm = Double.parseDouble(shippingPerKmFee.getText().toString().trim());
                viewModel.saveShippingConfig(base, free, perKm, new AdminRepository.ActionCallback() {
                    @Override public void onSuccess() {
                        if (!isAdded()) return;
                        Toast.makeText(requireContext(), "Đã lưu cấu hình và audit.", Toast.LENGTH_SHORT).show();
                        loadShippingConfig();
                    }
                    @Override public void onError(AdminRepository.ErrorCode code, String error) {
                        if (isAdded()) Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show();
                    }
                });
            } catch (NumberFormatException error) {
                Toast.makeText(requireContext(), "Nhập đủ ba giá trị số hợp lệ.", Toast.LENGTH_LONG).show();
            }
        });
        loadShippingConfig();
    }

    private EditText shippingNumberField(String hint) {
        EditText field = editor(hint);
        field.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        return field;
    }

    private void loadShippingConfig() {
        message.setText("Đang tải cấu hình từ Firestore…");
        viewModel.loadShippingConfig(new AdminRepository.DetailCallback() {
            @Override public void onSuccess(Map<String, Object> data) {
                if (!isAdded() || shippingBaseFee == null) return;
                shippingBaseFee.setText(data.get("baseFeeVnd") == null ? "" : String.valueOf(data.get("baseFeeVnd")));
                shippingFreeDistance.setText(data.get("freeDistanceKm") == null ? "" : String.valueOf(data.get("freeDistanceKm")));
                shippingPerKmFee.setText(data.get("perKmFeeVnd") == null ? "" : String.valueOf(data.get("perKmFeeVnd")));
                Object updater = data.get("updatedBy");
                message.setText(data.isEmpty() ? "Chưa cấu hình phí giao hàng."
                        : "Đã cập nhật bởi " + (updater == null ? "(chưa ghi nhận)" : updater));
                updateShippingPreview();
            }
            @Override public void onError(AdminRepository.ErrorCode code, String error) {
                if (isAdded()) message.setText(error);
            }
        });
    }

    private void updateShippingPreview() {
        if (shippingPreview == null) return;
        try {
            double base = Double.parseDouble(shippingBaseFee.getText().toString());
            double free = Double.parseDouble(shippingFreeDistance.getText().toString());
            double perKm = Double.parseDouble(shippingPerKmFee.getText().toString());
            double sample = base + Math.ceil(Math.max(0D, 10D - free) * perKm);
            shippingPreview.setText("Ví dụ 10 km: " + NumberFormat.getCurrencyInstance(new Locale("vi", "VN")).format(sample));
        } catch (NumberFormatException ignored) { shippingPreview.setText("Nhập đủ giá trị để xem ví dụ phí cho 10 km."); }
    }

    private void renderItems(java.util.List<AdminListItem> items) {
        if (section == Section.ANALYTICS) return;
        container.removeAllViews();
        if (items == null) return;
        for (AdminListItem item : items) renderItem(item);
    }

    private void renderItem(AdminListItem item) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, 16, 0, 16);
        TextView text = new TextView(requireContext());
        text.setText(getString(R.string.admin_resource_row_format, item.getTitle(), item.getSubtitle(),
                item.getStatus(), item.getId()));
        row.addView(text);
        Button detail = button(R.string.admin_view_detail_action);
        detail.setOnClickListener(v -> openDetail(item));
        row.addView(detail);
        if (section == Section.CATEGORIES || section == Section.BANNERS) {
            Button edit = buttonText("Chỉnh sửa thông tin");
            edit.setOnClickListener(v -> showCatalogEditor(item)); row.addView(edit);
            TextView imageStatus = new TextView(requireContext());
            imageStatus.setText(item.getImageUrl().isEmpty() ? R.string.no_image_selected
                    : R.string.category_banner_image_exists);
            row.addView(imageStatus);
            Button upload = button(R.string.upload_category_banner_image_action);
            upload.setOnClickListener(v -> {
                selectedAssetId = item.getId(); pendingImageButton = upload; pendingImageStatus = imageStatus;
                imagePicker.launch("image/*");
            });
            row.addView(upload);
            Button toggle = button(item.isActive() ? R.string.admin_disable_action
                    : R.string.admin_enable_action);
            toggle.setOnClickListener(v -> viewModel.setActive(item.getId(), !item.isActive(),
                    reason.getText().toString().trim()));
            row.addView(toggle);
        }
        container.addView(row);
    }

    private void showCatalogEditor(AdminListItem item) {
        boolean editing = item != null;
        boolean category = section == Section.CATEGORIES;
        LinearLayout fields = new LinearLayout(requireContext()); fields.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (20 * getResources().getDisplayMetrics().density);
        fields.setPadding(padding, padding, padding, padding);
        EditText id = editor(category ? "ID ổn định (slug)" : "ID Banner (để trống để tự tạo)");
        if (editing) { id.setText(item.getId()); id.setEnabled(false); }
        else if (category) id.setHint("Ví dụ: rau-cu");
        fields.addView(id);
        EditText name = editor(category ? "Tên danh mục" : "Tiêu đề Banner"); fields.addView(name);
        EditText description = null;
        if (category) {
            description = editor("Mô tả danh mục (không bắt buộc)");
            description.setSingleLine(false); description.setMinLines(2); description.setMaxLines(4);
            fields.addView(description);
        }
        final EditText descriptionField = description;
        EditText image = editor("Cloudinary image URL (có thể tải ảnh sau khi tạo)"); fields.addView(image);
        EditText order = editor("Thứ tự hiển thị (0–10000)"); order.setInputType(android.text.InputType.TYPE_CLASS_NUMBER); fields.addView(order);
        EditText campaign = null, start = null, end = null;
        if (!category) {
            campaign = editor("Campaign ID (không bắt buộc)"); fields.addView(campaign);
            start = dateEditor("Ngày bắt đầu (YYYY-MM-DD)"); fields.addView(start);
            end = dateEditor("Ngày kết thúc (YYYY-MM-DD)"); fields.addView(end);
            Calendar defaults = Calendar.getInstance();
            start.setText(formatDay(defaults.getTime())); defaults.add(Calendar.DAY_OF_YEAR, 7);
            end.setText(formatDay(defaults.getTime()));
        }
        final EditText campaignField = campaign, startField = start, endField = end;
        CheckBox active = new CheckBox(requireContext()); active.setText("Đang hiển thị"); fields.addView(active);
        EditText note = editor("Lý do quản trị (bắt buộc khi tắt) "); note.setMinLines(2); fields.addView(note);
        if (editing) {
            viewModel.loadDetail(section, item.getId(), new AdminRepository.DetailCallback() {
                @Override public void onSuccess(Map<String, Object> data) {
                    if (!isAdded()) return;
                    name.setText(String.valueOf(data.get(category ? "name" : "title")));
                    if (category) descriptionField.setText(data.get("description") == null
                            ? "" : String.valueOf(data.get("description")));
                    image.setText(String.valueOf(data.getOrDefault("imageUrl", "")));
                    order.setText(String.valueOf(data.getOrDefault("displayOrder", 0)));
                    active.setChecked(Boolean.TRUE.equals(data.get("active")));
                    note.setText("");
                    if (!category) {
                        Object campaignValue = data.get("campaignId");
                        campaignField.setText(campaignValue == null ? "" : String.valueOf(campaignValue));
                        startField.setText(formatDay(asDate(data.get("startDate"))));
                        endField.setText(formatDay(asDate(data.get("endDate"))));
                    }
                }
                @Override public void onError(AdminRepository.ErrorCode code, String message) {
                    if (isAdded()) Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
                }
            });
        }
        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle(editing ? "Chỉnh sửa " + (category ? "Category" : "Banner")
                        : "Tạo " + (category ? "Category" : "Banner"))
                .setView(fields).setNegativeButton("Hủy", null).setPositiveButton("Lưu", null).create();
        dialog.setOnShowListener(unused -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String resourceId = id.getText().toString().trim();
            String resourceName = name.getText().toString().trim();
            String displayImage = image.getText().toString().trim();
            String reasonText = note.getText().toString().trim();
            int displayOrder;
            try { displayOrder = Integer.parseInt(order.getText().toString().trim()); }
            catch (NumberFormatException error) { order.setError("Nhập số thứ tự hợp lệ."); return; }
            Map<String, Object> values = new java.util.HashMap<>();
            values.put(category ? "name" : "title", resourceName);
            if (category) values.put("description", descriptionField.getText().toString().trim());
            values.put("imageUrl", displayImage); values.put("displayOrder", displayOrder);
            values.put("active", active.isChecked()); values.put("moderationReason", reasonText);
            if (!category) {
                Date startDate = parseDay(startField.getText().toString());
                Date endDate = parseDay(endField.getText().toString());
                if (startDate == null || endDate == null || !endDate.after(startDate)) {
                    endField.setError("Nhập ngày hợp lệ; ngày kết thúc phải sau ngày bắt đầu."); return;
                }
                values.put("campaignId", campaignField.getText().toString().trim());
                values.put("startDate", startDate); values.put("endDate", endDate);
            }
            viewModel.saveCatalogResource(resourceId, values, new AdminRepository.ActionCallback() {
                @Override public void onSuccess() {
                    if (isAdded()) Toast.makeText(requireContext(), "Đã lưu và ghi audit.", Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                }
                @Override public void onError(AdminRepository.ErrorCode code, String message) {
                    if (isAdded()) Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
                }
            });
        }));
        dialog.show();
    }

    private EditText editor(String hint) {
        EditText input = new EditText(requireContext()); input.setHint(hint);
        input.setSingleLine(true); return input;
    }

    private EditText dateEditor(String hint) {
        EditText input = editor(hint); input.setFocusable(false); input.setClickable(true);
        input.setOnClickListener(v -> {
            Calendar value = Calendar.getInstance(); Date current = parseDay(input.getText().toString());
            if (current != null) value.setTime(current);
            new DatePickerDialog(requireContext(), (picker, year, month, day) -> {
                Calendar selected = Calendar.getInstance(); selected.set(year, month, day, 12, 0, 0);
                selected.set(Calendar.MILLISECOND, 0); input.setText(formatDay(selected.getTime()));
            }, value.get(Calendar.YEAR), value.get(Calendar.MONTH), value.get(Calendar.DAY_OF_MONTH)).show();
        });
        return input;
    }

    private String formatDay(Date value) {
        return value == null ? "" : new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(value);
    }

    private Date parseDay(String value) {
        try { return new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(value.trim()); }
        catch (ParseException | RuntimeException error) { return null; }
    }

    private Date asDate(Object value) {
        if (value instanceof Date) return (Date) value;
        if (value instanceof com.google.firebase.Timestamp) return ((com.google.firebase.Timestamp) value).toDate();
        return null;
    }

    private void pickOrderDate(boolean from) {
        Calendar value = Calendar.getInstance();
        Date current = from ? orderFromDate : orderThroughDate;
        if (current != null) value.setTime(current);
        new DatePickerDialog(requireContext(), (picker, year, month, day) -> {
            Calendar selected = Calendar.getInstance();
            selected.set(year, month, day, 0, 0, 0); selected.set(Calendar.MILLISECOND, 0);
            if (from) orderFromDate = selected.getTime();
            else {
                selected.add(Calendar.DAY_OF_YEAR, 1); // inclusive selected day, exclusive upper bound
                orderThroughDate = selected.getTime();
            }
            if (orderFromDate != null && orderThroughDate != null && !orderThroughDate.after(orderFromDate)) {
                Toast.makeText(requireContext(), "Ngày kết thúc phải cùng hoặc sau ngày bắt đầu.", Toast.LENGTH_SHORT).show();
                if (from) orderFromDate = null; else orderThroughDate = null;
            }
            refreshDateLabels(); viewModel.setOrderDateRange(orderFromDate, orderThroughDate);
        }, value.get(Calendar.YEAR), value.get(Calendar.MONTH), value.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void refreshDateLabels() {
        if (orderFromButton != null) orderFromButton.setText(orderFromDate == null ? "Từ ngày"
                : "Từ " + formatDay(orderFromDate));
        if (orderThroughButton != null) {
            Date shown = orderThroughDate == null ? null : new Date(orderThroughDate.getTime() - 1L);
            orderThroughButton.setText(shown == null ? "Đến ngày" : "Đến " + formatDay(shown));
        }
    }

    private void openDetail(AdminListItem item) {
        message.setText("Đang tải chi tiết…");
        viewModel.loadDetail(section, item.getId(), new AdminRepository.DetailCallback() {
            @Override public void onSuccess(Map<String, Object> detail) {
                if (!isAdded()) return;
                message.setText(""); showDetailDialog(item, detail);
            }
            @Override public void onError(AdminRepository.ErrorCode code, String error) {
                if (isAdded()) Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void showDetailDialog(AdminListItem item, Map<String, Object> detail) {
        ScrollView scroll = new ScrollView(requireContext());
        scroll.setBackgroundColor(requireContext().getColor(R.color.rescue_background));
        LinearLayout body = new LinearLayout(requireContext()); body.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (20 * getResources().getDisplayMetrics().density);
        body.setPadding(padding, padding, padding, padding); scroll.addView(body);
        new AdminDetailRenderer(requireContext()).render(body, item, detail);
        if (section == Section.ORDERS) addOrderWarnings(body, detail);
        EditText actionReason = new EditText(requireContext());
        actionReason.setHint(R.string.admin_reason_hint); actionReason.setMinLines(2);
        actionReason.setMaxLines(4); actionReason.setVisibility(section == Section.ORDERS
                || section == Section.CATEGORIES || section == Section.BANNERS ? View.GONE : View.VISIBLE);
        if (actionReason.getVisibility() == View.VISIBLE) {
            TextView heading = new TextView(requireContext()); heading.setText("Thao tác quản trị");
            heading.setTextSize(16); heading.setTypeface(null, android.graphics.Typeface.BOLD);
            heading.setTextColor(requireContext().getColor(R.color.rescue_primary));
            heading.setPadding(0, padding / 2, 0, padding / 2); body.addView(heading);
            com.google.android.material.textfield.TextInputLayout reasonBox = new com.google.android.material.textfield.TextInputLayout(
                    requireContext(), null, com.google.android.material.R.attr.textInputOutlinedStyle);
            reasonBox.setHint(getString(R.string.admin_reason_hint)); actionReason.setHint(null);
            reasonBox.addView(actionReason); body.addView(reasonBox);
        } else body.addView(actionReason);
        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext(), R.style.ThemeOverlay_RescueFarm_AdminDetail).setTitle(item.getTitle())
                .setView(scroll).setNegativeButton("Đóng", null).create();
        if (section == Section.REPORTS) {
            Button openTarget = buttonText("Mở nội dung bị báo cáo");
            openTarget.setOnClickListener(v -> { dialog.dismiss(); openReportedTarget(detail); }); body.addView(openTarget);
            addReportActions(body, dialog, item, detail, actionReason);
        }
        else if (section != Section.ORDERS && section != Section.CATEGORIES && section != Section.BANNERS) {
            for (String target : allowedTargets(item, detail)) {
                Button action = buttonText(adminActionLabel(target, item, detail)); body.addView(action);
                action.setOnClickListener(v -> {
                    String note = actionReason.getText().toString().trim();
                    try { policy.validate(section, item.getStatus(), target, note); }
                    catch (IllegalArgumentException | IllegalStateException error) {
                        Toast.makeText(requireContext(), "Cần lý do hợp lệ hoặc trạng thái đã thay đổi.", Toast.LENGTH_LONG).show();
                        return;
                    }
                    new AlertDialog.Builder(requireContext()).setTitle("Xác nhận thao tác")
                            .setMessage("Bạn có chắc muốn " + adminActionLabel(target, item, detail) + "?")
                            .setNegativeButton("Quay lại", null)
                            .setPositiveButton("Xác nhận", (confirm, which) -> viewModel.transition(item.getId(), target,
                                    note, new AdminRepository.ActionCallback() {
                                        @Override public void onSuccess() {
                                            if (isAdded()) Toast.makeText(requireContext(), "Đã cập nhật và ghi audit.", Toast.LENGTH_SHORT).show();
                                            dialog.dismiss();
                                        }
                                        @Override public void onError(AdminRepository.ErrorCode code, String message) {
                                            if (isAdded()) Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
                                        }
                                    })).show();
                });
            }
        }
        dialog.show();
        if (dialog.getWindow() != null) {
            int availableWidth = getResources().getDisplayMetrics().widthPixels - padding * 2;
            int maxWidth = (int) (720 * getResources().getDisplayMetrics().density);
            dialog.getWindow().setLayout(Math.min(availableWidth, maxWidth), ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    private void addOrderWarnings(LinearLayout body, Map<String, Object> detail) {
        List<String> warnings = new java.util.ArrayList<>();
        Date created = asDate(detail.get("createdAt"));
        long ageMs = created == null ? 0L : Math.max(0L, System.currentTimeMillis() - created.getTime());
        String status = String.valueOf(detail.get("status"));
        if ("PENDING".equals(status) && ageMs >= 24L * 60L * 60L * 1000L)
            warnings.add("Đơn đang chờ xác nhận hơn 24 giờ.");
        if ("BANK_TRANSFER".equals(String.valueOf(detail.get("paymentMethod")))
                && !"PAID".equals(String.valueOf(detail.get("paymentStatus")))
                && ageMs >= 60L * 60L * 1000L)
            warnings.add("Chuyển khoản chưa được xác nhận sau hơn 1 giờ.");
        if (!(detail.get("payment") instanceof Map<?, ?>)) warnings.add("Đơn hàng thiếu thông tin thanh toán.");
        if ("DELIVERY".equals(String.valueOf(detail.get("fulfillmentType")))) {
            Object raw = detail.get("shipment");
            if (!(raw instanceof Map<?, ?>)) warnings.add("Đơn hàng thiếu thông tin vận chuyển.");
            else {
                Map<?, ?> shipment = (Map<?, ?>) raw;
                String shipmentStatus = String.valueOf(shipment.get("status"));
                if ("SHIPPING".equals(status) && !"SHIPPING".equals(shipmentStatus))
                    warnings.add("Trạng thái vận chuyển không khớp trạng thái đơn hàng.");
                if ("DELIVERED".equals(status) && !"DELIVERED".equals(shipmentStatus))
                    warnings.add("Đơn hàng đã hoàn tất nhưng vận chuyển chưa xác nhận đã giao.");
            }
        }
        if (!warnings.isEmpty()) {
            new AdminDetailRenderer(requireContext()).notice(body, warnings);
        }
    }

    private void addReportActions(LinearLayout body, AlertDialog dialog, AdminListItem item,
            Map<String, Object> detail, EditText reasonInput) {
        String status = String.valueOf(detail.get("status"));
        String type = String.valueOf(detail.get("targetType"));
        if ("PENDING".equals(status)) addReportButton(body, dialog, item, reasonInput,
                AdminRepository.ReportAction.MARK_IN_REVIEW, "Bắt đầu xử lý");
        if (supportsHide(type, detail)) addReportButton(body, dialog, item, reasonInput,
                AdminRepository.ReportAction.HIDE_TARGET, "Ẩn nội dung và giải quyết");
        if (hasTargetOwner(type, detail)) addReportButton(body, dialog, item, reasonInput,
                AdminRepository.ReportAction.BLOCK_USER, "Khóa người tạo và giải quyết");
        addReportButton(body, dialog, item, reasonInput,
                AdminRepository.ReportAction.DISMISS, "Bác report");
    }

    private void addReportButton(LinearLayout body, AlertDialog dialog, AdminListItem item,
            EditText reasonInput, AdminRepository.ReportAction action, String label) {
        Button button = buttonText(label); body.addView(button);
        button.setOnClickListener(v -> {
            String note = reasonInput.getText().toString().trim();
            if (action != AdminRepository.ReportAction.MARK_IN_REVIEW && note.length() < 3) {
                reasonInput.setError("Nhập lý do xử lý tối thiểu 3 ký tự."); return;
            }
            new AlertDialog.Builder(requireContext()).setTitle("Xác nhận xử lý report")
                    .setMessage(label + "? Thao tác sẽ được ghi audit.")
                    .setNegativeButton("Quay lại", null)
                    .setPositiveButton("Xác nhận", (confirm, which) -> viewModel.resolveReport(item.getId(), action,
                            note, new AdminRepository.ActionCallback() {
                    @Override public void onSuccess() {
                        if (isAdded()) Toast.makeText(requireContext(), "Đã cập nhật report và ghi audit.", Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                    }
                    @Override public void onError(AdminRepository.ErrorCode code, String message) {
                        if (isAdded()) Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
                    }
                })).show();
        });
    }

    private boolean supportsHide(String type, Map<String, Object> detail) {
        Object raw = detail.get("target"); if (!(raw instanceof Map<?, ?>)) return false;
        Object status = ((Map<?, ?>) raw).get("status"); String current = status == null ? "" : String.valueOf(status);
        return ("PRODUCT".equals(type) && java.util.Arrays.asList("ACTIVE", "INACTIVE", "SOLD_OUT").contains(current))
                || ("POST".equals(type) && "PUBLISHED".equals(current))
                || ("CAMPAIGN".equals(type) && "ACTIVE".equals(current))
                || ("REVIEW".equals(type) && "PUBLISHED".equals(current))
                || ("COMMENT".equals(type) && java.util.Arrays.asList("ACTIVE", "EDITED").contains(current));
    }

    private boolean hasTargetOwner(String type, Map<String, Object> detail) {
        if ("USER".equals(type)) return true;
        Object raw = detail.get("target"); if (!(raw instanceof Map<?, ?>)) return false;
        Map<?, ?> target = (Map<?, ?>) raw;
        return target.containsKey("sellerId") || target.containsKey("customerId") || target.containsKey("userId");
    }

    private void addDetailText(LinearLayout body, String value) {
        TextView line = new TextView(requireContext()); line.setText(value);
        line.setPadding(0, 8, 0, 8); body.addView(line);
    }

    private String adminActionLabel(String status, AdminListItem item, Map<String, Object> detail) {
        if (section == Section.PRODUCTS && "HIDDEN".equals(item.getStatus())
                && java.util.Arrays.asList("ACTIVE", "INACTIVE", "SOLD_OUT").contains(status))
            return "Khôi phục sản phẩm về " + AdminDetailFormatter.status(status);
        switch (status) {
            case "APPROVED": case "ACTIVE": case "PUBLISHED": return "Phê duyệt / khôi phục";
            case "REJECTED": return "Từ chối"; case "HIDDEN": return "Ẩn";
            case "STOPPED": return "Dừng chiến dịch"; case "BLOCKED": return "Khóa tài khoản";
            default: return status;
        }
    }

    private List<String> allowedTargets(AdminListItem item, Map<String, Object> detail) {
        if (section == Section.PRODUCTS && "HIDDEN".equals(item.getStatus())) {
            String previous = detail.get("moderationPreviousStatus") == null ? "INACTIVE"
                    : String.valueOf(detail.get("moderationPreviousStatus"));
            if (!java.util.Arrays.asList("ACTIVE", "INACTIVE", "SOLD_OUT").contains(previous)) previous = "INACTIVE";
            return java.util.Collections.singletonList(previous);
        }
        return policy.allowedTargets(section, item.getStatus());
    }

    private void openReportedTarget(Map<String, Object> detail) {
        String type = String.valueOf(detail.get("targetType"));
        String targetId = String.valueOf(detail.get("targetId"));
        Object raw = detail.get("target");
        Map<?, ?> target = raw instanceof Map<?, ?> ? (Map<?, ?>) raw : java.util.Collections.emptyMap();
        Bundle args = new Bundle();
        int destination = 0;
        if ("PRODUCT".equals(type)) { destination = R.id.productDetailFragment; args.putString("productId", targetId); }
        else if ("CAMPAIGN".equals(type)) { destination = R.id.campaignDetailFragment; args.putString("campaignId", targetId); }
        else if ("POST".equals(type)) { destination = R.id.postDetailFragment; args.putString("postId", targetId); }
        else if ("COMMENT".equals(type)) {
            destination = R.id.postDetailFragment; args.putString("postId", String.valueOf(target.get("postId")));
        } else if ("REVIEW".equals(type)) {
            destination = R.id.reviewFragment; args.putString("productId", String.valueOf(target.get("productId")));
        }
        if (destination == 0) {
            Toast.makeText(requireContext(), "Loại đối tượng này chỉ xem được trong chi tiết report.", Toast.LENGTH_SHORT).show();
            return;
        }
        androidx.navigation.Navigation.findNavController(requireView()).navigate(destination, args);
    }

    private void renderAnalytics(AdminDashboardSnapshot value) {
        if (value == null) return;
        NumberFormat currency = NumberFormat.getCurrencyInstance(new Locale("vi", "VN"));
        AdminDashboardCards.addSectionTitle(requireContext(), container, "TỔNG QUAN HỆ THỐNG");
        AdminDashboardCards.addGrid(requireContext(), container, Arrays.asList(
                new AdminDashboardCards.Metric("Tổng hàng đợi moderation",
                        String.valueOf(value.getPendingModerationTotal()), "Đang chờ xử lý"),
                new AdminDashboardCards.Metric("Users", String.valueOf(value.total(Section.USERS)), "Tổng tài khoản"),
                new AdminDashboardCards.Metric("Products", String.valueOf(value.total(Section.PRODUCTS)), "Tổng sản phẩm"),
                new AdminDashboardCards.Metric("Campaigns", String.valueOf(value.total(Section.CAMPAIGNS)), "Tổng chiến dịch"),
                new AdminDashboardCards.Metric("Orders", String.valueOf(value.total(Section.ORDERS)), "Tổng đơn hàng"),
                new AdminDashboardCards.Metric("Doanh thu DELIVERED", currency.format(value.getDeliveredRevenue()),
                        "All-time, gồm phí giao hàng")));

        AdminDashboardCards.addSectionTitle(requireContext(), container, "RESCUE ANALYTICS");
        AdminDashboardCards.addGrid(requireContext(), container, Arrays.asList(
                new AdminDashboardCards.Metric("Campaign đang hoạt động", String.valueOf(value.getActiveCampaigns()), "Status ACTIVE"),
                new AdminDashboardCards.Metric("Campaign CRITICAL", String.valueOf(value.getCriticalCampaigns()), "Trong Campaign ACTIVE"),
                new AdminDashboardCards.Metric("Điểm mobile đang chia sẻ vị trí mới", String.valueOf(value.getLiveMobilePoints()),
                        "Vị trí được cập nhật trong 10 phút"),
                new AdminDashboardCards.Metric("Tổng lượng đã giải cứu", String.format(Locale.getDefault(), "%.2f kg",
                        value.getTotalRescuedQuantity()), "Số lượng đã rescued"),
                new AdminDashboardCards.Metric("Tỷ lệ hoàn thành", String.format(Locale.getDefault(), "%.1f%%",
                        value.getCompletionRate()),
                        "rescued / target của Campaign ACTIVE, COMPLETED, EXPIRED, STOPPED đã duyệt")));
        AdminDashboardCards.addFootnote(requireContext(), container,
                "Doanh thu là tổng all-time của đơn DELIVERED, gồm phí giao hàng."
                        + (value.getCalculatedAt().isEmpty() ? "" : "\nCập nhật: " + value.getCalculatedAt()));
    }

    private Button button(int label) { Button button = new Button(requireContext()); button.setText(label); return button; }
    private Button buttonText(String label) { Button button = new Button(requireContext()); button.setText(label); return button; }
    private String label(Section value) { return value.name().replace('_', ' '); }
}
