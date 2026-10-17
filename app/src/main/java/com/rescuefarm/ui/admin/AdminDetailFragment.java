package com.rescuefarm.ui.admin;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputLayout;
import com.rescuefarm.R;
import com.rescuefarm.data.repository.AdminRepository;
import com.rescuefarm.data.repository.AdminRepository.Section;
import com.rescuefarm.data.repository.admin.AdminListItem;
import com.rescuefarm.service.admin.AdminTransitionPolicy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

/** Full-page detail and moderation actions for every Admin resource section. */
public final class AdminDetailFragment extends Fragment {
    private final AdminTransitionPolicy policy = new AdminTransitionPolicy();
    private AdminViewModel viewModel;
    private Section section;
    private AdminListItem item;
    private LinearLayout content;
    private ProgressBar progress;
    private TextView message;
    private MaterialButton retry;
    private EditText reason;
    private final List<View> actionViews = new ArrayList<>();

    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater,
            @Nullable ViewGroup parent, @Nullable Bundle state) {
        return inflater.inflate(R.layout.fragment_admin_detail, parent, false);
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        Bundle args = getArguments() == null ? Bundle.EMPTY : getArguments();
        section = Section.from(args.getString("section"));
        item = new AdminListItem(args.getString("itemId"), args.getString("itemTitle"),
                args.getString("itemSubtitle"), args.getString("itemStatus"),
                args.getBoolean("itemActive"), args.getString("itemImageUrl"));
        content = view.findViewById(R.id.adminDetailContent);
        progress = view.findViewById(R.id.adminDetailProgress);
        message = view.findViewById(R.id.adminDetailMessage);
        retry = view.findViewById(R.id.adminDetailRetryButton);
        TextView title = view.findViewById(R.id.adminDetailTitle);
        TextView subtitle = view.findViewById(R.id.adminDetailSubtitle);
        title.setText(item.getTitle().isEmpty() ? "Chi tiết quản trị" : item.getTitle());
        subtitle.setText(item.getSubtitle().isEmpty() ? item.getId() : item.getSubtitle());
        view.findViewById(R.id.adminDetailBackButton).setOnClickListener(v ->
                androidx.navigation.fragment.NavHostFragment.findNavController(this).popBackStack());
        retry.setOnClickListener(v -> loadDetail());
        viewModel = new ViewModelProvider(this, new AdminViewModelFactory(requireContext()))
                .get(AdminViewModel.class);
        loadDetail();
    }

    private void loadDetail() {
        progress.setVisibility(View.VISIBLE);
        retry.setVisibility(View.GONE);
        message.setText("Đang tải đầy đủ hồ sơ…");
        content.removeAllViews();
        viewModel.loadDetail(section, item.getId(), new AdminRepository.DetailCallback() {
            @Override public void onSuccess(Map<String, Object> detail) {
                if (!isAdded()) return;
                progress.setVisibility(View.GONE);
                message.setText("");
                renderDetail(detail == null ? Collections.emptyMap() : detail);
            }
            @Override public void onError(AdminRepository.ErrorCode code, String error) {
                if (!isAdded()) return;
                progress.setVisibility(View.GONE);
                message.setText(error == null ? "Không tải được chi tiết." : error);
                retry.setVisibility(View.VISIBLE);
            }
        });
    }

    private void renderDetail(Map<String, Object> detail) {
        new AdminDetailRenderer(requireContext()).render(content, item, detail);
        if (section == Section.ORDERS) addOrderWarnings(content, detail);
        if (section == Section.REPORTS) addReportActions(detail);
        else if (section != Section.ORDERS && section != Section.CATEGORIES
                && section != Section.BANNERS && section != Section.ANALYTICS
                && section != Section.SHIPPING_SETTINGS) addModerationActions(detail);
    }

    private void addModerationActions(Map<String, Object> detail) {
        addActionHeading("Thao tác quản trị");
        reason = reasonEditor();
        for (String target : allowedTargets(detail)) {
            addActionButton(actionLabel(target, detail), () -> {
                String note = reason.getText().toString().trim();
                try { policy.validate(section, item.getStatus(), target, note); }
                catch (IllegalArgumentException | IllegalStateException error) {
                    reason.setError("Cần lý do hợp lệ hoặc trạng thái đã thay đổi.");
                    return;
                }
                confirm("Xác nhận thao tác", "Bạn có chắc muốn " + actionLabel(target, detail) + "?",
                        () -> viewModel.transition(section, item.getId(), target, note, actionCallback()));
            });
        }
    }

    private void addReportActions(Map<String, Object> detail) {
        addActionHeading("Nội dung và xử lý report");
        addActionButton("Mở nội dung bị báo cáo", () -> openReportedTarget(detail));
        reason = reasonEditor();
        String status = String.valueOf(detail.get("status"));
        String type = String.valueOf(detail.get("targetType"));
        if ("PENDING".equals(status)) addReportButton(detail,
                AdminRepository.ReportAction.MARK_IN_REVIEW, "Bắt đầu xử lý");
        if (supportsHide(type, detail)) addReportButton(detail,
                AdminRepository.ReportAction.HIDE_TARGET, "Ẩn nội dung và giải quyết");
        if (hasTargetOwner(type, detail)) addReportButton(detail,
                AdminRepository.ReportAction.BLOCK_USER, "Khóa người tạo và giải quyết");
        addReportButton(detail, AdminRepository.ReportAction.DISMISS, "Bác report");
    }

    private void addReportButton(Map<String, Object> detail, AdminRepository.ReportAction action,
            String label) {
        addActionButton(label, () -> {
            String note = reason.getText().toString().trim();
            if (action != AdminRepository.ReportAction.MARK_IN_REVIEW && note.length() < 3) {
                reason.setError("Nhập lý do xử lý tối thiểu 3 ký tự.");
                return;
            }
            confirm("Xác nhận xử lý report", label + "? Thao tác sẽ được ghi audit.",
                    () -> viewModel.resolveReport(item.getId(), action, note, actionCallback()));
        });
    }

    private AdminRepository.ActionCallback actionCallback() {
        return new AdminRepository.ActionCallback() {
            @Override public void onSuccess() {
                if (!isAdded()) return;
                Toast.makeText(requireContext(), "Đã cập nhật và ghi audit.", Toast.LENGTH_SHORT).show();
                androidx.navigation.fragment.NavHostFragment.findNavController(AdminDetailFragment.this)
                        .popBackStack();
            }
            @Override public void onError(AdminRepository.ErrorCode code, String error) {
                if (!isAdded()) return;
                progress.setVisibility(View.GONE);
                for (View action : actionViews) action.setEnabled(true);
                Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show();
            }
        };
    }

    private void confirm(String title, String prompt, Runnable action) {
        new AlertDialog.Builder(requireContext()).setTitle(title).setMessage(prompt)
                .setNegativeButton("Quay lại", null)
                .setPositiveButton("Xác nhận", (dialog, which) -> {
                    progress.setVisibility(View.VISIBLE);
                    for (View button : actionViews) button.setEnabled(false);
                    action.run();
                }).show();
    }

    private List<String> allowedTargets(Map<String, Object> detail) {
        if (section == Section.PRODUCTS && "HIDDEN".equals(item.getStatus())) {
            String previous = detail.get("moderationPreviousStatus") == null ? "INACTIVE"
                    : String.valueOf(detail.get("moderationPreviousStatus"));
            if (!Arrays.asList("ACTIVE", "INACTIVE", "SOLD_OUT").contains(previous)) previous = "INACTIVE";
            return Collections.singletonList(previous);
        }
        return policy.allowedTargets(section, item.getStatus());
    }

    private String actionLabel(String target, Map<String, Object> detail) {
        if (section == Section.PRODUCTS && "HIDDEN".equals(item.getStatus())
                && Arrays.asList("ACTIVE", "INACTIVE", "SOLD_OUT").contains(target))
            return "Khôi phục sản phẩm về " + AdminDetailFormatter.status(target);
        switch (target) {
            case "APPROVED": case "ACTIVE": case "PUBLISHED": return "Phê duyệt / khôi phục";
            case "REJECTED": return "Từ chối";
            case "HIDDEN": return "Ẩn";
            case "STOPPED": return "Dừng chiến dịch";
            case "BLOCKED": return "Khóa tài khoản";
            default: return AdminDetailFormatter.status(target);
        }
    }

    private boolean supportsHide(String type, Map<String, Object> detail) {
        Object raw = detail.get("target");
        if (!(raw instanceof Map<?, ?>)) return false;
        Object status = ((Map<?, ?>) raw).get("status");
        String current = status == null ? "" : String.valueOf(status);
        return ("PRODUCT".equals(type) && Arrays.asList("ACTIVE", "INACTIVE", "SOLD_OUT").contains(current))
                || ("POST".equals(type) && "PUBLISHED".equals(current))
                || ("CAMPAIGN".equals(type) && "ACTIVE".equals(current))
                || ("REVIEW".equals(type) && "PUBLISHED".equals(current))
                || ("COMMENT".equals(type) && Arrays.asList("ACTIVE", "EDITED").contains(current));
    }

    private boolean hasTargetOwner(String type, Map<String, Object> detail) {
        if ("USER".equals(type)) return true;
        Object raw = detail.get("target");
        if (!(raw instanceof Map<?, ?>)) return false;
        Map<?, ?> target = (Map<?, ?>) raw;
        return target.containsKey("sellerId") || target.containsKey("customerId") || target.containsKey("userId");
    }

    private void openReportedTarget(Map<String, Object> detail) {
        String type = String.valueOf(detail.get("targetType"));
        String targetId = String.valueOf(detail.get("targetId"));
        Object raw = detail.get("target");
        Map<?, ?> target = raw instanceof Map<?, ?> ? (Map<?, ?>) raw : Collections.emptyMap();
        Bundle args = new Bundle();
        int destination = 0;
        if ("PRODUCT".equals(type)) { destination = R.id.productDetailFragment; args.putString("productId", targetId); }
        else if ("CAMPAIGN".equals(type)) { destination = R.id.campaignDetailFragment; args.putString("campaignId", targetId); }
        else if ("POST".equals(type)) { destination = R.id.postDetailFragment; args.putString("postId", targetId); }
        else if ("COMMENT".equals(type)) {
            String parentId = String.valueOf(target.get("parentId"));
            if ("CAMPAIGN".equals(target.get("parentType"))) {
                destination = R.id.campaignDetailFragment; args.putString("campaignId", parentId);
            } else if ("POST".equals(target.get("parentType"))) {
                destination = R.id.postDetailFragment; args.putString("postId", parentId);
            }
            args.putString("commentId", String.valueOf(target.get("id")));
        } else if ("REVIEW".equals(type)) {
            destination = R.id.reviewFragment; args.putString("productId", String.valueOf(target.get("productId")));
        }
        if (destination == 0) {
            Toast.makeText(requireContext(), "Loại đối tượng này chỉ xem được trong chi tiết report.", Toast.LENGTH_SHORT).show();
            return;
        }
        Navigation.findNavController(requireView()).navigate(destination, args);
    }

    private void addOrderWarnings(LinearLayout body, Map<String, Object> detail) {
        List<String> warnings = new ArrayList<>();
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
        if (!warnings.isEmpty()) new AdminDetailRenderer(requireContext()).notice(body, warnings);
    }

    private Date asDate(Object value) {
        if (value instanceof Date) return (Date) value;
        if (value instanceof com.google.firebase.Timestamp) return ((com.google.firebase.Timestamp) value).toDate();
        return null;
    }

    private EditText reasonEditor() {
        TextInputLayout box = new TextInputLayout(requireContext(), null,
                com.google.android.material.R.attr.textInputOutlinedStyle);
        box.setHint(section == Section.REPORTS ? "Ghi chú xử lý nội bộ (không gửi người báo cáo)"
                : "Lý do kiểm duyệt (hiển thị cho chủ nội dung)");
        EditText input = new EditText(requireContext());
        input.setMinLines(2); input.setMaxLines(4);
        input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(500)});
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        box.addView(input);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = Math.round(12 * getResources().getDisplayMetrics().density);
        content.addView(box, params);
        return input;
    }

    private void addActionHeading(String text) {
        TextView heading = new TextView(requireContext());
        heading.setText(text); heading.setTextSize(18); heading.setTypeface(null, android.graphics.Typeface.BOLD);
        heading.setTextColor(requireContext().getColor(R.color.rescue_primary));
        heading.setPadding(0, Math.round(12 * getResources().getDisplayMetrics().density), 0,
                Math.round(8 * getResources().getDisplayMetrics().density));
        content.addView(heading);
    }

    private void addActionButton(String label, Runnable action) {
        MaterialButton button = new MaterialButton(requireContext());
        AdminUiStyles.outlinedButton(requireContext(), button);
        button.setText(label);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = Math.round(8 * getResources().getDisplayMetrics().density);
        content.addView(button, params);
        button.setOnClickListener(v -> action.run());
        actionViews.add(button);
    }
}
