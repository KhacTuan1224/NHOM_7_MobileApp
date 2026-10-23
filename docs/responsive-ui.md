# Responsive Android layouts

The app uses the available View width, so the same layouts adapt to phones, tablets,
rotation and split-screen windows. No orientation is locked.

- `ResponsiveContentLayout` centers scroll content and limits its width: 640dp for
  forms, 840dp for reading/cart/profile screens and 1120dp for catalog/dashboard screens.
  Override `app:maxContentWidth` for a different content type.
- `ResponsiveGridLayout` lays out product tiles and dashboard metrics in 1–4 columns.
  Its minimum tile width is 156dp, with 12dp gaps; category tiles use 104dp and up to
  8 columns. Larger system fonts increase the minimum tile width automatically.
  Add tiles directly with `MATCH_PARENT`/`WRAP_CONTENT` layout parameters. Column
  count is recalculated during measurement; it does not use physical display pixels.
- `ResponsiveActionLayout` keeps related text actions in a row when their labels
  fit, then stacks them at narrower widths or larger font sizes. Keep these rows at
  `wrap_content` height; text buttons retain at least a 48dp touch target.
- Product tile photos use their measured tile width at a 4:3 aspect ratio. Original
  and rescue prices have separate lines so large amounts do not overlap.
- Welcome screens scroll on short windows. Forms have flexible control heights;
  cart quantity/delete controls use 48dp touch targets.
- Discovery, checkout and product detail use two panes only at an available width
  of at least **840dp** (`layout-w840dp`). Portrait and wide versions expose the
  same field/action IDs. Narrow landscape windows use the normal scrolling layout.
- Admin and Seller dashboards use a permanent, independently scrolling navigation
  sidebar at an available width of **600dp** or more (`layout-w600dp`). Each overview occupies
  the remaining width without a modal scrim. Smaller windows retain the hamburger
  drawer; both layouts share the same menu buttons and navigation actions.
- The floating navigation is centered and limited to 560dp. MainActivity handles
  system bars, display cutouts and the keyboard; navigation hides while the keyboard
  is visible. Navigation-aware scroll containers reserve the bottom scroll range.

## Verification

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
.\gradlew.bat :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.rescuefarm.ResponsiveLayoutsTest'
```

`ResponsiveLayoutsTest` checks grid reflow and RTL placement, action stacking and
restored weights, centered form widths, field/action parity across layout variants,
and measures 15 representative screens at 320×480, 640×360, 600×960 and 960×600dp
with font scales 1.0 and 1.6. Additional checks cover populated product photos and
large prices inside resized grids, plus cart control bounds and touch targets.
It also creates preview PNGs in the test device's app
external-files `responsive-previews` folder (retained when running instrumentation
directly; Gradle may uninstall the app after tests).

For a manual pass, verify populated catalogs and long Vietnamese labels, expand
the keyboard in checkout/address forms, rotate with entered values, and scroll to
the final action on Customer, Seller and Admin screens.
