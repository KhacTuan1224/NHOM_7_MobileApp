package com.rescuefarm.ui.auth;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.SavedStateHandleSupport;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.viewmodel.CreationExtras;
import com.rescuefarm.data.repository.AuthRepositoryFactory;

public final class PasswordResetViewModelFactory implements ViewModelProvider.Factory {
    private final Context context;
    public PasswordResetViewModelFactory(Context context) { this.context = context.getApplicationContext(); }
    @NonNull @Override public <T extends ViewModel> T create(@NonNull Class<T> type) { return create(type, new SavedStateHandle()); }
    @NonNull @Override public <T extends ViewModel> T create(@NonNull Class<T> type, @NonNull CreationExtras extras) {
        return create(type, SavedStateHandleSupport.createSavedStateHandle(extras));
    }
    private <T extends ViewModel> T create(Class<T> type, SavedStateHandle saved) {
        if (!type.equals(PasswordResetViewModel.class)) throw new IllegalArgumentException("Unknown ViewModel");
        return type.cast(new PasswordResetViewModel(AuthRepositoryFactory.create(context), saved));
    }
}
