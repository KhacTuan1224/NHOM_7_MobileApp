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
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
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
import java.text.NumberFormat;
import java.util.Locale;

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
    private String searchTerm = "";
    private boolean bindingStatusFilter;
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
        title.setText(getString(R.string.admin_section_title_format, label(section)));
        reason.setVisibility(section == Section.CATEGORIES || section == Section.BANNERS
                ? View.VISIBLE : View.GONE);
        viewModel = new ViewModelProvider(this, new AdminViewModelFactory(requireContext()))
                .get(AdminViewModel.class);
        viewModel.getState().observe(getViewLifecycleOwner(), this::render);
        if (section != Section.ANALYTICS) {
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
                    searchTerm = s == null ? "" : s.toString().trim().toLowerCase(Locale.ROOT);
                    AdminScreenState current = viewModel.getState().getValue();
                    if (current != null) renderItems(current.getItems());
                }
                @Override public void afterTextChanged(android.text.Editable s) { }
            });
            loadMoreButton.setOnClickListener(v -> viewModel.loadNextPage());
        } else {
            searchInput.setVisibility(View.GONE); statusFilter.setVisibility(View.GONE);
            loadMoreButton.setVisibility(View.GONE);
        }
        view.findViewById(R.id.refreshAdminResourceButton).setOnClickListener(v -> refresh());
        refresh();
    }

    private void refresh() {
        if (section == Section.ANALYTICS) viewModel.refreshDashboard();
        else viewModel.refreshSection(section);
    }

    private void render(AdminScreenState state) {
        progress.setVisibility(state.getStatus() == AdminScreenState.Status.LOADING ? View.VISIBLE : View.GONE);
        message.setText(state.getMessage());
        container.removeAllViews();
        if (section == Section.ANALYTICS) { renderAnalytics(state.getDashboard()); return; }
        renderItems(state.getItems());
        loadMoreButton.setVisibility(viewModel.hasMore() ? View.VISIBLE : View.GONE);
    }

    private void renderItems(java.util.List<AdminListItem> items) {
        if (section == Section.ANALYTICS) return;
        container.removeAllViews();
        if (items == null) return;
        for (AdminListItem item : items) {
            String searchable = (item.getTitle() + " " + item.getSubtitle() + " " + item.getId())
                    .toLowerCase(Locale.ROOT);
            if (searchTerm.isEmpty() || searchable.contains(searchTerm)) renderItem(item);
        }
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
        } else if (section != Section.SELLER_APPLICATIONS && section != Section.REPORTS
                && section != Section.ORDERS && section != Section.USERS) {
            for (String target : policy.allowedTargets(section, item.getStatus())) {
                Button action = buttonText(target);
                action.setOnClickListener(v -> viewModel.transition(item.getId(), target,
                        reason.getText().toString().trim()));
                row.addView(action);
            }
        }
        container.addView(row);
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
        LinearLayout body = new LinearLayout(requireContext()); body.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (20 * getResources().getDisplayMetrics().density);
        body.setPadding(padding, padding, padding, padding); scroll.addView(body);
        addDetailText(body, item.getTitle() + "\n" + item.getStatus() + " · ID " + item.getId());
        addDetailMap(body, detail, "");
        EditText actionReason = new EditText(requireContext());
        actionReason.setHint(R.string.admin_reason_hint); actionReason.setMinLines(2);
        actionReason.setMaxLines(4); actionReason.setVisibility(section == Section.ORDERS
                || section == Section.CATEGORIES || section == Section.BANNERS ? View.GONE : View.VISIBLE);
        body.addView(actionReason);
        AlertDialog dialog = new AlertDialog.Builder(requireContext()).setTitle(item.getTitle())
                .setView(scroll).setNegativeButton("Đóng", null).create();
        if (section == Section.REPORTS) {
            Button openTarget = buttonText("Mở nội dung bị báo cáo");
            openTarget.setOnClickListener(v -> { dialog.dismiss(); openReportedTarget(detail); }); body.addView(openTarget);
            addReportActions(body, dialog, item, detail, actionReason);
        }
        else if (section != Section.ORDERS && section != Section.CATEGORIES && section != Section.BANNERS) {
            for (String target : policy.allowedTargets(section, item.getStatus())) {
                Button action = buttonText(adminActionLabel(target)); body.addView(action);
                action.setOnClickListener(v -> viewModel.transition(item.getId(), target,
                        actionReason.getText().toString().trim(), new AdminRepository.ActionCallback() {
                            @Override public void onSuccess() {
                                if (isAdded()) Toast.makeText(requireContext(), "Đã cập nhật và ghi audit.", Toast.LENGTH_SHORT).show();
                                dialog.dismiss();
                            }
                            @Override public void onError(AdminRepository.ErrorCode code, String message) {
                                if (isAdded()) Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
                            }
                        }));
            }
        }
        dialog.show();
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
        button.setOnClickListener(v -> viewModel.resolveReport(item.getId(), action,
                reasonInput.getText().toString().trim(), new AdminRepository.ActionCallback() {
                    @Override public void onSuccess() {
                        if (isAdded()) Toast.makeText(requireContext(), "Đã cập nhật report và ghi audit.", Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                    }
                    @Override public void onError(AdminRepository.ErrorCode code, String message) {
                        if (isAdded()) Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
                    }
                }));
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

    private void addDetailMap(LinearLayout body, Map<?, ?> data, String prefix) {
        for (Map.Entry<?, ?> entry : data.entrySet()) {
            String key = String.valueOf(entry.getKey()); Object value = entry.getValue();
            if ("password".equalsIgnoreCase(key) || "passwordHash".equalsIgnoreCase(key)) continue;
            if (value instanceof Map<?, ?>) {
                addDetailText(body, "\n" + humanize(key)); addDetailMap(body, (Map<?, ?>) value, prefix + key + ".");
            } else if (value instanceof List<?>) {
                addDetailText(body, humanize(key) + ":");
                for (Object row : (List<?>) value) if (row instanceof Map<?, ?>)
                    addDetailMap(body, (Map<?, ?>) row, prefix + key + ".");
                else if (row instanceof String && ((String) row).startsWith("https://res.cloudinary.com/")) {
                    ImageView image = new ImageView(requireContext()); image.setAdjustViewBounds(true);
                    image.setMaxHeight((int) (240 * getResources().getDisplayMetrics().density));
                    body.addView(image); Glide.with(this).load(row).centerCrop().into(image);
                } else addDetailText(body, String.valueOf(row));
            } else if (value instanceof String && ((String) value).startsWith("https://res.cloudinary.com/")) {
                ImageView image = new ImageView(requireContext()); image.setAdjustViewBounds(true);
                image.setMaxHeight((int) (240 * getResources().getDisplayMetrics().density));
                body.addView(image); Glide.with(this).load(value).centerCrop().into(image);
            } else if (value != null && !key.toLowerCase(Locale.ROOT).contains("publicid")) {
                addDetailText(body, humanize(prefix + key) + ": " + value);
            }
        }
    }

    private void addDetailText(LinearLayout body, String value) {
        TextView line = new TextView(requireContext()); line.setText(value);
        line.setPadding(0, 8, 0, 8); body.addView(line);
    }

    private String humanize(String value) {
        return value.replace('.', ' ').replace('_', ' ').replaceAll("([a-z])([A-Z])", "$1 $2");
    }

    private String adminActionLabel(String status) {
        switch (status) {
            case "APPROVED": case "ACTIVE": case "PUBLISHED": return "Phê duyệt / khôi phục";
            case "REJECTED": return "Từ chối"; case "HIDDEN": return "Ẩn";
            case "STOPPED": return "Dừng Campaign"; case "BLOCKED": return "Khóa tài khoản";
            default: return status;
        }
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
        TextView text = new TextView(requireContext());
        text.setText(getString(R.string.admin_analytics_format,
                value.getPendingModerationTotal(), value.total(Section.USERS),
                value.total(Section.PRODUCTS), value.total(Section.CAMPAIGNS),
                value.total(Section.ORDERS), NumberFormat.getCurrencyInstance(new Locale("vi", "VN"))
                        .format(value.getDeliveredRevenue())));
        text.append("\n\nRESCUE ANALYTICS"
                + "\nCampaign đang hoạt động: " + value.getActiveCampaigns()
                + "\nCampaign CRITICAL: " + value.getCriticalCampaigns()
                + "\nĐiểm mobile đang chia sẻ vị trí mới: " + value.getLiveMobilePoints()
                + "\nTổng lượng đã giải cứu: " + String.format(Locale.getDefault(), "%.2f kg", value.getTotalRescuedQuantity())
                + "\nTỷ lệ hoàn thành: " + String.format(Locale.getDefault(), "%.1f%%", value.getCompletionRate())
                + " (rescued / target của Campaign đã duyệt)");
        container.addView(text);
    }

    private Button button(int label) { Button button = new Button(requireContext()); button.setText(label); return button; }
    private Button buttonText(String label) { Button button = new Button(requireContext()); button.setText(label); return button; }
    private String label(Section value) { return value.name().replace('_', ' '); }
}
