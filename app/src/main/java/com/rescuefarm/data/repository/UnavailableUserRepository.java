package com.rescuefarm.data.repository;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.rescuefarm.domain.enums.UserRole;
import com.rescuefarm.domain.enums.CustomerType;
import com.rescuefarm.domain.model.Address;
import com.rescuefarm.domain.model.SellerApplication;
import com.rescuefarm.domain.model.User;

public class UnavailableUserRepository implements UserRepository {
    private static final String MESSAGE = "Firebase chưa được cấu hình cho chức năng hồ sơ.";

    @Override public LiveData<User> observeUser(String userId) { return new MutableLiveData<>(); }
    @Override public LiveData<java.util.List<Address>> observeAddresses(String customerId) {
        return new MutableLiveData<>(new java.util.ArrayList<>());
    }
    @Override public LiveData<Address> observeAddress(String customerId, String addressId) {
        return new MutableLiveData<>();
    }
    @Override public void getUser(String userId, UserCallback callback) { unavailable(callback); }
    @Override public void createRegistrationProfile(String userId, String email, String fullName,
            String phone, String avatarUrl, UserRole role, CustomerType customerType,
            String companyName, String taxCode, UserCallback callback) { unavailable(callback); }
    @Override public void updateProfile(String userId, String fullName, String phone, String avatarUrl,
            double latitude, double longitude, UserCallback callback) { unavailable(callback); }
    @Override public void updateSellerProfile(String sellerId, String representativeName, String shopName,
            String shopDescription, String shopAvatarUrl, String address, String bankCode,
            String bankAccountName, String bankAccountNumber, double latitude, double longitude,
            UserCallback callback) {
        unavailable(callback);
    }
    @Override public void getAddresses(String customerId, AddressListCallback callback) {
        callback.onError(ProfileError.UNKNOWN, MESSAGE);
    }
    @Override public void saveAddress(String customerId, Address address, AddressCallback callback) {
        callback.onError(ProfileError.UNKNOWN, MESSAGE);
    }
    @Override public void setDefaultAddress(String customerId, String addressId, ActionCallback callback) {
        callback.onError(ProfileError.UNKNOWN, MESSAGE);
    }
    @Override public void deleteAddress(String customerId, String addressId, ActionCallback callback) {
        callback.onError(ProfileError.UNKNOWN, MESSAGE);
    }
    @Override public void getSellerApplication(String sellerId, ApplicationCallback callback) {
        callback.onError(ProfileError.UNKNOWN, MESSAGE);
    }
    @Override public LiveData<SellerApplication> observeSellerApplication(String sellerId) {
        return new MutableLiveData<>();
    }
    @Override public void submitSellerApplication(String sellerId, SellerApplication application,
            ApplicationCallback callback) { callback.onError(ProfileError.UNKNOWN, MESSAGE); }

    private void unavailable(UserCallback callback) {
        callback.onError(ProfileError.UNKNOWN, MESSAGE);
    }
}
