package com.rescuefarm.ui.rescue;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;
import com.google.firebase.auth.FirebaseAuth;
import com.rescuefarm.data.repository.RescueProgramsRepository;
import com.rescuefarm.service.backend.RenderBackendClient;
import org.json.JSONArray;
import org.json.JSONObject;

public final class RescueProgramsViewModel extends ViewModel {
    public static final class State {
        public final boolean loading;
        public final String screen, message;
        public final JSONObject value;
        State(boolean loading, String screen, JSONObject value, String message) {
            this.loading = loading; this.screen = screen; this.value = value; this.message = message;
        }
    }
    private final RescueProgramsRepository repository = new RescueProgramsRepository();
    private final MutableLiveData<State> state = new MutableLiveData<>();
    private final SavedStateHandle saved;
    private int generation;
    private String screen = "catalog", path = "catalog";
    private JSONObject query = new JSONObject(), value = new JSONObject();
    public RescueProgramsViewModel(SavedStateHandle saved) { this.saved = saved; }
    public LiveData<State> state() { return state; }
    public void clearMessage() {
        State previous = state.getValue();
        if (previous != null && !previous.message.isEmpty())
            state.setValue(new State(previous.loading, previous.screen, previous.value, ""));
    }
    public String requestId() {
        String id = saved.get("createRequestId");
        if (id == null) { id = java.util.UUID.randomUUID().toString(); saved.set("createRequestId", id); }
        return id;
    }
    private String uid() { return FirebaseAuth.getInstance().getCurrentUser() == null ? "" : FirebaseAuth.getInstance().getCurrentUser().getUid(); }
    public void load(String screen, String path, JSONObject query) {
        generation++;
        this.screen = screen; this.path = path; this.query = query;
        saved.set("screen", screen); saved.set("path", path); saved.set("query", query.toString());
        value = new JSONObject(); state.setValue(new State(false, screen, value, "")); fetch(false);
    }
    public void restore() {
        String screen = saved.get("screen"), path = saved.get("path"), body = saved.get("query");
        try { load(screen == null ? "catalog" : screen, path == null ? "catalog" : path, body == null ? new JSONObject() : new JSONObject(body)); }
        catch (org.json.JSONException error) { load("catalog", "catalog", new JSONObject()); }
    }
    public void refresh() {
        State previous = state.getValue(); if (previous != null && previous.loading) return;
        value = new JSONObject(); fetch(false);
    }
    public void more() { fetch(true); }
    private void fetch(boolean more) {
        State previous = state.getValue(); if (previous != null && previous.loading) return;
        final int current = ++generation; final String owner = uid(), currentScreen = screen;
        JSONObject body;
        try { body = new JSONObject(query.toString()); if (more) body.put("cursor", value.optString("cursor")); }
        catch (org.json.JSONException error) { return; }
        state.setValue(new State(true, screen, value, ""));
        repository.request(path, body, new RenderBackendClient.Callback() {
            public void onSuccess(JSONObject result) {
                if (current != generation || !owner.equals(uid())) return;
                if (more) {
                    JSONArray combined = value.optJSONArray("items"), next = result.optJSONArray("items");
                    if (combined == null) combined = new JSONArray();
                    if (next != null) for (int i = 0; i < next.length(); i++) combined.put(next.opt(i));
                    try { result.put("items", combined); } catch (org.json.JSONException ignored) { }
                }
                value = result; state.setValue(new State(false, currentScreen, value, ""));
            }
            public void onError(String error) {
                if (current == generation && owner.equals(uid())) state.setValue(new State(false, currentScreen, value, error));
            }
        });
    }
    public void action(String path, JSONObject body, Runnable success) {
        State previous = state.getValue(); if (previous != null && previous.loading) return;
        final int current = ++generation; final String owner = uid();
        state.setValue(new State(true, screen, value, ""));
        repository.request(path, body, new RenderBackendClient.Callback() {
            public void onSuccess(JSONObject result) {
                if (current != generation || !owner.equals(uid())) return;
                state.setValue(new State(false, screen, value, ""));
                if (path.equals("create")) saved.remove("createRequestId");
                success.run();
            }
            public void onError(String error) {
                if (current == generation && owner.equals(uid())) state.setValue(new State(false, screen, value, error));
            }
        });
    }
    public void invalidate() { generation++; value = new JSONObject(); state.setValue(new State(false, screen, value, "")); }
}
