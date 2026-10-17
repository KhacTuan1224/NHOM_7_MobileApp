package com.rescuefarm.service;

import com.rescuefarm.domain.model.Payment;
import com.rescuefarm.domain.enums.PaymentMethod;
import com.rescuefarm.domain.enums.PaymentStatus;
import org.junit.Test;
import static org.junit.Assert.*;

public class SePayPaymentTest {
    @Test public void oldPaymentsKeepTheirManualFlowWithoutFabricatingAutomaticVerification() {
        Payment old = Payment.restore("order", "order", PaymentMethod.BANK_TRANSFER,
                PaymentStatus.PENDING, 20000, "", null);
        assertFalse(old.usesAutomaticVerification());
        assertEquals("", old.getTransferContent());
    }
    @Test public void automaticPaymentKeepsServerReferenceSeparateFromPaidStatus() {
        Payment payment = Payment.create("order", PaymentMethod.BANK_TRANSFER, 20000);
        String reference = "SEVQR0123456789ABCDEF0123";
        payment.setVerificationDetails("SEPAY", reference);
        assertTrue(payment.usesAutomaticVerification());
        assertEquals(reference, payment.getTransferContent());
        assertEquals(PaymentStatus.PENDING, payment.getStatus());
    }
}
