package com.rescuefarm.domain.model;

import com.rescuefarm.domain.enums.SellerStatus;
import com.rescuefarm.domain.enums.UserRole;

public class Seller extends User {
    private String shopName;
    private String representativeName;
    private String shopDescription;
    private String shopAvatarUrl;
    private String address;
    private SellerStatus sellerStatus;
    private double averageRating;
    private String bankCode = "";
    private String bankAccountName = "";
    private String bankAccountNumber = "";

    public Seller() { super(); }

    public Seller(String id, String email, String fullName, String shopName) {
        super(id, email, fullName, UserRole.SELLER);
        this.shopName = shopName;
        this.sellerStatus = SellerStatus.PENDING_APPROVAL;
    }

    public Seller(
            String id,
            String email,
            String fullName,
            String shopName,
            SellerStatus sellerStatus
    ) {
        super(id, email, fullName, UserRole.SELLER);
        this.shopName = shopName;
        this.sellerStatus = sellerStatus == null
                ? SellerStatus.PENDING_APPROVAL
                : sellerStatus;
    }

    public void updateSellerProfile(
            String representativeName,
            String shopName,
            String shopDescription,
            String shopAvatarUrl,
            String address
    ) {
        this.representativeName = representativeName == null ? "" : representativeName.trim();
        this.shopName = shopName == null ? "" : shopName.trim();
        this.shopDescription = shopDescription == null ? "" : shopDescription.trim();
        this.shopAvatarUrl = shopAvatarUrl == null ? "" : shopAvatarUrl.trim();
        this.address = address == null ? "" : address.trim();
    }

    public boolean canSell() { return sellerStatus == SellerStatus.APPROVED; }
    public void updateBankDetails(String bankCode, String bankAccountName, String bankAccountNumber) {
        this.bankCode = bankCode == null ? "" : bankCode.trim();
        this.bankAccountName = bankAccountName == null ? "" : bankAccountName.trim();
        this.bankAccountNumber = bankAccountNumber == null ? "" : bankAccountNumber.trim();
    }
    public String getShopName() { return shopName; }
    public String getRepresentativeName() { return representativeName; }
    public String getShopDescription() { return shopDescription; }
    public String getShopAvatarUrl() { return shopAvatarUrl; }
    public String getAddress() { return address; }
    public SellerStatus getSellerStatus() { return sellerStatus; }
    public double getAverageRating() { return averageRating; }
    public String getBankCode() { return bankCode; }
    public String getBankAccountName() { return bankAccountName; }
    public String getBankAccountNumber() { return bankAccountNumber; }
}
