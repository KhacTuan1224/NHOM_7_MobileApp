package com.rescuefarm.ui.profile;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;
import android.text.Editable;
import android.text.TextWatcher;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import com.google.android.material.textfield.TextInputEditText;
import com.rescuefarm.R;
import com.rescuefarm.domain.enums.AddressType;
import com.rescuefarm.ui.common.VietnamAddressView;
import java.util.Map;

public class AddressEditorFragment extends Fragment {
    private ProfileViewModel viewModel;
    private TextInputEditText name, phone;
    private VietnamAddressView address;
    private RadioGroup typeGroup; private CheckBox defaultCheck; private TextView locationText;
    private String id = ""; private double latitude, longitude;
    private boolean dirty, binding;
    private final ActivityResultLauncher<String[]> permissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(), this::onPermissionResult);

    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater,
            @Nullable ViewGroup parent, @Nullable Bundle state) {
        return inflater.inflate(R.layout.fragment_address_editor, parent, false);
    }
    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        name = view.findViewById(R.id.receiverNameInput); phone = view.findViewById(R.id.receiverPhoneInput);
        address = view.findViewById(R.id.addressForm);
        typeGroup = view.findViewById(R.id.addressTypeGroup); defaultCheck = view.findViewById(R.id.defaultAddressCheck);
        locationText = view.findViewById(R.id.addressLocationText);
        readArguments();
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
        name.addTextChangedListener(watcher); phone.addTextChangedListener(watcher);
        address.setOnAddressChangedListener(() -> {
            if (!binding) { dirty = true; latitude = 0; longitude = 0; showCoordinates(); }
        });
        defaultCheck.setOnCheckedChangeListener((button, checked) -> { if (!binding) dirty = true; });
        typeGroup.setOnCheckedChangeListener((group, checkedId) -> { if (!binding) dirty = true; });
        if (!id.isEmpty()) viewModel.observeAddress(id).observe(getViewLifecycleOwner(), value -> {
            if (value != null && !dirty) populate(value);
        });
        view.findViewById(R.id.useAddressLocationButton).setOnClickListener(v -> requestLocation());
        view.findViewById(R.id.saveAddressButton).setOnClickListener(v -> {
            if (!address.validate()) return;
            viewModel.saveAddress(id, text(name), text(phone), address.province(), address.district(),
                    address.ward(), address.street(), latitude, longitude, selectedType(), defaultCheck.isChecked());
        });
    }

    private void populate(com.rescuefarm.domain.model.Address value) {
        binding = true;
        name.setText(value.getReceiverName()); phone.setText(value.getReceiverPhone());
        address.setAddress(value.getProvince(), value.getDistrict(), value.getWard(), value.getStreet());
        latitude = value.getLatitude(); longitude = value.getLongitude();
        defaultCheck.setChecked(value.isDefault());
        if (value.getType() == AddressType.WORK) typeGroup.check(R.id.addressTypeWork);
        else if (value.getType() == AddressType.OTHER) typeGroup.check(R.id.addressTypeOther);
        else typeGroup.check(R.id.addressTypeHome);
        binding = false;
        showCoordinates();
    }

    private void readArguments() {
        Bundle args = getArguments(); if (args == null) return;
        id = safe(args.getString("id")); name.setText(args.getString("receiverName"));
        phone.setText(args.getString("receiverPhone"));
        address.setAddress(args.getString("province"), args.getString("district"), args.getString("ward"), args.getString("street"));
        latitude = args.getDouble("latitude");
        longitude = args.getDouble("longitude"); defaultCheck.setChecked(args.getBoolean("isDefault"));
        String type = args.getString("type");
        if (AddressType.WORK.name().equals(type)) typeGroup.check(R.id.addressTypeWork);
        else if (AddressType.OTHER.name().equals(type)) typeGroup.check(R.id.addressTypeOther);
        showCoordinates();
    }
    private void render(ProfileScreenState state) {
        if (state.getStatus() == ProfileScreenState.Status.LOCATION) {
            latitude = state.getLatitude(); longitude = state.getLongitude(); showCoordinates();
            if (state.getGeocodedAddress() != null) {
                address.setAddress(state.getGeocodedAddress().getProvince(), state.getGeocodedAddress().getDistrict(),
                        state.getGeocodedAddress().getWard(), state.getGeocodedAddress().getStreet());
            }
            if (state.getMessage() != null) {
                Toast.makeText(requireContext(), state.getMessage(), Toast.LENGTH_LONG).show();
            }
        } else if (state.getStatus() == ProfileScreenState.Status.SAVED) {
            Toast.makeText(requireContext(), state.getMessage(), Toast.LENGTH_SHORT).show();
            NavHostFragment.findNavController(this).popBackStack();
        } else if (state.getStatus() == ProfileScreenState.Status.ERROR) {
            Toast.makeText(requireContext(), state.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
    private void requestLocation() {
        if (hasPermission()) viewModel.requestCurrentLocation(true);
        else permissionLauncher.launch(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION});
    }
    private void onPermissionResult(Map<String, Boolean> result) {
        if (Boolean.TRUE.equals(result.get(Manifest.permission.ACCESS_FINE_LOCATION))
                || Boolean.TRUE.equals(result.get(Manifest.permission.ACCESS_COARSE_LOCATION))) {
            viewModel.requestCurrentLocation(true);
        } else viewModel.onLocationPermissionDenied();
    }
    private boolean hasPermission() {
        return ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }
    private AddressType selectedType() {
        if (typeGroup.getCheckedRadioButtonId() == R.id.addressTypeWork) return AddressType.WORK;
        if (typeGroup.getCheckedRadioButtonId() == R.id.addressTypeOther) return AddressType.OTHER;
        return AddressType.HOME;
    }
    private void showCoordinates() { locationText.setText(getString(R.string.coordinates_format, latitude, longitude)); }
    private static String text(TextInputEditText input) { return input.getText() == null ? "" : input.getText().toString(); }
    @Override public void onViewStateRestored(@Nullable Bundle state) {
        super.onViewStateRestored(state);
        if (state != null) { dirty = true; latitude = state.getDouble("latitude"); longitude = state.getDouble("longitude"); showCoordinates(); }
    }
    @Override public void onSaveInstanceState(@NonNull Bundle state) {
        super.onSaveInstanceState(state); state.putDouble("latitude", latitude); state.putDouble("longitude", longitude);
    }
    private static String safe(String value) { return value == null ? "" : value; }
}
