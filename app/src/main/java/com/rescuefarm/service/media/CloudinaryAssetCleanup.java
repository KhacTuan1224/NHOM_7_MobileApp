package com.rescuefarm.service.media;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Removes assets no longer referenced after the corresponding Firestore write succeeds. */
public final class CloudinaryAssetCleanup {
    private CloudinaryAssetCleanup() { }

    public static void deleteRemoved(List<String> oldUrls, List<String> currentUrls) {
        Set<String> currentIds = new HashSet<>(CloudinaryAssetIds.fromSecureUrls(currentUrls));
        if (oldUrls == null) return;
        for (String oldUrl : oldUrls) {
            String oldId = CloudinaryAssetIds.fromSecureUrl(oldUrl);
            if (!oldId.isEmpty() && !currentIds.contains(oldId))
                CloudinaryMediaStorage.deleteImageFromUrl(oldUrl);
        }
    }

    public static void deleteRemoved(String oldUrl, String currentUrl) {
        if (oldUrl == null || oldUrl.trim().isEmpty()) return;
        deleteRemoved(java.util.Collections.singletonList(oldUrl),
                currentUrl == null || currentUrl.trim().isEmpty()
                        ? java.util.Collections.emptyList() : java.util.Collections.singletonList(currentUrl));
    }
}
