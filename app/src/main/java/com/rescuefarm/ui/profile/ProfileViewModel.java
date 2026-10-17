package com.rescuefarm.ui.profile;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.rescuefarm.data.repository.AuthRepository;
import com.rescuefarm.data.repository.UserRepository;
import com.rescuefarm.domain.enums.AddressType;
import com.rescuefarm.domain.model.Address;
import com.rescuefarm.domain.model.SellerApplication;
import com.rescuefarm.domain.model.User;
import com.rescuefarm.service.location.LocationProvider;

public class ProfileViewModel extends ViewModel {
    private final AuthRepository authRepository;
    private final UserRepository userRepository;
    private final LocationProvider locationProvider;
    private final MutableLiveData<ProfileScreenState> state =
            new MutableLiveData<>(ProfileScreenState.idle());

    public ProfileViewModel(AuthRepository authRepository, UserRepository userRepository,
            LocationProvider locationProvider) {
        this.authRepository = authRepository;
        this.userRepository = userRepository;
        this.locationProvider = locationProvider;
    }

    public LiveData<ProfileScreenState> getState() { return state; }
    public LiveData<User> observeProfile() {
        String userId = authRepository.getCurrentUserId();
        return userId == null ? new MutableLiveData<>() : userRepository.observeUser(userId);
    }
    public LiveData<java.util.List<Address>> observeAddresses() {
        String userId = authRepository.getCurrentUserId();
        return userId == null ? new MutableLiveData<>(java.util.Collections.emptyList())
                : userRepository.observeAddresses(userId);
    }
    public LiveData<Address> observeAddress(String addressId) {
        String userId = authRepository.getCurrentUserId();
        return userId == null || addressId == null || addressId.trim().isEmpty()
                ? new MutableLiveData<>() : userRepository.observeAddress(userId, addressId);
    }
    public LiveData<SellerApplication> observeSellerApplication() {
        String userId = authRepository.getCurrentUserId();
        return userId == null ? new MutableLiveData<>()
                : userRepository.observeSellerApplication(userId);
    }
    public boolean isAuthenticated() { return authRepository.isAuthenticated(); }
    public LiveData<com.rescuefarm.data.repository.SellerStorefront> observeStorefront() {
        String id = authRepository.getCurrentUserId();
        return id == null ? new MutableLiveData<>() : userRepository.observePublicStorefront(id);
    }
    public String getCurrentUserId() { return authRepository.getCurrentUserId(); }

    public void signOut() { authRepository.signOut(); }
    public void signOut(AuthRepository.ActionCallback callback) {
        state.setValue(ProfileScreenState.loading());
        authRepository.signOut(callback);
    }

    public void loadProfile() {
        String userId = requireUserId(); if (userId == null) return;
        state.setValue(ProfileScreenState.loading());
        userRepository.getUser(userId, userCallback());
    }

    public void updateProfile(String fullName, String phone, String avatarUrl,
            double latitude, double longitude) {
        String validation = ProfileValidator.validateProfile(fullName, phone);
        if (validation != null) { state.setValue(ProfileScreenState.error(validation)); return; }
        String userId = requireUserId(); if (userId == null) return;
        state.setValue(ProfileScreenState.loading());
        userRepository.updateProfile(userId, fullName, phone, avatarUrl, latitude, longitude,
                new UserRepository.UserCallback() {
                    @Override public void onSuccess(User user) {
                        state.postValue(ProfileScreenState.saved("Đã cập nhật hồ sơ."));
                    }
                    @Override public void onError(UserRepository.ProfileError error, String message) {
                        state.postValue(ProfileScreenState.error(message));
                    }
                });
    }

    public void updateBusinessProfile(String fullName, String phone, String avatarUrl,
            double latitude, double longitude, String companyName, String taxCode) {
        String validation = ProfileValidator.validateProfile(fullName, phone);
        if (validation == null) validation = ProfileValidator.validateBusiness(companyName, taxCode);
        if (validation != null) { state.setValue(ProfileScreenState.error(validation)); return; }
        String userId = requireUserId(); if (userId == null) return;
        state.setValue(ProfileScreenState.loading());
        userRepository.updateBusinessProfile(userId, fullName, phone, avatarUrl, latitude, longitude, companyName, taxCode,
                new UserRepository.UserCallback() {
                    @Override public void onSuccess(User user) { state.postValue(ProfileScreenState.saved("Đã cập nhật hồ sơ doanh nghiệp.")); }
                    @Override public void onError(UserRepository.ProfileError error, String message) { state.postValue(ProfileScreenState.error(message)); }
                });
    }

    public void updateSellerProfile(String representativeName, String shopName,
            String shopDescription, String shopAvatarUrl, String address, String bankCode,
            String bankAccountName, String bankAccountNumber, double latitude, double longitude) {
        String validation = ProfileValidator.validateSellerProfile(representativeName, shopName,
                shopDescription, shopAvatarUrl, address, bankCode, bankAccountName, bankAccountNumber);
        if (validation != null) { state.setValue(ProfileScreenState.error(validation)); return; }
        if (!Double.isFinite(latitude) || !Double.isFinite(longitude)
                || latitude < -90D || latitude > 90D || longitude < -180D || longitude > 180D
                || (latitude == 0D && longitude == 0D)) {
            state.setValue(ProfileScreenState.error(
                    "Hãy lấy GPS tại cửa hàng trước khi lưu để tính phí giao hàng."));
            return;
        }
        String userId = requireUserId(); if (userId == null) return;
        state.setValue(ProfileScreenState.loading());
        userRepository.updateSellerProfile(userId, representativeName, shopName, shopDescription,
                shopAvatarUrl, address, bankCode, bankAccountName, bankAccountNumber, latitude, longitude,
                new UserRepository.UserCallback() {
                    @Override public void onSuccess(User user) {
                        state.postValue(ProfileScreenState.saved("Đã cập nhật hồ sơ người bán."));
                    }
                    @Override public void onError(UserRepository.ProfileError error, String message) {
                        state.postValue(ProfileScreenState.error(message));
                    }
                });
    }

    public void loadAddresses() {
        String userId = requireUserId(); if (userId == null) return;
        state.setValue(ProfileScreenState.loading());
        userRepository.getAddresses(userId, new UserRepository.AddressListCallback() {
            @Override public void onSuccess(java.util.List<Address> addresses) {
                state.postValue(ProfileScreenState.addresses(addresses));
            }
            @Override public void onError(UserRepository.ProfileError error, String message) {
                state.postValue(ProfileScreenState.error(message));
            }
        });
    }

    public void saveAddress(String id, String receiverName, String receiverPhone, String province,
            String district, String ward, String street, double latitude, double longitude,
            AddressType type, boolean isDefault) {
        String validation = ProfileValidator.validateAddress(receiverName, receiverPhone, province,
                district, ward, street);
        if (validation != null) { state.setValue(ProfileScreenState.error(validation)); return; }
        String userId = requireUserId(); if (userId == null) return;
        Address address = new Address(id, userId, receiverName, receiverPhone, province, district,
                ward, street, latitude, longitude, type, isDefault);
        state.setValue(ProfileScreenState.loading());
        userRepository.saveAddress(userId, address, new UserRepository.AddressCallback() {
            @Override public void onSuccess(Address value) {
                state.postValue(ProfileScreenState.saved("Đã lưu địa chỉ."));
            }
            @Override public void onError(UserRepository.ProfileError error, String message) {
                state.postValue(ProfileScreenState.error(message));
            }
        });
    }

    public void setDefaultAddress(String addressId) { addressAction(addressId, true); }
    public void deleteAddress(String addressId) { addressAction(addressId, false); }

    private void addressAction(String addressId, boolean makeDefault) {
        String userId = requireUserId(); if (userId == null) return;
        state.setValue(ProfileScreenState.loading());
        UserRepository.ActionCallback callback = new UserRepository.ActionCallback() {
            @Override public void onSuccess() { loadAddresses(); }
            @Override public void onError(UserRepository.ProfileError error, String message) {
                state.postValue(ProfileScreenState.error(message));
            }
        };
        if (makeDefault) userRepository.setDefaultAddress(userId, addressId, callback);
        else userRepository.deleteAddress(userId, addressId, callback);
    }

    public void loadSellerApplication() {
        String userId = requireUserId(); if (userId == null) return;
        state.setValue(ProfileScreenState.loading());
        userRepository.getSellerApplication(userId, new UserRepository.ApplicationCallback() {
            @Override public void onSuccess(SellerApplication application) {
                state.postValue(ProfileScreenState.application(application));
            }
            @Override public void onError(UserRepository.ProfileError error, String message) {
                if (error == UserRepository.ProfileError.NOT_FOUND) {
                    state.postValue(ProfileScreenState.application(null));
                } else state.postValue(ProfileScreenState.error(message));
            }
        });
    }

    public void submitSellerApplication(String representativeName, String shopName,
            String address, String proofImageUrl) {
        String validation = ProfileValidator.validateSellerApplication(representativeName, shopName,
                address, proofImageUrl);
        if (validation != null) { state.setValue(ProfileScreenState.error(validation)); return; }
        String userId = requireUserId(); if (userId == null) return;
        SellerApplication application = new SellerApplication(userId, userId, representativeName,
                shopName, address, proofImageUrl);
        state.setValue(ProfileScreenState.loading());
        userRepository.submitSellerApplication(userId, application,
                new UserRepository.ApplicationCallback() {
                    @Override public void onSuccess(SellerApplication value) {
                        state.postValue(ProfileScreenState.application(value));
                    }
                    @Override public void onError(UserRepository.ProfileError error, String message) {
                        state.postValue(ProfileScreenState.error(message));
                    }
                });
    }

    public void requestCurrentLocation(boolean reverseGeocode) {
        state.setValue(ProfileScreenState.loading());
        locationProvider.getCurrentLocation(new LocationProvider.LocationCallback() {
            @Override public void onLocationAvailable(double latitude, double longitude) {
                if (!reverseGeocode) {
                    state.postValue(ProfileScreenState.location(latitude, longitude, null)); return;
                }
                locationProvider.reverseGeocode(latitude, longitude, new LocationProvider.GeocodeCallback() {
                    @Override public void onAddressAvailable(LocationProvider.GeocodedAddress address) {
                        state.postValue(ProfileScreenState.location(latitude, longitude, address));
                    }
                    @Override public void onGeocodeUnavailable(String message) {
                        state.postValue(ProfileScreenState.location(latitude, longitude, null, message));
                    }
                });
            }
            @Override public void onLocationUnavailable(String message) {
                state.postValue(ProfileScreenState.error(message));
            }
        });
    }

    public void onLocationPermissionDenied() {
        state.setValue(ProfileScreenState.error("Bạn đã từ chối quyền vị trí. Có thể nhập địa chỉ thủ công."));
    }

    public void clearTransientState() { state.setValue(ProfileScreenState.idle()); }

    private String requireUserId() {
        String value = authRepository.getCurrentUserId();
        if (!authRepository.isAuthenticated() || value == null || value.trim().isEmpty()) {
            state.setValue(ProfileScreenState.error("Vui lòng đăng nhập để quản lý hồ sơ.")); return null;
        }
        return value;
    }

    private UserRepository.UserCallback userCallback() {
        return new UserRepository.UserCallback() {
            @Override public void onSuccess(User user) { state.postValue(ProfileScreenState.profile(user)); }
            @Override public void onError(UserRepository.ProfileError error, String message) {
                state.postValue(ProfileScreenState.error(message));
            }
        };
    }

    @Override protected void onCleared() {
        locationProvider.close();
        super.onCleared();
    }
}
