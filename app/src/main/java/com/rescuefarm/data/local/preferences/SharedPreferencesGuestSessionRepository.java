package com.rescuefarm.data.local.preferences;

import android.content.Context;
import android.content.SharedPreferences;

import com.rescuefarm.data.repository.GuestSessionRepository;

import java.util.UUID;

public class SharedPreferencesGuestSessionRepository implements GuestSessionRepository {
    private static final String PREFERENCES_NAME = "rescuefarm_guest_session";
    private static final String KEY_GUEST_ID = "guest_id";
    private static final String KEY_ONBOARDING_COMPLETED = "onboarding_completed";
    private static final String GUEST_ID_PREFIX = "guest_";

    private final SharedPreferences preferences;

    public SharedPreferencesGuestSessionRepository(Context context) {
        preferences = context.getApplicationContext().getSharedPreferences(
                PREFERENCES_NAME,
                Context.MODE_PRIVATE
        );
    }

    @Override
    public synchronized String getOrCreateGuestId() {
        String existingGuestId = preferences.getString(KEY_GUEST_ID, null);
        if (existingGuestId != null && !existingGuestId.trim().isEmpty()) {
            return existingGuestId;
        }

        String newGuestId = GUEST_ID_PREFIX + UUID.randomUUID();
        preferences.edit().putString(KEY_GUEST_ID, newGuestId).apply();
        return newGuestId;
    }

    @Override
    public boolean hasCompletedOnboarding() {
        return preferences.getBoolean(KEY_ONBOARDING_COMPLETED, false);
    }

    @Override
    public void markOnboardingCompleted() {
        preferences.edit().putBoolean(KEY_ONBOARDING_COMPLETED, true).apply();
    }
}
