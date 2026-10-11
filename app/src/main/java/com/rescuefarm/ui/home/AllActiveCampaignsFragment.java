package com.rescuefarm.ui.home;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.Space;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import com.rescuefarm.R;
import com.rescuefarm.domain.enums.UrgencyLevel;
import com.rescuefarm.domain.model.RescueCampaign;
import java.util.ArrayList;
import java.util.List;

public class AllActiveCampaignsFragment extends Fragment {
    private HomeViewModel viewModel;
    private HomeCardRenderer renderer;
    private LinearLayout campaignContainer;
    private TextView messageView;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_all_active_campaigns, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        renderer = new HomeCardRenderer(this);
        campaignContainer = view.findViewById(R.id.campaignContainer);
        messageView = view.findViewById(R.id.messageView);

        viewModel = new ViewModelProvider(requireActivity(), new HomeViewModelFactory(requireContext()))
                .get(HomeViewModel.class);
        
        viewModel.getHomeState().observe(getViewLifecycleOwner(), state -> {
            // Filter out critical campaigns
            List<RescueCampaign> otherCampaigns = new ArrayList<>();
            for (RescueCampaign campaign : state.getActiveCampaigns()) {
                if (campaign.getUrgencyLevel() != UrgencyLevel.CRITICAL) {
                    otherCampaigns.add(campaign);
                }
            }
            renderCampaigns(otherCampaigns, state);
        });
    }

    private void renderCampaigns(List<RescueCampaign> campaigns, HomeViewState state) {
        campaignContainer.removeAllViews();
        
        if (campaigns == null || campaigns.isEmpty()) {
            messageView.setVisibility(View.VISIBLE);
            messageView.setText("Chưa có chiến dịch nào đang diễn ra.");
            campaignContainer.setVisibility(View.GONE);
            return;
        }
        
        messageView.setVisibility(View.GONE);
        campaignContainer.setVisibility(View.VISIBLE);
        
        // Render in 2 columns
        for (int index = 0; index < campaigns.size(); index += 2) {
            LinearLayout row = new LinearLayout(requireContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setClipChildren(false);
            row.setClipToPadding(false);
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, -2);
            rowParams.setMargins(0, dp(2), 0, dp(5));
            campaignContainer.addView(row, rowParams);

            RescueCampaign firstCampaign = campaigns.get(index);
            View first = renderer.campaign(firstCampaign, state.distanceFor(firstCampaign.getId()), 
                    state.getValueProducts(), v -> openCampaign(firstCampaign.getId()));
            LinearLayout.LayoutParams firstParams = new LinearLayout.LayoutParams(0, -2, 1f);
            firstParams.setMargins(0, 0, dp(6), 0);
            row.addView(first, firstParams);
            
            if (index + 1 < campaigns.size()) {
                RescueCampaign secondCampaign = campaigns.get(index + 1);
                View second = renderer.campaign(secondCampaign, state.distanceFor(secondCampaign.getId()), 
                        state.getValueProducts(), v -> openCampaign(secondCampaign.getId()));
                row.addView(second, new LinearLayout.LayoutParams(0, -2, 1f));
            } else {
                row.addView(new Space(requireContext()), 
                        new LinearLayout.LayoutParams(0, 1, 1f));
            }
        }
    }

    private void openCampaign(String id) {
        Bundle args = new Bundle();
        args.putString("campaignId", id);
        Navigation.findNavController(requireView()).navigate(
                R.id.action_allActiveCampaignsFragment_to_campaignDetailFragment, args);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
