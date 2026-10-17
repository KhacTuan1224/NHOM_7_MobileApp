package com.rescuefarm.ui.profile;

import static org.junit.Assert.assertEquals;
import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import com.rescuefarm.data.repository.AuthRepository;
import com.rescuefarm.data.repository.UnavailableUserRepository;
import com.rescuefarm.domain.enums.UserRole;
import com.rescuefarm.service.location.LocationProvider;
import org.junit.Rule;
import org.junit.Test;

public class ProfileViewModelTest {
    @Rule public final InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    @Test public void deniedPermission_emitsRecoverableError() {
        ProfileViewModel viewModel = viewModel(new FakeLocationProvider(false));
        viewModel.onLocationPermissionDenied();
        assertEquals(ProfileScreenState.Status.ERROR, viewModel.getState().getValue().getStatus());
    }

    @Test public void unavailableLocation_emitsError() {
        ProfileViewModel viewModel = viewModel(new FakeLocationProvider(false));
        viewModel.requestCurrentLocation(true);
        assertEquals(ProfileScreenState.Status.ERROR, viewModel.getState().getValue().getStatus());
    }

    @Test public void approximateLocation_isAccepted() {
        ProfileViewModel viewModel = viewModel(new FakeLocationProvider(true));
        viewModel.requestCurrentLocation(false);
        assertEquals(ProfileScreenState.Status.LOCATION, viewModel.getState().getValue().getStatus());
        assertEquals(10.77, viewModel.getState().getValue().getLatitude(), 0.001);
    }

    @Test public void geocoderFailure_keepsCoordinatesForManualAddress() {
        ProfileViewModel viewModel = viewModel(new FakeLocationProvider(true));
        viewModel.requestCurrentLocation(true);
        assertEquals(ProfileScreenState.Status.LOCATION, viewModel.getState().getValue().getStatus());
        assertEquals("Geocoder unavailable", viewModel.getState().getValue().getMessage());
    }

    @Test public void sellerSave_acceptsGpsTextAddressWithoutAdministrativeCatalogSelection() {
        final String[] writtenAddress = {null};
        UnavailableUserRepository repository = new UnavailableUserRepository() {
            @Override public void updateSellerProfile(String id, String representative, String shop,
                    String description, String avatar, String address, String bank, String accountName,
                    String accountNumber, double latitude, double longitude, UserCallback callback) {
                writtenAddress[0] = address;
                assertEquals(10.8, latitude, 0.00001);
                callback.onSuccess(new com.rescuefarm.domain.model.Seller(id, "seller@example.com", "Nguyễn An", shop));
            }
        };
        ProfileViewModel model = new ProfileViewModel(new FakeAuthRepository(), repository, new FakeLocationProvider(true));
        model.updateSellerProfile("Nguyễn An", "Vườn nhà", "Nông sản", "", "12 đường A, Đồng Tháp",
                "MB", "NGUYEN AN", "123456789", 10.8, 106.7);
        assertEquals("12 đường A, Đồng Tháp", writtenAddress[0]);
        assertEquals(ProfileScreenState.Status.SAVED, model.getState().getValue().getStatus());
    }

    @Test public void sellerSave_reportsMissingGpsInsteadOfAttemptingWrite() {
        ProfileViewModel model = viewModel(new FakeLocationProvider(false));
        model.updateSellerProfile("Nguyễn An", "Vườn nhà", "", "", "12 đường A, Đồng Tháp",
                "", "", "", 0, 0);
        assertEquals(ProfileScreenState.Status.ERROR, model.getState().getValue().getStatus());
        org.junit.Assert.assertTrue(model.getState().getValue().getMessage().contains("GPS"));
    }

    private ProfileViewModel viewModel(LocationProvider provider) {
        return new ProfileViewModel(new FakeAuthRepository(), new UnavailableUserRepository(), provider);
    }

    private static final class FakeLocationProvider implements LocationProvider {
        private final boolean available;
        FakeLocationProvider(boolean available) { this.available = available; }
        @Override public void getCurrentLocation(LocationCallback callback) {
            if (available) callback.onLocationAvailable(10.77, 106.70);
            else callback.onLocationUnavailable("Location unavailable");
        }
        @Override public void reverseGeocode(double latitude, double longitude, GeocodeCallback callback) {
            callback.onGeocodeUnavailable("Geocoder unavailable");
        }
    }

    private static final class FakeAuthRepository implements AuthRepository {
        @Override public boolean isAvailable() { return true; }
        @Override public boolean isAuthenticated() { return true; }
        @Override public String getCurrentUserId() { return "customer-1"; }
        @Override public void restoreSession(AuthCallback callback) { }
        @Override public void signIn(String email, String password, AuthCallback callback) { }
        @Override public void register(String email, String password, String fullName, String phone,
                UserRole role, AuthCallback callback) { }
        @Override public void signInWithGoogleIdToken(String idToken, AuthCallback callback) { }
        @Override public void sendPasswordResetEmail(String email, ActionCallback callback) { }
        @Override public void signOut() { }
    }
}
