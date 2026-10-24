package com.rescuefarm.report;

import android.app.Application;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

/** Separate package and demo-only resources; never install over a user's app. */
public final class ReportDemoApplication extends Application {
    @Override public void onCreate() {
        super.onCreate();
        FirebaseApp app = FirebaseApp.initializeApp(this);
        if (app == null || !"demo-rescuefarm".equals(app.getOptions().getProjectId()))
            throw new IllegalStateException("Report demo requires the isolated demo project");
        FirebaseAuth.getInstance().useEmulator("10.0.2.2", 9098);
        FirebaseFirestore.getInstance().useEmulator("10.0.2.2", 8088);
        FirebaseFirestore.getInstance().setFirestoreSettings(
                new com.google.firebase.firestore.FirebaseFirestoreSettings.Builder()
                        .setPersistenceEnabled(false).build());
        if (getSharedPreferences("report-demo", MODE_PRIVATE).getBoolean("offline", false))
            FirebaseFirestore.getInstance().disableNetwork();
    }
}
