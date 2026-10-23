package com.rescuefarm.service.cart;

import android.content.Context;
import androidx.annotation.NonNull;
import com.google.firebase.auth.FirebaseAuth;
import com.rescuefarm.data.local.dao.GuestCartDao;
import com.rescuefarm.data.local.database.RescueFarmDatabase;
import com.rescuefarm.data.local.entity.CartMigrationEntity;
import com.rescuefarm.data.local.entity.GuestCartItemEntity;
import com.rescuefarm.data.repository.CartRepository;
import com.rescuefarm.service.backend.RenderBackendClient;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/** Durable snapshot + server receipt: safe across process death, retries and concurrent factories. */
public class CartMigrationService {
    private static final Executor IO = Executors.newFixedThreadPool(2);
    private final GuestCartDao guestCartDao;
    private final Context context;
    private final Executor executor;
    private final RescueFarmDatabase database;
    public CartMigrationService(@NonNull Context context) {
        this.context = context.getApplicationContext();
        database = RescueFarmDatabase.getInstance(context); guestCartDao = database.guestCartDao();
        executor = IO;
        CartMigrationWorker.scheduleRecovery(this.context);
    }
    public CartMigrationService(GuestCartDao dao, Context context, Executor executor) {
        this.context = context; this.guestCartDao = dao; this.executor = executor;
        this.database = context == null ? null : RescueFarmDatabase.getInstance(context);
    }
    public void migrateGuestCartToUser(String guestId, String targetUserId, CartRepository.ActionCallback callback) {
        if (guestId == null || guestId.trim().isEmpty() || targetUserId == null || targetUserId.trim().isEmpty()) {
            if (callback != null) callback.onSuccess(); return;
        }
        executor.execute(() -> {
            try {
                if (database == null) {
                    if (guestCartDao.getItems(guestId).isEmpty()) { if (callback != null) callback.onSuccess(); return; }
                    throw new IllegalStateException("Durable database required");
                }
                if (FirebaseAuth.getInstance().getCurrentUser() == null
                        || !targetUserId.equals(FirebaseAuth.getInstance().getCurrentUser().getUid())) {
                    failure(callback, "Giỏ Guest được giữ cho tài khoản trước."); return;
                }
                com.google.firebase.firestore.DocumentSnapshot profile = com.google.android.gms.tasks.Tasks.await(
                    com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("users").document(targetUserId)
                        .get(com.google.firebase.firestore.Source.SERVER), 15, java.util.concurrent.TimeUnit.SECONDS);
                if (!"CUSTOMER".equals(profile.getString("role"))) { if (callback != null) callback.onSuccess(); return; }
                CartMigrationEntity job = database.runInTransaction(() -> {
                    CartMigrationEntity pending = database.cartMigrationDao().pending(guestId, targetUserId);
                    if (pending != null) return pending;
                    List<GuestCartItemEntity> items = guestCartDao.getItems(guestId);
                    if (items == null || items.isEmpty()) return null;
                    CartMigrationEntity next = new CartMigrationEntity(UUID.randomUUID().toString());
                    JSONArray rows = new JSONArray();
                    for (GuestCartItemEntity item : items) rows.put(new JSONObject().put("id", item.id)
                        .put("productId", item.productId).put("batchId", item.batchId).put("sellerId", item.sellerId)
                        .put("quantity", item.quantity).put("unitPrice", item.unitPrice).put("selected", item.selected));
                    next.userId = targetUserId; next.guestId = guestId;
                    next.payload = new JSONObject().put("requestId", next.id).put("targetUserId", targetUserId).put("items", rows).toString();
                    database.cartMigrationDao().insert(next);
                    guestCartDao.clear(guestId); return next;
                });
                if (job == null) { if (callback != null) callback.onSuccess(); return; }
                CartMigrationWorker.schedule(context, guestId, targetUserId);
                if (FirebaseAuth.getInstance().getCurrentUser() == null
                        || !targetUserId.equals(FirebaseAuth.getInstance().getCurrentUser().getUid())) {
                    failure(callback, "Giỏ Guest được giữ trong hàng đợi của tài khoản trước."); return;
                }
                RenderBackendClient.post("/cart/migrate-guest", new JSONObject(job.payload), new RenderBackendClient.Callback() {
                    @Override public void onSuccess(JSONObject body) {
                        executor.execute(() -> {
                            try {
                                database.cartMigrationDao().complete(job.id);
                                migrateGuestCartToUser(guestId, targetUserId, callback);
                            } catch (Exception error) { failure(callback, "Dữ liệu vẫn trong hàng đợi; lần thử lại không cộng trùng giỏ."); }
                        });
                    }
                    @Override public void onError(String message) {
                        CartMigrationWorker.schedule(context, guestId, targetUserId);
                        failure(callback, message + " Giỏ Guest vẫn được giữ để thử lại.");
                    }
                });
            } catch (Exception error) {
                if (context != null) CartMigrationWorker.schedule(context, guestId, targetUserId);
                failure(callback, "Chưa chuyển được giỏ; dữ liệu vẫn được giữ trên máy.");
            }
        });
    }
    private static void failure(CartRepository.ActionCallback callback, String message) {
        if (callback != null) callback.onError(CartRepository.ErrorCode.UNKNOWN, message);
    }
}
