package com.rescuefarm.ui.profile;

import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.annotation.*;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import com.bumptech.glide.Glide;
import com.rescuefarm.R;
import com.rescuefarm.data.repository.*;
import com.rescuefarm.ui.common.CustomerUi;
import com.rescuefarm.ui.home.*;

public final class StorefrontFragment extends Fragment {
    private LinearLayout root, details, products;
    private ImageView qrImage;
    private TextView storeDetails;
    private TextView message;
    private View generateButton, reloadButton;
    private String sellerId;
    private boolean editing;
    private StorefrontViewModel vm;
    @Nullable @Override public View onCreateView(@NonNull LayoutInflater i,@Nullable ViewGroup p,@Nullable Bundle s) {
        root=CustomerUi.column(requireContext()); return CustomerUi.screen(this,root);
    }
    @Override public void onViewCreated(@NonNull View view,@Nullable Bundle s) {
        AuthRepository auth=AuthRepositoryFactory.create(requireContext());
        editing=getArguments()!=null&&getArguments().getBoolean("edit",false)&&auth.isAuthenticated();
        sellerId=editing?auth.getCurrentUserId():getArguments()==null?"":getArguments().getString("sellerId","");
        CustomerUi.text(root,editing?"Mã QR nhận chuyển khoản":"Thông tin người bán").setTextSize(24);
        message=CustomerUi.text(root,""); details=CustomerUi.column(requireContext()); root.addView(details);
        vm=new ViewModelProvider(this,new StorefrontViewModelFactory(requireContext())).get(StorefrontViewModel.class);
        if(editing) buildEditor();
        vm.message.observe(getViewLifecycleOwner(),message::setText);
        vm.storefront.observe(getViewLifecycleOwner(),this::render);
        reloadButton = CustomerUi.button(root,"Tải lại",v->vm.load(sellerId));
        if(!editing) {
            CustomerUi.text(root,"Sản phẩm và đánh giá").setTextSize(20);
            products=CustomerUi.column(requireContext()); root.addView(products);
            HomeViewModel home=new ViewModelProvider(this,new HomeViewModelFactory(requireContext())).get(HomeViewModel.class);
            home.getHomeState().observe(getViewLifecycleOwner(),state->{
                products.removeAllViews();
                for(com.rescuefarm.domain.model.Product product:home.getProductsSnapshot()) {
                    if(!sellerId.equals(product.getSellerId()))continue;
                    CustomerUi.button(products,product.getName()+" • "+product.getAverageRating()+" ★ ("+product.getReviewCount()+" đánh giá)",v->{
                        Bundle b=new Bundle(); b.putString("productId",product.getId());
                        Navigation.findNavController(view).navigate(R.id.productDetailFragment,b);
                    });
                }
                if(products.getChildCount()==0)CustomerUi.text(products,"Chưa có sản phẩm đang bán.");
            });
            home.refresh();
        }
        vm.load(sellerId);
    }
    private void buildEditor() {
        storeDetails = CustomerUi.text(details, "Đang tải tài khoản ngân hàng…");
        CustomerUi.button(details, "Sửa thông tin ngân hàng", v ->
                Navigation.findNavController(requireView()).navigate(R.id.sellerProfileFragment));
        CustomerUi.text(details, "Mã QR được tạo từ tài khoản đã lưu trong Hồ sơ người bán và tự lưu lên Cloudinary.");
        qrImage = new ImageView(requireContext());
        qrImage.setScaleType(ImageView.ScaleType.FIT_CENTER);
        qrImage.setContentDescription("Mã QR nhận chuyển khoản của cửa hàng");
        qrImage.setBackgroundColor(android.graphics.Color.WHITE);
        qrImage.setVisibility(View.GONE);
        details.addView(qrImage, new LinearLayout.LayoutParams(-1, CustomerUi.dp(requireContext(), 280)));
        generateButton = CustomerUi.button(details, "Tạo mã QR", v -> vm.generatePaymentQr());
        CustomerUi.text(details, "QR cửa hàng dùng để nhận tiền. Khi thanh toán đơn hàng, QR riêng sẽ kèm đúng số tiền và mã đơn.");
        vm.loading.observe(getViewLifecycleOwner(), loading -> {
            boolean busy = Boolean.TRUE.equals(loading);
            generateButton.setEnabled(!busy);
            ((TextView)generateButton).setText(busy ? "Đang xử lý…" : "Tạo mã QR");
            if (reloadButton != null) reloadButton.setEnabled(!busy);
        });
    }
    private void render(SellerStorefront value) {
        if(editing) {
            storeDetails.setText(value.shopName + "\n" + value.address + "\n\nNgân hàng: " + value.bankName
                    + "\nSố tài khoản: " + value.bankAccount + "\nChủ tài khoản: " + value.accountName);
            qrImage.setVisibility(value.qrUrl.isEmpty() ? View.GONE : View.VISIBLE);
            if (!value.qrUrl.isEmpty()) Glide.with(this).load(value.qrUrl).into(qrImage);
            else Glide.with(this).clear(qrImage);
        } else {
            details.removeAllViews();
            if(!value.configured){CustomerUi.text(details,"Người bán chưa cập nhật trang cửa hàng.");return;}
            CustomerUi.text(details,value.shopName).setTextSize(22);
            CustomerUi.text(details,value.description); CustomerUi.text(details,value.address);
            CustomerUi.text(details,"Phí giao hàng được tính theo khoảng cách GPS khi đặt giao hàng.");
        }
    }
}
