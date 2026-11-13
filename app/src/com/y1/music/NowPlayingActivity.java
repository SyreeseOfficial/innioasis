package com.y1.music;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.media.AudioManager;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Cover + title/artist/album + battery/shuffle/volume. Wheel = volume. Polls the service twice a second (cheap: views are
 *  only touched when something changed, and not at all while the backlight is off). */
public class NowPlayingActivity extends Base {
    ImageView cover;
    TextView title, artist, album, status, batTxt, volTxt;
    Icon batIcon, volIcon;
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

    /** Tiny battery / speaker glyph, drawn in the accent color so it follows the theme. */
    class Icon extends View {
        final boolean spk; int pct = 100;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        Icon(boolean spk) { super(NowPlayingActivity.this); this.spk = spk; }
        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight();
            p.setColor(accent());
            p.setStrokeWidth(2);
            if (spk) {
                Path s = new Path();
                s.moveTo(.05f * w, .36f * h); s.lineTo(.28f * w, .36f * h); s.lineTo(.55f * w, .14f * h);
                s.lineTo(.55f * w, .86f * h); s.lineTo(.28f * w, .64f * h); s.lineTo(.05f * w, .64f * h); s.close();
                p.setStyle(Paint.Style.FILL); c.drawPath(s, p);
                p.setStyle(Paint.Style.STROKE);
                c.drawArc(new RectF(.4f * w, .3f * h, .85f * w, .7f * h), -55, 110, false, p);
            } else {
                RectF body = new RectF(.28f * w, .12f * h, .72f * w, .96f * h);
                p.setStyle(Paint.Style.STROKE); c.drawRoundRect(body, 3, 3, p);
                p.setStyle(Paint.Style.FILL);
                c.drawRect(.42f * w, .02f * h, .58f * w, .12f * h, p);
                float top = body.bottom - 4 - (body.height() - 8) * Math.max(0, Math.min(100, pct)) / 100f;
                c.drawRect(body.left + 4, top, body.right - 4, body.bottom - 4, p);
            }
        }
    }

    TextView stat() { TextView t = tv(16, 0, 1); t.setPadding(10, 0, 0, 0); return t; }
    LinearLayout row(Icon i, TextView t) {
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.addView(i, new LinearLayout.LayoutParams(22, 24));
        r.addView(t);
        r.setPadding(0, 6, 0, 0);
        return r;
    }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        am = (AudioManager) getSystemService(AUDIO_SERVICE);
        maxVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        LinearLayout root = new LinearLayout(this);
        rootView = root;
        root.setPadding(24, 24, 12, 24);
        cover = new ImageView(this);
        cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
        root.addView(cover, new LinearLayout.LayoutParams(312, 312)); // square, fills the height minus padding
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(18, 0, 0, 0);
        artist = tv(15, 0, 3);
        album = tv(15, 0, 3);
        title = tv(20, 0, 6);
        title.setPadding(0, 10, 0, 0);
        status = tv(13, 0, 1);
        batTxt = stat(); volTxt = stat();
        batIcon = new Icon(false); volIcon = new Icon(true);
        col.addView(artist); col.addView(album); col.addView(title);
        col.addView(new View(this), new LinearLayout.LayoutParams(1, 0, 1)); // spacer pushes status down
        col.addView(status); col.addView(row(batIcon, batTxt)); col.addView(row(volIcon, volTxt));
        root.addView(col, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1));
        setContentView(root);
    }

    @Override void onTheme() {
        if (rootView == null) return;
        rootView.setBackgroundColor(bg());
        title.setTextColor(fg()); artist.setTextColor(accent()); album.setTextColor(accent());
        status.setTextColor(accent()); batTxt.setTextColor(fg()); volTxt.setTextColor(fg());
        batIcon.invalidate(); volIcon.invalidate();
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
        String st = (PlayerService.playing ? "" : "Paused  ") + (PlayerService.shuffle ? "SHUFFLE" : "");
        String bt = bat + "%", vt = am.getStreamVolume(AudioManager.STREAM_MUSIC) + "/" + maxVol;
        if (!(st + bt + vt).equals(lastStatus)) {
            lastStatus = st + bt + vt;
            status.setText(st.trim()); batTxt.setText(bt); volTxt.setText(vt);
            batIcon.pct = bat; batIcon.invalidate();
        }
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
        if (k == KeyEvent.KEYCODE_DPAD_RIGHT || k == KeyEvent.KEYCODE_DPAD_DOWN) v = 1;       // clockwise = louder
        else if (k == KeyEvent.KEYCODE_DPAD_LEFT || k == KeyEvent.KEYCODE_DPAD_UP) v = -1;
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
