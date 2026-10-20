package com.rescuefarm.ui.rescue;

import android.os.Bundle;
import androidx.annotation.Nullable;
import com.rescuefarm.R;

/** Builds the box-specific form; no method switch or unrelated plan fields. */
public final class MysteryBoxEditorFragment extends BaseRescueOfferEditorFragment {
    @Override protected int editorLayout() { return R.layout.fragment_mystery_box_editor; }
    @Override protected String offerType() { return "MYSTERY_BOX"; }
    @Override protected String createLabel() { return "Tạo hộp bí ẩn và giữ nông sản"; }
    @Override protected void buildFields(@Nullable Bundle state) {
        section(R.id.mysteryIdentity);
        add("title", "Tên hộp bí ẩn", false, "", state);
        add("categoryLabel", "Loại hộp (rau củ / trái cây…)", false, "", state);
        add("description", "Câu chuyện và chất lượng của hộp", false, "", state);
        section(R.id.mysteryContents);
        add("contentsDisclosure", "Thành phần có thể có / lưu ý dị ứng", false, "", state);
        addStockPicker();
        section(R.id.mysteryPricing);
        add("unitLabel", "Cách đóng gói mỗi suất", false, "hộp", state);
        add("originalUnitPrice", "Tổng giá trị gốc trong một hộp (VND)", true, "100000", state);
        add("unitPrice", "Giá giải cứu một hộp (VND)", true, "20000", state);
        add("totalUnits", "Số hộp có thể chuẩn bị", true, "10", state);
        add("maxUnitsPerClaim", "Số hộp tối đa mỗi khách", true, "1", state);
        section(R.id.mysteryPickup);
        long now = System.currentTimeMillis();
        addDateTime("closesAt", "Ngừng nhận đặt hộp", now + 3600000, state);
        addDateTime("pickupStartAt", "Khách bắt đầu nhận hộp", now + 3600000, state);
        addDateTime("pickupEndAt", "Nhận hộp muộn nhất", now + 7200000, state);
        addPickup(state);
    }
}
