package com.rescuefarm.ui.engagement;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
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
import com.rescuefarm.domain.model.Review;
import com.rescuefarm.service.media.CloudinaryUploadUi;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class ReviewFragment extends Fragment {
    private EngagementViewModel vm;
    private EditText order, item, rating, content, images;
    private LinearLayout list;
    private TextView imagesStatus;
    private View uploadImagesButton;
    private String productId = "", uid = "";
    private final ActivityResultLauncher<String> imagePicker = registerForActivityResult(
            new ActivityResultContracts.GetContent(), uri -> CloudinaryUploadUi.upload(this, uri,
                    "review", uid + "_" + text(order) + "_" + text(item), imageCount(), uploadImagesButton, imagesStatus, null,
                    url -> { appendImageUrl(url); order.setEnabled(false); item.setEnabled(false); }));

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
        if (!orderIdArg.isEmpty() && !orderItemArg.isEmpty()) {
            order.setText(orderIdArg); item.setText(orderItemArg);
            order.setEnabled(false); item.setEnabled(false);
        }
        rating = view.findViewById(R.id.reviewRating); content = view.findViewById(R.id.reviewContent);
        images = view.findViewById(R.id.reviewImages); list = view.findViewById(R.id.reviewContainer);
        imagesStatus = view.findViewById(R.id.reviewImagesStatus);
        uploadImagesButton = view.findViewById(R.id.uploadReviewImagesButton);
        uploadImagesButton.setOnClickListener(v -> {
            if (imageCount() >= 5) Toast.makeText(requireContext(), R.string.review_image_limit_message,
                    Toast.LENGTH_SHORT).show();
            else if (text(order).isEmpty() || text(item).isEmpty())
                Toast.makeText(requireContext(), "Nhập mã đơn hàng và mã sản phẩm trước khi tải ảnh.", Toast.LENGTH_SHORT).show();
            else imagePicker.launch("image/*");
        });
        view.findViewById(R.id.clearReviewImagesButton).setOnClickListener(v -> {
            images.setText(""); order.setEnabled(true); item.setEnabled(true); updateImageCount();
        });
        vm = new ViewModelProvider(this, new EngagementViewModelFactory(requireContext()))
                .get(EngagementViewModel.class);
        vm.reviews().observe(getViewLifecycleOwner(), this::render);
        vm.getState().observe(getViewLifecycleOwner(), value -> {
            if (value.getStatus() == EngagementState.Status.ERROR
                    || value.getStatus() == EngagementState.Status.SUCCESS) {
                Toast.makeText(requireContext(), value.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
        view.findViewById(R.id.saveReviewButton).setOnClickListener(v -> save());
        vm.refreshReviews(productId);
    }

    private void save() {
        try {
            List<String> urls = new ArrayList<>();
            for (String value : images.getText().toString().split("[,\\n]"))
                if (!value.trim().isEmpty()) urls.add(value.trim());
            Review review = Review.create(uid, productId, text(order), text(item),
                    Integer.parseInt(text(rating)), text(content), urls, new Date());
            vm.saveReview(review, false);
        } catch (Exception error) {
            Toast.makeText(requireContext(), error.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void appendImageUrl(String url) {
        if (imageCount() >= 5) {
            Toast.makeText(requireContext(), R.string.review_image_limit_message, Toast.LENGTH_SHORT).show();
            return;
        }
        String current = images.getText().toString().trim();
        images.setText(current.isEmpty() ? url : current + "\n" + url);
        updateImageCount();
    }

    private int imageCount() {
        int count = 0;
        for (String value : images.getText().toString().split("[,\\n]+"))
            if (!value.trim().isEmpty()) count++;
        return count;
    }

    private void updateImageCount() {
        int count = imageCount();
        if (count == 0) imagesStatus.setText(R.string.no_images_selected);
        else imagesStatus.setText(getString(R.string.uploaded_images_count_format, count));
    }

    private void render(List<Review> reviews) {
        list.removeAllViews();
        if (reviews == null) return;
        for (Review review : reviews) {
            TextView row = new TextView(requireContext());
            row.setText(getString(R.string.review_row_format, review.getRating(), review.getContent()));
            list.addView(row);
        }
    }

    private String text(EditText editText) { return editText.getText().toString().trim(); }
}
