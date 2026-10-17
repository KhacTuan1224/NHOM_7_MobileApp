package com.rescuefarm.ui.engagement;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavDeepLinkRequest;
import androidx.navigation.Navigation;
import com.google.android.material.card.MaterialCardView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.messaging.FirebaseMessaging;
import com.rescuefarm.R;
import com.rescuefarm.domain.enums.NotificationType;
import com.rescuefarm.domain.model.Notification;
import com.rescuefarm.service.engagement.NotificationDeepLinkService;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

public class NotificationFragment extends Fragment {
    private EngagementViewModel viewModel;
    private LinearLayout container;
    private TextView emptyText;
    private String userId = "";
    private final FirebaseAuth.AuthStateListener authListener = auth -> {
        String uid = auth.getCurrentUser() == null ? "" : auth.getCurrentUser().getUid();
        if (!uid.equals(userId)) {
            userId = uid;
            if (container != null) container.removeAllViews();
            if (emptyText != null) { emptyText.setVisibility(View.VISIBLE); emptyText.setText(uid.isEmpty() ? "Đăng nhập để xem thông báo." : "Đang tải thông báo…"); }
            if (viewModel != null && !uid.isEmpty()) viewModel.refreshNotifications(uid);
        }
    };
    @Override public void onStart() { super.onStart(); FirebaseAuth.getInstance().addAuthStateListener(authListener); }
    @Override public void onStop() { FirebaseAuth.getInstance().removeAuthStateListener(authListener); super.onStop(); }
    
    private final ActivityResultLauncher<String> permissionLauncher = 
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (!granted) {
                    Toast.makeText(requireContext(), 
                            R.string.notification_permission_denied, Toast.LENGTH_LONG).show();
                } else registerCurrentToken();
            });

    @Nullable
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup parent,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_notifications, parent, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        userId = FirebaseAuth.getInstance().getCurrentUser() == null 
                ? "" 
                : FirebaseAuth.getInstance().getCurrentUser().getUid();
        
        container = view.findViewById(R.id.notificationContainer);
        emptyText = view.findViewById(R.id.emptyNotificationText);
        
        viewModel = new ViewModelProvider(this, 
                new EngagementViewModelFactory(requireContext()))
                .get(EngagementViewModel.class);
        
        viewModel.notifications().observe(getViewLifecycleOwner(), this::renderNotifications);
        
        viewModel.getState().observe(getViewLifecycleOwner(), state -> {
            if (state.getStatus() == EngagementState.Status.ERROR) {
                Toast.makeText(requireContext(), state.getMessage(), Toast.LENGTH_LONG).show();
            } else if (state.getStatus() == EngagementState.Status.SUCCESS
                    && state.getMessage().startsWith("Đã bật thông báo")) {
                Toast.makeText(requireContext(), state.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
        
        view.findViewById(R.id.enableNotificationsButton).setOnClickListener(v -> 
                enableNotifications());
        
        viewModel.refreshNotifications(userId);
    }

    private void enableNotifications() {
        if (Build.VERSION.SDK_INT >= 33 
                && ContextCompat.checkSelfPermission(requireContext(), 
                        Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            return;
        }
        registerCurrentToken();
    }

    private void registerCurrentToken() {
        com.rescuefarm.service.notification.FcmDeviceSyncWorker.refreshNow(requireContext());
        FirebaseMessaging.getInstance().getToken()
                .addOnSuccessListener(token -> viewModel.registerToken(userId, token))
                .addOnFailureListener(e -> Toast.makeText(requireContext(), 
                        R.string.fcm_token_failed, Toast.LENGTH_LONG).show());
    }

    private void renderNotifications(List<Notification> notifications) {
        container.removeAllViews();
        String uid = FirebaseAuth.getInstance().getCurrentUser() == null ? "" : FirebaseAuth.getInstance().getCurrentUser().getUid();
        java.util.ArrayList<Notification> owned = new java.util.ArrayList<>();
        if (notifications != null) for (Notification item : notifications)
            if (item != null && !uid.isEmpty() && uid.equals(item.getUserId())) owned.add(item);
        notifications = owned;
        
        if (notifications == null || notifications.isEmpty()) {
            emptyText.setVisibility(View.VISIBLE);
            container.setVisibility(View.GONE);
            return;
        }
        
        emptyText.setVisibility(View.GONE);
        container.setVisibility(View.VISIBLE);
        
        for (Notification notification : notifications) {
            View card = createNotificationCard(notification);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
            params.setMargins(0, 0, 0, dp(8));
            container.addView(card, params);
        }
    }

    private View createNotificationCard(Notification notification) {
        MaterialCardView card = new MaterialCardView(requireContext());
        card.setRadius(dp(12));
        card.setStrokeWidth(0);
        card.setCardElevation(dp(2));
        card.setCardBackgroundColor(notification.isRead() 
                ? getColor(R.color.rescue_surface)
                : getColor(R.color.rescue_primary_container));
        
        LinearLayout content = new LinearLayout(requireContext());
        content.setOrientation(LinearLayout.HORIZONTAL);
        content.setPadding(dp(12), dp(12), dp(12), dp(12));
        content.setGravity(Gravity.CENTER_VERTICAL);
        
        // Icon
        ImageView icon = new ImageView(requireContext());
        icon.setScaleType(ImageView.ScaleType.CENTER);
        int iconRes = getNotificationIcon(notification.getType());
        icon.setImageResource(iconRes);
        icon.setColorFilter(getColor(R.color.rescue_primary));
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(40), dp(40));
        iconParams.setMarginEnd(dp(12));
        content.addView(icon, iconParams);
        
        // Text content
        LinearLayout textContent = new LinearLayout(requireContext());
        textContent.setOrientation(LinearLayout.VERTICAL);
        
        TextView title = new TextView(requireContext());
        title.setText(notification.getTitle());
        title.setTextSize(14);
        title.setTextColor(getColor(R.color.rescue_on_surface));
        title.setTypeface(notification.isRead() ? null : Typeface.DEFAULT_BOLD);
        textContent.addView(title);
        
        TextView body = new TextView(requireContext());
        body.setText(notification.getBody());
        body.setTextSize(12);
        body.setTextColor(getColor(R.color.rescue_on_surface_variant));
        body.setMaxLines(2);
        LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(-1, -2);
        bodyParams.topMargin = dp(4);
        textContent.addView(body, bodyParams);
        
        // Time
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm", 
                Locale.forLanguageTag("vi-VN"));
        TextView time = new TextView(requireContext());
        time.setText(dateFormat.format(notification.getCreatedAt()));
        time.setTextSize(10);
        time.setTextColor(getColor(R.color.rescue_on_surface_variant));
        LinearLayout.LayoutParams timeParams = new LinearLayout.LayoutParams(-1, -2);
        timeParams.topMargin = dp(4);
        textContent.addView(time, timeParams);
        
        content.addView(textContent, new LinearLayout.LayoutParams(0, -2, 1f));
        
        card.addView(content, new FrameLayout.LayoutParams(-1, -2));
        
        card.setOnClickListener(v -> {
            if (requireActivity() instanceof com.rescuefarm.MainActivity)
                ((com.rescuefarm.MainActivity)requireActivity()).openNotification(notification.getId());
        });
        
        return card;
    }

    private int getNotificationIcon(NotificationType type) {
        if (type == null) return R.drawable.ic_nav_profile;
        
        switch (type) {
            case ORDER:
                return R.drawable.ic_nav_orders;
            case CAMPAIGN:
            case PROMOTION:
                return R.drawable.ic_home_search;
            case MODERATION:
                return R.drawable.ic_nav_community;
            case SYSTEM:
            default:
                return R.drawable.ic_nav_profile;
        }
    }

    private int getColor(int resource) {
        return requireContext().getColor(resource);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
