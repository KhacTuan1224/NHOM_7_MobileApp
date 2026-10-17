package com.rescuefarm.ui.home;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.Spinner;
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
import com.google.android.material.textfield.TextInputEditText;
import com.rescuefarm.R;
import com.rescuefarm.ui.common.ProvinceFilterPicker;
import com.rescuefarm.domain.enums.RescueMode;
import com.rescuefarm.domain.enums.RescueReason;
import com.rescuefarm.domain.enums.UrgencyLevel;
import com.rescuefarm.domain.model.Category;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class DiscoveryFragment extends Fragment {
    private HomeViewModel viewModel; private HomeCardRenderer renderer;
    private TextInputEditText searchInput, provinceInput, minPriceInput, maxPriceInput,
            radiusInput, expiryDaysInput, minStockInput;
    private Spinner categorySpinner, reasonSpinner, urgencySpinner, modeSpinner, sortSpinner;
    private LinearLayout results; private TextView count;
    private final List<Category> categories = new ArrayList<>();
    private final ActivityResultLauncher<String[]> permissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(), this::onPermissions);

    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater,
            @Nullable ViewGroup container, @Nullable Bundle state) {
        return inflater.inflate(R.layout.fragment_discovery, container, false);
    }
    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        renderer = new HomeCardRenderer(this);
        searchInput = view.findViewById(R.id.searchInput); provinceInput = view.findViewById(R.id.provinceInput);
        ProvinceFilterPicker.bind(provinceInput);
        minPriceInput = view.findViewById(R.id.minPriceInput); maxPriceInput = view.findViewById(R.id.maxPriceInput);
        radiusInput = view.findViewById(R.id.radiusInput); expiryDaysInput = view.findViewById(R.id.expiryDaysInput);
        minStockInput = view.findViewById(R.id.minStockInput);
        categorySpinner = view.findViewById(R.id.categorySpinner); reasonSpinner = view.findViewById(R.id.reasonSpinner);
        urgencySpinner = view.findViewById(R.id.urgencySpinner); modeSpinner = view.findViewById(R.id.modeSpinner);
        sortSpinner = view.findViewById(R.id.sortSpinner); results = view.findViewById(R.id.searchResultContainer);
        count = view.findViewById(R.id.searchCount);
        setupEnumSpinner(reasonSpinner, "Tất cả reason", RescueReason.values());
        setupEnumSpinner(urgencySpinner, "Tất cả urgency", UrgencyLevel.values());
        setupEnumSpinner(modeSpinner, "Tất cả mode", RescueMode.values());
        setupEnumSpinner(sortSpinner, null, DiscoveryQuery.Sort.values());
        viewModel = new ViewModelProvider(this, new HomeViewModelFactory(requireContext()))
                .get(HomeViewModel.class);
        restoreInputs(viewModel.getQuery());
        viewModel.getHomeState().observe(getViewLifecycleOwner(), value -> setupCategories(value.getCategories()));
        viewModel.getSearchResults().observe(getViewLifecycleOwner(), this::render);
        view.findViewById(R.id.applyFilterButton).setOnClickListener(v -> apply());
        view.findViewById(R.id.clearFilterButton).setOnClickListener(v -> clear());
        view.findViewById(R.id.discoveryLocationButton).setOnClickListener(v -> requestLocation());
        viewModel.refresh();
    }
    private void setupCategories(List<Category> values) {
        String selectedId = viewModel.getQuery().getCategoryId();
        if (selectedId.isEmpty() && getArguments() != null) selectedId = getArguments().getString("categoryId", "");
        if (categories.size() == values.size() && !categories.isEmpty()) return;
        categories.clear(); categories.addAll(values); List<String> labels = new ArrayList<>(); labels.add("Tất cả danh mục");
        int selected = 0;
        for (int i = 0; i < categories.size(); i++) {
            labels.add(categories.get(i).getName());
            if (categories.get(i).getId().equals(selectedId)) selected = i + 1;
        }
        categorySpinner.setAdapter(adapter(labels)); categorySpinner.setSelection(selected);
        if (!selectedId.isEmpty() && viewModel.getQuery().getCategoryId().isEmpty()) apply();
    }
    private <T extends Enum<T>> void setupEnumSpinner(Spinner spinner, String allLabel, T[] values) {
        List<String> labels = new ArrayList<>(); if (allLabel != null) labels.add(allLabel);
        for (T value : values) labels.add(value.name().replace('_', ' ')); spinner.setAdapter(adapter(labels));
    }
    private ArrayAdapter<String> adapter(List<String> values) {
        ArrayAdapter<String> result = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, values);
        result.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); return result;
    }
    private void apply() {
        String category = categorySpinner.getSelectedItemPosition() <= 0 ? ""
                : categories.get(categorySpinner.getSelectedItemPosition() - 1).getId();
        try {
            double min = decimal(minPriceInput, 0D), max = decimal(maxPriceInput, Double.MAX_VALUE);
            if (max < min) throw new IllegalArgumentException("Giá tối đa phải lớn hơn hoặc bằng giá tối thiểu.");
            viewModel.setQuery(new DiscoveryQuery(text(searchInput), category, text(provinceInput),
                    selected(RescueReason.values(), reasonSpinner), selected(UrgencyLevel.values(), urgencySpinner),
                    selected(RescueMode.values(), modeSpinner),
                    DiscoveryQuery.Sort.values()[sortSpinner.getSelectedItemPosition()], min, max,
                    decimal(radiusInput, 0D), integer(expiryDaysInput), decimal(minStockInput, 0D)));
        } catch (IllegalArgumentException error) {
            Toast.makeText(requireContext(), error.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    private <T> T selected(T[] values, Spinner spinner) {
        int index = spinner.getSelectedItemPosition() - 1; return index < 0 ? null : values[index];
    }
    private void clear() {
        searchInput.setText(""); provinceInput.setText(""); categorySpinner.setSelection(0);
        minPriceInput.setText(""); maxPriceInput.setText(""); radiusInput.setText("");
        expiryDaysInput.setText(""); minStockInput.setText("");
        reasonSpinner.setSelection(0); urgencySpinner.setSelection(0); modeSpinner.setSelection(0);
        sortSpinner.setSelection(0); viewModel.setQuery(DiscoveryQuery.empty());
    }
    private void render(List<DiscoveryResult> values) {
        results.removeAllViews(); count.setText(values.size() + " kết quả từ cache/Firestore");
        if (values.isEmpty()) { results.addView(renderer.message("Không tìm thấy dữ liệu phù hợp.")); return; }
        for (DiscoveryResult value : values) {
            if (value.getType() == DiscoveryResult.Type.PRODUCT) {
                results.addView(renderer.product(value.getProduct(), v -> openProduct(value)));
            } else {
                results.addView(renderer.campaign(value.getCampaign(), value.getDistanceKilometers(),
                        v -> openCampaign(value.getCampaign().getId())));
            }
        }
    }
    private void requestLocation() {
        boolean fine = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        boolean coarse = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        if (fine || coarse) viewModel.requestNearbyLocation();
        else permissionLauncher.launch(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION});
    }
    private void onPermissions(Map<String, Boolean> values) {
        if (Boolean.TRUE.equals(values.get(Manifest.permission.ACCESS_FINE_LOCATION))
                || Boolean.TRUE.equals(values.get(Manifest.permission.ACCESS_COARSE_LOCATION))) viewModel.requestNearbyLocation();
        else viewModel.onLocationPermissionDenied();
    }
    private void openProduct(DiscoveryResult result) { Bundle args = new Bundle();
        args.putString("productId", result.getProduct().getId());
        args.putString("batchId", result.getBatchId());
        args.putString("campaignId", result.getCampaignId());
        Navigation.findNavController(requireView()).navigate(R.id.action_discoveryFragment_to_productDetailFragment, args); }
    private void openCampaign(String id) { Bundle args = new Bundle(); args.putString("campaignId", id);
        Navigation.findNavController(requireView()).navigate(R.id.action_discoveryFragment_to_campaignDetailFragment, args); }
    private static String text(TextInputEditText input) {
        return input.getText() == null ? "" : input.getText().toString().trim();
    }
    private double decimal(TextInputEditText input, double fallback) {
        String value = text(input); if (value.isEmpty()) return fallback;
        try { double parsed = Double.parseDouble(value.replace(',', '.'));
            if (!Double.isFinite(parsed) || parsed < 0D) throw new NumberFormatException();
            return parsed;
        } catch (NumberFormatException error) { throw new IllegalArgumentException("Giá, bán kính và tồn kho phải là số không âm."); }
    }
    private int integer(TextInputEditText input) {
        String value = text(input); if (value.isEmpty()) return 0;
        try { int parsed = Integer.parseInt(value); if (parsed < 0) throw new NumberFormatException(); return parsed; }
        catch (NumberFormatException error) { throw new IllegalArgumentException("Số ngày hết hạn phải là số nguyên không âm."); }
    }
    private void restoreInputs(DiscoveryQuery query) {
        searchInput.setText(query.getText()); provinceInput.setText(query.getProvince());
        minPriceInput.setText(query.getMinPrice() == 0D ? "" : String.valueOf(query.getMinPrice()));
        maxPriceInput.setText(query.getMaxPrice() == Double.MAX_VALUE ? "" : String.valueOf(query.getMaxPrice()));
        radiusInput.setText(query.getRadiusKm() == 0D ? "" : String.valueOf(query.getRadiusKm()));
        expiryDaysInput.setText(query.getExpiryWithinDays() == 0 ? "" : String.valueOf(query.getExpiryWithinDays()));
        minStockInput.setText(query.getMinStock() == 0D ? "" : String.valueOf(query.getMinStock()));
        selectEnum(reasonSpinner, query.getReason()); selectEnum(urgencySpinner, query.getUrgency());
        selectEnum(modeSpinner, query.getMode()); selectEnum(sortSpinner, query.getSort());
    }
    private void selectEnum(Spinner spinner, Enum<?> selected) {
        if (selected != null) for (int i = 0; i < spinner.getCount(); i++)
            if (spinner.getItemAtPosition(i).toString().equals(selected.name().replace('_', ' '))) {
                spinner.setSelection(i); return;
            }
    }
}
