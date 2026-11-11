package com.y1.music;

import android.app.Activity;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.media.AudioManager;
import android.media.MediaMetadataRetriever;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.view.KeyEvent;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Cover + title/artist/album + battery/shuffle/volume. Wheel = volume. Polls the service twice a second. */
public class NowPlayingActivity extends Activity {
    ImageView cover;
    TextView title, artist, album, status;
    AudioManager am;
    Handler h = new Handler();
    Library.Song shown;
    String coverPath; // path whose cover is loaded / loading (the "last cover" cache is the ImageView itself)

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
        LinearLayout root = new LinearLayout(this);
        root.setBackgroundColor(Color.BLACK);
        root.setPadding(12, 12, 12, 12);
        cover = new ImageView(this);
        cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
        root.addView(cover, new LinearLayout.LayoutParams(240, 240));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(16, 0, 0, 0);
        title = tv(22, Color.WHITE, 4);
        artist = tv(18, Color.WHITE, 2);
        album = tv(16, Color.LTGRAY, 2);
        status = tv(16, Color.LTGRAY, 3);
        col.addView(title); col.addView(artist); col.addView(album);
        LinearLayout.LayoutParams fill = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1);
        root.addView(col, fill);
        col.addView(new android.view.View(this), new LinearLayout.LayoutParams(1, 0, 1)); // spacer pushes status down
        col.addView(status);
        setContentView(root);
    }

    final Runnable tick = new Runnable() {
        public void run() {
            refresh();
            h.postDelayed(this, 500);
        }
    };

    @Override protected void onResume() { super.onResume(); tick.run(); }
    @Override protected void onPause() { super.onPause(); h.removeCallbacks(tick); }

    void refresh() {
        Library.Song s = PlayerService.cur();
        if (s != shown) {
            shown = s;
            title.setText(s == null ? "Nothing playing" : s.title);
            artist.setText(s == null ? "" : s.artist);
            album.setText(s == null ? "" : s.album);
            loadCover(s);
        }
        Intent bat = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        int pct = bat == null ? -1 : bat.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) * 100 / Math.max(1, bat.getIntExtra(BatteryManager.EXTRA_SCALE, 100));
        status.setText((PlayerService.playing ? "" : "Paused\n") + (PlayerService.shuffle ? "SHUFFLE  " : "")
            + "Bat " + pct + "%\nVol " + am.getStreamVolume(AudioManager.STREAM_MUSIC) + "/" + am.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
    }

    void loadCover(final Library.Song s) {
        cover.setImageBitmap(null);
        coverPath = s == null ? null : s.path;
        if (s == null || !getSharedPreferences("p", 0).getBoolean("covers", true)) return;
        new Thread(new Runnable() { public void run() {
            Bitmap bm = null;
            try {
                MediaMetadataRetriever m = new MediaMetadataRetriever();
                m.setDataSource(s.path);
                byte[] d = m.getEmbeddedPicture();
                m.release();
                if (d == null) { // no embedded art: fall back to cover.jpg / folder.jpg next to the song
                    for (String n : new String[] {"cover.jpg", "folder.jpg"}) {
                        java.io.File f = new java.io.File(new java.io.File(s.path).getParentFile(), n);
                        if (!f.exists()) continue;
                        d = new byte[(int) f.length()];
                        java.io.DataInputStream in = new java.io.DataInputStream(new java.io.FileInputStream(f));
                        in.readFully(d);
                        in.close();
                        break;
                    }
                }
                if (d != null) {
                    BitmapFactory.Options o = new BitmapFactory.Options();
                    o.inJustDecodeBounds = true;
                    BitmapFactory.decodeByteArray(d, 0, d.length, o);
                    int n = 1;
                    while (o.outWidth / (n * 2) >= 300) n *= 2; // decode small: ~300-600 px
                    o = new BitmapFactory.Options();
                    o.inSampleSize = n;
                    bm = BitmapFactory.decodeByteArray(d, 0, d.length, o);
                }
            } catch (Throwable e) { android.util.Log.w("Y1COVER", "no cover " + s.path, e); }
            final Bitmap fb = bm; android.util.Log.i("Y1COVER", s.path + " -> " + (bm == null ? "null" : bm.getWidth() + "x" + bm.getHeight()));
            runOnUiThread(new Runnable() { public void run() { if (s.path.equals(coverPath)) cover.setImageBitmap(fb); } });
        } }).start();
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
