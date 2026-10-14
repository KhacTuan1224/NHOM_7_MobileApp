package com.rescuefarm.data.remote.firebase;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FieldValue;
import com.rescuefarm.data.repository.SellerStorefront;
import java.util.Map;

public final class FirebaseStorefrontRepository implements com.rescuefarm.data.repository.StorefrontRepository {
    public androidx.lifecycle.LiveData<SellerStorefront> observe(String id) {
        return new androidx.lifecycle.LiveData<SellerStorefront>() {
            private com.google.firebase.firestore.ListenerRegistration listener;
            @Override protected void onActive() {
                listener = FirebaseFirestore.getInstance().collection("sellerPublicProfiles").document(id)
                    .addSnapshotListener((snapshot, error) -> {
                        if (error != null || snapshot == null) { postValue(null); return; }
                        postValue(SellerStorefront.from(id, snapshot.getData()));
                    });
            }
            @Override protected void onInactive() { if (listener != null) listener.remove(); listener = null; }
        };
    }
    public void load(String id, Callback callback) {
        if (id == null || id.isEmpty()) { callback.error("Chưa xác định người bán."); return; }
        FirebaseFirestore.getInstance().collection("sellerPublicProfiles").document(id).get()
                .addOnSuccessListener(d -> callback.success(SellerStorefront.from(id, d.getData())))
                .addOnFailureListener(e -> callback.error("Không tải được thông tin người bán. Kiểm tra kết nối và quyền truy cập."));
    }
    public void save(SellerStorefront value, Callback callback) {
        try { value.validate(); } catch (IllegalArgumentException e) { callback.error(e.getMessage()); return; }
        Map<String, Object> data = value.toMap(); data.put("updatedAt", FieldValue.serverTimestamp());
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        com.google.firebase.firestore.WriteBatch batch = db.batch();
        batch.set(db.collection("sellerPublicProfiles").document(value.sellerId), data,
                com.google.firebase.firestore.SetOptions.merge());
        Map<String,Object> privateFields = new java.util.HashMap<>();
        for (String field : new String[]{"shopName", "shopDescription", "address", "bankCode", "bankAccountName", "bankAccountNumber"})
            privateFields.put(field, data.get(field));
        privateFields.put("updatedAt", FieldValue.serverTimestamp());
        batch.update(db.collection("users").document(value.sellerId), privateFields);
        batch.commit()
                .addOnSuccessListener(unused -> { value.configured = true; value.shippingConfigured = true; callback.success(value); })
                .addOnFailureListener(e -> callback.error("Không lưu được. Seller phải được duyệt và có kết nối mạng."));
    }
}
