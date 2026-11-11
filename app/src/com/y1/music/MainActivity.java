package com.y1.music;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.widget.TextView;

// Phase 1 debug screen: shows every key / generic motion event the app receives.
public class MainActivity extends Activity {
    TextView t;
    StringBuilder log = new StringBuilder();

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        t = new TextView(this);
        t.setTextSize(14);
        t.setTextColor(Color.WHITE);
        t.setBackgroundColor(Color.BLACK);
        t.setText("press keys...");
        setContentView(t);
    }

    void show(String s) {
        Log.i("Y1KEY", s);
        log.insert(0, s + "\n");
        if (log.length() > 700) log.setLength(700);
        t.setText(log);
    }

    @Override public boolean onKeyDown(int k, KeyEvent e) {
        show("DOWN " + KeyEvent.keyCodeToString(k) + "(" + k + ") scan=" + e.getScanCode() + " dev=" + e.getDeviceId() + " rep=" + e.getRepeatCount());
        return true; // swallow, incl. BACK
    }

    @Override public boolean onKeyUp(int k, KeyEvent e) {
        show("UP   " + KeyEvent.keyCodeToString(k) + "(" + k + ")");
        return true;
    }

    @Override public boolean onGenericMotionEvent(MotionEvent e) {
        show("MOTION act=" + e.getAction() + " src=" + e.getSource() + " scroll=" + e.getAxisValue(MotionEvent.AXIS_VSCROLL));
        return true;
    }
}
