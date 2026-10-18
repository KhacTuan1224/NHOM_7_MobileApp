package com.rescuefarm.service.sync;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.rescuefarm.data.local.CacheMetadataStore;
import com.rescuefarm.data.local.database.RescueFarmDatabase;
import com.rescuefarm.data.repository.CampaignRepository;
import com.rescuefarm.data.repository.CampaignRepositoryFactory;
import com.rescuefarm.data.repository.OrderRepository;
import com.rescuefarm.data.repository.OrderRepositoryFactory;
import com.rescuefarm.data.repository.ProductRepository;
import com.rescuefarm.data.repository.ProductRepositoryFactory;
import com.rescuefarm.data.remote.firebase.FirebaseConfiguration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ReadCacheSyncWorker extends Worker {
    private static final long WAIT_SECONDS = 45L;
    private final Context context;
    private final CacheMetadataStore metadata;

    public ReadCacheSyncWorker(@NonNull Context context, @NonNull WorkerParameters parameters) {
        super(context, parameters); this.context = context.getApplicationContext();
        metadata = new CacheMetadataStore(RescueFarmDatabase.getInstance(context).cacheMetadataDao(),
                new CacheSyncPolicy());
    }

    @NonNull @Override public Result doWork() {
        if (!FirebaseConfiguration.isConfigured(context)) return Result.success();
        boolean force = getInputData().getBoolean("force", false);
        ProductRepository product = ProductRepositoryFactory.create(context);
        CampaignRepository campaign = CampaignRepositoryFactory.create(context);
        OrderRepository order = OrderRepositoryFactory.create(context);
        try {
            boolean success = syncCatalog(product, force)
                    & syncCampaigns(campaign, force)
                    & syncBanners(campaign, force)
                    & syncOrders(order, force);
            RescueFarmDatabase.getInstance(context).orderCacheDao().deleteOlderThan(
                    System.currentTimeMillis() - CacheSyncPolicy.HARD_EXPIRY_MILLIS);
            return success ? Result.success() : Result.retry();
        } finally {
            product.close(); campaign.close(); order.close();
        }
    }

    private boolean syncCatalog(ProductRepository repository, boolean force) {
        String scope = CacheSyncPolicy.CATALOG; if (!begin(scope, force)) return true;
        CountDownLatch latch = new CountDownLatch(1); AtomicBoolean ok = new AtomicBoolean();
        repository.refreshCatalog(new ProductRepository.ActionCallback() {
            public void onSuccess(){ok.set(true);latch.countDown();}
            public void onError(ProductRepository.ErrorCode error,String message){latch.countDown();}
        }); return finish(scope, latch, ok);
    }

    private boolean syncCampaigns(CampaignRepository repository, boolean force) {
        String scope = CacheSyncPolicy.CAMPAIGNS; if (!begin(scope, force)) return true;
        CountDownLatch latch = new CountDownLatch(1); AtomicBoolean ok = new AtomicBoolean();
        repository.refreshActiveCampaigns(new CampaignRepository.ActionCallback() {
            public void onSuccess(){ok.set(true);latch.countDown();}
            public void onError(CampaignRepository.ErrorCode error,String message){latch.countDown();}
        }); return finish(scope, latch, ok);
    }

    private boolean syncBanners(CampaignRepository repository, boolean force) {
        String scope = CacheSyncPolicy.BANNERS; if (!begin(scope, force)) return true;
        CountDownLatch latch = new CountDownLatch(1); AtomicBoolean ok = new AtomicBoolean();
        repository.refreshBanners(new CampaignRepository.ActionCallback() {
            public void onSuccess(){ok.set(true);latch.countDown();}
            public void onError(CampaignRepository.ErrorCode error,String message){latch.countDown();}
        }); return finish(scope, latch, ok);
    }



    private boolean syncOrders(OrderRepository repository, boolean force) {
        String uid = FirebaseAuth.getInstance().getCurrentUser() == null ? ""
                : FirebaseAuth.getInstance().getCurrentUser().getUid();
        if (uid.isEmpty()) return true;
        String scope = CacheSyncPolicy.ORDERS + ":" + uid; if (!begin(scope, force)) return true;
        CountDownLatch latch = new CountDownLatch(1); AtomicBoolean ok = new AtomicBoolean();
        try {
            DocumentSnapshot user = Tasks.await(FirebaseFirestore.getInstance().collection("users")
                    .document(uid).get(), WAIT_SECONDS, TimeUnit.SECONDS);
            OrderRepository.ActionCallback callback = new OrderRepository.ActionCallback() {
                public void onSuccess(){ok.set(true);latch.countDown();}
                public void onError(OrderRepository.ErrorCode error,String message){latch.countDown();}
            };
            if ("SELLER".equals(user.getString("role"))) repository.refreshSellerOrders(uid, callback);
            else if ("CUSTOMER".equals(user.getString("role"))) repository.refreshOrders(uid, callback);
            else { ok.set(true); latch.countDown(); }
        } catch (Exception error) { latch.countDown(); }
        return finish(scope, latch, ok);
    }

    private boolean begin(String scope, boolean force) {
        long now = System.currentTimeMillis();
        if (!metadata.shouldRefresh(scope, now, force)) return false;
        metadata.recordAttempt(scope, now); return true;
    }

    private boolean finish(String scope, CountDownLatch latch, AtomicBoolean success) {
        boolean completed;
        try { completed = latch.await(WAIT_SECONDS, TimeUnit.SECONDS); }
        catch (InterruptedException error) { Thread.currentThread().interrupt(); completed = false; }
        long now = System.currentTimeMillis();
        if (completed && success.get()) { metadata.recordSuccess(scope, now); return true; }
        metadata.recordFailure(scope, now, completed ? "SYNC_FAILED" : "SYNC_TIMEOUT"); return false;
    }
}
