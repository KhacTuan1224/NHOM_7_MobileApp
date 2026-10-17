package com.rescuefarm.ui.engagement;

import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.annotation.*;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import com.rescuefarm.service.backend.RenderBackendClient;
import com.rescuefarm.ui.common.NavigationAwareNestedScrollView;
import org.json.*;

/** Public projection of the signed-in reporter's own reports; internal resolution notes are never loaded. */
public final class ReportResultFragment extends Fragment {
    private LinearLayout content;
    private TextView status;
    private Button more;
    private String cursor = "", reportId = "";
    private boolean loading;
    private String ownerUid = "";
    private final com.google.firebase.auth.FirebaseAuth.AuthStateListener authListener = auth -> checkOwner();
    private boolean checkOwner() {
        com.google.firebase.auth.FirebaseUser user = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
        if (!ownerUid.equals(user == null ? "" : user.getUid())) {
            if (content != null) { content.removeAllViews(); addText("Tài khoản đã thay đổi. Hãy mở lại báo cáo từ tài khoản hiện tại.", 16); }
            return false;
        }
        return true;
    }
    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup parent, @Nullable Bundle state) {
        NavigationAwareNestedScrollView scroll = new NavigationAwareNestedScrollView(requireContext());
        content = new LinearLayout(requireContext()); content.setOrientation(LinearLayout.VERTICAL);
        int padding = Math.round(20 * getResources().getDisplayMetrics().density); content.setPadding(padding, padding, padding, padding);
        scroll.addView(content); return scroll;
    }
    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        com.google.firebase.auth.FirebaseUser user = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
        ownerUid = user == null ? "" : user.getUid();
        reportId = getArguments() == null ? "" : getArguments().getString("reportId", ""); cursor = ""; loading = false;
        addText(reportId.isEmpty() ? "Báo cáo của tôi" : "Kết quả báo cáo", 26);
        Button back = button(); back.setText("Quay lại"); back.setOnClickListener(v -> NavHostFragment.findNavController(this).popBackStack()); content.addView(back);
        status = addText("", 14); more = button(); more.setText("Tải lại");
        more.setOnClickListener(v -> load()); content.addView(more); load();
    }
    private Button button() {
        com.google.android.material.button.MaterialButton button = new com.google.android.material.button.MaterialButton(requireContext());
        button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.TRANSPARENT));
        button.setStrokeColor(android.content.res.ColorStateList.valueOf(requireContext().getColor(com.rescuefarm.R.color.rescue_primary)));
        button.setStrokeWidth(Math.max(1, Math.round(getResources().getDisplayMetrics().density)));
        button.setTextColor(requireContext().getColor(com.rescuefarm.R.color.rescue_on_surface));
        button.setCornerRadius(Math.round(14 * getResources().getDisplayMetrics().density)); button.setAllCaps(false); return button;
    }
    private TextView addText(String value, int size) {
        TextView text = new TextView(requireContext()); text.setText(value); text.setTextSize(size); text.setPadding(0, 12, 0, 12);
        text.setTextColor(requireContext().getColor(com.rescuefarm.R.color.rescue_on_surface));
        content.addView(text); return text;
    }
    private void load() {
        if (loading) return; loading = true; status.setText("Đang tải báo cáo…"); more.setEnabled(false);
        final LinearLayout boundContent = content;
        try {
            JSONObject data = new JSONObject().put("reportId", reportId).put("cursor", cursor);
            RenderBackendClient.post("/reports/mine", data, new RenderBackendClient.Callback() {
                public void onSuccess(JSONObject json) {
                    if (!isAdded() || getView() == null || content != boundContent || !checkOwner()) return;
                    loading = false; more.setEnabled(true); JSONArray reports = json.optJSONArray("reports");
                    int count = reports == null ? 0 : reports.length(); status.setText(count == 0 ? "Chưa có báo cáo nào." : "");
                    for (int i = 0; i < count; i++) {
                        JSONObject report = reports.optJSONObject(i); if (report == null) continue;
                        addText("Báo cáo " + report.optString("id"), 18);
                        addText("Nội dung báo cáo: " + report.optString("reason") + "\n" + report.optString("description"), 14);
                        if (!report.optString("parentId").isEmpty()) addText("Bình luận " + report.optString("targetId") + " thuộc "
                                + ("CAMPAIGN".equals(report.optString("parentType")) ? "chiến dịch " : "bài viết cũ ") + report.optString("parentId"), 14);
                        addText(report.optString("publicResult"), 16);
                    }
                    cursor = json.optString("nextCursor"); more.setText("Xem thêm"); more.setVisibility(cursor.isEmpty() ? View.GONE : View.VISIBLE);
                }
                public void onError(String message) {
                    if (!isAdded() || getView() == null || content != boundContent || !checkOwner()) return;
                    loading = false; status.setText(message); more.setEnabled(true); more.setText("Thử lại"); more.setVisibility(View.VISIBLE);
                }
            });
        } catch (JSONException error) { loading = false; status.setText("Không tạo được yêu cầu."); more.setEnabled(true); }
    }
    @Override public void onStart() { super.onStart(); com.google.firebase.auth.FirebaseAuth.getInstance().addAuthStateListener(authListener); }
    @Override public void onStop() { com.google.firebase.auth.FirebaseAuth.getInstance().removeAuthStateListener(authListener); super.onStop(); }
    @Override public void onDestroyView() { content = null; status = null; more = null; super.onDestroyView(); }
}
