package com.rescuefarm.data.repository;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class SellerStorefrontTest {
    @Test public void explicitZeroFeeIsConfiguredButMissingFeeIsNot() {
        Map<String,Object> data=new HashMap<>();
        assertFalse(SellerStorefront.from("seller",data).shippingConfigured);
        data.put("shippingFee",0);
        assertTrue(SellerStorefront.from("seller",data).shippingConfigured);
    }
    @Test public void mapsExistingPublicProfileAndKeepsCoordinatesAndAvatarWhenSaved() {
        Map<String,Object> data=new HashMap<>(); data.put("shopName","Shop");data.put("address","Farm");
        data.put("latitude",10D);data.put("longitude",106D);data.put("shopAvatarUrl","https://example.com/avatar.png");
        data.put("shippingFee",15000);data.put("bankCode","VCB");data.put("bankAccountName","Seller");data.put("bankAccountNumber","12345");
        SellerStorefront value=SellerStorefront.from("seller",data);
        value.validate();assertTrue(value.acceptsTransfer());
        assertEquals(data.get("latitude"),value.toMap().get("latitude"));
        assertEquals(data.get("shopAvatarUrl"),value.toMap().get("shopAvatarUrl"));
        assertEquals("VCB",value.bankName);
    }
}
