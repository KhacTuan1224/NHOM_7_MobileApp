package com.rescuefarm.service.media;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;

/** Small UI adapter shared by image upload forms. */
public final class CloudinaryUploadUi {
    private CloudinaryUploadUi() { }

    public static void upload(Fragment fragment, Uri uri, String assetType, String resourceId, int slot, View progressView,
                              TextView statusView, ImageView preview, Result callback) {
        if (uri == null || !fragment.isAdded()) return;
        Context context = fragment.requireContext();
        View boundRoot = fragment.getView();
        String uploader = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser() == null ? ""
                : com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser().getUid();
        progressView.setEnabled(false);
        if (statusView != null) statusView.setText("Đang tải ảnh lên…");
        new CloudinaryMediaStorage(context).uploadImage(uri.toString(), assetType, resourceId, slot).whenComplete((url, error) ->
                new Handler(Looper.getMainLooper()).post(() -> {
                    String current = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser() == null ? ""
                            : com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser().getUid();
                    if (!fragment.isAdded() || fragment.getView() != boundRoot || !uploader.equals(current)) return;
                    progressView.setEnabled(true);
                    if (error != null) {
                        String message = error.getCause() != null ? error.getCause().getMessage() : error.getMessage();
                        if (statusView != null) statusView.setText(com.rescuefarm.R.string.image_upload_failed_existing_kept);
                        Toast.makeText(fragment.requireContext(), message, Toast.LENGTH_LONG).show();
                        return;
                    }
                    callback.onUploaded(url);
                    if (preview != null) Glide.with(fragment).load(url).centerCrop().into(preview);
                    if (statusView != null) statusView.setText(com.rescuefarm.R.string.image_upload_save_pending);
                }));
    }

    public interface Result { void onUploaded(String secureUrl); }
}
