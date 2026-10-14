package com.rescuefarm.data.repository;

import android.content.Context;
import com.rescuefarm.data.local.database.RescueFarmDatabase;
import com.rescuefarm.data.remote.firebase.FirebaseConfiguration;
import com.rescuefarm.data.remote.firebase.FirebasePostRepository;
import com.rescuefarm.service.network.AndroidNetworkStatusProvider;
import java.util.concurrent.Executors;

public final class PostRepositoryFactory {
    private PostRepositoryFactory() { }
    public static PostRepository create(Context context) {
        RescueFarmDatabase database = RescueFarmDatabase.getInstance(context);
        if (!FirebaseConfiguration.isConfigured(context)) return new OfflinePostRepository(
                database.postCacheDao(), Executors.newSingleThreadExecutor());
        return new FirebasePostRepository(database, Executors.newSingleThreadExecutor(),
                new AndroidNetworkStatusProvider(context),
                new com.rescuefarm.data.local.preferences.SharedPreferencesGuestSessionRepository(context).getOrCreateGuestId());
    }
}
