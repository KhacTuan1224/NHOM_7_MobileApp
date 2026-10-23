package com.rescuefarm.service.admin;

import java.text.Normalizer;
import java.util.Locale;

public final class AdminSearchText {
    private AdminSearchText() { }

    public static String normalize(String value) {
        return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replace('đ', 'd').replace('Đ', 'd')
                .toLowerCase(Locale.ROOT).trim().replaceAll("\\s+", " ");
    }

    public static boolean contains(String value, String query) {
        String needle = normalize(query);
        return needle.isEmpty() || normalize(value).contains(needle);
    }
}
