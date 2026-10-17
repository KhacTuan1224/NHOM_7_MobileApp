package com.rescuefarm.ui.product;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.text.Editable;
import android.text.TextWatcher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.rescuefarm.R;
import com.rescuefarm.ui.common.DatePickerInput;
import com.rescuefarm.domain.enums.PromotionType;
import com.rescuefarm.domain.model.Promotion;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.Map;

public class PromotionEditorFragment extends Fragment {
    private ProductViewModel viewModel;
    private Spinner type;
    private EditText value;
    private EditText tiers;
    private EditText start;
    private EditText end;
    private SwitchMaterial active;
    private String productId;
    private boolean dirty, binding;

    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater,
            @Nullable ViewGroup parent, @Nullable Bundle state) {
        return inflater.inflate(R.layout.fragment_promotion_editor, parent, false);
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        Bundle args = getArguments();
        productId = args == null ? "" : args.getString("productId", "");
        ((TextView) view.findViewById(R.id.promotionProductName)).setText(
                args == null ? "" : args.getString("productName", ""));
        type = view.findViewById(R.id.promotionTypeSpinner);
        value = view.findViewById(R.id.promotionValueInput);
        tiers = view.findViewById(R.id.promotionTiersInput);
        start = view.findViewById(R.id.promotionStartInput);
        end = view.findViewById(R.id.promotionEndInput);
        DatePickerInput.bind(start);
        DatePickerInput.bind(end);
        active = view.findViewById(R.id.promotionActiveSwitch);
        type.setAdapter(new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_dropdown_item, PromotionType.values()));

        viewModel = new ViewModelProvider(this, new ProductViewModelFactory(requireContext()))
                .get(ProductViewModel.class);
        viewModel.getPromotion().observe(getViewLifecycleOwner(), this::render);
        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (!binding) dirty = true;
            }
            @Override public void afterTextChanged(Editable s) { }
        };
        value.addTextChangedListener(watcher); tiers.addTextChangedListener(watcher);
        start.addTextChangedListener(watcher); end.addTextChangedListener(watcher);
        active.setOnCheckedChangeListener((button, checked) -> { if (!binding) dirty = true; });
        boolean[] typeInitialized = {false};
        type.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View selected,
                    int position, long itemId) {
                if (!typeInitialized[0]) typeInitialized[0] = true;
                else if (!binding) dirty = true;
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        });
        viewModel.getState().observe(getViewLifecycleOwner(), screen -> {
            if (screen.getStatus() == ProductScreenState.Status.ERROR) {
                Toast.makeText(requireContext(), screen.getMessage(), Toast.LENGTH_LONG).show();
            } else if (screen.getStatus() == ProductScreenState.Status.SAVED) {
                Toast.makeText(requireContext(), screen.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
        view.findViewById(R.id.savePromotionButton).setOnClickListener(v -> save());
        if (productId.isEmpty()) {
            Toast.makeText(requireContext(), "Thiếu productId.", Toast.LENGTH_LONG).show();
            view.findViewById(R.id.savePromotionButton).setEnabled(false);
        } else {
            viewModel.observePromotion(productId).observe(getViewLifecycleOwner(), this::render);
            viewModel.loadPromotion(productId);
        }
    }

    private void save() {
        viewModel.savePromotion(productId, (PromotionType) type.getSelectedItem(),
                value.getText().toString(), tiers.getText().toString(),
                start.getText().toString(), end.getText().toString(), active.isChecked());
    }

    private void render(Promotion promotion) {
        if (promotion == null || dirty) return;
        binding = true;
        type.setSelection(promotion.getType().ordinal());
        value.setText(formatNumber(promotion.getValue()));
        StringBuilder tierText = new StringBuilder();
        for (Map.Entry<String, Double> entry : promotion.getQuantityDiscountTiers().entrySet()) {
            if (tierText.length() > 0) tierText.append('\n');
            tierText.append(entry.getKey()).append('=').append(formatNumber(entry.getValue()));
        }
        tiers.setText(tierText.toString());
        SimpleDateFormat date = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        start.setText(date.format(promotion.getStartDate()));
        end.setText(date.format(promotion.getEndDate()));
        active.setChecked(promotion.isActive());
        binding = false;
    }

    private static String formatNumber(double number) {
        long whole = (long) number;
        return number == whole ? Long.toString(whole) : Double.toString(number);
    }
}
