package com.rescuefarm.ui.admin;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.drawerlayout.widget.DrawerLayout;
import android.widget.LinearLayout;
import com.rescuefarm.R;
import com.rescuefarm.data.repository.AdminRepository.Section;
import com.rescuefarm.data.repository.admin.AdminDashboardSnapshot;
import java.text.NumberFormat;
import java.util.Arrays;
import java.util.Locale;

public final class AdminDashboardFragment extends Fragment {
    private AdminViewModel viewModel;
    private ProgressBar progress;
    private TextView message;
    private LinearLayout stats;
    private DrawerLayout drawer;

    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater,
            @Nullable ViewGroup container, @Nullable Bundle state) {
        return inflater.inflate(R.layout.fragment_admin_dashboard, container, false);
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        progress = view.findViewById(R.id.adminDashboardProgress);
        message = view.findViewById(R.id.adminDashboardMessage);
        stats = view.findViewById(R.id.adminDashboardStats);
        drawer = view.findViewById(R.id.adminDashboardDrawer);
        viewModel = new ViewModelProvider(this, new AdminViewModelFactory(requireContext()))
                .get(AdminViewModel.class);
        viewModel.getState().observe(getViewLifecycleOwner(), this::render);
        view.findViewById(R.id.adminDashboardBackButton).setOnClickListener(v -> androidx.navigation.fragment.NavHostFragment.findNavController(this).popBackStack());
        view.findViewById(R.id.adminDashboardMenuButton).setOnClickListener(v -> drawer.openDrawer(android.view.Gravity.START));
        view.findViewById(R.id.refreshAdminDashboardButton).setOnClickListener(v -> viewModel.refreshDashboard());
        bind(view, R.id.adminSellersButton, Section.SELLER_APPLICATIONS);
        bind(view, R.id.adminUsersButton, Section.USERS);
        bind(view, R.id.adminProductsButton, Section.PRODUCTS);
        bind(view, R.id.adminCampaignsButton, Section.CAMPAIGNS);
        bind(view, R.id.adminCategoriesButton, Section.CATEGORIES);
        bind(view, R.id.adminBannersButton, Section.BANNERS);
        bind(view, R.id.adminReviewsButton, Section.REVIEWS);
        bind(view, R.id.adminReportsButton, Section.REPORTS);
        bind(view, R.id.adminOrdersButton, Section.ORDERS);
        bind(view, R.id.adminAnalyticsButton, Section.ANALYTICS);
        bind(view, R.id.adminShippingSettingsButton, Section.SHIPPING_SETTINGS);
        viewModel.refreshDashboard();
    }

    private void bind(View root, int id, Section section) {
        root.findViewById(id).setOnClickListener(view -> {
            if (drawer != null && drawer.isDrawerOpen(android.view.Gravity.START)) drawer.closeDrawer(android.view.Gravity.START);
            Bundle args = new Bundle(); args.putString("section", section.name());
            Navigation.findNavController(view).navigate(
                    R.id.action_adminDashboardFragment_to_adminResourceFragment, args);
        });
    }

    private void render(AdminScreenState state) {
        progress.setVisibility(state.getStatus() == AdminScreenState.Status.LOADING ? View.VISIBLE : View.GONE);
        message.setText(state.getMessage());
        AdminDashboardSnapshot value = state.getDashboard();
        if (value == null) { stats.removeAllViews(); return; }
        stats.removeAllViews();
        NumberFormat currency = NumberFormat.getCurrencyInstance(new Locale("vi", "VN"));
        AdminDashboardCards.addSectionTitle(requireContext(), stats, "TỔNG QUAN HỆ THỐNG");
        AdminDashboardCards.addGrid(requireContext(), stats, Arrays.asList(
                metric("Users", String.valueOf(value.total(Section.USERS)), "Tổng tài khoản"),
                metric("Seller applications", String.valueOf(value.total(Section.SELLER_APPLICATIONS)),
                        value.getPendingSellers() + " chờ duyệt"),
                metric("Products", String.valueOf(value.total(Section.PRODUCTS)), "Tổng sản phẩm"),
                metric("Campaigns", String.valueOf(value.total(Section.CAMPAIGNS)),
                        value.getPendingCampaigns() + " chờ duyệt"),
                metric("Orders", String.valueOf(value.total(Section.ORDERS)), "Tổng đơn hàng"),
                metric("Reports đang mở", String.valueOf(value.getOpenReports()), "Pending + In-review"),
                metric("Doanh thu DELIVERED", currency.format(value.getDeliveredRevenue()),
                        "All-time, gồm phí giao hàng")));

        AdminDashboardCards.addSectionTitle(requireContext(), stats, "RESCUE ANALYTICS");
        AdminDashboardCards.addGrid(requireContext(), stats, Arrays.asList(
                metric("Campaign đang hoạt động", String.valueOf(value.getActiveCampaigns()), "Status ACTIVE"),
                metric("Campaign CRITICAL", String.valueOf(value.getCriticalCampaigns()), "Trong Campaign ACTIVE"),
                metric("Điểm mobile đang chia sẻ vị trí mới", String.valueOf(value.getLiveMobilePoints()),
                        "Vị trí được cập nhật trong 10 phút"),
                metric("Tổng lượng đã giải cứu", String.format(Locale.getDefault(), "%.2f kg",
                        value.getTotalRescuedQuantity()), "Số lượng đã rescued"),
                metric("Tỷ lệ hoàn thành", String.format(Locale.getDefault(), "%.1f%%",
                        value.getCompletionRate()),
                        "rescued / target của Campaign ACTIVE, COMPLETED, EXPIRED, STOPPED đã duyệt")));
        AdminDashboardCards.addFootnote(requireContext(), stats,
                "Doanh thu là tổng all-time của đơn DELIVERED, gồm phí giao hàng."
                        + (value.getCalculatedAt().isEmpty() ? "" : "\nCập nhật: " + value.getCalculatedAt()));
    }

    private AdminDashboardCards.Metric metric(String title, String value, String detail) {
        return new AdminDashboardCards.Metric(title, value, detail);
    }
}
