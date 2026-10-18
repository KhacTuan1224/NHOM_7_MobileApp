package com.rescuefarm.ui.common;

import android.view.View;
import android.widget.LinearLayout;
import com.google.android.material.button.MaterialButton;
import com.rescuefarm.R;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Renders only the requested window. Remote cursors remain owned by the ViewModel. */
public final class ListPagination {
    private final PageWindow window;
    private BooleanSupplier remoteMore = () -> false;
    private BooleanSupplier loading = () -> false;
    private Runnable loadMore = () -> { };
    private boolean compactHomeAction;
    private java.lang.ref.WeakReference<View> previousHomeAction = new java.lang.ref.WeakReference<>(null);
    public ListPagination() { this(12); }
    public ListPagination(int pageSize) { window = new PageWindow(pageSize); }
    public void reset() { window.reset(); }
    public void advance() { window.advance(); }
    public ListPagination compactHomeAction() { compactHomeAction = true; return this; }
    public ListPagination remote(BooleanSupplier hasMore, BooleanSupplier busy, Runnable load) {
        remoteMore = hasMore; loading = busy; loadMore = load; return this;
    }
    public <T> void render(LinearLayout container, List<T> values, Consumer<List<T>> renderPage) {
        View previous = previousHomeAction.get();
        if (previous != null && previous.getParent() instanceof android.view.ViewGroup)
            ((android.view.ViewGroup) previous.getParent()).removeView(previous);
        previousHomeAction.clear();
        List<T> all = values == null ? java.util.Collections.emptyList() : values;
        renderPage.accept(window.take(all));
        boolean localMore = window.hasMore(all.size());
        if (!localMore && !remoteMore.getAsBoolean()) return;
        // Empty filtered results can still have matches in a later remote page.
        container.setVisibility(View.VISIBLE);
        View more;
        if (compactHomeAction) more = new HomeLoadMoreButton(container.getContext());
        else {
            MaterialButton button = new MaterialButton(container.getContext(), null,
                    com.google.android.material.R.attr.materialButtonOutlinedStyle);
            button.setTextSize(16); button.setAllCaps(false);
            more = button;
        }
        setLabel(more, loading.getAsBoolean() ? R.string.pagination_loading : R.string.pagination_more);
        more.setEnabled(!loading.getAsBoolean());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                compactHomeAction ? -2 : container.getOrientation() == LinearLayout.HORIZONTAL
                        ? Math.round(160 * container.getResources().getDisplayMetrics().density) : -1, -2);
        params.gravity = android.view.Gravity.CENTER;
        params.topMargin = Math.round(12 * container.getResources().getDisplayMetrics().density);
        // Keep home arrows outside card grids and horizontal scrollers, centered in the section.
        View anchor = container;
        if (compactHomeAction && container.getParent() instanceof android.widget.HorizontalScrollView)
            anchor = (View) container.getParent();
        if (compactHomeAction && anchor.getParent() instanceof LinearLayout
                && ((LinearLayout) anchor.getParent()).getOrientation() == LinearLayout.VERTICAL) {
            LinearLayout section = (LinearLayout) anchor.getParent();
            section.addView(more, section.indexOfChild(anchor) + 1, params);
            previousHomeAction = new java.lang.ref.WeakReference<>(more);
        } else container.addView(more, params);
        more.setOnClickListener(v -> {
            if (loading.getAsBoolean()) return;
            window.advance();
            if (localMore) render(container, all, renderPage);
            else { more.setEnabled(false); setLabel(more, R.string.pagination_loading); loadMore.run(); }
        });
    }
    private static void setLabel(View view, int label) {
        if (view instanceof HomeLoadMoreButton) ((HomeLoadMoreButton) view).setText(label);
        else ((MaterialButton) view).setText(label);
    }
}
