package com.rescuefarm.ui.campaign;
import com.rescuefarm.ui.common.EnumLabels;

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
import com.rescuefarm.domain.enums.CampaignStatus;
import com.rescuefarm.domain.enums.RescueMode;
import com.rescuefarm.domain.model.RescueCampaign;
import java.util.List;

public class SellerCampaignListFragment extends Fragment {
    private CampaignViewModel viewModel; private LinearLayout container; private List<RescueCampaign> currentCampaigns;
    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater,
            @Nullable ViewGroup parent, @Nullable Bundle state) {
        return inflater.inflate(R.layout.fragment_seller_campaign_list, parent, false);
    }
    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        container = view.findViewById(R.id.sellerCampaignContainer);
        view.findViewById(R.id.addCampaignButton).setOnClickListener(
                Navigation.createNavigateOnClickListener(R.id.action_sellerCampaignListFragment_to_campaignEditorFragment));
        viewModel = new ViewModelProvider(this, new CampaignViewModelFactory(requireContext()))
                .get(CampaignViewModel.class);
        viewModel.getSellerCampaigns().observe(getViewLifecycleOwner(), this::render);
        viewModel.getSellerCampaignsHaveMore().observe(getViewLifecycleOwner(), unused -> {
            if (currentCampaigns != null) render(currentCampaigns);
        });
        viewModel.getState().observe(getViewLifecycleOwner(), value -> {
            if (value.getStatus() == CampaignScreenState.Status.ERROR)
                Toast.makeText(requireContext(), value.getMessage(), Toast.LENGTH_LONG).show();
        });
    }
    @Override public void onResume() { super.onResume(); if (viewModel != null) viewModel.refreshSellerPage(true); }
    private void render(List<RescueCampaign> values) {
        currentCampaigns = values;
        container.removeAllViews(); if (values == null) return;
        for (RescueCampaign campaign : values) container.addView(card(campaign));
        if (viewModel != null && Boolean.TRUE.equals(viewModel.getSellerCampaignsHaveMore().getValue())) {
            Button more = button(R.string.load_more_action);
            more.setOnClickListener(v -> viewModel.refreshSellerPage(false)); container.addView(more);
        }
    }
    private View card(RescueCampaign value) {
        MaterialCardView card = new MaterialCardView(requireContext());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1,-2); params.setMargins(0,0,0,dp(12)); card.setLayoutParams(params);
        card.setRadius(dp(16)); card.setStrokeWidth(dp(1));
        LinearLayout body = new LinearLayout(requireContext()); body.setOrientation(LinearLayout.VERTICAL); body.setPadding(dp(12),dp(12),dp(12),dp(12));
        TextView title = new TextView(requireContext()); title.setText(value.getTitle() + " • " + EnumLabels.label(value.getStatus())); title.setTextSize(17); title.setTypeface(title.getTypeface(),1);
        TextView progress = new TextView(requireContext()); progress.setText(getString(R.string.campaign_progress_format,
                value.calculateProgress(), value.getRescuedQuantity(), value.getTargetQuantity()));
        LinearLayout actions = new LinearLayout(requireContext());
        Button detail = button(R.string.detail_action); detail.setOnClickListener(v -> navigate(R.id.action_sellerCampaignListFragment_to_campaignDetailFragment, value)); actions.addView(detail);
        if (value.getStatus() == CampaignStatus.DRAFT || value.getStatus() == CampaignStatus.REJECTED) {
            Button edit = button(R.string.edit_action); edit.setOnClickListener(v -> navigate(R.id.action_sellerCampaignListFragment_to_campaignEditorFragment, value)); actions.addView(edit);
        }
        if (value.getStatus() == CampaignStatus.ACTIVE && value.getRescueMode() == RescueMode.MOBILE_POINT) {
            Button mobile = button(R.string.mobile_rescue_action); mobile.setOnClickListener(v -> navigate(R.id.action_sellerCampaignListFragment_to_mobileRescueFragment, value)); actions.addView(mobile);
        }
        body.addView(title); body.addView(progress); body.addView(actions); card.addView(body); return card;
    }
    private Button button(int text) { Button value = new Button(requireContext()); value.setText(text); return value; }
    private void navigate(int action, RescueCampaign value) {
        Bundle args = new Bundle(); args.putString("campaignId", value.getId());
        Navigation.findNavController(requireView()).navigate(action, args);
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
