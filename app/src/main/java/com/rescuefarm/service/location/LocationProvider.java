package com.rescuefarm.service.location;

public interface LocationProvider {
    void getCurrentLocation(LocationCallback callback);
    void reverseGeocode(double latitude, double longitude, GeocodeCallback callback);
    default void close() { }

    interface LocationCallback {
        void onLocationAvailable(double latitude, double longitude);
        void onLocationUnavailable(String message);
    }


    interface GeocodeCallback {
        void onAddressAvailable(GeocodedAddress address);
        void onGeocodeUnavailable(String message);
    }

    final class GeocodedAddress {
        private final String province;
        private final String district;
        private final String ward;
        private final String street;
        private final String fullAddress;

        public GeocodedAddress(String province, String district, String ward, String street) {
            this(province, district, ward, street, "");
        }

        public GeocodedAddress(String province, String district, String ward, String street, String fullAddress) {
            this.province = province == null ? "" : province;
            this.district = district == null ? "" : district;
            this.ward = ward == null ? "" : ward;
            this.street = street == null ? "" : street;
            this.fullAddress = fullAddress == null ? "" : fullAddress;
        }

        public String getProvince() { return province; }
        public String getDistrict() { return district; }
        public String getWard() { return ward; }
        public String getStreet() { return street; }
        public String getFullAddress() { return fullAddress; }
    }
}
