package com.rescuefarm.service.notification;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.*;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.*;
import com.google.firebase.messaging.FirebaseMessaging;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Retries token registration after network/process failures without requiring the inbox screen. */
public final class FcmDeviceSyncWorker extends Worker {
    public FcmDeviceSyncWorker(@NonNull Context context, @NonNull WorkerParameters parameters) { super(context, parameters); }
    private static Constraints network() { return new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(); }
    public static void schedule(Context context) {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork("rescuefarm-fcm-device-periodic", ExistingPeriodicWorkPolicy.KEEP,
            new PeriodicWorkRequest.Builder(FcmDeviceSyncWorker.class, 12, TimeUnit.HOURS).setConstraints(network()).build());
        refreshNow(context);
    }
    public static void refreshNow(Context context) {
        WorkManager.getInstance(context).enqueueUniqueWork("rescuefarm-fcm-device-refresh", ExistingWorkPolicy.REPLACE,
            new OneTimeWorkRequest.Builder(FcmDeviceSyncWorker.class).setConstraints(network())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build());
    }
    @NonNull @Override public Result doWork() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return Result.success();
        String uid = user.getUid();
        try {
            FirebaseFirestore db = FirebaseFirestore.getInstance();
            DocumentSnapshot profile = Tasks.await(db.collection("users").document(uid).get(Source.SERVER), 30, TimeUnit.SECONDS);
            if (!profile.exists() || !"ACTIVE".equals(profile.getString("status"))) return Result.success();
            String token = Tasks.await(FirebaseMessaging.getInstance().getToken(), 30, TimeUnit.SECONDS);
            FirebaseUser current = FirebaseAuth.getInstance().getCurrentUser();
            if (current == null || !uid.equals(current.getUid()) || isStopped()) return Result.success();
            if (token == null || token.isEmpty()) return Result.retry();
            StringBuilder id = new StringBuilder();
            for (byte value : MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))) id.append(String.format(Locale.ROOT, "%02x", value & 255));
            Map<String, Object> device = new HashMap<>(); device.put("userId", uid); device.put("token", token);
            device.put("platform", "ANDROID"); device.put("updatedAt", FieldValue.serverTimestamp());
            Tasks.await(db.collection("users").document(uid).collection("devices").document(id.toString()).set(device), 30, TimeUnit.SECONDS);
            return Result.success();
        } catch (Exception error) {
            Throwable cause = error.getCause() == null ? error : error.getCause();
            if (cause instanceof FirebaseFirestoreException && ((FirebaseFirestoreException) cause).getCode() == FirebaseFirestoreException.Code.PERMISSION_DENIED)
                return Result.success();
            return Result.retry();
        }
    }
}
