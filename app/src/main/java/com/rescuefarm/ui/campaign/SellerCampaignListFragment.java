package com.rescuefarm.ui.campaign;
import com.rescuefarm.ui.common.EnumLabels;
import com.rescuefarm.ui.seller.SellerUiStyles;

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
        SellerUiStyles.styleActions(view, SellerUiStyles.Section.CAMPAIGNS);
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
        SellerUiStyles.Row row = SellerUiStyles.row(requireContext(), SellerUiStyles.Section.CAMPAIGNS);
        row.title(value.getTitle()); row.body("Trạng thái: " + EnumLabels.label(value.getStatus()));
        row.body(getString(R.string.campaign_progress_format,
                value.calculateProgress(), value.getRescuedQuantity(), value.getTargetQuantity()));
        row.action("Chi tiết", "Xem chi tiết chiến dịch", v -> navigate(R.id.action_sellerCampaignListFragment_to_campaignDetailFragment, value));
        if (value.getStatus() == CampaignStatus.DRAFT || value.getStatus() == CampaignStatus.REJECTED) {
            row.action("Sửa", "Chỉnh sửa chiến dịch", v -> navigate(R.id.action_sellerCampaignListFragment_to_campaignEditorFragment, value));
        }
        if (value.getStatus() == CampaignStatus.ACTIVE && value.getRescueMode() == RescueMode.MOBILE_POINT) {
            row.action("Vị trí", getString(R.string.mobile_rescue_action), v -> navigate(R.id.action_sellerCampaignListFragment_to_mobileRescueFragment, value));
        }
        return row.root;
    }
    private Button button(int text) { return SellerUiStyles.button(requireContext(), SellerUiStyles.Section.CAMPAIGNS,
            getString(text), getString(text)); }
    private void navigate(int action, RescueCampaign value) {
        Bundle args = new Bundle(); args.putString("campaignId", value.getId());
        Navigation.findNavController(requireView()).navigate(action, args);
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
