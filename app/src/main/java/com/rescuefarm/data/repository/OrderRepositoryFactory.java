package com.rescuefarm.data.repository;
import android.content.Context;
import com.rescuefarm.data.local.database.RescueFarmDatabase;
import com.rescuefarm.data.remote.firebase.FirebaseConfiguration;
import com.rescuefarm.data.remote.firebase.FirebaseOrderRepository;
import com.rescuefarm.service.network.AndroidNetworkStatusProvider;
import java.util.concurrent.Executors;
public final class OrderRepositoryFactory { private OrderRepositoryFactory(){} public static OrderRepository create(Context context){
    RescueFarmDatabase database=RescueFarmDatabase.getInstance(context);
    if(!FirebaseConfiguration.isConfigured(context))return new OfflineOrderRepository(database.orderCacheDao(),Executors.newSingleThreadExecutor());
    return new FirebaseOrderRepository(database,Executors.newSingleThreadExecutor(),new AndroidNetworkStatusProvider(context),
        new com.rescuefarm.data.local.preferences.SharedPreferencesGuestSessionRepository(context));} }
