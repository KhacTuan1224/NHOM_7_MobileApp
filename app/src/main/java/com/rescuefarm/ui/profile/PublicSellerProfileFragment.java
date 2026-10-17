package com.rescuefarm.ui.profile;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.bumptech.glide.Glide;
import androidx.lifecycle.ViewModelProvider;
import com.rescuefarm.R;

/** Public seller card for customers; data comes from the intentionally public mirror collection. */
public class PublicSellerProfileFragment extends Fragment {
    private TextView name, description, address, bank;
    private ImageView avatar;

    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater,
            @Nullable ViewGroup parent, @Nullable Bundle state) {
        return inflater.inflate(R.layout.fragment_public_seller_profile, parent, false);
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        name = view.findViewById(R.id.publicSellerName); description = view.findViewById(R.id.publicSellerDescription);
        address = view.findViewById(R.id.publicSellerAddress); bank = view.findViewById(R.id.publicSellerBank);
        avatar = view.findViewById(R.id.publicSellerAvatar);
        String sellerId = getArguments() == null ? "" : getArguments().getString("sellerId", "");
        if (sellerId.isEmpty()) { name.setText("Không tìm thấy người bán."); return; }
        StorefrontViewModel vm = new ViewModelProvider(this,new StorefrontViewModelFactory(requireContext())).get(StorefrontViewModel.class);
        android.widget.LinearLayout reportActions = new android.widget.LinearLayout(requireContext());
        ((android.view.ViewGroup)bank.getParent()).addView(reportActions);
        vm.observe(sellerId).observe(getViewLifecycleOwner(), profile -> {
                    reportActions.removeAllViews();
                    if (profile == null || !profile.configured) {
                        name.setText("Người bán chưa công khai hồ sơ cửa hàng."); return;
                    }
                    com.rescuefarm.ui.engagement.ContextReportAction.add(this, reportActions, "USER", sellerId, sellerId, "Báo cáo người bán");
                    name.setText(value(profile.shopName, "Cửa hàng RescueFarm"));
                    description.setText(value(profile.description, "Chưa có giới thiệu cửa hàng."));
                    address.setText(value(profile.address, "Chưa cập nhật địa chỉ.")
                        + "\nPhí giao hàng được tính theo khoảng cách GPS khi đặt hàng.");
                    bank.setText("Thông tin chuyển khoản sẽ hiển thị an toàn trong chi tiết đơn hàng.");
                    String url = profile.avatarUrl;
                    if (!blank(url)) Glide.with(this).load(url).circleCrop().into(avatar);
                });
    }

    private String value(String value, String fallback) { return blank(value) ? fallback : value; }
    private boolean blank(String value) { return value == null || value.trim().isEmpty(); }
}
