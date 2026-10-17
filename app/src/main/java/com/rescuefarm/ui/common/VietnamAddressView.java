package com.rescuefarm.ui.common;

import android.content.Context;
import android.os.Bundle;
import android.os.Parcelable;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.util.SparseArray;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import androidx.annotation.Nullable;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.rescuefarm.R;
import com.rescuefarm.service.address.VietnamAddressCatalog;
import com.rescuefarm.service.address.VietnamAddressCatalog.Unit;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Shared searchable, parent-scoped address editor used by every role. */
public final class VietnamAddressView extends LinearLayout {
    private static CompletableFuture<VietnamAddressCatalog> loading;
    private VietnamAddressCatalog catalog;
    private Unit province, district, ward;
    private final TextInputEditText provinceInput, districtInput, wardInput, streetInput;
    private final TextView status;
    private String[] pending = {"", "", "", ""};
    private String pendingFormatted;
    private boolean binding;
    private Runnable onChange;

    public VietnamAddressView(Context context) { this(context, null); }
    public VietnamAddressView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setOrientation(VERTICAL);
        provinceInput = field(R.string.province_label, true);
        districtInput = field(R.string.district_label, true);
        wardInput = field(R.string.ward_label, true);
        streetInput = field(R.string.street_label, false);
        streetInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        status = new TextView(context); addView(status);
        status.setText(R.string.address_catalog_loading);
        provinceInput.setOnClickListener(v -> choose(catalog == null ? Collections.emptyList()
                : catalog.provinces(), R.string.province_label, 0));
        districtInput.setOnClickListener(v -> choose(children(province), R.string.district_label, 1));
        wardInput.setOnClickListener(v -> choose(children(district), R.string.ward_label, 2));
        streetInput.addTextChangedListener(watcher(() -> {
            if (!binding) { pending[3] = street(); pendingFormatted = null; }
            changed();
        }));
        updateFields();
        load(context.getApplicationContext()).whenComplete((value, error) -> post(() -> {
            if (error != null) { status.setText(R.string.address_catalog_error); return; }
            catalog = value;
            if (pendingFormatted != null) setFormattedAddress(pendingFormatted);
            else setAddress(pending[0], pending[1], pending[2], pending[3]);
        }));
    }

    static synchronized CompletableFuture<VietnamAddressCatalog> load(Context context) {
        if (loading == null) loading = CompletableFuture.supplyAsync(() -> {
            try (InputStreamReader reader = new InputStreamReader(
                    context.getAssets().open("address/dvhcvn.tsv"), StandardCharsets.UTF_8)) {
                return VietnamAddressCatalog.read(reader);
            } catch (Exception error) { throw new IllegalStateException(error); }
        });
        return loading;
    }

    private TextInputEditText field(int label, boolean picker) {
        TextInputLayout box = new TextInputLayout(getContext(), null,
                com.google.android.material.R.attr.textInputOutlinedStyle);
        box.setHint(getContext().getString(label));
        LayoutParams params = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        params.topMargin = Math.round(8 * getResources().getDisplayMetrics().density);
        addView(box, params);
        TextInputEditText input = new TextInputEditText(box.getContext());
        box.addView(input, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        if (picker) {
            input.setFocusable(false); input.setClickable(true);
            input.setInputType(android.text.InputType.TYPE_NULL);
            box.setEndIconMode(TextInputLayout.END_ICON_CUSTOM);
            box.setEndIconDrawable(android.R.drawable.arrow_down_float);
            box.setEndIconContentDescription(label);
            box.setEndIconOnClickListener(v -> input.performClick());
        }
        return input;
    }

    private void choose(List<Unit> units, int title, int level) {
        if (units.isEmpty()) return;
        LinearLayout content = new LinearLayout(getContext()); content.setOrientation(VERTICAL);
        int padding = Math.round(20 * getResources().getDisplayMetrics().density);
        content.setPadding(padding, 0, padding, 0);
        EditText search = new EditText(getContext()); search.setSingleLine(true);
        search.setHint(R.string.address_search_hint); content.addView(search);
        ListView list = new ListView(getContext());
        int listHeight = Math.min(Math.round(320 * getResources().getDisplayMetrics().density),
                getResources().getDisplayMetrics().heightPixels / 3);
        content.addView(list, new LayoutParams(LayoutParams.MATCH_PARENT, listHeight));
        List<Unit> filtered = new ArrayList<>(units);
        ArrayAdapter<Unit> adapter = new ArrayAdapter<>(getContext(),
                android.R.layout.simple_list_item_1, new ArrayList<>(filtered));
        list.setAdapter(adapter);
        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(getContext())
                .setTitle(title).setView(content).setNegativeButton(R.string.cancel_action, null).create();
        search.addTextChangedListener(watcher(() -> {
            String query = VietnamAddressCatalog.normalize(search.getText().toString());
            filtered.clear();
            for (Unit unit : units) if (VietnamAddressCatalog.normalize(unit.name).contains(query)) filtered.add(unit);
            adapter.clear(); adapter.addAll(filtered); adapter.notifyDataSetChanged();
        }));
        list.setOnItemClickListener((parent, row, position, id) -> {
            Unit selected = filtered.get(position);
            if (level == 0 && selected != province) { province = selected; district = null; ward = null; }
            if (level == 1 && selected != district) { district = selected; ward = null; }
            if (level == 2) ward = selected;
            updateFields(); status.setText(""); changed(); dialog.dismiss();
        });
        dialog.show();
    }

    public void setOnAddressChangedListener(Runnable listener) { onChange = listener; }
    private void changed() { if (!binding && onChange != null) onChange.run(); }
    private static List<Unit> children(Unit value) { return value == null ? Collections.emptyList() : value.children(); }
    private void updateFields() {
        provinceInput.setText(province == null ? "" : province.name);
        districtInput.setText(district == null ? "" : district.name);
        wardInput.setText(ward == null ? "" : ward.name);
        provinceInput.setEnabled(catalog != null);
        districtInput.setEnabled(province != null);
        wardInput.setEnabled(district != null);
    }

    public void setAddress(String p, String d, String w, String street) {
        pendingFormatted = null;
        pending = new String[]{safe(p), safe(d), safe(w), safe(street)};
        binding = true;
        streetInput.setText(safe(street));
        if (catalog != null) {
            province = VietnamAddressCatalog.find(catalog.provinces(), p);
            district = VietnamAddressCatalog.find(children(province), d);
            ward = VietnamAddressCatalog.find(children(district), w);
            updateFields();
            status.setText((!safe(p).isEmpty() && province == null) || (!safe(d).isEmpty() && district == null)
                    || (!safe(w).isEmpty() && ward == null) ? R.string.address_reselect_hint : R.string.address_catalog_hint);
        }
        binding = false;
    }

    /** Existing seller documents store a single formatted address; retain unmatched text. */
    public void setFormattedAddress(String address) {
        if (catalog == null) {
            setAddress("", "", "", address); pendingFormatted = safe(address); return;
        }
        String[] parts = safe(address).split(",\\s*");
        int n = parts.length;
        Unit p = n < 4 ? null : VietnamAddressCatalog.find(catalog.provinces(), parts[n - 1]);
        Unit d = n < 4 ? null : VietnamAddressCatalog.find(children(p), parts[n - 2]);
        Unit w = n < 4 ? null : VietnamAddressCatalog.find(children(d), parts[n - 3]);
        if (w != null) setAddress(p.name, d.name, w.name,
                String.join(", ", java.util.Arrays.copyOf(parts, n - 3)));
        else {
            setAddress("", "", "", address);
            if (!safe(address).isEmpty()) status.setText(R.string.address_reselect_hint);
        }
    }
    public String province() { return province == null ? "" : province.name; }
    public String district() { return district == null ? "" : district.name; }
    public String ward() { return ward == null ? "" : ward.name; }
    public String street() { return streetInput.getText() == null ? "" : streetInput.getText().toString().trim(); }
    public String formattedAddress() { return String.join(", ", street(), ward(), district(), province()); }
    public boolean isReady() { return catalog != null; }
    public boolean isComplete() { return catalog != null && province != null && district != null
            && ward != null && street().length() >= 3; }
    public boolean validate() {
        String error = catalog == null ? getContext().getString(R.string.address_catalog_loading)
                : province == null ? "Vui lòng chọn tỉnh/thành phố."
                : district == null ? "Vui lòng chọn quận/huyện thuộc tỉnh/thành phố."
                : ward == null ? "Vui lòng chọn xã/phường thuộc quận/huyện."
                : street().length() < 3 ? "Vui lòng nhập số nhà, tên đường (ít nhất 3 ký tự)." : null;
        status.setText(error == null ? "" : error);
        if (error != null) { status.announceForAccessibility(error); return false; }
        return true;
    }
    public void clear() { setAddress("", "", "", ""); }

    /** Autofill only when the geocoder agrees with the bundled address catalog. */
    public boolean applyGeocodedAddress(com.rescuefarm.service.location.LocationProvider.GeocodedAddress value) {
        if (catalog == null || value == null) return false;
        Unit p = VietnamAddressCatalog.find(catalog.provinces(), value.getProvince());
        Unit d = VietnamAddressCatalog.find(children(p), value.getDistrict());
        Unit w = VietnamAddressCatalog.find(children(d), value.getWard());
        if (p == null || d == null || w == null || value.getStreet().trim().length() < 3) return false;
        setAddress(p.name, d.name, w.name, value.getStreet());
        return true;
    }

    @Override protected Parcelable onSaveInstanceState() {
        Bundle state = new Bundle(); state.putParcelable("super", super.onSaveInstanceState());
        state.putStringArray("address", catalog == null ? pending
                : new String[]{province(), district(), ward(), street()});
        return state;
    }
    @Override protected void onRestoreInstanceState(Parcelable value) {
        if (!(value instanceof Bundle)) { super.onRestoreInstanceState(value); return; }
        Bundle state = (Bundle) value; super.onRestoreInstanceState(state.getParcelable("super"));
        String[] parts = state.getStringArray("address");
        if (parts != null && parts.length == 4) setAddress(parts[0], parts[1], parts[2], parts[3]);
    }
    // Each compound editor owns its children's state, avoiding duplicate generated child IDs.
    @Override protected void dispatchSaveInstanceState(SparseArray<Parcelable> container) { dispatchFreezeSelfOnly(container); }
    @Override protected void dispatchRestoreInstanceState(SparseArray<Parcelable> container) { dispatchThawSelfOnly(container); }
    private static String safe(String value) { return value == null ? "" : value.trim(); }
    private static TextWatcher watcher(Runnable action) {
        return new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            public void onTextChanged(CharSequence s, int start, int before, int count) { action.run(); }
            public void afterTextChanged(Editable s) { }
        };
    }
}
