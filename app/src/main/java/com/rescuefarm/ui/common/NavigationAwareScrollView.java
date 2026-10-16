package com.rescuefarm.ui.common;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.ScrollView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class NavigationAwareScrollView extends ScrollView {
    public NavigationAwareScrollView(@NonNull Context context) {
        super(context);
        NavigationScrollInsets.install(this);
    }

    public NavigationAwareScrollView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        NavigationScrollInsets.install(this);
    }

    public NavigationAwareScrollView(@NonNull Context context, @Nullable AttributeSet attrs,
            int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        NavigationScrollInsets.install(this);
    }
}
