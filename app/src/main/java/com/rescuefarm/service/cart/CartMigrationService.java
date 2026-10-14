package com.rescuefarm.service.cart;

import android.content.Context;
import androidx.annotation.NonNull;
import com.rescuefarm.data.local.dao.GuestCartDao;
import com.rescuefarm.data.local.database.RescueFarmDatabase;
import com.rescuefarm.data.local.entity.GuestCartItemEntity;
import com.rescuefarm.data.local.mapper.GuestCartMapper;
import com.rescuefarm.data.remote.firebase.FirebaseCartRepository;
import com.rescuefarm.data.repository.CartRepository;
import com.rescuefarm.domain.model.CartItem;
import com.rescuefarm.service.network.AndroidNetworkStatusProvider;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class CartMigrationService {
    private final GuestCartDao guestCartDao;
    private final Context context;
    private final Executor executor;

    public CartMigrationService(@NonNull Context context) {
        this.context = context.getApplicationContext();
        this.guestCartDao = RescueFarmDatabase.getInstance(context).guestCartDao();
        this.executor = Executors.newSingleThreadExecutor();
    }

    public CartMigrationService(GuestCartDao guestCartDao, Context context, Executor executor) {
        this.guestCartDao = guestCartDao;
        this.context = context;
        this.executor = executor;
    }

    public void migrateGuestCartToUser(String guestId, String targetUserId, CartRepository.ActionCallback callback) {
        if (guestId == null || guestId.trim().isEmpty() || targetUserId == null || targetUserId.trim().isEmpty()) {
            if (callback != null) callback.onSuccess();
            return;
        }

        executor.execute(() -> {
            try {
                List<GuestCartItemEntity> entities = guestCartDao.getItems(guestId);
                if (entities == null || entities.isEmpty()) {
                    safeLog("No guest items to migrate for " + guestId);
                    if (callback != null) callback.onSuccess();
                    return;
                }

                safeLog("Found " + entities.size() + " guest items for " + guestId + ", migrating to " + targetUserId);
                List<CartItem> domainItems = GuestCartMapper.toCart(guestId, entities).getItems();

                FirebaseCartRepository firebaseCart = new FirebaseCartRepository(
                        targetUserId, new AndroidNetworkStatusProvider(context));

                firebaseCart.mergeItems(domainItems, new CartRepository.ActionCallback() {
                    @Override
                    public void onSuccess() {
                        executor.execute(() -> {
                            guestCartDao.clear(guestId);
                            safeLog("Migration success: merged " + domainItems.size() + " items and cleared guest cart " + guestId);
                            if (callback != null) callback.onSuccess();
                        });
                    }

                    @Override
                    public void onError(CartRepository.ErrorCode error, String message) {
                        safeLog("Migration failed: " + error + " - " + message);
                        if (callback != null) callback.onError(error, message);
                    }
                });
            } catch (Exception e) {
                safeLog("Migration exception: " + e.getMessage());
                if (callback != null) {
                    callback.onError(CartRepository.ErrorCode.UNKNOWN, e.getMessage());
                }
            }
        });
    }

    private static void safeLog(String msg) {
        try {
            android.util.Log.d("CartMigration", msg);
        } catch (Throwable ignored) {
            // JVM unit test environment
        }
    }
}
