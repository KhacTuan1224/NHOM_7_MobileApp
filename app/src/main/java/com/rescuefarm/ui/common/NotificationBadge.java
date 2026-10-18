package com.rescuefarm.ui.common;

import android.view.View;
import android.widget.TextView;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.rescuefarm.R;
import com.rescuefarm.ui.engagement.UnreadNotificationsViewModel;

public final class NotificationBadge {
    private NotificationBadge() { }

    public static void bind(Fragment fragment, View button, TextView badge) {
        new ViewModelProvider(fragment.requireActivity()).get(UnreadNotificationsViewModel.class)
                .count().observe(fragment.getViewLifecycleOwner(), value -> {
                    int count = value == null ? 0 : Math.max(0, value);
                    badge.setText(count > 99 ? "99+" : String.valueOf(count));
                    badge.setVisibility(count == 0 ? View.GONE : View.VISIBLE);
                    button.setContentDescription(count == 0
                            ? fragment.getString(R.string.notifications_title)
                            : fragment.getString(R.string.notification_unread_description, count));
                });
    }
}
