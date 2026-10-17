package com.rescuefarm.ui.profile;

import android.os.Bundle;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import com.google.android.material.button.MaterialButton;
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
        if (com.rescuefarm.ui.navigation.ActorUiPolicy.isSeller(this)) {
            androidx.navigation.fragment.NavHostFragment.findNavController(this).navigate(R.id.sellerProfileFragment);
            return;
        }

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
        card.setCardElevation(0);
        card.setStrokeWidth(dp(1));
        card.setStrokeColor(requireContext().getColor(R.color.rescue_outline));
        card.setRadius(dp(14));
        card.setCardBackgroundColor(requireContext().getColor(R.color.rescue_surface));
        
        LinearLayout body = new LinearLayout(requireContext());
        body.setOrientation(LinearLayout.VERTICAL); 
        body.setPadding(padding, padding, padding, padding);
        
        LinearLayout heading = new LinearLayout(requireContext());
        heading.setOrientation(LinearLayout.HORIZONTAL);
        heading.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(requireContext());
        title.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        title.setText(address.getReceiverName() + (address.isDefault() ? " • Mặc định" : ""));
        title.setTextSize(15);
        title.setMaxLines(2);
        title.setTextColor(requireContext().getColor(R.color.rescue_on_surface));
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);

        MaterialButton edit = actionButton(R.string.edit_action,
                R.color.rescue_primary, R.color.rescue_surface, R.color.rescue_outline);
        edit.setOnClickListener(v -> Navigation.findNavController(requireView()).navigate(
                R.id.action_addressListFragment_to_addressEditorFragment, addressBundle(address)));
        heading.addView(title);
        LinearLayout.LayoutParams editParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(40));
        editParams.setMarginStart(dp(8));
        heading.addView(edit, editParams);
        
        TextView detail = new TextView(requireContext());
        detail.setText(address.getReceiverPhone() + "\n" + address.getFormattedAddress());
        detail.setTextSize(14);
        detail.setTextColor(requireContext().getColor(R.color.rescue_on_surface_variant));
        detail.setPadding(0, dp(8), 0, 0);
        
        LinearLayout actions = new LinearLayout(requireContext()); 
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        actions.setPadding(0, dp(8), 0, 0);
        
        if (!address.isDefault()) {
            MaterialButton makeDefault = actionButton(R.string.set_default_action,
                    R.color.rescue_on_primary_container, R.color.rescue_primary_container, 0);
            makeDefault.setOnClickListener(v -> viewModel.setDefaultAddress(address.getId()));
            
            MaterialButton delete = actionButton(R.string.delete_action,
                    R.color.rescue_critical, R.color.rescue_surface, R.color.rescue_outline);
            delete.setOnClickListener(v -> viewModel.deleteAddress(address.getId()));
            
            actions.addView(makeDefault, actionLayoutParams());
            actions.addView(delete, actionLayoutParams());
        }
        
        body.addView(heading);
        body.addView(detail); 
        if (!address.isDefault()) body.addView(actions);
        card.addView(body); 
        return card;
    }

    private MaterialButton actionButton(int label, int textColor, int backgroundColor, int strokeColor) {
        MaterialButton button = new MaterialButton(requireContext());
        button.setText(label);
        button.setTextSize(12);
        button.setAllCaps(false);
        button.setMinWidth(0);
        button.setMinHeight(dp(40));
        button.setInsetTop(0);
        button.setInsetBottom(0);
        button.setCornerRadius(dp(10));
        button.setPadding(dp(12), 0, dp(12), 0);
        button.setTextColor(requireContext().getColor(textColor));
        button.setBackgroundTintList(ColorStateList.valueOf(requireContext().getColor(backgroundColor)));
        if (strokeColor != 0) {
            button.setStrokeWidth(dp(1));
            button.setStrokeColor(ColorStateList.valueOf(requireContext().getColor(strokeColor)));
        } else {
            button.setStrokeWidth(0);
        }
        return button;
    }

    private LinearLayout.LayoutParams actionLayoutParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(40));
        params.setMarginStart(dp(8));
        return params;
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
