package com.rescuefarm.ui.feed;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import com.rescuefarm.R;
import com.rescuefarm.ui.seller.SellerUiStyles;
import com.rescuefarm.domain.enums.PostStatus;
import com.rescuefarm.domain.model.Post;
import java.util.List;

public class SellerPostListFragment extends Fragment {
    private final com.rescuefarm.ui.common.ListPagination pagination = new com.rescuefarm.ui.common.ListPagination();
    private PostViewModel viewModel; private PostCardRenderer renderer; private LinearLayout container; private List<Post> currentPosts;
    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater,
            @Nullable ViewGroup parent, @Nullable Bundle state) {
        return inflater.inflate(R.layout.fragment_seller_post_list, parent, false);
    }
    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        SellerUiStyles.styleActions(view, SellerUiStyles.Section.POSTS);
        renderer = new PostCardRenderer(this); container = view.findViewById(R.id.sellerPostContainer);
        viewModel = new ViewModelProvider(this, new PostViewModelFactory(requireContext())).get(PostViewModel.class);
        viewModel.getSellerPosts().observe(getViewLifecycleOwner(), this::render);
        viewModel.getSellerPostsHaveMore().observe(getViewLifecycleOwner(), unused -> {
            if (currentPosts != null) render(currentPosts);
        });
        viewModel.getState().observe(getViewLifecycleOwner(), value -> {
            if (currentPosts != null) render(currentPosts);
            if (value.getStatus() == PostScreenState.Status.ERROR) Toast.makeText(requireContext(), value.getMessage(), Toast.LENGTH_LONG).show();
        });
        view.findViewById(R.id.addPostButton).setOnClickListener(v -> Navigation.findNavController(view)
                .navigate(R.id.action_sellerPostListFragment_to_postEditorFragment));
        view.findViewById(R.id.refreshSellerPostsButton).setOnClickListener(v -> viewModel.refreshSellerPosts());
    }
    @Override public void onResume() { super.onResume(); if (viewModel != null) viewModel.refreshSellerPostPage(true); }
    private void render(List<Post> posts) {
        currentPosts = posts;
        pagination.remote(() -> Boolean.TRUE.equals(viewModel.getSellerPostsHaveMore().getValue()),
                viewModel::isSellerPageLoading, () -> viewModel.refreshSellerPostPage(false))
                .render(container, posts, this::renderPage);
    }
    private void renderPage(List<Post> posts) {
        container.removeAllViews();
        if (posts == null || posts.isEmpty()) { container.addView(renderer.message("Bạn chưa có post nào.")); return; }
        for (Post post : posts) {
            SellerUiStyles.Row row = SellerUiStyles.row(requireContext(), SellerUiStyles.Section.POSTS);
            row.title(post.getTitle());
            row.body("Trạng thái: " + com.rescuefarm.ui.common.EnumLabels.label(post.getStatus()));
            row.body("Mức độ: " + com.rescuefarm.ui.common.EnumLabels.label(post.getUrgencyLevel()));
            String excerpt = post.getContent().length() > 180 ? post.getContent().substring(0, 180) + "…" : post.getContent();
            row.body(excerpt);
            if (post.getCreatedAt() != null) row.metadata(java.text.DateFormat.getDateTimeInstance(
                    java.text.DateFormat.SHORT, java.text.DateFormat.SHORT, java.util.Locale.forLanguageTag("vi-VN"))
                    .format(post.getCreatedAt()));
            if (!post.getImageUrls().isEmpty()) {
                android.widget.ImageView image = new android.widget.ImageView(requireContext());
                image.setContentDescription("Ảnh bài viết giải cứu"); image.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
                row.information.addView(image, new LinearLayout.LayoutParams(-1, SellerUiStyles.dp(requireContext(), 120)));
                com.bumptech.glide.Glide.with(this).load(post.getImageUrls().get(0)).centerCrop().into(image);
            }
            boolean editable = post.getStatus() == PostStatus.DRAFT || post.getStatus() == PostStatus.REJECTED;
            row.action(editable ? "Sửa" : "Chi tiết", editable ? "Chỉnh sửa bài đăng" : "Xem chi tiết bài đăng", v -> open(post));
            row.root.setOnClickListener(v -> open(post));
            container.addView(row.root);
        }

    }
    private void open(Post post) {
        Bundle args = new Bundle(); args.putString("postId", post.getId());
        int action = post.getStatus() == PostStatus.DRAFT || post.getStatus() == PostStatus.REJECTED
                ? R.id.action_sellerPostListFragment_to_postEditorFragment
                : R.id.action_sellerPostListFragment_to_postDetailFragment;
        Navigation.findNavController(requireView()).navigate(action, args);
    }
}
