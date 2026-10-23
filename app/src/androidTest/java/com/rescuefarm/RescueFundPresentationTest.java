package com.rescuefarm;

import static org.junit.Assert.*;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.material.chip.Chip;
import com.google.android.material.textfield.TextInputLayout;
import com.rescuefarm.ui.fund.RescueFundFragment;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Render-only fixtures. Never starts the fund API, creates a donation, or transfers money. */
@RunWith(AndroidJUnit4.class)
public class RescueFundPresentationTest {
    private void set(RescueFundFragment fragment, String name, Object value) throws Exception {
        Field field = RescueFundFragment.class.getDeclaredField(name);
        field.setAccessible(true); field.set(fragment, value);
    }

    private void call(RescueFundFragment fragment, String name) throws Exception {
        Method method = RescueFundFragment.class.getDeclaredMethod(name);
        method.setAccessible(true); method.invoke(fragment);
    }

    private View find(View root, String text) {
        if (root instanceof TextView && text.contentEquals(((TextView) root).getText())) return root;
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = find(group.getChildAt(i), text);
                if (found != null) return found;
            }
        }
        return null;
    }

    private EditText field(View root, String hint) {
        if (root instanceof TextInputLayout && hint.contentEquals(((TextInputLayout) root).getHint()))
            return ((TextInputLayout) root).getEditText();
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                EditText found = field(group.getChildAt(i), hint);
                if (found != null) return found;
            }
        }
        return null;
    }

    private void measure(View root, int widthDp) {
        float density = root.getResources().getDisplayMetrics().density;
        root.measure(View.MeasureSpec.makeMeasureSpec(Math.round(widthDp * density), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(Math.round(900 * density), View.MeasureSpec.EXACTLY));
        root.layout(0, 0, root.getMeasuredWidth(), root.getMeasuredHeight());
    }

    private void checkText(View view) {
        if (view.getVisibility() != View.VISIBLE) return;
        if (view instanceof TextView && !(view instanceof EditText)) {
            TextView text = (TextView) view;
            if (text.getLayout() != null && text.length() > 0) {
                for (int line = 0; line < text.getLayout().getLineCount(); line++) {
                    assertEquals("Ellipsized: " + text.getText(), 0, text.getLayout().getEllipsisCount(line));
                    // getLineMax excludes trailing whitespace at wrapped line endings.
                    assertTrue("Text too wide: " + text.getText() + " (line=" + text.getLayout().getLineMax(line)
                            + ", available=" + (text.getWidth() - text.getCompoundPaddingLeft() - text.getCompoundPaddingRight()) + ")", text.getLayout().getLineMax(line)
                            <= text.getWidth() - text.getCompoundPaddingLeft() - text.getCompoundPaddingRight() + 2);
                }
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) checkText(group.getChildAt(i));
        }
    }

    private void save(MainActivity activity, View root, String name) throws Exception {
        Bitmap bitmap = Bitmap.createBitmap(root.getWidth(), root.getHeight(), Bitmap.Config.ARGB_8888);
        root.draw(new Canvas(bitmap));
        File output = new File(activity.getExternalFilesDir(null), name);
        try (FileOutputStream stream = new FileOutputStream(output)) {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream));
        } finally { bitmap.recycle(); }
        // Keep render previews after the test runner removes the test app's external files.
        try (android.os.ParcelFileDescriptor copied = androidx.test.platform.app.InstrumentationRegistry
                .getInstrumentation().getUiAutomation().executeShellCommand("cp " + output.getAbsolutePath() + " /data/local/tmp/" + name);
                java.io.FileInputStream result = new java.io.FileInputStream(copied.getFileDescriptor())) {
            while (result.read() != -1) { }
        }
    }

    @Test public void fundOverviewAndDonationFormStayReadableAndProtectAnonymousDonors() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                try {
                    RescueFundFragment fragment = new RescueFundFragment();
                    // CREATED attaches a themed context without invoking onViewCreated or loading the backend.
                    activity.getSupportFragmentManager().beginTransaction().add(fragment, "fund-render-test")
                            .setMaxLifecycle(fragment, Lifecycle.State.CREATED).commitNow();
                    View root = fragment.onCreateView(activity.getLayoutInflater(), null, null);
                    root.findViewById(R.id.fundProgress).setVisibility(View.GONE);
                    root.findViewById(R.id.fundStatus).setVisibility(View.GONE);
                    set(fragment, "prefs", activity.getSharedPreferences("fund_presentation_test", 0));
                    set(fragment, "fund", new JSONObject().put("name", "Hũ giải cứu nông sản").put("active", true)
                            .put("available", 12500000).put("reserved", 850000).put("spent", 3200000)
                            .put("charityName", "Bếp ăn thiện nguyện").put("address", "Thành phố Hồ Chí Minh"));
                    call(fragment, "renderOverview");
                    assertNotNull(find(root, "12.500.000 đ"));
                    assertTrue(find(root, activity.getString(R.string.fund_donate)).isEnabled());
                    for (int width : new int[]{320, 411, 840}) {
                        measure(root, width); checkText(root);
                        if (width == 411) save(activity, root, "fund-overview.png");
                    }

                    find(root, activity.getString(R.string.fund_donate)).performClick();
                    measure(root, 320); checkText(root);
                    EditText amount = field(root, activity.getString(R.string.fund_amount_hint));
                    EditText name = field(root, "Tên hiển thị");
                    assertNotNull(amount); assertNotNull(name);
                    // Canvas snapshots have no frame loop to finish floating-label animations.
                    ((TextInputLayout) amount.getParent().getParent()).setHintAnimationEnabled(false);
                    ((TextInputLayout) name.getParent().getParent()).setHintAnimationEnabled(false);
                    View nameContainer = (View) name.getParent().getParent();
                    assertEquals(View.GONE, nameContainer.getVisibility());
                    Chip preset = (Chip) find(root, "50.000 đ"); preset.performClick();
                    assertEquals("50000", amount.getText().toString());
                    amount.setText("60000"); assertFalse(preset.isChecked());
                    amount.setText("9999"); find(root, "Xác nhận và xem QR").performClick();
                    assertNotNull(amount.getError());
                    assertNotNull(find(root, activity.getString(R.string.fund_amount_title)));
                    View share = find(root, "Hiển thị tên trong danh sách ủng hộ"); share.performClick();
                    assertEquals(View.VISIBLE, nameContainer.getVisibility());
                    amount.setText("50000"); find(root, "Xác nhận và xem QR").performClick();
                    assertNotNull(name.getError());
                    measure(root, 411); save(activity, root, "fund-donation.png");

                    set(fragment, "fund", new JSONObject().put("active", false));
                    ((ViewGroup) root.findViewById(R.id.fundBody)).removeAllViews(); call(fragment, "renderOverview");
                    assertFalse(find(root, activity.getString(R.string.fund_donate)).isEnabled());
                    assertNotNull(find(root, activity.getString(R.string.fund_destination_empty)));
                    measure(root, 320); checkText(root);

                    Method receipt = RescueFundFragment.class.getDeclaredMethod("showReceipt", JSONObject.class);
                    receipt.setAccessible(true);
                    receipt.invoke(fragment, new JSONObject().put("status", "PAID").put("amount", 50000)
                            .put("accountName", "TAI KHOAN KIEM THU").put("accountNumber", "0000000000").put("transferContent", "TEST"));
                    assertNotNull(find(root, activity.getString(R.string.fund_paid_title)));
                    TextView account = (TextView) find(root, "0000000000");
                    assertTrue(account.isTextSelectable());
                    measure(root, 320); checkText(root);
                    measure(root, 411); save(activity, root, "fund-receipt.png");
                    receipt.invoke(fragment, new JSONObject().put("status", "PENDING").put("expiresAt", 1));
                    assertNotNull(find(root, activity.getString(R.string.fund_receipt_expired)));
                    receipt.invoke(fragment, new JSONObject().put("status", "PENDING").put("amount", 50000)
                            .put("expiresAt", System.currentTimeMillis() + 60000).put("qrUrl", ""));
                    java.util.ArrayList<View> qrViews = new java.util.ArrayList<>();
                    root.findViewsWithText(qrViews, "QR chuyển khoản ủng hộ", View.FIND_VIEWS_WITH_CONTENT_DESCRIPTION);
                    assertEquals(1, qrViews.size());
                    assertNotNull(find(root, activity.getString(R.string.fund_qr_title)));
                    measure(root, 320); checkText(root);
                    activity.getSupportFragmentManager().beginTransaction().remove(fragment).commitNow();
                } catch (Exception error) { throw new AssertionError(error); }
            });
        }
    }
}
