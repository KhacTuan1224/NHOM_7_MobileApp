package com.rescuefarm.ui.feed;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import com.rescuefarm.R;
import com.rescuefarm.domain.model.Post;
import java.util.List;

public class FeedFragment extends Fragment {
    private PostViewModel viewModel; private PostCardRenderer renderer;
    private LinearLayout container; private ProgressBar progress; private View loadMore;
    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater,
            @Nullable ViewGroup parent, @Nullable Bundle state) {
        return inflater.inflate(R.layout.fragment_feed, parent, false);
    }
    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        renderer = new PostCardRenderer(this); container = view.findViewById(R.id.feedContainer);
        progress = view.findViewById(R.id.feedProgress); loadMore = view.findViewById(R.id.loadMorePostsButton);
        viewModel = new ViewModelProvider(this, new PostViewModelFactory(requireContext())).get(PostViewModel.class);
        viewModel.getFeed().observe(getViewLifecycleOwner(), this::render);
        viewModel.getHasMore().observe(getViewLifecycleOwner(), more -> loadMore.setVisibility(Boolean.TRUE.equals(more) ? View.VISIBLE : View.GONE));
        viewModel.getState().observe(getViewLifecycleOwner(), value -> {
            progress.setVisibility(value.getStatus() == PostScreenState.Status.LOADING ? View.VISIBLE : View.GONE);
            if (value.getStatus() == PostScreenState.Status.ERROR) Toast.makeText(requireContext(), value.getMessage(), Toast.LENGTH_LONG).show();
        });
        view.findViewById(R.id.refreshFeedButton).setOnClickListener(v -> viewModel.refreshFeed());
        loadMore.setOnClickListener(v -> viewModel.loadNextPage()); viewModel.refreshFeed();
    }
    private void render(List<Post> values) {
        container.removeAllViews();
        if (values == null || values.isEmpty()) { container.addView(renderer.message(getString(R.string.feed_empty_message))); return; }
        for (Post post : values) container.addView(renderer.card(post, false, v -> open(post.getId())));
    }
    private void open(String id) {
        Bundle args = new Bundle(); args.putString("postId", id);
        Navigation.findNavController(requireView()).navigate(R.id.action_feedFragment_to_postDetailFragment, args);
    }
}
