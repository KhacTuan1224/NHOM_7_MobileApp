package com.rescuefarm;

import static org.junit.Assert.*;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.not;

import android.Manifest;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.rescuefarm.data.repository.CampaignRepository;
import com.rescuefarm.data.repository.ProductRepository;
import com.rescuefarm.data.repository.UnavailableAuthRepository;
import com.rescuefarm.data.repository.UnavailableUserRepository;
import com.rescuefarm.service.location.LocationProvider;
import com.rescuefarm.ui.campaign.CampaignEditorFragment;
import com.rescuefarm.ui.campaign.CampaignViewModel;
import com.rescuefarm.ui.common.VietnamAddressView;
import com.rescuefarm.ui.profile.ProfileViewModel;

import org.junit.Test;
import org.junit.runner.RunWith;
import java.lang.reflect.Proxy;
import java.util.Collections;

/** Exercises real editor callbacks and address pickers with isolated repositories and GPS. */
@RunWith(AndroidJUnit4.class)
public class CampaignEditorLocationTest {
    public static class TestEditor extends CampaignEditorFragment {
        final FakeLocation location = new FakeLocation();
        int saves;

        @Override public void onCreate(Bundle state) {
            super.onCreate(state);
            ViewModelProvider provider = new ViewModelProvider(this, new ViewModelProvider.Factory() {
                @NonNull @Override public <T extends ViewModel> T create(@NonNull Class<T> type) {
                    if (type == ProfileViewModel.class) return type.cast(new ProfileViewModel(
                            new UnavailableAuthRepository(), new UnavailableUserRepository(), location));
                    CampaignRepository campaigns = (CampaignRepository) Proxy.newProxyInstance(
                            CampaignRepository.class.getClassLoader(), new Class<?>[]{CampaignRepository.class},
                            (proxy, method, args) -> {
                                if (method.getName().equals("saveCampaign")) saves++;
                                return LiveData.class.isAssignableFrom(method.getReturnType())
                                        ? new MutableLiveData<>() : null;
                            });
                    ProductRepository products = (ProductRepository) Proxy.newProxyInstance(
                            ProductRepository.class.getClassLoader(), new Class<?>[]{ProductRepository.class},
                            (proxy, method, args) -> LiveData.class.isAssignableFrom(method.getReturnType())
                                    ? new MutableLiveData<>(Collections.emptyList()) : null);
                    return type.cast(new CampaignViewModel(campaigns, new UnavailableAuthRepository(), products, location));
                }
            });
            provider.get(ProfileViewModel.class);
            provider.get(CampaignViewModel.class);
        }
    }

    private static class FakeLocation implements LocationProvider {
        LocationCallback pending;
        GeocodedAddress address;
        @Override public void getCurrentLocation(LocationCallback callback) { pending = callback; }
        @Override public void reverseGeocode(double latitude, double longitude, GeocodeCallback callback) {
            if (address == null) callback.onGeocodeUnavailable("Không tìm thấy địa chỉ cho vị trí này.");
            else callback.onAddressAvailable(address);
        }
        void finish(GeocodedAddress value) {
            address = value;
            assertNotNull("The editor requested a GPS fix", pending);
            pending.onLocationAvailable(10.7894883, 106.6547124);
            pending = null;
        }
    }

    private TestEditor show(ActivityScenario<MainActivity> scenario) {
        String app = InstrumentationRegistry.getInstrumentation().getTargetContext().getPackageName();
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .grantRuntimePermission(app, Manifest.permission.ACCESS_COARSE_LOCATION);
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .grantRuntimePermission(app, Manifest.permission.ACCESS_FINE_LOCATION);
        TestEditor[] result = new TestEditor[1];
        scenario.onActivity(activity -> {
            FrameLayout holder = new FrameLayout(activity);
            holder.setId(View.generateViewId());
            holder.setBackgroundColor(activity.getColor(R.color.rescue_background));
            activity.addContentView(holder, new ViewGroup.LayoutParams(-1, -1));
            result[0] = new TestEditor();
            activity.getSupportFragmentManager().beginTransaction()
                    .add(holder.getId(), result[0]).commitNow();
        });
        long deadline = SystemClock.uptimeMillis() + 5000;
        boolean[] ready = {false};
        while (!ready[0] && SystemClock.uptimeMillis() < deadline) {
            scenario.onActivity(activity -> ready[0] = address(result[0]).isReady());
            if (!ready[0]) SystemClock.sleep(50);
        }
        assertTrue("The address catalog loaded", ready[0]);
        return result[0];
    }

    private VietnamAddressView address(TestEditor editor) {
        return editor.requireView().findViewById(R.id.campaignLocationNameInput);
    }

    private void assertGps(TestEditor editor) {
        Bundle saved = new Bundle();
        editor.onSaveInstanceState(saved);
        assertEquals(10.7894883, saved.getDouble("campaignLatitude"), 0.000001);
        assertEquals(106.6547124, saved.getDouble("campaignLongitude"), 0.000001);
    }

    private void setStreet(TestEditor editor, String street) {
        ViewGroup form = address(editor);
        for (int i = 0; i < form.getChildCount(); i++) {
            View child = form.getChildAt(i);
            if (child instanceof com.google.android.material.textfield.TextInputLayout) {
                com.google.android.material.textfield.TextInputLayout box =
                        (com.google.android.material.textfield.TextInputLayout) child;
                if (editor.getString(R.string.street_label).contentEquals(box.getHint())) {
                    box.getEditText().setText(street);
                    return;
                }
            }
        }
        fail("Street input exists");
    }

    private void choose(int label, String value) {
        onView(withHint(label)).perform(scrollTo(), click());
        onView(withHint(R.string.address_search_hint)).inRoot(isDialog()).perform(replaceText(value));
        onView(allOf(withText(value), not(isAssignableFrom(android.widget.EditText.class))))
                .inRoot(isDialog()).perform(click());
    }

    @Test public void completingPartialGpsAddressKeepsCoordinates() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            TestEditor editor = show(scenario);
            scenario.onActivity(activity -> {
                editor.requireView().findViewById(R.id.campaignUseGpsButton).performClick();
                editor.location.finish(new LocationProvider.GeocodedAddress(
                        "Thành phố Hồ Chí Minh", "", "", "273 Phạm Phú Thứ"));
            });
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            choose(R.string.district_label, "Quận Tân Bình");
            choose(R.string.ward_label, "Phường 11");
            scenario.onActivity(activity -> {
                setStreet(editor, "273 Phạm Phú Thứ, điểm bán");
                assertTrue(address(editor).validate());
                assertGps(editor);
                ((TextView) editor.requireView().findViewById(R.id.campaignDescriptionInput))
                        .setText("Nội dung câu chuyện giải cứu nông sản chi tiết.");
                editor.requireView().findViewById(R.id.submitCampaignButton).performClick();
                String message = ((TextView) editor.requireView().findViewById(R.id.campaignEditorMessage)).getText().toString();
                assertTrue("Submission passed address/GPS checks and reached title validation", message.contains("Tên chiến dịch"));
                assertEquals(0, editor.saves);
            });
        }
    }

    @Test public void gettingGpsKeepsTheManuallyCompletedAddress() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            TestEditor editor = show(scenario);
            scenario.onActivity(activity -> {
                address(editor).setAddress("Hồ Chí Minh", "Tân Bình", "Phường 11", "273 Phạm Phú Thứ");
                editor.requireView().findViewById(R.id.campaignUseGpsButton).performClick();
                editor.location.finish(new LocationProvider.GeocodedAddress("Hà Nội", "Ba Đình", "Phúc Xá", "12 Đường A"));
            });
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(activity -> {
                assertTrue(address(editor).validate());
                assertEquals("273 Phạm Phú Thứ", address(editor).street());
                assertEquals("Thành phố Hồ Chí Minh", address(editor).province());
                assertGps(editor);
            });
        }
    }

    @Test public void typingWhileGpsLoadsDoesNotDiscardTheFixOrOverwriteTheAddress() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            TestEditor editor = show(scenario);
            scenario.onActivity(activity -> {
                editor.requireView().findViewById(R.id.campaignUseGpsButton).performClick();
                setStreet(editor, "273 Phạm Phú Thứ");
                editor.location.finish(new LocationProvider.GeocodedAddress("Hà Nội", "Ba Đình", "Phúc Xá", "12 Đường A"));
            });
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(activity -> {
                assertEquals("273 Phạm Phú Thứ", address(editor).street());
                assertGps(editor);
            });
        }
    }

    @Test public void geocoderFailureStillAllowsManualAddressCompletion() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            TestEditor editor = show(scenario);
            scenario.onActivity(activity -> {
                editor.requireView().findViewById(R.id.campaignUseGpsButton).performClick();
                editor.location.finish(null);
            });
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(activity -> {
                address(editor).setAddress("Hồ Chí Minh", "Tân Bình", "Phường 11", "273 Phạm Phú Thứ");
                setStreet(editor, "273 Phạm Phú Thứ, điểm bán");
                assertTrue(address(editor).validate());
                assertGps(editor);
            });
        }
    }

    @Test public void missingGpsAndIncompleteAddressStillBlockSubmission() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            TestEditor editor = show(scenario);
            scenario.onActivity(activity -> {
                address(editor).setAddress("Hồ Chí Minh", "Tân Bình", "Phường 11", "273 Phạm Phú Thứ");
                editor.requireView().findViewById(R.id.submitCampaignButton).performClick();
                TextView message = editor.requireView().findViewById(R.id.campaignEditorMessage);
                assertEquals(editor.getString(R.string.campaign_gps_required), message.getText().toString());
                editor.requireView().findViewById(R.id.campaignUseGpsButton).performClick();
                editor.requireView().findViewById(R.id.submitCampaignButton).performClick();
                assertEquals(editor.getString(R.string.campaign_gps_wait), message.getText().toString());
                address(editor).clear();
                editor.location.finish(null);
            });
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(activity -> {
                editor.requireView().findViewById(R.id.submitCampaignButton).performClick();
                TextView message = editor.requireView().findViewById(R.id.campaignEditorMessage);
                assertEquals(editor.getString(R.string.campaign_address_required), message.getText().toString());
                assertEquals(0, editor.saves);
            });
        }
    }
}
