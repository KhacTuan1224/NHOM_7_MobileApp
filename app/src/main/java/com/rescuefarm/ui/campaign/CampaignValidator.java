package com.rescuefarm.ui.campaign;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class CampaignValidator {
    private CampaignValidator() { }

    public static String validate(String title, String targets, String startDate, String endDate,
            String latitude, String longitude) {
        if (clean(title).length() < 3 || clean(title).length() > 120)
            return "Tên chiến dịch cần từ 3 đến 120 ký tự.";
        Map<String, Double> parsed = parseTargets(targets);
        if (parsed.isEmpty()) return "Cần ít nhất một batch target theo dạng batchId=số lượng.";
        if (parsed.size() > 8) return "Mỗi chiến dịch hỗ trợ tối đa 8 batch.";
        Date start = parseDate(startDate); Date end = parseDate(endDate);
        if (start == null || end == null) return "Ngày phải theo định dạng yyyy-MM-dd.";
        if (!end.after(start)) return "Ngày kết thúc phải sau ngày bắt đầu.";
        Double lat = number(latitude); Double lng = number(longitude);
        if (lat == null || lng == null || !com.rescuefarm.service.location.GpsCoordinates.isValid(lat, lng)) {
            return "Hãy lấy GPS tại điểm giải cứu trước khi lưu.";
        }
        return null;
    }

    public static String validate(String title, Map<String, Double> targets, String startDate,
            String endDate, String latitude, String longitude) {
        if (clean(title).length() < 3 || clean(title).length() > 120)
            return "Tên chiến dịch cần từ 3 đến 120 ký tự.";
        if (targets == null || targets.isEmpty()) return "Hãy chọn ít nhất một lô hàng cho Campaign.";
        if (targets.size() > 8) return "Mỗi chiến dịch hỗ trợ tối đa 8 batch.";
        for (Map.Entry<String, Double> entry : targets.entrySet()) {
            if (clean(entry.getKey()).isEmpty() || entry.getValue() == null
                    || !Double.isFinite(entry.getValue()) || entry.getValue() <= 0D)
                return "Mỗi batch đã chọn phải có lượng mục tiêu lớn hơn 0.";
        }
        Date start = parseDate(startDate); Date end = parseDate(endDate);
        if (start == null || end == null) return "Ngày phải theo định dạng yyyy-MM-dd.";
        if (!end.after(start)) return "Ngày kết thúc phải sau ngày bắt đầu.";
        Double lat = number(latitude); Double lng = number(longitude);
        if (lat == null || lng == null || !com.rescuefarm.service.location.GpsCoordinates.isValid(lat, lng))
            return "Hãy lấy GPS tại điểm giải cứu trước khi lưu.";
        return null;
    }

    public static Map<String, Double> parseTargets(String raw) {
        Map<String, Double> result = new LinkedHashMap<>();
        if (raw == null) return result;
        for (String line : raw.split("[,;\\n]")) {
            String item = line.trim(); if (item.isEmpty()) continue;
            int separator = item.lastIndexOf('=');
            if (separator <= 0) return new LinkedHashMap<>();
            String id = item.substring(0, separator).trim();
            Double quantity = number(item.substring(separator + 1));
            if (id.isEmpty() || quantity == null || quantity <= 0 || result.containsKey(id)) {
                return new LinkedHashMap<>();
            }
            result.put(id, quantity);
        }
        return result;
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
        try { double parsed = Double.parseDouble(clean(value)); return Double.isFinite(parsed) ? parsed : null; }
        catch (NumberFormatException error) { return null; }
    }
    private static String clean(String value) { return value == null ? "" : value.trim(); }
}
