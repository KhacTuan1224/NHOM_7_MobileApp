package com.rescuefarm.domain.model;

import com.rescuefarm.domain.enums.PaymentMethod;
import com.rescuefarm.domain.enums.PaymentStatus;

import java.util.Date;

public class Payment {
    private String verificationProvider = "", transferContent = "";
    public void setVerificationDetails(String provider, String content) {
        verificationProvider = provider == null ? "" : provider.trim();
        transferContent = content == null ? "" : content.trim();
    }
    public boolean usesAutomaticVerification() { return "SEPAY".equals(verificationProvider); }
    public String getTransferContent() { return transferContent; }
    private String qrUrl = "";
    public String getQrUrl() { return qrUrl; }
    public void setQrUrl(String value) { qrUrl = value == null ? "" : value.trim(); }
    private String id;
    private String orderId;
    private PaymentMethod method;
    private PaymentStatus status;
    private double amount;
    private String referenceCode;
    private Date paidAt;
    private String bankCode = "";
    private String bankAccountName = "";
    private String bankAccountNumber = "";

    public Payment() { status = PaymentStatus.UNPAID; }

    public static Payment create(String orderId, PaymentMethod method, double amount) {
        if (orderId == null || orderId.trim().isEmpty() || method == null
                || !Double.isFinite(amount) || amount < 0D) {
            throw new IllegalArgumentException("Payment data is invalid");
        }
        Payment value = new Payment();
        value.id = orderId.trim(); value.orderId = orderId.trim(); value.method = method;
        value.amount = amount;
        value.status = method == PaymentMethod.BANK_TRANSFER
                ? PaymentStatus.PENDING : PaymentStatus.UNPAID;
        return value;
    }

    public static Payment restore(String id, String orderId, PaymentMethod method,
            PaymentStatus status, double amount, String referenceCode, Date paidAt) {
        Payment value = create(orderId, method, amount);
        value.id = id == null || id.trim().isEmpty() ? value.orderId : id.trim();
        value.status = status == null ? value.status : status;
        value.referenceCode = referenceCode == null ? "" : referenceCode.trim();
        value.paidAt = copy(paidAt); return value;
    }

    public void markPending() { status = PaymentStatus.PENDING; }

    public void markPaid(Date paymentTime) {
        if (status != PaymentStatus.PENDING && status != PaymentStatus.UNPAID) {
            throw new IllegalStateException("Only an unpaid or pending payment can be marked paid");
        }
        status = PaymentStatus.PAID;
        paidAt = copy(paymentTime == null ? new Date() : paymentTime);
    }

    public void setReferenceCode(String value) {
        referenceCode = value == null ? "" : value.trim();
    }
    public void setBankDetails(String code, String accountName, String accountNumber) {
        bankCode = code == null ? "" : code.trim();
        bankAccountName = accountName == null ? "" : accountName.trim();
        bankAccountNumber = accountNumber == null ? "" : accountNumber.trim();
    }

    public void markFailed() {
        if (status == PaymentStatus.PAID) { throw new IllegalStateException("A paid payment cannot be marked failed"); }
        status = PaymentStatus.FAILED;
    }

    public String getId() { return id; }
    public String getOrderId() { return orderId; }
    public PaymentMethod getMethod() { return method; }
    public PaymentStatus getStatus() { return status; }
    public double getAmount() { return amount; }
    public String getReferenceCode() { return referenceCode; }
    public Date getPaidAt() { return copy(paidAt); }
    public String getBankCode() { return bankCode; }
    public String getBankAccountName() { return bankAccountName; }
    public String getBankAccountNumber() { return bankAccountNumber; }
    private static Date copy(Date value) { return value == null ? null : new Date(value.getTime()); }
}
