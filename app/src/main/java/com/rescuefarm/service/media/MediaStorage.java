package com.rescuefarm.service.media;

import java.util.concurrent.CompletableFuture;

public interface MediaStorage {
    CompletableFuture<String> uploadImage(String localUri);
    default CompletableFuture<String> uploadImage(String localUri, String folder) {
        return uploadImage(localUri);
    }
    CompletableFuture<Void> deleteImage(String remoteUrl);
}
