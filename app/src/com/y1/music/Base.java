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
