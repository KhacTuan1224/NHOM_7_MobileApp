package com.rescuefarm.ui.common;

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

public class NavigationAwareRecyclerView extends RecyclerView {
    public NavigationAwareRecyclerView(@NonNull Context context) {
        super(context);
        NavigationScrollInsets.install(this);
    }

    public NavigationAwareRecyclerView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        NavigationScrollInsets.install(this);
    }

    public NavigationAwareRecyclerView(@NonNull Context context, @Nullable AttributeSet attrs,
            int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        NavigationScrollInsets.install(this);
    }
}
