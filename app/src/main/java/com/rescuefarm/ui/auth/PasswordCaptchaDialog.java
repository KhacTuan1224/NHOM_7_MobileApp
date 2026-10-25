package com.rescuefarm.ui.auth;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.rescuefarm.BuildConfig;
import com.rescuefarm.R;
import java.net.URI;

/** Real hosted Google widget; no credentials are loaded into this WebView. */
public final class PasswordCaptchaDialog extends DialogFragment {
    public static final String RESULT = "passwordCaptchaResult";
    private WebView web;
    private boolean completed, destroyed;
    private String pageUrl;
    @NonNull @Override @SuppressLint("SetJavaScriptEnabled")
    public Dialog onCreateDialog(Bundle state) {
        destroyed=false;completed=false;
        LinearLayout content = new LinearLayout(requireContext());content.setOrientation(LinearLayout.VERTICAL);
        TextView status = new TextView(requireContext());status.setPadding(20,12,20,12);
        status.setText(R.string.password_captcha_dialog_hint);content.addView(status);
        web = new WebView(requireContext());content.addView(web,new LinearLayout.LayoutParams(-1,0,1));
        web.getSettings().setJavaScriptEnabled(true);web.getSettings().setDomStorageEnabled(true);
        web.getSettings().setAllowFileAccess(false);web.getSettings().setAllowContentAccess(false);
        web.getSettings().setMixedContentMode(android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        web.getSettings().setCacheMode(android.webkit.WebSettings.LOAD_NO_CACHE);
        web.addJavascriptInterface(new Object() {
            @JavascriptInterface public void complete(String token) {
                if (token == null || token.length() < 10 || token.length() > 8192) return;
                android.app.Activity activity = getActivity();if(activity == null)return;
                activity.runOnUiThread(() -> {
                    if (destroyed || completed || !isAdded()) return;
                    completed = true;Bundle result = new Bundle();result.putString("responseToken",token);
                    result.putString("email",requireArguments().getString("email",""));
                    getParentFragmentManager().setFragmentResult(RESULT,result);dismissAllowingStateLoss();
                });
            }
        },"RescueFarmCaptcha");
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (!request.isForMainFrame()) return false;
                return !request.getUrl().toString().equals(pageUrl);
            }
            @Override public void onReceivedError(WebView view, android.webkit.WebResourceRequest request, android.webkit.WebResourceError error) {
                if(request.isForMainFrame())status.setText(R.string.password_captcha_network_error);
            }
            @Override public void onReceivedHttpError(WebView view, WebResourceRequest request, android.webkit.WebResourceResponse response) {
                if(request.isForMainFrame())status.setText(R.string.password_captcha_setup_error);
            }
        });
        try {
            URI base = URI.create(BuildConfig.RENDER_BACKEND_URL.trim());
            boolean localDemo = BuildConfig.BUILD_TYPE.equals("reportDemo") && "http".equals(base.getScheme())
                && "10.0.2.2".equals(base.getHost());
            if ((!("https".equals(base.getScheme())) && !localDemo) || base.getHost() == null || base.getUserInfo() != null
                || base.getQuery() != null || base.getFragment() != null) throw new IllegalArgumentException();
            pageUrl = base.toString().replaceAll("/$","") + "/auth/password-captcha";web.loadUrl(pageUrl);
        } catch (RuntimeException error) { status.setText(R.string.password_captcha_url_error); }
        return new MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.password_captcha_action)
            .setView(content).setNegativeButton(R.string.password_captcha_close,null).create();
    }
    @Override public void onStart() {
        super.onStart();if(getDialog()!=null && getDialog().getWindow()!=null)
            getDialog().getWindow().setLayout(-1,(int)(getResources().getDisplayMetrics().heightPixels*.85));
    }
    @Override public void onDestroyView() {
        destroyed=true;if(web!=null){web.removeJavascriptInterface("RescueFarmCaptcha");web.stopLoading();web.destroy();web=null;}
        super.onDestroyView();
    }
}
