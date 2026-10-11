package com.rescuefarm.ui.profile;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import com.google.android.material.card.MaterialCardView;
import com.rescuefarm.R;
import com.rescuefarm.domain.model.Address;

public class AddressListFragment extends Fragment {
    private ProfileViewModel viewModel;
    private LinearLayout container;
    private TextView emptyView;

    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater,
            @Nullable ViewGroup parent, @Nullable Bundle state) {
        return inflater.inflate(R.layout.fragment_address_list, parent, false);
    }
    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        container = view.findViewById(R.id.addressContainer);
        emptyView = view.findViewById(R.id.emptyAddressText);
        view.findViewById(R.id.addAddressButton).setOnClickListener(
                Navigation.createNavigateOnClickListener(R.id.action_addressListFragment_to_addressEditorFragment));
        viewModel = new ViewModelProvider(this, new ProfileViewModelFactory(requireContext()))
                .get(ProfileViewModel.class);
        viewModel.getState().observe(getViewLifecycleOwner(), this::render);
        viewModel.observeAddresses().observe(getViewLifecycleOwner(), this::renderAddresses);
        viewModel.loadAddresses();
    }

    private void render(ProfileScreenState state) {
        if (state.getStatus() == ProfileScreenState.Status.ERROR) {
            Toast.makeText(requireContext(), state.getMessage(), Toast.LENGTH_LONG).show(); return;
        }
        if (state.getStatus() != ProfileScreenState.Status.ADDRESSES) return;
        renderAddresses(state.getAddresses());
    }

    private void renderAddresses(java.util.List<Address> addresses) {
        container.removeAllViews();
        emptyView.setVisibility(addresses.isEmpty() ? View.VISIBLE : View.GONE);
        for (Address address : addresses) container.addView(createCard(address));
    }

    private View createCard(Address address) {
        int padding = dp(16);
        MaterialCardView card = new MaterialCardView(requireContext());
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardParams.setMargins(0, 0, 0, dp(12)); 
        card.setLayoutParams(cardParams);
        card.setCardElevation(dp(2)); // Thêm elevation thay vì stroke
        card.setStrokeWidth(0); // Bỏ stroke
        card.setRadius(dp(12)); // Giảm radius cho modern hơn
        card.setCardBackgroundColor(requireContext().getColor(R.color.rescue_surface));
        
        LinearLayout body = new LinearLayout(requireContext());
        body.setOrientation(LinearLayout.VERTICAL); 
        body.setPadding(padding, padding, padding, padding);
        
        TextView title = new TextView(requireContext());
        title.setText(address.getReceiverName() + (address.isDefault() ? " • Mặc định" : ""));
        title.setTextSize(16); 
        title.setTextColor(requireContext().getColor(R.color.rescue_on_surface));
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        
        TextView detail = new TextView(requireContext());
        detail.setText(address.getReceiverPhone() + "\n" + address.getFormattedAddress());
        detail.setTextSize(14);
        detail.setTextColor(requireContext().getColor(R.color.rescue_on_surface_variant));
        detail.setPadding(0, dp(6), 0, dp(8));
        
        LinearLayout actions = new LinearLayout(requireContext()); 
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setPadding(0, dp(4), 0, 0);
        
        Button edit = new Button(requireContext()); 
        edit.setText(R.string.edit_action);
        edit.setTextSize(12);
        edit.setOnClickListener(v -> Navigation.findNavController(requireView()).navigate(
                R.id.action_addressListFragment_to_addressEditorFragment, addressBundle(address)));
        actions.addView(edit);
        
        if (!address.isDefault()) {
            Button makeDefault = new Button(requireContext()); 
            makeDefault.setText(R.string.set_default_action);
            makeDefault.setTextSize(12);
            makeDefault.setOnClickListener(v -> viewModel.setDefaultAddress(address.getId()));
            
            Button delete = new Button(requireContext()); 
            delete.setText(R.string.delete_action);
            delete.setTextSize(12);
            delete.setOnClickListener(v -> viewModel.deleteAddress(address.getId()));
            
            actions.addView(makeDefault); 
            actions.addView(delete);
        }
        
        body.addView(title); 
        body.addView(detail); 
        body.addView(actions); 
        card.addView(body); 
        return card;
    }

    private Bundle addressBundle(Address value) {
        Bundle bundle = new Bundle(); bundle.putString("id", value.getId());
        bundle.putString("receiverName", value.getReceiverName());
        bundle.putString("receiverPhone", value.getReceiverPhone()); bundle.putString("province", value.getProvince());
        bundle.putString("district", value.getDistrict()); bundle.putString("ward", value.getWard());
        bundle.putString("street", value.getStreet()); bundle.putDouble("latitude", value.getLatitude());
        bundle.putDouble("longitude", value.getLongitude()); bundle.putString("type", value.getType().name());
        bundle.putBoolean("isDefault", value.isDefault()); return bundle;
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
