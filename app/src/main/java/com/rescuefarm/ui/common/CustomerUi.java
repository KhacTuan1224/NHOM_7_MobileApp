package com.rescuefarm.ui.common;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import androidx.fragment.app.Fragment;
import com.google.android.material.button.MaterialButton;
import com.rescuefarm.R;

/** Shared spacing for the small customer forms and public storefront. */
public final class CustomerUi {
    private CustomerUi() { }
    public static LinearLayout column(Context c) {
        LinearLayout column = new LinearLayout(c); column.setOrientation(LinearLayout.VERTICAL); return column;
    }
    public static View screen(Fragment fragment, LinearLayout content) {
        Context c = fragment.requireContext(); NavigationAwareNestedScrollView scroll = new NavigationAwareNestedScrollView(c);
        scroll.setBackgroundColor(c.getColor(R.color.rescue_background)); scroll.setFillViewport(true);
        content.setPadding(dp(c,16), dp(c,16), dp(c,16), dp(c,16));
        ResponsiveContentLayout wrapper = new ResponsiveContentLayout(c);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.setMaxContentWidth(c.getResources().getDimensionPixelSize(R.dimen.form_max_width));
        wrapper.addView(content, new LinearLayout.LayoutParams(-1, -2));
        android.widget.FrameLayout.LayoutParams params = new android.widget.FrameLayout.LayoutParams(-1, -2);
        params.gravity = android.view.Gravity.CENTER_HORIZONTAL;
        scroll.addView(wrapper, params); return scroll;
    }
    public static TextView text(LinearLayout parent, String value) {
        TextView text = new TextView(parent.getContext()); text.setText(value); text.setTextSize(16);
        AppTypography.body(text);
        text.setTextColor(parent.getContext().getColor(R.color.rescue_on_surface));
        text.setPadding(0,dp(parent.getContext(),8),0,dp(parent.getContext(),8));
        parent.addView(text,new LinearLayout.LayoutParams(-1,-2)); return text;
    }
    public static EditText input(LinearLayout parent, String hint, int type) {
        text(parent,hint); EditText input=new EditText(parent.getContext()); input.setHint(hint);
        input.setInputType(type); input.setMinHeight(dp(parent.getContext(),48));
        parent.addView(input,new LinearLayout.LayoutParams(-1,-2)); return input;
    }
    public static MaterialButton button(LinearLayout parent,String text,View.OnClickListener click) {
        MaterialButton b=new MaterialButton(parent.getContext()); b.setText(text); b.setAllCaps(false);
        b.setOnClickListener(click); parent.addView(b,new LinearLayout.LayoutParams(-1,-2)); return b;
    }
    public static int dp(Context c,int n) { return Math.round(n*c.getResources().getDisplayMetrics().density); }
    public static String value(EditText input) { return input.getText().toString().trim(); }
}
