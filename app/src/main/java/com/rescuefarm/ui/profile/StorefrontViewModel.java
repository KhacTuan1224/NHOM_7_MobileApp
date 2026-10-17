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
    public void load(String id) { if (Boolean.TRUE.equals(loading.getValue())) return; loading.setValue(true); repository.load(id, callback(false)); }
    public void generatePaymentQr() {
        if (Boolean.TRUE.equals(loading.getValue())) return;
        SellerStorefront current = storefront.getValue();
        if (current == null || !current.acceptsTransfer()) {
            message.setValue("Hãy lưu đủ thông tin ngân hàng trong Hồ sơ người bán trước khi tạo QR."); return;
        }
        loading.setValue(true); message.setValue("Đang tạo mã VietQR và lưu ảnh lên Cloudinary…");
        com.rescuefarm.service.backend.RenderBackendClient.post("/seller/payment-qr", new org.json.JSONObject(),
                new com.rescuefarm.service.backend.RenderBackendClient.Callback() {
            @Override public void onSuccess(org.json.JSONObject response) {
                loading.setValue(false);
                String url = response.optString("qrUrl", "");
                if (!url.startsWith("https://res.cloudinary.com/")) {
                    message.setValue("Máy chủ chưa trả về ảnh QR hợp lệ. Hãy thử lại."); return;
                }
                current.qrUrl = url; storefront.setValue(current);
                message.setValue("Đã tạo mã QR và tự lưu ảnh thành công.");
            }
            @Override public void onError(String error) {
                loading.setValue(false); message.setValue(error);
            }
        });
    }
    private StorefrontRepository.Callback callback(boolean saved) {
        return new StorefrontRepository.Callback() {
            public void success(SellerStorefront value) {
                loading.setValue(false); storefront.setValue(value);
                message.setValue(saved ? "Đã lưu ảnh QR thanh toán." : "");
            }
            public void error(String value) { loading.setValue(false); message.setValue(value); }
        };
    }
}
