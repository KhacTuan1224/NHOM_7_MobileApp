package com.rescuefarm.service.admin;

import org.junit.Test;
import static org.junit.Assert.*;

public class CatalogImagePolicyTest {
    private final String seed = "https://images.unsplash.com/photo-1586201375761-83865001e31c?auto=format&fit=crop&w=1000&q=85";
    @Test public void editingMetadataCanRetainExistingSeedImage() {
        assertTrue(CatalogImagePolicy.isAllowed(seed, seed));
        assertTrue(CatalogImagePolicy.isAllowed("https://res.cloudinary.com/demo/image/upload/category.png", seed));
        assertTrue(CatalogImagePolicy.isAllowed("", ""));
    }
    @Test public void adminCanPasteHttpsImagesForNewResourcesAndReplacements() {
        assertTrue(CatalogImagePolicy.isAllowed(seed, ""));
        assertTrue(CatalogImagePolicy.isAllowed(seed + "&changed=true", seed));
        assertTrue(CatalogImagePolicy.isAllowed("https://example.com/banner.jpg", seed));
    }
    @Test public void rejectsInsecureLocalMalformedAndCredentialBearingUrls() {
        assertFalse(CatalogImagePolicy.isAllowed("https://user:password@example.com/image.png", seed));
        assertFalse(CatalogImagePolicy.isAllowed("file:///storage/image.png", seed));
        assertFalse(CatalogImagePolicy.isAllowed("https://example.com/image with spaces.png", seed));
        assertFalse(CatalogImagePolicy.isAllowed("http://images.unsplash.com/image.png", "http://images.unsplash.com/image.png"));
        assertFalse(CatalogImagePolicy.isAllowed("not a URL", seed));
    }
}
