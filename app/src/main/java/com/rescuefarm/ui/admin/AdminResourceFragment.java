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
import android.widget.CheckBox;
import android.widget.Toast;
import android.app.DatePickerDialog;
import androidx.appcompat.app.AlertDialog;
import com.google.android.material.button.MaterialButton;
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
import com.rescuefarm.service.media.CloudinaryUploadUi;
import com.rescuefarm.data.repository.AdminRepository;
import com.bumptech.glide.Glide;
import java.util.Map;
import java.util.List;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.Calendar;
import java.util.Date;
import java.text.SimpleDateFormat;
import java.text.ParseException;

public final class AdminResourceFragment extends Fragment {
    private final android.os.Handler timeHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable timeRefresh = new Runnable() {
        @Override public void run() {
            if (getView() != null && viewModel != null && section == Section.ANALYTICS
                    && (viewModel.getState().getValue() == null || viewModel.getState().getValue().getStatus() != AdminScreenState.Status.LOADING))
                viewModel.refreshDashboard();
            timeHandler.postDelayed(this, 60000L);
        }
    };
    private AdminViewModel viewModel;
    private Section section;
    private TextView title, message;
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
    private boolean resumedOnce;
    private final Runnable pendingSearch = () -> {
        if (viewModel != null) viewModel.setSearchTerm(searchTerm);
    };
    private String selectedAssetId = "";
    private View pendingImageButton;
    private TextView pendingImageStatus;
    private ImageView pendingImagePreview;
    private CloudinaryUploadUi.Result pendingImageResult;
    private final ActivityResultLauncher<String> imagePicker = registerForActivityResult(
            new ActivityResultContracts.GetContent(), uri -> {
                if (selectedAssetId.isEmpty()) return;
                String targetId = selectedAssetId;
                String assetType = section == Section.CATEGORIES ? "category" : "banner";
                if (uri == null) return;
                ImageView preview = pendingImagePreview;
                TextView status = pendingImageStatus;
                CloudinaryUploadUi.Result result = pendingImageResult;
                pendingImageResult = null;
                status.setVisibility(View.VISIBLE);
                CloudinaryUploadUi.upload(this, uri, assetType, targetId, 0, pendingImageButton, status,
                        preview, url -> {
                            if (result != null) { result.onUploaded(url); return; }
                            preview.setVisibility(View.VISIBLE); status.setVisibility(View.GONE);
                            viewModel.setResourceImage(targetId, url);
                        });
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
        view.findViewById(R.id.adminModerationReason).setVisibility(View.GONE);
        progress = view.findViewById(R.id.adminResourceProgress);
        container = view.findViewById(R.id.adminResourceContainer);
        searchInput = view.findViewById(R.id.adminSearchInput);
        statusFilter = view.findViewById(R.id.adminStatusFilter);
        loadMoreButton = view.findViewById(R.id.loadMoreAdminItemsButton);
        View dateFilters = view.findViewById(R.id.adminOrderDateFilters);
        orderFromButton = view.findViewById(R.id.adminOrderFromDate);
        orderThroughButton = view.findViewById(R.id.adminOrderThroughDate);
        title.setText(getString(R.string.admin_section_title_format, label(section)));
        styleSectionButton(view.findViewById(R.id.adminResourceBackButton));
        styleSectionButton(view.findViewById(R.id.adminAddCatalogResourceButton));
        styleSectionButton(orderFromButton);
        styleSectionButton(orderThroughButton);
        styleSectionButton(view.findViewById(R.id.adminOrderClearDates));
        styleSectionButton(view.findViewById(R.id.refreshAdminResourceButton));
        styleSectionButton(loadMoreButton);
        View addCatalog = view.findViewById(R.id.adminAddCatalogResourceButton);
        if (section == Section.CATEGORIES || section == Section.BANNERS) {
            addCatalog.setVisibility(View.VISIBLE);
            addCatalog.setOnClickListener(v -> showCatalogEditor(null));
        }
        if (section == Section.SHIPPING_SETTINGS) {
            addCatalog.setVisibility(View.GONE);
            searchInput.setVisibility(View.GONE); statusFilter.setVisibility(View.GONE);
            view.findViewById(R.id.adminOrderDateFilters).setVisibility(View.GONE);
            loadMoreButton.setVisibility(View.GONE);
        }
        // Admin hide/show actions on catalog resources do not require a reason.
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
            else if (section == Section.REPORTS) searchInput.setHint("Tìm tiêu đề, nội dung hoặc target:<ID>");
            else searchInput.setHint("Tìm theo tiêu đề, tên hoặc ID (không dấu cũng được)");
            statusFilter.setAdapter(new com.rescuefarm.ui.common.CodeLabelAdapter(requireContext(),
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
        View backButton = view.findViewById(R.id.adminResourceBackButton);
        backButton.setOnClickListener(v -> androidx.navigation.fragment.NavHostFragment.findNavController(this).popBackStack());
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
        loadMoreButton.setEnabled(state.getStatus() != AdminScreenState.Status.LOADING);
        ((TextView) loadMoreButton).setText(state.getStatus() == AdminScreenState.Status.LOADING
                ? R.string.pagination_loading : R.string.pagination_more);
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
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        int padding = listDp(14);
        row.setPadding(padding, padding, padding, padding);
        LinearLayout information = new LinearLayout(requireContext());
        information.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams informationParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        informationParams.setMarginEnd(listDp(12));
        row.addView(information, informationParams);
        addListText(information, item.getTitle(), 18, true);
        if (!item.getSubtitle().isEmpty()) addListText(information, item.getSubtitle(), 16, false);
        addListText(information, "Trạng thái: " + AdminDetailFormatter.status(item.getStatus()), 16, false);
        addListText(information, "ID: " + item.getId(), 14, false);
        LinearLayout actions = new LinearLayout(requireContext());
        actions.setOrientation(LinearLayout.VERTICAL);
        row.addView(actions, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        Button detail = listButton("Chi tiết", getString(R.string.admin_view_detail_action));
        detail.setOnClickListener(v -> openDetail(item));
        actions.addView(detail);
        android.graphics.drawable.GradientDrawable outline = new android.graphics.drawable.GradientDrawable();
        outline.setColor(android.graphics.Color.TRANSPARENT);
        outline.setCornerRadius(listDp(16));
        outline.setStroke(listDp(1), ((MaterialButton) detail).getStrokeColor().getDefaultColor());
        row.setBackground(outline);
        if (section == Section.CATEGORIES || section == Section.BANNERS) {
            Button edit = listButton("Sửa", "Chỉnh sửa thông tin");
            edit.setOnClickListener(v -> showCatalogEditor(item)); actions.addView(edit);
            ImageView preview = catalogImage(information, item.getImageUrl());
            TextView imageStatus = new TextView(requireContext());
            imageStatus.setVisibility(View.GONE);
            information.addView(imageStatus);
            Button upload = listButton("Tải ảnh", getString(R.string.upload_category_banner_image_action));
            upload.setOnClickListener(v -> {
                selectedAssetId = item.getId(); pendingImageButton = upload; pendingImageStatus = imageStatus; pendingImagePreview = preview;
                pendingImageResult = null;
                imagePicker.launch("image/*");
            });
            actions.addView(upload);
            Button toggle = listButton(item.isActive() ? "Ẩn" : "Hiện", getString(item.isActive()
                    ? R.string.admin_disable_action : R.string.admin_enable_action));
            toggle.setOnClickListener(v -> changeCatalogVisibility(item, toggle));
            actions.addView(toggle);
        }
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        cardParams.topMargin = listDp(6); cardParams.bottomMargin = listDp(6);
        container.addView(row, cardParams);
    }

    private void changeCatalogVisibility(AdminListItem item, Button toggle) {
        boolean show = !item.isActive();
        if (show) {
            toggle.setEnabled(false);
            viewModel.setActive(item.getId(), true, "");
            return;
        }
        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle("Ẩn nội dung")
                .setMessage("Bạn có muốn ẩn nội dung này không?")
                .setNegativeButton("Hủy", null)
                .setPositiveButton("Ẩn", (d, which) -> {
                    toggle.setEnabled(false);
                    viewModel.setActive(item.getId(), false, "");
                }).show();
    }

    private void addListText(LinearLayout parent, String value, int size, boolean bold) {
        TextView text = new TextView(requireContext());
        text.setText(value); text.setTextSize(size);
        text.setTextColor(requireContext().getColor(bold ? R.color.rescue_on_surface : R.color.rescue_on_surface_variant));
        if (bold) text.setTypeface(text.getTypeface(), android.graphics.Typeface.BOLD);
        if (bold) com.rescuefarm.ui.common.AppTypography.title(text);
        text.setPadding(0, 0, 0, listDp(5));
        parent.addView(text, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private Button listButton(String label, String description) {
        MaterialButton button = (MaterialButton) buttonText(label);
        button.setContentDescription(description);
        button.setMinWidth(0); button.setMinimumWidth(0);
        button.setMinHeight(listDp(40)); button.setMinimumHeight(listDp(40));
        button.setPaddingRelative(listDp(10), listDp(4), listDp(10), listDp(4));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.gravity = android.view.Gravity.END; params.topMargin = listDp(3); params.bottomMargin = listDp(3);
        button.setLayoutParams(params);
        return button;
    }

    private int listDp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

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
        Button upload = buttonText("Tải ảnh từ thiết bị"); fields.addView(upload);
        TextView uploadStatus = new TextView(requireContext()); uploadStatus.setVisibility(View.GONE); fields.addView(uploadStatus);
        final String[] originalImageUrl = {editing ? item.getImageUrl() : ""};
        ImageView imagePreview = catalogImage(fields, originalImageUrl[0]);
        Button externalImage = buttonText("Dán link ảnh ngoài (HTTPS)"); fields.addView(externalImage);
        EditText image = editor("Dán đường dẫn ảnh HTTPS");
        image.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        image.setVisibility(View.GONE); fields.addView(image);
        externalImage.setOnClickListener(v -> {
            image.setVisibility(image.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
            if (image.getVisibility() == View.VISIBLE) image.requestFocus();
        });
        image.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence value, int start, int count, int after) { }
            public void onTextChanged(CharSequence value, int start, int before, int count) {
                showCatalogImage(imagePreview, value.toString().trim());
            }
            public void afterTextChanged(android.text.Editable value) { }
        });
        EditText order = editor("Thứ tự hiển thị (0–10000)"); order.setInputType(android.text.InputType.TYPE_CLASS_NUMBER); fields.addView(order);
        Button campaign = null;
        final String[] selectedCampaignId = {""};
        EditText start = null, end = null;
        if (!category) {
            addDetailText(fields, "Chiến dịch liên kết (không bắt buộc)");
            campaign = buttonText("Không liên kết chiến dịch"); fields.addView(campaign);
            start = dateEditor("Ngày bắt đầu (YYYY-MM-DD)"); fields.addView(start);
            end = dateEditor("Ngày kết thúc (YYYY-MM-DD)"); fields.addView(end);
            Calendar defaults = Calendar.getInstance();
            start.setText(formatDay(defaults.getTime())); defaults.add(Calendar.DAY_OF_YEAR, 7);
            end.setText(formatDay(defaults.getTime()));
        }
        final Button campaignField = campaign;
        if (campaignField != null) campaignField.setOnClickListener(v ->
                chooseBannerCampaign(campaignField, selectedCampaignId));
        final EditText startField = start, endField = end;
        CheckBox active = new CheckBox(requireContext()); active.setText("Đang hiển thị"); active.setChecked(true); fields.addView(active);
        EditText note = editor("Lý do quản trị (bắt buộc khi tắt) "); note.setMinLines(2); fields.addView(note);
        final boolean[] detailLoaded = {!editing};
        upload.setEnabled(!editing);
        if (editing) {
            viewModel.loadDetail(section, item.getId(), new AdminRepository.DetailCallback() {
                @Override public void onSuccess(Map<String, Object> data) {
                    if (!isAdded()) return;
                    detailLoaded[0] = true; upload.setEnabled(true);
                    name.setText(String.valueOf(data.get(category ? "name" : "title")));
                    if (category) descriptionField.setText(data.get("description") == null
                            ? "" : String.valueOf(data.get("description")));
                    originalImageUrl[0] = data.get("imageUrl") == null ? "" : String.valueOf(data.get("imageUrl"));
                    image.setText(originalImageUrl[0]);
                    order.setText(String.valueOf(data.getOrDefault("displayOrder", 0)));
                    active.setChecked(Boolean.TRUE.equals(data.get("active")));
                    note.setText("");
                    if (!category) {
                        Object campaignValue = data.get("campaignId");
                        selectedCampaignId[0] = campaignValue == null ? "" : String.valueOf(campaignValue);
                        showSelectedCampaign(campaignField, selectedCampaignId);
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
                .setTitle(editing ? "Chỉnh sửa " + (category ? "danh mục" : "ảnh quảng bá")
                        : "Tạo " + (category ? "danh mục" : "ảnh quảng bá"))
                .setView(scrollEditor(fields)).setNegativeButton("Hủy", null).setPositiveButton("Lưu", null).create();
        upload.setOnClickListener(v -> {
            String assetId = id.getText().toString().trim();
            if (assetId.isEmpty() && !category) {
                assetId = java.util.UUID.randomUUID().toString(); id.setText(assetId);
            }
            if (!assetId.matches("[A-Za-z0-9_-]{2,128}")) {
                id.setError("Nhập ID gồm 2–128 chữ, số, dấu gạch ngang hoặc gạch dưới trước khi tải ảnh.");
                id.requestFocus(); return;
            }
            id.setEnabled(false);
            selectedAssetId = assetId; pendingImageButton = upload; pendingImageStatus = uploadStatus;
            pendingImagePreview = imagePreview;
            pendingImageResult = url -> {
                if (!dialog.isShowing()) return;
                image.setText(url); image.setError(null); imagePreview.setVisibility(View.VISIBLE);
            };
            imagePicker.launch("image/*");
        });
        dialog.setOnShowListener(unused -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (!detailLoaded[0] || !upload.isEnabled()) {
                Toast.makeText(requireContext(), "Vui lòng chờ tải dữ liệu hoặc tải ảnh hoàn tất.", Toast.LENGTH_SHORT).show(); return;
            }
            String resourceId = id.getText().toString().trim();
            String resourceName = name.getText().toString().trim();
            String displayImage = image.getText().toString().trim();
            String reasonText = note.getText().toString().trim();
            if (!com.rescuefarm.service.admin.CatalogImagePolicy.isAllowed(displayImage, originalImageUrl[0])) {
                image.setVisibility(View.VISIBLE); image.setError("Nhập đường dẫn ảnh HTTPS hợp lệ."); return;
            }
            if (resourceName.length() < (category ? 2 : 3) || resourceName.length() > 120) {
                name.setError(category ? "Tên danh mục phải có từ 2 đến 120 ký tự." : "Tiêu đề phải có từ 3 đến 120 ký tự."); return;
            }
            if (!active.isChecked() && reasonText.length() < 3) {
                note.setError("Nhập lý do tắt hiển thị (ít nhất 3 ký tự)."); return;
            }
            int displayOrder;
            try { displayOrder = Integer.parseInt(order.getText().toString().trim()); }
            catch (NumberFormatException error) { order.setError("Nhập số thứ tự hợp lệ."); return; }
            if (displayOrder < 0 || displayOrder > 10000) { order.setError("Thứ tự phải từ 0 đến 10000."); return; }
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
                values.put("campaignId", selectedCampaignId[0]);
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

    private void showSelectedCampaign(Button button, String[] selectedId) {
        if (selectedId[0].isEmpty()) { button.setText("Không liên kết chiến dịch"); return; }
        String requestedId = selectedId[0];
        button.setText("Đang tải tên chiến dịch…");
        viewModel.loadDetail(Section.CAMPAIGNS, requestedId, new AdminRepository.DetailCallback() {
            @Override public void onSuccess(Map<String, Object> data) {
                if (!isAdded() || !requestedId.equals(selectedId[0])) return;
                Object title = data.get("title") == null ? data.get("name") : data.get("title");
                button.setText(title == null ? "Chọn lại chiến dịch liên kết" : String.valueOf(title));
            }
            @Override public void onError(AdminRepository.ErrorCode code, String message) {
                if (isAdded() && requestedId.equals(selectedId[0])) button.setText("Không tải được chiến dịch – nhấn chọn lại");
            }
        });
    }

    private void chooseBannerCampaign(Button button, String[] selectedId) {
        button.setEnabled(false);
        viewModel.loadBannerCampaigns(new AdminRepository.ListCallback() {
            @Override public void onSuccess(List<AdminListItem> campaigns) {
                button.setEnabled(true);
                if (!isAdded() || !button.isAttachedToWindow()) return;
                LinearLayout body = new LinearLayout(requireContext()); body.setOrientation(LinearLayout.VERTICAL);
                int pad = (int) (20 * getResources().getDisplayMetrics().density); body.setPadding(pad, 0, pad, 0);
                EditText search = editor("Tìm theo tên chiến dịch"); body.addView(search);
                TextView empty = new TextView(requireContext()); empty.setText("Không có chiến dịch đang hoạt động phù hợp."); body.addView(empty);
                android.widget.ListView list = new android.widget.ListView(requireContext());
                body.addView(list, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        (int) (300 * getResources().getDisplayMetrics().density)));
                list.setEmptyView(empty);
                List<AdminListItem> visible = new java.util.ArrayList<>();
                List<String> names = new java.util.ArrayList<>();
                ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_list_item_1, names);
                list.setAdapter(adapter);
                Runnable filter = () -> {
                    String term = search.getText().toString().trim();
                    visible.clear(); names.clear();
                    for (AdminListItem campaign : campaigns) if (com.rescuefarm.service.admin.AdminSearchText
                            .contains(campaign.getTitle(), term)) {
                        visible.add(campaign); names.add(campaign.getTitle());
                    }
                    adapter.notifyDataSetChanged();
                };
                search.addTextChangedListener(new android.text.TextWatcher() {
                    public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
                    public void onTextChanged(CharSequence s, int start, int before, int count) { filter.run(); }
                    public void afterTextChanged(android.text.Editable s) { }
                });
                filter.run();
                AlertDialog picker = new AlertDialog.Builder(requireContext()).setTitle("Chọn chiến dịch")
                        .setView(body).setNegativeButton("Hủy", null).setNeutralButton("Không liên kết", (dialog, which) -> {
                            selectedId[0] = ""; button.setText("Không liên kết chiến dịch");
                        }).create();
                list.setOnItemClickListener((parent, row, position, itemId) -> {
                    AdminListItem choice = visible.get(position); selectedId[0] = choice.getId();
                    button.setText(choice.getTitle()); picker.dismiss();
                });
                picker.show();
            }
            @Override public void onError(AdminRepository.ErrorCode code, String message) {
                button.setEnabled(true);
                if (isAdded()) Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private View scrollEditor(View fields) {
        android.widget.ScrollView scroll = new android.widget.ScrollView(requireContext());
        scroll.addView(fields); return scroll;
    }

    private ImageView catalogImage(LinearLayout parent, String url) {
        com.google.android.material.imageview.ShapeableImageView image =
                new com.google.android.material.imageview.ShapeableImageView(requireContext());
        float density = getResources().getDisplayMetrics().density;
        image.setShapeAppearanceModel(image.getShapeAppearanceModel().toBuilder().setAllCornerSizes(12 * density).build());
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setContentDescription(section == Section.CATEGORIES ? "Ảnh danh mục" : "Ảnh quảng bá");
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) (156 * density));
        params.topMargin = (int) (8 * density); params.bottomMargin = (int) (8 * density);
        parent.addView(image, params); showCatalogImage(image, url); return image;
    }

    private void showCatalogImage(ImageView image, String url) {
        image.setVisibility(url.isEmpty() ? View.GONE : View.VISIBLE);
        if (url.isEmpty()) { Glide.with(image).clear(image); return; }
        Glide.with(image).load(url).placeholder(android.R.drawable.ic_menu_gallery)
                .error(android.R.drawable.ic_menu_gallery).centerCrop().into(image);
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
        Bundle args = new Bundle();
        args.putString("section", section.name());
        args.putString("itemId", item.getId());
        args.putString("itemTitle", item.getTitle());
        args.putString("itemSubtitle", item.getSubtitle());
        args.putString("itemStatus", item.getStatus());
        args.putBoolean("itemActive", item.isActive());
        args.putString("itemImageUrl", item.getImageUrl());
        androidx.navigation.Navigation.findNavController(requireView())
                .navigate(R.id.action_adminResourceFragment_to_adminDetailFragment, args);
    }

    private void addDetailText(LinearLayout body, String value) {
        TextView line = new TextView(requireContext()); line.setText(value);
        line.setPadding(0, 8, 0, 8); body.addView(line);
    }

    private void renderAnalytics(AdminDashboardSnapshot value) {
        if (value == null) return;
        NumberFormat currency = NumberFormat.getCurrencyInstance(new Locale("vi", "VN"));
        AdminDashboardCards.addSectionTitle(requireContext(), container, "TỔNG QUAN HỆ THỐNG");
        AdminDashboardCards.addColumnChart(requireContext(), container,
                "Tổng quan số lượng", "Moderation là số đang chờ; các mục còn lại là tổng hiện có",
                new String[]{"Moderation", "Users", "Products", "Campaigns", "Orders"},
                new double[]{value.getPendingModerationTotal(), value.total(Section.USERS),
                        value.total(Section.PRODUCTS), value.total(Section.CAMPAIGNS), value.total(Section.ORDERS)},
                new String[]{String.valueOf(value.getPendingModerationTotal()),
                        String.valueOf(value.total(Section.USERS)), String.valueOf(value.total(Section.PRODUCTS)),
                        String.valueOf(value.total(Section.CAMPAIGNS)), String.valueOf(value.total(Section.ORDERS))}, 0);
        AdminDashboardCards.addColumnChart(requireContext(), container,
                "Doanh thu đơn đã giao", "All-time, gồm phí giao hàng",
                new String[]{"Đã giao hàng"}, new double[]{value.getDeliveredRevenue()},
                new String[]{currency.format(value.getDeliveredRevenue())}, 1);

        AdminDashboardCards.addSectionTitle(requireContext(), container, "RESCUE ANALYTICS");
        AdminDashboardCards.addColumnChart(requireContext(), container,
                "Campaign đang hoạt động và cần ưu tiên", "Các chiến dịch rất khẩn cấp đang hoạt động",
                new String[]{"Đang hoạt động", "Rất khẩn cấp"},
                new double[]{value.getActiveCampaigns(), value.getCriticalCampaigns()},
                new String[]{String.valueOf(value.getActiveCampaigns()), String.valueOf(value.getCriticalCampaigns())}, 0);
        AdminDashboardCards.addColumnChart(requireContext(), container,
                "Điểm giải cứu di động", "Vị trí được cập nhật trong 10 phút",
                new String[]{"Mobile point"}, new double[]{value.getLiveMobilePoints()},
                new String[]{String.valueOf(value.getLiveMobilePoints())}, 3);
        AdminDashboardCards.addColumnChart(requireContext(), container,
                "Đã giải cứu (Campaign kg)", "Số lượng đã rescued",
                new String[]{"Đã giải cứu"}, new double[]{value.getTotalRescuedQuantity()},
                new String[]{String.format(Locale.getDefault(), "%.2f kg", value.getTotalRescuedQuantity())}, 2);
        AdminDashboardCards.addCompletionChart(requireContext(), container,
                "Tỷ lệ hoàn thành (Campaign kg)",
                "Rescued / target của Campaign kg đã xác minh và được kích hoạt",
                value.getCompletionRate());
        AdminDashboardCards.addFootnote(requireContext(), container,
                "Doanh thu là tổng all-time của đơn đã giao, gồm phí giao hàng."
                        + (value.getCalculatedAt().isEmpty() ? "" : "\nCập nhật: " + value.getCalculatedAt()));
    }

    private Button button(int label) {
        MaterialButton button = new MaterialButton(requireContext());
        AdminUiStyles.sectionButton(requireContext(), button, section);
        button.setText(label);
        return button;
    }
    private Button buttonText(String label) {
        MaterialButton button = new MaterialButton(requireContext());
        AdminUiStyles.sectionButton(requireContext(), button, section);
        button.setText(label);
        return button;
    }
    private void styleSectionButton(View view) {
        if (view instanceof MaterialButton) {
            AdminUiStyles.sectionButton(requireContext(), (MaterialButton) view, section);
        }
    }
    private String label(Section value) { return com.rescuefarm.ui.common.EnumLabels.label(value); }

    @Override public void onResume() {
        super.onResume();
        timeHandler.removeCallbacks(timeRefresh); timeHandler.postDelayed(timeRefresh, 60000L);
        if (resumedOnce && section != Section.SHIPPING_SETTINGS) {
            viewModel.refreshSection(section);
        }
        resumedOnce = true;
    }
    @Override public void onPause() {
        timeHandler.removeCallbacks(timeRefresh); super.onPause();
    }
}
