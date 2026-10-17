package com.rescuefarm.data.repository;

import androidx.lifecycle.LiveData;

import com.rescuefarm.domain.enums.UserRole;
import com.rescuefarm.domain.enums.CustomerType;
import com.rescuefarm.domain.model.Address;
import com.rescuefarm.domain.model.SellerApplication;
import com.rescuefarm.domain.model.User;

import java.util.List;

public interface UserRepository {
    default LiveData<SellerStorefront> observePublicStorefront(String sellerId) {
        return new androidx.lifecycle.MutableLiveData<>();
    }
    default void getPublicStorefront(String sellerId, StorefrontRepository.Callback callback) {
        callback.error("Chưa kết nối dữ liệu cửa hàng.");
    }
    enum ProfileError {
        NOT_FOUND,
        DISABLED,
        PERMISSION_DENIED,
        CONFLICT,
        VALIDATION,
        NETWORK,
        UNKNOWN
    }

    interface UserCallback {
        void onSuccess(User user);
        void onError(ProfileError error, String message);
    }

    interface AddressListCallback {
        void onSuccess(List<Address> addresses);
        void onError(ProfileError error, String message);
    }

    interface AddressCallback {
        void onSuccess(Address address);
        void onError(ProfileError error, String message);
    }

    interface ApplicationCallback {
        void onSuccess(SellerApplication application);
        void onError(ProfileError error, String message);
    }

    interface ActionCallback {
        void onSuccess();
        void onError(ProfileError error, String message);
    }

    LiveData<User> observeUser(String userId);
    LiveData<List<Address>> observeAddresses(String customerId);
    LiveData<Address> observeAddress(String customerId, String addressId);
    void getUser(String userId, UserCallback callback);
    void createRegistrationProfile(
            String userId,
            String email,
            String fullName,
            String phone,
            String avatarUrl,
            UserRole role,
            CustomerType customerType,
            String companyName,
            String taxCode,
            UserCallback callback
    );
    void updateProfile(
            String userId,
            String fullName,
            String phone,
            String avatarUrl,
            double latitude,
            double longitude,
            UserCallback callback
    );
    default void updateBusinessProfile(String userId, String fullName, String phone, String avatarUrl,
            double latitude, double longitude, String companyName, String taxCode, UserCallback callback) {
        callback.onError(ProfileError.VALIDATION, "Chưa hỗ trợ chỉnh sửa hồ sơ doanh nghiệp.");
    }
    void updateSellerProfile(
            String sellerId,
            String representativeName,
            String shopName,
            String shopDescription,
            String shopAvatarUrl,
            String address,
            String bankCode,
            String bankAccountName,
            String bankAccountNumber,
            double latitude,
            double longitude,
            UserCallback callback
    );
    void getAddresses(String customerId, AddressListCallback callback);
    void saveAddress(String customerId, Address address, AddressCallback callback);
    void setDefaultAddress(String customerId, String addressId, ActionCallback callback);
    void deleteAddress(String customerId, String addressId, ActionCallback callback);
    void getSellerApplication(String sellerId, ApplicationCallback callback);
    LiveData<SellerApplication> observeSellerApplication(String sellerId);
    void submitSellerApplication(
            String sellerId,
            SellerApplication application,
            ApplicationCallback callback
    );
}
