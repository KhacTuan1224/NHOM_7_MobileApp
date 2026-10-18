package com.rescuefarm.ui.campaign;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import com.google.android.material.card.MaterialCardView;
import com.rescuefarm.R;
import com.rescuefarm.domain.model.RescueCampaign;
import com.rescuefarm.service.location.CampaignLocationService;
import com.rescuefarm.service.location.LocationProvider;
import com.rescuefarm.service.location.LocationProviderFactory;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class CampaignListFragment extends Fragment {
    private final com.rescuefarm.ui.common.ListPagination pagination = new com.rescuefarm.ui.common.ListPagination();
    private CampaignViewModel viewModel; private LinearLayout container; private TextView empty;
    private final CampaignLocationService distanceService = new CampaignLocationService();
    private LocationProvider locationProvider; private List<RescueCampaign> campaigns = new ArrayList<>();
    private Double nearbyLatitude, nearbyLongitude;
    private final ActivityResultLauncher<String[]> permissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(), this::onPermissions);
    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater,
            @Nullable ViewGroup parent, @Nullable Bundle state) {
        return inflater.inflate(R.layout.fragment_campaign_list, parent, false);
    }
    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        container = view.findViewById(R.id.campaignContainer); empty = view.findViewById(R.id.emptyCampaignText);
        locationProvider = LocationProviderFactory.create(requireContext());
        viewModel = new ViewModelProvider(this, new CampaignViewModelFactory(requireContext()))
                .get(CampaignViewModel.class);
        viewModel.getPublicCampaigns().observe(getViewLifecycleOwner(), values -> {
            campaigns = values == null ? new ArrayList<>() : values; render();
        });
        viewModel.getState().observe(getViewLifecycleOwner(), value -> {
            render();
            if (value.getStatus() == CampaignScreenState.Status.ERROR)
                Toast.makeText(requireContext(), value.getMessage(), Toast.LENGTH_LONG).show();
        });
        view.findViewById(R.id.refreshCampaignButton).setOnClickListener(v -> { pagination.reset(); viewModel.refreshPublicPage(true); });
        view.findViewById(R.id.nearbyCampaignButton).setOnClickListener(v -> requestNearby());
        view.findViewById(R.id.allCampaignButton).setOnClickListener(v -> {
            nearbyLatitude = null; nearbyLongitude = null; render();
        });
        viewModel.refreshPublicPage(true);
    }
    private void requestNearby() {
        boolean fine = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        boolean coarse = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        if (!fine && !coarse) {
            permissionLauncher.launch(new String[]{ Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION }); return;
        }
        loadNearby();
    }
    private void onPermissions(Map<String, Boolean> values) {
        if (Boolean.TRUE.equals(values.get(Manifest.permission.ACCESS_FINE_LOCATION))
                || Boolean.TRUE.equals(values.get(Manifest.permission.ACCESS_COARSE_LOCATION))) loadNearby();
        else Toast.makeText(requireContext(), R.string.location_permission_required, Toast.LENGTH_LONG).show();
    }
    private void loadNearby() {
        locationProvider.getCurrentLocation(new LocationProvider.LocationCallback() {
            @Override public void onLocationAvailable(double latitude, double longitude) {
                nearbyLatitude = latitude; nearbyLongitude = longitude; render();
            }
            @Override public void onLocationUnavailable(String message) {
                Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
            }
        });
    }
    private void render() {
        if (container == null) return;
        container.removeAllViews(); List<RescueCampaign> shown = new ArrayList<>(); Date now = new Date();
        for (RescueCampaign campaign : campaigns) {
            if (nearbyLatitude == null || distanceService.isEligibleForNearby(campaign, now)) shown.add(campaign);
        }
        if (nearbyLatitude != null) shown.sort(Comparator.comparingDouble(value ->
                distanceService.distanceToCampaign(nearbyLatitude, nearbyLongitude, value, now)));
        empty.setVisibility(shown.isEmpty() ? View.VISIBLE : View.GONE);
        pagination.remote(viewModel::hasMorePublicCampaigns, viewModel::isPublicPageLoading,
                () -> viewModel.refreshPublicPage(false)).render(container, shown, page -> {
            container.removeAllViews();
            for (RescueCampaign campaign : page) container.addView(card(campaign, now));
        });
    }
    private View card(RescueCampaign campaign, Date now) {
        MaterialCardView card = new MaterialCardView(requireContext());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2); params.setMargins(0,0,0,dp(12));
        card.setLayoutParams(params); card.setRadius(dp(16)); card.setStrokeWidth(dp(1));
        LinearLayout body = new LinearLayout(requireContext()); body.setOrientation(LinearLayout.VERTICAL); body.setPadding(dp(14),dp(14),dp(14),dp(14));
        TextView title = text(campaign.getTitle(), 18, true);
        com.rescuefarm.ui.common.AppTypography.title(title);
        TextView badge = text(campaign.getHighlightLabel(), 14, true);
        TextView progress = text(getString(R.string.campaign_progress_format,
                campaign.calculateProgress(), campaign.getRescuedQuantity(), campaign.getTargetQuantity()), 15, false);
        body.addView(title); body.addView(badge); body.addView(progress);
        if (nearbyLatitude != null) {
            double km = distanceService.distanceToCampaign(nearbyLatitude, nearbyLongitude, campaign, now);
            body.addView(text(String.format(Locale.forLanguageTag("vi-VN"), "Cách bạn %.1f km", km), 14, false));
        }
        card.addView(body); card.setOnClickListener(v -> {
            Bundle args = new Bundle(); args.putString("campaignId", campaign.getId());
            Navigation.findNavController(requireView()).navigate(
                    R.id.action_campaignListFragment_to_campaignDetailFragment, args);
        }); return card;
    }
    private TextView text(String value, int size, boolean bold) {
        TextView view = new TextView(requireContext()); view.setText(value); view.setTextSize(size);
        if (bold) view.setTypeface(view.getTypeface(), android.graphics.Typeface.BOLD); return view;
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    @Override public void onDestroyView() { if (locationProvider != null) locationProvider.close(); super.onDestroyView(); }
}
