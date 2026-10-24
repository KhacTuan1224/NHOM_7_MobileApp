package com.rescuefarm.service.location;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.location.Location;
import android.os.*;
import android.widget.Toast;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import com.google.android.gms.location.*;
import com.google.firebase.auth.FirebaseAuth;
import com.rescuefarm.MainActivity;
import com.rescuefarm.R;
import com.rescuefarm.data.repository.*;
import com.rescuefarm.domain.model.RescueCampaign;
import java.util.concurrent.TimeUnit;

/** Explicit, non-sticky sharing session. A process restart never silently resumes GPS. */
public final class MobileLocationService extends Service {
    private static final String CHANNEL="mobile_rescue_location", STOP="com.rescuefarm.STOP_LOCATION";
    private static volatile String activeCampaign="";
    private final Handler main=new Handler(Looper.getMainLooper());
    private final LocationPublishPolicy policy=new LocationPublishPolicy();
    private CampaignRepository repository;
    private FusedLocationProviderClient provider;
    private FirebaseAuth.AuthStateListener authWatch;
    private String campaignId="",owner="";
    private long endAt, lastAttempt=-1;
    private boolean stopping, inFlight, clearing;
    private final Runnable expiryCheck=new Runnable(){public void run(){if(System.currentTimeMillis()>=endAt)stopSharing();else main.postDelayed(this,10000);}};
    private final LocationCallback locations=new LocationCallback(){@Override public void onLocationResult(LocationResult result){Location fix=result.getLastLocation();if(fix!=null)publish(fix);}};
    public static boolean isTracking(String id){return id!=null&&id.equals(activeCampaign);}
    public static void start(Context context,String id,long endAt){
        if(!activeCampaign.isEmpty()&&!activeCampaign.equals(id)){
            Toast.makeText(context,"Hãy dừng chia sẻ chiến dịch hiện tại trước khi đổi chuyến.",Toast.LENGTH_LONG).show();return;
        }
        try { ContextCompat.startForegroundService(context,new Intent(context,MobileLocationService.class).putExtra("campaignId",id).putExtra("endAt",endAt)); }
        catch(RuntimeException error){Toast.makeText(context,"Không bắt đầu được chia sẻ. Hãy mở ứng dụng và kiểm tra quyền vị trí.",Toast.LENGTH_LONG).show();}
    }
    public static void stop(Context context){context.startService(new Intent(context,MobileLocationService.class).setAction(STOP));}
    @Override public IBinder onBind(Intent intent){return null;}
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent==null){stopSelf();return START_NOT_STICKY;}
        if(STOP.equals(intent.getAction())){stopSharing();return START_NOT_STICKY;}
        if(!campaignId.isEmpty())return START_NOT_STICKY;
        campaignId=intent.getStringExtra("campaignId");endAt=intent.getLongExtra("endAt",0);
        if(campaignId==null||!campaignId.matches("[A-Za-z0-9_-]{1,128}")||endAt<=System.currentTimeMillis()
                ||FirebaseAuth.getInstance().getCurrentUser()==null){stopSelf();return START_NOT_STICKY;}
        owner=FirebaseAuth.getInstance().getCurrentUser().getUid();
        boolean fine=ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED;
        boolean coarse=ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED;
        if(!fine&&!coarse){stopSelf();return START_NOT_STICKY;}
        NotificationManager manager=getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel(CHANNEL,"Chia sẻ vị trí điểm giải cứu",NotificationManager.IMPORTANCE_LOW));
        PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop=PendingIntent.getService(this,1,new Intent(this,MobileLocationService.class).setAction(STOP),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification notification=new NotificationCompat.Builder(this,CHANNEL).setSmallIcon(R.drawable.ic_notification_rescuefarm)
            .setContentTitle("Đang chia sẻ vị trí điểm giải cứu")
            .setContentText("Vị trí điện thoại được công khai cho người xem bản đồ.")
            .setOngoing(true).setContentIntent(open).addAction(0,"Dừng chia sẻ",stop).build();
        if(Build.VERSION.SDK_INT>=29)startForeground(701,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);else startForeground(701,notification);
        activeCampaign=campaignId;
        repository=CampaignRepositoryFactory.create(this);
        authWatch=auth->{if(auth.getCurrentUser()==null||!owner.equals(auth.getCurrentUser().getUid()))stopSharing();};
        FirebaseAuth.getInstance().addAuthStateListener(authWatch);
        provider=LocationServices.getFusedLocationProviderClient(this);
        try {
            LocationRequest request=new LocationRequest.Builder(fine?Priority.PRIORITY_HIGH_ACCURACY:Priority.PRIORITY_BALANCED_POWER_ACCURACY,5000)
                .setMinUpdateIntervalMillis(5000).setMaxUpdateAgeMillis(0).build();
            provider.requestLocationUpdates(request,locations,Looper.getMainLooper()).addOnFailureListener(error->stopSharing());
            main.post(expiryCheck);
        } catch(SecurityException error){stopSharing();}
        return START_NOT_STICKY;
    }
    private void publish(Location fix){
        long elapsed=SystemClock.elapsedRealtime();
        long ageNanos=SystemClock.elapsedRealtimeNanos()-fix.getElapsedRealtimeNanos();
        if(stopping||inFlight||ageNanos<0||ageNanos>TimeUnit.SECONDS.toNanos(15)
                ||!fix.hasAccuracy()||fix.getAccuracy()>100||elapsed-lastAttempt<10000
                ||!policy.shouldPublish(fix.getLatitude(),fix.getLongitude(),elapsed))return;
        if(System.currentTimeMillis()>=endAt){stopSharing();return;}
        lastAttempt=elapsed;inFlight=true;
        repository.updateMobileLocation(campaignId,fix.getLatitude(),fix.getLongitude(),true,new CampaignRepository.CampaignCallback(){
            @Override public void onSuccess(RescueCampaign c){main.post(()->{inFlight=false;policy.published(fix.getLatitude(),fix.getLongitude(),elapsed);if(stopping)clearAndFinish();});}
            @Override public void onError(CampaignRepository.ErrorCode code,String message){main.post(()->{
                inFlight=false;
                if(code!=CampaignRepository.ErrorCode.NETWORK)stopSharing();
                if(stopping)clearAndFinish();
            });}
        });
    }
    private void stopSharing(){
        stopping=true;
        if(provider!=null)provider.removeLocationUpdates(locations);
        main.removeCallbacks(expiryCheck);
        // Serialize STOP after an in-flight publish so its callback cannot re-enable sharing.
        if(!inFlight)clearAndFinish();
    }
    private void clearAndFinish(){
        if(clearing)return;clearing=true;
        if(repository==null||campaignId==null||campaignId.isEmpty()){stopSelf();return;}
        repository.updateMobileLocation(campaignId,0,0,false,new CampaignRepository.CampaignCallback(){
            @Override public void onSuccess(RescueCampaign c){main.post(()->stopSelf());}
            @Override public void onError(CampaignRepository.ErrorCode code,String message){main.post(()->{
                Toast.makeText(MobileLocationService.this,"Đã dừng GPS. Nếu mất mạng, vị trí đã gửi sẽ hết hiệu lực sau tối đa 10 phút.",Toast.LENGTH_LONG).show();stopSelf();});}
        });
    }
    @Override public void onDestroy(){
        stopping=true;main.removeCallbacksAndMessages(null);
        if(provider!=null)provider.removeLocationUpdates(locations);
        if(authWatch!=null)FirebaseAuth.getInstance().removeAuthStateListener(authWatch);
        if(repository!=null)repository.close();activeCampaign="";super.onDestroy();
    }
}
