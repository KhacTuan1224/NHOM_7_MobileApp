package com.rescuefarm.ui.campaign;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import com.rescuefarm.R;
import com.rescuefarm.domain.enums.RescueMode;
import com.rescuefarm.domain.model.RescueCampaign;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class CampaignDetailFragment extends Fragment {
    
    private String campaignId = "";
    
    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater,
            @Nullable ViewGroup parent, @Nullable Bundle state) {
        return inflater.inflate(R.layout.fragment_campaign_detail, parent, false);
    }
    
    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        campaignId = getArguments() == null ? "" : getArguments().getString("campaignId", "");
        
        CampaignViewModel viewModel = new ViewModelProvider(this,
                new CampaignViewModelFactory(requireContext())).get(CampaignViewModel.class);
        
        viewModel.getState().observe(getViewLifecycleOwner(), value -> {
            if (value.getStatus() == CampaignScreenState.Status.CAMPAIGN) {
                render(view, value.getCampaign());
            } else if (value.getStatus() == CampaignScreenState.Status.ERROR) {
                Toast.makeText(requireContext(), value.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
        
        // Setup button click listeners
        view.findViewById(R.id.viewCampaignProductsButton).setOnClickListener(v -> {
            // Navigate to campaign products list
            Toast.makeText(requireContext(), "Xem sản phẩm trong chiến dịch", Toast.LENGTH_SHORT).show();
            // TODO: Navigate to product list filtered by campaign
        });
        
        view.findViewById(R.id.shareCampaignButton).setOnClickListener(v -> {
            Toast.makeText(requireContext(), "Chia sẻ chiến dịch", Toast.LENGTH_SHORT).show();
            // TODO: Implement share functionality
        });
        
        view.findViewById(R.id.reportCampaignButton).setOnClickListener(v -> {
            Bundle bundle = new Bundle();
            bundle.putString("targetType", "CAMPAIGN");
            bundle.putString("targetId", campaignId);
            Navigation.findNavController(v).navigate(
                R.id.action_campaignDetailFragment_to_reportFragment, bundle);
        });
        
        viewModel.load(campaignId);
    }
    
    private void render(View view, RescueCampaign value) {
        set(view, R.id.campaignDetailTitle, value.getTitle());
        set(view, R.id.campaignDetailBadge, value.getHighlightLabel());
        set(view, R.id.campaignDetailDescription, value.getDescription());
        set(view, R.id.campaignDetailReason, getString(R.string.campaign_reason_format,
                value.getRescueReason().name(), value.getUrgencyLevel().name()));
        set(view, R.id.campaignDetailProgress, getString(R.string.campaign_progress_format,
                value.calculateProgress(), value.getRescuedQuantity(), value.getTargetQuantity()));
        
        // Set progress bar
        android.widget.ProgressBar progressBar = view.findViewById(R.id.campaignProgressBar);
        if (progressBar != null) {
            int progress = (int) Math.round(value.calculateProgress());
            progressBar.setProgress(progress);
        }
        
        SimpleDateFormat format = new SimpleDateFormat("dd/MM/yyyy", Locale.forLanguageTag("vi-VN"));
        set(view, R.id.campaignDetailSchedule, format.format(value.getStartDate()) + " – " + format.format(value.getEndDate()));
        
        String location = value.getLocationName();
        if (value.getRescueMode() == RescueMode.MOBILE_POINT) {
            location = value.isLocationFresh(new Date())
                    ? getString(R.string.mobile_location_fresh)
                    : getString(R.string.mobile_location_stale);
        }
        set(view, R.id.campaignDetailLocation, location);
    }
    
    private static void set(View root, int id, String value) {
        TextView textView = root.findViewById(id);
        if (textView != null) {
            textView.setText(value == null ? "" : value);
            // Hide badge if empty
            if (id == R.id.campaignDetailBadge && (value == null || value.trim().isEmpty())) {
                textView.setVisibility(View.GONE);
            } else {
                textView.setVisibility(View.VISIBLE);
            }
        }
    }
}
