package com.rescuefarm;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.widget.EditText;
import androidx.navigation.Navigation;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Actual Activity rotation with no sign-in or business action and Firestore network disabled. */
@RunWith(AndroidJUnit4.class)
public class CourseRotationEvidenceTest {
    @Test public void loginInputSurvivesPortraitLandscapeAndActivityRecreation() throws Exception {
        assumeTrue("Requires a clean emulator session; never signs out a real user",FirebaseAuth.getInstance().getCurrentUser()==null);
        assumeTrue("Requires isolated demo Firebase and HTTP APIs; enable courseAuditIsolated only after setup",
                "true".equals(InstrumentationRegistry.getArguments().getString("courseAuditIsolated")));
        Tasks.await(FirebaseFirestore.getInstance().disableNetwork(),10,TimeUnit.SECONDS);
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity->{
                Navigation.findNavController(activity,R.id.main).navigate(R.id.loginFragment);
                activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            });
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(activity->((EditText)activity.findViewById(R.id.emailInput)).setText("rotation-audit@example.test"));
            scenario.onActivity(activity->activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE));
            long deadline=System.currentTimeMillis()+10000;
            final boolean[] landscape={false};
            while(!landscape[0]&&System.currentTimeMillis()<deadline) {
                InstrumentationRegistry.getInstrumentation().waitForIdleSync();
                scenario.onActivity(activity->landscape[0]=activity.getResources().getConfiguration().orientation==Configuration.ORIENTATION_LANDSCAPE);
                if(!landscape[0])Thread.sleep(100);
            }
            assertTrue("Device must actually enter landscape",landscape[0]);
            scenario.onActivity(activity->assertEquals("rotation-audit@example.test",((EditText)activity.findViewById(R.id.emailInput)).getText().toString()));
            scenario.recreate();
            scenario.onActivity(activity->{
                assertEquals("rotation-audit@example.test",((EditText)activity.findViewById(R.id.emailInput)).getText().toString());
                activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            });
        }
        // Do not re-enable networking: queued startup work must not flush to production.
    }
}
