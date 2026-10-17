package com.rescuefarm.service.notification;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.*;
import java.util.HashMap;
import java.util.Map;

/** Persist this event in the same commit as the business mutation. Delivery belongs to the backend. */
public final class NotificationOutbox {
    private NotificationOutbox() { }
    public static void enqueue(Transaction tx, FirebaseFirestore db, String kind, String type, String id) {
        tx.set(db.collection("notificationOutbox").document(), event(kind, type, id));
    }
    public static void enqueue(WriteBatch batch, FirebaseFirestore db, String kind, String type, String id) {
        batch.set(db.collection("notificationOutbox").document(), event(kind, type, id));
    }
    private static Map<String, Object> event(String kind, String type, String id) {
        Map<String, Object> value = new HashMap<>();
        value.put("kind", kind); value.put("resourceType", type); value.put("resourceId", id);
        value.put("actorId", FirebaseAuth.getInstance().getCurrentUser().getUid());
        value.put("status", "PENDING"); value.put("retryCount", 0);
        value.put("createdAt", FieldValue.serverTimestamp()); value.put("nextAttemptAt", FieldValue.serverTimestamp());
        return value;
    }
}
