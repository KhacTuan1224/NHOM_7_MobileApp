package com.rescuefarm.ui.profile;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.rescuefarm.data.repository.SellerStorefront;
import com.rescuefarm.data.repository.StorefrontRepository;

public final class StorefrontViewModel extends ViewModel {
    public final MutableLiveData<SellerStorefront> storefront = new MutableLiveData<>();
    public final MutableLiveData<String> message = new MutableLiveData<>("");
    public final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);
    private final StorefrontRepository repository;
    public StorefrontViewModel(StorefrontRepository repository) { this.repository = repository; }
    public androidx.lifecycle.LiveData<SellerStorefront> observe(String id) { return repository.observe(id); }
    public void load(String id) { loading.setValue(true); repository.load(id, callback(false)); }
    public void save(SellerStorefront value) { loading.setValue(true); repository.save(value, callback(true)); }
    private StorefrontRepository.Callback callback(boolean saved) {
        return new StorefrontRepository.Callback() {
            public void success(SellerStorefront value) {
                loading.setValue(false); storefront.setValue(value);
                message.setValue(saved ? "Đã lưu thông tin công khai và thanh toán." : "");
            }
            public void error(String value) { loading.setValue(false); message.setValue(value); }
        };
    }
}
