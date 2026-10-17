package com.rescuefarm.ui.checkout;
import androidx.lifecycle.LiveData;import androidx.lifecycle.MutableLiveData;import androidx.lifecycle.SavedStateHandle;import androidx.lifecycle.ViewModel;
import com.rescuefarm.data.repository.CartRepository;import com.rescuefarm.data.repository.OrderRepository;
import com.rescuefarm.domain.enums.*;import com.rescuefarm.domain.model.*;import com.rescuefarm.service.order.*;
import java.util.*;
public class CheckoutViewModel extends ViewModel {private final CartRepository cartRepo;private final OrderRepository orderRepo;private final com.rescuefarm.data.repository.UserRepository userRepo;private final LiveData<Cart> cart;private final MutableLiveData<CheckoutState> state=new MutableLiveData<>(CheckoutState.idle());private final SavedStateHandle savedState;private final String requestId;private boolean submitting;public CheckoutViewModel(CartRepository c,OrderRepository o){this(c,o,null,new SavedStateHandle());}public CheckoutViewModel(CartRepository c,OrderRepository o,SavedStateHandle state){this(c,o,null,state);}public CheckoutViewModel(CartRepository c,OrderRepository o,com.rescuefarm.data.repository.UserRepository u,SavedStateHandle state){cartRepo=c;orderRepo=o;userRepo=u;this.savedState=state;requestId=CheckoutRequestIdentity.getOrCreate(state);cart=c.observeCart();}public LiveData<Cart> getCart(){return cart;}public LiveData<CheckoutState> getState(){return state;}public LiveData<java.util.List<com.rescuefarm.domain.model.Address>> observeAddresses(){String id=cartRepo.getOwnerKey();return userRepo==null||isGuest()?new MutableLiveData<>(java.util.Collections.emptyList()):userRepo.observeAddresses(id);}public boolean isGuest(){return cartRepo.getOwnerType()==CartOwnerType.GUEST;}public String getRequestId(){return requestId;}
public void loadStorefront(String id, com.rescuefarm.data.repository.StorefrontRepository.Callback callback) {
    if(userRepo == null) callback.error("Chưa kết nối dữ liệu cửa hàng.");
    else userRepo.getPublicStorefront(id, callback);
}
public boolean consumeCheckoutSuccessNavigation(){Boolean consumed=savedState.get("checkout.successNavigationConsumed");if(Boolean.TRUE.equals(consumed))return false;savedState.set("checkout.successNavigationConsumed",true);return true;}
public void submit(String name,String phone,String address,String latText,String lngText,FulfillmentType fulfillment,PaymentMethod payment,String note){
    submit(name,phone,address,latText,lngText,fulfillment,payment,note,Double.NaN,Double.NaN);
}
public void submit(String name,String phone,String address,String latText,String lngText,FulfillmentType fulfillment,
        PaymentMethod payment,String note,double expectedSubtotal,double expectedShippingFee){
    if(submitting||state.getValue()!=null&&state.getValue().getStatus()==CheckoutState.Status.LOADING)return;
    Cart value=cart.getValue();List<CartItem> selected=new ArrayList<>();
    if(value!=null)for(CartItem i:value.getItems())if(i.isSelected())selected.add(i);
    try{
        CheckoutPolicy.requireSingleSeller(selected);
        double lat=parseCoordinate(latText),lng=parseCoordinate(lngText);
        if(fulfillment==FulfillmentType.DELIVERY&&!com.rescuefarm.service.location.GpsCoordinates.isValid(lat,lng))
            throw new IllegalArgumentException("Hãy lấy GPS tại nơi nhận hàng hoặc chọn địa chỉ đã lưu có GPS.");
        if(clean(name).length()<2||clean(phone).length()<9)
            throw new IllegalArgumentException("Tên người nhận và số điện thoại chưa hợp lệ.");
        if(fulfillment==FulfillmentType.DELIVERY&&clean(address).isEmpty())
            throw new IllegalArgumentException("Giao hàng cần địa chỉ nhận hàng.");
        OrderOwnerType owner=cartRepo.getOwnerType()==CartOwnerType.GUEST?OrderOwnerType.GUEST:OrderOwnerType.CUSTOMER;
        CheckoutRequest req=new CheckoutRequest(requestId,owner,cartRepo.getOwnerKey(),name,phone,address,
                lat,lng,fulfillment,payment,note,selected,expectedSubtotal,expectedShippingFee);
        submitting=true;state.setValue(CheckoutState.loading());
        orderRepo.checkout(req,new OrderRepository.OrderCallback(){
            public void onSuccess(Order o,boolean replay){submitting=false;state.postValue(CheckoutState.success(o,replay));}
            public void onError(OrderRepository.ErrorCode e,String m){submitting=false;state.postValue(CheckoutState.error(m));}
        });
    }catch(IllegalArgumentException e){
        state.setValue(CheckoutState.error("MULTI_SELLER".equals(e.getMessage())
                ?"Hãy checkout riêng từng seller.":e.getMessage()));
    }
}
private double parseCoordinate(String v){if(clean(v).isEmpty())return 0D;double n=Double.parseDouble(clean(v));if(!Double.isFinite(n))throw new IllegalArgumentException("Tọa độ không hợp lệ.");return n;}private String clean(String v){return v==null?"":v.trim();}@Override protected void onCleared(){cartRepo.close();orderRepo.close();super.onCleared();}}
