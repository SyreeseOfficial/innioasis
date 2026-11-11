package com.y1.music;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.media.AudioManager;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.view.KeyEvent;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Cover + title/artist/album + battery/shuffle/volume. Wheel = volume. Polls the service twice a second (cheap: views are
 *  only touched when something changed, and not at all while the backlight is off). */
public class NowPlayingActivity extends Base {
    ImageView cover;
    TextView title, artist, album, status;
    AudioManager am;
    Handler h = new Handler();
    Library.Song shown;
    volatile String coverPath; // path whose cover is wanted; queued cover jobs for other songs skip themselves
    static final java.util.concurrent.ExecutorService coverIO = java.util.concurrent.Executors.newSingleThreadExecutor();
    int bat = -1, maxVol;
    String lastStatus = "";

    /** Battery level arrives as a broadcast (once now, then only on change) instead of re-registering every tick. */
    final BroadcastReceiver batRx = new BroadcastReceiver() {
        public void onReceive(Context c, Intent i) {
            bat = i.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) * 100 / Math.max(1, i.getIntExtra(BatteryManager.EXTRA_SCALE, 100));
        }
    };

    LinearLayout rootView;
    TextView tv(int size, int color, int lines) {
        TextView t = new TextView(this);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setMaxLines(lines);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        t.setPadding(0, 0, 0, 8);
        return t;
    }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        am = (AudioManager) getSystemService(AUDIO_SERVICE);
        maxVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        LinearLayout root = new LinearLayout(this);
        rootView = root;
        root.setPadding(12, 12, 12, 12);
        cover = new ImageView(this);
        cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
        root.addView(cover, new LinearLayout.LayoutParams(240, 240));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(16, 0, 0, 0);
        title = tv(22, 0, 4);
        artist = tv(18, 0, 2);
        album = tv(16, 0, 2);
        status = tv(16, 0, 3);
        col.addView(title); col.addView(artist); col.addView(album);
        LinearLayout.LayoutParams fill = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1);
        root.addView(col, fill);
        col.addView(new android.view.View(this), new LinearLayout.LayoutParams(1, 0, 1)); // spacer pushes status down
        col.addView(status);
        setContentView(root);
    }

    @Override void onTheme() {
        if (rootView == null) return;
        rootView.setBackgroundColor(bg());
        title.setTextColor(fg()); artist.setTextColor(fg());
        album.setTextColor(accent()); status.setTextColor(accent());
    }

    final Runnable tick = new Runnable() {
        public void run() {
            if (!dark) refresh(); // backlight off: nobody is looking, the first tick after wake catches up
            h.postDelayed(this, 500);
        }
    };

    @Override protected void onResume() {
        super.onResume();
        Intent i = registerReceiver(batRx, new IntentFilter(Intent.ACTION_BATTERY_CHANGED)); // returns the sticky level now; changes follow via batRx
        if (i != null) batRx.onReceive(this, i);
        tick.run();
    }
    @Override protected void onPause() {
        super.onPause();
        h.removeCallbacks(tick);
        unregisterReceiver(batRx);
    }

    void refresh() {
        Library.Song s = PlayerService.cur();
        if (s != shown) {
            shown = s;
            title.setText(s == null ? "Nothing playing" : s.title);
            artist.setText(s == null ? "" : s.artist);
            album.setText(s == null ? "" : s.album);
            loadCover(s);
        }
        String st = (PlayerService.playing ? "" : "Paused\n") + (PlayerService.shuffle ? "SHUFFLE  " : "")
            + "Bat " + bat + "%\nVol " + am.getStreamVolume(AudioManager.STREAM_MUSIC) + "/" + maxVol;
        if (!st.equals(lastStatus)) { lastStatus = st; status.setText(st); }
    }

    void loadCover(final Library.Song s) {
        cover.setImageBitmap(null);
        coverPath = s == null ? null : s.path;
        if (s == null || !getSharedPreferences("p", 0).getBoolean("covers", true)) return;
        coverIO.execute(new Runnable() { public void run() {
            if (!s.path.equals(coverPath)) return; // user already skipped on: don't decode art nobody will see
            final Bitmap bm = Covers.get(NowPlayingActivity.this, s);
            runOnUiThread(new Runnable() { public void run() { if (s.path.equals(coverPath)) cover.setImageBitmap(bm); } });
        } });
    }

    @Override public boolean onKeyDown(int k, KeyEvent e) {
        int v = 0;
        if (k == KeyEvent.KEYCODE_DPAD_RIGHT) v = 1;       // clockwise = louder
        else if (k == KeyEvent.KEYCODE_DPAD_LEFT) v = -1;
        if (v != 0) {
            int n = Math.max(0, Math.min(am.getStreamMaxVolume(AudioManager.STREAM_MUSIC), am.getStreamVolume(AudioManager.STREAM_MUSIC) + v));
            am.setStreamVolume(AudioManager.STREAM_MUSIC, n, 0);
            refresh();
            return true;
        }
        String a = MediaButtonReceiver.actionFor(k);
        if (a != null) { PlayerService.send(this, a); return true; }
        return super.onKeyDown(k, e);
    }
}
