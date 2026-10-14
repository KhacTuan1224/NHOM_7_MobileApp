package com.rescuefarm.ui.auth;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.rescuefarm.data.local.preferences.SharedPreferencesGuestSessionRepository;
import com.rescuefarm.data.repository.AuthRepositoryFactory;

public class AuthViewModelFactory implements ViewModelProvider.Factory {
    private final Context applicationContext;

    public AuthViewModelFactory(Context context) {
        applicationContext = context.getApplicationContext();
    }

    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (!modelClass.isAssignableFrom(AuthViewModel.class)) {
            throw new IllegalArgumentException("Unsupported ViewModel class: " + modelClass.getName());
        }
        return (T) new AuthViewModel(
                AuthRepositoryFactory.create(applicationContext),
                new SharedPreferencesGuestSessionRepository(applicationContext),
                new com.rescuefarm.service.cart.CartMigrationService(applicationContext)
        );
    }
}
