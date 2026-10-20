package com.rescuefarm.data.repository;

import com.google.firebase.auth.FirebaseAuth;
import com.rescuefarm.service.backend.RenderBackendClient;
import org.json.JSONObject;

/** All reservations and private program data are owned by the trusted backend. */
public final class RescueProgramsRepository {
    public void request(String path, JSONObject body, RenderBackendClient.Callback callback) {
        if (FirebaseAuth.getInstance().getCurrentUser() == null)
            RenderBackendClient.postGuest("/rescue/" + path, body, callback);
        else RenderBackendClient.post("/rescue/" + path, body, callback);
    }
}
