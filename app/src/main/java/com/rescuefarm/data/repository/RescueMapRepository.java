package com.rescuefarm.data.repository;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import androidx.lifecycle.LiveData;
import com.rescuefarm.BuildConfig;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** One HTTP stream per app process; server shares one Firestore watch across viewers.
 * Public disk cache is display-only. No Firebase reads here, no API call per marker. */
public final class RescueMapRepository extends LiveData<RescueMapRepository.State> {
    public static final class State {
        public final JSONArray points;
        public final boolean live, truncated;
        public final long receivedAt;
        State(JSONArray points, boolean live, boolean truncated, long at) {
            this.points=points; this.live=live; this.truncated=truncated; this.receivedAt=at;
        }
    }
    private static RescueMapRepository instance;
    public static synchronized RescueMapRepository get(Context context) {
        if (instance == null) instance = new RescueMapRepository(context.getApplicationContext());
        return instance;
    }
    private final SharedPreferences cache;
    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile int generation;
    private volatile HttpURLConnection connection;
    private final Runnable stop = () -> {
        generation++; HttpURLConnection c=connection; if(c!=null)c.disconnect();
        State value=getValue(); if(value!=null)setValue(new State(value.points,false,value.truncated,value.receivedAt));
    };
    private int runningGeneration = -1;
    private RescueMapRepository(Context context) {
        cache=context.getSharedPreferences("rescue_map_public_v1",Context.MODE_PRIVATE);
        try {
            JSONObject saved=new JSONObject(cache.getString("snapshot","{}"));
            // Old cache is bounded to one day and never makes a mobile point live.
            long at=saved.optLong("savedAt");
            if(System.currentTimeMillis()-at<=86400000L) setValue(new State(saved.optJSONArray("points"),false,saved.optBoolean("truncated"),at));
        } catch(Exception ignored) { }
    }
    @Override protected void onActive() {
        main.removeCallbacks(stop);
        if(runningGeneration!=generation) {
            int request=generation; runningGeneration=request;
            new Thread(() -> stream(request),"rescue-map-stream").start();
        }
    }
    @Override protected void onInactive() { main.postDelayed(stop,5000L); }
    private void stale(int request) {
        main.post(() -> { if(request!=generation)return; State current=getValue();
            setValue(new State(current==null?new JSONArray():current.points,false,current!=null&&current.truncated,current==null?0:current.receivedAt)); });
    }
    private void stream(int request) {
        long backoff=2000L;
        while(request==generation) {
            HttpURLConnection c=null;
            try {
                String base=BuildConfig.RENDER_BACKEND_URL.trim().replaceAll("/$", "");
                if(base.isEmpty()) throw new IllegalStateException("Backend chưa cấu hình");
                c=(HttpURLConnection)new URL(base+"/rescue-map/events").openConnection(); connection=c;
                c.setConnectTimeout(30000); c.setReadTimeout(45000); c.setRequestProperty("Accept","text/event-stream");
                int status=c.getResponseCode();
                if(status!=200) {
                    if(status==429||status==503) backoff=Math.max(backoff,Math.min(300000L,1000L*parseSeconds(c.getHeaderField("Retry-After"))));
                    throw new IllegalStateException("Map unavailable");
                }
                try(BufferedReader reader=new BufferedReader(new InputStreamReader(c.getInputStream(),StandardCharsets.UTF_8))) {
                    String line;
                    while(request==generation && (line=reader.readLine())!=null) {
                        if(!line.startsWith("data: "))continue;
                        JSONObject body=new JSONObject(line.substring(6));
                        JSONArray points=body.optJSONArray("points"); if(points==null||points.length()>500)continue;
                        long at=System.currentTimeMillis(); boolean live=body.optBoolean("live");
                        State value=new State(points,live,body.optBoolean("truncated"),at);
                        if(live) {
                            JSONObject saved=new JSONObject(body.toString()).put("savedAt",at);
                            cache.edit().putString("snapshot",saved.toString()).apply(); backoff=2000L;
                        }
                        main.post(() -> { if(request==generation)setValue(value); });
                    }
                }
            } catch(Exception ignored) { /* Surface cache state and back off; never expose auth or raw data in logs. */ }
            finally { if(c!=null)c.disconnect(); if(connection==c)connection=null; }
            stale(request);
            try { Thread.sleep(backoff); } catch(InterruptedException ignored) { return; }
            backoff=Math.min(60000L,backoff*2);
        }
        main.post(() -> { if(runningGeneration==request)runningGeneration=-1; if(hasActiveObservers())onActive(); });
    }
    private static int parseSeconds(String text) { try{return Math.max(1,Integer.parseInt(text));}catch(Exception ignored){return 60;} }
}
