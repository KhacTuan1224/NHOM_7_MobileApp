package com.rescuefarm.service.realtime;

import android.os.Handler;
import android.os.Looper;
import androidx.lifecycle.LiveData;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.rescuefarm.service.backend.RenderBackendClient;
import org.json.JSONObject;

/** Firestore push signals for API-only data; no business data is read from the signal document. */
public final class BackendSnapshotWatch extends LiveData<Long> {
    private final Handler main = new Handler(Looper.getMainLooper());
    private String scope;
    private JSONObject arguments = new JSONObject();
    private JSONObject lease;
    private ListenerRegistration registration;
    private FirebaseAuth.AuthStateListener authListener;
    private String account = "", revision = "";
    private int generation;
    private long retryMs = 1000L, sequence;
    private final Runnable reconnect = () -> { if (hasActiveObservers()) connect(); };
    private final Runnable keepalive = this::renew;
    private final Runnable notifyChange = () -> { if (hasActiveObservers()) setValue(++sequence); };

    public BackendSnapshotWatch(String scope) { this.scope = scope; }

    public void configure(String nextScope, JSONObject nextArguments) {
        String next = nextArguments == null ? "{}" : nextArguments.toString();
        if (scope.equals(nextScope) && arguments.toString().equals(next)) return;
        scope = nextScope;
        try { arguments = new JSONObject(next); } catch (Exception invalid) { arguments = new JSONObject(); }
        disconnect();
        if (hasActiveObservers()) connect();
    }
    private String uid() {
        return FirebaseAuth.getInstance().getCurrentUser() == null ? ""
                : FirebaseAuth.getInstance().getCurrentUser().getUid();
    }
    @Override protected void onActive() {
        account = uid();
        authListener = auth -> {
            if (!account.equals(uid())) { account = uid(); disconnect(); connect(); }
        };
        FirebaseAuth.getInstance().addAuthStateListener(authListener);
        connect();
    }
    @Override protected void onInactive() {
        if (authListener != null) FirebaseAuth.getInstance().removeAuthStateListener(authListener);
        authListener = null; disconnect();
    }
    private void connect() {
        if (!hasActiveObservers() || scope.isEmpty()) return;
        main.removeCallbacks(reconnect);
        int request = ++generation;
        JSONObject body;
        try { body = new JSONObject(arguments.toString()).put("scope", scope); }
        catch (Exception invalid) { return; }
        RenderBackendClient.Callback callback = new RenderBackendClient.Callback() {
            @Override public void onSuccess(JSONObject response) {
                if (request != generation || !hasActiveObservers()) { closeLease(response); return; }
                String id = response.optString("subscriptionId");
                if (!id.matches("[a-f0-9-]{36}")) { retry(); return; }
                lease = response; revision = "";
                registration = FirebaseFirestore.getInstance().collection("realtimeSubscriptions").document(id)
                        .addSnapshotListener((snapshot, error) -> {
                            if (request != generation || !hasActiveObservers()) return;
                            if (error != null) { disconnect(); retry(); return; }
                            if (snapshot == null || !snapshot.exists()) {
                                if (snapshot != null && !snapshot.getMetadata().isFromCache()) { disconnect(); retry(); }
                                return;
                            }
                            if (Boolean.TRUE.equals(snapshot.getBoolean("unavailable"))) { disconnect(); retry(); return; }
                            String next = snapshot.getString("revision");
                            if (!snapshot.getMetadata().isFromCache()) retryMs = 1000L;
                            if (next != null && !next.equals(revision)) {
                                revision = next; main.removeCallbacks(notifyChange); main.postDelayed(notifyChange, 120L);
                            }
                        });
                main.postDelayed(keepalive, 45000L);
            }
            @Override public void onError(String message) { if (request == generation && hasActiveObservers()) retry(); }
        };
        if (account.isEmpty()) RenderBackendClient.postGuest("/realtime/watch", body, callback);
        else RenderBackendClient.post("/realtime/watch", body, callback);
    }
    private JSONObject leaseBody(JSONObject value) {
        JSONObject body = new JSONObject();
        try { body.put("subscriptionId", value.optString("subscriptionId")).put("leaseToken", value.optString("leaseToken")); }
        catch (Exception ignored) { }
        return body;
    }
    private void renew() {
        if (lease == null || !hasActiveObservers()) return;
        int request = generation;
        RenderBackendClient.postGuest("/realtime/keepalive", leaseBody(lease), new RenderBackendClient.Callback() {
            public void onSuccess(JSONObject ignored) {
                if (request == generation && hasActiveObservers()) main.postDelayed(keepalive, 45000L);
            }
            public void onError(String message) {
                if (request == generation && hasActiveObservers()) { disconnect(); retry(); }
            }
        });
    }
    private void retry() {
        main.removeCallbacks(reconnect); main.postDelayed(reconnect, retryMs);
        retryMs = Math.min(60000L, retryMs * 2L);
    }
    private void closeLease(JSONObject value) {
        if (value == null || value.optString("leaseToken").isEmpty()) return;
        RenderBackendClient.postGuest("/realtime/close", leaseBody(value), new RenderBackendClient.Callback() {
            public void onSuccess(JSONObject ignored) { }
            public void onError(String ignored) { } // The server lease also expires after background/disconnection.
        });
    }
    private void disconnect() {
        generation++; main.removeCallbacks(reconnect); main.removeCallbacks(keepalive); main.removeCallbacks(notifyChange);
        if (registration != null) registration.remove(); registration = null;
        JSONObject previous = lease; lease = null; closeLease(previous);
    }
}
