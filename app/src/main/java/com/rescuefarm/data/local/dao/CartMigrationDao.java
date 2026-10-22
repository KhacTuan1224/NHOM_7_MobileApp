package com.rescuefarm.data.local.dao;
import androidx.room.*;
import com.rescuefarm.data.local.entity.CartMigrationEntity;
@Dao
public interface CartMigrationDao {
    @Query("SELECT * FROM cart_migration_outbox WHERE userId = :userId")
    java.util.List<CartMigrationEntity> forUser(String userId);
    @Query("SELECT * FROM cart_migration_outbox WHERE guestId = :guestId AND userId = :userId LIMIT 1")
    CartMigrationEntity pending(String guestId, String userId);
    @Insert(onConflict = OnConflictStrategy.ABORT) void insert(CartMigrationEntity job);
    @Query("DELETE FROM cart_migration_outbox WHERE id = :id") void complete(String id);
}
