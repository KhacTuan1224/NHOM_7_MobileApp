package com.rescuefarm.service.campaign;
import java.util.Locale;
import java.text.Normalizer;
public final class QuantityUnitPolicy {
    private QuantityUnitPolicy() { }
    public static String normalize(String value) {
        String unit = Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFC).trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        return unit.equals("kilogram") || unit.equals("kilograms") || unit.equals("kilôgam") || unit.equals("ký") ? "kg" : unit;
    }
}
