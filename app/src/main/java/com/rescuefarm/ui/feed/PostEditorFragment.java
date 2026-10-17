package com.rescuefarm.ui.feed;
import com.rescuefarm.ui.common.EnumLabelAdapter;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.Toast;
import android.widget.TextView;
import android.view.View;
import android.text.Editable;
import android.text.TextWatcher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import com.google.android.material.textfield.TextInputEditText;
import com.rescuefarm.R;
import com.rescuefarm.domain.enums.UrgencyLevel;
import com.rescuefarm.domain.model.Post;
import com.rescuefarm.service.media.CloudinaryUploadUi;
import java.util.Arrays;

public class PostEditorFragment extends Fragment {
    private PostViewModel viewModel; private TextInputEditText title, content, campaign, images, products;
    private Spinner urgency; private String postId = ""; private boolean dirty, binding;
    private TextView imagesStatus;
    private View uploadImagesButton;
    private final ActivityResultLauncher<String> imagePicker = registerForActivityResult(
            new ActivityResultContracts.GetContent(), uri -> CloudinaryUploadUi.upload(this, uri,
                    "post", postId.isEmpty() ? (postId = java.util.UUID.randomUUID().toString()) : postId, imageCount(), uploadImagesButton, imagesStatus, null,
                    url -> appendImageUrl(url)));
    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater,
            @Nullable ViewGroup parent, @Nullable Bundle state) {
        return inflater.inflate(R.layout.fragment_post_editor, parent, false);
    }
    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        title = view.findViewById(R.id.postTitleInput); content = view.findViewById(R.id.postContentInput);
        campaign = view.findViewById(R.id.postCampaignInput); images = view.findViewById(R.id.postImagesInput);
        products = view.findViewById(R.id.postProductsInput); urgency = view.findViewById(R.id.postUrgencySpinner);
        imagesStatus = view.findViewById(R.id.postImagesUploadStatus);
        uploadImagesButton = view.findViewById(R.id.uploadPostImageButton);
        uploadImagesButton.setOnClickListener(v -> {
            if (imageCount() >= 6) {
                Toast.makeText(requireContext(), R.string.post_image_limit_message, Toast.LENGTH_SHORT).show();
            } else imagePicker.launch("image/*");
        });
        view.findViewById(R.id.clearPostImagesButton).setOnClickListener(v -> {
            images.setText(""); updateImageCount(); dirty = true;
        });
        urgency.setAdapter(new EnumLabelAdapter<>(requireContext(), Arrays.asList(UrgencyLevel.values())));
        viewModel = new ViewModelProvider(this, new PostViewModelFactory(requireContext())).get(PostViewModel.class);
        postId = getArguments() == null ? "" : getArguments().getString("postId", "");
        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (!binding) dirty = true;
            }
            @Override public void afterTextChanged(Editable s) { }
        };
        for (TextInputEditText input : new TextInputEditText[]{title, content, campaign, images, products})
            input.addTextChangedListener(watcher);
        boolean[] urgencyInitialized = {false};
        urgency.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent,
                    View selected, int position, long itemId) {
                if (!urgencyInitialized[0]) urgencyInitialized[0] = true;
                else if (!binding) dirty = true;
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        });
        viewModel.getState().observe(getViewLifecycleOwner(), value -> {
            if (value.getStatus() == PostScreenState.Status.POST && !dirty) populate(value.getPost());
            else if (value.getStatus() == PostScreenState.Status.SAVED) {
                Toast.makeText(requireContext(), value.getMessage(), Toast.LENGTH_SHORT).show();
                Navigation.findNavController(view).navigateUp();
            } else if (value.getStatus() == PostScreenState.Status.ERROR) {
                Toast.makeText(requireContext(), value.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
        view.findViewById(R.id.savePostDraftButton).setOnClickListener(v -> save(false));
        view.findViewById(R.id.submitPostButton).setOnClickListener(v -> save(true));
        if (!postId.isEmpty()) {
            viewModel.observePost(postId).observe(getViewLifecycleOwner(), value -> {
                if (value != null && !dirty) populate(value);
            });
            viewModel.loadPost(postId);
        }
    }
    private void populate(Post post) {
        binding = true;
        title.setText(post.getTitle()); content.setText(post.getContent());
        campaign.setText(post.getCampaignId()); images.setText(String.join("\n", post.getImageUrls()));
        updateImageCount();
        products.setText(String.join("\n", post.getLinkedProductIds())); urgency.setSelection(post.getUrgencyLevel().ordinal());
        binding = false;
    }
    private void save(boolean submit) {
        viewModel.savePost(postId, text(campaign), text(title), text(content), text(images),
                text(products), (UrgencyLevel) urgency.getSelectedItem(), submit);
    }
    private void appendImageUrl(String url) {
        if (imageCount() >= 6) {
            Toast.makeText(requireContext(), R.string.post_image_limit_message, Toast.LENGTH_SHORT).show();
            return;
        }
        String current = text(images).trim();
        images.setText(current.isEmpty() ? url : current + "\n" + url);
        updateImageCount(); dirty = true;
    }
    private int imageCount() {
        int count = 0;
        for (String item : text(images).split("[\\n,]+")) if (!item.trim().isEmpty()) count++;
        return count;
    }
    private void updateImageCount() {
        int count = imageCount();
        if (count == 0) imagesStatus.setText(R.string.no_images_selected);
        else imagesStatus.setText(getString(R.string.uploaded_images_count_format, count));
    }
    private static String text(TextInputEditText input) { return input.getText() == null ? "" : input.getText().toString(); }
}
