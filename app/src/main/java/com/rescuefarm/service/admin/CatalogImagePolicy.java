package com.rescuefarm.service.admin;

import java.net.URI;

/** Existing HTTPS seed images may be retained; replacement uploads use Cloudinary. */
public final class CatalogImagePolicy {
    private CatalogImagePolicy() { }
    public static boolean isAllowed(String url, String previousUrl) {
        if (url == null || url.isEmpty()) return true;
        if (url.length() > 2048) return false;
        try {
            URI uri = URI.create(url);
            if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null) return false;
            return url.equals(previousUrl) || ("res.cloudinary.com".equals(uri.getHost())
                    && uri.getPath() != null && uri.getPath().length() > 1);
        } catch (IllegalArgumentException error) { return false; }
    }
}
