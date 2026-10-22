package com.rescuefarm.service.cart;
import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.*;
import com.rescuefarm.data.repository.CartRepository;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
public final class CartMigrationWorker extends Worker {
    public CartMigrationWorker(@NonNull Context context, @NonNull WorkerParameters parameters) { super(context, parameters); }
    public static void schedule(Context context, String guest, String user) {
        WorkManager.getInstance(context).enqueueUniqueWork("cart-migrate-" + guest + "-" + user, ExistingWorkPolicy.KEEP,
            new OneTimeWorkRequest.Builder(CartMigrationWorker.class).setInputData(new Data.Builder()
                .putString("guest", guest).putString("user", user).build())
                .setConstraints(new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build());
    }
    public static void scheduleRecovery(Context context) {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork("cart-migration-outbox-recovery", ExistingPeriodicWorkPolicy.KEEP,
            new PeriodicWorkRequest.Builder(CartMigrationWorker.class, 15, TimeUnit.MINUTES)
                .setInputData(new Data.Builder().putBoolean("recover", true).build())
                .setConstraints(new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build());
    }
    @NonNull @Override public Result doWork() {
        String user = getInputData().getString("user");
        com.google.firebase.auth.FirebaseUser active = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
        if (getInputData().getBoolean("recover", false)) {
            if (active != null) for (com.rescuefarm.data.local.entity.CartMigrationEntity job : com.rescuefarm.data.local.database.RescueFarmDatabase
                    .getInstance(getApplicationContext()).cartMigrationDao().forUser(active.getUid()))
                schedule(getApplicationContext(), job.guestId, job.userId);
            return Result.success();
        }
        if (active == null || !active.getUid().equals(user)) return Result.success();
        CountDownLatch finished = new CountDownLatch(1); AtomicBoolean success = new AtomicBoolean();
        new CartMigrationService(getApplicationContext()).migrateGuestCartToUser(getInputData().getString("guest"), user,
            new CartRepository.ActionCallback() {
                public void onSuccess() { success.set(true); finished.countDown(); }
                public void onError(CartRepository.ErrorCode code, String message) { finished.countDown(); }
            });
        try { return finished.await(150, TimeUnit.SECONDS) && success.get() ? Result.success() : Result.retry(); }
        catch (InterruptedException error) { Thread.currentThread().interrupt(); return Result.retry(); }
    }
}
