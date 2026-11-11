package com.y1.music;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.widget.TextView;

// Phase 2 throwaway: play each test file 8 s, log start time/position drift/errors, and cover extraction.
public class SpikeActivity extends Activity {
    static final String D = "/storage/sdcard1/Spike/";
    static final String[] F = {"n1.flac", "n2.flac", "h1.flac", "h2.flac", "m1.mp3", "a1.m4a", "n1.flac"};
    TextView t;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        t = new TextView(this);
        t.setTextSize(14);
        setContentView(t);
        new Thread(new Runnable() { public void run() { go(); } }).start();
    }

    void out(final String s) {
        Log.i("Y1SPIKE", s);
        runOnUiThread(new Runnable() { public void run() { t.append(s + "\n"); } });
    }

    void go() {
        for (String f : F) {
            try { cover(f); } catch (Throwable e) { out(f + " COVER EXC " + e); }
            try { play(f); } catch (Throwable e) { out(f + " PLAY EXC " + e); }
        }
        out("DONE");
    }

    void cover(String f) throws Exception {
        long t0 = SystemClock.elapsedRealtime();
        MediaMetadataRetriever r = new MediaMetadataRetriever();
        r.setDataSource(D + f);
        byte[] p = r.getEmbeddedPicture();
        long t1 = SystemClock.elapsedRealtime();
        String s = f + " COVER bytes=" + (p == null ? "null" : p.length) + " fetch=" + (t1 - t0) + "ms";
        if (p != null) {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inSampleSize = 4; // ponytail: crude downscale, real code sizes to 300px
            Bitmap bm = BitmapFactory.decodeByteArray(p, 0, p.length, o);
            s += " decode=" + (SystemClock.elapsedRealtime() - t1) + "ms " + (bm == null ? "FAIL" : bm.getWidth() + "x" + bm.getHeight());
        }
        r.release();
        out(s);
    }

    void play(String f) throws Exception {
        final MediaPlayer m = new MediaPlayer();
        final String[] err = {null};
        m.setOnErrorListener(new MediaPlayer.OnErrorListener() {
            public boolean onError(MediaPlayer mp, int w, int x) { err[0] = w + "/" + x; return true; }
        });
        long t0 = SystemClock.elapsedRealtime();
        m.setDataSource(D + f);
        m.prepare();
        m.start();
        long start = SystemClock.elapsedRealtime() - t0;
        // wait for position to move off 0
        long tm = SystemClock.elapsedRealtime();
        while (m.getCurrentPosition() == 0 && SystemClock.elapsedRealtime() - tm < 3000) Thread.sleep(10);
        long first = SystemClock.elapsedRealtime() - t0;
        int p0 = m.getCurrentPosition();
        long w0 = SystemClock.elapsedRealtime();
        Thread.sleep(8000);
        int p1 = m.getCurrentPosition();
        long w1 = SystemClock.elapsedRealtime();
        out(f + " PLAY prepare+start=" + start + "ms firstPos=" + first + "ms dur=" + m.getDuration()
            + " advanced=" + (p1 - p0) + "ms/wall=" + (w1 - w0) + "ms err=" + err[0]);
        m.release();
    }
}
