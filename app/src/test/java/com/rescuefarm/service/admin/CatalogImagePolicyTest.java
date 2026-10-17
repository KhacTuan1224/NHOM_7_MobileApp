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
    @Test public void replacementsAndNewResourcesStillUseCloudinaryUploads() {
        assertFalse(CatalogImagePolicy.isAllowed(seed, ""));
        assertFalse(CatalogImagePolicy.isAllowed(seed + "&changed=true", seed));
        assertFalse(CatalogImagePolicy.isAllowed("https://res.cloudinary.com.evil.test/image.png", seed));
        assertFalse(CatalogImagePolicy.isAllowed("http://images.unsplash.com/image.png", "http://images.unsplash.com/image.png"));
        assertFalse(CatalogImagePolicy.isAllowed("not a URL", seed));
    }
}
