package com.rescuefarm.service.realtime;

import android.os.Handler;
import android.os.Looper;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import java.util.ArrayList;
import java.util.List;

/** Invalidates API projections only while the owning view is visible. Business writes stay unchanged. */
public final class FirebaseRealtime implements DefaultLifecycleObserver {
    private final LifecycleOwner owner;
    private final Runnable refresh;
    private Object[] targets;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<ListenerRegistration> listeners = new ArrayList<>();
    private int generation;
    private final Runnable dispatch;

    public static FirebaseRealtime bind(LifecycleOwner owner, Runnable refresh, Object... targets) {
        FirebaseRealtime source = new FirebaseRealtime(owner, refresh, targets);
        owner.getLifecycle().addObserver(source); return source;
    }
    public void configure(Object... next) {
        if (java.util.Arrays.equals(targets, next)) return;
        onStop(owner); targets = next;
        if (owner.getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED)) onStart(owner);
    }
    private FirebaseRealtime(LifecycleOwner owner, Runnable refresh, Object[] targets) {
        this.owner = owner; this.refresh = refresh; this.targets = targets;
        this.dispatch = () -> {
            if (owner.getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED)) refresh.run();
        };
    }
    @Override public void onStart(LifecycleOwner ignored) {
        int epoch = ++generation;
        for (Object target : targets) {
            if (target instanceof Query) listeners.add(((Query) target).addSnapshotListener(com.google.firebase.firestore.MetadataChanges.INCLUDE, (snapshot, error) -> {
                if (epoch == generation && (error != null || snapshot != null
                        && (!snapshot.getMetadata().isFromCache() || snapshot.getMetadata().hasPendingWrites()))) changed();
            }));
            else if (target instanceof DocumentReference) listeners.add(((DocumentReference) target).addSnapshotListener(com.google.firebase.firestore.MetadataChanges.INCLUDE, (snapshot, error) -> {
                if (epoch == generation && (error != null || snapshot != null
                        && (!snapshot.getMetadata().isFromCache() || snapshot.getMetadata().hasPendingWrites()))) changed();
            }));
            else throw new IllegalArgumentException("Expected a Firestore Query or DocumentReference");
        }
    }
    private void changed() { main.removeCallbacks(dispatch); main.postDelayed(dispatch, 120L); }
    @Override public void onStop(LifecycleOwner ignored) {
        generation++; main.removeCallbacks(dispatch);
        for (ListenerRegistration listener : listeners) listener.remove();
        listeners.clear();
    }
    @Override public void onDestroy(LifecycleOwner ignored) {
        onStop(ignored); owner.getLifecycle().removeObserver(this);
    }
}
