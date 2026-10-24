package com.rescuefarm;

import static org.junit.Assert.*;
import android.content.Context;
import androidx.room.Room;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.rescuefarm.data.local.database.RescueFarmDatabase;
import com.rescuefarm.data.local.entity.ProductCacheEntity;
import com.rescuefarm.data.local.entity.ProductBatchCacheEntity;
import com.rescuefarm.data.repository.OfflineProductRepository;
import com.rescuefarm.data.repository.ProductRepository;
import com.rescuefarm.domain.model.Product;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Real SQLite persistence + offline repository, isolated from the app's cache and Firebase. */
@RunWith(AndroidJUnit4.class)
public class OfflineRoomEvidenceTest {
    @Test public void productAndStockRemainReadableAfterDatabaseReopensWithoutRemoteRepository() throws Exception {
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        String name="course-audit-"+UUID.randomUUID()+".db";
        RescueFarmDatabase db=null;
        ExecutorService executor=Executors.newSingleThreadExecutor();
        try {
            db=Room.databaseBuilder(context,RescueFarmDatabase.class,name).build();
            ProductCacheEntity p=new ProductCacheEntity("audit-product");
            p.sellerId="audit-seller";p.categoryId="vegetables";p.name="Rau kiem thu offline";
            p.description="Du lieu gia lap trong SQLite rieng";p.originalPrice=20000;p.rescuePrice=12000;
            p.unit="kg";p.origin="Da Lat";p.province="Lam Dong";p.status="ACTIVE";
            p.imageUrlsSerialized="";p.cachedAtEpochMillis=System.currentTimeMillis();
            db.catalogCacheDao().replaceProduct(p);
            ProductBatchCacheEntity batch=new ProductBatchCacheEntity("audit-batch");
            batch.productId=p.id;batch.initialQuantity=10;batch.availableQuantity=10;batch.status="AVAILABLE";
            batch.harvestAtEpochMillis=System.currentTimeMillis()-86400000L;
            batch.expiresAtEpochMillis=System.currentTimeMillis()+86400000L;
            batch.cachedAtEpochMillis=System.currentTimeMillis();
            db.catalogCacheDao().replaceBatch(batch);
            db.close();
            db=Room.databaseBuilder(context,RescueFarmDatabase.class,name).build();
            assertEquals(10,db.catalogCacheDao().findBatches(p.id).get(0).availableQuantity,0);
            OfflineProductRepository repository=new OfflineProductRepository(db.catalogCacheDao(),executor);
            BlockingQueue<Object> result=new LinkedBlockingQueue<>();
            repository.getProduct(p.id,new ProductRepository.ProductCallback(){
                public void onSuccess(Product product){result.add(product);}
                public void onError(ProductRepository.ErrorCode code,String message){result.add(message);}
            });
            Object value=result.poll(10,TimeUnit.SECONDS);
            assertTrue(String.valueOf(value),value instanceof Product);
            assertEquals(p.name,((Product)value).getName());
            assertEquals(12000,((Product)value).getRescuePrice(),0);
        } finally {
            executor.shutdown();executor.awaitTermination(10,TimeUnit.SECONDS);
            if(db!=null)db.close();context.deleteDatabase(name);
        }
    }
}
