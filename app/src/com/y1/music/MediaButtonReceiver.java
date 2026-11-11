package com.y1.music;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.view.KeyEvent;

/** Headset / hardware media keys while we are not in the foreground (screen off etc.). */
public class MediaButtonReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent i) {
        KeyEvent e = (KeyEvent) i.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
        if (e == null || e.getAction() != KeyEvent.ACTION_DOWN || PlayerService.queue.isEmpty()) return;
        String a = actionFor(e.getKeyCode());
        if (a != null) PlayerService.send(c, a);
    }

    static String actionFor(int k) {
        if (k == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE || k == KeyEvent.KEYCODE_HEADSETHOOK
            || k == KeyEvent.KEYCODE_MEDIA_PLAY || k == KeyEvent.KEYCODE_MEDIA_PAUSE) return "toggle";
        if (k == KeyEvent.KEYCODE_MEDIA_NEXT) return "next";
        if (k == KeyEvent.KEYCODE_MEDIA_PREVIOUS) return "prev";
        return null;
    }
}
