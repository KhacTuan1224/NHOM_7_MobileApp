package com.rescuefarm;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.navigation.Navigation;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import org.junit.Test;

/** Real running Activity screenshots, guarded against the standard app or project. */
public class ReportScreenshotEvidenceTest {
    private ActivityScenario<MainActivity> scenario;
    private Context context;
    private void guard() {
        context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        assumeTrue(context.getPackageName().endsWith(".reportdemo"));
        assertEquals("demo-rescuefarm",FirebaseApp.getInstance().getOptions().getProjectId());
    }
    @Test public void prepareRoomOfflineRestart() throws Exception {
        guard(); Tasks.await(FirebaseFirestore.getInstance().enableNetwork(),10,TimeUnit.SECONDS);
        try { actor("guest");open(R.id.productListFragment,null);settle();
            scenario.onActivity(a->assertTrue(hasText(a.getWindow().getDecorView(),"Cà chua DEMO")));
            assertNotNull(com.rescuefarm.data.local.database.RescueFarmDatabase.getInstance(context).catalogCacheDao().findProduct("report-product-0"));
            context.getSharedPreferences("report-demo",Context.MODE_PRIVATE).edit().putBoolean("offline",true).commit();
        } finally {if(scenario!=null)scenario.close();}
    }
    @Test public void captureRoomOnlyOfflineAfterProcessRestart() throws Exception {
        guard();assertTrue(context.getSharedPreferences("report-demo",Context.MODE_PRIVATE).getBoolean("offline",false));
        assertFalse(FirebaseFirestore.getInstance().getFirestoreSettings().isPersistenceEnabled());
        try { scenario=ActivityScenario.launch(MainActivity.class);settle();open(R.id.productListFragment,null);
            scenario.onActivity(a->assertTrue("Room fallback after new process with Firestore memory empty and disk persistence off",hasText(a.getWindow().getDecorView(),"Cà chua DEMO")));
            shot("catalog-room-offline-restart");
        } finally {if(scenario!=null)scenario.close();context.getSharedPreferences("report-demo",Context.MODE_PRIVATE).edit().putBoolean("offline",false).commit();Tasks.await(FirebaseFirestore.getInstance().enableNetwork(),10,TimeUnit.SECONDS);}
    }
    @Test public void captureFundStage() throws Exception {
        guard(); Tasks.await(FirebaseFirestore.getInstance().enableNetwork(),10,TimeUnit.SECONDS);
        String role=InstrumentationRegistry.getArguments().getString("reportActor","seller");
        String stage=InstrumentationRegistry.getArguments().getString("reportStage","reserved");
        try {actor(role);open(R.id.rescueFundFragment,null);shot("fund-"+stage+"-"+role+"-top");
            open(R.id.rescueFundDeliveriesFragment,null);
            scenario.onActivity(a->((androidx.core.widget.NestedScrollView)a.findViewById(R.id.fundScroll)).fullScroll(View.FOCUS_DOWN));settle();shot("fund-"+stage+"-"+role+"-jobs");
        } finally {if(scenario!=null)scenario.close();FirebaseAuth.getInstance().signOut();}
    }
    private void settle() throws Exception { Thread.sleep(2400); InstrumentationRegistry.getInstrumentation().waitForIdleSync(); }
    private void open(int destination, Bundle args) throws Exception {
        scenario.onActivity(a -> Navigation.findNavController(a,R.id.main).navigate(destination,args)); settle();
        scenario.onActivity(a -> assertEquals(destination,Navigation.findNavController(a,R.id.main).getCurrentDestination().getId()));
    }
    private void shot(String name) throws Exception {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        Bitmap image = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
        File dir = new File(context.getExternalFilesDir(null),"report-shots"); dir.mkdirs();
        try(FileOutputStream out = new FileOutputStream(new File(dir,name+".png"))) { image.compress(Bitmap.CompressFormat.PNG,100,out); }
        image.recycle();
    }
    private boolean hasText(View v,String text) {
        if(v instanceof TextView && ((TextView)v).getText().toString().contains(text)) return true;
        if(v instanceof ViewGroup) for(int i=0;i<((ViewGroup)v).getChildCount();i++) if(hasText(((ViewGroup)v).getChildAt(i),text))return true;
        return false;
    }
    private View textView(View v,String text) {
        if(v instanceof TextView && ((TextView)v).getText().toString().contains(text))return v;
        if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){
            View found=textView(((ViewGroup)v).getChildAt(i),text);if(found!=null)return found;
        }
        return null;
    }
    private void scrollToText(String value) throws Exception {
        scenario.onActivity(a->{
            androidx.core.widget.NestedScrollView scroll=a.findViewById(R.id.fundScroll);
            scroll.scrollTo(0,0);
            View target=textView(a.getWindow().getDecorView(),value);assertNotNull(target);
            android.graphics.Rect rect=new android.graphics.Rect();target.getDrawingRect(rect);
            scroll.offsetDescendantRectToMyCoords(target,rect);scroll.scrollTo(0,Math.max(0,rect.top-28));
        });settle();
    }
    @Test public void captureFundFinalDetails() throws Exception {
        guard();Tasks.await(FirebaseFirestore.getInstance().enableNetwork(),10,TimeUnit.SECONDS);
        try{actor("admin");Bundle args=new Bundle();args.putString("deliveryId",InstrumentationRegistry.getArguments().getString("reportDeliveryId"));
            open(R.id.rescueFundFragment,args);scrollToText("Admin đã ghi chuyển tiền");shot("fund-paid-admin-status");
            open(R.id.rescueFundFragment,null);
            scenario.onActivity(a->{View target=textView(a.getWindow().getDecorView(),"Lịch sử biến động");assertNotNull(target);target.performClick();});settle();
            scrollToText("Hoàn phần tiền đang giữ");shot("fund-cancelled-admin-ledger");
        }finally{if(scenario!=null)scenario.close();FirebaseAuth.getInstance().signOut();}
    }
    @Test public void captureStableRotationPair() throws Exception {
        guard();FirebaseAuth.getInstance().signOut();Tasks.await(FirebaseFirestore.getInstance().disableNetwork(),10,TimeUnit.SECONDS);
        try{scenario=ActivityScenario.launch(MainActivity.class);settle();open(R.id.loginFragment,null);
            scenario.onActivity(a->{a.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);((TextView)a.findViewById(R.id.emailInput)).setText("rotation@report.example.test");});
            settle();scenario.onActivity(a->assertEquals("rotation@report.example.test",((TextView)a.findViewById(R.id.emailInput)).getText().toString()));shot("login-portrait");
            scenario.onActivity(a->a.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE));settle();
            scenario.onActivity(a->assertEquals("rotation@report.example.test",((TextView)a.findViewById(R.id.emailInput)).getText().toString()));shot("login-landscape");
            scenario.recreate();settle();scenario.onActivity(a->{assertEquals("rotation@report.example.test",((TextView)a.findViewById(R.id.emailInput)).getText().toString());a.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);});
        }finally{if(scenario!=null)scenario.close();}
    }
    private void actor(String role) throws Exception {
        if(scenario!=null){scenario.close(); scenario=null;}
        FirebaseAuth.getInstance().signOut();
        if(!role.equals("guest")) Tasks.await(FirebaseAuth.getInstance().signInWithEmailAndPassword(role+"@report.example.test",
            InstrumentationRegistry.getArguments().getString("reportDemoPassword")),15,TimeUnit.SECONDS);
        scenario=ActivityScenario.launch(MainActivity.class);settle();
    }
    @Test public void captureIsolatedActorsAndOfflineUi() throws Exception {
        context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        assumeTrue(context.getPackageName().endsWith(".reportdemo"));
        assertEquals("demo-rescuefarm",FirebaseApp.getInstance().getOptions().getProjectId());
        try {
            actor("guest"); open(R.id.guestHomeFragment,null);shot("guest-home");
            open(R.id.discoveryFragment,null);shot("guest-discovery");
            Bundle product=new Bundle();product.putString("productId","report-product-0");product.putString("batchId","report-batch-0");
            open(R.id.productDetailFragment,product);shot("guest-product");
            scenario.onActivity(a->{a.findViewById(R.id.addToCartButton).performClick();});settle();
            open(R.id.cartFragment,null);shot("guest-cart");
            open(R.id.productListFragment,null);settle();
            scenario.onActivity(a->assertTrue("Demo catalog must be visible online",hasText(a.getWindow().getDecorView(),"Cà chua DEMO")));shot("catalog-online");
            Tasks.await(FirebaseFirestore.getInstance().disableNetwork(),10,TimeUnit.SECONDS);
            scenario.close();scenario=ActivityScenario.launch(MainActivity.class);settle();open(R.id.productListFragment,null);
            scenario.onActivity(a->assertTrue("Previously read catalog must survive screen/app Activity reopen",hasText(a.getWindow().getDecorView(),"Cà chua DEMO")));shot("catalog-sdk-offline");
            Tasks.await(FirebaseFirestore.getInstance().enableNetwork(),10,TimeUnit.SECONDS);
            actor("customer");open(R.id.cartFragment,null);shot("customer-cart");open(R.id.checkoutFragment,null);shot("customer-checkout");
            open(R.id.customerOrderListFragment,null);shot("customer-orders");
            actor("seller");open(R.id.sellerDashboardFragment,null);shot("seller-dashboard");
            open(R.id.sellerProductListFragment,null);shot("seller-products");
            open(R.id.batchManagerFragment,product);shot("seller-batches");
            open(R.id.sellerCampaignListFragment,null);shot("seller-campaigns");
            open(R.id.rescueFundFragment,null);shot("seller-fund");
            actor("admin");open(R.id.adminDashboardFragment,null);shot("admin-dashboard");
            Bundle section=new Bundle();section.putString("section","PRODUCTS");open(R.id.adminResourceFragment,section);shot("admin-products");
            open(R.id.rescueFundFragment,null);shot("admin-fund");
            actor("guest");open(R.id.loginFragment,null);
            scenario.onActivity(a->((TextView)a.findViewById(R.id.emailInput)).setText("rotation@report.example.test"));settle();shot("login-portrait");
            scenario.onActivity(a->a.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE));settle();
            scenario.onActivity(a->assertEquals("rotation@report.example.test",((TextView)a.findViewById(R.id.emailInput)).getText().toString()));shot("login-landscape");
            scenario.recreate();settle();scenario.onActivity(a->assertEquals("rotation@report.example.test",((TextView)a.findViewById(R.id.emailInput)).getText().toString()));
            scenario.onActivity(a->a.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));
        } finally { if(scenario!=null)scenario.close();Tasks.await(FirebaseFirestore.getInstance().enableNetwork(),10,TimeUnit.SECONDS);FirebaseAuth.getInstance().signOut(); }
    }
}
