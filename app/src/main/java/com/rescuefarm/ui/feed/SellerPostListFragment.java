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
import com.rescuefarm.domain.enums.PostStatus;
import com.rescuefarm.domain.model.Post;
import java.util.List;

public class SellerPostListFragment extends Fragment {
    private PostViewModel viewModel; private PostCardRenderer renderer; private LinearLayout container; private List<Post> currentPosts;
    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater,
            @Nullable ViewGroup parent, @Nullable Bundle state) {
        return inflater.inflate(R.layout.fragment_seller_post_list, parent, false);
    }
    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        renderer = new PostCardRenderer(this); container = view.findViewById(R.id.sellerPostContainer);
        viewModel = new ViewModelProvider(this, new PostViewModelFactory(requireContext())).get(PostViewModel.class);
        viewModel.getSellerPosts().observe(getViewLifecycleOwner(), this::render);
        viewModel.getSellerPostsHaveMore().observe(getViewLifecycleOwner(), unused -> {
            if (currentPosts != null) render(currentPosts);
        });
        viewModel.getState().observe(getViewLifecycleOwner(), value -> {
            if (value.getStatus() == PostScreenState.Status.ERROR) Toast.makeText(requireContext(), value.getMessage(), Toast.LENGTH_LONG).show();
        });
        view.findViewById(R.id.addPostButton).setOnClickListener(v -> Navigation.findNavController(view)
                .navigate(R.id.action_sellerPostListFragment_to_postEditorFragment));
        view.findViewById(R.id.refreshSellerPostsButton).setOnClickListener(v -> viewModel.refreshSellerPosts());
    }
    @Override public void onResume() { super.onResume(); if (viewModel != null) viewModel.refreshSellerPostPage(true); }
    private void render(List<Post> posts) {
        currentPosts = posts;
        container.removeAllViews();
        if (posts == null || posts.isEmpty()) { container.addView(renderer.message("Bạn chưa có post nào.")); return; }
        for (Post post : posts) container.addView(renderer.card(post, true, v -> open(post)));
        if (viewModel != null && Boolean.TRUE.equals(viewModel.getSellerPostsHaveMore().getValue())) {
            android.widget.Button more = new android.widget.Button(requireContext());
            more.setAllCaps(false); more.setText("Tải thêm bài đăng");
            more.setOnClickListener(v -> viewModel.refreshSellerPostPage(false)); container.addView(more);
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
