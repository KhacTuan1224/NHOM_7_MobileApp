package com.rescuefarm.service;

import static org.junit.Assert.assertEquals;

import com.rescuefarm.domain.model.ProductBatch;
import com.rescuefarm.domain.model.RescueCampaign;
import com.rescuefarm.service.inventory.InventoryService;

import org.junit.Test;

public class InventoryServiceTest {
    private static final double DELTA = 0.000001;

    @Test
    public void deliveryFlow_reservesThenCommitsBatchAndCampaignTogether() {
        ProductBatch batch = new ProductBatch("batch-1", "product-1", 50.0);
        batch.updateDetails(new java.util.Date(1), new java.util.Date(Long.MAX_VALUE), 50, new java.util.Date());
        RescueCampaign campaign = new RescueCampaign("campaign-1", "seller-1", 40.0);
        InventoryService service = new InventoryService();

        service.reserveStock(batch, campaign, 10.0);
        service.commitReservedStock(batch, campaign, 10.0);

        assertEquals(40.0, batch.getAvailableQuantity(), DELTA);
        assertEquals(10.0, batch.getSoldQuantity(), DELTA);
        assertEquals(0.0, campaign.getReservedQuantity(), DELTA);
        assertEquals(10.0, campaign.getRescuedQuantity(), DELTA);
    }
}
