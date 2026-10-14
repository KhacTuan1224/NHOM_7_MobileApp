package com.rescuefarm.ui.admin;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
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
        title.setText(getString(R.string.admin_section_title_format, label(section)));
        reason.setVisibility(section == Section.ORDERS || section == Section.ANALYTICS
                ? View.GONE : View.VISIBLE);
        viewModel = new ViewModelProvider(this, new AdminViewModelFactory(requireContext()))
                .get(AdminViewModel.class);
        viewModel.getState().observe(getViewLifecycleOwner(), this::render);
        view.findViewById(R.id.adminResourceBackButton).setOnClickListener(v -> androidx.navigation.fragment.NavHostFragment.findNavController(this).popBackStack());
        if (section != Section.ANALYTICS) viewModel.observeSection(section).observe(
                getViewLifecycleOwner(), this::renderItems);
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
        } else {
            for (String target : policy.allowedTargets(section, item.getStatus())) {
                Button action = buttonText(target);
                action.setOnClickListener(v -> viewModel.transition(item.getId(), target,
                        reason.getText().toString().trim()));
                row.addView(action);
            }
        }
        container.addView(row);
    }

    private void renderAnalytics(AdminDashboardSnapshot value) {
        if (value == null) return;
        TextView text = new TextView(requireContext());
        text.setText(getString(R.string.admin_analytics_format,
                value.getPendingModerationTotal(), value.total(Section.USERS),
                value.total(Section.PRODUCTS), value.total(Section.CAMPAIGNS),
                value.total(Section.ORDERS), NumberFormat.getCurrencyInstance(new Locale("vi", "VN"))
                        .format(value.getDeliveredRevenue())));
        container.addView(text);
    }

    private Button button(int label) { Button button = new Button(requireContext()); button.setText(label); return button; }
    private Button buttonText(String label) { Button button = new Button(requireContext()); button.setText(label); return button; }
    private String label(Section value) { return value.name().replace('_', ' '); }
}
