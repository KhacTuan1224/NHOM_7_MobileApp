package com.rescuefarm.ui.product;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.text.Editable;
import android.text.TextWatcher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputEditText;
import com.rescuefarm.R;
import com.rescuefarm.ui.common.DatePickerInput;
import com.rescuefarm.domain.model.ProductBatch;
import com.rescuefarm.domain.enums.BatchStatus;
import com.rescuefarm.service.inventory.InventoryAlertEvaluator;
import java.util.ArrayList;
import java.util.Date;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

public class BatchManagerFragment extends Fragment {
    private ProductViewModel viewModel; private String productId, editingId = ""; private long editingVersion;
    private LinearLayout container; private TextInputEditText harvest, expiry, quantity;
    private final InventoryAlertEvaluator alertEvaluator = new InventoryAlertEvaluator();
    private List<ProductBatch> batches = new ArrayList<>();
    private InventoryAlertEvaluator.Filter alertFilter = InventoryAlertEvaluator.Filter.ALL;
    private Button alertFilterButton;
    private boolean dirty, binding;
    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater,
            @Nullable ViewGroup parent, @Nullable Bundle state) {
        return inflater.inflate(R.layout.fragment_batch_manager, parent, false);
    }
    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        Bundle args = getArguments(); productId = args == null ? "" : args.getString("productId", "");
        ((TextView) view.findViewById(R.id.batchProductName)).setText(args == null ? "" : args.getString("productName", ""));
        container = view.findViewById(R.id.batchContainer); harvest = view.findViewById(R.id.harvestDateInput);
        alertFilterButton = view.findViewById(R.id.batchAlertFilterButton);
        alertFilterButton.setOnClickListener(unused -> cycleAlertFilter());
        expiry = view.findViewById(R.id.expiryDateInput); quantity = view.findViewById(R.id.batchQuantityInput);
        DatePickerInput.bind(harvest);
        DatePickerInput.bind(expiry);
        viewModel = new ViewModelProvider(this, new ProductViewModelFactory(requireContext()))
                .get(ProductViewModel.class);
        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (!binding) dirty = true;
            }
            @Override public void afterTextChanged(Editable s) { }
        };
        harvest.addTextChangedListener(watcher); expiry.addTextChangedListener(watcher);
        quantity.addTextChangedListener(watcher);
        viewModel.getBatches(productId).observe(getViewLifecycleOwner(), this::render);
        viewModel.getState().observe(getViewLifecycleOwner(), value -> {
            if (value.getStatus() == ProductScreenState.Status.SAVED) {
                Toast.makeText(requireContext(), value.getMessage(), Toast.LENGTH_SHORT).show();
                clearEditor(); viewModel.refreshBatches(productId);
            } else if (value.getStatus() == ProductScreenState.Status.ERROR)
                Toast.makeText(requireContext(), value.getMessage(), Toast.LENGTH_LONG).show();
        });
        view.findViewById(R.id.saveBatchButton).setOnClickListener(v -> viewModel.saveBatch(editingId,
                productId, text(harvest), text(expiry), text(quantity), editingVersion));
        view.findViewById(R.id.cancelBatchEditButton).setOnClickListener(v -> clearEditor());
        viewModel.refreshBatches(productId);
    }
    private void render(List<ProductBatch> values) {
        batches = values == null ? new ArrayList<>() : new ArrayList<>(values);
        container.removeAllViews(); Date now = new Date();
        for (ProductBatch batch : batches) if (alertEvaluator.matches(batch, alertFilter, now))
            container.addView(card(batch, now));
        if (!dirty && !editingId.isEmpty()) for (ProductBatch batch : batches) {
            if (editingId.equals(batch.getId())) { edit(batch); break; }
        }
    }
    private View card(ProductBatch batch, Date now) {
        MaterialCardView card = new MaterialCardView(requireContext());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2); params.setMargins(0, 0, 0, dp(10)); card.setLayoutParams(params);
        card.setRadius(dp(14)); card.setStrokeWidth(dp(1));
        LinearLayout body = new LinearLayout(requireContext()); body.setOrientation(LinearLayout.VERTICAL); body.setPadding(dp(12), dp(12), dp(12), dp(12));
        TextView info = new TextView(requireContext()); info.setText(getString(R.string.batch_info_format,
                batch.getAvailableQuantity(), batch.getReservedQuantity(), batch.getSoldQuantity(),
                batch.getStatus().name(), batch.getInventoryVersion()));
        TextView alerts = new TextView(requireContext());
        StringBuilder warning = new StringBuilder();
        if (batch.getStatus() == BatchStatus.EXPIRED || batch.isExpired(now)) {
            warning.append(getString(R.string.batch_alert_expired));
        } else {
            if (batch.getStatus() == BatchStatus.SOLD_OUT || batch.getAvailableQuantity() <= 0D)
                warning.append(getString(R.string.batch_alert_sold_out));
            else if (alertEvaluator.isLowStock(batch)) {
                if (warning.length() > 0) warning.append('\n');
                double remainingPercent = 100D * batch.getAvailableQuantity() / batch.getInitialQuantity();
                warning.append(getString(R.string.batch_alert_low_stock, remainingPercent));
            }
            if (alertEvaluator.isNearExpiry(batch, now)) {
                if (warning.length() > 0) warning.append('\n');
                warning.append(getString(R.string.batch_alert_near_expiry,
                        alertEvaluator.daysUntilExpiry(batch, now)));
            }
        }
        alerts.setText(warning.toString());
        alerts.setTextColor(getResources().getColor(warning.length() > 0
                ? R.color.rescue_critical : R.color.rescue_on_surface_variant, requireContext().getTheme()));
        alerts.setVisibility(warning.length() == 0 ? View.GONE : View.VISIBLE);
        LinearLayout actions = new LinearLayout(requireContext());
        Button edit = new Button(requireContext()); edit.setText(R.string.edit_action); edit.setOnClickListener(v -> edit(batch));
        edit.setEnabled(batch.getStatus() != BatchStatus.ARCHIVED);
        Button delete = new Button(requireContext()); delete.setText("Lưu trữ lô");
        delete.setEnabled(batch.getStatus() != BatchStatus.ARCHIVED && batch.getReservedQuantity() == 0D && batch.getSoldQuantity() == 0D
                && (batch.getActiveCampaignId() == null || batch.getActiveCampaignId().isEmpty()));
        delete.setOnClickListener(v -> viewModel.deleteBatch(batch.getId(), batch.getInventoryVersion()));
        actions.addView(edit); actions.addView(delete); body.addView(info); body.addView(alerts);
        body.addView(actions); card.addView(body); return card;
    }
    private void cycleAlertFilter() {
        if (alertFilter == InventoryAlertEvaluator.Filter.ALL) {
            alertFilter = InventoryAlertEvaluator.Filter.LOW_STOCK;
            alertFilterButton.setText(R.string.batch_filter_low_stock);
        } else if (alertFilter == InventoryAlertEvaluator.Filter.LOW_STOCK) {
            alertFilter = InventoryAlertEvaluator.Filter.NEAR_EXPIRY;
            alertFilterButton.setText(R.string.batch_filter_near_expiry);
        } else {
            alertFilter = InventoryAlertEvaluator.Filter.ALL;
            alertFilterButton.setText(R.string.batch_filter_all);
        }
        render(batches);
    }
    private void edit(ProductBatch batch) {
        binding = true; dirty = false;
        editingId = batch.getId(); editingVersion = batch.getInventoryVersion();
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        harvest.setText(batch.getHarvestDate() == null ? "" : format.format(batch.getHarvestDate()));
        expiry.setText(batch.getExpiryDate() == null ? "" : format.format(batch.getExpiryDate()));
        quantity.setText(String.valueOf(batch.getInitialQuantity()));
        binding = false;
    }
    private void clearEditor() {
        binding = true; dirty = false; editingId = ""; editingVersion = 0L;
        harvest.setText(""); expiry.setText(""); quantity.setText(""); binding = false;
    }
    private static String text(TextInputEditText input) { return input.getText() == null ? "" : input.getText().toString(); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
