package com.rescuefarm.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.rescuefarm.data.local.entity.GuestCartItemEntity;

import java.util.List;

@Dao
public interface GuestCartDao {
    @Query("SELECT * FROM guest_cart_items WHERE guestId = :guestId ORDER BY updatedAtEpochMillis DESC")
    LiveData<List<GuestCartItemEntity>> observeItems(String guestId);

    @Query("SELECT * FROM guest_cart_items WHERE guestId = :guestId")
    List<GuestCartItemEntity> getItems(String guestId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(GuestCartItemEntity item);

    @Query("SELECT * FROM guest_cart_items WHERE guestId = :guestId AND id = :itemId LIMIT 1")
    GuestCartItemEntity findItem(String guestId, String itemId);

    @Delete
    void delete(GuestCartItemEntity item);

    @Query("DELETE FROM guest_cart_items WHERE guestId = :guestId AND id = :itemId")
    void deleteById(String guestId, String itemId);

    @Query("DELETE FROM guest_cart_items WHERE guestId = :guestId")
    void clear(String guestId);
}
