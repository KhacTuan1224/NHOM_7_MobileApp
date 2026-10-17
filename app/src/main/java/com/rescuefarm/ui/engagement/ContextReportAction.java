package com.rescuefarm.ui.engagement;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Toast;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import com.google.firebase.auth.FirebaseAuth;
import com.google.android.material.button.MaterialButton;
import com.rescuefarm.MainActivity;
import com.rescuefarm.R;
import com.rescuefarm.domain.enums.UserRole;
/** Context IDs stay internal; the report form only asks for the user's explanation. */
public final class ContextReportAction {
    private ContextReportAction() { }
    public static void add(Fragment fragment, LinearLayout parent, String type, String id, String ownerId, String label) {
        MaterialButton button = new MaterialButton(fragment.requireContext(), null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle);
        button.setText(label); parent.addView(button);
        Runnable visibility = () -> {
            String uid = FirebaseAuth.getInstance().getCurrentUser() == null ? "" : FirebaseAuth.getInstance().getCurrentUser().getUid();
            boolean customer = fragment.requireActivity() instanceof MainActivity
                    && ((MainActivity) fragment.requireActivity()).getResolvedActorRole() == UserRole.CUSTOMER;
            button.setVisibility(customer && !uid.isEmpty() && !uid.equals(ownerId) ? View.VISIBLE : View.GONE);
        };
        visibility.run();
        if (fragment.requireActivity() instanceof MainActivity)
            ((MainActivity) fragment.requireActivity()).observeActorRole().observe(fragment.getViewLifecycleOwner(), role -> visibility.run());
        button.setOnClickListener(v -> {
            if (!(fragment.requireActivity() instanceof MainActivity)
                    || ((MainActivity) fragment.requireActivity()).getResolvedActorRole() != UserRole.CUSTOMER) {
                Toast.makeText(fragment.requireContext(), "Cần đăng nhập tài khoản khách hàng để báo cáo.", Toast.LENGTH_SHORT).show(); return;
            }
            Bundle args = new Bundle(); args.putString("targetType", type); args.putString("targetId", id);
            NavHostFragment.findNavController(fragment).navigate(R.id.reportFragment, args);
        });
    }
}
