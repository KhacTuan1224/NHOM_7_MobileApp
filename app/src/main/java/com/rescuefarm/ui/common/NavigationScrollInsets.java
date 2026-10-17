package com.rescuefarm.ui.common;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.view.View;
import android.view.ViewGroup;

import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.rescuefarm.R;

/** Keeps the final scrolled content clear of the app's floating bottom navigation. */
final class NavigationScrollInsets {
    private NavigationScrollInsets() { }

    static void install(View scrollView) {
        final int baseLeft = scrollView.getPaddingLeft();
        final int baseTop = scrollView.getPaddingTop();
        final int baseRight = scrollView.getPaddingRight();
        final int baseBottom = scrollView.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(scrollView, (view, insets) -> {
            Activity activity = findActivity(view.getContext());
            View root = activity == null ? null : activity.findViewById(R.id.mainRoot);
            View nav = activity == null ? null
                    : activity.findViewById(R.id.customerBottomNavigationContainer);

            int systemBottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom;
            // MainActivity has already applied system bars (and IME) to mainRoot.
            int handledSystemBottom = root == null ? 0 : root.getPaddingBottom();
            int unhandledSystemBottom = Math.max(0, systemBottom - handledSystemBottom);
            boolean navVisible = nav != null && nav.getVisibility() == View.VISIBLE;
            int navHeight = navVisible
                    ? Math.max(nav.getHeight(), view.getResources().getDimensionPixelSize(
                            R.dimen.customer_bottom_navigation_height)) : 0;
            int breathingRoom = navVisible
                    ? Math.round(32 * view.getResources().getDisplayMetrics().density) : 0;

            view.setPadding(baseLeft, baseTop, baseRight,
                    baseBottom + unhandledSystemBottom + navHeight + breathingRoom);
            if (view instanceof ViewGroup) ((ViewGroup) view).setClipToPadding(false);
            return insets;
        });
        if (scrollView instanceof RecyclerView) {
            ((RecyclerView) scrollView).addOnScrollListener(new RecyclerView.OnScrollListener() {
                @Override public void onScrolled(@androidx.annotation.NonNull RecyclerView recyclerView,
                        int dx, int dy) {
                    dispatchScroll(recyclerView, dy);
                }
            });
        } else {
            scrollView.setOnScrollChangeListener((view, scrollX, scrollY, oldScrollX, oldScrollY) ->
                    dispatchScroll(view, scrollY - oldScrollY));
        }
        ViewCompat.requestApplyInsets(scrollView);
    }

    private static void dispatchScroll(View view, int deltaY) {
        Activity activity = findActivity(view.getContext());
        if (activity instanceof NavigationScrollListener) {
            ((NavigationScrollListener) activity).onContentScrolled(deltaY,
                    !view.canScrollVertically(-1));
        }
    }

    private static Activity findActivity(Context context) {
        Context current = context;
        while (current instanceof ContextWrapper) {
            if (current instanceof Activity) return (Activity) current;
            current = ((ContextWrapper) current).getBaseContext();
        }
        return current instanceof Activity ? (Activity) current : null;
    }
}
