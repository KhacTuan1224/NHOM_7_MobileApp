package com.rescuefarm.ui.home;
import com.rescuefarm.domain.model.Product;
/** Presentation snapshot of an achievable, currently active quantity tier. */
public final class VolumeOffer {
    public final Product product;
    public final String batchId, campaignId;
    public final double threshold, percent, unitPrice;
    public VolumeOffer(Product product,String batchId,String campaignId,double threshold,double percent,double unitPrice) {
        this.product=product;this.batchId=batchId;this.campaignId=campaignId==null?"":campaignId;
        this.threshold=threshold;this.percent=percent;this.unitPrice=unitPrice;
    }
}
