package com.rescuefarm.service.notification;

import com.rescuefarm.service.backend.RenderBackendClient;
import org.json.JSONObject;

/** Best-effort trusted-backend notifications for new Admin work queue items. */
public final class AdminNotificationClient {
    private AdminNotificationClient() { }

    public static void notifyAdmins(String eventType, String referenceId, String resourceType) {
        try {
            JSONObject request = new JSONObject();
            request.put("eventType", eventType);
            request.put("referenceId", referenceId);
            if (resourceType != null && !resourceType.trim().isEmpty())
                request.put("resourceType", resourceType.trim());
            RenderBackendClient.post("/notify-admins", request, new RenderBackendClient.Callback() {
                @Override public void onSuccess(JSONObject response) { }
                @Override public void onError(String message) { }
            });
        } catch (org.json.JSONException ignored) { }
    }
}
