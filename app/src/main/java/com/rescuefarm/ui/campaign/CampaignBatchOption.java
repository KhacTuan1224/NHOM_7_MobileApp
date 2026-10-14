package com.rescuefarm.ui.campaign;

import com.rescuefarm.domain.enums.ProductStatus;
import com.rescuefarm.domain.model.Product;
import com.rescuefarm.domain.model.ProductBatch;
import java.util.Date;

public final class CampaignBatchOption {
    private final Product product;
    private final ProductBatch batch;
    public CampaignBatchOption(Product product, ProductBatch batch) {
        this.product = product; this.batch = batch;
    }
    public Product getProduct() { return product; }
    public ProductBatch getBatch() { return batch; }
    public boolean isSelectable(String campaignId, Date now) {
        String activeCampaign = batch.getActiveCampaignId() == null ? "" : batch.getActiveCampaignId();
        boolean campaignAvailable = activeCampaign.isEmpty() || activeCampaign.equals(campaignId);
        return product.getStatus() == ProductStatus.ACTIVE && batch.isSellable(now) && campaignAvailable;
    }
}
