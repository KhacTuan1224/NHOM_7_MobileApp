package com.rescuefarm.ui.common;

import android.text.Editable;
import android.text.TextWatcher;
import android.view.MotionEvent;
import android.widget.EditText;
import java.util.function.BooleanSupplier;

/** User interaction opens suggestions; restored text/focus alone never does. */
public final class HomeSearchInput {
    private HomeSearchInput() { }
    public static void bind(EditText input, BooleanSupplier active, Runnable show) {
        input.setOnTouchListener((v,event)->{
            if(active.getAsBoolean()&&event.getActionMasked()==MotionEvent.ACTION_UP)show.run();
            return false;
        });
        input.setOnClickListener(v->{if(active.getAsBoolean())show.run();});
        input.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s,int start,int count,int after){ }
            public void onTextChanged(CharSequence s,int start,int before,int count){
                if(active.getAsBoolean()&&input.hasFocus())show.run();
            }
            public void afterTextChanged(Editable s){ }
        });
    }
}
