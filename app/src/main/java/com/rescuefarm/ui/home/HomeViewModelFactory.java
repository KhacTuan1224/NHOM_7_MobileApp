package com.rescuefarm.ui.home;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.viewmodel.CreationExtras;
import androidx.lifecycle.SavedStateHandleSupport;
import com.rescuefarm.data.repository.AuthRepositoryFactory;
import com.rescuefarm.data.repository.CampaignRepositoryFactory;
import com.rescuefarm.data.repository.ProductRepositoryFactory;
import com.rescuefarm.data.repository.UserRepositoryFactory;
import com.rescuefarm.service.location.LocationProviderFactory;

public class HomeViewModelFactory implements ViewModelProvider.Factory {
    private final Context context;
    public HomeViewModelFactory(Context context) { this.context = context.getApplicationContext(); }
    @NonNull @Override public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (!modelClass.isAssignableFrom(HomeViewModel.class)) {
            throw new IllegalArgumentException("Unknown ViewModel class: " + modelClass.getName());
        }
        return (T) new HomeViewModel(ProductRepositoryFactory.create(context),
                CampaignRepositoryFactory.create(context), AuthRepositoryFactory.create(context),
                LocationProviderFactory.create(context), UserRepositoryFactory.create(context),
                new androidx.lifecycle.SavedStateHandle(),
                com.rescuefarm.data.local.preferences.HomeLocationStore.get(context));
    }
    @NonNull @Override public <T extends ViewModel> T create(@NonNull Class<T> modelClass,
            @NonNull CreationExtras extras) {
        if (!modelClass.isAssignableFrom(HomeViewModel.class)) {
            throw new IllegalArgumentException("Unknown ViewModel class: " + modelClass.getName());
        }
        return (T) new HomeViewModel(ProductRepositoryFactory.create(context),
                CampaignRepositoryFactory.create(context), AuthRepositoryFactory.create(context),
                LocationProviderFactory.create(context), UserRepositoryFactory.create(context),
                SavedStateHandleSupport.createSavedStateHandle(extras),
                com.rescuefarm.data.local.preferences.HomeLocationStore.get(context));
    }
}
