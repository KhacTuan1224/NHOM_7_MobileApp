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
import com.rescuefarm.domain.model.Product;
import com.rescuefarm.domain.model.RescueCampaign;
import com.rescuefarm.ui.common.ListPagination;
import com.rescuefarm.ui.feed.CampaignFeedCardRenderer;
import java.util.ArrayList;
import java.util.List;

/** A category's public catalog and campaign stories, separate from discovery filters. */
public class CategoryDetailFragment extends Fragment {
    private final ListPagination productPages = new ListPagination();
    private final ListPagination articlePages = new ListPagination();
    private HomeViewModel viewModel;
    private HomeCardRenderer productsRenderer;
    private CampaignFeedCardRenderer articlesRenderer;
    private LinearLayout productsContainer, articlesContainer;
    private String categoryId;

    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater,
            @Nullable ViewGroup container, @Nullable Bundle state) {
        return inflater.inflate(R.layout.fragment_category_detail, container, false);
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        super.onViewCreated(view, state);
        categoryId = requireArguments().getString("categoryId", "");
        ((TextView) view.findViewById(R.id.categoryDetailName)).setText(
                requireArguments().getString("categoryName", getString(R.string.category_detail_title)));
        productsContainer = view.findViewById(R.id.categoryDetailProducts);
        articlesContainer = view.findViewById(R.id.categoryDetailArticles);
        productsRenderer = new HomeCardRenderer(this);
        articlesRenderer = new CampaignFeedCardRenderer(this);
        // Own the ViewModel so browsing a category never changes Home's saved search or filters.
        viewModel = new ViewModelProvider(this, new HomeViewModelFactory(requireContext()))
                .get(HomeViewModel.class);
        view.findViewById(R.id.categoryBackButton).setOnClickListener(v ->
                Navigation.findNavController(v).navigateUp());
        view.findViewById(R.id.categoryRefreshButton).setOnClickListener(v -> {
            productPages.reset();
            articlePages.reset();
            viewModel.refresh();
        });
        viewModel.getHomeState().observe(getViewLifecycleOwner(), this::render);
        viewModel.ensureLoaded();
    }

    private void render(HomeViewState state) {
        View view = requireView();
        for (Category category : state.getCategories()) {
            if (categoryId.equals(category.getId())) {
                ((TextView) view.findViewById(R.id.categoryDetailName)).setText(category.getName());
                break;
            }
        }
        List<Product> products = new ArrayList<>();
        List<RescueCampaign> articles = new ArrayList<>();
        for (DiscoveryResult result : viewModel.getCategoryContent(categoryId)) {
            if (result.getType() == DiscoveryResult.Type.PRODUCT) products.add(result.getProduct());
            else articles.add(result.getCampaign());
        }
        ((TextView) view.findViewById(R.id.categoryDetailSummary)).setText(
                getString(R.string.category_detail_summary, products.size(), articles.size()));
        view.findViewById(R.id.categoryDetailProgress).setVisibility(state.isRefreshing() ? View.VISIBLE : View.GONE);
        view.findViewById(R.id.categoryRefreshButton).setEnabled(!state.isRefreshing());
        TextView status = view.findViewById(R.id.categoryDetailStatus);
        status.setVisibility(state.isRefreshing() || state.getDataFreshness() != HomeViewState.DataFreshness.FRESH
                ? View.VISIBLE : View.GONE);
        status.setText(state.isRefreshing() ? getString(R.string.category_detail_loading) : state.getMessage());

        productPages.remote(viewModel::hasMoreProducts, viewModel::isPageLoading,
                () -> viewModel.loadNextPage(true, false)).render(productsContainer, products, page -> {
            productsContainer.removeAllViews();
            if (page.isEmpty()) productsContainer.addView(productsRenderer.message(getString(
                    state.isRefreshing() ? R.string.category_detail_loading
                            : viewModel.hasMoreProducts() ? R.string.category_detail_more_products
                            : R.string.category_detail_no_products)));
            for (Product product : page) productsContainer.addView(productsRenderer.product(product, v -> {
                Bundle args = new Bundle(); args.putString("productId", product.getId());
                Navigation.findNavController(v).navigate(
                        R.id.action_categoryDetailFragment_to_productDetailFragment, args);
            }), new LinearLayout.LayoutParams(-1, -2));
        });
        // Articles are linked through product batches; both paged sources can reveal more matches.
        articlePages.remote(() -> viewModel.hasMoreProducts() || viewModel.hasMoreCampaigns(),
                viewModel::isPageLoading, () -> viewModel.loadNextPage(true, true))
                .render(articlesContainer, articles, page -> {
                    articlesContainer.removeAllViews();
                    if (page.isEmpty()) articlesContainer.addView(productsRenderer.message(getString(
                            state.isRefreshing() ? R.string.category_detail_loading
                                    : viewModel.hasMoreProducts() || viewModel.hasMoreCampaigns()
                                    ? R.string.category_detail_more_articles : R.string.category_detail_no_articles)));
                    for (RescueCampaign campaign : page) articlesContainer.addView(articlesRenderer.card(
                            campaign, viewModel.getCategoryCampaignProducts(campaign, products), v -> {
                                Bundle args = new Bundle(); args.putString("campaignId", campaign.getId());
                                Navigation.findNavController(v).navigate(
                                        R.id.action_categoryDetailFragment_to_campaignDetailFragment, args);
                            }));
                });
    }

    @Override public void onDestroyView() {
        productsContainer = null;
        articlesContainer = null;
        productsRenderer = null;
        articlesRenderer = null;
        super.onDestroyView();
    }
}
