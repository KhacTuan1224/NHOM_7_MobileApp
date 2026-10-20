package com.rescuefarm.ui.rescue;

import android.os.Bundle;
import androidx.annotation.Nullable;
import com.rescuefarm.R;

/** Seller publishes one stocked pickup round under a stable subscription key. */
public final class SubscriptionOfferEditorFragment extends BaseRescueOfferEditorFragment {
    @Override protected int editorLayout() { return R.layout.fragment_subscription_offer_editor; }
    @Override protected String offerType() { return "SUBSCRIPTION"; }
    @Override protected String createLabel() { return "Đăng đợt nhận của gói định kỳ"; }
    @Override protected void buildFields(@Nullable Bundle state) {
        section(R.id.subscriptionPlan);
        add("title", "Tên gói định kỳ", false, "", state);
        add("planKey", "Mã gói cố định (dùng lại cho các đợt sau)", false, "", state);
        add("categoryLabel", "Nhóm nông sản của gói", false, "", state);
        add("description", "Giới thiệu gói và đợt nhận này", false, "", state);
        section(R.id.subscriptionSchedule);
        long now = System.currentTimeMillis();
        addDateTime("closesAt", "Hạn xác nhận suất kỳ này", now + 3600000, state);
        addDateTime("pickupStartAt", "Bắt đầu đợt nhận", now + 3600000, state);
        addDateTime("pickupEndAt", "Kết thúc đợt nhận", now + 7200000, state);
        addPickup(state);
        section(R.id.subscriptionHarvest);
        add("contentsDisclosure", "Thành phần dự kiến và lưu ý kỳ này", false, "", state);
        addStockPicker();
        section(R.id.subscriptionAllocation);
        add("unitLabel", "Quy cách một suất của kỳ này", false, "suất", state);
        add("totalUnits", "Số suất cung cấp trong đợt nhận", true, "10", state);
        add("maxUnitsPerClaim", "Giới hạn suất mỗi khách trong đợt", true, "1", state);
        add("originalUnitPrice", "Giá trị gốc một suất kỳ này (VND)", true, "100000", state);
        add("unitPrice", "Giá nhận một suất kỳ này (VND)", true, "20000", state);
    }
}
