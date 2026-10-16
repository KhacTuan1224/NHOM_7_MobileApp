package com.rescuefarm.data.remote.firebase;

import androidx.lifecycle.LiveData;
import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.MetadataChanges;
import com.rescuefarm.data.repository.UserRepository;
import com.rescuefarm.domain.enums.AddressType;
import com.rescuefarm.domain.enums.ApplicationStatus;
import com.rescuefarm.domain.enums.CustomerType;
import com.rescuefarm.domain.enums.SellerStatus;
import com.rescuefarm.domain.enums.UserRole;
import com.rescuefarm.domain.model.Address;
import com.rescuefarm.domain.model.Admin;
import com.rescuefarm.domain.model.Customer;
import com.rescuefarm.domain.model.Seller;
import com.rescuefarm.domain.model.SellerApplication;
import com.rescuefarm.domain.model.User;
import com.rescuefarm.service.address.AddressDefaultPolicy;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FirebaseUserRepository implements UserRepository {
    @Override public androidx.lifecycle.LiveData<com.rescuefarm.data.repository.SellerStorefront> observePublicStorefront(String id) {
        return new FirebaseStorefrontRepository().observe(id);
    }
    @Override public void getPublicStorefront(String id, com.rescuefarm.data.repository.StorefrontRepository.Callback callback) {
        new FirebaseStorefrontRepository().load(id, callback);
    }
    private static final String USERS = "users";
    private static final String ADDRESSES = "addresses";
    private static final String SELLER_APPLICATIONS = "sellerApplications";
    private static final String ACTIVE_STATUS = "ACTIVE";
    private static final String PENDING_SELLER_STATUS = "PENDING_APPROVAL";
    private final FirebaseFirestore firestore;

    public FirebaseUserRepository() { firestore = FirebaseFirestore.getInstance(); }

    @Override public LiveData<User> observeUser(String userId) {
        return new LiveData<User>() {
            private ListenerRegistration registration;
            @Override protected void onActive() {
                registration = user(userId).addSnapshotListener((snapshot, exception) -> {
                    if (exception != null || snapshot == null || !snapshot.exists()
                            || !ACTIVE_STATUS.equals(snapshot.getString("status"))) {
                        postValue(null);
                        return;
                    }
                    postValue(mapActiveUser(snapshot));
                });
            }
            @Override protected void onInactive() {
                if (registration != null) { registration.remove(); registration = null; }
            }
        };
    }

    @Override public void getUser(String userId, UserCallback callback) {
        user(userId).get().addOnSuccessListener(snapshot -> {
            if (!snapshot.exists()) {
                callback.onError(ProfileError.NOT_FOUND, "Không tìm thấy hồ sơ users.");
            } else if (!ACTIVE_STATUS.equals(snapshot.getString("status"))) {
                callback.onError(ProfileError.DISABLED, "Tài khoản đã bị vô hiệu hóa.");
            } else {
                User value = mapActiveUser(snapshot);
                if (value == null) callback.onError(ProfileError.UNKNOWN, "Role trong hồ sơ không hợp lệ.");
                else callback.onSuccess(value);
            }
        }).addOnFailureListener(error -> notifyFailure(error, callback::onError));
    }

    @Override public void createRegistrationProfile(String userId, String email, String fullName,
            String phone, String avatarUrl, UserRole role, CustomerType customerType,
            String companyName, String taxCode, UserCallback callback) {
        Map<String, Object> profile = new HashMap<>();
        profile.put("id", userId); profile.put("email", safe(email));
        profile.put("fullName", clean(fullName)); profile.put("phone", clean(phone));
        profile.put("avatarUrl", safe(avatarUrl)); profile.put("role", role.name());
        profile.put("status", ACTIVE_STATUS); profile.put("latitude", 0D); profile.put("longitude", 0D);
        profile.put("createdAt", FieldValue.serverTimestamp());
        profile.put("updatedAt", FieldValue.serverTimestamp());
        if (role == UserRole.CUSTOMER) {
            CustomerType selectedType = customerType == null ? CustomerType.INDIVIDUAL : customerType;
            profile.put("customerType", selectedType.name());
            profile.put("companyName", selectedType == CustomerType.BUSINESS ? clean(companyName) : "");
            profile.put("taxCode", selectedType == CustomerType.BUSINESS ? clean(taxCode) : "");
        }
        if (role == UserRole.SELLER) profile.put("sellerStatus", PENDING_SELLER_STATUS);
        user(userId).set(profile).addOnSuccessListener(unused -> getUser(userId, callback))
                .addOnFailureListener(error -> notifyFailure(error, callback::onError));
    }

    @Override public void updateProfile(String userId, String fullName, String phone, String avatarUrl,
            double latitude, double longitude, UserCallback callback) {
        Map<String, Object> fields = new HashMap<>();
        fields.put("fullName", clean(fullName)); fields.put("phone", clean(phone));
        fields.put("avatarUrl", clean(avatarUrl)); fields.put("latitude", latitude);
        fields.put("longitude", longitude); fields.put("updatedAt", FieldValue.serverTimestamp());
        user(userId).get().addOnSuccessListener(previous -> user(userId).update(fields)
                .addOnSuccessListener(unused -> {
                    com.rescuefarm.service.media.CloudinaryAssetCleanup.deleteRemoved(
                            previous.getString("avatarUrl"), avatarUrl);
                    getUser(userId, callback);
                }).addOnFailureListener(error -> notifyFailure(error, callback::onError)))
                .addOnFailureListener(error -> notifyFailure(error, callback::onError));
    }

    @Override public void updateSellerProfile(String sellerId, String representativeName, String shopName,
            String shopDescription, String shopAvatarUrl, String address, String bankCode,
            String bankAccountName, String bankAccountNumber, double latitude, double longitude,
            UserCallback callback) {
        Map<String, Object> fields = new HashMap<>();
        fields.put("representativeName", clean(representativeName));
        fields.put("shopName", clean(shopName));
        fields.put("shopDescription", clean(shopDescription));
        fields.put("shopAvatarUrl", clean(shopAvatarUrl));
        fields.put("address", clean(address));
        fields.put("bankCode", clean(bankCode));
        fields.put("bankAccountName", clean(bankAccountName));
        fields.put("bankAccountNumber", clean(bankAccountNumber));
        fields.put("latitude", latitude); fields.put("longitude", longitude);
        fields.put("updatedAt", FieldValue.serverTimestamp());
        Map<String,Object> publicProfile = new HashMap<>();
        publicProfile.put("sellerId", sellerId); publicProfile.put("shopName", clean(shopName));
        publicProfile.put("shopDescription", clean(shopDescription));
        publicProfile.put("shopAvatarUrl", clean(shopAvatarUrl)); publicProfile.put("address", clean(address));
        publicProfile.put("latitude", latitude); publicProfile.put("longitude", longitude);
        publicProfile.put("bankCode", FieldValue.delete());
        publicProfile.put("bankAccountName", FieldValue.delete());
        publicProfile.put("bankAccountNumber", FieldValue.delete());
        publicProfile.put("qrUrl", FieldValue.delete());
        publicProfile.put("updatedAt", FieldValue.serverTimestamp());
        Map<String,Object> paymentProfile = new HashMap<>();
        paymentProfile.put("sellerId", sellerId); paymentProfile.put("bankCode", clean(bankCode));
        paymentProfile.put("bankAccountName", clean(bankAccountName));
        paymentProfile.put("bankAccountNumber", clean(bankAccountNumber));
        paymentProfile.put("updatedAt", FieldValue.serverTimestamp());
        user(sellerId).get().addOnSuccessListener(previous -> {
            com.google.firebase.firestore.WriteBatch batch = firestore.batch();
            batch.update(user(sellerId), fields);
            batch.set(firestore.collection("sellerPublicProfiles").document(sellerId), publicProfile, com.google.firebase.firestore.SetOptions.merge());
            batch.set(firestore.collection("sellerPaymentProfiles").document(sellerId), paymentProfile, com.google.firebase.firestore.SetOptions.merge());
            batch.commit().addOnSuccessListener(unused -> {
                    com.rescuefarm.service.media.CloudinaryAssetCleanup.deleteRemoved(
                            previous.getString("shopAvatarUrl"), shopAvatarUrl);
                    getUser(sellerId, callback);
                }).addOnFailureListener(error -> notifyFailure(error, callback::onError));
        }).addOnFailureListener(error -> notifyFailure(error, callback::onError));
    }

    @Override public void getAddresses(String customerId, AddressListCallback callback) {
        user(customerId).collection(ADDRESSES).get().addOnSuccessListener(snapshot -> {
            List<Address> values = new ArrayList<>();
            for (DocumentSnapshot document : snapshot.getDocuments()) {
                Address address = mapAddress(document, customerId);
                if (address != null) values.add(address);
            }
            values.sort((left, right) -> Boolean.compare(right.isDefault(), left.isDefault()));
            callback.onSuccess(values);
        }).addOnFailureListener(error -> notifyFailure(error, callback::onError));
    }

    @Override public LiveData<List<Address>> observeAddresses(String customerId) {
        return new LiveData<List<Address>>() {
            private ListenerRegistration registration;
            @Override protected void onActive() {
                registration = user(customerId).collection(ADDRESSES)
                        .addSnapshotListener((snapshot, exception) -> {
                            if (exception != null || snapshot == null) return;
                            List<Address> values = new ArrayList<>();
                            for (DocumentSnapshot document : snapshot.getDocuments()) {
                                Address address = mapAddress(document, customerId);
                                if (address != null) values.add(address);
                            }
                            values.sort((left, right) -> Boolean.compare(
                                    right.isDefault(), left.isDefault()));
                            postValue(values);
                        });
            }
            @Override protected void onInactive() {
                if (registration != null) { registration.remove(); registration = null; }
            }
        };
    }

    @Override public LiveData<Address> observeAddress(String customerId, String addressId) {
        return new LiveData<Address>() {
            private ListenerRegistration registration;
            @Override protected void onActive() {
                registration = user(customerId).collection(ADDRESSES).document(addressId)
                        .addSnapshotListener((snapshot, exception) -> {
                            if (exception != null || snapshot == null) return;
                            postValue(snapshot.exists() ? mapAddress(snapshot, customerId) : null);
                        });
            }
            @Override protected void onInactive() {
                if (registration != null) { registration.remove(); registration = null; }
            }
        };
    }

    @Override public void saveAddress(String customerId, Address address, AddressCallback callback) {
        DocumentReference userRef = user(customerId);
        DocumentReference addressRef = clean(address.getId()).isEmpty()
                ? userRef.collection(ADDRESSES).document()
                : userRef.collection(ADDRESSES).document(address.getId());
        Address saved = copyAddress(addressRef.getId(), customerId, address, address.isDefault());
        firestore.runTransaction(transaction -> {
            DocumentSnapshot owner = transaction.get(userRef);
            if (!owner.exists()) throw new IllegalStateException("PROFILE_NOT_FOUND");
            String oldDefaultId = owner.getString("defaultAddressId");
            boolean makeDefault = AddressDefaultPolicy.shouldMakeDefault(
                    saved.isDefault(), oldDefaultId, addressRef.getId());
            Address value = copyAddress(addressRef.getId(), customerId, saved, makeDefault);
            if (makeDefault && oldDefaultId != null && !oldDefaultId.isEmpty()
                    && !oldDefaultId.equals(addressRef.getId())) {
                transaction.update(userRef.collection(ADDRESSES).document(oldDefaultId),
                        "isDefault", false,
                        "updatedAt", FieldValue.serverTimestamp());
            }
            transaction.set(addressRef, addressMap(value));
            if (makeDefault) transaction.update(userRef,
                    "defaultAddressId", addressRef.getId(),
                    "updatedAt", FieldValue.serverTimestamp());
            return value;
        }).addOnSuccessListener(callback::onSuccess)
                .addOnFailureListener(error -> notifyFailure(error, callback::onError));
    }

    @Override public void setDefaultAddress(String customerId, String addressId, ActionCallback callback) {
        DocumentReference userRef = user(customerId);
        DocumentReference nextRef = userRef.collection(ADDRESSES).document(addressId);
        firestore.runTransaction(transaction -> {
            DocumentSnapshot owner = transaction.get(userRef);
            DocumentSnapshot next = transaction.get(nextRef);
            if (!owner.exists() || !next.exists()) throw new IllegalStateException("ADDRESS_NOT_FOUND");
            String oldDefaultId = owner.getString("defaultAddressId");
            if (oldDefaultId != null && !oldDefaultId.isEmpty() && !oldDefaultId.equals(addressId)) {
                transaction.update(userRef.collection(ADDRESSES).document(oldDefaultId),
                        "isDefault", false, "updatedAt", FieldValue.serverTimestamp());
            }
            transaction.update(nextRef, "isDefault", true, "updatedAt", FieldValue.serverTimestamp());
            transaction.update(userRef, "defaultAddressId", addressId,
                    "updatedAt", FieldValue.serverTimestamp());
            return null;
        }).addOnSuccessListener(unused -> callback.onSuccess())
                .addOnFailureListener(error -> notifyFailure(error, callback::onError));
    }

    @Override public void deleteAddress(String customerId, String addressId, ActionCallback callback) {
        DocumentReference reference = user(customerId).collection(ADDRESSES).document(addressId);
        reference.get().addOnSuccessListener(snapshot -> {
            if (!snapshot.exists()) callback.onError(ProfileError.NOT_FOUND, "Địa chỉ không còn tồn tại.");
            else if (Boolean.TRUE.equals(snapshot.getBoolean("isDefault"))) callback.onError(
                    ProfileError.CONFLICT, "Hãy chọn địa chỉ mặc định khác trước khi xóa.");
            else reference.delete().addOnSuccessListener(unused -> callback.onSuccess())
                    .addOnFailureListener(error -> notifyFailure(error, callback::onError));
        }).addOnFailureListener(error -> notifyFailure(error, callback::onError));
    }

    @Override public void getSellerApplication(String sellerId, ApplicationCallback callback) {
        firestore.collection(SELLER_APPLICATIONS).document(sellerId).get().addOnSuccessListener(snapshot -> {
            if (!snapshot.exists()) callback.onError(ProfileError.NOT_FOUND, "Bạn chưa gửi hồ sơ người bán.");
            else callback.onSuccess(mapApplication(snapshot));
        }).addOnFailureListener(error -> notifyFailure(error, callback::onError));
    }

    @Override public LiveData<SellerApplication> observeSellerApplication(String sellerId) {
        return new LiveData<SellerApplication>() {
            private ListenerRegistration registration;
            @Override protected void onActive() {
                registration = firestore.collection(SELLER_APPLICATIONS).document(sellerId)
                        .addSnapshotListener(MetadataChanges.INCLUDE, (snapshot, exception) -> {
                            if (exception != null || snapshot == null) return;
                            postValue(snapshot.exists() ? mapApplication(snapshot) : null);
                        });
            }
            @Override protected void onInactive() {
                if (registration != null) { registration.remove(); registration = null; }
            }
        };
    }

    @Override public void submitSellerApplication(String sellerId, SellerApplication application,
            ApplicationCallback callback) {
        DocumentReference reference = firestore.collection(SELLER_APPLICATIONS).document(sellerId);
        String[] previousProofUrl = {""};
        firestore.runTransaction(transaction -> {
            DocumentSnapshot existing = transaction.get(reference);
            String status = existing.getString("status");
            if ("PENDING".equals(status) || "APPROVED".equals(status)) {
                throw new IllegalStateException("APPLICATION_CONFLICT");
            }
            previousProofUrl[0] = safe(existing.getString("proofImageUrl"));
            Map<String, Object> data = new HashMap<>();
            data.put("id", sellerId); data.put("sellerId", sellerId);
            data.put("representativeName", clean(application.getRepresentativeName()));
            data.put("shopName", clean(application.getShopName()));
            data.put("address", clean(application.getAddress()));
            data.put("proofImageUrl", clean(application.getProofImageUrl()));
            data.put("status", ApplicationStatus.PENDING.name()); data.put("rejectionReason", "");
            data.put("submittedAt", FieldValue.serverTimestamp());
            data.put("updatedAt", FieldValue.serverTimestamp());
            transaction.set(reference, data);
            return null;
        }).addOnSuccessListener(unused -> {
            com.rescuefarm.service.media.CloudinaryAssetCleanup.deleteRemoved(
                    previousProofUrl[0], application.getProofImageUrl());
            getSellerApplication(sellerId, callback);
        })
                .addOnFailureListener(error -> notifyFailure(error, callback::onError));
    }

    private User mapActiveUser(DocumentSnapshot snapshot) {
        UserRole role = enumValue(UserRole.class, snapshot.getString("role"), null);
        if (role == null) return null;
        String id = snapshot.getId(); String email = safe(snapshot.getString("email"));
        String fullName = fallback(snapshot.getString("fullName"), "Người dùng RescueFarm");
        User value;
        if (role == UserRole.ADMIN) value = new Admin(id, email, fullName);
        else if (role == UserRole.SELLER) {
            Seller seller = new Seller(id, email, fullName,
                    fallback(snapshot.getString("shopName"), fullName),
                    enumValue(SellerStatus.class, snapshot.getString("sellerStatus"), SellerStatus.PENDING_APPROVAL));
            seller.updateSellerProfile(snapshot.getString("representativeName"), snapshot.getString("shopName"),
                    snapshot.getString("shopDescription"), snapshot.getString("shopAvatarUrl"),
                    snapshot.getString("address"));
            seller.updateBankDetails(snapshot.getString("bankCode"), snapshot.getString("bankAccountName"),
                    snapshot.getString("bankAccountNumber"));
            value = seller;
        } else {
            Customer customer = new Customer(id, email, fullName,
                    enumValue(CustomerType.class, snapshot.getString("customerType"), CustomerType.INDIVIDUAL));
            customer.updateCustomerDetails(customer.getCustomerType(), snapshot.getString("companyName"),
                    snapshot.getString("taxCode"));
            value = customer;
        }
        value.updateProfile(fullName, snapshot.getString("phone"), snapshot.getString("avatarUrl"),
                number(snapshot, "latitude"), number(snapshot, "longitude"), date(snapshot, "updatedAt"));
        return value;
    }

    private Address mapAddress(DocumentSnapshot snapshot, String customerId) {
        try {
            return new Address(snapshot.getId(), customerId, snapshot.getString("receiverName"),
                    snapshot.getString("receiverPhone"), snapshot.getString("province"),
                    snapshot.getString("district"), snapshot.getString("ward"), snapshot.getString("street"),
                    number(snapshot, "latitude"), number(snapshot, "longitude"),
                    enumValue(AddressType.class, snapshot.getString("type"), AddressType.HOME),
                    Boolean.TRUE.equals(snapshot.getBoolean("isDefault")));
        } catch (IllegalArgumentException exception) { return null; }
    }

    private SellerApplication mapApplication(DocumentSnapshot snapshot) {
        return SellerApplication.restore(snapshot.getId(), snapshot.getString("sellerId"),
                snapshot.getString("representativeName"), snapshot.getString("shopName"),
                snapshot.getString("address"), snapshot.getString("proofImageUrl"),
                enumValue(ApplicationStatus.class, snapshot.getString("status"), ApplicationStatus.DRAFT),
                snapshot.getString("rejectionReason"), date(snapshot, "submittedAt"));
    }

    private Map<String, Object> addressMap(Address address) {
        Map<String, Object> data = new HashMap<>();
        data.put("id", address.getId()); data.put("customerId", address.getCustomerId());
        data.put("receiverName", address.getReceiverName()); data.put("receiverPhone", address.getReceiverPhone());
        data.put("province", address.getProvince()); data.put("district", address.getDistrict());
        data.put("ward", address.getWard()); data.put("street", address.getStreet());
        data.put("latitude", address.getLatitude()); data.put("longitude", address.getLongitude());
        data.put("type", address.getType().name()); data.put("isDefault", address.isDefault());
        data.put("updatedAt", FieldValue.serverTimestamp());
        return data;
    }

    private Address copyAddress(String id, String customerId, Address source, boolean isDefault) {
        return new Address(id, customerId, source.getReceiverName(), source.getReceiverPhone(),
                source.getProvince(), source.getDistrict(), source.getWard(), source.getStreet(),
                source.getLatitude(), source.getLongitude(), source.getType(), isDefault);
    }

    private DocumentReference user(String userId) { return firestore.collection(USERS).document(userId); }

    private void notifyFailure(Exception error, ErrorConsumer consumer) {
        if (error instanceof IllegalStateException) {
            String code = error.getMessage();
            if ("PROFILE_NOT_FOUND".equals(code) || "ADDRESS_NOT_FOUND".equals(code)) {
                consumer.accept(ProfileError.NOT_FOUND, "Không tìm thấy dữ liệu cần cập nhật."); return;
            }
            if ("APPLICATION_CONFLICT".equals(code)) {
                consumer.accept(ProfileError.CONFLICT, "Hồ sơ đang chờ duyệt hoặc đã được phê duyệt."); return;
            }
        }
        if (error instanceof FirebaseNetworkException || (error instanceof FirebaseFirestoreException
                && ((FirebaseFirestoreException) error).getCode() == FirebaseFirestoreException.Code.UNAVAILABLE)) {
            consumer.accept(ProfileError.NETWORK, "Không thể kết nối Firestore."); return;
        }
        if (error instanceof FirebaseFirestoreException
                && ((FirebaseFirestoreException) error).getCode() == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
            consumer.accept(ProfileError.PERMISSION_DENIED, "Bạn không có quyền thực hiện thao tác này."); return;
        }
        consumer.accept(ProfileError.UNKNOWN, "Không thể đọc hoặc ghi dữ liệu hồ sơ.");
    }

    private static double number(DocumentSnapshot snapshot, String field) {
        Double value = snapshot.getDouble(field); return value == null ? 0D : value;
    }
    private static Date date(DocumentSnapshot snapshot, String field) {
        Timestamp value = snapshot.getTimestamp(field); return value == null ? new Date() : value.toDate();
    }
    private static String safe(String value) { return value == null ? "" : value; }
    private static String clean(String value) { return safe(value).trim(); }
    private static String fallback(String value, String fallback) { return clean(value).isEmpty() ? fallback : clean(value); }
    private static <T extends Enum<T>> T enumValue(Class<T> type, String value, T fallback) {
        try { return Enum.valueOf(type, value == null ? "" : value); }
        catch (IllegalArgumentException exception) { return fallback; }
    }
    private interface ErrorConsumer { void accept(ProfileError error, String message); }
}
