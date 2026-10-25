package com.rescuefarm;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.location.Location;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import androidx.navigation.Navigation;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.rescuefarm.service.location.MobileLocationService;
import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;

/** Runs only against the isolated APK + Auth/Firestore + HTTP demo harness. */
public class RescueMapEvidenceTest {
    private ActivityScenario<MainActivity> scenario;
    private Context context;
    private WebView find(View v){if(v instanceof WebView)return(WebView)v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){WebView w=find(((ViewGroup)v).getChildAt(i));if(w!=null)return w;}return null;}
    private String js(String code)throws Exception{
        AtomicReference<String> value=new AtomicReference<>();CountDownLatch latch=new CountDownLatch(1);
        scenario.onActivity(a->{WebView map=find(a.findViewById(R.id.homeRescueMap));if(map==null){latch.countDown();return;}map.evaluateJavascript(code,s->{value.set(s);latch.countDown();});});
        assertTrue(latch.await(5,TimeUnit.SECONDS));return value.get();
    }
    private void awaitJs(String condition)throws Exception{long until=System.currentTimeMillis()+45000;while(System.currentTimeMillis()<until){if("true".equals(js("Boolean("+condition+")")))return;Thread.sleep(500);}fail("Map condition timed out: "+condition);}
    private void fix(double latitude,double longitude)throws Exception{
        Location l=new Location("gps");l.setLatitude(latitude);l.setLongitude(longitude);l.setAccuracy(5);l.setTime(System.currentTimeMillis());l.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
        Tasks.await(LocationServices.getFusedLocationProviderClient(context).setMockLocation(l),10,TimeUnit.SECONDS);
    }
    private void shot(String name)throws Exception{
        Bitmap b=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
        File folder=new File(context.getExternalFilesDir(null),"map-evidence");folder.mkdirs();
        try(FileOutputStream out=new FileOutputStream(new File(folder,name+".png"))){b.compress(Bitmap.CompressFormat.PNG,100,out);}b.recycle();
    }
    @Test public void actualGpsServiceMovesHomeMarkerAndStopRemovesIt()throws Exception{
        context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        assumeTrue(context.getPackageName().endsWith(".reportdemo"));
        assertEquals("demo-rescuefarm",FirebaseApp.getInstance().getOptions().getProjectId());
        assertEquals("http://10.0.2.2:3008",BuildConfig.RENDER_BACKEND_URL);
        context.getSharedPreferences("report-demo",Context.MODE_PRIVATE).edit().putBoolean("offline",false).commit();
        Tasks.await(FirebaseFirestore.getInstance().enableNetwork(),10,TimeUnit.SECONDS);
        Tasks.await(FirebaseAuth.getInstance().signInWithEmailAndPassword("seller@map.example.test","demo-map-only-password"),15,TimeUnit.SECONDS);
        try {
            scenario=ActivityScenario.launch(MainActivity.class);Thread.sleep(2500);
            scenario.onActivity(a->Navigation.findNavController(a,R.id.main).navigate(R.id.guestHomeFragment));
            awaitJs("window.rescueMapReady && markers.size===16");
            // > 12 proves the map is independent of Home's first campaign page.
            assertEquals("16",js("markers.size"));
            Tasks.await(LocationServices.getFusedLocationProviderClient(context).setMockMode(true),10,TimeUnit.SECONDS);
            scenario.onActivity(a->MobileLocationService.start(a,"map-demo-mobile",System.currentTimeMillis()+3600000));
            Thread.sleep(2000);fix(10.781,106.701);
            awaitJs("markers.has('map-demo-mobile') && Math.abs(markers.get('map-demo-mobile').getLatLng().lat-10.781)<0.00001");
            // Home preview opens the large map on tap; popups belong to the large map.
            awaitJs("!markers.get('map-demo-mobile').getPopup()");
            shot("home-mobile-gps-A");
            Thread.sleep(11000);fix(10.786,106.706);
            awaitJs("Math.abs(markers.get('map-demo-mobile').getLatLng().lat-10.786)<0.00001");
            shot("home-mobile-gps-B");
            scenario.onActivity(a->a.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE));
            Thread.sleep(2500);awaitJs("markers.has('map-demo-mobile')");shot("home-map-landscape");
            scenario.onActivity(a->MobileLocationService.stop(a));
            awaitJs("!markers.has('map-demo-mobile') && markers.size===16");
            assertFalse(Tasks.await(FirebaseFirestore.getInstance().collection("campaigns").document("map-demo-mobile").get(),10,TimeUnit.SECONDS).getBoolean("locationSharingEnabled"));
        } finally {
            if(MobileLocationService.isTracking("map-demo-mobile"))MobileLocationService.stop(context);
            Tasks.await(LocationServices.getFusedLocationProviderClient(context).setMockMode(false),10,TimeUnit.SECONDS);
            if(scenario!=null)scenario.close();FirebaseAuth.getInstance().signOut();
        }
    }
}
