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
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
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

public class DiscoveryBottomSheet extends BottomSheetDialogFragment {
    private HomeViewModel viewModel;
    private HomeCardRenderer renderer;
    private TextInputEditText searchInput, provinceInput, minPriceInput, maxPriceInput,
            radiusInput, expiryDaysInput, minStockInput;
    private Spinner categorySpinner, reasonSpinner, urgencySpinner, modeSpinner, sortSpinner;
    private LinearLayout results;
    private TextView count;
    private boolean filterOnly;
    private final List<Category> categories = new ArrayList<>();
    private final ActivityResultLauncher<String[]> permissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(), this::onPermissions);

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottom_sheet_discovery, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        filterOnly = getArguments() != null && getArguments().getBoolean("filterOnly", false);
        
        // Setup bottom sheet behavior to expand fully
        View parent = (View) view.getParent();
        if (parent != null) {
            BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(parent);
            behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            behavior.setSkipCollapsed(true);
            // Set peek height to show most of the content
            behavior.setPeekHeight((int) (getResources().getDisplayMetrics().heightPixels * 0.9));
        }

        renderer = new HomeCardRenderer(this);
        searchInput = view.findViewById(R.id.searchInput);
        provinceInput = view.findViewById(R.id.provinceInput);
        ProvinceFilterPicker.bind(provinceInput);
        minPriceInput = view.findViewById(R.id.minPriceInput); maxPriceInput = view.findViewById(R.id.maxPriceInput);
        radiusInput = view.findViewById(R.id.radiusInput); expiryDaysInput = view.findViewById(R.id.expiryDaysInput);
        minStockInput = view.findViewById(R.id.minStockInput);
        categorySpinner = view.findViewById(R.id.categorySpinner);
        reasonSpinner = view.findViewById(R.id.reasonSpinner);
        urgencySpinner = view.findViewById(R.id.urgencySpinner);
        modeSpinner = view.findViewById(R.id.modeSpinner);
        sortSpinner = view.findViewById(R.id.sortSpinner);
        results = view.findViewById(R.id.searchResultContainer);
        count = view.findViewById(R.id.searchCount);

        setupEnumSpinner(reasonSpinner, "Tất cả lý do", RescueReason.values());
        setupEnumSpinner(urgencySpinner, "Tất cả mức độ", UrgencyLevel.values());
        setupEnumSpinner(modeSpinner, "Tất cả hình thức", RescueMode.values());
        setupEnumSpinner(sortSpinner, null, DiscoveryQuery.Sort.values());

        viewModel = new ViewModelProvider(filterOnly && getParentFragment() instanceof GuestHomeFragment
                ? requireParentFragment() : requireActivity(), new HomeViewModelFactory(requireContext()))
                .get(HomeViewModel.class);
        restoreInputs(viewModel.getQuery());
        if (filterOnly) {
            view.findViewById(R.id.discoveryKeywordLayout).setVisibility(View.GONE);
            ((TextView) view.findViewById(R.id.discoverySheetTitle)).setText(R.string.home_filter_title);
            count.setVisibility(View.GONE);
            results.setVisibility(View.GONE);
        }
        viewModel.getHomeState().observe(getViewLifecycleOwner(), value -> setupCategories(value.getCategories()));
        if (!filterOnly) viewModel.getSearchResults().observe(getViewLifecycleOwner(), this::render);

        view.findViewById(R.id.applyFilterButton).setOnClickListener(v -> {
            if (apply() && filterOnly) {
                getParentFragmentManager().setFragmentResult("home.filtersApplied", new Bundle());
                dismiss();
            }
        });
        view.findViewById(R.id.clearFilterButton).setOnClickListener(v -> clear());
        view.findViewById(R.id.discoveryLocationButton).setOnClickListener(v -> requestLocation());
        
        // Close button
        view.findViewById(R.id.closeButton).setOnClickListener(v -> dismiss());

        // Load initial data if needed
        if (getArguments() != null && getArguments().containsKey("categoryId")) {
            // Will be handled in setupCategories
        }
    }

    private void setupCategories(List<Category> values) {
        String selectedId = viewModel.getQuery().getCategoryId();
        if (selectedId.isEmpty() && getArguments() != null) {
            selectedId = getArguments().getString("categoryId", "");
        }
        if (categories.size() == values.size() && !categories.isEmpty()) return;
        
        categories.clear();
        categories.addAll(values);
        List<String> labels = new ArrayList<>();
        labels.add("Tất cả danh mục");
        int selected = 0;
        for (int i = 0; i < categories.size(); i++) {
            labels.add(categories.get(i).getName());
            if (categories.get(i).getId().equals(selectedId)) selected = i + 1;
        }
        categorySpinner.setAdapter(adapter(labels));
        categorySpinner.setSelection(selected);
        if (!selectedId.isEmpty() && viewModel.getQuery().getCategoryId().isEmpty()) apply();
    }

    private <T extends Enum<T>> void setupEnumSpinner(Spinner spinner, String allLabel, T[] values) {
        List<String> labels = new ArrayList<>();
        if (allLabel != null) labels.add(allLabel);
        for (T value : values) labels.add(enumLabel(value));
        spinner.setAdapter(adapter(labels));
    }

    private ArrayAdapter<String> adapter(List<String> values) {
        ArrayAdapter<String> result = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, values);
        result.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        return result;
    }

    private boolean apply() {
        String category = categorySpinner.getSelectedItemPosition() <= 0 ? ""
                : categories.get(categorySpinner.getSelectedItemPosition() - 1).getId();
        try {
            double min = decimal(minPriceInput, 0D), max = decimal(maxPriceInput, Double.MAX_VALUE);
            if (max < min) throw new IllegalArgumentException("Giá tối đa phải lớn hơn hoặc bằng giá tối thiểu.");
            viewModel.setQuery(new DiscoveryQuery(filterOnly ? viewModel.getQuery().getText() : text(searchInput), category, text(provinceInput),
                    selected(RescueReason.values(), reasonSpinner), selected(UrgencyLevel.values(), urgencySpinner),
                    selected(RescueMode.values(), modeSpinner),
                    DiscoveryQuery.Sort.values()[sortSpinner.getSelectedItemPosition()], min, max,
                    decimal(radiusInput, 0D), integer(expiryDaysInput), decimal(minStockInput, 0D)));
            return true;
        } catch (IllegalArgumentException error) {
            Toast.makeText(requireContext(), error.getMessage(), Toast.LENGTH_SHORT).show();
            return false;
        }
    }

    private <T> T selected(T[] values, Spinner spinner) {
        int index = spinner.getSelectedItemPosition() - 1;
        return index < 0 ? null : values[index];
    }

    private void clear() {
        String keyword = filterOnly ? viewModel.getQuery().getText() : "";
        searchInput.setText(keyword);
        provinceInput.setText("");
        minPriceInput.setText(""); maxPriceInput.setText(""); radiusInput.setText("");
        expiryDaysInput.setText(""); minStockInput.setText("");
        categorySpinner.setSelection(0);
        reasonSpinner.setSelection(0);
        urgencySpinner.setSelection(0);
        modeSpinner.setSelection(0);
        sortSpinner.setSelection(0);
        viewModel.setQuery(DiscoveryQuery.empty().withText(keyword));
        if (filterOnly) getParentFragmentManager().setFragmentResult("home.filtersApplied", new Bundle());
    }

    private void render(List<DiscoveryResult> values) {
        results.removeAllViews();
        count.setText(values.size() + " kết quả · Giá và lượng bán so sánh trong cùng đơn vị");
        if (values.isEmpty()) {
            results.addView(renderer.message("Không tìm thấy dữ liệu phù hợp."));
            return;
        }
        for (DiscoveryResult value : values) {
            if (value.getType() == DiscoveryResult.Type.PRODUCT) {
                results.addView(renderer.product(value.getProduct(), v -> openProduct(value)));
            } else {
                android.widget.LinearLayout group = new android.widget.LinearLayout(requireContext());
                group.setOrientation(android.widget.LinearLayout.VERTICAL);
                group.addView(renderer.campaign(value.getCampaign(), value.getDistanceKilometers(),
                        v -> openCampaign(value.getCampaign().getId())));
                android.widget.TextView metrics = new android.widget.TextView(requireContext());
                metrics.setText(String.format(java.util.Locale.forLanguageTag("vi-VN"),
                        "Từ %,.0f đ / %s · Giảm tối đa %.0f%%\nĐã giao trong Campaign: %.2f %s (toàn thời gian)",
                        value.getMinimumPrice(), value.getCampaign().getQuantityUnitLabel(), value.getMaximumDiscount(),
                        value.getSoldQuantity(), value.getCampaign().getQuantityUnitLabel()));
                group.addView(metrics); results.addView(group);
            }
        }
    }

    private void requestLocation() {
        boolean fine = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        boolean coarse = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        if (fine || coarse) {
            Toast.makeText(requireContext(), "Đang lấy vị trí hiện tại…", Toast.LENGTH_SHORT).show();
            viewModel.requestNearbyLocation();
        } else permissionLauncher.launch(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION});
    }

    private void onPermissions(Map<String, Boolean> values) {
        if (Boolean.TRUE.equals(values.get(Manifest.permission.ACCESS_FINE_LOCATION))
                || Boolean.TRUE.equals(values.get(Manifest.permission.ACCESS_COARSE_LOCATION))) {
            Toast.makeText(requireContext(), "Đang lấy vị trí hiện tại…", Toast.LENGTH_SHORT).show();
            viewModel.requestNearbyLocation();
        } else {
            Toast.makeText(requireContext(), "Chưa có quyền vị trí.", Toast.LENGTH_SHORT).show();
            viewModel.onLocationPermissionDenied();
        }
    }

    private void openProduct(DiscoveryResult result) {
        Bundle args = new Bundle();
        args.putString("productId", result.getProduct().getId());
        args.putString("batchId", result.getBatchId());
        args.putString("campaignId", result.getCampaignId());
        dismiss();
        Navigation.findNavController(requireActivity().findViewById(R.id.main))
                .navigate(R.id.productDetailFragment, args);
    }

    private void openCampaign(String id) {
        Bundle args = new Bundle();
        args.putString("campaignId", id);
        dismiss();
        Navigation.findNavController(requireActivity().findViewById(R.id.main))
                .navigate(R.id.campaignDetailFragment, args);
    }

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
            if (spinner.getItemAtPosition(i).toString().equals(enumLabel(selected))) {
                spinner.setSelection(i); return;
            }
    }

    private String enumLabel(Enum<?> value) {
        switch (value.name()) {
            case "OVER_SUPPLY": return "Dư cung";
            case "NEAR_EXPIRY": return "Gần hết hạn";
            case "SEASONAL_HARVEST": return "Thu hoạch vào mùa";
            case "WEATHER_IMPACT": return "Ảnh hưởng thời tiết";
            case "ORDER_CANCELLED": return "Đơn hàng bị hủy";
            case "LOGISTICS_DISRUPTION": return "Gián đoạn vận chuyển";
            case "DEMAND_DROP": return "Nhu cầu giảm";
            case "COSMETIC_GRADE": return "Ngoại hình chưa đẹp";
            case "NORMAL": return "Bình thường";
            case "HIGH": return "Khẩn cấp";
            case "CRITICAL": return "Rất khẩn cấp";
            case "FIXED_POINT": return "Điểm cố định";
            case "MOBILE_POINT": return "Điểm di động";
            case "RELEVANCE": return "Phù hợp nhất";
            case "URGENCY": return "Mức độ khẩn cấp";
            case "DISCOUNT_DESC": return "Giảm giá nhiều nhất";
            case "PRICE_ASC": return "Giá tăng dần";
            case "DISTANCE": return "Gần bạn nhất";
            case "NEAREST_DEADLINE": return "Sắp kết thúc";
            case "BEST_SELLING": return "Bán chạy nhất";
            default: return value.name().replace('_', ' ');
        }
    }
}
