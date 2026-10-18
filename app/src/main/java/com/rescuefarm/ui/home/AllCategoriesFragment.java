package com.rescuefarm.ui.home;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import com.rescuefarm.R;
import com.rescuefarm.domain.model.Category;
import com.rescuefarm.ui.common.ResponsiveGridLayout;
import java.util.List;

public class AllCategoriesFragment extends Fragment {
    private final com.rescuefarm.ui.common.ListPagination pagination = new com.rescuefarm.ui.common.ListPagination();
    private HomeViewModel viewModel;
    private HomeCardRenderer renderer;
    private ResponsiveGridLayout categoryGrid;
    private TextView messageView;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_all_categories, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        renderer = new HomeCardRenderer(this);
        categoryGrid = view.findViewById(R.id.categoryGrid);
        messageView = view.findViewById(R.id.messageView);

        viewModel = new ViewModelProvider(requireActivity(), new HomeViewModelFactory(requireContext()))
                .get(HomeViewModel.class);
        viewModel.ensureLoaded();
        
        viewModel.getHomeState().observe(getViewLifecycleOwner(), state -> 
                renderCategories(state.getCategories()));
    }

    private void renderCategories(List<Category> categories) {
        pagination.render(categoryGrid, categories, this::renderCategoriesPage);
    }
    private void renderCategoriesPage(List<Category> categories) {
        categoryGrid.removeAllViews();
        
        if (categories == null || categories.isEmpty()) {
            messageView.setVisibility(View.VISIBLE);
            messageView.setText("Chưa có danh mục nào.");
            categoryGrid.setVisibility(View.GONE);
            return;
        }
        
        messageView.setVisibility(View.GONE);
        categoryGrid.setVisibility(View.VISIBLE);
        
        for (Category category : categories) {
            View tile = renderer.category(category, v -> {
                Bundle args = new Bundle();
                args.putString("categoryId", category.getId());
                DiscoveryBottomSheet bottomSheet = new DiscoveryBottomSheet();
                bottomSheet.setArguments(args);
                bottomSheet.show(getChildFragmentManager(), "DiscoveryBottomSheet");
            });
            
            categoryGrid.addView(tile, new LinearLayout.LayoutParams(-1, -2));
        }
    }

}
