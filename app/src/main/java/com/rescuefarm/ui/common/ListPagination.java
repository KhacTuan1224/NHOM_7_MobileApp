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
    public ListPagination() { this(12); }
    public ListPagination(int pageSize) { window = new PageWindow(pageSize); }
    public void reset() { window.reset(); }
    public void advance() { window.advance(); }
    public ListPagination remote(BooleanSupplier hasMore, BooleanSupplier busy, Runnable load) {
        remoteMore = hasMore; loading = busy; loadMore = load; return this;
    }
    public <T> void render(LinearLayout container, List<T> values, Consumer<List<T>> renderPage) {
        List<T> all = values == null ? java.util.Collections.emptyList() : values;
        renderPage.accept(window.take(all));
        boolean localMore = window.hasMore(all.size());
        if (!localMore && !remoteMore.getAsBoolean()) return;
        // Empty filtered results can still have matches in a later remote page.
        container.setVisibility(View.VISIBLE);
        MaterialButton more = new MaterialButton(container.getContext(), null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle);
        more.setText(loading.getAsBoolean() ? R.string.pagination_loading : R.string.pagination_more);
        more.setTextSize(16); more.setAllCaps(false);
        more.setEnabled(!loading.getAsBoolean());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                container.getOrientation() == LinearLayout.HORIZONTAL
                        ? Math.round(160 * container.getResources().getDisplayMetrics().density) : -1, -2);
        params.topMargin = Math.round(12 * container.getResources().getDisplayMetrics().density);
        container.addView(more, params);
        more.setOnClickListener(v -> {
            if (loading.getAsBoolean()) return;
            window.advance();
            if (localMore) render(container, all, renderPage);
            else { more.setEnabled(false); more.setText(R.string.pagination_loading); loadMore.run(); }
        });
    }
}
