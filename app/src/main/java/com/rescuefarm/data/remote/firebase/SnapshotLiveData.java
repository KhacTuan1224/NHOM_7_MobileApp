package com.rescuefarm.data.remote.firebase;

import android.os.Handler;
import android.os.Looper;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.MetadataChanges;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.Source;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/** A single, lifecycle-owned Firestore subscription with a Room fallback and manual refresh. */
public final class SnapshotLiveData<T> extends MediatorLiveData<T> {
    public interface Completion { void complete(Exception error); }
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Function<QuerySnapshot, T> mapper;
    private String ownerId;
    private final T empty;
    private final List<Completion> pending = new ArrayList<>();
    private Query query;
    private ListenerRegistration registration;
    private FirebaseAuth.AuthStateListener authListener;
    private ListenerRegistration profileRegistration;
    private String profileOwner = "", profileVersion = "";
    private boolean receivedRemote;
    private int generation;

    public SnapshotLiveData(Query query, LiveData<T> cache, String ownerId, T empty,
            Function<QuerySnapshot, T> mapper) {
        this.query = query; this.mapper = mapper; this.ownerId = ownerId; this.empty = empty;
        if (cache != null) addSource(cache, value -> {
            if (!receivedRemote && allowed()) setValue(value);
        });
    }

    private boolean allowed() {
        return ownerId == null || FirebaseAuth.getInstance().getCurrentUser() != null
                && ownerId.equals(FirebaseAuth.getInstance().getCurrentUser().getUid());
    }

    @Override protected void onActive() {
        receivedRemote = false;
        super.onActive();
        if (ownerId != null) {
            authListener = auth -> {
                if (!allowed()) { stopProfile(); detach(); setValue(empty); finish(new SecurityException("ACCOUNT_CHANGED")); }
                else { watchProfile(); if (registration == null) attach(); }
            };
            FirebaseAuth.getInstance().addAuthStateListener(authListener);
        } else attach();
    }

    @Override protected void onInactive() {
        detach();
        if (authListener != null) FirebaseAuth.getInstance().removeAuthStateListener(authListener);
        authListener = null;
        stopProfile();
        // A request already initiated by a visible screen may finish without retaining a listener.
        if (!pending.isEmpty() && allowed()) fetchOnce();
        super.onInactive();
    }

    private void stopProfile() {
        if (profileRegistration != null) profileRegistration.remove();
        profileRegistration = null; profileOwner = ""; profileVersion = "";
    }
    private void watchProfile() {
        if (ownerId == null || !allowed() || ownerId.equals(profileOwner)) return;
        stopProfile(); profileOwner = ownerId; final String expected = ownerId;
        profileRegistration = com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("users").document(expected)
                .addSnapshotListener((snapshot, error) -> {
                    if (!hasActiveObservers() || !expected.equals(ownerId) || !allowed() || snapshot == null
                            || error != null || snapshot.getMetadata().isFromCache()) return;
                    String version = snapshot.exists() ? snapshot.getString("role") + ":" + snapshot.getString("status")
                            + ":" + snapshot.getString("sellerStatus") : "deleted";
                    boolean changed = !profileVersion.isEmpty() && !version.equals(profileVersion);
                    profileVersion = version;
                    if (changed || registration == null) attach();
                });
    }

    private void detach() {
        generation++;
        if (registration != null) registration.remove();
        registration = null;
    }

    private void attach() {
        detach();
        receivedRemote = false;
        if (!allowed()) { setValue(empty); return; }
        int request = generation;
        registration = query.addSnapshotListener(MetadataChanges.INCLUDE, (snapshot, error) -> {
            if (request != generation || !allowed()) return;
            if (error != null) {
                if (error.getCode() == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                    receivedRemote = true; setValue(empty);
                }
                detach(); finish(error); return;
            }
            if (snapshot == null) return;
            if (receivedRemote && snapshot.getDocumentChanges().isEmpty()) {
                if (!snapshot.getMetadata().isFromCache()) finish(null);
                return;
            }
            // An empty, incomplete local query is not evidence that server documents were deleted.
            if (snapshot.getMetadata().isFromCache() && snapshot.isEmpty() && !receivedRemote) return;
            try {
                T value = mapper.apply(snapshot);
                receivedRemote = true; setValue(value);
                if (!snapshot.getMetadata().isFromCache()) finish(null);
            } catch (RuntimeException invalid) { finish(invalid); }
        });
    }

    /** Change an explicitly authenticated owner without displaying the previous account's list. */
    public void changeOwner(String nextOwner) {
        if (java.util.Objects.equals(ownerId, nextOwner)) return;
        stopProfile(); detach(); ownerId = nextOwner; receivedRemote = true; setValue(empty);
        if (hasActiveObservers()) watchProfile();
    }

    public void refresh(Query nextQuery, Completion callback) {
        Runnable work = () -> {
            query = nextQuery;
            if (callback != null) pending.add(callback);
            if (!allowed()) { setValue(empty); finish(new SecurityException("ACCOUNT_CHANGED")); return; }
            if (hasActiveObservers()) attach(); else fetchOnce();
        };
        if (Looper.myLooper() == Looper.getMainLooper()) work.run(); else main.post(work);
    }

    private void fetchOnce() {
        int request = ++generation;
        query.get(Source.SERVER).addOnSuccessListener(snapshot -> {
            if (request != generation || !allowed()) return;
            try { receivedRemote = true; setValue(mapper.apply(snapshot)); finish(null); }
            catch (RuntimeException invalid) { finish(invalid); }
        }).addOnFailureListener(error -> { if (request == generation) finish(error); });
    }

    private void finish(Exception error) {
        List<Completion> callbacks = new ArrayList<>(pending); pending.clear();
        for (Completion callback : callbacks) callback.complete(error);
    }
}
