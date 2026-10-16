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
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import com.rescuefarm.R;
import com.rescuefarm.domain.model.Order;
import com.rescuefarm.service.seller.SellerDashboardSnapshot;
import com.rescuefarm.service.seller.SellerRoutePolicy;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

public class SellerDashboardFragment extends Fragment {
    private SellerViewModel viewModel;
    private Spinner periodSpinner;
    private TextView message, stats, analytics;
    private ProgressBar progress;
    private LinearLayout recent;
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
        viewModel = new ViewModelProvider(requireActivity(), new SellerViewModelFactory(requireContext()))
                .get(SellerViewModel.class);
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
        view.findViewById(R.id.refreshSellerDashboardButton).setOnClickListener(unused -> viewModel.refresh());
        bind(view, R.id.dashboardProductsButton, "products");
        bind(view, R.id.dashboardBatchesButton, "batches");
        bind(view, R.id.dashboardPromotionsButton, "promotions");
        bind(view, R.id.dashboardCampaignsButton, "campaigns");
        bind(view, R.id.dashboardMobileButton, "mobile");
        bind(view, R.id.dashboardOrdersButton, "orders");
        bind(view, R.id.dashboardShipmentsButton, "shipments");
        view.findViewById(R.id.dashboardStatisticsButton).setOnClickListener(unused ->
                navigate(routes.destinationFor("statistics"), "statistics"));
        viewModel.refresh();
    }

    private void bind(View root, int id, String key) {
        root.findViewById(id).setOnClickListener(unused -> navigate(routes.destinationFor(key), key));
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
                R.id.dashboardStatisticsButton}) requireView().findViewById(id).setEnabled(enabled);
        if (value.getSnapshot() == null) { stats.setText(""); analytics.setText(""); recent.removeAllViews(); return; }
        SellerDashboardSnapshot snapshot = value.getSnapshot();
        NumberFormat currency = NumberFormat.getCurrencyInstance(new Locale("vi", "VN"));
        stats.setText(getString(R.string.seller_dashboard_stats_format, snapshot.getProducts(),
                snapshot.getActiveProducts(), snapshot.getCampaigns(), snapshot.getActiveCampaigns(),
                snapshot.getMobileCampaigns(), snapshot.getOrders(), snapshot.getPendingOrders(), snapshot.getDeliveryOrders(),
                snapshot.getPromotions(), currency.format(snapshot.getRevenue()), snapshot.getRescuedQuantity()));
        String[] periods = getResources().getStringArray(R.array.seller_stats_periods);
        String period = periods[Math.min(periodSpinner.getSelectedItemPosition(), periods.length - 1)];
        StringBuilder details = new StringBuilder(getString(R.string.seller_stats_period_label, period));
        details.append("\n\n").append(getString(R.string.seller_analytics_summary_format,
                snapshot.getRescuedQuantity(), snapshot.getCompletedCampaigns(), snapshot.getRescueRate(),
                currency.format(snapshot.getRevenue()), snapshot.getOrders()));
        appendMetrics(details, getString(R.string.seller_top_products_title), snapshot.getTopProducts());
        appendMetrics(details, getString(R.string.seller_top_rescue_reasons_title), snapshot.getTopReasons());
        analytics.setText(details.toString());
        recent.removeAllViews();
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

    private void appendMetrics(StringBuilder output, String title,
            List<SellerDashboardSnapshot.RankedMetric> values) {
        output.append("\n\n").append(title);
        if (values.isEmpty()) { output.append("\n—"); return; }
        for (SellerDashboardSnapshot.RankedMetric metric : values)
            output.append("\n• ").append(metric.getLabel()).append(": ")
                    .append(String.format(Locale.getDefault(), "%.2f", metric.getQuantity()));
    }
}
