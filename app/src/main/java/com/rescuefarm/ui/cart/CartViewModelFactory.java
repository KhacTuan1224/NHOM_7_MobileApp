package com.rescuefarm.ui.cart;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import com.rescuefarm.data.repository.CartRepositoryFactory;
import com.rescuefarm.data.repository.ProductRepositoryFactory;

public class CartViewModelFactory implements ViewModelProvider.Factory {
    private final Context context;
    public CartViewModelFactory(Context context) { this.context = context.getApplicationContext(); }
    @NonNull @Override public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (!modelClass.isAssignableFrom(CartViewModel.class)) throw new IllegalArgumentException("Unknown ViewModel");
        com.rescuefarm.data.repository.AuthRepository auth = com.rescuefarm.data.repository.AuthRepositoryFactory.create(context);
        if (auth.isAuthenticated() && auth.getCurrentUserId() != null) {
            String guestId = new com.rescuefarm.data.local.preferences.SharedPreferencesGuestSessionRepository(context).getOrCreateGuestId();
            new com.rescuefarm.service.cart.CartMigrationService(context).migrateGuestCartToUser(guestId, auth.getCurrentUserId(), null);
        }
        return (T) new CartViewModel(CartRepositoryFactory.create(context),
                ProductRepositoryFactory.create(context));
    }
}
