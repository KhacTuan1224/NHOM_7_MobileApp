package com.rescuefarm.ui.feed;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import com.rescuefarm.R;
import com.rescuefarm.domain.model.RescueCampaign;
import com.rescuefarm.ui.campaign.CampaignScreenState;
import com.rescuefarm.ui.campaign.CampaignViewModel;
import com.rescuefarm.ui.campaign.CampaignViewModelFactory;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Public rescue feed: campaign stories and their community interactions. */
public class FeedFragment extends Fragment {
    private final com.rescuefarm.ui.common.ListPagination pagination = new com.rescuefarm.ui.common.ListPagination();
    private CampaignViewModel viewModel;
    private CampaignFeedCardRenderer renderer;
    private LinearLayout container;
    private ProgressBar progress;
    private List<RescueCampaign> campaigns = new ArrayList<>();

    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater,
            @Nullable ViewGroup parent, @Nullable Bundle state) {
        return inflater.inflate(R.layout.fragment_feed, parent, false);
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        renderer = new CampaignFeedCardRenderer(this);
        container = view.findViewById(R.id.feedContainer);
        progress = view.findViewById(R.id.feedProgress);
        viewModel = new ViewModelProvider(this, new CampaignViewModelFactory(requireContext()))
                .get(CampaignViewModel.class);
        viewModel.getPublicCampaigns().observe(getViewLifecycleOwner(), values -> {
            campaigns = values == null ? new ArrayList<>() : new ArrayList<>(values);
            render();
        });
        viewModel.getState().observe(getViewLifecycleOwner(), value -> {
            render();
            if (progress != null) progress.setVisibility(value.getStatus() == CampaignScreenState.Status.LOADING
                    ? View.VISIBLE : View.GONE);
            if (value.getStatus() == CampaignScreenState.Status.ERROR)
                Toast.makeText(requireContext(), value.getMessage(), Toast.LENGTH_LONG).show();
        });
        view.findViewById(R.id.refreshFeedButton).setOnClickListener(v -> { pagination.reset(); viewModel.refreshPublicPage(true); });
        viewModel.refreshPublicPage(true);
    }

    private void render() {
        if (container == null) return;
        pagination.remote(viewModel::hasMorePublicCampaigns, viewModel::isPublicPageLoading,
                () -> viewModel.refreshPublicPage(false)).render(container, campaigns, this::renderPage);
    }
    private void renderPage(List<RescueCampaign> campaigns) {
        if (!isAdded() || container == null) return;
        container.removeAllViews();
        if (campaigns.isEmpty()) {
            container.addView(renderer.message("Chưa có chiến dịch giải cứu đang hoạt động."));
            return;
        }
        for (RescueCampaign campaign : campaigns) {
            container.addView(renderer.card(campaign, Collections.emptyList(), v -> {
                Bundle args = new Bundle(); args.putString("campaignId", campaign.getId());
                Navigation.findNavController(v).navigate(
                        R.id.action_feedFragment_to_campaignDetailFragment, args);
            }));
        }
    }
}
