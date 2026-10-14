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
import com.rescuefarm.R;
import com.rescuefarm.data.repository.AdminRepository.Section;
import com.rescuefarm.data.repository.admin.AdminDashboardSnapshot;
import java.text.NumberFormat;
import java.util.Locale;

public final class AdminDashboardFragment extends Fragment {
    private AdminViewModel viewModel;
    private ProgressBar progress;
    private TextView message, stats;

    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater,
            @Nullable ViewGroup container, @Nullable Bundle state) {
        return inflater.inflate(R.layout.fragment_admin_dashboard, container, false);
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        progress = view.findViewById(R.id.adminDashboardProgress);
        message = view.findViewById(R.id.adminDashboardMessage);
        stats = view.findViewById(R.id.adminDashboardStats);
        viewModel = new ViewModelProvider(this, new AdminViewModelFactory(requireContext()))
                .get(AdminViewModel.class);
        viewModel.getState().observe(getViewLifecycleOwner(), this::render);
        view.findViewById(R.id.refreshAdminDashboardButton).setOnClickListener(v -> viewModel.refreshDashboard());
        bind(view, R.id.adminSellersButton, Section.SELLER_APPLICATIONS);
        bind(view, R.id.adminUsersButton, Section.USERS);
        bind(view, R.id.adminPostsButton, Section.POSTS);
        bind(view, R.id.adminProductsButton, Section.PRODUCTS);
        bind(view, R.id.adminCampaignsButton, Section.CAMPAIGNS);
        bind(view, R.id.adminCategoriesButton, Section.CATEGORIES);
        bind(view, R.id.adminBannersButton, Section.BANNERS);
        bind(view, R.id.adminReviewsButton, Section.REVIEWS);
        bind(view, R.id.adminReportsButton, Section.REPORTS);
        bind(view, R.id.adminOrdersButton, Section.ORDERS);
        bind(view, R.id.adminAnalyticsButton, Section.ANALYTICS);
        viewModel.refreshDashboard();
    }

    private void bind(View root, int id, Section section) {
        root.findViewById(id).setOnClickListener(view -> {
            Bundle args = new Bundle(); args.putString("section", section.name());
            Navigation.findNavController(view).navigate(
                    R.id.action_adminDashboardFragment_to_adminResourceFragment, args);
        });
    }

    private void render(AdminScreenState state) {
        progress.setVisibility(state.getStatus() == AdminScreenState.Status.LOADING ? View.VISIBLE : View.GONE);
        message.setText(state.getMessage());
        AdminDashboardSnapshot value = state.getDashboard();
        if (value == null) { stats.setText(""); return; }
        stats.setText(getString(R.string.admin_dashboard_stats_format,
                value.total(Section.USERS), value.total(Section.SELLER_APPLICATIONS),
                value.getPendingSellers(), value.total(Section.PRODUCTS),
                value.total(Section.CAMPAIGNS), value.getPendingCampaigns(),
                value.total(Section.POSTS), value.getPendingPosts(),
                value.total(Section.ORDERS), value.getOpenReports(),
                NumberFormat.getCurrencyInstance(new Locale("vi", "VN"))
                        .format(value.getDeliveredRevenue())));
        stats.append("\n\nRESCUE ANALYTICS"
                + "\nCampaign đang hoạt động: " + value.getActiveCampaigns()
                + "\nCampaign CRITICAL: " + value.getCriticalCampaigns()
                + "\nĐiểm mobile đang chia sẻ vị trí mới: " + value.getLiveMobilePoints()
                + "\nTổng lượng đã giải cứu: " + String.format(Locale.getDefault(), "%.2f kg", value.getTotalRescuedQuantity())
                + "\nTỷ lệ hoàn thành: " + String.format(Locale.getDefault(), "%.1f%%", value.getCompletionRate())
                + " (rescued / target của Campaign đã duyệt)");
    }
}
