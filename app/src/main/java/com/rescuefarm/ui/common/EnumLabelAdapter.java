package com.rescuefarm.ui.common;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.Arrays;
import java.util.List;

/** Displays translated labels while preserving typed enum selections. */
public final class EnumLabelAdapter<T extends Enum<T>> extends ArrayAdapter<T> {
    public EnumLabelAdapter(Context context, T[] values) { this(context, Arrays.asList(values)); }
    public EnumLabelAdapter(Context context, List<T> values) {
        super(context, android.R.layout.simple_spinner_dropdown_item, values);
        setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    }
    @NonNull @Override public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        View view = super.getView(position, convertView, parent);
        ((TextView) view).setText(EnumLabels.label(getItem(position)));
        return view;
    }
    @Override public View getDropDownView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        View view = super.getDropDownView(position, convertView, parent);
        ((TextView) view).setText(EnumLabels.label(getItem(position)));
        return view;
    }
}
