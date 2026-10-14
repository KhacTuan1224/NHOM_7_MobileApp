package com.rescuefarm.ui.navigation;

import androidx.fragment.app.Fragment;
import com.google.firebase.auth.FirebaseAuth;
import com.rescuefarm.MainActivity;
import com.rescuefarm.domain.enums.UserRole;

/** Presentation-only actor checks. Firestore/repository authorization remains authoritative. */
public final class ActorUiPolicy {
    private ActorUiPolicy() { }

    public static boolean isSeller(Fragment fragment) {
        return fragment != null && fragment.requireActivity() instanceof MainActivity
                && ((MainActivity) fragment.requireActivity()).getResolvedActorRole() == UserRole.SELLER;
    }

    /** Hide Customer-only actions while a signed-in account role is still resolving. */
    public static boolean isSellerReadOnly(Fragment fragment) {
        if (isSeller(fragment)) return true;
        if (fragment == null || !(fragment.requireActivity() instanceof MainActivity)) return false;
        MainActivity activity = (MainActivity) fragment.requireActivity();
        return activity.getResolvedActorRole() == null
                && FirebaseAuth.getInstance().getCurrentUser() != null;
    }
}
