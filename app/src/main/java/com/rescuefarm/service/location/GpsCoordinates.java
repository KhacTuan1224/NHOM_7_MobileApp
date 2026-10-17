package com.rescuefarm.service.location;

/** Shared validity rule for saved pickup and receiver GPS; (0, 0) means unset. */
public final class GpsCoordinates {
    private GpsCoordinates() { }

    public static boolean isValid(double latitude, double longitude) {
        return Double.isFinite(latitude) && Double.isFinite(longitude)
                && Math.abs(latitude) <= 90 && Math.abs(longitude) <= 180
                && (latitude != 0 || longitude != 0);
    }
}
