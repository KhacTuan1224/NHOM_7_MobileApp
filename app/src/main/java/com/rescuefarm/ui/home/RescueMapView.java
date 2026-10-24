package com.rescuefarm.ui.home;

import android.app.Dialog;
import android.content.Context;
import android.net.Uri;
import android.util.AttributeSet;
import android.view.ViewGroup;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import com.rescuefarm.data.repository.RescueMapRepository;
import java.util.function.Consumer;

/** Leaflet renderer reused by the Home card and its full-screen dialog. */
public final class RescueMapView extends LinearLayout {
    private final WebView map;
    private final TextView status;
    private final Button expand;
    private RescueMapRepository repository;
    private RescueMapRepository.State state;
    private Observer<RescueMapRepository.State> observer;
    private boolean ready;
    private Dialog dialog;
    public RescueMapView(Context context, AttributeSet attrs) {
        super(context,attrs); setOrientation(VERTICAL);setPadding(0,dp(12),0,dp(8));
        TextView title=new TextView(context);title.setText("Bản đồ điểm giải cứu");title.setTextSize(18);title.setTypeface(null,android.graphics.Typeface.BOLD);addView(title);
        status=new TextView(context);status.setText("Đang kết nối bản đồ…");status.setTextSize(13);addView(status);
        TextView legend=new TextView(context);legend.setText("● Cố định    ■ Di động · chạm điểm để xem thông tin");legend.setTextSize(12);addView(legend);
        map=new WebView(context);addView(map,new LayoutParams(-1,dp(220)));
        expand=new Button(context);expand.setText("Mở bản đồ lớn");addView(expand,new LayoutParams(-1,-2));
    }
    public void bind(Fragment fragment, Consumer<String> openCampaign) { bind(fragment,openCampaign,false); }
    private void bind(Fragment fragment, Consumer<String> openCampaign, boolean expanded) {
        map.getSettings().setJavaScriptEnabled(true);map.getSettings().setAllowFileAccess(false);
        map.getSettings().setAllowContentAccess(false);map.getSettings().setDomStorageEnabled(false);
        map.getSettings().setCacheMode(android.webkit.WebSettings.LOAD_DEFAULT);
        map.getSettings().setUserAgentString(map.getSettings().getUserAgentString()+" RescueFarmAndroid/1.0 (+https://github.com/LaiThanhDat-glitch/NHOM_7_MobileApp)");
        map.setWebViewClient(new WebViewClient(){
            @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest req) {
                Uri u=req.getUrl();
                if("https".equals(u.getScheme())&&"appassets.androidplatform.net".equals(u.getHost())) {
                    String name=u.getLastPathSegment(),asset,mime;
                    if("rescue-map.html".equals(name)){asset="maps/rescue-map.html";mime="text/html";}
                    else if("leaflet.js".equals(name)||"leaflet.css".equals(name)){asset="maps/leaflet/"+name;mime=name.endsWith(".js")?"text/javascript":"text/css";}
                    else return new WebResourceResponse("text/plain","UTF-8",new java.io.ByteArrayInputStream(new byte[0]));
                    try{return new WebResourceResponse(mime,"UTF-8",getContext().getAssets().open(asset));}
                    catch(java.io.IOException error){return new WebResourceResponse("text/plain","UTF-8",new java.io.ByteArrayInputStream(new byte[0]));}
                }
                return super.shouldInterceptRequest(view,req);
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest req) {
                Uri u=req.getUrl();
                if("rescuefarm-map".equals(u.getScheme())&&"campaign".equals(u.getHost())) {
                    String id=u.getLastPathSegment();
                    if(id!=null&&id.matches("[A-Za-z0-9_-]{1,128}")&&contains(id)) {
                        if(dialog!=null)dialog.dismiss();openCampaign.accept(id);
                    }
                }
                return true; // Never navigate WebView to external pages or arbitrary JS.
            }
            @Override public void onPageFinished(WebView view,String url){ready=true;render();}
        });
        map.loadUrl("https://appassets.androidplatform.net/maps/rescue-map.html?expanded="+(expanded?"1":"0"));
        repository=RescueMapRepository.get(getContext());observer=value->{state=value;render();};
        repository.observe(fragment.getViewLifecycleOwner(),observer);
        if(expanded) {
            map.setLayoutParams(new LayoutParams(-1,0,1));
            LinearLayout filters=new LinearLayout(getContext());
            String[] labels={"Tất cả","Cố định","Di động"},modes={"ALL","FIXED_POINT","MOBILE_POINT"};
            for(int i=0;i<labels.length;i++){Button b=new Button(getContext());b.setText(labels[i]);final String mode=modes[i];b.setOnClickListener(v->map.evaluateJavascript("window.filterRescuePoints&&window.filterRescuePoints('"+mode+"')",null));filters.addView(b,new LayoutParams(0,-2,1));}
            addView(filters,3);expand.setText("Đóng bản đồ");expand.setOnClickListener(v->{if(dialog!=null)dialog.dismiss();});
        } else expand.setOnClickListener(v->{
            Dialog d=new Dialog(fragment.requireContext(),android.R.style.Theme_Material_Light_NoActionBar);
            RescueMapView large=new RescueMapView(getContext(),null);large.dialog=d;large.bind(fragment,openCampaign,true);
            d.setContentView(large);d.setOnDismissListener(unused->large.release());d.show();
            if(d.getWindow()!=null)d.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT);
            dialog=d;
        });
    }
    private boolean contains(String id){if(state==null||state.points==null)return false;for(int i=0;i<state.points.length();i++){org.json.JSONObject p=state.points.optJSONObject(i);if(p!=null&&id.equals(p.optString("id")))return true;}return false;}
    private void render(){
        if(state==null)return;
        int count=state.points==null?0:state.points.length();
        status.setText((state.live?(count==0?"Chưa có điểm đang hoạt động":"Đang cập nhật trực tiếp · "+count+" điểm"):
            "Chưa có kết nối trực tiếp · dữ liệu đã lưu; ẩn vị trí xe")+(state.truncated?" · Đã đạt giới hạn 500 điểm":""));
        if(ready)map.evaluateJavascript("window.updateRescuePoints&&window.updateRescuePoints("+(state.points==null?"[]":state.points.toString())+","+state.live+")",null);
    }
    public void release(){if(dialog!=null&&dialog.isShowing())dialog.dismiss();if(repository!=null&&observer!=null)repository.removeObserver(observer);ready=false;map.stopLoading();map.destroy();}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
}
