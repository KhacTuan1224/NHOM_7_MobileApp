package com.rescuefarm.ui.profile;

import android.content.Context;
import androidx.lifecycle.*;
import com.rescuefarm.data.repository.StorefrontRepositoryFactory;

public final class StorefrontViewModelFactory implements ViewModelProvider.Factory {
    private final Context context;
    public StorefrontViewModelFactory(Context context) { this.context = context.getApplicationContext(); }
    @SuppressWarnings("unchecked") public <T extends ViewModel> T create(Class<T> type) {
        if (type != StorefrontViewModel.class) throw new IllegalArgumentException("Unknown ViewModel");
        return (T) new StorefrontViewModel(StorefrontRepositoryFactory.create(context));
    }
}
