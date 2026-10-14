package com.rescuefarm.ui.campaign;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import com.rescuefarm.data.repository.AuthRepositoryFactory;
import com.rescuefarm.data.repository.CampaignRepositoryFactory;
import com.rescuefarm.data.repository.ProductRepositoryFactory;
import com.rescuefarm.service.location.LocationProviderFactory;

public class CampaignViewModelFactory implements ViewModelProvider.Factory {
    private final Context context;
    public CampaignViewModelFactory(Context context) { this.context = context.getApplicationContext(); }
    @NonNull @Override public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (!modelClass.isAssignableFrom(CampaignViewModel.class)) {
            throw new IllegalArgumentException("Unknown ViewModel class: " + modelClass.getName());
        }
        return (T) new CampaignViewModel(CampaignRepositoryFactory.create(context),
                AuthRepositoryFactory.create(context), ProductRepositoryFactory.create(context),
                LocationProviderFactory.create(context));
    }
}
