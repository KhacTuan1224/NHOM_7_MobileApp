package com.rescuefarm.domain.model;

import java.util.ArrayList;
import java.util.List;

public class RecentGuestOrder {
    private String orderId;
    private String orderCode;
    private String phone;
    private double totalAmount;
    private String status;
    private String paymentMethod;
    private long createdAtMillis;

    public RecentGuestOrder() {
        this.orderId = "";
        this.orderCode = "";
        this.phone = "";
        this.totalAmount = 0.0;
        this.status = "PENDING";
        this.paymentMethod = "COD";
        this.createdAtMillis = System.currentTimeMillis();
    }

    public RecentGuestOrder(String orderId, String orderCode, String phone, double totalAmount,
                            String status, String paymentMethod, long createdAtMillis) {
        this.orderId = orderId == null ? "" : orderId.trim();
        this.orderCode = orderCode == null ? "" : orderCode.trim();
        this.phone = phone == null ? "" : phone.trim();
        this.totalAmount = totalAmount;
        this.status = status == null || status.trim().isEmpty() ? "PENDING" : status.trim();
        this.paymentMethod = paymentMethod == null || paymentMethod.trim().isEmpty() ? "COD" : paymentMethod.trim();
        this.createdAtMillis = createdAtMillis > 0 ? createdAtMillis : System.currentTimeMillis();
    }

    public String getOrderId() {
        return orderId == null ? "" : orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId == null ? "" : orderId.trim();
    }

    public String getOrderCode() {
        return orderCode == null ? "" : orderCode;
    }

    public void setOrderCode(String orderCode) {
        this.orderCode = orderCode == null ? "" : orderCode.trim();
    }

    public String getPhone() {
        return phone == null ? "" : phone;
    }

    public void setPhone(String phone) {
        this.phone = phone == null ? "" : phone.trim();
    }

    public double getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(double totalAmount) {
        this.totalAmount = totalAmount;
    }

    public String getStatus() {
        return status == null ? "PENDING" : status;
    }

    public void setStatus(String status) {
        this.status = status == null || status.trim().isEmpty() ? "PENDING" : status.trim();
    }

    public String getPaymentMethod() {
        return paymentMethod == null ? "COD" : paymentMethod;
    }

    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod == null || paymentMethod.trim().isEmpty() ? "COD" : paymentMethod.trim();
    }

    public long getCreatedAtMillis() {
        return createdAtMillis;
    }

    public void setCreatedAtMillis(long createdAtMillis) {
        this.createdAtMillis = createdAtMillis;
    }

    public String toJsonString() {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"orderId\":\"").append(escape(getOrderId())).append("\",");
        sb.append("\"orderCode\":\"").append(escape(getOrderCode())).append("\",");
        sb.append("\"phone\":\"").append(escape(getPhone())).append("\",");
        sb.append("\"totalAmount\":").append(totalAmount).append(",");
        sb.append("\"status\":\"").append(escape(getStatus())).append("\",");
        sb.append("\"paymentMethod\":\"").append(escape(getPaymentMethod())).append("\",");
        sb.append("\"createdAtMillis\":").append(createdAtMillis);
        sb.append("}");
        return sb.toString();
    }

    public static RecentGuestOrder fromJsonString(String json) {
        if (json == null || json.trim().isEmpty()) return null;
        String clean = json.trim();
        if (!clean.startsWith("{") || !clean.endsWith("}")) return null;
        clean = clean.substring(1, clean.length() - 1);

        RecentGuestOrder order = new RecentGuestOrder();
        int len = clean.length();
        int i = 0;
        while (i < len) {
            int keyStart = clean.indexOf('"', i);
            if (keyStart == -1) break;
            int keyEnd = clean.indexOf('"', keyStart + 1);
            if (keyEnd == -1) break;
            String key = clean.substring(keyStart + 1, keyEnd);

            int colon = clean.indexOf(':', keyEnd + 1);
            if (colon == -1) break;

            int valStart = colon + 1;
            while (valStart < len && Character.isWhitespace(clean.charAt(valStart))) valStart++;
            if (valStart >= len) break;

            String valueStr = "";
            int valEnd;
            if (clean.charAt(valStart) == '"') {
                StringBuilder valSb = new StringBuilder();
                int v = valStart + 1;
                boolean escaped = false;
                while (v < len) {
                    char c = clean.charAt(v);
                    if (escaped) {
                        valSb.append(c);
                        escaped = false;
                    } else if (c == '\\') {
                        escaped = true;
                    } else if (c == '"') {
                        break;
                    } else {
                        valSb.append(c);
                    }
                    v++;
                }
                valueStr = valSb.toString();
                valEnd = v + 1;
            } else {
                valEnd = valStart;
                while (valEnd < len && clean.charAt(valEnd) != ',' && clean.charAt(valEnd) != '}') {
                    valEnd++;
                }
                valueStr = clean.substring(valStart, valEnd).trim();
            }

            switch (key) {
                case "orderId": order.setOrderId(valueStr); break;
                case "orderCode": order.setOrderCode(valueStr); break;
                case "phone": order.setPhone(valueStr); break;
                case "totalAmount":
                    try { order.setTotalAmount(Double.parseDouble(valueStr)); } catch (Exception ignored) {}
                    break;
                case "status": order.setStatus(valueStr); break;
                case "paymentMethod": order.setPaymentMethod(valueStr); break;
                case "createdAtMillis":
                    try { order.setCreatedAtMillis(Long.parseLong(valueStr)); } catch (Exception ignored) {}
                    break;
            }

            int nextComma = clean.indexOf(',', valEnd);
            if (nextComma == -1) break;
            i = nextComma + 1;
        }
        return order.getOrderCode().isEmpty() ? null : order;
    }

    public static String listToJson(List<RecentGuestOrder> list) {
        if (list == null || list.isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            RecentGuestOrder item = list.get(i);
            if (item != null) {
                sb.append(item.toJsonString());
            }
        }
        sb.append("]");
        return sb.toString();
    }

    public static List<RecentGuestOrder> listFromJson(String jsonStr) {
        List<RecentGuestOrder> list = new ArrayList<>();
        if (jsonStr == null || jsonStr.trim().isEmpty()) return list;
        String clean = jsonStr.trim();
        if (!clean.startsWith("[") || !clean.endsWith("]")) return list;

        int depth = 0;
        int objStart = -1;
        boolean inQuotes = false;
        boolean escaped = false;

        for (int i = 0; i < clean.length(); i++) {
            char c = clean.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (c == '\\') {
                escaped = true;
                continue;
            }
            if (c == '"') {
                inQuotes = !inQuotes;
                continue;
            }
            if (!inQuotes) {
                if (c == '{') {
                    if (depth == 0) objStart = i;
                    depth++;
                } else if (c == '}') {
                    depth--;
                    if (depth == 0 && objStart != -1) {
                        String objStr = clean.substring(objStart, i + 1);
                        RecentGuestOrder order = fromJsonString(objStr);
                        if (order != null && !order.getOrderCode().isEmpty()) {
                            list.add(order);
                        }
                        objStart = -1;
                    }
                }
            }
        }
        return list;
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}
