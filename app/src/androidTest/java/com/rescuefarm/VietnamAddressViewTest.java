package com.rescuefarm;

import android.os.Parcelable;
import android.os.SystemClock;
import android.util.SparseArray;
import android.view.ViewGroup;
import android.view.View;
import android.content.res.Configuration;
import android.content.Context;
import android.view.LayoutInflater;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.rescuefarm.ui.common.VietnamAddressView;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static androidx.test.espresso.assertion.ViewAssertions.matches;

@RunWith(AndroidJUnit4.class)
public class VietnamAddressViewTest {
    private VietnamAddressView show(ActivityScenario<MainActivity> scenario) {
        AtomicReference<VietnamAddressView> result = new AtomicReference<>();
        scenario.onActivity(activity -> {
            VietnamAddressView view = new VietnamAddressView(activity);
            view.setId(R.id.addressForm);
            View previous = activity.findViewById(R.id.addressForm);
            if (previous != null) ((ViewGroup) previous.getParent()).removeView(previous);
            view.setBackgroundColor(activity.getColor(R.color.rescue_surface));
            view.setPadding(24, 80, 24, 24);
            activity.addContentView(view, new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            result.set(view);
        });
        waitForCatalog(scenario, result.get());
        return result.get();
    }
    private void waitForCatalog(ActivityScenario<MainActivity> scenario, VietnamAddressView view) {
        boolean[] ready = {false};
        long deadline = SystemClock.uptimeMillis() + 5000;
        while (!ready[0] && SystemClock.uptimeMillis() < deadline) {
            scenario.onActivity(activity -> ready[0] = view.isReady());
            if (!ready[0]) SystemClock.sleep(50);
        }
        assertTrue("Offline address catalog loaded", ready[0]);
    }

    @Test public void pickerSearchesWithoutAccentsAndClearsChildrenOnParentChange() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            VietnamAddressView view = show(scenario);
            onView(withHint(R.string.province_label)).perform(click());
            onView(withHint(R.string.address_search_hint)).perform(replaceText("ha noi"), closeSoftKeyboard());
            onView(withText("Thành phố Hà Nội")).perform(click());
            onView(withHint(R.string.district_label)).perform(click());
            onView(withHint(R.string.address_search_hint)).perform(replaceText("hoan kiem"), closeSoftKeyboard());
            onView(withText("Quận Hoàn Kiếm")).perform(click());
            onView(withHint(R.string.ward_label)).perform(click());
            onView(withHint(R.string.address_search_hint)).perform(replaceText("hang bac"), closeSoftKeyboard());
            onView(withText("Phường Hàng Bạc")).perform(click());
            onView(withHint(R.string.street_label)).perform(replaceText("12 Phố Huế"), closeSoftKeyboard());
            scenario.onActivity(activity -> assertTrue(view.validate()));
            onView(withHint(R.string.province_label)).perform(click());
            onView(withHint(R.string.address_search_hint)).perform(replaceText("ho chi minh"), closeSoftKeyboard());
            onView(withText("Thành phố Hồ Chí Minh")).perform(click());
            scenario.onActivity(activity -> {
                assertEquals("", view.district()); assertEquals("", view.ward());
                assertEquals("12 Phố Huế", view.street()); assertFalse(view.validate());
            });
            onView(withHint(R.string.ward_label)).check(matches(org.hamcrest.Matchers.not(isEnabled())));
        }
    }

    @Test public void compoundStateRestoresFullAddressAndRejectsForeignDistrict() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            VietnamAddressView view = show(scenario);
            SparseArray<Parcelable> saved = new SparseArray<>();
            scenario.onActivity(activity -> {
                view.setAddress("Ha Noi", "Ba Dinh", "Phuc Xa", "12, Ngõ 3");
                assertTrue(view.validate()); view.saveHierarchyState(saved);
            });
            VietnamAddressView restored = show(scenario);
            scenario.onActivity(activity -> {
                restored.restoreHierarchyState(saved);
                assertEquals("12, Ngõ 3, Phường Phúc Xá, Quận Ba Đình, Thành phố Hà Nội", restored.formattedAddress());
                assertTrue(restored.validate());
                restored.setAddress("Hà Nội", "Quận Bình Thạnh", "Phường 1", "12 Đường A");
                assertEquals("", restored.district()); assertEquals("", restored.ward()); assertFalse(restored.validate());
                String legacy = "12 Ngõ 3, Khu B, Địa danh cũ, Tỉnh chưa khớp";
                restored.setFormattedAddress(legacy);
                assertEquals(legacy, restored.street()); assertFalse(restored.validate());
            });
        }
    }

    @Test public void checkoutIncludesAddressSelectorAndQuoteControlsInBothOrientations() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                for (int orientation : new int[]{Configuration.ORIENTATION_PORTRAIT, Configuration.ORIENTATION_LANDSCAPE}) {
                    Configuration configuration = new Configuration(activity.getResources().getConfiguration());
                    configuration.orientation = orientation;
                    Context context = activity.createConfigurationContext(configuration);
                    context = new androidx.appcompat.view.ContextThemeWrapper(context, R.style.Theme_RescueFarm);
                    View checkout = LayoutInflater.from(context).inflate(R.layout.fragment_checkout, null, false);
                    assertNotNull(checkout.findViewById(R.id.checkoutSavedAddressSpinner));
                    assertTrue(checkout.findViewById(R.id.checkoutAddress) instanceof VietnamAddressView);
                    assertNotNull(checkout.findViewById(R.id.checkoutShippingEstimate));
                    assertNotNull(checkout.findViewById(R.id.checkoutCoordinates));
                    assertNotNull(checkout.findViewById(R.id.checkoutUseLocation));
                    assertNotNull(checkout.findViewById(R.id.checkoutName));
                    assertNotNull(checkout.findViewById(R.id.checkoutPhone));
                }
            });
        }
    }
}
