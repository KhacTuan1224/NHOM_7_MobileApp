package com.rescuefarm.ui.profile;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;
import android.text.Editable;
import android.text.TextWatcher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.material.textfield.TextInputEditText;
import com.rescuefarm.ui.common.VietnamAddressView;
import com.rescuefarm.R;
import com.rescuefarm.domain.enums.ApplicationStatus;
import com.rescuefarm.domain.model.SellerApplication;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import com.rescuefarm.service.media.CloudinaryUploadUi;

public class SellerApplicationFragment extends Fragment {
    private ProfileViewModel viewModel; private View form, submit;
    private TextView status; private TextInputEditText representative, shop, proof;
    private VietnamAddressView address;
    private boolean dirty, binding;
    private TextView proofStatus;
    private View proofUploadButton;
    private final ActivityResultLauncher<String> proofPicker = registerForActivityResult(
            new ActivityResultContracts.GetContent(), uri -> CloudinaryUploadUi.upload(this, uri,
                    "sellerProof", com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser().getUid(), 0, proofUploadButton, proofStatus, null,
                    url -> proof.setText(url)));
    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater,
            @Nullable ViewGroup parent, @Nullable Bundle state) {
        return inflater.inflate(R.layout.fragment_seller_application, parent, false);
    }
    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        form = view.findViewById(R.id.sellerApplicationForm); submit = view.findViewById(R.id.submitApplicationButton);
        status = view.findViewById(R.id.applicationStatusText);
        representative = view.findViewById(R.id.representativeInput); shop = view.findViewById(R.id.shopNameInput);
        address = view.findViewById(R.id.shopAddressInput); proof = view.findViewById(R.id.proofImageInput);
        proofStatus = view.findViewById(R.id.proofImageUploadStatus);
        proofUploadButton = view.findViewById(R.id.uploadProofImageButton);
        proofUploadButton.setOnClickListener(v -> proofPicker.launch("image/*"));
        viewModel = new ViewModelProvider(this, new ProfileViewModelFactory(requireContext()))
                .get(ProfileViewModel.class);
        viewModel.getState().observe(getViewLifecycleOwner(), this::render);
        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (!binding) dirty = true;
            }
            @Override public void afterTextChanged(Editable s) { }
        };
        representative.addTextChangedListener(watcher); shop.addTextChangedListener(watcher);
        address.setOnAddressChangedListener(() -> { if (!binding) dirty = true; });
        proof.addTextChangedListener(watcher);
        viewModel.observeSellerApplication().observe(getViewLifecycleOwner(), this::renderApplication);
        submit.setOnClickListener(v -> {
            if (address.validate()) viewModel.submitSellerApplication(text(representative), text(shop),
                    address.formattedAddress(), text(proof));
        });
        viewModel.loadSellerApplication();
    }
    private void render(ProfileScreenState state) {
        if (state.getStatus() == ProfileScreenState.Status.ERROR) {
            Toast.makeText(requireContext(), state.getMessage(), Toast.LENGTH_LONG).show(); return;
        }
        if (state.getStatus() != ProfileScreenState.Status.APPLICATION) return;
        renderApplication(state.getApplication());
    }

    private void renderApplication(SellerApplication application) {
        boolean editable = application == null || application.getStatus() == ApplicationStatus.REJECTED;
        form.setVisibility(editable ? View.VISIBLE : View.GONE);
        if (application == null) {
            status.setText(R.string.application_not_submitted);
            if (!dirty) {
                binding = true;
                representative.setText(""); shop.setText(""); address.clear(); proof.setText("");
                proofStatus.setText(R.string.no_image_selected);
                binding = false;
            }
        }
        else {
            status.setText(getString(R.string.application_status_format, application.getStatus().name()));
            if (editable && !dirty) {
                binding = true;
                representative.setText(application.getRepresentativeName()); shop.setText(application.getShopName());
                address.setFormattedAddress(application.getAddress()); proof.setText(application.getProofImageUrl());
                proofStatus.setText(application.getProofImageUrl() == null || application.getProofImageUrl().isEmpty()
                        ? R.string.no_image_selected : R.string.image_uploaded_status);
                binding = false;
            }
        }
    }
    private static String text(TextInputEditText input) { return input.getText() == null ? "" : input.getText().toString(); }
}
