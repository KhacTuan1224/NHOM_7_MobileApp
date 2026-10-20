package com.rescuefarm.ui.rescue;

import android.os.Bundle;
import android.widget.LinearLayout;
import androidx.annotation.Nullable;
import com.google.android.material.button.MaterialButton;
import com.rescuefarm.R;
import static com.rescuefarm.ui.rescue.RescueUi.*;

/** Time-first editor for an immediately available last-minute pickup offer. */
public final class FlashRescueEditorFragment extends BaseRescueOfferEditorFragment {
    @Override protected int editorLayout() { return R.layout.fragment_flash_rescue_editor; }
    @Override protected String offerType() { return "FLASH"; }
    @Override protected String createLabel() { return "Mở đợt giải cứu giờ chót"; }
    @Override protected void buildFields(@Nullable Bundle state) {
        section(R.id.flashWindow);
        long now = System.currentTimeMillis();
        addDateTime("closesAt", "Đóng nhận đặt sau", now + 30 * 60000, state);
        addDateTime("pickupStartAt", "Có thể nhận từ", now + 5 * 60000, state);
        addDateTime("pickupEndAt", "Cần nhận xong trước", now + 60 * 60000, state);
        text(form, "Thiết lập nhanh khung bán và nhận", false);
        com.rescuefarm.ui.common.ResponsiveActionLayout durations = new com.rescuefarm.ui.common.ResponsiveActionLayout(requireContext());
        form.addView(durations, new LinearLayout.LayoutParams(-1, -2));
        for (int minutes : new int[]{15, 30, 60}) {
            MaterialButton option = new MaterialButton(requireContext(), null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
            option.setText(minutes + " phút"); option.setAllCaps(false);
            durations.addView(option, new LinearLayout.LayoutParams(0, -2, 1));
            option.setOnClickListener(v -> {
                long start = System.currentTimeMillis();
                setDateTime("closesAt", start + minutes * 60000L);
                setDateTime("pickupStartAt", start + 60000L);
                setDateTime("pickupEndAt", start + (minutes + 30) * 60000L);
            });
        }
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
