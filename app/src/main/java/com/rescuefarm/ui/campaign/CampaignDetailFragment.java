package com.rescuefarm.ui.campaign;
import com.rescuefarm.ui.common.EnumLabels;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.Gravity;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.LinearLayout;
import android.widget.FrameLayout;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import com.bumptech.glide.Glide;
import com.rescuefarm.MainActivity;
import com.rescuefarm.R;
import com.rescuefarm.data.repository.ProductRepository;
import com.rescuefarm.data.repository.ProductRepositoryFactory;
import com.rescuefarm.data.repository.CampaignEngagementRepository;
import com.rescuefarm.data.repository.CampaignEngagementRepositoryFactory;
import com.rescuefarm.domain.enums.RescueMode;
import com.rescuefarm.domain.enums.ReactionType;
import com.rescuefarm.domain.model.Comment;
import com.rescuefarm.domain.model.CampaignEngagementSnapshot;
import com.rescuefarm.domain.model.Product;
import com.rescuefarm.domain.model.ProductBatch;
import com.rescuefarm.domain.model.RescueCampaign;
import com.rescuefarm.service.location.CampaignLocationService;
import com.rescuefarm.service.location.LocationProvider;
import com.rescuefarm.service.location.LocationProviderFactory;
import com.rescuefarm.ui.cart.CartScreenState;
import com.rescuefarm.ui.cart.CartViewModel;
import com.rescuefarm.ui.cart.CartViewModelFactory;
import com.rescuefarm.ui.navigation.ActorUiPolicy;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class CampaignDetailFragment extends Fragment {
    private final com.rescuefarm.ui.common.ListPagination commentPages = new com.rescuefarm.ui.common.ListPagination();
    
    private final android.os.Handler clock = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable timeTick = new Runnable() {
        @Override public void run() { updateTemporalUi(); clock.postDelayed(this, 1000L); }
    };
    private void updateTemporalUi() {
        View root=getView(); if(root==null || campaign==null) return;
        Date now=new Date();
        set(root,R.id.campaignDetailSchedule,dateFormat.format(campaign.getStartDate())+" – "+dateFormat.format(campaign.getEndDate())
                +"\n"+com.rescuefarm.service.campaign.CampaignTimeFormatter.remaining(campaign,now));
        boolean open=com.rescuefarm.service.campaign.CampaignTimeFormatter.withinSchedule(campaign,now);
        boolean purchase=com.rescuefarm.service.campaign.CampaignTimeFormatter.canPurchase(campaign,now)
                && selectedProduct!=null && selectedBatch!=null && selectedBatch.isSellable(now) && !ActorUiPolicy.isSellerReadOnly(this);
        root.findViewById(R.id.campaignAddToCartButton).setEnabled(purchase);
        root.findViewById(R.id.campaignRescueNowButton).setEnabled(purchase);
        boolean mobile=campaign.getRescueMode()==RescueMode.MOBILE_POINT;
        root.findViewById(R.id.campaignDirectionsButton).setEnabled(open && (!mobile || campaign.isLocationFresh(now)));
        for(int id:new int[]{R.id.campaignSendCommentButton, R.id.campaignCareButton})
            root.findViewById(id).setEnabled(open && canUseCampaignSocialActions());
        if(mobile) {
            set(root,R.id.campaignDetailLocation,com.rescuefarm.service.campaign.CampaignTimeFormatter.locationStatus(campaign,now)
                    +"\n"+com.rescuefarm.service.campaign.CampaignTimeFormatter.locationUpdated(campaign.getLocationUpdatedAt(),now));
            if(!open || !campaign.isLocationFresh(now)) {
                TextView distance=root.findViewById(R.id.campaignDistanceText); distance.setText("Không dùng vị trí cũ để chỉ đường hoặc đặt hàng."); distance.setOnClickListener(null);
            }
        }
    }
    @Override public void onStart() { super.onStart(); clock.removeCallbacks(timeTick); clock.post(timeTick); }
    @Override public void onStop() { clock.removeCallbacks(timeTick); super.onStop(); }
    private String campaignId = "";
    private RescueCampaign campaign;
    private CartViewModel cartViewModel;
    private LocationProvider locationProvider;
    private final CampaignLocationService distanceService = new CampaignLocationService();
    private boolean pendingDirectCheckout = false;
    
    private List<ProductBatch> loadedBatches = new ArrayList<>();
    private List<Product> loadedProducts = new ArrayList<>();
    private ProductBatch selectedBatch;
    private Product selectedProduct;
    private CampaignEngagementRepository engagementRepository;
    private boolean engagementLoaded;
    private boolean viewRecorded;
    private boolean commentSubmitted;
    private TextView engagementSummary;
    private LinearLayout commentContainer;
    private com.google.android.material.textfield.TextInputEditText commentInput;
    private boolean careSelected;
    
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.forLanguageTag("vi-VN"));
    private final NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(new Locale("vi", "VN"));
    
    private EditText quantityInput;
    private Spinner batchSpinner;
    private TextView batchHint;
    private TextView productNameText;
    private TextView productPriceText;
    private TextView quantityUnitText;
    private ImageView productImageView;

    private final ActivityResultLauncher<String[]> locationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                boolean fine = Boolean.TRUE.equals(result.get(Manifest.permission.ACCESS_FINE_LOCATION));
                boolean coarse = Boolean.TRUE.equals(result.get(Manifest.permission.ACCESS_COARSE_LOCATION));
                if (fine || coarse) {
                    if (campaign != null && campaign.getRescueMode() == RescueMode.MOBILE_POINT) {
                        checkPermissionAndFetchDistance(campaign);
                    }
                } else {
                    Toast.makeText(requireContext(), R.string.location_permission_required, Toast.LENGTH_SHORT).show();
                }
            });

    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater,
            @Nullable ViewGroup parent, @Nullable Bundle state) {
        return inflater.inflate(R.layout.fragment_campaign_detail, parent, false);
    }
    
    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        com.rescuefarm.ui.common.DetailTypography.bindHeader(this, view, "Chi tiết chiến dịch");
        campaignId = getArguments() == null ? "" : getArguments().getString("campaignId", "");
        locationProvider = LocationProviderFactory.create(requireContext());
        
        quantityInput = view.findViewById(R.id.campaignQuantityInput);
        batchSpinner = view.findViewById(R.id.campaignBatchSpinner);
        batchHint = view.findViewById(R.id.campaignBatchHint);
        productNameText = view.findViewById(R.id.campaignProductName);
        productPriceText = view.findViewById(R.id.campaignProductPrice);
        quantityUnitText = view.findViewById(R.id.campaignQuantityUnit);
        productImageView = view.findViewById(R.id.campaignProductImage);
        engagementSummary = view.findViewById(R.id.campaignEngagementSummary);
        commentContainer = view.findViewById(R.id.campaignCommentContainer);
        commentInput = view.findViewById(R.id.campaignCommentInput);
        engagementRepository = CampaignEngagementRepositoryFactory.create(requireContext());

        CampaignViewModel viewModel = new ViewModelProvider(this,
                new CampaignViewModelFactory(requireContext())).get(CampaignViewModel.class);
        
        cartViewModel = new ViewModelProvider(this,
                new CartViewModelFactory(requireContext())).get(CartViewModel.class);

        cartViewModel.getState().observe(getViewLifecycleOwner(), screenState -> {
            if (screenState == null) return;
            if (screenState.getStatus() == CartScreenState.Status.SAVED) {
                if (pendingDirectCheckout) {
                    pendingDirectCheckout = false;
                    androidx.navigation.NavController nav = Navigation.findNavController(view);
                    if (nav.getCurrentDestination() != null
                            && nav.getCurrentDestination().getId() == R.id.campaignDetailFragment) {
                        nav.navigate(R.id.action_campaignDetailFragment_to_checkoutFragment);
                    }
                } else {
                    String name = selectedProduct != null ? selectedProduct.getName() : "sản phẩm";
                    Toast.makeText(requireContext(), "Đã thêm " + name + " vào giỏ hàng!", Toast.LENGTH_SHORT).show();
                }
            } else if (screenState.getStatus() == CartScreenState.Status.ERROR) {
                pendingDirectCheckout = false;
                Toast.makeText(requireContext(), screenState.getMessage(), Toast.LENGTH_LONG).show();
            }
        });

        viewModel.getState().observe(getViewLifecycleOwner(), value -> {
            if (value.getStatus() == CampaignScreenState.Status.CAMPAIGN) {
                render(view, value.getCampaign());
                refreshUrgency(value.getCampaign());
                if (value.getCampaign().getStatus() == com.rescuefarm.domain.enums.CampaignStatus.ACTIVE
                        && !engagementLoaded) { engagementLoaded = true; loadEngagement(); }
                if (value.getCampaign().getStatus() == com.rescuefarm.domain.enums.CampaignStatus.ACTIVE
                        && !viewRecorded) {
                    viewRecorded = true;
                    engagementRepository.recordView(campaignId, new CampaignEngagementRepository.ViewCallback() {
                        @Override public void onSuccess(long count) {
                            if (!isAdded() || campaign == null) return;
                            campaign.setViewCount(count);
                            View root = getView();
                            if (root != null) set(root, R.id.campaignViewCount, count + " lượt xem");
                        }
                        @Override public void onError(String message) { }
                    });
                }
            } else if (value.getStatus() == CampaignScreenState.Status.ERROR) {
                Toast.makeText(requireContext(), value.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
        
        // Setup quantity stepper
        View decreaseBtn = view.findViewById(R.id.campaignQuantityDecreaseButton);
        if (decreaseBtn != null) {
            decreaseBtn.setOnClickListener(v -> {
                try {
                    double current = Double.parseDouble(quantityInput.getText().toString().trim());
                    if (current > 1) {
                        quantityInput.setText(String.valueOf((int) (current - 1)));
                    }
                } catch (Exception ignored) {
                    quantityInput.setText("1");
                }
            });
        }
        View increaseBtn = view.findViewById(R.id.campaignQuantityIncreaseButton);
        if (increaseBtn != null) {
            increaseBtn.setOnClickListener(v -> {
                try {
                    double current = Double.parseDouble(quantityInput.getText().toString().trim());
                    if (selectedBatch != null && current + 1 <= selectedBatch.getAvailableQuantity()) {
                        quantityInput.setText(String.valueOf((int) (current + 1)));
                    } else if (selectedBatch != null) {
                        Toast.makeText(requireContext(), "Đã đạt số lượng tồn kho khả dụng tối đa",
                                Toast.LENGTH_SHORT).show();
                    } else {
                        quantityInput.setText(String.valueOf((int) (current + 1)));
                    }
                } catch (Exception ignored) {
                    quantityInput.setText("1");
                }
            });
        }

        // Action Buttons: "Thêm vào giỏ" & "Cứu trợ ngay"
        View addToCartBtn = view.findViewById(R.id.campaignAddToCartButton);
        if (addToCartBtn != null) {
            addToCartBtn.setOnClickListener(v -> onAddToCart(false));
        }

        View rescueNowBtn = view.findViewById(R.id.campaignRescueNowButton);
        if (rescueNowBtn != null) {
            rescueNowBtn.setOnClickListener(v -> onAddToCart(true));
        }

        // Setup secondary buttons
        view.findViewById(R.id.viewCampaignProductsButton).setOnClickListener(this::openCampaignProducts);
        view.findViewById(R.id.campaignDirectionsButton).setOnClickListener(v -> openDirections());
        
        view.findViewById(R.id.shareCampaignButton).setOnClickListener(v -> {
            if (campaign == null) return;
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("text/plain");
            share.putExtra(Intent.EXTRA_SUBJECT, campaign.getTitle());
            share.putExtra(Intent.EXTRA_TEXT, campaign.getTitle() + "\n" + campaign.getDescription()
                + "\nCòn " + campaign.calculateRemainingQuantity() + " " + campaign.getQuantityUnitLabel() + " cần giải cứu."
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
        view.findViewById(R.id.campaignCareButton).setOnClickListener(v -> toggleCare());
        view.findViewById(R.id.campaignSendCommentButton).setOnClickListener(v -> {
            String content = commentInput.getText() == null ? "" : commentInput.getText().toString();
            commentSubmitted = !content.trim().isEmpty();
            engagementRepository.addComment(campaignId, currentUserId(), content, actionRefresh());
        });

        applyActorActions(view);
        if (requireActivity() instanceof MainActivity) {
            ((MainActivity) requireActivity()).observeActorRole().observe(getViewLifecycleOwner(), role ->
                    applyActorActions(view));
        }
        
        viewModel.load(campaignId);
    }

    private void applyActorActions(View root) {
        boolean isReadOnly = ActorUiPolicy.isSellerReadOnly(this);
        boolean socialEnabled = canUseCampaignSocialActions();
        root.findViewById(R.id.reportCampaignButton).setVisibility(
                socialEnabled ? View.VISIBLE : View.GONE);
        root.findViewById(R.id.campaignCareButton).setVisibility(socialEnabled ? View.VISIBLE : View.GONE);
        root.findViewById(R.id.campaignCommentInput).setVisibility(socialEnabled ? View.VISIBLE : View.GONE);
        root.findViewById(R.id.campaignSendCommentButton).setVisibility(socialEnabled ? View.VISIBLE : View.GONE);
        View actionCard = root.findViewById(R.id.campaignProductActionCard);
        if (actionCard != null) {
            actionCard.setVisibility(isReadOnly ? View.GONE : View.VISIBLE);
        }
    }
    
    private void render(View view, RescueCampaign value) {
        campaign = value;
        set(view, R.id.campaignDetailTitle, value.getTitle());
        set(view, R.id.campaignDetailBadge, value.getHighlightLabel());
        set(view, R.id.campaignDetailDescription, value.getDescription());
        renderStoryImages(view, value);
        set(view, R.id.campaignDetailReason, getString(R.string.campaign_reason_format,
                EnumLabels.label(value.getRescueReason()), EnumLabels.label(value.getUrgencyLevel())));
        set(view, R.id.campaignViewCount, value.getViewCount() + " lượt xem");
        
        // Progress Bar & 3 Metrics (3 mốc: Đỏ < 30%, Vàng 30-69%, Xanh >= 70%)
        double progressPercent = value.calculateProgress();
        int progress = (int) Math.round(progressPercent);
        ProgressBar progressBar = view.findViewById(R.id.campaignProgressBar);
        TextView percentText = view.findViewById(R.id.campaignProgressPercent);
        if (percentText != null) {
            percentText.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(),
                    com.rescuefarm.ui.common.CampaignProgressColors.forPercent(value.calculateProgress())));
            percentText.setText(String.format(Locale.getDefault(), "%d%%", progress));
        }
        com.rescuefarm.ui.common.RescueProgressUtils.applyProgressWithText(progressBar, percentText, progressPercent);

        TextView targetText = view.findViewById(R.id.campaignTargetQuantity);
        if (targetText != null) {
            targetText.setText(String.format(Locale.getDefault(), "%,.1f %s", value.getTargetQuantity(), value.getQuantityUnitLabel()));
        }

        TextView rescuedText = view.findViewById(R.id.campaignRescuedQuantity);
        if (rescuedText != null) {
            rescuedText.setText(String.format(Locale.getDefault(), "%,.1f %s", value.getRescuedQuantity(), value.getQuantityUnitLabel()));
        }

        TextView availableText = view.findViewById(R.id.campaignAvailableQuantity);
        if (availableText != null) {
            availableText.setText(String.format(Locale.getDefault(), "%,.1f %s", value.calculateRemainingQuantity(), value.getQuantityUnitLabel()));
        }

        set(view, R.id.campaignDetailProgress, getString(R.string.campaign_progress_quantities_format,
                value.calculateProgress(), value.getTargetQuantity(), value.getRescuedQuantity(),
                value.getReservedQuantity(), value.calculateRemainingQuantity(), value.getQuantityUnitLabel()));

        if ("REVIEW_REQUIRED".equals(value.getUnitStatus())) {
            if (progressBar != null) progressBar.setVisibility(View.GONE);
            if (percentText != null) percentText.setText("Cần đối soát");
            if (targetText != null) targetText.setText("—");
            if (rescuedText != null) rescuedText.setText("—");
            if (availableText != null) availableText.setText("—");
            set(view, R.id.campaignDetailProgress, "Đơn vị của chiến dịch chưa nhất quán. Tạm chưa hiển thị tiến độ; cần người bán và quản trị đối soát.");
        } else if (progressBar != null) progressBar.setVisibility(View.VISIBLE);

        set(view, R.id.campaignDetailSchedule, dateFormat.format(value.getStartDate()) + " – " + dateFormat.format(value.getEndDate()));
        
        updateLocationInfo(value);
        loadCampaignBatchesAndProducts(value);
        updateTemporalUi();
    }

    private void renderStoryImages(View root, RescueCampaign value) {
        LinearLayout gallery = root.findViewById(R.id.campaignStoryGallery);
        if (gallery == null) return;
        if (value.getImageUrls().equals(gallery.getTag())) return;
        gallery.setTag(new java.util.ArrayList<>(value.getImageUrls()));
        gallery.removeAllViews();
        for (String url : value.getImageUrls()) {
            ImageView image = new ImageView(requireContext());
            int width = Math.round(280 * getResources().getDisplayMetrics().density);
            int height = Math.round(210 * getResources().getDisplayMetrics().density);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, height);
            params.leftMargin = params.rightMargin = Math.round(4 * getResources().getDisplayMetrics().density);
            image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            image.setContentDescription("Ảnh nội dung chiến dịch");
            Glide.with(this).load(url).fitCenter().dontAnimate().into(image); gallery.addView(image, params);
        }
        gallery.setVisibility(value.getImageUrls().isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void loadEngagement() {
        if (engagementRepository == null || campaignId.isEmpty()) return;
        engagementRepository.load(campaignId, currentUserId(), new CampaignEngagementRepository.Callback() {
            @Override public void onSuccess(CampaignEngagementSnapshot value) {
                if (!isAdded()) return; renderEngagement(value);
                if (commentSubmitted) { commentSubmitted = false; commentInput.setText(""); }
            }
            @Override public void onError(String message) {
                if (isAdded()) engagementSummary.setText(message);
            }
        });
    }

    private void toggleCare() {
        boolean nextSelected = !careSelected;
        careSelected = nextSelected;
        updateCareButton();
        engagementRepository.toggleReaction(campaignId, currentUserId(), ReactionType.CARE,
                new CampaignEngagementRepository.Action() {
                    @Override public void onSuccess() {
                        if (!isAdded()) return;
                        if (nextSelected) animateCareHearts();
                        loadEngagement();
                    }
                    @Override public void onError(String message) {
                        if (!isAdded()) return;
                        careSelected = !nextSelected;
                        updateCareButton();
                        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void updateCareButton() {
        View root = getView();
        if (root == null) return;
        com.google.android.material.button.MaterialButton button = root.findViewById(R.id.campaignCareButton);
        int red = Color.rgb(220, 38, 38);
        if (careSelected) {
            button.setBackgroundTintList(ColorStateList.valueOf(red));
            button.setTextColor(Color.WHITE);
            button.setIconTint(ColorStateList.valueOf(Color.WHITE));
            button.setStrokeColor(ColorStateList.valueOf(red));
        } else {
            button.setBackgroundTintList(ColorStateList.valueOf(Color.WHITE));
            button.setTextColor(ContextCompat.getColor(requireContext(), R.color.rescue_primary));
            button.setIconTint(ColorStateList.valueOf(red));
            button.setStrokeColor(ColorStateList.valueOf(red));
        }
    }

    private void animateCareHearts() {
        View root = getView();
        if (root == null) return;
        FrameLayout frame = root.findViewById(R.id.campaignCareButtonFrame);
        if (frame == null) return;
        ViewParent parent = frame;
        while (parent instanceof ViewGroup) {
            ((ViewGroup) parent).setClipChildren(false);
            ((ViewGroup) parent).setClipToPadding(false);
            parent = parent.getParent();
        }
        int size = (int) (24 * getResources().getDisplayMetrics().density);
        for (int i = 0; i < 5; i++) {
            TextView heart = new TextView(requireContext());
            heart.setText("♥");
            heart.setTextColor(Color.rgb(239, 68, 68));
            heart.setTextSize(20 + (i % 2) * 4);
            heart.setGravity(Gravity.CENTER);
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(size, size, Gravity.CENTER);
            frame.addView(heart, params);
            heart.setAlpha(0f);
            heart.setScaleX(0.5f);
            heart.setScaleY(0.5f);
            heart.animate().alpha(1f).scaleX(1f).scaleY(1f).translationY(-size * (2.6f + (i % 3) * .45f))
                    .translationX((i - 2) * size * .72f).setStartDelay(i * 75L).setDuration(850)
                    .withEndAction(() -> frame.removeView(heart)).start();
        }
    }

    private CampaignEngagementRepository.Action actionRefresh() {
        return new CampaignEngagementRepository.Action() {
            @Override public void onSuccess() { if (isAdded()) loadEngagement(); }
            @Override public void onError(String message) {
                if (isAdded()) Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
            }
        };
    }

    private String currentUserId() {
        com.google.firebase.auth.FirebaseUser user = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
        return user == null ? "" : user.getUid();
    }

    private boolean canUseCampaignSocialActions() {
        return requireActivity() instanceof MainActivity
                && ((MainActivity) requireActivity()).getResolvedActorRole()
                == com.rescuefarm.domain.enums.UserRole.CUSTOMER;
    }

    private void renderEngagement(CampaignEngagementSnapshot value) {
        java.util.Map<ReactionType, Integer> counts = value.getReactionCounts();
        careSelected = value.getCurrentUserReaction() == ReactionType.CARE;
        updateCareButton();
        engagementSummary.setText("Quan tâm " + counts.getOrDefault(ReactionType.CARE, 0));
        commentPages.render(commentContainer, value.getComments(), page -> {
        commentContainer.removeAllViews();
        if (page.isEmpty()) { commentContainer.addView(commentText("Chưa có bình luận.")); return; }
        for (Comment comment : page) {
            LinearLayout row = new LinearLayout(requireContext()); row.setOrientation(LinearLayout.VERTICAL);
            row.addView(commentText(comment.getContent()
                    + (comment.getStatus() == com.rescuefarm.domain.enums.CommentStatus.EDITED ? " · đã chỉnh sửa" : "")));
            if (comment.getUserId().equals(currentUserId()) && canUseCampaignSocialActions()) {
                LinearLayout actions = new LinearLayout(requireContext());
                com.google.android.material.button.MaterialButton edit = new com.google.android.material.button.MaterialButton(requireContext());
                edit.setText("Sửa"); edit.setOnClickListener(v -> editCampaignComment(comment));
                com.google.android.material.button.MaterialButton delete = new com.google.android.material.button.MaterialButton(requireContext());
                delete.setText("Xóa"); delete.setOnClickListener(v -> new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                        .setTitle("Xóa bình luận?").setMessage("Bình luận sẽ được ẩn khỏi chiến dịch.")
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton("Xóa", (dialog, which) -> engagementRepository.deleteComment(
                                campaignId, comment.getId(), currentUserId(), actionRefresh())).show());
                actions.addView(edit); actions.addView(delete); row.addView(actions);
            }
            if (canUseCampaignSocialActions() && (comment.getStatus() == com.rescuefarm.domain.enums.CommentStatus.ACTIVE
                    || comment.getStatus() == com.rescuefarm.domain.enums.CommentStatus.EDITED)) {
                com.google.android.material.button.MaterialButton report = new com.google.android.material.button.MaterialButton(
                        requireContext(), null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
                report.setText("Báo cáo bình luận"); report.setOnClickListener(v -> {
                    Bundle args = new Bundle(); args.putString("targetType", "COMMENT");
                    args.putString("targetId", "campaigns/" + campaignId + "/comments/" + comment.getId());
                    androidx.navigation.fragment.NavHostFragment.findNavController(this).navigate(R.id.action_campaignDetailFragment_to_reportFragment, args);
                }); row.addView(report);
            }
            commentContainer.addView(row);
        }
        });
    }

    private void editCampaignComment(Comment comment) {
        EditText input = new EditText(requireContext()); input.setText(comment.getContent());
        input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(500)});
        new androidx.appcompat.app.AlertDialog.Builder(requireContext()).setTitle("Sửa bình luận")
                .setView(input).setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton("Lưu", (dialog, which) -> engagementRepository.editComment(
                        campaignId, comment.getId(), currentUserId(), input.getText().toString(), actionRefresh())).show();
    }

    private TextView commentText(String value) {
        TextView text = new TextView(requireContext()); text.setText(value); com.rescuefarm.ui.common.DetailTypography.body(text); text.setPadding(8, 10, 8, 10); return text;
    }

    @Override public void onDestroy() {
        if (engagementRepository != null) engagementRepository.close();
        super.onDestroy();
    }

    private void updateLocationInfo(RescueCampaign value) {
        if (value == null) return;
        View root = getView();
        if (root == null) return;
        
        TextView badge = root.findViewById(R.id.campaignRescueModeBadge);
        TextView locationView = root.findViewById(R.id.campaignDetailLocation);
        TextView distanceView = root.findViewById(R.id.campaignDistanceText);
        
        if (value.getRescueMode() == RescueMode.MOBILE_POINT) {
            if (badge != null) {
                badge.setText(R.string.campaign_mode_mobile);
                badge.setBackgroundResource(R.drawable.bg_mobile_badge);
                badge.setTextColor(ContextCompat.getColor(requireContext(), R.color.rescue_on_primary));
                badge.setVisibility(View.VISIBLE);
            }
            Date now = new Date();
            boolean isFresh = value.isLocationFresh(now);
            if (isFresh) {
                if (locationView != null) {
                    locationView.setText(R.string.mobile_location_fresh);
                }
                if (distanceView != null) {
                    distanceView.setVisibility(View.VISIBLE);
                    distanceView.setText("Đang tính khoảng cách đến xe cứu trợ...");
                }
                checkPermissionAndFetchDistance(value);
            } else {
                if (locationView != null) {
                    locationView.setText(R.string.mobile_location_stale);
                }
                if (distanceView != null) {
                    distanceView.setVisibility(View.VISIBLE);
                    distanceView.setText("Vị trí xe di động chưa cập nhật gần đây (quá 10 phút)");
                }
            }
        } else {
            // FIXED_POINT
            if (badge != null) {
                badge.setText(R.string.campaign_mode_fixed);
                badge.setBackgroundResource(R.drawable.bg_fixed_badge);
                badge.setTextColor(ContextCompat.getColor(requireContext(), R.color.rescue_on_primary_container));
                badge.setVisibility(View.VISIBLE);
            }
            String loc = value.getLocationName();
            if (loc == null || loc.trim().isEmpty()) {
                loc = "Kho / Hợp tác xã tại điểm tập kết cố định";
            }
            if (locationView != null) {
                locationView.setText(loc);
            }
            if (distanceView != null) {
                distanceView.setVisibility(View.GONE);
            }
        }
    }

    private void checkPermissionAndFetchDistance(RescueCampaign value) {
        if (!isAdded()) return;
        boolean fine = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        boolean coarse = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        if (!fine && !coarse) {
            View root = getView();
            if (root != null) {
                TextView distanceView = root.findViewById(R.id.campaignDistanceText);
                if (distanceView != null) {
                    distanceView.setText(R.string.campaign_distance_permission_prompt);
                    distanceView.setOnClickListener(v -> locationPermissionLauncher.launch(new String[]{
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                    }));
                }
            }
            return;
        }
        
        locationProvider.getCurrentLocation(new LocationProvider.LocationCallback() {
            @Override public void onLocationAvailable(double userLat, double userLng) {
                if (!isAdded() || campaign != value || !value.isLocationFresh(new Date())
                        || !com.rescuefarm.service.campaign.CampaignTimeFormatter.withinSchedule(value, new Date())) return;
                double dist = distanceService.distanceKilometers(userLat, userLng,
                        value.getCurrentLatitude(), value.getCurrentLongitude());
                View root = getView();
                if (root != null) {
                    TextView distanceView = root.findViewById(R.id.campaignDistanceText);
                    if (distanceView != null) {
                        distanceView.setText(getString(R.string.campaign_distance_format, dist));
                        distanceView.setOnClickListener(null);
                    }
                }
            }
            @Override public void onLocationUnavailable(String msg) {
                if (!isAdded()) return;
                View root = getView();
                if (root != null) {
                    TextView distanceView = root.findViewById(R.id.campaignDistanceText);
                    if (distanceView != null) {
                        distanceView.setText("Chưa xác định được vị trí GPS thiết bị");
                    }
                }
            }
        });
    }

    private void loadCampaignBatchesAndProducts(RescueCampaign value) {
        if (value == null || value.getBatchTargets().isEmpty()) {
            bindEmptyProductSection();
            return;
        }
        List<String> batchIds = new ArrayList<>(value.getBatchTargets().keySet());
        ProductRepository repo = ProductRepositoryFactory.create(requireContext());
        repo.getBatchesByIds(batchIds, new ProductRepository.BatchListCallback() {
            @Override public void onSuccess(List<ProductBatch> batches) {
                if (!isAdded()) { repo.close(); return; }
                loadedBatches = batches != null ? batches : new ArrayList<>();
                repo.getCampaignProducts(batchIds, new ProductRepository.ProductListCallback() {
                    @Override public void onSuccess(List<Product> products) {
                        repo.close();
                        if (!isAdded()) return;
                        loadedProducts = products != null ? products : new ArrayList<>();
                        bindProductBatchSection();
                    }
                    @Override public void onError(ProductRepository.ErrorCode error, String message) {
                        repo.close();
                    }
                });
            }
            @Override public void onError(ProductRepository.ErrorCode error, String message) {
                repo.close();
            }
        });
    }

    private void bindEmptyProductSection() {
        if (productImageView != null) {
            productImageView.setImageResource(R.drawable.ic_placeholder_food);
        }
        if (productNameText != null) {
            productNameText.setText(R.string.campaign_no_batches_available);
        }
        if (productPriceText != null) productPriceText.setText("");
        if (batchHint != null) batchHint.setText("");
        if (batchSpinner != null) batchSpinner.setVisibility(View.GONE);
        View root = getView();
        if (root != null) {
            View addBtn = root.findViewById(R.id.campaignAddToCartButton);
            View rescueBtn = root.findViewById(R.id.campaignRescueNowButton);
            if (addBtn != null) addBtn.setEnabled(false);
            if (rescueBtn != null) rescueBtn.setEnabled(false);
        }
    }

    private void bindProductBatchSection() {
        if (!isAdded() || loadedBatches.isEmpty()) {
            bindEmptyProductSection();
            return;
        }
        View root = getView();
        if (root == null) return;
        
        View addBtn = root.findViewById(R.id.campaignAddToCartButton);
        View rescueBtn = root.findViewById(R.id.campaignRescueNowButton);
        if (addBtn != null) addBtn.setEnabled(true);
        if (rescueBtn != null) rescueBtn.setEnabled(true);

        List<String> batchLabels = new ArrayList<>();
        for (ProductBatch batch : loadedBatches) {
            Product prod = findProductForBatch(batch);
            String prodName = prod != null ? prod.getName() : "Nông sản";
            String unit = prod != null ? prod.getUnit() : campaign.getQuantityUnitLabel();
            batchLabels.add(prodName + " (Còn " + batch.getAvailableQuantity() + " " + unit + ")");
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_dropdown_item, batchLabels);
        if (batchSpinner != null) {
            batchSpinner.setAdapter(adapter);
            batchSpinner.setVisibility(loadedBatches.size() > 1 ? View.VISIBLE : View.GONE);
            View batchLabel = root.findViewById(R.id.campaignBatchLabel);
            if (batchLabel != null) {
                batchLabel.setVisibility(loadedBatches.size() > 1 ? View.VISIBLE : View.GONE);
            }
            batchSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
                @Override public void onItemSelected(android.widget.AdapterView<?> parent, View row, int position, long id) {
                    if (position >= 0 && position < loadedBatches.size()) {
                        selectBatch(loadedBatches.get(position));
                    }
                }
                @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
            });
        }
        if (!loadedBatches.isEmpty()) {
            selectBatch(loadedBatches.get(0));
        }
    }

    private Product findProductForBatch(ProductBatch batch) {
        if (batch == null) return null;
        for (Product product : loadedProducts) {
            if (product.getId().equals(batch.getProductId())) {
                return product;
            }
        }
        return null;
    }

    private void selectBatch(ProductBatch batch) {
        selectedBatch = batch;
        selectedProduct = findProductForBatch(batch);
        if (selectedProduct == null || selectedBatch == null) return;

        if (productImageView != null) {
            String imageUrl = (selectedProduct.getImageUrls() != null && !selectedProduct.getImageUrls().isEmpty())
                    ? selectedProduct.getImageUrls().get(0) : null;
            if (imageUrl != null && !imageUrl.trim().isEmpty()) {
                Glide.with(this)
                        .load(imageUrl)
                        .centerCrop()
                        .placeholder(R.drawable.ic_placeholder_food)
                        .error(R.drawable.ic_placeholder_food)
                        .into(productImageView);
            } else {
                productImageView.setImageResource(R.drawable.ic_placeholder_food);
            }
        }

        if (productNameText != null) {
            productNameText.setText(selectedProduct.getName());
        }
        
        double price = selectedProduct.getRescuePrice() > 0
                ? selectedProduct.getRescuePrice() : selectedProduct.getOriginalPrice();
        if (productPriceText != null) {
            productPriceText.setText(currencyFormat.format(price) + " / " + selectedProduct.getUnit());
        }

        if (quantityUnitText != null) {
            quantityUnitText.setText(selectedProduct.getUnit());
        }

        if (batchHint != null) {
            String expiry = selectedBatch.getExpiryDate() != null
                    ? dateFormat.format(selectedBatch.getExpiryDate()) : "N/A";
            batchHint.setText(String.format(Locale.getDefault(),
                    "Khả dụng: %.1f %s · HSD: %s",
                    Math.min(selectedBatch.getAvailableQuantity(), campaign.calculateRemainingForBatch(selectedBatch.getId())), selectedProduct.getUnit(), expiry));
        }
    }

    private void onAddToCart(boolean isDirectCheckout) {
        if (campaign != null && "REVIEW_REQUIRED".equals(campaign.getUnitStatus())) {
            Toast.makeText(requireContext(), "Chiến dịch cần đối soát đơn vị trước khi đặt hàng.", Toast.LENGTH_LONG).show(); return;
        }
        if (!com.rescuefarm.service.campaign.CampaignTimeFormatter.canPurchase(campaign, new Date())) {
            Toast.makeText(requireContext(), "Chiến dịch chưa nhận đơn, đã hết hạn hoặc vị trí di động chưa còn hiệu lực.", Toast.LENGTH_LONG).show(); return;
        }
        if (ActorUiPolicy.isSellerReadOnly(this)) {
            Toast.makeText(requireContext(), "Tài khoản người bán không thể đặt hàng giải cứu.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (selectedProduct == null || selectedBatch == null) {
            Toast.makeText(requireContext(), "Đang tải thông tin sản phẩm chiến dịch...", Toast.LENGTH_SHORT).show();
            return;
        }
        double qty;
        try {
            qty = Double.parseDouble(quantityInput.getText().toString().trim());
        } catch (Exception e) {
            Toast.makeText(requireContext(), R.string.invalid_pricing_quantity, Toast.LENGTH_SHORT).show();
            return;
        }
        if (!Double.isFinite(qty) || qty <= 0) {
            Toast.makeText(requireContext(), R.string.invalid_pricing_quantity, Toast.LENGTH_SHORT).show();
            return;
        }
        if (!selectedBatch.hasAvailableStock(qty)) {
            Toast.makeText(requireContext(), "Số lượng vượt quá tồn kho khả dụng (" +
                    selectedBatch.getAvailableQuantity() + " " + selectedProduct.getUnit() + ")", Toast.LENGTH_LONG).show();
            return;
        }
        if (campaign == null || qty > campaign.calculateRemainingForBatch(selectedBatch.getId()) + 0.000001) {
            Toast.makeText(requireContext(), "Số lượng vượt phân bổ còn lại của lô trong chiến dịch. Hãy tải lại dữ liệu.", Toast.LENGTH_LONG).show();
            return;
        }
        pendingDirectCheckout = isDirectCheckout;
        double price = selectedProduct.getRescuePrice() > 0 ? selectedProduct.getRescuePrice() : selectedProduct.getOriginalPrice();
        cartViewModel.add(selectedProduct, selectedBatch, qty, price);
    }

    private void refreshUrgency(RescueCampaign value) {
        if (value == null || value.getBatchTargets().isEmpty()) return;
        ProductRepository repository = ProductRepositoryFactory.create(requireContext());
        repository.getBatchesByIds(new ArrayList<>(value.getBatchTargets().keySet()),
                new ProductRepository.BatchListCallback() {
            @Override public void onSuccess(List<ProductBatch> batches) {
                repository.close();
                if (!isAdded() || value.getEndDate() == null) return;
                Date earliest = null;
                if (batches != null) for (ProductBatch batch : batches) {
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
            @Override public void onSuccess(List<Product> values) {
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
                    Product product = values.get(i);
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
        if (!com.rescuefarm.service.campaign.CampaignTimeFormatter.withinSchedule(campaign, new Date())) return;
        double latitude = campaign.getLatitude();
        double longitude = campaign.getLongitude();
        if (campaign.getRescueMode() == RescueMode.MOBILE_POINT) {
            if (!campaign.isLocationFresh(new Date())) {
                Toast.makeText(requireContext(), "Vị trí điểm di động chưa được cập nhật gần đây.",
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
    
    @Override public void onDestroyView() {
        clock.removeCallbacks(timeTick);
        if (locationProvider != null) {
            locationProvider.close();
        }
        super.onDestroyView();
    }

    private static void set(View root, int id, String value) {
        TextView textView = root.findViewById(id);
        if (textView != null) {
            textView.setText(value == null ? "" : value);
            if (id == R.id.campaignDetailBadge && (value == null || value.trim().isEmpty())) {
                textView.setVisibility(View.GONE);
            } else if (id == R.id.campaignDetailProgress) {
                textView.setVisibility(View.GONE);
            } else {
                textView.setVisibility(View.VISIBLE);
            }
        }
    }
}
