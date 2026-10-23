package com.rescuefarm.ui.rescue;

import android.os.Bundle;
import androidx.annotation.Nullable;
import com.rescuefarm.R;
import static com.rescuefarm.ui.rescue.RescueUi.*;

/** Time-first editor for an immediately available last-minute pickup offer. */
public final class FlashRescueEditorFragment extends BaseRescueOfferEditorFragment {
    @Override protected int editorLayout() { return R.layout.fragment_flash_rescue_editor; }
    @Override protected String offerType() { return "FLASH"; }
    @Override protected String createLabel() { return "Mở đợt giải cứu giờ chót"; }
    @Override protected void buildFields(@Nullable Bundle state) {
        section(R.id.flashWindow);
        text(form, "Giờ Việt Nam (UTC+7). Hạn đóng nhận đặt tối đa 2 giờ tính từ lúc tạo gói.", false);
        long now = System.currentTimeMillis();
        addDateTime("closesAt", "Đóng nhận đặt lúc", now + 30 * 60000, state);
        addDateTime("pickupStartAt", "Có thể nhận từ", now + 5 * 60000, state);
        addDateTime("pickupEndAt", "Cần nhận xong trước", now + 60 * 60000, state);
        addQuickPickupTimes();
        section(R.id.flashLocation);
        addPickup(state);
        section(R.id.flashProduce);
        add("title", "Tên đợt giải cứu giờ chót", false, "", state);
        add("categoryLabel", "Nông sản đang cần bán nhanh", false, "", state);
        add("description", "Tình trạng hàng và lý do cần giải cứu", false, "", state);
        add("contentsDisclosure", "Thành phần / lưu ý bảo quản, sử dụng", false, "", state);
        addStockPicker();
        section(R.id.flashDeal);
        add("unitLabel", "Quy cách một phần nhận nhanh", false, "túi", state);
        add("unitPrice", "Giá giờ chót mỗi phần (VND)", true, "20000", state);
        add("originalUnitPrice", "Giá trị gốc mỗi phần (VND)", true, "100000", state);
        add("totalUnits", "Số phần còn cần giải cứu", true, "10", state);
        add("maxUnitsPerClaim", "Số phần tối đa mỗi khách", true, "1", state);
    }
}
