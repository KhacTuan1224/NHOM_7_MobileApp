package com.rescuefarm.ui.common;

import android.view.View;
import android.widget.TextView;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import com.rescuefarm.R;

/** Typography and title row shared by detail screens, including dynamically created content. */
public final class DetailTypography {
    private DetailTypography() { }
    public static void bindHeader(Fragment fragment, View root, String title) {
        TextView heading = root.findViewById(R.id.detailPageTitle);
        heading.setText(title);
        root.findViewById(R.id.detailBackButton).setOnClickListener(v ->
                NavHostFragment.findNavController(fragment).navigateUp());
    }
    public static TextView section(TextView view) {
        view.setTextAppearance(R.style.TextAppearance_RescueFarm_Detail_Section);
        view.setAccessibilityHeading(true);
        return view;
    }
    public static TextView body(TextView view) {
        view.setTextAppearance(R.style.TextAppearance_RescueFarm_Detail_Body);
        return view;
    }
    public static TextView meta(TextView view) {
        view.setTextAppearance(R.style.TextAppearance_RescueFarm_Detail_Meta);
        return view;
    }
}
