package com.rescuefarm.service.media;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/** Extracts Cloudinary public IDs so Firestore can track and later remove assets. */
public final class CloudinaryAssetIds {
    private static final ConcurrentHashMap<String, String> UPLOAD_IDS = new ConcurrentHashMap<>();
    private CloudinaryAssetIds() { }

    public static String fromSecureUrl(String value) {
        if (value != null) {
            String known = UPLOAD_IDS.get(value.trim());
            if (known != null) return known;
        }
        try {
            URI uri = URI.create(value == null ? "" : value.trim());
            if (!"res.cloudinary.com".equalsIgnoreCase(uri.getHost())) return "";
            String path = uri.getPath();
            int upload = path.indexOf("/upload/");
            if (upload < 0) return "";
            String tail = path.substring(upload + 8);
            String[] parts = tail.split("/");
            int start = 0;
            while (start < parts.length && (parts[start].startsWith("v") && parts[start].substring(1).matches("[0-9]+")
                    || parts[start].contains(",") || parts[start].contains("_") && parts[start].contains("x"))) start++;
            if (start >= parts.length) return "";
            StringBuilder id = new StringBuilder();
            for (int i = start; i < parts.length; i++) {
                if (id.length() > 0) id.append('/');
                id.append(parts[i]);
            }
            int dot = id.lastIndexOf(".");
            if (dot > id.lastIndexOf("/")) id.setLength(dot);
            return id.toString();
        } catch (Exception ignored) { return ""; }
    }

    public static void rememberUpload(String secureUrl, String publicId) {
        if (secureUrl != null && publicId != null && !secureUrl.trim().isEmpty() && !publicId.trim().isEmpty())
            UPLOAD_IDS.put(secureUrl.trim(), publicId.trim());
    }

    public static List<String> fromSecureUrls(List<String> urls) {
        List<String> ids = new ArrayList<>();
        if (urls != null) for (String url : urls) {
            String id = fromSecureUrl(url);
            if (!id.isEmpty()) ids.add(id);
        }
        return ids;
    }
}
