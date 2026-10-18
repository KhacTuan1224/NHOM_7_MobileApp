package com.rescuefarm.ui.engagement;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.DocumentSnapshot;

/** One foreground listener for the signed-in actor, shared by every notification button. */
public class UnreadNotificationsViewModel extends ViewModel {
    private final FirebaseAuth auth = FirebaseAuth.getInstance();
    private final MutableLiveData<Integer> count = new MutableLiveData<>(0);
    private final FirebaseAuth.AuthStateListener authListener = ignored -> bindUser();
    private ListenerRegistration registration;
    private String userId = "";
    private int generation;
    private boolean started;

    public LiveData<Integer> count() { return count; }

    public void start() {
        if (started) return;
        started = true;
        bindUser();
        auth.addAuthStateListener(authListener);
    }

    public void stop() {
        started = false;
        auth.removeAuthStateListener(authListener);
        detach();
        userId = "";
        count.setValue(0);
    }

    private void detach() {
        generation++;
        if (registration != null) registration.remove();
        registration = null;
    }

    private void bindUser() {
        if (!started) return;
        String uid = auth.getCurrentUser() == null ? "" : auth.getCurrentUser().getUid();
        if (uid.equals(userId) && registration != null) return;
        detach();
        if (!uid.equals(userId) || uid.isEmpty()) count.setValue(0);
        userId = uid;
        if (uid.isEmpty()) return;
        int request = generation;
        registration = FirebaseFirestore.getInstance().collection("notifications")
                .whereEqualTo("userId", uid).addSnapshotListener((snapshot, error) -> {
                    String current = auth.getCurrentUser() == null ? "" : auth.getCurrentUser().getUid();
                    if (!started || request != generation || !uid.equals(current)) return;
                    if (error != null) { count.setValue(0); return; }
                    if (snapshot == null) return;
                    int unread = 0;
                    // Matches the inbox's treatment of legacy documents without isRead.
                    for (DocumentSnapshot document : snapshot.getDocuments())
                        if (uid.equals(document.getString("userId"))
                                && !Boolean.TRUE.equals(document.getBoolean("isRead"))) unread++;
                    count.setValue(unread);
                });
    }

    @Override protected void onCleared() { stop(); }
}
