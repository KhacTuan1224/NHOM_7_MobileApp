package com.rescuefarm.data.repository;

import android.content.Context;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.rescuefarm.data.remote.firebase.FirebaseConfiguration;
import com.rescuefarm.data.remote.firebase.FirebaseStorefrontRepository;

public final class StorefrontRepositoryFactory {
    private StorefrontRepositoryFactory() { }
    public static StorefrontRepository create(Context context) {
        if (FirebaseConfiguration.isConfigured(context)) return new FirebaseStorefrontRepository();
        return new StorefrontRepository() {
            public LiveData<SellerStorefront> observe(String id) { return new MutableLiveData<>(); }
            public void load(String id, Callback cb) { cb.error("Chưa cấu hình kết nối dữ liệu cửa hàng."); }
            public void save(SellerStorefront value, Callback cb) { cb.error("Chưa cấu hình kết nối dữ liệu cửa hàng."); }
        };
    }
}
