package com.rescuefarm.ui.campaign;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.ImageView;
import android.widget.Toast;
import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import com.google.android.material.textfield.TextInputEditText;
import com.rescuefarm.R;
import com.rescuefarm.service.media.CloudinaryUploadUi;
import com.rescuefarm.ui.common.VietnamAddressView;
import com.rescuefarm.ui.common.DatePickerInput;
import com.rescuefarm.domain.enums.RescueMode;
import com.rescuefarm.domain.enums.RescueReason;
import com.rescuefarm.domain.model.ProductBatch;
import com.rescuefarm.domain.model.RescueCampaign;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class CampaignEditorFragment extends Fragment {
    private String id = "";
    private CampaignViewModel viewModel;
    private TextInputEditText title, description, start, end;
    private double campaignLatitude, campaignLongitude;
    private TextView gpsStatus;
    private com.rescuefarm.ui.profile.ProfileViewModel gpsModel;
    private int addressVersion, gpsRequestVersion;
    private final ActivityResultLauncher<String[]> gpsPermission = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                if (Boolean.TRUE.equals(result.get(android.Manifest.permission.ACCESS_FINE_LOCATION))
                        || Boolean.TRUE.equals(result.get(android.Manifest.permission.ACCESS_COARSE_LOCATION))) takeGps();
                else if (gpsStatus != null) gpsStatus.setText("Cần cấp quyền vị trí để lấy GPS điểm giải cứu.");
            });
    private VietnamAddressView locationName;
    private Spinner reason, mode;
    private LinearLayout batchContainer;
    private TextView batchLoading;
    private TextView imageStatus;
    private LinearLayout imageGallery;
    private final List<String> imageUrls = new ArrayList<>();
    private final List<String> imagePublicIds = new ArrayList<>();
    private final ActivityResultLauncher<String> imagePicker = registerForActivityResult(
            new ActivityResultContracts.GetContent(), this::uploadCampaignImage);
    private int nextImageSlot;
    private final Map<String, Double> selectedTargets = new LinkedHashMap<>();
    private final Map<String, CheckBox> batchChecks = new LinkedHashMap<>();
    private final Map<String, EditText> quantityInputs = new LinkedHashMap<>();
    private boolean dirty, binding, optionsLoaded;

    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater,
            @Nullable ViewGroup parent, @Nullable Bundle state) {
        return inflater.inflate(R.layout.fragment_campaign_editor, parent, false);
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        imageStatus = view.findViewById(R.id.campaignImageUploadStatus);
        imageGallery = view.findViewById(R.id.campaignImageGallery);
        title = view.findViewById(R.id.campaignTitleInput);
        description = view.findViewById(R.id.campaignDescriptionInput);
        title.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(120)});
        description.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(3000)});
        start = view.findViewById(R.id.campaignStartInput);
        end = view.findViewById(R.id.campaignEndInput);
        DatePickerInput.bind(start);
        DatePickerInput.bind(end);
        gpsStatus = view.findViewById(R.id.campaignGpsStatus);
        gpsModel = new ViewModelProvider(this, new com.rescuefarm.ui.profile.ProfileViewModelFactory(requireContext()))
                .get(com.rescuefarm.ui.profile.ProfileViewModel.class);
        view.findViewById(R.id.campaignUseGpsButton).setOnClickListener(v -> requestGps());
        locationName = view.findViewById(R.id.campaignLocationNameInput);
        reason = view.findViewById(R.id.campaignReasonSpinner);
        mode = view.findViewById(R.id.campaignModeSpinner);
        batchContainer = view.findViewById(R.id.campaignBatchOptions);
        batchLoading = view.findViewById(R.id.campaignBatchLoading);
        reason.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_dropdown_item,
                RescueReason.values()));
        mode.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_dropdown_item,
                RescueMode.values()));
        id = getArguments() == null ? "" : getArguments().getString("campaignId", "");
        if (id.trim().isEmpty()) id = UUID.randomUUID().toString();
        view.findViewById(R.id.uploadCampaignImageButton).setOnClickListener(v -> {
            if (imageUrls.size() >= 6) {
                Toast.makeText(requireContext(), "Mỗi chiến dịch tối đa 6 ảnh.", Toast.LENGTH_SHORT).show();
                return;
            }
            imagePicker.launch("image/*");
        });
        viewModel = new ViewModelProvider(this, new CampaignViewModelFactory(requireContext()))
                .get(CampaignViewModel.class);
        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (!binding) dirty = true;
            }
            @Override public void afterTextChanged(Editable s) { }
        };
        for (TextInputEditText input : new TextInputEditText[]{title, description, start, end}) input.addTextChangedListener(watcher);
        locationName.setOnAddressChangedListener(() -> {
            if (!binding) { dirty = true; addressVersion++; campaignLatitude = 0; campaignLongitude = 0; showGps(); }
        });
        installSpinnerDirtyListener(reason);
        installSpinnerDirtyListener(mode);
        viewModel.getBatchOptions().observe(getViewLifecycleOwner(), this::renderBatchOptions);
        viewModel.getState().observe(getViewLifecycleOwner(), value -> {
            if (value.getStatus() == CampaignScreenState.Status.CAMPAIGN && !dirty)
                populate(value.getCampaign());
            else if (value.getStatus() == CampaignScreenState.Status.SAVED) {
                Toast.makeText(requireContext(), value.getMessage(), Toast.LENGTH_SHORT).show();
                NavHostFragment.findNavController(this).popBackStack();
            } else if (value.getStatus() == CampaignScreenState.Status.ERROR)
                Toast.makeText(requireContext(), value.getMessage(), Toast.LENGTH_LONG).show();
        });
        view.findViewById(R.id.saveCampaignDraftButton).setOnClickListener(v -> save(false));
        view.findViewById(R.id.submitCampaignButton).setOnClickListener(v -> save(true));
        if (!id.isEmpty()) {
            viewModel.observeCampaign(id).observe(getViewLifecycleOwner(), value -> {
                if (value != null && !dirty) populate(value);
            });
        }
        gpsModel.getState().observe(getViewLifecycleOwner(), result -> {
            if (result.getStatus() == com.rescuefarm.ui.profile.ProfileScreenState.Status.LOCATION) {
                if (gpsRequestVersion == addressVersion) {
                    binding = true;
                    locationName.applyGeocodedAddress(result.getGeocodedAddress());
                    campaignLatitude = result.getLatitude(); campaignLongitude = result.getLongitude();
                    binding = false; dirty = true; showGps();
                }
                gpsModel.clearTransientState();
            } else if (result.getStatus() == com.rescuefarm.ui.profile.ProfileScreenState.Status.ERROR) {
                gpsStatus.setText(result.getMessage()); gpsModel.clearTransientState();
            }
        });
        viewModel.loadSellerBatches(id);
        if (!id.isEmpty()) viewModel.load(id);
    }

    private void renderBatchOptions(List<CampaignBatchOption> options) {
        if (!isAdded() || batchContainer == null) return;
        preserveVisibleTargets();
        batchContainer.removeAllViews(); batchChecks.clear(); quantityInputs.clear();
        optionsLoaded = true;
        batchLoading.setVisibility(options == null || options.isEmpty() ? View.VISIBLE : View.GONE);
        if (options == null || options.isEmpty()) {
            batchLoading.setText("Chưa có lô hàng nào. Hãy tạo Product và ProductBatch trước khi lập Campaign.");
            return;
        }
        Date now = new Date();
        for (CampaignBatchOption option : options) {
            ProductBatch batch = option.getBatch();
            String batchId = batch.getId();
            boolean selectable = option.isSelectable(id, now);
            boolean alreadySelected = selectedTargets.containsKey(batchId);
            LinearLayout card = new LinearLayout(requireContext());
            card.setOrientation(LinearLayout.VERTICAL);
            int padding = (int) (12 * getResources().getDisplayMetrics().density);
            card.setPadding(padding, padding, padding, padding);
            android.graphics.drawable.GradientDrawable cardBackground = new android.graphics.drawable.GradientDrawable();
            cardBackground.setColor(ContextCompat.getColor(requireContext(), R.color.rescue_surface));
            cardBackground.setCornerRadius(12 * getResources().getDisplayMetrics().density);
            cardBackground.setStroke(Math.max(1, padding / 12),
                    ContextCompat.getColor(requireContext(), R.color.rescue_outline));
            card.setBackground(cardBackground);
            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            cardParams.bottomMargin = padding / 2;
            batchContainer.addView(card, cardParams);

            CheckBox check = new CheckBox(requireContext());
            check.setText(option.getProduct().getName() + " · lô " + batchId);
            check.setChecked(alreadySelected);
            check.setEnabled(selectable || alreadySelected);
            card.addView(check);
            String dates = "Thu hoạch: " + date(batch.getHarvestDate()) + " · Hạn dùng: "
                    + date(batch.getExpiryDate());
            addCardText(card, dates);
            addCardText(card, String.format(Locale.getDefault(),
                    "Khả dụng %.2f %s · Đang giữ %.2f · Đã bán %.2f · %s",
                    batch.getAvailableQuantity(), option.getProduct().getUnit(), batch.getReservedQuantity(),
                    batch.getSoldQuantity(), batch.getStatus().name()));
            TextView warning = new TextView(requireContext());
            warning.setText(alreadySelected && !selectable
                    ? "Batch đang được campaign khác giữ, hết hạn hoặc không còn bán được. Điều chỉnh lựa chọn trước khi gửi."
                    : selectable ? "" : "Không khả dụng cho Campaign mới.");
            warning.setTextColor(ContextCompat.getColor(requireContext(), R.color.rescue_critical));
            card.addView(warning);
            EditText quantity = new EditText(requireContext());
            quantity.setSingleLine(true);
            quantity.setInputType(android.text.InputType.TYPE_CLASS_NUMBER
                    | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
            quantity.setHint("Lượng mục tiêu (" + option.getProduct().getUnit() + ")");
            Double previous = selectedTargets.get(batchId);
            if (previous != null) quantity.setText(String.valueOf(previous));
            quantity.setEnabled(alreadySelected);
            card.addView(quantity);
            batchChecks.put(batchId, check); quantityInputs.put(batchId, quantity);
            check.setOnCheckedChangeListener((button, checked) -> {
                if (checked && selectedTargets.size() >= 200 && !selectedTargets.containsKey(batchId)) {
                    button.setChecked(false);
                    Toast.makeText(requireContext(), "Mỗi Campaign hỗ trợ tối đa 200 lô để an toàn với Firestore.",
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                quantity.setEnabled(checked);
                if (!checked) selectedTargets.remove(batchId);
                else if (!selectedTargets.containsKey(batchId)) selectedTargets.put(batchId, 0D);
                if (!binding) dirty = true;
            });
            quantity.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                    if (check.isChecked()) {
                        try { selectedTargets.put(batchId, Double.parseDouble(s.toString())); }
                        catch (NumberFormatException error) { selectedTargets.put(batchId, Double.NaN); }
                    }
                    if (!binding) dirty = true;
                }
                @Override public void afterTextChanged(Editable s) { }
            });
        }
        for (String missingBatchId : new ArrayList<>(selectedTargets.keySet())) {
            if (batchChecks.containsKey(missingBatchId)) continue;
            LinearLayout staleCard = new LinearLayout(requireContext());
            staleCard.setOrientation(LinearLayout.VERTICAL);
            int padding = (int) (12 * getResources().getDisplayMetrics().density);
            staleCard.setPadding(padding, padding, padding, padding);
            staleCard.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.rescue_surface));
            CheckBox stale = new CheckBox(requireContext());
            stale.setText("Batch " + missingBatchId + " (không còn trong danh sách batch của Seller)");
            stale.setChecked(true);
            TextView warning = new TextView(requireContext());
            warning.setText("Bỏ chọn batch này trước khi lưu hoặc tải lại dữ liệu sản phẩm/batch.");
            warning.setTextColor(ContextCompat.getColor(requireContext(), R.color.rescue_critical));
            staleCard.addView(stale); staleCard.addView(warning); batchContainer.addView(staleCard);
            batchChecks.put(missingBatchId, stale);
            stale.setOnCheckedChangeListener((button, checked) -> {
                if (!checked) selectedTargets.remove(missingBatchId);
                else if (!selectedTargets.containsKey(missingBatchId)) selectedTargets.put(missingBatchId, 0D);
                if (!binding) dirty = true;
            });
        }
    }

    private void preserveVisibleTargets() {
        for (Map.Entry<String, CheckBox> entry : batchChecks.entrySet()) {
            String batchId = entry.getKey();
            if (!entry.getValue().isChecked()) { selectedTargets.remove(batchId); continue; }
            EditText quantity = quantityInputs.get(batchId);
            if (quantity == null) continue;
            try { selectedTargets.put(batchId, Double.parseDouble(quantity.getText().toString())); }
            catch (NumberFormatException error) { selectedTargets.put(batchId, Double.NaN); }
        }
    }

    private void addCardText(LinearLayout card, String value) {
        TextView text = new TextView(requireContext()); text.setText(value); card.addView(text);
    }

    private void save(boolean submit) {
        if (submit && !locationName.validate()) return;
        preserveVisibleTargets();
        viewModel.save(id, text(title), text(description), (RescueReason) reason.getSelectedItem(),
                (RescueMode) mode.getSelectedItem(), new LinkedHashMap<>(selectedTargets), text(start),
            text(end), String.valueOf(campaignLatitude), String.valueOf(campaignLongitude), locationName.isComplete()
                    ? locationName.formattedAddress() : locationName.street(), submit,
            new ArrayList<>(imageUrls), new ArrayList<>(imagePublicIds));
    }

    private void uploadCampaignImage(Uri uri) {
        if (uri == null || !isAdded()) return;
        if (imageUrls.size() >= 6) { Toast.makeText(requireContext(), "Mỗi chiến dịch tối đa 6 ảnh.", Toast.LENGTH_SHORT).show(); return; }
        String uid = com.google.firebase.auth.FirebaseAuth.getInstance().getUid();
        if (uid == null || uid.trim().isEmpty()) { Toast.makeText(requireContext(), "Vui lòng đăng nhập Seller.", Toast.LENGTH_SHORT).show(); return; }
        int slot = (nextImageSlot++) % 6;
        CloudinaryUploadUi.upload(this, uri, "campaign", id, slot,
                requireView().findViewById(R.id.uploadCampaignImageButton), imageStatus, null, url -> {
                    imageUrls.add(url);
                    imagePublicIds.add(com.rescuefarm.service.media.CloudinaryAssetIds.fromSecureUrl(url));
                    dirty = true; renderCampaignImages();
                });
    }

    private void renderCampaignImages() {
        if (!isAdded() || imageGallery == null) return;
        imageGallery.removeAllViews();
        for (int i = 0; i < imageUrls.size(); i++) {
            final int index = i;
            LinearLayout item = new LinearLayout(requireContext()); item.setOrientation(LinearLayout.VERTICAL);
            ImageView image = new ImageView(requireContext()); image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            int size = (int) (112 * getResources().getDisplayMetrics().density);
            com.bumptech.glide.Glide.with(this).load(imageUrls.get(i)).centerCrop().into(image);
            item.addView(image, new LinearLayout.LayoutParams(size, size));
            TextView remove = new TextView(requireContext()); remove.setText("Gỡ ảnh");
            remove.setOnClickListener(v -> { imageUrls.remove(index); imagePublicIds.remove(index); dirty = true; renderCampaignImages(); });
            item.addView(remove); imageGallery.addView(item);
        }
    }

    private void installSpinnerDirtyListener(Spinner spinner) {
        boolean[] initialized = {false};
        spinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View selected,
                    int position, long itemId) {
                if (!initialized[0]) initialized[0] = true;
                else if (!binding) dirty = true;
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        });
    }

    private void populate(RescueCampaign value) {
        binding = true;
        title.setText(value.getTitle()); description.setText(value.getDescription());
        imageUrls.clear(); imageUrls.addAll(value.getImageUrls());
        nextImageSlot = imageUrls.size();
        imagePublicIds.clear(); imagePublicIds.addAll(value.getImagePublicIds());
        while (imagePublicIds.size() < imageUrls.size())
            imagePublicIds.add(com.rescuefarm.service.media.CloudinaryAssetIds.fromSecureUrl(
                    imageUrls.get(imagePublicIds.size())));
        renderCampaignImages();
        reason.setSelection(value.getRescueReason().ordinal()); mode.setSelection(value.getRescueMode().ordinal());
        selectedTargets.clear(); selectedTargets.putAll(value.getBatchTargets());
        if (optionsLoaded) renderBatchOptions(viewModel.getBatchOptions().getValue());
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        start.setText(format.format(value.getStartDate())); end.setText(format.format(value.getEndDate()));
        campaignLatitude = value.getLatitude(); campaignLongitude = value.getLongitude(); showGps();
        locationName.setFormattedAddress(value.getLocationName());
        binding = false;
    }

    private void requestGps() {
        if (ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.ACCESS_FINE_LOCATION)
                == android.content.pm.PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.ACCESS_COARSE_LOCATION)
                == android.content.pm.PackageManager.PERMISSION_GRANTED) takeGps();
        else gpsPermission.launch(new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION,
                android.Manifest.permission.ACCESS_COARSE_LOCATION});
    }
    private void takeGps() {
        gpsRequestVersion = addressVersion;
        gpsStatus.setText("Đang lấy GPS điểm giải cứu…");
        gpsModel.requestCurrentLocation(true);
    }
    private void showGps() {
        gpsStatus.setText(com.rescuefarm.service.location.GpsCoordinates.isValid(campaignLatitude, campaignLongitude)
                ? R.string.gps_selected : R.string.gps_not_selected);
    }
    @Override public void onViewStateRestored(@Nullable Bundle state) {
        super.onViewStateRestored(state);
        if (state != null) {
            campaignLatitude = state.getDouble("campaignLatitude"); campaignLongitude = state.getDouble("campaignLongitude");
            dirty = true; showGps();
        }
    }
    @Override public void onSaveInstanceState(@NonNull Bundle state) {
        super.onSaveInstanceState(state);
        state.putDouble("campaignLatitude", campaignLatitude); state.putDouble("campaignLongitude", campaignLongitude);
    }

    private String date(Date value) {
        return value == null ? "Chưa đặt" : new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(value);
    }
    private static String text(TextInputEditText value) {
        return value.getText() == null ? "" : value.getText().toString();
    }
}
