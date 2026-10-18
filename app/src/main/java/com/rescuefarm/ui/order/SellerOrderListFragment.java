package com.rescuefarm.ui.order;
import com.rescuefarm.ui.seller.SellerUiStyles;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import com.google.firebase.auth.FirebaseAuth;
import com.rescuefarm.R;
import com.rescuefarm.domain.enums.FulfillmentType;
import com.rescuefarm.domain.model.Order;
import java.util.ArrayList;
import java.util.List;

public class SellerOrderListFragment extends Fragment {
    private final com.rescuefarm.ui.common.ListPagination pagination = new com.rescuefarm.ui.common.ListPagination();
    private List<Order> currentOrders;
    private LinearLayout container;
    private TextView empty, title;
    private OrderViewModel viewModel;
    private String sellerId = "";
    private boolean deliveryOnly;

    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater,
            @Nullable ViewGroup parent, @Nullable Bundle state) {
        return inflater.inflate(R.layout.fragment_seller_order_list, parent, false);
    }
    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        container = view.findViewById(R.id.sellerOrderContainer);
        empty = view.findViewById(R.id.sellerOrderEmpty);
        title = view.findViewById(R.id.sellerOrderTitle);
        Bundle args = getArguments(); deliveryOnly = args != null && args.getBoolean("deliveryOnly", false);
        SellerUiStyles.styleActions(view, deliveryOnly ? SellerUiStyles.Section.SHIPMENTS : SellerUiStyles.Section.ORDERS);
        title.setText(deliveryOnly ? R.string.seller_shipments_title : R.string.seller_orders_title);
        sellerId = FirebaseAuth.getInstance().getCurrentUser() == null ? ""
                : FirebaseAuth.getInstance().getCurrentUser().getUid();
        viewModel = new ViewModelProvider(this, new OrderViewModelFactory(requireContext()))
                .get(OrderViewModel.class);
        viewModel.observeSellerOrders(sellerId).observe(getViewLifecycleOwner(), this::render);
        viewModel.getState().observe(getViewLifecycleOwner(), value -> {
            if (currentOrders != null) render(currentOrders);
            if (value.getStatus() == OrderLifecycleState.Status.ERROR)
                Toast.makeText(requireContext(), value.getMessage(), Toast.LENGTH_LONG).show();
        });
        view.findViewById(R.id.refreshSellerOrdersButton)
                .setOnClickListener(unused -> viewModel.refreshSellerOrders(sellerId));
        if (!sellerId.isEmpty()) viewModel.refreshSellerOrders(sellerId);
    }
    private void render(List<Order> orders) {
        currentOrders = orders;
        container.removeAllViews();
        List<Order> visible = new ArrayList<>();
        if (orders != null) for (Order order : orders)
            if (!deliveryOnly || order.getFulfillmentType() == FulfillmentType.DELIVERY) visible.add(order);
        empty.setText(deliveryOnly ? R.string.seller_shipments_empty : R.string.seller_orders_empty);
        empty.setVisibility(visible.isEmpty() ? View.VISIBLE : View.GONE);
        pagination.remote(() -> viewModel.hasMoreSellerOrders(sellerId),
                () -> viewModel.getState().getValue() != null && viewModel.getState().getValue().getStatus() == OrderLifecycleState.Status.LOADING,
                () -> viewModel.loadMoreSellerOrders(sellerId)).render(container, visible, this::renderPage);
    }
    private void renderPage(List<Order> visible) {
        container.removeAllViews();
        for (Order order : visible) {
            View.OnClickListener open = unused -> {
                Bundle args = new Bundle(); args.putString("orderId", order.getId());
                Navigation.findNavController(requireView()).navigate(
                        R.id.action_sellerOrderListFragment_to_orderDetailFragment, args);
            };
            container.addView(OrderListRow.create(container, order, true,
                    deliveryOnly ? R.color.admin_shipping : R.color.admin_orders, open));
        }

    }
}
