package com.rescuefarm.ui.rescue;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import com.rescuefarm.R;

/** Entry point: each rescue method opens its own editor destination and layout. */
public final class RescueOfferEditorFragment extends Fragment {
    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater,
            @Nullable ViewGroup parent, @Nullable Bundle state) {
        return inflater.inflate(R.layout.fragment_rescue_offer_editor, parent, false);
    }
    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        super.onViewCreated(view, state);
        view.findViewById(R.id.rescueEditorBack).setOnClickListener(v -> NavHostFragment.findNavController(this).navigateUp());
        view.findViewById(R.id.rescueChooseMystery).setOnClickListener(v -> NavHostFragment.findNavController(this).navigate(R.id.mysteryBoxEditorFragment));
        view.findViewById(R.id.rescueChooseSubscription).setOnClickListener(v -> NavHostFragment.findNavController(this).navigate(R.id.subscriptionOfferEditorFragment));
        view.findViewById(R.id.rescueChooseFlash).setOnClickListener(v -> NavHostFragment.findNavController(this).navigate(R.id.flashRescueEditorFragment));
    }
}
