package com.rescuefarm.ui.admin;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import com.google.android.material.button.MaterialButton;
import com.rescuefarm.R;
import androidx.core.content.ContextCompat;

/** Shared Admin button appearance for programmatically-created actions. */
final class AdminUiStyles {
    private AdminUiStyles() { }

    static void outlinedButton(Context context, MaterialButton button) {
        float density = context.getResources().getDisplayMetrics().density;
        button.setBackgroundTintList(ColorStateList.valueOf(Color.TRANSPARENT));
        button.setStrokeColor(ColorStateList.valueOf(ContextCompat.getColor(context, R.color.rescue_primary)));
        button.setStrokeWidth(Math.round(density));
        button.setTextColor(ContextCompat.getColor(context, R.color.rescue_on_surface));
        button.setCornerRadius(Math.round(14 * density));
        button.setElevation(0);
        button.setInsetTop(0);
        button.setInsetBottom(0);
        button.setAllCaps(false);
    }
}
