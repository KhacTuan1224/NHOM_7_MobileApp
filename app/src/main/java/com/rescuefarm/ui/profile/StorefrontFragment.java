package com.rescuefarm.ui.profile;

import android.os.Bundle;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import androidx.annotation.*;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import com.bumptech.glide.Glide;
import com.rescuefarm.R;
import com.rescuefarm.data.repository.*;
import com.rescuefarm.ui.common.VietnamAddressView;
import com.rescuefarm.ui.common.CustomerUi;
import com.rescuefarm.ui.home.*;
import com.rescuefarm.service.media.CloudinaryUploadUi;

public final class StorefrontFragment extends Fragment {
    private LinearLayout root, details, products;
    private EditText qr;
    private TextView storeDetails;
    private TextView message;
    private View upload;
    private String sellerId;
    private boolean editing;
    private StorefrontViewModel vm;
    private final ActivityResultLauncher<String> picker = registerForActivityResult(
        new ActivityResultContracts.GetContent(), uri -> CloudinaryUploadUi.upload(this, uri,
            "paymentQr", sellerId, 0, upload, message, null, url -> qr.setText(url)));
    @Nullable @Override public View onCreateView(@NonNull LayoutInflater i,@Nullable ViewGroup p,@Nullable Bundle s) {
        root=CustomerUi.column(requireContext()); return CustomerUi.screen(this,root);
    }
    @Override public void onViewCreated(@NonNull View view,@Nullable Bundle s) {
        AuthRepository auth=AuthRepositoryFactory.create(requireContext());
        editing=getArguments()!=null&&getArguments().getBoolean("edit",false)&&auth.isAuthenticated();
        sellerId=editing?auth.getCurrentUserId():getArguments()==null?"":getArguments().getString("sellerId","");
        CustomerUi.text(root,editing?"Ảnh QR thanh toán":"Thông tin người bán").setTextSize(24);
        message=CustomerUi.text(root,""); details=CustomerUi.column(requireContext()); root.addView(details);
        vm=new ViewModelProvider(this,new StorefrontViewModelFactory(requireContext())).get(StorefrontViewModel.class);
        if(editing) buildEditor();
        vm.message.observe(getViewLifecycleOwner(),message::setText);
        vm.storefront.observe(getViewLifecycleOwner(),this::render);
        CustomerUi.button(root,"Tải lại",v->vm.load(sellerId));
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
        storeDetails = CustomerUi.text(details, "Đang tải thông tin cửa hàng…");
        CustomerUi.text(details, "Địa chỉ, GPS và tài khoản ngân hàng được quản lý tại Hồ sơ người bán.");
        CustomerUi.button(details, "Mở Hồ sơ người bán", v ->
                Navigation.findNavController(requireView()).navigate(R.id.sellerProfileFragment));
        qr=CustomerUi.input(details,"Đường dẫn ảnh QR (HTTPS)",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);
        upload=CustomerUi.button(details,"Tải ảnh QR từ thiết bị",v->picker.launch("image/*"));
        View save=CustomerUi.button(details,"Lưu ảnh QR",v->vm.savePaymentQr(sellerId,CustomerUi.value(qr)));
        vm.loading.observe(getViewLifecycleOwner(),loading->save.setEnabled(!Boolean.TRUE.equals(loading)));
    }
    private void render(SellerStorefront value) {
        if(editing) {
            storeDetails.setText(value.shopName + "\n" + value.address);
            qr.setText(value.qrUrl);
        } else {
            details.removeAllViews();
            if(!value.configured){CustomerUi.text(details,"Người bán chưa cập nhật trang cửa hàng.");return;}
            CustomerUi.text(details,value.shopName).setTextSize(22);
            CustomerUi.text(details,value.description); CustomerUi.text(details,value.address);
            CustomerUi.text(details,"Phí giao hàng được tính theo khoảng cách GPS khi đặt giao hàng.");
        }
    }
}
