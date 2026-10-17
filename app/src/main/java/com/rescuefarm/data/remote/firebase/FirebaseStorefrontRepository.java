package com.rescuefarm.data.remote.firebase;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.auth.FirebaseAuth;
import com.rescuefarm.data.repository.SellerStorefront;
import java.util.Map;

public final class FirebaseStorefrontRepository implements com.rescuefarm.data.repository.StorefrontRepository {
    @Override public void savePaymentQr(String sellerId, String qrUrl, Callback callback) {
        String url = text(qrUrl);
        if (!url.isEmpty() && (!url.startsWith("https://") || url.length() > 2048)) {
            callback.error("Ảnh QR phải là đường dẫn HTTPS hợp lệ."); return;
        }
        FirebaseFirestore.getInstance().collection("sellerPaymentProfiles").document(sellerId)
                .update("qrUrl", url, "updatedAt", FieldValue.serverTimestamp())
                .addOnSuccessListener(unused -> load(sellerId, callback))
                .addOnFailureListener(error -> callback.error("Hãy lưu tài khoản ngân hàng trong Hồ sơ người bán trước khi lưu QR."));
    }
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
                .addOnSuccessListener(d -> {
                    SellerStorefront value = SellerStorefront.from(id, d.getData());
                    String currentUid = FirebaseAuth.getInstance().getCurrentUser() == null ? ""
                            : FirebaseAuth.getInstance().getCurrentUser().getUid();
                    if (!id.equals(currentUid)) { callback.success(value); return; }
                    FirebaseFirestore.getInstance().collection("sellerPaymentProfiles").document(id).get()
                            .addOnSuccessListener(payment -> {
                                if (payment.exists()) {
                                    value.bankName = text(payment.get("bankCode"));
                                    value.accountName = text(payment.get("bankAccountName"));
                                    value.bankAccount = text(payment.get("bankAccountNumber"));
                                    value.qrUrl = text(payment.get("qrUrl"));
                                }
                                callback.success(value);
                            }).addOnFailureListener(e -> callback.error("Không tải được cấu hình thanh toán riêng."));
                })
                .addOnFailureListener(e -> callback.error("Không tải được thông tin người bán. Kiểm tra kết nối và quyền truy cập."));
    }
    public void save(SellerStorefront value, Callback callback) {
        try { value.validate(); } catch (IllegalArgumentException e) { callback.error(e.getMessage()); return; }
        Map<String, Object> data = value.toMap(); data.put("updatedAt", FieldValue.serverTimestamp());
        data.put("bankCode", FieldValue.delete()); data.put("bankAccountName", FieldValue.delete());
        data.put("bankAccountNumber", FieldValue.delete()); data.put("qrUrl", FieldValue.delete());
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        com.google.firebase.firestore.WriteBatch batch = db.batch();
        batch.set(db.collection("sellerPublicProfiles").document(value.sellerId), data,
                com.google.firebase.firestore.SetOptions.merge());
        Map<String,Object> payment = new java.util.HashMap<>();
        payment.put("sellerId", value.sellerId); payment.put("bankCode", value.bankName);
        payment.put("bankAccountName", value.accountName); payment.put("bankAccountNumber", value.bankAccount);
        payment.put("qrUrl", value.qrUrl); payment.put("updatedAt", FieldValue.serverTimestamp());
        batch.set(db.collection("sellerPaymentProfiles").document(value.sellerId), payment,
                com.google.firebase.firestore.SetOptions.merge());
        Map<String,Object> privateFields = new java.util.HashMap<>();
        for (String field : new String[]{"shopName", "shopDescription", "address", "bankCode", "bankAccountName", "bankAccountNumber"})
            privateFields.put(field, data.get(field));
        privateFields.put("latitude", value.latitude); privateFields.put("longitude", value.longitude);
        privateFields.put("updatedAt", FieldValue.serverTimestamp());
        batch.update(db.collection("users").document(value.sellerId), privateFields);
        batch.commit()
                .addOnSuccessListener(unused -> { value.configured = true; value.shippingConfigured = true; callback.success(value); })
                .addOnFailureListener(e -> callback.error("Không lưu được. Seller phải được duyệt và có kết nối mạng."));
    }
    private static String text(Object value) { return value instanceof String ? ((String) value).trim() : ""; }
}
