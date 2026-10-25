package com.rescuefarm.ui.auth;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.MotionEvent;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.TextView;
import com.rescuefarm.BuildConfig;
import com.rescuefarm.R;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;

/** Hosted Google widget embedded in Login. Never passes email/password into HTML. */
final class InlinePasswordCaptcha {
    interface Listener { void onToken(String token); void onInvalidated(); }
    private final WebView web;
    private final View viewport;
    private final TextView status;
    private final Listener listener;
    private String pageUrl, adapterScript;
    private boolean destroyed, completed, expanded, busy, interactionReserved, sawChallenge;
    private int measuredHeight = 96;
    private int lastHeight;

    @SuppressLint({"SetJavaScriptEnabled", "ClickableViewAccessibility"})
    InlinePasswordCaptcha(WebView web, TextView status, Listener listener) {
        this.web=web;this.viewport=(View)web.getParent();this.status=status;this.listener=listener;
        web.setBackgroundColor(web.getContext().getColor(R.color.rescue_surface));
        web.getSettings().setJavaScriptEnabled(true);web.getSettings().setDomStorageEnabled(true);
        web.getSettings().setAllowFileAccess(false);web.getSettings().setAllowContentAccess(false);
        web.getSettings().setMixedContentMode(android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        web.getSettings().setCacheMode(android.webkit.WebSettings.LOAD_NO_CACHE);
        web.setSaveEnabled(false);web.setVisibility(View.INVISIBLE);
        web.addJavascriptInterface(new Object() {
            @JavascriptInterface public void complete(String token) {
                if(token==null || token.length()<10 || token.length()>8192)return;
                web.post(()->{
                    if(destroyed || completed || busy)return;
                    completed=true;interactionReserved=false;sawChallenge=false;expanded=false;
                    resizeViewport(96);listener.onToken(token);
                });
            }
            @JavascriptInterface public void layout(int cssHeight, boolean challengeOpen, boolean ready, String notice) {
                web.post(()->{
                    if(destroyed)return;
                    boolean opening=challengeOpen&&!expanded;expanded=challengeOpen;
                    if(challengeOpen)sawChallenge=true;
                    else if(sawChallenge){interactionReserved=false;sawChallenge=false;}
                    if(notice!=null&&!notice.isEmpty())interactionReserved=false;
                    measuredHeight=Math.max(96,Math.min(cssHeight,1200));
                    // The login page scrolls: do not cap a challenge to the short
                    // checkbox viewport or the keyboard-reduced screen height.
                    resizeViewport(completed?96:(interactionReserved||challengeOpen?Math.max(620,measuredHeight):measuredHeight));
                    if(!completed){
                        if(notice!=null&&!notice.isEmpty())status.setText(notice.substring(0,Math.min(notice.length(),250)));
                        else status.setText(ready?R.string.password_captcha_hint:R.string.password_captcha_loading);
                    }
                    if(opening)web.post(()->{if(!destroyed)web.requestRectangleOnScreen(new android.graphics.Rect(0,0,web.getWidth(),web.getHeight()),false);});
                });
            }
        },"RescueFarmCaptcha");
        web.setOnTouchListener((v,event)->{
            if(busy)return true;
            if(event.getActionMasked()==MotionEvent.ACTION_DOWN){
                android.view.inputmethod.InputMethodManager ime=(android.view.inputmethod.InputMethodManager)v.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                if(ime!=null)ime.hideSoftInputFromWindow(v.getWindowToken(),0);
                if(!completed){interactionReserved=true;resizeViewport(Math.max(620,measuredHeight));}
                // Let the outer login page scroll when the full challenge fits.
                if(web.canScrollVertically(-1)||web.canScrollVertically(1))v.getParent().requestDisallowInterceptTouchEvent(true);
            }else if(event.getActionMasked()==MotionEvent.ACTION_UP||event.getActionMasked()==MotionEvent.ACTION_CANCEL)
                v.getParent().requestDisallowInterceptTouchEvent(false);
            return false;
        });
        web.setWebViewClient(new WebViewClient(){
            private boolean failed;
            @Override public void onPageStarted(WebView view,String url,android.graphics.Bitmap favicon){
                failed=false;completed=false;listener.onInvalidated();status.setText(R.string.password_captcha_loading);
            }
            @Override public void onPageFinished(WebView view,String url){
                if(destroyed||failed||!url.equals(pageUrl))return;
                view.evaluateJavascript(adapterScript,unused->{if(!destroyed)view.setVisibility(View.VISIBLE);});
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){
                return request.isForMainFrame()&&!request.getUrl().toString().equals(pageUrl);
            }
            @Override public void onReceivedError(WebView view,WebResourceRequest request,android.webkit.WebResourceError error){
                if(request.isForMainFrame()){failed=true;status.setText(R.string.password_captcha_network_error);}
            }
            @Override public void onReceivedHttpError(WebView view,WebResourceRequest request,android.webkit.WebResourceResponse response){
                if(request.isForMainFrame()){failed=true;status.setText(R.string.password_captcha_setup_error);}
            }
        });
        try {
            StringBuilder script=new StringBuilder();
            try(BufferedReader reader=new BufferedReader(new InputStreamReader(web.getContext().getAssets().open("auth/password-captcha-inline.js"),StandardCharsets.UTF_8))){
                String line;while((line=reader.readLine())!=null)script.append(line).append('\n');
            }
            adapterScript=script.toString();
            URI base=URI.create(BuildConfig.RENDER_BACKEND_URL.trim());
            boolean demo=BuildConfig.BUILD_TYPE.equals("reportDemo")&&"http".equals(base.getScheme())&&"10.0.2.2".equals(base.getHost());
            if((!"https".equals(base.getScheme())&&!demo)||base.getHost()==null||base.getUserInfo()!=null||base.getQuery()!=null||base.getFragment()!=null)
                throw new IllegalArgumentException();
            pageUrl=base.toString().replaceAll("/$","")+"/auth/password-captcha";web.loadUrl(pageUrl);
        }catch(Exception error){status.setText(R.string.password_captcha_url_error);}
    }
    void reset(){
        if(destroyed||(!completed&&!expanded&&!interactionReserved))return;
        completed=false;expanded=false;interactionReserved=false;sawChallenge=false;resizeViewport(96);
        web.evaluateJavascript("if(window.rescueFarmResetCaptcha)window.rescueFarmResetCaptcha();",null);
    }
    private void resizeViewport(int heightDp){
        int px=Math.round(heightDp*web.getResources().getDisplayMetrics().density);
        if(px!=lastHeight){lastHeight=px;android.view.ViewGroup.LayoutParams p=viewport.getLayoutParams();p.height=px;viewport.setLayoutParams(p);}
    }
    void setBusy(boolean value){busy=value;web.setEnabled(!value);}
    void destroy(){
        destroyed=true;web.removeJavascriptInterface("RescueFarmCaptcha");web.stopLoading();
        if(web.getParent() instanceof android.view.ViewGroup)((android.view.ViewGroup)web.getParent()).removeView(web);
        web.destroy();
    }
}
