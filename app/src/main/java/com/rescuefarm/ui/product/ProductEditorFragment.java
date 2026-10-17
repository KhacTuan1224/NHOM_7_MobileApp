package com.rescuefarm.ui.product;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import android.text.Editable;
import android.text.TextWatcher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import com.google.android.material.textfield.TextInputEditText;
import com.rescuefarm.R;
import com.rescuefarm.domain.enums.ProductStatus;
import com.rescuefarm.domain.model.Category;
import com.rescuefarm.service.media.CloudinaryUploadUi;
import java.util.List;

public class ProductEditorFragment extends Fragment {
    private ProductViewModel viewModel; private String id = "";
    private com.google.android.material.textfield.MaterialAutoCompleteTextView category;
    private TextInputEditText name, description, originalPrice, rescuePrice, unit, origin, province, images;
    private RadioGroup statusGroup;
    private boolean dirty, binding, creatingNewProduct;
    private TextView imagesStatus;
    private View uploadImagesButton;
    private final List<Category> activeCategories = new java.util.ArrayList<>();
    private String selectedCategoryId = "";
    private final ActivityResultLauncher<String> imagePicker = registerForActivityResult(
            new ActivityResultContracts.GetContent(), uri -> CloudinaryUploadUi.upload(this, uri,
                    "product", id, imageCount(), uploadImagesButton, imagesStatus, null,
                    url -> appendImageUrl(url)));
    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater,
            @Nullable ViewGroup parent, @Nullable Bundle state) {
        return inflater.inflate(R.layout.fragment_product_editor, parent, false);
    }
    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        category = view.findViewById(R.id.productCategoryInput); name = view.findViewById(R.id.productNameInput);
        description = view.findViewById(R.id.productDescriptionInput); originalPrice = view.findViewById(R.id.originalPriceInput);
        rescuePrice = view.findViewById(R.id.rescuePriceInput); unit = view.findViewById(R.id.productUnitInput);
        origin = view.findViewById(R.id.productOriginInput); province = view.findViewById(R.id.productProvinceInput);
        images = view.findViewById(R.id.productImagesInput); statusGroup = view.findViewById(R.id.productStatusGroup);
        imagesStatus = view.findViewById(R.id.productImagesUploadStatus);
        uploadImagesButton = view.findViewById(R.id.uploadProductImageButton);
        uploadImagesButton.setOnClickListener(v -> imagePicker.launch("image/*"));
        view.findViewById(R.id.clearProductImagesButton).setOnClickListener(v -> {
            images.setText(""); updateImageCount(); dirty = true;
        });
        category.setOnItemClickListener((parent, itemView, position, itemId) -> {
            if (position < 0 || position >= activeCategories.size()) return;
            selectedCategoryId = activeCategories.get(position).getId();
            dirty = true;
        });
        readArguments();
        boolean editingExisting = !id.isEmpty();
        creatingNewProduct = !editingExisting;
        if (id.isEmpty()) id = java.util.UUID.randomUUID().toString();
        viewModel = new ViewModelProvider(this, new ProductViewModelFactory(requireContext()))
                .get(ProductViewModel.class);
        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (!binding) dirty = true;
            }
            @Override public void afterTextChanged(Editable s) { }
        };
        for (TextInputEditText input : new TextInputEditText[]{name, description,
                originalPrice, rescuePrice, unit, origin, province, images}) input.addTextChangedListener(watcher);
        statusGroup.setOnCheckedChangeListener((group, checkedId) -> { if (!binding) dirty = true; });
        if (editingExisting) viewModel.observeProduct(id).observe(getViewLifecycleOwner(), value -> {
            if (value != null && !dirty) populate(value);
        });
        viewModel.getCategories().observe(getViewLifecycleOwner(), values -> showCategories(view, values));
        viewModel.getState().observe(getViewLifecycleOwner(), value -> {
            if (value.getStatus() == ProductScreenState.Status.SAVED) {
                Toast.makeText(requireContext(), value.getMessage(), Toast.LENGTH_SHORT).show();
                NavHostFragment.findNavController(this).popBackStack();
            } else if (value.getStatus() == ProductScreenState.Status.ERROR)
                Toast.makeText(requireContext(), value.getMessage(), Toast.LENGTH_LONG).show();
        });
        view.findViewById(R.id.saveProductButton).setOnClickListener(v -> viewModel.saveProduct(id,
                selectedCategoryId, text(name), text(description), text(originalPrice), text(rescuePrice),
                text(unit), text(origin), text(province), text(images), selectedStatus(), creatingNewProduct));
        viewModel.refreshCatalog();
    }
    private void populate(com.rescuefarm.domain.model.Product value) {
        binding = true;
        selectedCategoryId = value.getCategoryId();
        displaySelectedCategory(); name.setText(value.getName());
        description.setText(value.getDescription());
        originalPrice.setText(String.valueOf(value.getOriginalPrice()));
        rescuePrice.setText(String.valueOf(value.getRescuePrice())); unit.setText(value.getUnit());
        origin.setText(value.getOrigin()); province.setText(value.getProvince());
        images.setText(String.join("\n", value.getImageUrls()));
        updateImageCount();
        statusGroup.check(value.getStatus() == ProductStatus.INACTIVE || value.getStatus() == ProductStatus.HIDDEN
                ? R.id.productStatusInactive : R.id.productStatusActive);
        binding = false;
    }
    private void readArguments() {
        Bundle b = getArguments(); if (b == null) return; id = safe(b.getString("id"));
        selectedCategoryId = safe(b.getString("categoryId")); name.setText(b.getString("name"));
        description.setText(b.getString("description"));
        if (b.containsKey("originalPrice")) originalPrice.setText(String.valueOf(b.getDouble("originalPrice")));
        if (b.containsKey("rescuePrice")) rescuePrice.setText(String.valueOf(b.getDouble("rescuePrice")));
        unit.setText(b.getString("unit")); origin.setText(b.getString("origin")); province.setText(b.getString("province"));
        images.setText(b.getString("imageUrls"));
        updateImageCount();
        if (ProductStatus.INACTIVE.name().equals(b.getString("status"))) statusGroup.check(R.id.productStatusInactive);
    }
    private void showCategories(View view, List<Category> values) {
        activeCategories.clear();
        if (values != null) activeCategories.addAll(values);
        List<String> labels = new java.util.ArrayList<>();
        for (Category value : activeCategories) labels.add(value.getName() + " · " + value.getId());
        category.setAdapter(new android.widget.ArrayAdapter<>(requireContext(),
                android.R.layout.simple_list_item_1, labels));
        category.setEnabled(!activeCategories.isEmpty());
        TextView categoryHint = view.findViewById(R.id.availableCategoriesText);
        categoryHint.setText(activeCategories.isEmpty() ? R.string.no_active_categories
                : R.string.category_selection_hint);
        displaySelectedCategory();
    }
    private void displaySelectedCategory() {
        if (category == null) return;
        if (activeCategories.isEmpty()) {
            category.setText("", false);
            return;
        }
        for (Category value : activeCategories) {
            if (value.getId().equals(selectedCategoryId)) {
                category.setText(value.getName() + " · " + value.getId(), false);
                return;
            }
        }
        selectedCategoryId = "";
        category.setText("", false);
    }
    private ProductStatus selectedStatus() {
        return statusGroup.getCheckedRadioButtonId() == R.id.productStatusInactive
                ? ProductStatus.INACTIVE : ProductStatus.ACTIVE;
    }
    private static String text(TextInputEditText input) { return input.getText() == null ? "" : input.getText().toString(); }
    private static String safe(String value) { return value == null ? "" : value; }
    private void appendImageUrl(String url) {
        String current = text(images).trim();
        images.setText(current.isEmpty() ? url : current + "\n" + url);
        updateImageCount();
        dirty = true;
    }
    private int imageCount() {
        int count = 0;
        for (String item : text(images).split("[\\n,]+")) if (!item.trim().isEmpty()) count++;
        return count;
    }
    private void updateImageCount() {
        int count = imageCount();
        if (count == 0) imagesStatus.setText(R.string.no_images_selected);
        else imagesStatus.setText(getString(R.string.uploaded_images_count_format, count));
    }
}
