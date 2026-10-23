package com.rescuefarm;

import static org.junit.Assert.*;

import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LifecycleRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.firestore.FieldPath;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreSettings;
import com.google.firebase.firestore.Query;
import com.rescuefarm.data.remote.firebase.SnapshotLiveData;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Run with Firestore emulator on host:8088. A named demo app never connects to production. */
@RunWith(AndroidJUnit4.class)
public final class SnapshotLiveDataTest {
    private static final String PROJECT = "demo-rescuefarm", HOST = "127.0.0.1";
    private FirebaseApp app;
    private FirebaseFirestore store;
    private SnapshotLiveData<List<String>> source;
    private Owner owner;
    private final String run = "snapshot_" + UUID.randomUUID().toString().replace("-", "");
    private final BlockingQueue<List<String>> events = new LinkedBlockingQueue<>();
    private final AtomicInteger received = new AtomicInteger();
    private static final class Owner implements LifecycleOwner {
        final LifecycleRegistry lifecycle = new LifecycleRegistry(this);
        @Override public Lifecycle getLifecycle() { return lifecycle; }
    }
    private void main(Runnable action) { InstrumentationRegistry.getInstrumentation().runOnMainSync(action); }
    @Before public void setUp() throws Exception {
        app = FirebaseApp.initializeApp(InstrumentationRegistry.getInstrumentation().getTargetContext(),
                new FirebaseOptions.Builder().setProjectId(PROJECT).setApplicationId("1:123456789:android:realtime")
                        .setApiKey("emulator-only").build(), run);
        store = FirebaseFirestore.getInstance(app);
        store.setFirestoreSettings(new FirebaseFirestoreSettings.Builder().setPersistenceEnabled(false).build());
        store.useEmulator(HOST, 8088);
        write("a", "A", true); write("b", "B", true); write("c", "C", true);
    }
    @After public void tearDown() throws Exception {
        if (owner != null) main(() -> owner.lifecycle.setCurrentState(Lifecycle.State.DESTROYED));
        if (store != null) Tasks.await(store.terminate(), 10, TimeUnit.SECONDS);
        for (String key : new String[]{"a", "b", "c"}) rest("DELETE", key, "");
        if (app != null) app.delete();
    }
    private Query query(int count) {
        return store.collection("categories").whereEqualTo("active", true).whereEqualTo("testRun", run)
                .orderBy(FieldPath.documentId()).limit(count);
    }
    @Test public void createUpdateRemoveAndPageWindowStayLiveUntilLifecycleStops() throws Exception {
        main(() -> {
            owner = new Owner(); owner.lifecycle.setCurrentState(Lifecycle.State.CREATED);
            source = new SnapshotLiveData<>(query(2), null, null, Collections.emptyList(), snapshot -> {
                List<String> names = new ArrayList<>(); snapshot.forEach(row -> names.add(row.getString("name"))); return names;
            });
            source.observe(owner, values -> { events.add(values); received.incrementAndGet(); });
            owner.lifecycle.setCurrentState(Lifecycle.State.STARTED);
        });
        expect("A", "B");
        write("a", "Changed", true); expect("Changed", "B");
        // A record leaving the query must immediately be replaced by the next record.
        write("b", "B", false); expect("Changed", "C");
        main(() -> source.refresh(query(3), error -> assertNull(error)));
        write("b", "Returned", true); expect("Changed", "Returned", "C");
        // Replacing a populated query with an empty query cannot retain the previous values.
        main(() -> source.refresh(store.collection("categories").whereEqualTo("active", true)
                .whereEqualTo("testRun", run + "_missing"), error -> assertNull(error)));
        expect();
        main(() -> owner.lifecycle.setCurrentState(Lifecycle.State.CREATED));
        int before = received.get(); write("a", "Background", true);
        Thread.sleep(400); assertEquals(before, received.get());
        main(() -> { source.refresh(query(3), error -> assertNull(error)); owner.lifecycle.setCurrentState(Lifecycle.State.STARTED); });
        expect("Background", "Returned", "C");
        rest("DELETE", "a", ""); expect("Returned", "C");
    }
    private void expect(String... names) throws Exception {
        List<String> expected = java.util.Arrays.asList(names); long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        List<String> actual = null;
        while (System.nanoTime() < until) {
            actual = events.poll(1, TimeUnit.SECONDS); if (expected.equals(actual)) return;
        }
        fail("Expected " + expected + "; last snapshot " + actual);
    }
    private void write(String key, String name, boolean active) throws Exception {
        JSONObject fields = new JSONObject().put("name", new JSONObject().put("stringValue", name))
                .put("active", new JSONObject().put("booleanValue", active))
                .put("testRun", new JSONObject().put("stringValue", run));
        rest("PATCH", key, new JSONObject().put("fields", fields).toString());
    }
    private void rest(String method, String key, String body) throws Exception {
        // Administrative REST requests are hard-coded to the isolated local emulator.
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        String path = "/v1/projects/" + PROJECT + "/databases/(default)/documents/categories/" + run + key;
        try (Socket socket = new Socket()) {
            socket.connect(new java.net.InetSocketAddress(HOST, 8088), 5000);
            socket.setSoTimeout(10000);
            String headers = method + " " + path + " HTTP/1.1\r\nHost: " + HOST
                    + "\r\nAuthorization: Bearer owner\r\nContent-Type: application/json\r\nContent-Length: "
                    + bytes.length + "\r\nConnection: close\r\n\r\n";
            socket.getOutputStream().write(headers.getBytes(StandardCharsets.UTF_8)); socket.getOutputStream().write(bytes);
            String status = new java.io.BufferedReader(new java.io.InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8)).readLine();
            assertNotNull("No response from local Firestore emulator", status);
            assertTrue(status, status.startsWith("HTTP/1.1 200") || method.equals("DELETE") && status.startsWith("HTTP/1.1 404"));
        }
    }
}
