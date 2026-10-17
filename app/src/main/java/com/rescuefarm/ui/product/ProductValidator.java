package com.rescuefarm.ui.product;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class ProductValidator {
    private ProductValidator() { }

    public static String validateProduct(String categoryId, String name, String originalPrice,
            String rescuePrice, String unit) {
        if (clean(categoryId).isEmpty()) return "Vui lòng chọn danh mục đang hoạt động.";
        if (clean(name).length() < 2) return "Tên sản phẩm phải có ít nhất 2 ký tự.";
        Double original = number(originalPrice); Double rescue = number(rescuePrice);
        if (original == null || rescue == null || original <= 0 || rescue <= 0) {
            return "Giá phải là số dương.";
        }
        if (rescue > original) return "Giá giải cứu không được cao hơn giá gốc.";
        if (clean(unit).isEmpty()) return "Vui lòng nhập đơn vị bán.";
        return null;
    }

    public static String validateBatch(String harvestDate, String expiryDate, String quantity,
            Date now) {
        Date harvest = parseDate(harvestDate); Date expiry = parseDate(expiryDate);
        if (harvest == null || expiry == null) return "Ngày phải theo định dạng yyyy-MM-dd.";
        if (harvest.after(expiry)) return "Ngày thu hoạch không được sau hạn sử dụng.";
        if (!expiry.after(now)) return "Hạn sử dụng phải ở tương lai.";
        Double value = number(quantity);
        if (value == null || value <= 0) return "Số lượng batch phải là số dương.";
        return null;
    }

    public static Date parseDate(String value) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        format.setLenient(false);
        try { return format.parse(clean(value)); }
        catch (ParseException error) { return null; }
    }
    public static double parseNumber(String value) {
        Double result = number(value); return result == null ? Double.NaN : result;
    }
    private static Double number(String value) {
        try {
            double result = Double.parseDouble(clean(value));
            return Double.isFinite(result) ? result : null;
        } catch (NumberFormatException error) { return null; }
    }
    private static String clean(String value) { return value == null ? "" : value.trim(); }
}
