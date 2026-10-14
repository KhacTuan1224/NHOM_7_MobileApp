package com.rescuefarm.ui.seller;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.ViewModel;
import com.rescuefarm.data.repository.CampaignRepository;
import com.rescuefarm.data.repository.OrderRepository;
import com.rescuefarm.data.repository.PostRepository;
import com.rescuefarm.data.repository.ProductRepository;
import com.rescuefarm.data.repository.UserRepository;
import com.rescuefarm.domain.enums.OrderStatus;
import com.rescuefarm.domain.model.Order;
import com.rescuefarm.domain.model.OrderItem;
import com.rescuefarm.domain.model.Product;
import com.rescuefarm.domain.model.Post;
import com.rescuefarm.domain.model.RescueCampaign;
import com.rescuefarm.domain.model.Seller;
import com.rescuefarm.domain.model.User;
import com.rescuefarm.service.seller.SellerDashboardAggregator;
import com.rescuefarm.service.seller.SellerDashboardSnapshot;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

public class SellerViewModel extends ViewModel {
    private final String sellerId;
    private final ProductRepository productRepo;
    private final CampaignRepository campaignRepo;
    private final PostRepository postRepo;
    private final OrderRepository orderRepo;
    private final UserRepository userRepo;
    private final SellerDashboardAggregator aggregator = new SellerDashboardAggregator();
    private final MediatorLiveData<SellerDashboardState> state = new MediatorLiveData<>();
    private List<Product> products = new ArrayList<>();
    private List<RescueCampaign> campaigns = new ArrayList<>();
    private List<Post> posts = new ArrayList<>();
    private List<Order> orders = new ArrayList<>();
    private final List<OrderItem> deliveredItems = new ArrayList<>();
    private final Map<String, List<OrderItem>> deliveredItemCache = new HashMap<>();
    private int promotionCount, pending, pendingItemLoads, analyticsGeneration;
    private int statisticsPeriodDays = 30;
    private boolean approved, failed, analyticsReady, analyticsLoading;
    private String dataWarning = "";

    public SellerViewModel(String id, ProductRepository products, CampaignRepository campaigns,
            PostRepository posts, OrderRepository orders, UserRepository users) {
        sellerId = id; productRepo = products; campaignRepo = campaigns; postRepo = posts;
        orderRepo = orders; userRepo = users;
        state.setValue(SellerDashboardState.of(SellerDashboardState.Status.VERIFYING, null,
                "Đang xác minh seller…"));
        state.addSource(productRepo.observeSellerProducts(id), value -> { this.products = safe(value); publish(); });
        state.addSource(campaignRepo.observeSellerCampaigns(id), value -> { this.campaigns = safe(value); publish(); });
        state.addSource(postRepo.observeSellerPosts(id), value -> { this.posts = safe(value); publish(); });
        state.addSource(orderRepo.observeSellerOrders(id), value -> {
            this.orders = safe(value);
            if (approved && pending == 0) loadDeliveredItems();
            publish();
        });
    }

    public LiveData<SellerDashboardState> getState() { return state; }
    public void setStatisticsPeriodDays(int days) {
        statisticsPeriodDays = Math.max(0, days);
        publish();
    }

    public void refresh() {
        if (sellerId == null || sellerId.trim().isEmpty()) {
            state.setValue(SellerDashboardState.of(SellerDashboardState.Status.ERROR, null,
                    "Cần đăng nhập seller.")); return;
        }
        state.setValue(SellerDashboardState.of(SellerDashboardState.Status.VERIFYING, null,
                "Đang xác minh seller…"));
        userRepo.getUser(sellerId, new UserRepository.UserCallback() {
            @Override public void onSuccess(User user) {
                if (!(user instanceof Seller) || !((Seller) user).canSell()) {
                    approved = false;
                    state.postValue(SellerDashboardState.of(SellerDashboardState.Status.NOT_APPROVED,
                            null, "Seller phải được duyệt trước khi quản lý gian hàng."));
                    return;
                }
                approved = true; loadAll();
            }
            @Override public void onError(UserRepository.ProfileError error, String message) {
                state.postValue(SellerDashboardState.of(SellerDashboardState.Status.ERROR, null, message));
            }
        });
    }

    private void loadAll() {
        pending = 5; failed = false; dataWarning = ""; analyticsReady = false;
        analyticsLoading = false;
        state.setValue(SellerDashboardState.of(SellerDashboardState.Status.LOADING, null,
                "Đang tải dashboard seller…"));
        productRepo.refreshSellerProducts(sellerId, action(this::done, this::fail));
        productRepo.getSellerPromotions(sellerId, new ProductRepository.PromotionListCallback() {
            @Override public void onSuccess(List<com.rescuefarm.domain.model.Promotion> value) {
                promotionCount = value == null ? 0 : value.size(); done();
            }
            @Override public void onError(ProductRepository.ErrorCode error, String message) { fail(message); }
        });
        campaignRepo.refreshSellerCampaigns(sellerId, action(this::done, this::fail));
        postRepo.refreshSellerPosts(sellerId, action(this::done, this::fail));
        orderRepo.refreshSellerOrders(sellerId, action(this::done, this::fail));
    }

    private ProductRepository.ActionCallback action(Runnable success, java.util.function.Consumer<String> error) {
        return new ProductRepository.ActionCallback() {
            @Override public void onSuccess() { success.run(); }
            @Override public void onError(ProductRepository.ErrorCode code, String message) { error.accept(message); }
        };
    }

    private CampaignRepository.ActionCallback action(Runnable success, java.util.function.Consumer<String> error) {
        return new CampaignRepository.ActionCallback() {
            @Override public void onSuccess() { success.run(); }
            @Override public void onError(CampaignRepository.ErrorCode code, String message) { error.accept(message); }
        };
    }

    private PostRepository.ActionCallback action(Runnable success, java.util.function.Consumer<String> error) {
        return new PostRepository.ActionCallback() {
            @Override public void onSuccess() { success.run(); }
            @Override public void onError(PostRepository.ErrorCode code, String message) { error.accept(message); }
        };
    }

    private OrderRepository.ActionCallback action(Runnable success, java.util.function.Consumer<String> error) {
        return new OrderRepository.ActionCallback() {
            @Override public void onSuccess() { success.run(); }
            @Override public void onError(OrderRepository.ErrorCode code, String message) { error.accept(message); }
        };
    }

    private synchronized void done() {
        if (pending > 0) pending--;
        if (pending == 0 && !analyticsLoading) loadDeliveredItems();
        publish();
    }

    private synchronized void fail(String message) {
        failed = true;
        if (!dataWarning.isEmpty()) dataWarning += " · ";
        dataWarning += message == null || message.trim().isEmpty() ? "Một nguồn dữ liệu chưa tải được." : message;
        if (pending > 0) pending--;
        if (pending == 0 && !analyticsLoading) loadDeliveredItems();
        publish();
    }

    private synchronized void loadDeliveredItems() {
        if (analyticsLoading || pending > 0) return;
        analyticsLoading = true; analyticsReady = false;
        final int generation = ++analyticsGeneration;
        List<Order> completed = new ArrayList<>();
        for (Order order : orders) if (order.getStatus() == OrderStatus.DELIVERED) completed.add(order);
        Set<String> currentDeliveredIds = new HashSet<>();
        for (Order order : completed) currentDeliveredIds.add(order.getId());
        deliveredItemCache.keySet().retainAll(currentDeliveredIds);
        pendingItemLoads = 0; deliveredItems.clear();
        List<Order> missing = new ArrayList<>();
        for (Order order : completed) {
            List<OrderItem> cached = deliveredItemCache.get(order.getId());
            if (cached == null) missing.add(order); else deliveredItems.addAll(cached);
        }
        pendingItemLoads = missing.size();
        if (missing.isEmpty()) { analyticsReady = true; analyticsLoading = false; publish(); return; }
        for (Order order : missing) orderRepo.getOrderItems(order.getId(), new OrderRepository.ItemCallback() {
            @Override public void success(List<OrderItem> items) {
                synchronized (SellerViewModel.this) {
                    if (generation != analyticsGeneration) return;
                    List<OrderItem> stableItems = items == null ? new ArrayList<>() : new ArrayList<>(items);
                    deliveredItemCache.put(order.getId(), stableItems);
                    deliveredItems.addAll(stableItems);
                    finishItemLoad();
                }
            }
            @Override public void error(String message) {
                synchronized (SellerViewModel.this) {
                    if (generation != analyticsGeneration) return;
                    failed = true;
                    if (!dataWarning.isEmpty()) dataWarning += " · ";
                    dataWarning += "Một số mặt hàng đơn đã giao chưa tải được.";
                    finishItemLoad();
                }
            }
        });
    }

    private synchronized void finishItemLoad() {
        if (pendingItemLoads > 0) pendingItemLoads--;
        if (pendingItemLoads == 0) { analyticsReady = true; analyticsLoading = false; publish(); }
    }

    private void publish() {
        if (!approved || pending > 0 || !analyticsReady) return;
        SellerDashboardSnapshot snapshot = aggregator.aggregate(sellerId, products, campaigns, posts,
                orders, deliveredItems, promotionCount, statisticsPeriodDays);
        String message = !dataWarning.isEmpty() ? "Một số dữ liệu chưa đồng bộ; đang hiển thị phần đã tải được. " + dataWarning
                : snapshot.isEmpty() ? "Chưa có dữ liệu seller." : "Đã đồng bộ dashboard.";
        state.postValue(SellerDashboardState.of(snapshot.isEmpty() ? SellerDashboardState.Status.EMPTY
                : SellerDashboardState.Status.READY, snapshot, message));
    }

    private static <T> List<T> safe(List<T> value) {
        return value == null ? new ArrayList<>() : new ArrayList<>(value);
    }

    @Override protected void onCleared() {
        productRepo.close(); campaignRepo.close(); postRepo.close(); orderRepo.close();
    }
}
