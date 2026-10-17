package com.rescuefarm.data.repository;

import androidx.lifecycle.LiveData;

public interface StorefrontRepository {
    interface Callback { void success(SellerStorefront value); void error(String message); }
    LiveData<SellerStorefront> observe(String sellerId);
    void load(String sellerId, Callback callback);
    void save(SellerStorefront value, Callback callback);
    default void savePaymentQr(String sellerId, String qrUrl, Callback callback) {
        callback.error("Chưa hỗ trợ cập nhật ảnh QR.");
    }
}
