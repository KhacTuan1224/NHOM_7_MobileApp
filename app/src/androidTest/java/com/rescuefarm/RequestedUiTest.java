package com.rescuefarm;
import static org.junit.Assert.*;
import android.content.Context;
import android.view.*;
import android.widget.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.rescuefarm.ui.common.*;
import org.junit.Test;
import org.junit.runner.RunWith;
@RunWith(AndroidJUnit4.class)
public class RequestedUiTest {
 private Context context(){return new ContextThemeWrapper(InstrumentationRegistry.getInstrumentation().getTargetContext(),R.style.Theme_RescueFarm);}
 @Test public void searchRestorationStaysClosedButClickAndTypingOpen(){InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
  EditText input=new EditText(context());final boolean[] active={false};final int[] shown={0};
  HomeSearchInput.bind(input,()->active[0],()->shown[0]++);
  input.requestFocus();input.setText("khôi phục");assertEquals(0,shown[0]);
  active[0]=true;assertEquals(0,shown[0]);input.performClick();assertEquals(1,shown[0]);
  input.setText("cà rốt");assertEquals(2,shown[0]);
  active[0]=false;input.clearFocus();input.setText("quay lại");active[0]=true;assertEquals(2,shown[0]);
  input.requestFocus();assertEquals(2,shown[0]);input.performClick();input.setText("rau");assertEquals(4,shown[0]);
 });}
}
