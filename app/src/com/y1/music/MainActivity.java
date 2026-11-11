package com.y1.music;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.widget.TextView;

// Phase 3 test screen: load cache or rescan, log counts and timings.
public class MainActivity extends Activity {
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        final TextView t = new TextView(this);
        t.setTextSize(18);
        t.setTextColor(Color.WHITE);
        t.setBackgroundColor(Color.BLACK);
        t.setText("loading...");
        setContentView(t);
        new Thread(new Runnable() { public void run() {
            Library lib = new Library(getFilesDir());
            long t0 = SystemClock.elapsedRealtime();
            String how = "cache";
            if (!lib.load()) {
                how = "scan";
                try { lib.rescan(); } catch (Exception e) { Log.e("Y1LIB", "scan failed", e); }
            }
            final String s = how + ": " + lib.songs.size() + " songs, " + lib.artists().size() + " artists, "
                + lib.albums(null).size() + " albums, " + (SystemClock.elapsedRealtime() - t0) + " ms";
            Log.i("Y1LIB", s);
            runOnUiThread(new Runnable() { public void run() { t.setText(s); } });
        } }).start();
    }
}
