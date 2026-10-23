package com.rescuefarm;

import static org.junit.Assert.*;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.rescuefarm.ui.common.ResponsiveActionLayout;
import com.rescuefarm.ui.common.ResponsiveContentLayout;
import com.rescuefarm.ui.common.ResponsiveGridLayout;
import com.rescuefarm.ui.common.ProductTileImageView;
import com.rescuefarm.ui.home.HomeCardRenderer;
import com.rescuefarm.domain.model.Product;
import com.rescuefarm.domain.enums.ProductStatus;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.util.HashSet;
import java.util.Set;

@RunWith(AndroidJUnit4.class)
public class ResponsiveLayoutsTest {
    private Context context(int width, int height, float fontScale) {
        Context target = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Configuration config = new Configuration(target.getResources().getConfiguration());
        config.screenWidthDp = width;
        config.screenHeightDp = height;
        config.smallestScreenWidthDp = Math.min(width, height);
        config.orientation = width > height ? Configuration.ORIENTATION_LANDSCAPE
                : Configuration.ORIENTATION_PORTRAIT;
        config.fontScale = fontScale;
        return new ContextThemeWrapper(target.createConfigurationContext(config), R.style.Theme_RescueFarm);
    }

    private int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private void measure(View view, int widthDp, int heightDp) {
        view.measure(View.MeasureSpec.makeMeasureSpec(dp(view.getContext(), widthDp), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(dp(view.getContext(), heightDp), View.MeasureSpec.EXACTLY));
        view.layout(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight());
    }

    @Test public void gridsReflowOnResizeAndMakeRoomForLargeFonts() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            Context c = context(1120, 800, 1f);
            ResponsiveGridLayout grid = new ResponsiveGridLayout(c);
            for (int index = 0; index < 7; index++) {
                TextView tile = new TextView(c);
                tile.setText("Nông sản " + index);
                grid.addView(tile, new LinearLayout.LayoutParams(-1, dp(c, 60 + index * 3)));
            }
            int previous = 0;
            for (int width : new int[]{288, 328, 568, 808, 1072}) {
                measure(grid, width, 800);
                assertTrue(grid.getColumnCount() >= previous);
                previous = grid.getColumnCount();
                for (int index = 0; index < grid.getChildCount(); index++) {
                    View child = grid.getChildAt(index);
                    assertTrue(child.getLeft() >= 0 && child.getRight() <= grid.getWidth());
                    if (index >= grid.getColumnCount()) {
                        View above = grid.getChildAt(index - grid.getColumnCount());
                        assertTrue(child.getTop() >= above.getBottom());
                    }
                }
            }
            assertEquals(4, grid.getColumnCount());
            measure(grid, 288, 800);
            assertEquals(1, grid.getColumnCount());
            ResponsiveGridLayout large = new ResponsiveGridLayout(context(600, 960, 1.6f));
            measure(large, 568, 800);
            measure(grid, 568, 800);
            assertTrue(large.getColumnCount() < grid.getColumnCount());
            grid.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
            measure(grid, 568, 800);
            assertTrue(grid.getChildAt(0).getLeft() > grid.getChildAt(1).getLeft());
        });
    }

    @Test public void actionsStackAndReturnToARowWithoutLosingWeights() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            Context c = context(360, 640, 1.6f);
            ResponsiveActionLayout row = new ResponsiveActionLayout(c);
            for (String label : new String[]{"Thêm vào giỏ hàng", "Xem giỏ hàng"}) {
                MaterialButton button = new MaterialButton(c);
                button.setText(label);
                button.setMinHeight(dp(c, 48));
                row.addView(button, new LinearLayout.LayoutParams(0, -2, 1f));
            }
            measure(row, 288, 400);
            assertEquals(LinearLayout.VERTICAL, row.getOrientation());
            assertTrue(row.getChildAt(1).getTop() >= row.getChildAt(0).getBottom());
            measure(row, 1120, 400);
            assertEquals(LinearLayout.HORIZONTAL, row.getOrientation());
            assertEquals(1f, ((LinearLayout.LayoutParams) row.getChildAt(0).getLayoutParams()).weight, 0f);
            assertEquals(row.getChildAt(0).getWidth(), row.getChildAt(1).getWidth(), 1);
        });
    }

    @Test public void formsStayCenteredAndReadableOnTablet() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            Context c = context(1200, 800, 1f);
            FrameLayout frame = new FrameLayout(c);
            ResponsiveContentLayout content = new ResponsiveContentLayout(c);
            content.setMaxContentWidth(dp(c, 640));
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER_HORIZONTAL);
            frame.addView(content, params);
            measure(frame, 1200, 800);
            assertEquals(dp(c, 640), content.getWidth());
            assertEquals(dp(c, 280), content.getLeft());
            measure(frame, 320, 480);
            assertEquals(dp(c, 320), content.getWidth());
        });
    }

    @Test public void populatedProductTilesKeepPhotosAndLargePricesInsideTheirColumns() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            for (float scale : new float[]{1f, 1.6f}) {
                Context c = context(960, 600, scale);
                // This renderer only needs a themed context; empty media avoids network requests.
                Fragment fragment = new Fragment() {
                    @Override public Context getContext() { return c; }
                };
                HomeCardRenderer renderer = new HomeCardRenderer(fragment);
                ResponsiveGridLayout grid = new ResponsiveGridLayout(c);
                grid.setBackgroundColor(c.getColor(R.color.rescue_background));
                for (int index = 0; index < 7; index++) {
                    Product product = new Product("preview-" + index, "seller", "fruit",
                            "Nông sản tươi từ nhà vườn Đồng Tháp " + index, "", 1250000D, 999000D,
                            "kg", "Đồng Tháp", "Đồng Tháp", java.util.Collections.emptyList(), ProductStatus.ACTIVE);
                    grid.addView(renderer.product(product, unused -> { }), new LinearLayout.LayoutParams(-1, -2));
                }
                for (int width : new int[]{288, 328, 568, 1072}) {
                    measure(grid, width, 1200);
                    checkTilePhotos(grid);
                    if (scale == 1f && (width == 328 || width == 1072)) {
                        savePreview(c, grid, "product-grid-" + width);
                    }
                }
            }
        });
    }

    private void checkTilePhotos(View view) {
        if (view instanceof ProductTileImageView) {
            View parent = (View) view.getParent();
            assertTrue("Photos must have a measured height", view.getHeight() > 0);
            assertTrue(view.getWidth() <= parent.getWidth());
            assertTrue("Photo wrappers must grow with their image", view.getHeight() <= parent.getHeight());
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) checkTilePhotos(group.getChildAt(i));
        }
    }

    @Test public void cartControlsStayReachableWithLargeFontsOnSmallPhones() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            Context c = context(320, 480, 1.6f);
            View item = LayoutInflater.from(c).inflate(R.layout.item_cart_product, null);
            ((TextView) item.findViewById(R.id.cartItemTotal)).setText("1.250.000.000 ₫");
            ((TextView) item.findViewById(R.id.cartItemQuantity)).setText("999,5 kg");
            measure(item, 288, 480);
            for (int id : new int[]{R.id.cartItemDecrease, R.id.cartItemIncrease, R.id.cartItemDelete}) {
                View control = item.findViewById(id);
                android.graphics.Rect bounds = new android.graphics.Rect(0, 0, control.getWidth(), control.getHeight());
                ((ViewGroup) item).offsetDescendantRectToMyCoords(control, bounds);
                assertTrue(bounds.left >= 0 && bounds.right <= item.getWidth());
                assertTrue(bounds.top >= 0 && bounds.bottom <= item.getHeight());
                assertTrue(control.getWidth() >= dp(c, 48) && control.getHeight() >= dp(c, 48));
            }
        });
    }

    @Test public void wideLayoutsKeepEveryPortraitFieldAndAction() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            for (int layout : new int[]{R.layout.fragment_discovery, R.layout.fragment_checkout,
                    R.layout.fragment_product_detail}) {
                Set<Integer> portrait = new HashSet<>();
                Set<Integer> wide = new HashSet<>();
                collectIds(LayoutInflater.from(context(360, 800, 1f)).inflate(layout, null), portrait);
                collectIds(LayoutInflater.from(context(960, 600, 1f)).inflate(layout, null), wide);
                assertEquals("Every field/action must survive the wide layout", portrait, wide);
            }
        });
    }

    private void collectIds(View view, Set<Integer> ids) {
        if (view.getId() != View.NO_ID) ids.add(view.getId());
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) collectIds(group.getChildAt(i), ids);
        }
    }

    @Test public void adminNavigationSharesActionsAndReservesItsOwnTabletPane() {
        assertDashboardNavigation(R.layout.fragment_admin_dashboard, R.id.adminDashboardDrawer,
                R.id.adminDashboardNavigation, R.id.adminDashboardContent, R.id.adminShippingSettingsButton);
    }

    @Test public void sellerNavigationSharesActionsAndReservesItsOwnTabletPane() {
        assertDashboardNavigation(R.layout.fragment_seller_dashboard, R.id.sellerDashboardDrawer,
                R.id.sellerDashboardNavigation, R.id.sellerDashboardContent, R.id.dashboardProfileButton);
    }

    private void assertDashboardNavigation(int layout, int drawerId, int navigationId,
            int contentId, int lastActionId) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            View phone = LayoutInflater.from(context(599, 960, 1f))
                    .inflate(layout, null);
            assertTrue(phone instanceof androidx.drawerlayout.widget.DrawerLayout);
            Set<Integer> phoneIds = new HashSet<>();
            collectIds(phone, phoneIds);
            phoneIds.remove(drawerId);

            for (int width : new int[]{600, 960}) {
                for (float scale : new float[]{1f, 1.6f}) {
                    for (int direction : new int[]{View.LAYOUT_DIRECTION_LTR, View.LAYOUT_DIRECTION_RTL}) {
                        Context c = context(width, 600, scale);
                        View tablet = LayoutInflater.from(c).inflate(layout, null);
                        tablet.setLayoutDirection(direction);
                        measure(tablet, width, 600);
                        Set<Integer> tabletIds = new HashSet<>();
                        collectIds(tablet, tabletIds);
                        assertEquals("Every dashboard action must survive the sidebar layout", phoneIds, tabletIds);

                        android.widget.ScrollView navigation = tablet.findViewById(navigationId);
                        View content = tablet.findViewById(contentId);
                        assertEquals(View.VISIBLE, navigation.getVisibility());
                        assertTrue(content.getWidth() >= dp(c, 335));
                        assertTrue("Sidebar and overview must not overlap",
                                navigation.getRight() <= content.getLeft()
                                        || content.getRight() <= navigation.getLeft());
                        View lastAction = tablet.findViewById(lastActionId);
                        assertTrue(lastAction.getWidth() > 0);
                        navigation.scrollTo(0, navigation.getChildAt(0).getHeight());
                        android.graphics.Rect bounds = new android.graphics.Rect();
                        lastAction.getDrawingRect(bounds);
                        navigation.offsetDescendantRectToMyCoords(lastAction, bounds);
                        assertTrue("The final menu action must remain reachable", bounds.bottom
                                <= navigation.getScrollY() + navigation.getHeight());
                        assertEquals("Scrolling the menu must not move the overview", 0, content.getScrollY());
                    }
                }
            }
        });
    }

    @Test public void keyScreensMeasureAtPhoneLandscapeTabletAndLargeFontSizes() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            int[] layouts = {R.layout.fragment_splash, R.layout.fragment_onboarding,
                    R.layout.fragment_login, R.layout.fragment_register, R.layout.fragment_registration_choice,
                    R.layout.fragment_guest_home, R.layout.fragment_all_categories, R.layout.fragment_all_value_products,
                    R.layout.fragment_discovery, R.layout.fragment_product_detail, R.layout.fragment_checkout,
                    R.layout.fragment_cart, R.layout.fragment_profile, R.layout.fragment_seller_dashboard,
                    R.layout.fragment_admin_dashboard};
            for (int[] size : new int[][]{{320, 480}, {640, 360}, {600, 960}, {960, 600}}) {
                for (float fontScale : new float[]{1f, 1.6f}) {
                    Context c = context(size[0], size[1], fontScale);
                    for (int layout : layouts) {
                        View view = LayoutInflater.from(c).inflate(layout, null);
                        measure(view, size[0], size[1]);
                        assertEquals(dp(c, size[0]), view.getWidth());
                        assertEquals(dp(c, size[1]), view.getHeight());
                        // Save reviewable renders of representative compact/wide layouts.
                        if (fontScale == 1f && (size[0] == 320 || size[0] == 960)
                                && (layout == R.layout.fragment_login || layout == R.layout.fragment_checkout
                                    || layout == R.layout.fragment_discovery)) {
                            savePreview(c, view, c.getResources().getResourceEntryName(layout) + "-" + size[0]);
                        }
                    }
                }
            }
        });
    }

    private void savePreview(Context c, View view, String name) {
        File folder = new File(c.getExternalFilesDir(null), "responsive-previews");
        assertTrue(folder.isDirectory() || folder.mkdirs());
        Bitmap bitmap = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
        view.draw(new Canvas(bitmap));
        try (FileOutputStream output = new FileOutputStream(new File(folder, name + ".png"))) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output);
        } catch (java.io.IOException error) {
            throw new AssertionError(error);
        } finally {
            bitmap.recycle();
        }
    }
}
