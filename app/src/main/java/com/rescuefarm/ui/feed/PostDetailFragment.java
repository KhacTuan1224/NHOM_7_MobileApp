package com.rescuefarm.ui.feed;
import com.rescuefarm.ui.common.EnumLabels;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import com.bumptech.glide.Glide;
import com.google.android.material.textfield.TextInputEditText;
import com.rescuefarm.R;
import com.rescuefarm.MainActivity;
import com.rescuefarm.ui.navigation.ActorUiPolicy;
import com.rescuefarm.data.repository.PostRepository;
import com.rescuefarm.domain.enums.ReactionType;
import com.rescuefarm.domain.enums.PostStatus;
import com.rescuefarm.domain.model.Comment;
import com.rescuefarm.domain.model.Post;
import java.util.Map;

public class PostDetailFragment extends Fragment {
    private final com.rescuefarm.ui.common.ListPagination commentPages = new com.rescuefarm.ui.common.ListPagination();
    private PostViewModel viewModel; private TextInputEditText commentInput;
    private LinearLayout comments; private TextView reactionSummary;
    private boolean engagementRequested;
    private boolean commentSubmitted;
    private boolean publishedPost;
    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater,
            @Nullable ViewGroup parent, @Nullable Bundle state) {
        return inflater.inflate(R.layout.fragment_post_detail, parent, false);
    }
    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        commentInput = view.findViewById(R.id.commentInput); comments = view.findViewById(R.id.commentContainer);
        reactionSummary = view.findViewById(R.id.reactionSummary);
        viewModel = new ViewModelProvider(this, new PostViewModelFactory(requireContext())).get(PostViewModel.class);
        view.findViewById(R.id.likeButton).setOnClickListener(v -> viewModel.toggleReaction(ReactionType.LIKE));
        view.findViewById(R.id.supportButton).setOnClickListener(v -> viewModel.toggleReaction(ReactionType.SUPPORT));
        view.findViewById(R.id.careButton).setOnClickListener(v -> viewModel.toggleReaction(ReactionType.CARE));
        view.findViewById(R.id.sendCommentButton).setOnClickListener(v -> {
            String content = text(commentInput); commentSubmitted = !content.trim().isEmpty();
            viewModel.addComment(content);
        });
        viewModel.getState().observe(getViewLifecycleOwner(), value -> {
            if (value.getStatus() == PostScreenState.Status.POST) {
                renderPost(view, value.getPost());
                boolean published = value.getPost().getStatus() == PostStatus.PUBLISHED;
                publishedPost = published;
                setInteractionsEnabled(view, publishedPost && !ActorUiPolicy.isSellerReadOnly(this));
                if (!published) reactionSummary.setText("Post đang " + EnumLabels.label(value.getPost().getStatus())
                        + "; tương tác chỉ mở sau khi được duyệt.");
                else if (!engagementRequested) { engagementRequested = true; viewModel.loadEngagement(); }
            }
            else if (value.getStatus() == PostScreenState.Status.ENGAGEMENT) {
                renderEngagement(value.getEngagement());
                if (commentSubmitted) { commentInput.setText(""); commentSubmitted = false; }
            }
            else if (value.getStatus() == PostScreenState.Status.ERROR) {
                commentSubmitted = false;
                Toast.makeText(requireContext(), value.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
        String postId = getArguments() == null ? "" : getArguments().getString("postId", "");
        view.findViewById(R.id.reportPostButton).setOnClickListener(v -> { Bundle b=new Bundle();b.putString("targetType","POST");b.putString("targetId",postId);Navigation.findNavController(v).navigate(R.id.action_postDetailFragment_to_reportFragment,b); });
        applyActorActions(view);
        if (requireActivity() instanceof MainActivity) {
            ((MainActivity) requireActivity()).observeActorRole().observe(getViewLifecycleOwner(), role ->
                    applyActorActions(view));
        }
        viewModel.loadPost(postId);
    }
    private void applyActorActions(View root) {
        root.findViewById(R.id.likeButton).setVisibility(View.GONE);
        root.findViewById(R.id.supportButton).setVisibility(View.GONE);
        root.findViewById(R.id.careButton).setVisibility(View.GONE);
        commentInput.setVisibility(View.GONE);
        root.findViewById(R.id.sendCommentButton).setVisibility(View.GONE);
        root.findViewById(R.id.reportPostButton).setVisibility(View.GONE);
        setInteractionsEnabled(root, false);
    }
    private void renderPost(View root, Post post) {
        ((TextView) root.findViewById(R.id.postDetailTitle)).setText(post.getTitle());
        ((TextView) root.findViewById(R.id.postDetailContent)).setText(post.getContent());
        ((TextView) root.findViewById(R.id.postViewCount)).setText(post.getViewCount() + " lượt xem");
        ((TextView) root.findViewById(R.id.postDetailUrgency)).setText(EnumLabels.label(post.getUrgencyLevel()));
        ImageView image = root.findViewById(R.id.postDetailImage);
        if (post.getImageUrls().isEmpty()) image.setVisibility(View.GONE);
        else { image.setVisibility(View.VISIBLE); Glide.with(this).load(post.getImageUrls().get(0)).centerCrop().into(image); }
    }
    private void renderEngagement(PostRepository.Engagement value) {
        Map<ReactionType, Integer> counts = value.getReactionCounts();
        reactionSummary.setText("Thích " + counts.getOrDefault(ReactionType.LIKE, 0)
                + " • Ủng hộ " + counts.getOrDefault(ReactionType.SUPPORT, 0)
                + " • Quan tâm " + counts.getOrDefault(ReactionType.CARE, 0)
                + (value.getCurrentUserReaction() == null ? "" : " • Bạn: " + EnumLabels.label(value.getCurrentUserReaction())));
        commentPages.render(comments, value.getComments(), page -> {
        comments.removeAllViews();
        if (page.isEmpty()) { comments.addView(textView("Chưa có bình luận.")); return; }
        for (Comment comment : page) {
            if (comment.getStatus() == com.rescuefarm.domain.enums.CommentStatus.HIDDEN) continue;
            LinearLayout row = new LinearLayout(requireContext()); row.setOrientation(LinearLayout.VERTICAL);
            boolean deleted = comment.getStatus() == com.rescuefarm.domain.enums.CommentStatus.DELETED;
            row.addView(textView(deleted ? "Bình luận đã được xóa" : comment.getContent()
                    + (comment.getStatus() == com.rescuefarm.domain.enums.CommentStatus.EDITED ? " · đã chỉnh sửa" : "")));
            comments.addView(row);
        }
        });
    }
    private void editComment(Comment comment) {
        android.widget.EditText input = new android.widget.EditText(requireContext());
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(500)});
        input.setText(comment.getContent());
        new androidx.appcompat.app.AlertDialog.Builder(requireContext()).setTitle("Sửa bình luận").setView(input)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton("Lưu", (dialog, which) -> viewModel.editComment(comment.getId(), input.getText().toString())).show();
    }
    private void setInteractionsEnabled(View root, boolean enabled) {
        root.findViewById(R.id.likeButton).setEnabled(enabled);
        root.findViewById(R.id.supportButton).setEnabled(enabled);
        root.findViewById(R.id.careButton).setEnabled(enabled);
        root.findViewById(R.id.sendCommentButton).setEnabled(enabled);
        commentInput.setEnabled(enabled);
    }
    private TextView textView(String value) {
        TextView text = new TextView(requireContext()); text.setText(value); text.setTextSize(14);
        text.setPadding(8, 12, 8, 12); return text;
    }
    private static String text(TextInputEditText input) { return input.getText() == null ? "" : input.getText().toString(); }
}
