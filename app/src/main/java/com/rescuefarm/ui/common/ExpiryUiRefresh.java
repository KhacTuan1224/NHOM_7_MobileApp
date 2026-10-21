package com.rescuefarm.ui.common;

import android.os.Handler;
import android.os.Looper;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;

/** Re-evaluate wall-clock availability even when no Firestore document changes. */
public final class ExpiryUiRefresh {
    private ExpiryUiRefresh() { }
    public static void bind(Fragment fragment, Runnable refresh) {
        Handler handler = new Handler(Looper.getMainLooper());
        Runnable tick = new Runnable() {
            @Override public void run() { refresh.run(); handler.postDelayed(this, 30_000L); }
        };
        fragment.getViewLifecycleOwner().getLifecycle().addObserver(new DefaultLifecycleObserver() {
            @Override public void onResume(LifecycleOwner owner) { handler.removeCallbacks(tick); tick.run(); }
            @Override public void onPause(LifecycleOwner owner) { handler.removeCallbacks(tick); }
            @Override public void onDestroy(LifecycleOwner owner) { handler.removeCallbacks(tick); }
        });
    }
}
