package com.rescuefarm.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import androidx.lifecycle.LiveData;

import com.rescuefarm.data.local.dao.GuestCartDao;
import com.rescuefarm.data.local.entity.GuestCartItemEntity;
import com.rescuefarm.data.repository.CartRepository;
import com.rescuefarm.service.cart.CartMigrationService;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

public class CartMigrationServiceTest {

    @Test
    public void emptyGuestId_completesImmediatelyWithoutError() {
        FakeGuestCartDao dao = new FakeGuestCartDao();
        CartMigrationService service = new CartMigrationService(dao, null, Runnable::run);

        AtomicBoolean success = new AtomicBoolean(false);
        service.migrateGuestCartToUser("", "user-123", new CartRepository.ActionCallback() {
            @Override
            public void onSuccess() {
                success.set(true);
            }

            @Override
            public void onError(CartRepository.ErrorCode error, String message) {
            }
        });

        assertTrue(success.get());
        assertTrue(!dao.clearCalled);
    }

    @Test
    public void emptyCart_completesImmediatelyWithoutClearing() {
        FakeGuestCartDao dao = new FakeGuestCartDao();
        CartMigrationService service = new CartMigrationService(dao, null, Runnable::run);

        AtomicBoolean success = new AtomicBoolean(false);
        service.migrateGuestCartToUser("guest-123", "user-123", new CartRepository.ActionCallback() {
            @Override
            public void onSuccess() {
                success.set(true);
            }

            @Override
            public void onError(CartRepository.ErrorCode error, String message) {
            }
        });

        assertTrue(success.get());
        assertTrue(!dao.clearCalled);
    }

    static class FakeGuestCartDao implements GuestCartDao {
        List<GuestCartItemEntity> items = new ArrayList<>();
        boolean clearCalled = false;

        @Override
        public LiveData<List<GuestCartItemEntity>> observeItems(String guestId) {
            return null;
        }

        @Override
        public List<GuestCartItemEntity> getItems(String guestId) {
            return items;
        }

        @Override
        public void upsert(GuestCartItemEntity item) {
            items.add(item);
        }

        @Override
        public GuestCartItemEntity findItem(String guestId, String itemId) {
            return null;
        }

        @Override
        public void delete(GuestCartItemEntity item) {
        }

        @Override
        public void deleteById(String guestId, String itemId) {
        }

        @Override
        public void clear(String guestId) {
            clearCalled = true;
            items.clear();
        }
    }
}
