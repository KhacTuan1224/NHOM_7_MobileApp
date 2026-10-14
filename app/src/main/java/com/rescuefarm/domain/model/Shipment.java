package com.rescuefarm.domain.model;

import com.rescuefarm.domain.enums.ShipmentStatus;

public class Shipment {
    private String id;
    private String orderId;
    private String carrierName;
    private String trackingCode;
    private ShipmentStatus status;
    private double distanceKm;
    private double shippingFee;

    public Shipment() { status = ShipmentStatus.PENDING; }

    public static Shipment create(String orderId, double distanceKm, double shippingFee) {
        if (orderId == null || orderId.trim().isEmpty() || !Double.isFinite(distanceKm)
                || distanceKm < 0D || !Double.isFinite(shippingFee) || shippingFee < 0D) {
            throw new IllegalArgumentException("Shipment data is invalid");
        }
        Shipment value = new Shipment(); value.id = orderId.trim(); value.orderId = orderId.trim();
        value.distanceKm = distanceKm; value.shippingFee = shippingFee;
        value.carrierName = ""; value.trackingCode = ""; return value;
    }

    public static Shipment restore(String id, String orderId, String carrierName,
            String trackingCode, ShipmentStatus status, double distanceKm, double shippingFee) {
        Shipment value = create(orderId, distanceKm, shippingFee);
        value.id = id == null || id.trim().isEmpty() ? value.orderId : id.trim();
        value.carrierName = clean(carrierName); value.trackingCode = clean(trackingCode);
        value.status = status == null ? ShipmentStatus.PENDING : status; return value;
    }

    public void updateStatus(ShipmentStatus updatedStatus) {
        if (updatedStatus == null) { throw new IllegalArgumentException("Shipment status is required"); }
        status = updatedStatus;
    }

    public void updateCarrierDetails(String updatedCarrierName, String updatedTrackingCode) {
        String carrier = clean(updatedCarrierName);
        String tracking = clean(updatedTrackingCode);
        if (carrier.isEmpty() || carrier.length() > 120) {
            throw new IllegalArgumentException("Hãng vận chuyển phải có từ 1 đến 120 ký tự.");
        }
        if (tracking.isEmpty() || tracking.length() > 120) {
            throw new IllegalArgumentException("Mã vận đơn phải có từ 1 đến 120 ký tự.");
        }
        carrierName = carrier;
        trackingCode = tracking;
    }

    public String getId() { return id; }
    public String getOrderId() { return orderId; }
    public String getCarrierName() { return carrierName; }
    public String getTrackingCode() { return trackingCode; }
    public ShipmentStatus getStatus() { return status; }
    public double getDistanceKm() { return distanceKm; }
    public double getShippingFee() { return shippingFee; }
    private static String clean(String value) { return value == null ? "" : value.trim(); }
}
