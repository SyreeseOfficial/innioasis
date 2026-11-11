package com.y1.music;

import android.app.Activity;
import android.os.Handler;
import android.view.KeyEvent;
import android.view.WindowManager;

/** Backlight off after 15 s idle while music plays; any key brings it back. The screen stays "awake" so the wheel
 *  (which has no WAKE flag in the keylayout) still reaches us. A key that wakes the backlight is swallowed unless it is a media key. */
public class Base extends Activity {
    static final int IDLE_MS = 15000;
    Handler idleH = new Handler();
    boolean dark;
    static boolean guardOn;

    /** Crash-loop guard: count uncaught exceptions + log the stack. Home resets the count after 10 s of life. */
    @Override protected void onCreate(android.os.Bundle b) {
        super.onCreate(b);
        if (guardOn) return;
        guardOn = true;
        final android.content.Context c = getApplicationContext();
        final Thread.UncaughtExceptionHandler old = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            public void uncaughtException(Thread t, Throwable e) {
                try {
                    android.content.SharedPreferences p = c.getSharedPreferences("p", 0);
                    p.edit().putInt("crashes", p.getInt("crashes", 0) + 1).commit();
                    java.io.StringWriter w = new java.io.StringWriter();
                    e.printStackTrace(new java.io.PrintWriter(w));
                    PlayerService.log("CRASH " + w);
                } catch (Throwable x) { /* nothing more we can do */ }
                if (old != null) old.uncaughtException(t, e);
            }
        });
    }

    final Runnable goDark = new Runnable() {
        public void run() {
            if (PlayerService.playing) setBrightness(0f); // ponytail: 0f may be clamped by the driver; real sleep needs a .kl WAKE flag
            idleH.postDelayed(this, IDLE_MS);
        }
    };

    void setBrightness(float b) {
        WindowManager.LayoutParams p = getWindow().getAttributes();
        p.screenBrightness = b;
        getWindow().setAttributes(p);
        dark = b == 0f;
    }

    void touchIdle() {
        if (dark) setBrightness(-1f);
        idleH.removeCallbacks(goDark);
        idleH.postDelayed(goDark, IDLE_MS);
    }

    @Override protected void onResume() { super.onResume(); touchIdle(); }

    @Override protected void onPause() {
        super.onPause();
        idleH.removeCallbacks(goDark);
        setBrightness(-1f);
    }

    @Override public boolean dispatchKeyEvent(KeyEvent e) {
        boolean wasDark = dark;
        touchIdle();
        if (wasDark && MediaButtonReceiver.actionFor(e.getKeyCode()) == null) return true;
        return super.dispatchKeyEvent(e);
    }
}
