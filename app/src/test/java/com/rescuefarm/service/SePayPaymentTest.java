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
        assertEquals(reference, payment.resolveQrTransferContent("RF-OLD-CODE"));
    }
    @Test public void automaticPaymentNeverFallsBackToLegacyCodeOrPrefixesAnUnmappedCode() {
        Payment payment = Payment.create("order", PaymentMethod.BANK_TRANSFER, 20000);
        for (String code : new String[]{null, "", "RF77B4506", "SEVQRRF77B4506", "SEVQR0123456789ABCDEF0123EXTRA"}) {
            payment.setVerificationDetails("SEPAY", code);
            assertEquals("", payment.resolveQrTransferContent("RF77B4506"));
        }
        payment.setVerificationDetails("MANUAL", "");
        assertEquals("", payment.resolveQrTransferContent("RF77B4506"));
        payment.setVerificationDetails("MANUAL", "SEVQR0123456789ABCDEF0123");
        assertEquals("SEVQR0123456789ABCDEF0123", payment.resolveQrTransferContent("RF77B4506"));
    }
}
