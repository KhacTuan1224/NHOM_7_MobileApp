package com.rescuefarm.ui.campaign;

import android.content.Intent;
import android.content.ActivityNotFoundException;
import android.net.Uri;
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
import com.rescuefarm.data.repository.ProductRepository;
import com.rescuefarm.data.repository.ProductRepositoryFactory;
import com.rescuefarm.domain.enums.RescueMode;
import com.rescuefarm.domain.model.RescueCampaign;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.List;
import java.util.ArrayList;

public class CampaignDetailFragment extends Fragment {
    
    private String campaignId = "";
    private RescueCampaign campaign;
    
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
                refreshUrgency(value.getCampaign());
            } else if (value.getStatus() == CampaignScreenState.Status.ERROR) {
                Toast.makeText(requireContext(), value.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
        
        // Setup button click listeners
        view.findViewById(R.id.viewCampaignProductsButton).setOnClickListener(v -> {
            openCampaignProducts(v);
        });
        view.findViewById(R.id.campaignDirectionsButton).setOnClickListener(v -> openDirections());
        
        view.findViewById(R.id.shareCampaignButton).setOnClickListener(v -> {
            if (campaign == null) return;
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("text/plain");
            share.putExtra(Intent.EXTRA_SUBJECT, campaign.getTitle());
            share.putExtra(Intent.EXTRA_TEXT, campaign.getTitle() + "\n" + campaign.getDescription()
                + "\nCòn " + campaign.calculateRemainingQuantity() + " kg cần giải cứu."
                + "\nrescuefarm://campaigns/" + Uri.encode(campaignId));
            startActivity(Intent.createChooser(share, "Chia sẻ chiến dịch"));
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
        campaign = value;
        set(view, R.id.campaignDetailTitle, value.getTitle());
        set(view, R.id.campaignDetailBadge, value.getHighlightLabel());
        set(view, R.id.campaignDetailDescription, value.getDescription());
        set(view, R.id.campaignDetailProgress, getString(R.string.campaign_progress_quantities_format,
                value.calculateProgress(), value.getTargetQuantity(), value.getRescuedQuantity(),
                value.getReservedQuantity(), value.calculateRemainingQuantity(), "kg"));
        set(view, R.id.campaignDetailReason, getString(R.string.campaign_reason_format,
                value.getRescueReason().name(), value.getUrgencyLevel().name()));
        
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

    private void refreshUrgency(RescueCampaign value) {
        if (value == null || value.getBatchTargets().isEmpty()) return;
        ProductRepository repository = ProductRepositoryFactory.create(requireContext());
        repository.getBatchesByIds(new ArrayList<>(value.getBatchTargets().keySet()),
                new ProductRepository.BatchListCallback() {
            @Override public void onSuccess(List<com.rescuefarm.domain.model.ProductBatch> batches) {
                repository.close();
                if (!isAdded() || value.getEndDate() == null) return;
                Date earliest = null;
                if (batches != null) for (com.rescuefarm.domain.model.ProductBatch batch : batches) {
                    if (batch != null && batch.isSellable(new Date()) && batch.getExpiryDate() != null
                            && (earliest == null || batch.getExpiryDate().before(earliest))) {
                        earliest = batch.getExpiryDate();
                    }
                }
                if (earliest != null) {
                    value.calculateUrgency(new Date(), earliest);
                    View root = getView();
                    if (root != null && campaign == value) render(root, value);
                }
            }
            @Override public void onError(ProductRepository.ErrorCode error, String message) {
                repository.close();
            }
        });
    }

    private void openCampaignProducts(View source) {
        if (campaign == null || campaignId.trim().isEmpty()) return;
        source.setEnabled(false);
        ProductRepository products = ProductRepositoryFactory.create(requireContext());
        products.getCampaignProducts(new ArrayList<>(campaign.getBatchTargets().keySet()),
                new ProductRepository.ProductListCallback() {
            @Override public void onSuccess(List<com.rescuefarm.domain.model.Product> values) {
                products.close();
                if (!isAdded()) return;
                source.setEnabled(true);
                if (values == null || values.isEmpty()) {
                    Toast.makeText(requireContext(), "Chiến dịch hiện chưa có sản phẩm khả dụng.",
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                String[] labels = new String[values.size()];
                for (int i = 0; i < values.size(); i++) {
                    com.rescuefarm.domain.model.Product product = values.get(i);
                    labels[i] = product.getName() + " · " + product.getUnit();
                }
                new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                        .setTitle("Sản phẩm trong chiến dịch")
                        .setItems(labels, (dialog, index) -> {
                            Bundle args = new Bundle();
                            args.putString("productId", values.get(index).getId());
                            args.putString("campaignId", campaignId);
                            args.putStringArrayList("campaignBatchIds",
                                    new ArrayList<>(campaign.getBatchTargets().keySet()));
                            androidx.navigation.NavController nav = Navigation.findNavController(source);
                            if (nav.getCurrentDestination() != null
                                    && nav.getCurrentDestination().getId() == R.id.campaignDetailFragment) {
                                nav.navigate(R.id.action_campaignDetailFragment_to_productDetailFragment, args);
                            }
                        }).setNegativeButton(android.R.string.cancel, null).show();
            }
            @Override public void onError(ProductRepository.ErrorCode error, String message) {
                products.close();
                if (!isAdded()) return;
                source.setEnabled(true);
                Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void openDirections() {
        if (campaign == null) return;
        double latitude = campaign.getLatitude();
        double longitude = campaign.getLongitude();
        if (campaign.getRescueMode() == RescueMode.MOBILE_POINT) {
            if (!campaign.isLocationFresh(new Date())) {
                Toast.makeText(requireContext(), "Vị trí điểm di động chưa được cập nhật.",
                        Toast.LENGTH_LONG).show();
                return;
            }
            latitude = campaign.getCurrentLatitude();
            longitude = campaign.getCurrentLongitude();
        }
        String label = Uri.encode(campaign.getLocationName().isEmpty()
                ? campaign.getTitle() : campaign.getLocationName());
        Uri destination = Uri.parse("geo:" + latitude + "," + longitude + "?q="
                + latitude + "," + longitude + "(" + label + ")");
        Intent intent = new Intent(Intent.ACTION_VIEW, destination);
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException error) {
            Toast.makeText(requireContext(), "Không tìm thấy ứng dụng bản đồ trên thiết bị.",
                    Toast.LENGTH_LONG).show();
        }
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
