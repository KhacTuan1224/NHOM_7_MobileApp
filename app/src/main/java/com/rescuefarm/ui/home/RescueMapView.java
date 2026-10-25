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
import android.widget.LinearLayout;
import com.google.android.material.card.MaterialCardView;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import com.rescuefarm.data.repository.RescueMapRepository;
import java.util.function.Consumer;

/** Leaflet renderer reused by the Home card and its full-screen dialog. */
public final class RescueMapView extends LinearLayout {
    private final WebView map;
    private final MaterialCardView frame;
    private RescueMapRepository repository;
    private RescueMapRepository.State state;
    private Observer<RescueMapRepository.State> observer;
    private boolean ready;
    private Dialog dialog;
    public RescueMapView(Context context, AttributeSet attrs) {
        super(context,attrs); setOrientation(VERTICAL);setPadding(0,dp(12),0,dp(8));
        frame=new MaterialCardView(context);frame.setRadius(dp(10));frame.setCardElevation(0);
        frame.setStrokeWidth(dp(1));frame.setStrokeColor(0x44366B47);
        frame.setClipToOutline(true);addView(frame,new LayoutParams(-1,dp(220)));
        map=new WebView(context);frame.addView(map,new ViewGroup.LayoutParams(-1,-1));
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
                if(expanded&&"rescuefarm-map".equals(u.getScheme())&&"back".equals(u.getHost())) {
                    if(dialog!=null)dialog.dismiss();return true;
                }
                if(!expanded&&"rescuefarm-map".equals(u.getScheme())&&"expand".equals(u.getHost())) {
                    openExpanded(fragment,openCampaign);return true;
                }
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
            setPadding(0,0,0,0);frame.setRadius(0);frame.setStrokeWidth(0);
            frame.setLayoutParams(new LayoutParams(-1,-1));
        }
    }
    private void openExpanded(Fragment fragment,Consumer<String> openCampaign) {
            if(dialog!=null&&dialog.isShowing())return;
            Dialog d=new Dialog(fragment.requireContext(),android.R.style.Theme_Material_Light_NoActionBar);
            RescueMapView large=new RescueMapView(getContext(),null);large.dialog=d;large.bind(fragment,openCampaign,true);
            d.setContentView(large);d.setOnDismissListener(unused->large.release());d.show();
            if(d.getWindow()!=null)d.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT);
            dialog=d;
    }
    private boolean contains(String id){if(state==null||state.points==null)return false;for(int i=0;i<state.points.length();i++){org.json.JSONObject p=state.points.optJSONObject(i);if(p!=null&&id.equals(p.optString("id")))return true;}return false;}
    private void render(){
        if(state==null)return;
        if(ready)map.evaluateJavascript("window.updateRescuePoints&&window.updateRescuePoints("+(state.points==null?"[]":state.points.toString())+","+state.live+","+state.truncated+")",null);
    }
    public void release(){if(dialog!=null&&dialog.isShowing())dialog.dismiss();if(repository!=null&&observer!=null)repository.removeObserver(observer);ready=false;map.stopLoading();map.destroy();}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
}
