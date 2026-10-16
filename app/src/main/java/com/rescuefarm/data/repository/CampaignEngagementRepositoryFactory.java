package com.rescuefarm.data.repository;

import android.content.Context;
import com.rescuefarm.data.remote.firebase.FirebaseCampaignEngagementRepository;
import com.rescuefarm.service.network.AndroidNetworkStatusProvider;

public final class CampaignEngagementRepositoryFactory {
    private CampaignEngagementRepositoryFactory() { }
    public static CampaignEngagementRepository create(Context context) {
        String viewerId = new com.rescuefarm.data.local.preferences.SharedPreferencesGuestSessionRepository(context)
                .getOrCreateGuestId();
        return new FirebaseCampaignEngagementRepository(new AndroidNetworkStatusProvider(context), viewerId);
    }
}
