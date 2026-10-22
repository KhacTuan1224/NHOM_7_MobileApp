package com.rescuefarm.data.local.entity;
import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
@Entity(tableName = "cart_migration_outbox")
public class CartMigrationEntity {
    @PrimaryKey @NonNull public String id;
    public String guestId;
    public String userId;
    public String payload;
    public CartMigrationEntity(@NonNull String id) { this.id = id; }
}
