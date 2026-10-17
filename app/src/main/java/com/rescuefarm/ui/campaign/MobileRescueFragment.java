package com.rescuefarm.ui.campaign;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
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
import com.google.android.material.card.MaterialCardView;
import com.rescuefarm.R;
import com.rescuefarm.domain.enums.CampaignStatus;
import com.rescuefarm.domain.enums.RescueMode;
import com.rescuefarm.domain.model.RescueCampaign;
import java.util.Date;
import java.util.List;
import java.util.Map;

public class MobileRescueFragment extends Fragment {
    private java.util.List<RescueCampaign> current = java.util.Collections.emptyList();
    private final java.util.Map<String,TextView> stateLabels=new java.util.HashMap<>();
    private final java.util.Map<String,Button> updateButtons=new java.util.HashMap<>();
    private void refreshTimes() {
        Date now=new Date(); for(RescueCampaign c:current) {
            TextView label=stateLabels.get(c.getId()); if(label!=null) label.setText(com.rescuefarm.service.campaign.CampaignTimeFormatter.locationStatus(c,now)
                +"\n"+com.rescuefarm.service.campaign.CampaignTimeFormatter.locationUpdated(c.getLocationUpdatedAt(),now)
                +"\n"+com.rescuefarm.service.campaign.CampaignTimeFormatter.remaining(c,now));
            Button button=updateButtons.get(c.getId()); if(button!=null) button.setEnabled(com.rescuefarm.service.campaign.CampaignTimeFormatter.canUpdateLocation(c,now));
        }
    }
    private final android.os.Handler clock = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable tick = new Runnable() { @Override public void run() { if(getView()!=null) refreshTimes(); clock.postDelayed(this,1000L); } };
    @Override public void onStart() { super.onStart(); clock.post(tick); }
    @Override public void onStop() { clock.removeCallbacks(tick); super.onStop(); }
    private CampaignViewModel viewModel; private LinearLayout container; private String requestedCampaignId;
    private final ActivityResultLauncher<String[]> permissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(), this::onPermissions);
    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater,
            @Nullable ViewGroup parent, @Nullable Bundle state) {
        return inflater.inflate(R.layout.fragment_mobile_rescue, parent, false);
    }
    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        container = view.findViewById(R.id.mobileCampaignContainer);
        requestedCampaignId = getArguments() == null ? "" : getArguments().getString("campaignId", "");
        viewModel = new ViewModelProvider(this, new CampaignViewModelFactory(requireContext())).get(CampaignViewModel.class);
        viewModel.getSellerCampaigns().observe(getViewLifecycleOwner(), this::render);
        viewModel.getState().observe(getViewLifecycleOwner(), value -> {
            if (value.getStatus() == CampaignScreenState.Status.LOCATION_UPDATED) {
                Toast.makeText(requireContext(), value.getMessage(), Toast.LENGTH_SHORT).show(); viewModel.refreshSeller();
            } else if (value.getStatus() == CampaignScreenState.Status.ERROR)
                Toast.makeText(requireContext(), value.getMessage(), Toast.LENGTH_LONG).show();
        });
        viewModel.refreshSeller();
    }
    private void render(List<RescueCampaign> values) {
        current = values == null ? java.util.Collections.emptyList() : values;
        container.removeAllViews(); stateLabels.clear(); updateButtons.clear(); if (values == null) return;
        for (RescueCampaign value : values) if (value.getStatus() == CampaignStatus.ACTIVE
                && value.getRescueMode() == RescueMode.MOBILE_POINT
                && (requestedCampaignId.isEmpty() || requestedCampaignId.equals(value.getId()))) {
            container.addView(card(value));
        }
    }
    private View card(RescueCampaign value) {
        MaterialCardView card = new MaterialCardView(requireContext()); card.setRadius(dp(16)); card.setStrokeWidth(dp(1));
        LinearLayout body = new LinearLayout(requireContext()); body.setOrientation(LinearLayout.VERTICAL); body.setPadding(dp(14),dp(14),dp(14),dp(14));
        TextView title = new TextView(requireContext()); title.setText(value.getTitle()); title.setTextSize(18); title.setTypeface(title.getTypeface(),1);
        TextView state = new TextView(requireContext()); state.setText(value.isLocationFresh(new Date())
                ? R.string.mobile_location_fresh : R.string.mobile_location_stale);
        state.setText(com.rescuefarm.service.campaign.CampaignTimeFormatter.locationStatus(value,new Date())
                +"\n"+com.rescuefarm.service.campaign.CampaignTimeFormatter.locationUpdated(value.getLocationUpdatedAt(),new Date())
                +"\n"+com.rescuefarm.service.campaign.CampaignTimeFormatter.remaining(value,new Date()));
        Button update = new Button(requireContext()); update.setText(R.string.update_mobile_location_action);
        update.setEnabled(com.rescuefarm.service.campaign.CampaignTimeFormatter.canUpdateLocation(value,new Date()));
        update.setOnClickListener(v -> requestLocation(value.getId()));
        stateLabels.put(value.getId(),state); updateButtons.put(value.getId(),update);
        Button stop = new Button(requireContext()); stop.setText(R.string.stop_location_sharing_action);
        stop.setEnabled(value.isLocationSharingEnabled()); stop.setOnClickListener(v -> viewModel.stopLocationSharing(value.getId()));
        body.addView(title); body.addView(state); body.addView(update); body.addView(stop); card.addView(body); return card;
    }
    private void requestLocation(String campaignId) {
        requestedCampaignId = campaignId;
        boolean fine = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        boolean coarse = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        if (!fine && !coarse) permissionLauncher.launch(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION});
        else viewModel.updateCurrentLocation(campaignId);
    }
    private void onPermissions(Map<String, Boolean> result) {
        if (Boolean.TRUE.equals(result.get(Manifest.permission.ACCESS_FINE_LOCATION))
                || Boolean.TRUE.equals(result.get(Manifest.permission.ACCESS_COARSE_LOCATION))) {
            viewModel.updateCurrentLocation(requestedCampaignId);
        } else Toast.makeText(requireContext(), R.string.location_permission_required, Toast.LENGTH_LONG).show();
    }
    @Override public void onDestroyView() {clock.removeCallbacks(tick);stateLabels.clear();updateButtons.clear();container=null;super.onDestroyView();}
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
