package com.rescuefarm.service;

import com.rescuefarm.domain.model.CartItem;
import com.rescuefarm.service.cart.CheckoutCartCleanup;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class CheckoutCartCleanupTest {
    private Map<String,Object> item(String id, double quantity, boolean selected) {
        Map<String,Object> value = new HashMap<>(); value.put("id",id); value.put("quantity",quantity);
        value.put("selected",selected); return value;
    }
    @Test public void preservesUnselectedItemsAndConcurrentExtraQuantityWithoutMutatingInput() {
        Map<String,Object> bought=item("p::b",5,true), keep=item("other",2,false);
        CartItem request=new CartItem("p::b","p","b","seller",3,30000);
        List<Map<String,Object>> remaining=CheckoutCartCleanup.remaining(Arrays.asList(bought,keep),Collections.singletonList(request));
        assertEquals(2,remaining.size()); assertEquals(2D,remaining.get(0).get("quantity"));
        assertEquals(false,remaining.get(1).get("selected")); assertEquals(5D,bought.get("quantity"));
    }
    @Test public void removesOnlyThePurchasedLine() {
        CartItem request=new CartItem("p::b","p","b","seller",3,30000);
        List<Map<String,Object>> remaining=CheckoutCartCleanup.remaining(Arrays.asList(item("p::b",3,true),item("keep",2,true)),Collections.singletonList(request));
        assertEquals(1,remaining.size()); assertEquals("keep",remaining.get(0).get("id"));
    }
    @Test(expected=IllegalStateException.class) public void rejectsStaleCartQuantity() {
        CheckoutCartCleanup.remaining(Collections.singletonList(item("p::b",1,true)),
            Collections.singletonList(new CartItem("p::b","p","b","seller",3,30000)));
    }
}
