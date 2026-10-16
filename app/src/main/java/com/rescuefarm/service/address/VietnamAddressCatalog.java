package com.rescuefarm.service.address;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Offline three-level snapshot from daohoangson/dvhcvn. IDs retain leading zeros. */
public final class VietnamAddressCatalog {
    public static final class Unit {
        public final String id, name;
        private final List<Unit> children = new ArrayList<>();
        private Unit(String id, String name) { this.id = id; this.name = name; }
        public List<Unit> children() { return Collections.unmodifiableList(children); }
        @Override public String toString() { return name; }
    }

    private final List<Unit> provinces;
    private VietnamAddressCatalog(List<Unit> provinces) {
        this.provinces = Collections.unmodifiableList(provinces);
    }
    public List<Unit> provinces() { return provinces; }

    public static VietnamAddressCatalog read(Reader reader) throws IOException {
        List<Unit> provinces = new ArrayList<>();
        Map<String, Unit> units = new LinkedHashMap<>();
        BufferedReader lines = new BufferedReader(reader);
        String line;
        while ((line = lines.readLine()) != null) {
            String[] parts = line.split("\t", -1);
            if (parts.length != 3 || parts[0].isEmpty() || parts[2].isEmpty()
                    || units.containsKey(parts[0])) throw new IOException("Invalid address catalog row");
            Unit unit = new Unit(parts[0], parts[2]);
            if (parts[1].isEmpty()) provinces.add(unit);
            else {
                Unit parent = units.get(parts[1]);
                if (parent == null) throw new IOException("Missing administrative parent");
                parent.children.add(unit);
            }
            units.put(unit.id, unit);
        }
        if (provinces.isEmpty()) throw new IOException("Empty address catalog");
        return new VietnamAddressCatalog(provinces);
    }

    /** Match names from older saved addresses/GPS, but only within the selected parent. */
    public static Unit find(List<Unit> units, String name) {
        String key = normalize(name);
        if (key.isEmpty()) return null;
        for (Unit unit : units) if (normalize(unit.name).equals(key)) return unit;
        String shortKey = withoutPrefix(key);
        Unit match = null;
        for (Unit unit : units) if (withoutPrefix(normalize(unit.name)).equals(shortKey)) {
            if (match != null) return null; // Ambiguous short names require an explicit choice.
            match = unit;
        }
        return match;
    }

    public static String normalize(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value.trim().toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").replace('đ', 'd').replaceAll("\\s+", " ");
    }
    private static String withoutPrefix(String value) {
        return value.replaceFirst("^(thanh pho|tinh|quan|huyen|thi xa|phuong|xa|thi tran|tp[.]?)\\s+", "");
    }
}
