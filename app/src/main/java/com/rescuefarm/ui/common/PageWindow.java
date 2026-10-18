package com.rescuefarm.ui.common;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A display window independent of the remote cursor and of realtime list updates. */
public final class PageWindow {
    private final int pageSize;
    private int visible;
    public PageWindow(int pageSize) {
        if (pageSize < 1) throw new IllegalArgumentException("pageSize must be positive");
        this.pageSize = pageSize; visible = pageSize;
    }
    public void reset() { visible = pageSize; }
    public void advance() { visible += pageSize; }
    public boolean hasMore(int total) { return total > visible; }
    public <T> List<T> take(List<T> values) {
        if (values == null) return Collections.emptyList();
        return new ArrayList<>(values.subList(0, Math.min(visible, values.size())));
    }
}
