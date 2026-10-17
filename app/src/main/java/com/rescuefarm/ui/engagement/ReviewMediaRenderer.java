package com.rescuefarm.ui.engagement;
import android.app.Dialog;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.*;
import androidx.fragment.app.Fragment;
import com.bumptech.glide.Glide;
import com.rescuefarm.domain.enums.ReviewStatus;
import com.rescuefarm.domain.model.Review;
import java.util.*;

/** Shared public media renderer. Only published reviews expose images. */
public final class ReviewMediaRenderer {
    private ReviewMediaRenderer() { }
    public static List<String> publicImages(Review review) {
        List<String> urls = new ArrayList<>();
        if (review == null || review.getStatus() != ReviewStatus.PUBLISHED) return urls;
        for (String url : review.getImageUrls()) {
            if (urls.size() == 5) break;
            if (url != null && (url.startsWith("https://") || url.startsWith("http://"))) urls.add(url);
        }
        return urls;
    }
    public static void append(Fragment fragment, LinearLayout parent, Review review) {
        List<String> urls = publicImages(review);
        if (urls.isEmpty()) return;
        HorizontalScrollView scroll = new HorizontalScrollView(fragment.requireContext());
        LinearLayout strip = new LinearLayout(fragment.requireContext());
        int size = Math.round(80 * fragment.getResources().getDisplayMetrics().density);
        for (int index = 0; index < urls.size(); index++) {
            final int selected = index;
            ImageView image = new ImageView(fragment.requireContext());
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(size, size);
            params.setMargins(0, 8, 12, 8); strip.addView(image, params);
            image.setContentDescription("Ảnh đánh giá " + (index + 1) + "/" + urls.size());
            Glide.with(fragment).load(urls.get(index)).centerCrop()
                    .placeholder(android.R.drawable.ic_menu_gallery).error(android.R.drawable.ic_menu_report_image).into(image);
            image.setOnClickListener(v -> open(fragment, urls, selected));
        }
        scroll.addView(strip); parent.addView(scroll);
    }
    private static void open(Fragment fragment, List<String> urls, int initial) {
        com.bumptech.glide.RequestManager requests = Glide.with(fragment);
        Dialog dialog = new Dialog(fragment.requireContext(), android.R.style.Theme_Material_Light_NoActionBar);
        LinearLayout root = new LinearLayout(fragment.requireContext()); root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);
        TextView count = new TextView(fragment.requireContext()); count.setTextColor(Color.WHITE); count.setGravity(Gravity.CENTER);
        root.addView(count);
        ImageView image = new ImageView(fragment.requireContext()); image.setAdjustViewBounds(true);
        root.addView(image, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout controls = new LinearLayout(fragment.requireContext());
        Button previous = new Button(fragment.requireContext()), close = new Button(fragment.requireContext()), next = new Button(fragment.requireContext());
        previous.setText("Trước"); close.setText("Đóng"); next.setText("Sau");
        for (Button button : new Button[]{previous, close, next}) controls.addView(button, new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(controls); final int[] position = {initial};
        Runnable render = () -> {
            count.setText("Ảnh " + (position[0] + 1) + "/" + urls.size()); image.setContentDescription(count.getText());
            previous.setEnabled(position[0] > 0); next.setEnabled(position[0] < urls.size() - 1);
            requests.load(urls.get(position[0])).fitCenter()
                    .placeholder(android.R.drawable.ic_menu_gallery).error(android.R.drawable.ic_menu_report_image).into(image);
        };
        previous.setOnClickListener(v -> { position[0]--; render.run(); }); next.setOnClickListener(v -> { position[0]++; render.run(); });
        close.setOnClickListener(v -> dialog.dismiss()); dialog.setContentView(root);
        androidx.lifecycle.Lifecycle lifecycle = fragment.getViewLifecycleOwner().getLifecycle();
        androidx.lifecycle.DefaultLifecycleObserver observer = new androidx.lifecycle.DefaultLifecycleObserver() {
            @Override public void onDestroy(androidx.lifecycle.LifecycleOwner owner) { dialog.dismiss(); }
        };
        lifecycle.addObserver(observer);
        dialog.setOnDismissListener(d -> { requests.clear(image); lifecycle.removeObserver(observer); });
        dialog.show(); dialog.getWindow().setLayout(-1, -1); render.run();
    }
}
