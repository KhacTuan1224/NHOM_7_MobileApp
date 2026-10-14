package com.rescuefarm.ui.home;

import com.rescuefarm.domain.model.Product;
import com.rescuefarm.domain.model.ProductBatch;

/** Home projection of an actual sellable batch; not an additional business domain class. */
public final class ExpiringProduct {
    private final Product product;
    private final ProductBatch batch;
    public ExpiringProduct(Product product, ProductBatch batch) { this.product = product; this.batch = batch; }
    public Product getProduct() { return product; }
    public ProductBatch getBatch() { return batch; }
}
