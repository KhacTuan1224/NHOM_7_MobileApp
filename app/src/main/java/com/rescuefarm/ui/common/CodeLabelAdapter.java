package com.rescuefarm.ui.common;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

/** Translates server filter codes without changing the value sent back to the server. */
public final class CodeLabelAdapter extends ArrayAdapter<String> {
    public CodeLabelAdapter(Context context, String[] codes) {
        super(context, android.R.layout.simple_spinner_dropdown_item, codes);
    }
    @Override public View getView(int position, View convertView, ViewGroup parent) {
        View view = super.getView(position, convertView, parent);
        ((TextView) view).setText(displayLabel(getItem(position)));
        return view;
    }
    @Override public View getDropDownView(int position, View convertView, ViewGroup parent) {
        View view = super.getDropDownView(position, convertView, parent);
        ((TextView) view).setText(displayLabel(getItem(position)));
        return view;
    }
    private String displayLabel(String code) {
        if (code == null) return "";
        int separator = code.indexOf(':');
        if (separator > 0) {
            String prefix = code.substring(0, separator);
            String value = code.substring(separator + 1);
            String title = "FULFILLMENT".equals(prefix) ? "Nhận hàng: "
                    : "PAYMENT".equals(prefix) ? "Thanh toán: " : "Trạng thái thanh toán: ";
            return title + EnumLabels.label(value);
        }
        return EnumLabels.hasLabel(code) ? EnumLabels.label(code) : code;
    }
}
