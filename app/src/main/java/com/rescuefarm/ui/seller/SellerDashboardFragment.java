package com.rescuefarm.ui.seller;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import com.rescuefarm.R;
import com.rescuefarm.domain.model.Order;
import com.rescuefarm.service.seller.SellerDashboardSnapshot;
import com.rescuefarm.service.seller.SellerRoutePolicy;
import java.text.NumberFormat;
import java.util.List;
import java.util.Arrays;
import java.util.Locale;

public class SellerDashboardFragment extends Fragment {
    private final android.os.Handler timeHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable timeRefresh = new Runnable() {
        @Override public void run() {
            if (getView() != null && viewModel != null && (viewModel.getState().getValue() == null
                    || viewModel.getState().getValue().getStatus() != SellerDashboardState.Status.LOADING)) viewModel.refresh();
            timeHandler.postDelayed(this, 60000L);
        }
    };
    private SellerViewModel viewModel;
    private Spinner periodSpinner;
    private TextView message;
    private ProgressBar progress;
    private LinearLayout stats, analytics, recent;
    private DrawerLayout drawer;
    private final SellerRoutePolicy routes = new SellerRoutePolicy();

    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater,
            @Nullable ViewGroup parent, @Nullable Bundle state) {
        return inflater.inflate(R.layout.fragment_seller_dashboard, parent, false);
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle savedState) {
        message = view.findViewById(R.id.sellerDashboardMessage);
        stats = view.findViewById(R.id.sellerDashboardStats);
        analytics = view.findViewById(R.id.sellerDashboardAnalytics);
        progress = view.findViewById(R.id.sellerDashboardProgress);
        recent = view.findViewById(R.id.sellerRecentOrders);
        drawer = view.findViewById(R.id.sellerDashboardDrawer);
        viewModel = new ViewModelProvider(requireActivity(), new SellerViewModelFactory(requireContext()))
                .get("seller-dashboard:" + (com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser() == null ? ""
                        : com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser().getUid()), SellerViewModel.class);
        periodSpinner = view.findViewById(R.id.sellerStatsPeriodSpinner);
        periodSpinner.setAdapter(new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_dropdown_item,
                getResources().getStringArray(R.array.seller_stats_periods)));
        periodSpinner.setSelection(1);
        periodSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View selected,
                    int position, long id) {
                viewModel.setStatisticsPeriodDays(position == 0 ? 7 : position == 1 ? 30 : position == 2 ? 90 : 0);
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        });
        viewModel.getState().observe(getViewLifecycleOwner(), this::render);
        view.findViewById(R.id.sellerDashboardMenuButton).setOnClickListener(unused ->
                drawer.openDrawer(android.view.Gravity.START));
        view.findViewById(R.id.refreshSellerDashboardButton).setOnClickListener(unused -> viewModel.refresh());
        bind(view, R.id.dashboardProductsButton, "products");
        bind(view, R.id.dashboardBatchesButton, "batches");
        bind(view, R.id.dashboardPromotionsButton, "promotions");
        bind(view, R.id.dashboardCampaignsButton, "campaigns");
        bind(view, R.id.dashboardMobileButton, "mobile");
        bind(view, R.id.dashboardOrdersButton, "orders");
        bind(view, R.id.dashboardShipmentsButton, "shipments");
        bind(view, R.id.dashboardStatisticsButton, "statistics");
        bind(view, R.id.dashboardProfileButton, "profile");
        view.findViewById(R.id.sellerHomeOrdersButton).setOnClickListener(unused ->
                navigate(SellerRoutePolicy.Destination.ORDERS, "orders"));
    }

    private void bind(View root, int id, String key) {
        root.findViewById(id).setOnClickListener(unused -> {
            closeDrawer();
            navigate(routes.destinationFor(key), key);
        });
    }

    private void closeDrawer() {
        if (drawer != null && drawer.isDrawerOpen(android.view.Gravity.START))
            drawer.closeDrawer(android.view.Gravity.START);
    }

    private void navigate(SellerRoutePolicy.Destination destination, String key) {
        if (destination == SellerRoutePolicy.Destination.STATISTICS) {
            Navigation.findNavController(requireView()).navigate(
                    R.id.action_sellerDashboardFragment_to_sellerStatisticsFragment);
            return;
        }
        int action;
        if (destination == SellerRoutePolicy.Destination.PRODUCTS)
            action = R.id.action_sellerDashboardFragment_to_sellerProductListFragment;
        else if (destination == SellerRoutePolicy.Destination.BATCHES) {
            Bundle args = new Bundle(); args.putBoolean("batchSelectionMode", true);
            Navigation.findNavController(requireView()).navigate(
                    R.id.action_sellerDashboardFragment_to_sellerProductListFragment, args);
            return;
        }
        else if (destination == SellerRoutePolicy.Destination.CAMPAIGNS)
            action = R.id.action_sellerDashboardFragment_to_sellerCampaignListFragment;
        else if (destination == SellerRoutePolicy.Destination.ORDERS) {
            Bundle args = new Bundle(); args.putBoolean("deliveryOnly", "shipments".equals(key));
            Navigation.findNavController(requireView()).navigate(
                    R.id.action_sellerDashboardFragment_to_sellerOrderListFragment, args); return;
        } else action = R.id.action_sellerDashboardFragment_to_sellerProfileFragment;
        Navigation.findNavController(requireView()).navigate(action);
    }

    private void render(SellerDashboardState value) {
        progress.setVisibility(value.getStatus() == SellerDashboardState.Status.LOADING
                || value.getStatus() == SellerDashboardState.Status.VERIFYING ? View.VISIBLE : View.GONE);
        message.setText(value.getMessage());
        boolean enabled = value.getStatus() == SellerDashboardState.Status.READY
                || value.getStatus() == SellerDashboardState.Status.EMPTY;
        for (int id : new int[]{R.id.dashboardProductsButton, R.id.dashboardBatchesButton,
                R.id.dashboardPromotionsButton, R.id.dashboardCampaignsButton, R.id.dashboardMobileButton,
                R.id.dashboardOrdersButton, R.id.dashboardShipmentsButton,
                R.id.dashboardStatisticsButton, R.id.dashboardProfileButton, R.id.sellerHomeOrdersButton})
            requireView().findViewById(id).setEnabled(enabled);
        if (value.getSnapshot() == null) {
            stats.removeAllViews(); analytics.removeAllViews(); recent.removeAllViews(); return;
        }
        SellerDashboardSnapshot snapshot = value.getSnapshot();
        NumberFormat currency = NumberFormat.getCurrencyInstance(new Locale("vi", "VN"));
        stats.removeAllViews();
        SellerDashboardCards.addSectionTitle(requireContext(), stats, "Công việc cửa hàng");
        SellerDashboardCards.addGrid(requireContext(), stats, Arrays.asList(
                metric("Đơn đang xử lý", String.valueOf(snapshot.getPendingOrders()), "Tất cả thời gian · chưa giao hoặc hủy"),
                metric("Đơn giao hàng", String.valueOf(snapshot.getDeliveryOrders()), "Đang xử lý · giao tận nơi · tất cả thời gian"),
                metric("Sản phẩm", String.valueOf(snapshot.getProducts()),
                        snapshot.getActiveProducts() + " đang hoạt động"),
                metric("Chiến dịch", String.valueOf(snapshot.getCampaigns()),
                        snapshot.getActiveCampaigns() + " đang hoạt động · "
                                + snapshot.getMobileCampaigns() + " điểm di động")));

        String[] periods = getResources().getStringArray(R.array.seller_stats_periods);
        String period = periods[Math.min(periodSpinner.getSelectedItemPosition(), periods.length - 1)];
        analytics.removeAllViews();
        SellerDashboardCards.addSectionTitle(requireContext(), analytics, "Kết quả kinh doanh và giải cứu");
        SellerDashboardCards.addGrid(requireContext(), analytics, Arrays.asList(
                metric("Đã giải cứu (Campaign kg)", String.format(Locale.getDefault(), "%.2f kg",
                        snapshot.getRescuedQuantity()), "Chỉ Campaign kg đã xác minh"),
                metric("Chiến dịch hoàn thành", String.valueOf(snapshot.getCompletedCampaigns()),
                        "Đã hoàn thành mục tiêu"),
                metric("Tỷ lệ giải cứu", String.format(Locale.getDefault(), "%.1f%%",
                        snapshot.getRescueRate()), "Trung bình tỷ lệ các Campaign đã xác minh"),
                metric("Doanh thu đơn đã giao", currency.format(snapshot.getRevenue()),
                        "Trong kỳ · không gồm phí giao hàng"),
                metric("Số đơn trong kỳ", String.valueOf(snapshot.getOrders()),
                        "Toàn bộ đơn theo ngày tạo trong kỳ")));
        SellerDashboardCards.addFootnote(requireContext(), analytics,
                getString(R.string.seller_stats_period_label, period)
                        + "\nDoanh thu không bao gồm phí giao hàng và không đồng nghĩa với tiền đã đối soát.");
        addRankedCards(analytics, getString(R.string.seller_top_products_title),
                snapshot.getTopProducts(), "Số lượng đã giao");
        addRankedCards(analytics, getString(R.string.seller_top_rescue_reasons_title),
                snapshot.getTopReasons(), "Đã giải cứu · đơn vị ghi ở từng mục");
        recent.removeAllViews();
        if (snapshot.getRecentOrders().isEmpty()) {
            SellerDashboardCards.addFootnote(requireContext(), recent, "Chưa có đơn hàng.");
        }
        for (Order order : snapshot.getRecentOrders()) {
            Button row = new Button(requireContext()); row.setAllCaps(false);
            row.setText(getString(R.string.seller_recent_order_format, order.getOrderCode(),
                    order.getStatus().name(), order.getTotalAmount()));
            row.setOnClickListener(unused -> {
                Bundle args = new Bundle(); args.putString("orderId", order.getId());
                Navigation.findNavController(requireView()).navigate(
                        R.id.action_sellerDashboardFragment_to_orderDetailFragment, args);
            });
            recent.addView(row);
        }
        if (snapshot.hasMoreOrders()) {
            TextView more = new TextView(requireContext()); more.setText(R.string.seller_more_orders_notice);
            recent.addView(more);
        }
    }

    private SellerDashboardCards.Metric metric(String title, String value, String detail) {
        return new SellerDashboardCards.Metric(title, value, detail);
    }

    private void addRankedCards(LinearLayout parent, String title,
            List<SellerDashboardSnapshot.RankedMetric> values, String detail) {
        SellerDashboardCards.addSectionTitle(requireContext(), parent, title);
        if (values.isEmpty()) {
            SellerDashboardCards.addFootnote(requireContext(), parent, "Chưa có dữ liệu trong kỳ.");
            return;
        }
        java.util.ArrayList<SellerDashboardCards.Metric> cards = new java.util.ArrayList<>();
        for (SellerDashboardSnapshot.RankedMetric value : values) {
            cards.add(metric(value.getLabel(), String.format(Locale.getDefault(), "%.2f",
                    value.getQuantity()), detail));
        }
        SellerDashboardCards.addGrid(requireContext(), parent, cards);
    }
    @Override public void onResume() {
        super.onResume(); if (viewModel != null) viewModel.refresh(); timeHandler.removeCallbacks(timeRefresh); timeHandler.postDelayed(timeRefresh, 60000L);
    }
    @Override public void onPause() {
        timeHandler.removeCallbacks(timeRefresh); super.onPause();
    }
}
