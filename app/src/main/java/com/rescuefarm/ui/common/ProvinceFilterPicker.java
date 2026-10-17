package com.rescuefarm.ui.common;

import android.text.Editable;
import android.text.TextWatcher;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.rescuefarm.R;
import com.rescuefarm.service.address.VietnamAddressCatalog;
import com.rescuefarm.service.address.VietnamAddressCatalog.Unit;
import java.util.ArrayList;
import java.util.List;

/** Selection-only province filter backed by the shared offline address catalog. */
public final class ProvinceFilterPicker {
    private ProvinceFilterPicker() { }

    public static void bind(TextInputEditText input) {
        input.setFocusable(false);
        input.setClickable(true);
        input.setInputType(android.text.InputType.TYPE_NULL);
        input.setKeyListener(null);
        input.setEnabled(false);
        android.view.ViewParent parent = input.getParent();
        while (parent != null && !(parent instanceof TextInputLayout)) parent = parent.getParent();
        TextInputLayout box = parent instanceof TextInputLayout ? (TextInputLayout) parent : null;
        if (box != null) {
            box.setEndIconMode(TextInputLayout.END_ICON_CUSTOM);
            box.setEndIconDrawable(android.R.drawable.arrow_down_float);
            box.setEndIconContentDescription(R.string.province_label);
            box.setEndIconOnClickListener(v -> input.performClick());
            box.setPlaceholderText(input.getContext().getString(R.string.address_catalog_loading));
        }
        VietnamAddressView.load(input.getContext().getApplicationContext()).whenComplete((catalog, error) -> input.post(() -> {
            if (error != null) {
                if (box != null) box.setError(input.getContext().getString(R.string.address_catalog_error));
                return;
            }
            input.setEnabled(true);
            if (box != null) box.setPlaceholderText(input.getContext().getString(R.string.discovery_all_provinces));
            input.setOnClickListener(v -> choose(input, catalog));
        }));
    }

    private static void choose(TextInputEditText input, VietnamAddressCatalog catalog) {
        android.content.Context context = input.getContext();
        float density = input.getResources().getDisplayMetrics().density;
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        int padding = Math.round(20 * density);
        content.setPadding(padding, 0, padding, 0);
        EditText search = new EditText(context);
        search.setSingleLine(true);
        search.setHint(R.string.address_search_hint);
        content.addView(search);
        ListView list = new ListView(context);
        list.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        content.addView(list, new LinearLayout.LayoutParams(-1,
                Math.min(Math.round(360 * density), input.getResources().getDisplayMetrics().heightPixels / 2)));
        Unit selected = VietnamAddressCatalog.find(catalog.provinces(),
                input.getText() == null ? "" : input.getText().toString());
        List<Unit> filtered = new ArrayList<>();
        ArrayAdapter<String> adapter = new ArrayAdapter<>(context, android.R.layout.simple_list_item_single_choice);
        list.setAdapter(adapter);
        Runnable update = () -> {
            String query = VietnamAddressCatalog.normalize(search.getText().toString());
            filtered.clear();
            filtered.add(null); // Clearing the region is always available.
            for (Unit unit : catalog.provinces())
                if (VietnamAddressCatalog.normalize(unit.name).contains(query)) filtered.add(unit);
            adapter.clear();
            int checked = -1;
            for (int i = 0; i < filtered.size(); i++) {
                Unit unit = filtered.get(i);
                adapter.add(unit == null ? context.getString(R.string.discovery_all_provinces) : unit.name);
                if (unit == selected) checked = i;
            }
            list.clearChoices();
            if (checked >= 0) list.setItemChecked(checked, true);
            adapter.notifyDataSetChanged();
        };
        update.run();
        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.province_label).setView(content)
                .setNegativeButton(R.string.cancel_action, null).create();
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { update.run(); }
            @Override public void afterTextChanged(Editable s) { }
        });
        list.setOnItemClickListener((parent, row, position, id) -> {
            Unit unit = filtered.get(position);
            input.setText(unit == null ? "" : unit.name);
            dialog.dismiss();
        });
        content.setFocusableInTouchMode(true);
        content.requestFocus();
        dialog.show();
        if (selected != null) list.setSelection(catalog.provinces().indexOf(selected) + 1);
    }
}
