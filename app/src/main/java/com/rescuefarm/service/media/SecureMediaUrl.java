package com.rescuefarm.service.media;

import java.net.URI;

/** Accepts only absolute HTTPS URLs for remote media; rejects file/content/cleartext schemes. */
public final class SecureMediaUrl {
    private SecureMediaUrl() { }

    public static String from(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        String candidate = value.trim();
        try {
            URI uri = new URI(candidate);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                    || uri.getHost().isEmpty() || uri.getRawUserInfo() != null
                    || uri.getPort() > 65535 || uri.getRawFragment() != null) return null;
            return candidate;
        } catch (Exception invalidUrl) {
            return null;
        }
    }
}
