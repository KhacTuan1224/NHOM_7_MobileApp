package com.rescuefarm.ui.profile;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.bumptech.glide.Glide;
import androidx.lifecycle.ViewModelProvider;
import com.rescuefarm.R;

/** Public seller card for customers; data comes from the intentionally public mirror collection. */
public class PublicSellerProfileFragment extends Fragment {
    private TextView name, description, address;
    private ImageView avatar;

    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater,
            @Nullable ViewGroup parent, @Nullable Bundle state) {
        return inflater.inflate(R.layout.fragment_public_seller_profile, parent, false);
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        view.findViewById(R.id.publicSellerBackButton).setOnClickListener(v ->
                requireActivity().getOnBackPressedDispatcher().onBackPressed());
        name = view.findViewById(R.id.publicSellerName); description = view.findViewById(R.id.publicSellerDescription);
        address = view.findViewById(R.id.publicSellerAddress);
        avatar = view.findViewById(R.id.publicSellerAvatar);
        TextView message = view.findViewById(R.id.publicSellerMessage);
        View content = view.findViewById(R.id.publicSellerContent);
        String sellerId = getArguments() == null ? "" : getArguments().getString("sellerId", "");
        if (blank(sellerId)) { message.setText(R.string.public_seller_not_found); return; }
        StorefrontViewModel vm = new ViewModelProvider(this,new StorefrontViewModelFactory(requireContext())).get(StorefrontViewModel.class);
        android.widget.LinearLayout reportActions = view.findViewById(R.id.publicSellerReportActions);
        com.rescuefarm.ui.engagement.ContextReportAction.add(this, reportActions, "USER", sellerId,
                sellerId, getString(R.string.public_seller_report));
        for (int i = 0; i < reportActions.getChildCount(); i++) {
            View action = reportActions.getChildAt(i);
            action.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            if (action instanceof TextView) ((TextView) action).setTextSize(16);
        }
        vm.observe(sellerId).observe(getViewLifecycleOwner(), profile -> {
                    if (profile == null || !profile.configured) {
                        content.setVisibility(View.GONE);
                        message.setVisibility(View.VISIBLE);
                        message.setText(R.string.public_seller_not_configured);
                        return;
                    }
                    message.setVisibility(View.GONE);
                    content.setVisibility(View.VISIBLE);
                    name.setText(value(profile.shopName, getString(R.string.public_seller_name_fallback)));
                    description.setText(value(profile.description, getString(R.string.public_seller_description_fallback)));
                    address.setText(value(profile.address, getString(R.string.public_seller_address_fallback)));
                    String url = profile.avatarUrl;
                    Glide.with(this).load(blank(url) ? null : url).placeholder(R.drawable.ic_store)
                            .error(R.drawable.ic_store).circleCrop().into(avatar);
                });
    }

    private String value(String value, String fallback) { return blank(value) ? fallback : value; }
    private boolean blank(String value) { return value == null || value.trim().isEmpty(); }
}
