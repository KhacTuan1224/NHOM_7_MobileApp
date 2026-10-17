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
    private final android.os.Handler timeHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable timeRefresh = new Runnable() {
        @Override public void run() {
            if (getView() != null && viewModel != null
                    && (viewModel.getState().getValue() == null || viewModel.getState().getValue().getStatus() != AdminScreenState.Status.LOADING))
                viewModel.refreshDashboard();
            timeHandler.postDelayed(this, 60000L);
        }
    };
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
        bind(view, R.id.adminHomeSellersButton, Section.SELLER_APPLICATIONS);
        bind(view, R.id.adminHomeCampaignsButton, Section.CAMPAIGNS);
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
        AdminDashboardCards.addSectionTitle(requireContext(), stats, "Cần xử lý");
        AdminDashboardCards.addGrid(requireContext(), stats, Arrays.asList(
                metric("Người bán chờ duyệt", String.valueOf(value.getPendingSellers()), "Hồ sơ cần xét duyệt"),
                metric("Chiến dịch chờ duyệt", String.valueOf(value.getPendingCampaigns()), "Nội dung cần xét duyệt"),
                metric("Báo cáo đang mở", String.valueOf(value.getOpenReports()), "Chờ xử lý hoặc đang xem xét")));

        AdminDashboardCards.addSectionTitle(requireContext(), stats, "Hoạt động hệ thống");
        AdminDashboardCards.addGrid(requireContext(), stats, Arrays.asList(
                metric("Người dùng", String.valueOf(value.total(Section.USERS)), "Tổng tài khoản"),
                metric("Sản phẩm", String.valueOf(value.total(Section.PRODUCTS)), "Tổng sản phẩm"),
                metric("Chiến dịch", String.valueOf(value.total(Section.CAMPAIGNS)), "Tổng chiến dịch"),
                metric("Đơn hàng", String.valueOf(value.total(Section.ORDERS)), "Tổng đơn hàng"),
                metric("Doanh thu đơn đã giao", currency.format(value.getDeliveredRevenue()),
                        "Toàn thời gian · gồm phí giao hàng")));

        AdminDashboardCards.addSectionTitle(requireContext(), stats, "Hiệu quả giải cứu");
        AdminDashboardCards.addGrid(requireContext(), stats, Arrays.asList(
                metric("Chiến dịch đang hoạt động", String.valueOf(value.getActiveCampaigns()), "Đang diễn ra"),
                metric("Cần giải cứu gấp", String.valueOf(value.getCriticalCampaigns()), "Trong chiến dịch đang hoạt động"),
                metric("Điểm di động trực tuyến", String.valueOf(value.getLiveMobilePoints()),
                        "Vị trí được cập nhật trong 10 phút"),
                metric("Đã giải cứu (Campaign kg)", String.format(Locale.getDefault(), "%.2f kg",
                        value.getTotalRescuedQuantity()), "Đã giải cứu (Campaign kg)"),
                metric("Tỷ lệ hoàn thành (Campaign kg)", String.format(Locale.getDefault(), "%.1f%%",
                        value.getCompletionRate()),
                        "Đã giải cứu / mục tiêu của các chiến dịch đã kích hoạt")));
        AdminDashboardCards.addFootnote(requireContext(), stats,
                "Doanh thu tính trên đơn đã giao trong toàn bộ thời gian, gồm phí giao hàng."
                        + (value.getCalculatedAt().isEmpty() ? "" : "\nCập nhật: " + value.getCalculatedAt()));
    }

    private AdminDashboardCards.Metric metric(String title, String value, String detail) {
        return new AdminDashboardCards.Metric(title, value, detail);
    }
    @Override public void onResume() {
        super.onResume(); if (viewModel != null) viewModel.refreshDashboard(); timeHandler.removeCallbacks(timeRefresh); timeHandler.postDelayed(timeRefresh, 60000L);
    }
    @Override public void onPause() {
        timeHandler.removeCallbacks(timeRefresh); super.onPause();
    }
}
