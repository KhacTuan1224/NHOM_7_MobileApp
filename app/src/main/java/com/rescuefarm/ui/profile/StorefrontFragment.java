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
    private EditText name, description, bank, account, accountName, qr, latitude, longitude;
    private VietnamAddressView address;
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
        CustomerUi.text(root,editing?"Cửa hàng và chuyển khoản":"Thông tin người bán").setTextSize(24);
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
        name=CustomerUi.input(details,"Tên cửa hàng",InputType.TYPE_CLASS_TEXT);
        description=CustomerUi.input(details,"Giới thiệu",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        CustomerUi.text(details,"Địa chỉ nhận hàng tại cửa hàng");
        address=new VietnamAddressView(requireContext()); address.setId(R.id.storefrontAddressForm); details.addView(address);
        latitude=CustomerUi.input(details,"Vĩ độ cửa hàng",InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL|InputType.TYPE_NUMBER_FLAG_SIGNED);
        longitude=CustomerUi.input(details,"Kinh độ cửa hàng",InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL|InputType.TYPE_NUMBER_FLAG_SIGNED);
        address.setOnAddressChangedListener(() -> { latitude.setText(""); longitude.setText(""); });
        CustomerUi.text(details,"Phí giao hàng được tính theo khoảng cách GPS từ vị trí cửa hàng đến địa chỉ nhận. Hãy cập nhật tọa độ cửa hàng trong hồ sơ Seller. Pickup và mua tại chỗ miễn phí.");
        CustomerUi.text(details,"Để trống toàn bộ thông tin ngân hàng nếu chưa nhận chuyển khoản.");
        bank=CustomerUi.input(details,"Mã ngân hàng (ví dụ: VCB, MB)",InputType.TYPE_CLASS_TEXT);
        account=CustomerUi.input(details,"Số tài khoản",InputType.TYPE_CLASS_TEXT);
        accountName=CustomerUi.input(details,"Tên chủ tài khoản",InputType.TYPE_CLASS_TEXT);
        qr=CustomerUi.input(details,"Đường dẫn ảnh QR (HTTPS)",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);
        upload=CustomerUi.button(details,"Tải ảnh QR từ thiết bị",v->picker.launch("image/*"));
        View save=CustomerUi.button(details,"Lưu thông tin công khai",v->{
            if (!address.validate()) return;
            try {
                SellerStorefront value=new SellerStorefront(); value.sellerId=sellerId;
                SellerStorefront previous=vm.storefront.getValue();
                if(previous!=null){value.latitude=previous.latitude;value.longitude=previous.longitude;value.avatarUrl=previous.avatarUrl;value.shippingFee=previous.shippingFee;}
                value.shopName=CustomerUi.value(name); value.description=CustomerUi.value(description);
                value.address=address.formattedAddress();
                value.latitude=coordinate(latitude,90); value.longitude=coordinate(longitude,180);
                if(value.latitude==0D&&value.longitude==0D)throw new IllegalArgumentException("Cần tọa độ cửa hàng để tính phí giao hàng.");
                value.bankName=CustomerUi.value(bank); value.bankAccount=CustomerUi.value(account);
                value.accountName=CustomerUi.value(accountName); value.qrUrl=CustomerUi.value(qr); vm.save(value);
            }catch(IllegalArgumentException e){message.setText(e.getMessage());}
        });
        vm.loading.observe(getViewLifecycleOwner(),loading->save.setEnabled(!Boolean.TRUE.equals(loading)));
    }
    private void render(SellerStorefront value) {
        if(editing) {
            name.setText(value.shopName); description.setText(value.description); address.setFormattedAddress(value.address);
            latitude.setText(value.latitude==0D?"":String.valueOf(value.latitude));
            longitude.setText(value.longitude==0D?"":String.valueOf(value.longitude));
            bank.setText(value.bankName);
            account.setText(value.bankAccount); accountName.setText(value.accountName); qr.setText(value.qrUrl);
        } else {
            details.removeAllViews();
            if(!value.configured){CustomerUi.text(details,"Người bán chưa cập nhật trang cửa hàng.");return;}
            CustomerUi.text(details,value.shopName).setTextSize(22);
            CustomerUi.text(details,value.description); CustomerUi.text(details,value.address);
            CustomerUi.text(details,"Phí giao hàng được tính theo khoảng cách GPS khi đặt giao hàng.");
        }
    }
    private double coordinate(EditText input,int limit) {
        try {
            double value=Double.parseDouble(CustomerUi.value(input).replace(',','.'));
            if(!Double.isFinite(value)||Math.abs(value)>limit)throw new NumberFormatException();
            return value;
        }catch(NumberFormatException error){throw new IllegalArgumentException("Nhập tọa độ chính xác của địa chỉ cửa hàng, hoặc lấy GPS trong hồ sơ Seller.");}
    }
}
