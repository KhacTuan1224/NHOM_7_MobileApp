package com.rescuefarm.ui.engagement;

import android.os.Bundle;
import android.animation.ValueAnimator;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.google.firebase.auth.FirebaseAuth;



import com.rescuefarm.R;
import com.rescuefarm.MainActivity;
import com.rescuefarm.ui.navigation.ActorUiPolicy;
import com.rescuefarm.domain.enums.OrderStatus;
import com.rescuefarm.domain.model.Order;
import com.rescuefarm.domain.model.OrderItem;
import com.rescuefarm.domain.model.Review;
import com.rescuefarm.service.media.CloudinaryUploadUi;
import com.rescuefarm.service.backend.RenderBackendClient;
import com.rescuefarm.ui.order.OrderViewModel;
import com.rescuefarm.ui.order.OrderViewModelFactory;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

public class ReviewFragment extends Fragment {
    private EngagementViewModel vm;
    private OrderViewModel orderViewModel;
    private EditText order, item, content, images;
    private LinearLayout list;
    private LinearLayout ratingStars;
    private Spinner eligibleItems;
    private TextView imagesStatus;
    private TextView reviewSummary, reviewStatus;
    private View uploadImagesButton, saveButton;
    private String productId = "", uid = "";
    private final List<ReviewTarget> targets = new ArrayList<>();
    private final Map<String, Review> reviewsByTarget = new HashMap<>();
    private final Map<String, Order> eligibleOrders = new HashMap<>();
    private final Map<String, List<OrderItem>> itemsByOrder = new HashMap<>();
    private final Set<String> loadedOrderIds = new HashSet<>();
    private boolean bindingTargets;
    private boolean editing;
    private boolean reviewsLoaded;
    private int selectedRating;


    private final ActivityResultLauncher<String> imagePicker = registerForActivityResult(
            new ActivityResultContracts.GetContent(), uri -> CloudinaryUploadUi.upload(this, uri,
                    "review", uid + "_" + text(order) + "_" + text(item), imageCount(),
                    uploadImagesButton, imagesStatus, null, url -> { appendImageUrl(url); }));

    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater,
            @Nullable ViewGroup parent, @Nullable Bundle state) {
        return inflater.inflate(R.layout.fragment_reviews, parent, false);
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        productId = getArguments() == null ? "" : getArguments().getString("productId", "");
        String orderIdArg = getArguments() == null ? "" : getArguments().getString("orderId", "");
        String orderItemArg = getArguments() == null ? "" : getArguments().getString("orderItemId", "");
        uid = FirebaseAuth.getInstance().getCurrentUser() == null ? ""
                : FirebaseAuth.getInstance().getCurrentUser().getUid();
        order = view.findViewById(R.id.reviewOrderId); item = view.findViewById(R.id.reviewOrderItemId);
        ratingStars = view.findViewById(R.id.reviewRatingStars); content = view.findViewById(R.id.reviewContent);
        images = view.findViewById(R.id.reviewImages); list = view.findViewById(R.id.reviewContainer);
        eligibleItems = view.findViewById(R.id.reviewEligibleOrderItem);
        imagesStatus = view.findViewById(R.id.reviewImagesStatus);
        reviewSummary = view.findViewById(R.id.reviewProductSummary);
        reviewStatus = view.findViewById(R.id.reviewListStatus);
        uploadImagesButton = view.findViewById(R.id.uploadReviewImagesButton);
        saveButton = view.findViewById(R.id.saveReviewButton);
        bindRatingStars();
        applyActorMode(view);
        if (requireActivity() instanceof MainActivity) {
            ((MainActivity) requireActivity()).observeActorRole().observe(getViewLifecycleOwner(), role ->
                    applyActorMode(view));
        }
        uploadImagesButton.setOnClickListener(v -> {
            if (targets.isEmpty()) noEligibleOrder();
            else if (imageCount() >= 5) Toast.makeText(requireContext(),
                    R.string.review_image_limit_message, Toast.LENGTH_SHORT).show();
            else imagePicker.launch("image/*");
        });
        view.findViewById(R.id.clearReviewImagesButton).setOnClickListener(v -> {
            images.setText(""); updateImageCount();
        });

        vm = new ViewModelProvider(this, new EngagementViewModelFactory(requireContext()))
                .get(EngagementViewModel.class);
        orderViewModel = new ViewModelProvider(this, new OrderViewModelFactory(requireContext()))
                .get(OrderViewModel.class);
        vm.reviews().observe(getViewLifecycleOwner(), this::renderReviews);
        vm.getState().observe(getViewLifecycleOwner(), value -> {
            if (value.getStatus() == EngagementState.Status.LOADING)
                reviewStatus.setText("Đang tải đánh giá…");
            if (value.getStatus() == EngagementState.Status.ERROR
                    || value.getStatus() == EngagementState.Status.SUCCESS) {
                reviewsLoaded = value.getStatus() == EngagementState.Status.SUCCESS;
                if (value.getStatus() == EngagementState.Status.ERROR)
                    reviewStatus.setText("Không tải được đánh giá: " + value.getMessage());
                else updateReviewStatus(vm.reviews().getValue());
                Toast.makeText(requireContext(), value.getMessage(), Toast.LENGTH_SHORT).show();
                if (value.getStatus() == EngagementState.Status.SUCCESS) {
                    editing = false; saveButton.setEnabled(!targets.isEmpty());
                    ((TextView) saveButton).setText("Gửi đánh giá");
                    bindSelectedReview();
                }
            }
        });
        eligibleItems.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View row, int position, long id) {
                if (!bindingTargets && position >= 0 && position < targets.size()) {
                    ReviewTarget target = targets.get(position);
                    order.setText(target.order.getId()); item.setText(target.item.getId());
                    bindSelectedReview();
                }
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        });
        saveButton.setOnClickListener(v -> save());
        com.rescuefarm.ui.product.ProductViewModel productVm = new ViewModelProvider(this,
            new com.rescuefarm.ui.product.ProductViewModelFactory(requireContext())).get(com.rescuefarm.ui.product.ProductViewModel.class);
        productVm.observeProduct(productId).observe(getViewLifecycleOwner(), product -> {
            if(product!=null)reviewSummary.setText(String.format(java.util.Locale.forLanguageTag("vi-VN"),
                "★ %.1f · %d đánh giá", product.getAverageRating(),product.getReviewCount()));
        });
        vm.refreshReviews(productId);
        if (!uid.isEmpty()) orderViewModel.observeCustomerOrders(uid).observe(
                getViewLifecycleOwner(), orders -> renderEligibleItems(orders, orderIdArg, orderItemArg));
        else saveButton.setEnabled(false);
    }

    private void renderEligibleItems(List<Order> orders, String selectedOrderId, String selectedItemId) {
        eligibleOrders.clear();
        if (orders != null) for (Order orderValue : orders) {
            if (orderValue != null && orderValue.getStatus() == OrderStatus.DELIVERED)
                eligibleOrders.put(orderValue.getId(), orderValue);
        }
        for (String id : eligibleOrders.keySet()) if (loadedOrderIds.add(id)) {
            orderViewModel.getOrderItems(id,new com.rescuefarm.data.repository.OrderRepository.ItemCallback() {
                public void success(List<OrderItem> values) {
                    if (getView()==null || !eligibleOrders.containsKey(id)) return;
                    List<OrderItem> savedItems = new ArrayList<>();
                    for(OrderItem value:values)if(productId.equals(value.getProductId()))savedItems.add(value);
                    itemsByOrder.put(id,savedItems); bindReviewTargets(selectedOrderId,selectedItemId);
                }
                public void error(String message) {
                    loadedOrderIds.remove(id);
                    if(getView()!=null)reviewStatus.setText(message);
                }
            });
        }
        bindReviewTargets(selectedOrderId, selectedItemId);
    }

    private void bindReviewTargets(String selectedOrderId, String selectedItemId) {
        targets.clear();
        for (Map.Entry<String, Order> entry : eligibleOrders.entrySet()) {
            List<OrderItem> orderItems = itemsByOrder.get(entry.getKey());
            if (orderItems != null) for (OrderItem orderItem : orderItems)
                targets.add(new ReviewTarget(entry.getValue(), orderItem));
        }
        List<String> labels = new ArrayList<>(); int selected = 0;
        for (int index = 0; index < targets.size(); index++) {
            ReviewTarget target = targets.get(index);
            labels.add(target.order.getOrderCode() + " · " + target.item.getProductNameSnapshot()
                    + " · " + target.item.getQuantity() + " " + target.item.getUnitSnapshot());
            if (target.order.getId().equals(selectedOrderId) && target.item.getId().equals(selectedItemId))
                selected = index;
        }
        bindingTargets = true;
        eligibleItems.setAdapter(new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_dropdown_item, labels));
        if (!targets.isEmpty()) eligibleItems.setSelection(selected);
        bindingTargets = false;
        saveButton.setEnabled(!targets.isEmpty());
        if (targets.isEmpty()) saveButton.setEnabled(false);
        else {
            if (orderIdArgMatches(selectedOrderId, selectedItemId)) {
                order.setText(selectedOrderId); item.setText(selectedItemId);
            } else {
                order.setText(targets.get(selected).order.getId()); item.setText(targets.get(selected).item.getId());
            }
            bindSelectedReview();
        }
    }

    private boolean orderIdArgMatches(String orderId, String itemId) {
        if (orderId == null || itemId == null) return false;
        for (ReviewTarget target : targets) if (target.order.getId().equals(orderId)
                && target.item.getId().equals(itemId)) return true;
        return false;
    }

    private void bindSelectedReview() {
        String key = targetKey(text(order), text(item));
        Review review = reviewsByTarget.get(key);
        editing = review != null;
        if (review == null) {
            selectedRating = 0; renderRatingStars(false, selectedRating); content.setText(""); images.setText("");
            ((TextView) saveButton).setText("Gửi đánh giá");
        } else {
            selectedRating = review.getRating(); renderRatingStars(false, selectedRating); content.setText(review.getContent());
            images.setText(join(review.getImageUrls())); updateImageCount();
            ((TextView) saveButton).setText("Cập nhật đánh giá");
        }
        saveButton.setEnabled(!targets.isEmpty());
    }

    private void save() {
        if (ActorUiPolicy.isSellerReadOnly(this)) return;
        if (targets.isEmpty()) { noEligibleOrder(); return; }
        if (selectedRating < 1 || selectedRating > 5) {
            Toast.makeText(requireContext(), "Vui lòng chọn số sao đánh giá.", Toast.LENGTH_LONG).show();
            return;
        }
        try {
            List<String> urls = new ArrayList<>();
            for (String value : images.getText().toString().split("[,\\n]+"))
                if (!value.trim().isEmpty()) urls.add(value.trim());
            Review review = Review.create(uid, productId, text(order), text(item),
                    selectedRating, text(content), urls, new Date());
            vm.saveReview(review, editing, () -> requestRatingRecalculation(review.getId(),
                    review.getOrderId(), review.getOrderItemId()));
        } catch (Exception error) {
            Toast.makeText(requireContext(), error.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void applyActorMode(View root) {
        boolean readOnly = ActorUiPolicy.isSellerReadOnly(this);
        View composer = root.findViewById(R.id.reviewComposer);
        View sellerNotice = root.findViewById(R.id.sellerReviewReadOnlyNotice);
        if (composer != null) composer.setVisibility(readOnly ? View.GONE : View.VISIBLE);
        if (sellerNotice != null) sellerNotice.setVisibility(readOnly ? View.VISIBLE : View.GONE);
    }

    private void bindRatingStars() {
        ratingStars.removeAllViews();
        for (int index = 1; index <= 5; index++) {
            final int value = index;
            TextView star = new TextView(requireContext());
            star.setGravity(android.view.Gravity.CENTER);
            star.setTextSize(34);
            star.setMinHeight(Math.round(52 * getResources().getDisplayMetrics().density));
            star.setFocusable(true);
            star.setClickable(true);
            star.setBackgroundResource(android.R.drawable.list_selector_background);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            ratingStars.addView(star, params);
            star.setOnClickListener(v -> {
                int previousRating = selectedRating;
                selectedRating = value;
                renderRatingStars(true, previousRating);
                star.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK);
            });
        }
        renderRatingStars(false, selectedRating);
    }

    private void renderRatingStars(boolean animateSelection, int previousRating) {
        int filledColor = androidx.core.content.ContextCompat.getColor(requireContext(), R.color.rescue_primary);
        int emptyColor = androidx.core.content.ContextCompat.getColor(requireContext(), R.color.rescue_outline);
        for (int index = 0; index < ratingStars.getChildCount(); index++) {
            TextView star = (TextView) ratingStars.getChildAt(index);
            boolean filled = index < selectedRating;
            star.setText(filled ? "★" : "☆");
            star.setContentDescription((index + 1) + " sao" + (filled ? ", đã chọn" : ""));
            boolean newlyFilled = animateSelection && filled && index >= previousRating;
            star.setTextColor(filled && !newlyFilled ? filledColor : emptyColor);
            if (newlyFilled) {
                ValueAnimator color = ValueAnimator.ofArgb(emptyColor, filledColor);
                color.setDuration(180);
                color.addUpdateListener(value -> star.setTextColor((int) value.getAnimatedValue()));
                color.start();
                star.setScaleX(0.8f);
                star.setScaleY(0.8f);
                star.animate().scaleX(1.12f).scaleY(1.12f).setDuration(110)
                        .withEndAction(() -> star.animate().scaleX(1f).scaleY(1f)
                                .setDuration(100).start()).start();
            } else {
                star.setScaleX(1f);
                star.setScaleY(1f);
            }
        }
    }

    private void appendImageUrl(String url) {
        if (imageCount() >= 5) {
            Toast.makeText(requireContext(), R.string.review_image_limit_message, Toast.LENGTH_SHORT).show(); return;
        }
        String current = images.getText().toString().trim();
        images.setText(current.isEmpty() ? url : current + "\n" + url); updateImageCount();
    }

    private int imageCount() {
        int count = 0;
        for (String value : images.getText().toString().split("[,\\n]+")) if (!value.trim().isEmpty()) count++;
        return count;
    }

    private void updateImageCount() {
        int count = imageCount();
        imagesStatus.setText(count == 0 ? getString(R.string.no_images_selected)
                : getString(R.string.uploaded_images_count_format, count));
    }

    private void renderReviews(List<Review> reviews) {
        list.removeAllViews(); reviewsByTarget.clear();
        updateReviewStatus(reviews);
        if (reviews == null || reviews.isEmpty()) return;
        for (Review review : reviews) {
            if (uid.equals(review.getCustomerId())) reviewsByTarget.put(
                    targetKey(review.getOrderId(), review.getOrderItemId()), review);
            LinearLayout row = new LinearLayout(requireContext()); row.setOrientation(LinearLayout.VERTICAL);
            TextView text = new TextView(requireContext());
            text.setText(getString(R.string.review_row_format, review.getRating(), review.getContent()));
            row.addView(text);
            ReviewMediaRenderer.append(this, row, review);
            if (uid.equals(review.getCustomerId())) {
                LinearLayout actions = new LinearLayout(requireContext());
                com.google.android.material.button.MaterialButton edit = new com.google.android.material.button.MaterialButton(
                        requireContext(), null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
                edit.setText("Sửa đánh giá");
                edit.setOnClickListener(v -> selectTarget(review.getOrderId(), review.getOrderItemId()));
                com.google.android.material.button.MaterialButton delete = new com.google.android.material.button.MaterialButton(
                        requireContext(), null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
                delete.setText("Xóa");
                int textColor = androidx.core.content.ContextCompat.getColor(requireContext(), R.color.rescue_on_surface);
                int borderColor = androidx.core.content.ContextCompat.getColor(requireContext(), R.color.rescue_primary);
                android.content.res.ColorStateList clearBackground = android.content.res.ColorStateList.valueOf(
                        android.graphics.Color.TRANSPARENT);
                edit.setTextColor(textColor); edit.setStrokeColor(android.content.res.ColorStateList.valueOf(borderColor));
                edit.setBackgroundTintList(clearBackground);
                delete.setTextColor(textColor); delete.setStrokeColor(android.content.res.ColorStateList.valueOf(borderColor));
                delete.setBackgroundTintList(clearBackground);
                delete.setOnClickListener(v -> new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                        .setTitle("Xóa đánh giá?").setMessage("Đánh giá này sẽ bị gỡ khỏi sản phẩm.")
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton("Xóa", (dialog, which) -> vm.deleteReview(review.getId(), productId,
                                () -> requestRatingRecalculation("", review.getOrderId(), review.getOrderItemId()))).show());
                actions.addView(edit); actions.addView(delete); row.addView(actions);
            }
            ContextReportAction.add(this, row, "REVIEW", review.getId(), review.getCustomerId(), "Báo cáo đánh giá");
            list.addView(row);
        }
        bindSelectedReview();
    }

    private void selectTarget(String orderId, String itemId) {
        for (int index = 0; index < targets.size(); index++) {
            ReviewTarget target = targets.get(index);
            if (target.order.getId().equals(orderId) && target.item.getId().equals(itemId)) {
                eligibleItems.setSelection(index); this.order.setText(orderId); this.item.setText(itemId);
                bindSelectedReview(); return;
            }
        }
    }

    private void noEligibleOrder() {
        saveButton.setEnabled(false);
        Toast.makeText(requireContext(), "Chỉ có thể đánh giá sản phẩm trong đơn đã giao.", Toast.LENGTH_SHORT).show();
    }

    private String targetKey(String orderId, String itemId) { return uid + "_" + orderId + "_" + itemId; }

    private void requestRatingRecalculation(String reviewId, String orderId, String itemId) {
        org.json.JSONObject payload = new org.json.JSONObject();
        try {
            if (reviewId != null && !reviewId.isEmpty()) payload.put("reviewId", reviewId);
            payload.put("productId", productId); payload.put("orderId", orderId); payload.put("orderItemId", itemId);
        } catch (org.json.JSONException ignored) { return; }
        RenderBackendClient.post("/recalculate-rating", payload, new RenderBackendClient.Callback() {
            @Override public void onSuccess(org.json.JSONObject response) { }
            @Override public void onError(String error) {
                if (isAdded()) Toast.makeText(requireContext(), "Chưa cập nhật được tổng rating: " + error,
                        Toast.LENGTH_LONG).show();
            }
        });
    }
    private String join(List<String> values) { return values == null ? "" : android.text.TextUtils.join("\n", values); }
    private void updateReviewStatus(List<Review> reviews) {
        if (!reviewsLoaded) return;
        reviewStatus.setText(reviews == null || reviews.isEmpty()
                ? "Sản phẩm chưa có đánh giá." : "");
    }

    private String text(EditText field) { return field.getText().toString().trim(); }

    private static final class ReviewTarget {
        final Order order; final OrderItem item;
        ReviewTarget(Order order, OrderItem item) { this.order = order; this.item = item; }
    }
}
