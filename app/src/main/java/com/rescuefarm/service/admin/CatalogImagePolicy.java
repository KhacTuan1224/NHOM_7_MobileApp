package com.rescuefarm.service.admin;

import java.net.URI;

/** Admin catalog forms accept uploaded images or explicitly pasted HTTPS image URLs. */
public final class CatalogImagePolicy {
    private CatalogImagePolicy() { }
    public static boolean isAllowed(String url, String previousUrl) {
        if (url == null || url.isEmpty()) return true;
        if (url.length() > 2048) return false;
        try {
            URI uri = URI.create(url);
            if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null) return false;
            return uri.getHost().matches("[A-Za-z0-9]([A-Za-z0-9.-]*[A-Za-z0-9])?")
                    && (uri.getPort() == -1 || (uri.getPort() >= 0 && uri.getPort() <= 65535));
        } catch (IllegalArgumentException error) { return false; }
    }
}
