package com.rescuefarm.data.local.database;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.annotation.NonNull;

import com.rescuefarm.data.local.dao.CampaignCacheDao;
import com.rescuefarm.data.local.dao.BannerCacheDao;
import com.rescuefarm.data.local.dao.CatalogCacheDao;
import com.rescuefarm.data.local.dao.GuestCartDao;
import com.rescuefarm.data.local.dao.PostCacheDao;
import com.rescuefarm.data.local.dao.CacheMetadataDao;
import com.rescuefarm.data.local.dao.OrderCacheDao;
import com.rescuefarm.data.local.entity.CampaignCacheEntity;
import com.rescuefarm.data.local.entity.BannerCacheEntity;
import com.rescuefarm.data.local.entity.CategoryCacheEntity;
import com.rescuefarm.data.local.entity.GuestCartItemEntity;
import com.rescuefarm.data.local.entity.PostCacheEntity;
import com.rescuefarm.data.local.entity.ProductBatchCacheEntity;
import com.rescuefarm.data.local.entity.ProductCacheEntity;
import com.rescuefarm.data.local.entity.CacheMetadataEntity;
import com.rescuefarm.data.local.entity.OrderCacheEntity;

@Database(
        entities = {
                CategoryCacheEntity.class,
                ProductCacheEntity.class,
                ProductBatchCacheEntity.class,
                CampaignCacheEntity.class,
                PostCacheEntity.class,
                GuestCartItemEntity.class,
                BannerCacheEntity.class,
                CacheMetadataEntity.class,
                OrderCacheEntity.class
        },
        version = 9,
        exportSchema = true
)
public abstract class RescueFarmDatabase extends RoomDatabase {
    private static final String DATABASE_NAME = "rescue_farm.db";
    private static volatile RescueFarmDatabase instance;
    private static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE product_cache ADD COLUMN description TEXT");
            database.execSQL("ALTER TABLE product_cache ADD COLUMN origin TEXT");
            database.execSQL("ALTER TABLE product_cache ADD COLUMN province TEXT");
            database.execSQL("ALTER TABLE product_cache ADD COLUMN imageUrlsSerialized TEXT");
            database.execSQL("ALTER TABLE product_cache ADD COLUMN averageRating REAL NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE product_cache ADD COLUMN reviewCount INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE product_batch_cache ADD COLUMN inventoryVersion INTEGER NOT NULL DEFAULT 0");
        }
    };
    private static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE campaign_cache ADD COLUMN description TEXT");
            database.execSQL("ALTER TABLE campaign_cache ADD COLUMN batchTargetsSerialized TEXT");
            database.execSQL("ALTER TABLE campaign_cache ADD COLUMN startAtEpochMillis INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE campaign_cache ADD COLUMN endAtEpochMillis INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE campaign_cache ADD COLUMN latitude REAL NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE campaign_cache ADD COLUMN longitude REAL NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE campaign_cache ADD COLUMN locationName TEXT");
        }
    };
    private static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS banner_cache (id TEXT NOT NULL, title TEXT, imageUrl TEXT, campaignId TEXT, displayOrder INTEGER NOT NULL, startAtEpochMillis INTEGER NOT NULL, endAtEpochMillis INTEGER NOT NULL, active INTEGER NOT NULL, cachedAtEpochMillis INTEGER NOT NULL, PRIMARY KEY(id))");
        }
    };
    private static final Migration MIGRATION_4_5 = new Migration(4, 5) {
        @Override public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE post_cache ADD COLUMN imageUrlsSerialized TEXT");
            database.execSQL("ALTER TABLE post_cache ADD COLUMN linkedProductIdsSerialized TEXT");
            database.execSQL("ALTER TABLE post_cache ADD COLUMN viewCount INTEGER NOT NULL DEFAULT 0");
        }
    };
    public static final Migration MIGRATION_5_6 = new Migration(5, 6) {
        @Override public void migrate(@NonNull SupportSQLiteDatabase database) {
            for (String statement : CacheMigrationContract.version6Statements()) database.execSQL(statement);
        }
    };
    private static final Migration MIGRATION_6_7 = new Migration(6, 7) {
        @Override public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE campaign_cache ADD COLUMN imageUrlsSerialized TEXT");
            database.execSQL("ALTER TABLE campaign_cache ADD COLUMN imagePublicIdsSerialized TEXT");
            database.execSQL("ALTER TABLE campaign_cache ADD COLUMN viewCount INTEGER NOT NULL DEFAULT 0");
        }
    };
    private static final Migration MIGRATION_7_8 = new Migration(7, 8) {
        @Override public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE campaign_cache ADD COLUMN reservedByBatchSerialized TEXT");
            database.execSQL("ALTER TABLE campaign_cache ADD COLUMN rescuedByBatchSerialized TEXT");
            database.execSQL("ALTER TABLE campaign_cache ADD COLUMN allocationVersion INTEGER NOT NULL DEFAULT 0");
        }
    };

    private static final Migration MIGRATION_8_9 = new Migration(8, 9) {
        @Override public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE campaign_cache ADD COLUMN quantityUnit TEXT");
            database.execSQL("ALTER TABLE campaign_cache ADD COLUMN unitStatus TEXT");
        }
    };

    public static RescueFarmDatabase getInstance(Context context) {
        if (instance == null) {
            synchronized (RescueFarmDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(
                            context.getApplicationContext(),
                            RescueFarmDatabase.class,
                            DATABASE_NAME
                    ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4,
                            MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9).build();
                }
            }
        }
        return instance;
    }

    public abstract CatalogCacheDao catalogCacheDao();
    public abstract CampaignCacheDao campaignCacheDao();
    public abstract BannerCacheDao bannerCacheDao();
    public abstract PostCacheDao postCacheDao();
    public abstract GuestCartDao guestCartDao();
    public abstract CacheMetadataDao cacheMetadataDao();
    public abstract OrderCacheDao orderCacheDao();
}
