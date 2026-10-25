package com.rescuefarm.data.local.preferences;

import android.content.Context;
import android.content.SharedPreferences;
import com.google.firebase.auth.FirebaseAuth;
import com.rescuefarm.data.remote.firebase.FirebaseConfiguration;
import com.rescuefarm.service.location.ScopedHomeLocation;
import java.util.HashSet;
import java.util.Set;

/** One process-owned auth listener; no Firestore/HTTP reads for location selection. */
public final class HomeLocationStore {
    private static HomeLocationStore instance;
    public static synchronized HomeLocationStore get(Context context){
        if(instance==null)instance=new HomeLocationStore(context.getApplicationContext());return instance;
    }
    private final FirebaseAuth auth;
    private final ScopedHomeLocation scoped;
    private final Set<Runnable> listeners=new HashSet<>();
    private HomeLocationStore(Context context){
        SharedPreferences prefs=context.getSharedPreferences("rescuefarm_home_location",Context.MODE_PRIVATE);
        auth=FirebaseConfiguration.isConfigured(context)?FirebaseAuth.getInstance():null;
        scoped=new ScopedHomeLocation(new ScopedHomeLocation.Storage(){
            public String get(String key){return prefs.getString(key,null);}
            public void put(String key,String value){SharedPreferences.Editor edit=prefs.edit();if(value==null)edit.remove(key);else edit.putString(key,value);edit.apply();}
        },uid());
        if(auth!=null)auth.addAuthStateListener(unused->syncOwner());
    }
    private String uid(){return auth==null||auth.getCurrentUser()==null?null:auth.getCurrentUser().getUid();}
    private void syncOwner(){if(scoped.changeOwner(uid()))for(Runnable listener:new HashSet<>(listeners))listener.run();}
    public ScopedHomeLocation.Selection read(){syncOwner();return scoped.read();}
    public void save(double lat,double lng,String label){syncOwner();scoped.save(new ScopedHomeLocation.Selection(lat,lng,label));}
    public void clearAccount(String uid){
        // Invalidate pending GPS/geocoder results even if the same user logs
        // back in before Firebase's asynchronous auth listener runs.
        if(scoped.clearAccount(uid))for(Runnable listener:new HashSet<>(listeners))listener.run();
    }
    public void addListener(Runnable listener){listeners.add(listener);}
    public void removeListener(Runnable listener){listeners.remove(listener);}
}
