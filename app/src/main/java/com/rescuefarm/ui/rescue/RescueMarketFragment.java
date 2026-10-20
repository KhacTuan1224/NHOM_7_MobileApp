package com.rescuefarm.ui.rescue;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import com.google.android.material.button.MaterialButton;
import com.rescuefarm.R;
import com.rescuefarm.ui.common.NavigationAwareNestedScrollView;
import com.rescuefarm.ui.common.ResponsiveContentLayout;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.LinkedHashMap;
import java.util.Map;
import static com.rescuefarm.ui.rescue.RescueMarketUi.*;

/** Shared transport and lifecycle; each marketplace has its own presentation. */
abstract class RescueMarketFragment extends Fragment {
    protected RescueProgramsViewModel model;
    protected LinearLayout body;
    protected JSONArray offers = new JSONArray();
    private TextView status;
    private ProgressBar progress;
    private MaterialButton more;
    protected final Map<TextView, Long> countdowns = new LinkedHashMap<>();
    private final Map<View, Long> expirations = new LinkedHashMap<>();
    private final Handler ticker = new Handler(Looper.getMainLooper());
    private String owner = "";
    private long refreshedDeadline;
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            long now = System.currentTimeMillis();
            for (Map.Entry<TextView, Long> entry : countdowns.entrySet()) entry.getKey().setText(RescueMarketUi.countdown(entry.getValue(), now));
            long expiredDeadline = 0;
            for (Map.Entry<View, Long> entry : expirations.entrySet()) {
                entry.getKey().setEnabled(entry.getValue() > now);
                if (entry.getValue() <= now) expiredDeadline = Math.max(expiredDeadline, entry.getValue());
            }
            RescueProgramsViewModel.State state = model == null ? null : model.state().getValue();
            if (expiredDeadline > refreshedDeadline && state != null && !state.loading) {
                refreshedDeadline = expiredDeadline; model.refresh();
            }
            onTimeChanged(now); ticker.postDelayed(this, 1000);
        }
    };
    protected abstract String offerType();
    protected abstract String pageTitle();
    protected abstract void buildPage();
    protected abstract void renderOffers();
    protected void onTimeChanged(long now) { }
    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle saved) {
        android.content.Context c = requireContext(); LinearLayout root = col(c); root.setBackgroundColor(0xFFFFFFFF);
        LinearLayout bar = row(c); bar.setPadding(dp(c, 8), dp(c, 4), dp(c, 8), dp(c, 4)); root.addView(bar);
        MaterialButton back = new MaterialButton(c, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        back.setStrokeWidth(0); back.setPadding(0, 0, 0, 0); back.setTextColor(INK);
        back.setText("‹"); back.setTextSize(30); back.setContentDescription("Quay lại"); back.setMinWidth(0);
        bar.addView(back, new LinearLayout.LayoutParams(dp(c, 48), dp(c, 52)));
        back.setOnClickListener(v -> NavHostFragment.findNavController(this).navigateUp());
        TextView title = new TextView(c); title.setText(pageTitle()); title.setTextSize(19); title.setTextColor(INK);
        title.setTypeface(null, android.graphics.Typeface.BOLD); bar.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        MaterialButton mine = new MaterialButton(c, null, com.google.android.material.R.attr.materialButtonOutlinedStyle); mine.setStrokeWidth(0);
        mine.setText("Suất của tôi"); mine.setTextSize(12); mine.setAllCaps(false); bar.addView(mine);
        mine.setOnClickListener(v -> { Bundle args = new Bundle(); args.putString("initialTab", "mine");
            NavHostFragment.findNavController(this).navigate(R.id.rescueHubFragment, args); });
        NavigationAwareNestedScrollView scroll = new NavigationAwareNestedScrollView(c); scroll.setFillViewport(true);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        ResponsiveContentLayout content = new ResponsiveContentLayout(c); content.setOrientation(1);
        content.setPadding(dp(c, 18), 0, dp(c, 18), dp(c, 28));
        scroll.addView(content, new android.widget.FrameLayout.LayoutParams(-1, -2, android.view.Gravity.TOP | android.view.Gravity.CENTER_HORIZONTAL));
        body = content; buildPage();
        progress = new ProgressBar(c, null, android.R.attr.progressBarStyleHorizontal); progress.setIndeterminate(true);
        body.addView(progress, new LinearLayout.LayoutParams(-1, dp(c, 3)));
        status = label(body, "Đang tìm nông sản…", 13, MUTED, false); status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        more = action(body, "Xem thêm", offerType().equals("FLASH") ? RED : PURPLE, () -> model.more());
        MaterialButton refresh = new MaterialButton(c, null, com.google.android.material.R.attr.materialButtonOutlinedStyle); refresh.setStrokeWidth(0);
        refresh.setText("Làm mới danh sách"); refresh.setAllCaps(false); body.addView(refresh);
        refresh.setOnClickListener(v -> model.refresh()); return root;
    }
    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle saved) {
        model = new ViewModelProvider(this).get(RescueProgramsViewModel.class); owner = uid();
        model.state().observe(getViewLifecycleOwner(), state -> {
            progress.setVisibility(state.loading ? View.VISIBLE : View.GONE);
            status.setText(state.message.isEmpty() ? state.loading ? "Đang cập nhật nông sản…" : "Nhận tại điểm • Thanh toán khi nhận" : state.message);
            more.setVisibility(state.value.optBoolean("hasMore") ? View.VISIBLE : View.GONE); more.setEnabled(!state.loading);
            if (!state.loading) {
                offers = state.value.optJSONArray("items"); if (offers == null) offers = new JSONArray();
                countdowns.clear(); expirations.clear(); renderOffers();
                if (offers.length() == 0 && state.message.isEmpty() && state.value.optBoolean("hasMore")) model.more();
            }
        });
        model.load("catalog", "catalog", RescueUi.body("type", offerType()));
    }
    protected void countdown(TextView view, long end, View action) {
        countdowns.put(view, end); view.setText(RescueMarketUi.countdown(end, System.currentTimeMillis()));
        if (action != null) { expirations.put(action, end); action.setEnabled(end > System.currentTimeMillis()); }
    }
    protected void clearCountdowns() { countdowns.clear(); expirations.clear(); }
    protected void openOffer(JSONObject offer) {
        Bundle args = new Bundle(); args.putString("offerId", offer.optString("id")); args.putBoolean("standalone", true);
        NavHostFragment.findNavController(this).navigate(R.id.rescueHubFragment, args);
    }
    private String uid() { com.google.firebase.auth.FirebaseUser user = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser(); return user == null ? "" : user.getUid(); }
    @Override public void onStart() {
        super.onStart(); if (model != null && !owner.equals(uid())) { owner = uid(); model.refresh(); }
        ticker.post(tick);
    }
    @Override public void onStop() { ticker.removeCallbacks(tick); super.onStop(); }
    @Override public void onDestroyView() { ticker.removeCallbacks(tick); countdowns.clear(); expirations.clear(); body = null; super.onDestroyView(); }
}
