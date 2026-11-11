package com.y1.music;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.*;

/** One reusable list screen. mode: home | artists | albums | artist (albums of one artist) | songs. */
public class MainActivity extends Activity {
    static Library LIB;

    static synchronized Library lib(Context c) {
        if (LIB == null) {
            Library l = new Library(c.getFilesDir());
            if (!l.load()) try { l.rescan(); } catch (Exception e) { /* empty library */ }
            LIB = l;
        }
        return LIB;
    }

    String mode, artist, album;
    List<String[]> rows = new ArrayList<String[]>(); // {label, extra...}
    List<Library.Song> songList;
    int sel = 0;
    ListView lv;
    BaseAdapter ad;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Intent i = getIntent();
        mode = i.getStringExtra("m");
        if (mode == null) mode = "home";
        artist = i.getStringExtra("a");
        album = i.getStringExtra("b");
        lv = new ListView(this);
        lv.setBackgroundColor(Color.BLACK);
        lv.setDivider(null);
        lv.setFocusable(false);
        lv.setOnTouchListener(new View.OnTouchListener() { // touch ignored
            public boolean onTouch(View v, android.view.MotionEvent e) { return true; }
        });
        ad = new BaseAdapter() {
            public int getCount() { return rows.size(); }
            public Object getItem(int p) { return rows.get(p); }
            public long getItemId(int p) { return p; }
            public View getView(int p, View v, ViewGroup g) {
                TextView t = (TextView) v;
                if (t == null) {
                    t = new TextView(MainActivity.this);
                    t.setTextSize(24);
                    t.setSingleLine(true);
                    t.setPadding(16, 8, 16, 8);
                    t.setMinHeight(56);
                }
                boolean on = p == sel;
                t.setText(rows.get(p)[0]);
                t.setTextColor(on ? Color.BLACK : Color.WHITE);
                t.setBackgroundColor(on ? Color.WHITE : Color.BLACK);
                return t;
            }
        };
        lv.setAdapter(ad);
        setContentView(lv);
        if (mode.equals("home")) {
            for (String s : new String[] {"Artists", "Albums", "Shuffle All", "Now Playing", "Settings"}) rows.add(new String[] {s});
            // warm the library (cache load, or first-time scan) without blocking the menu
            new Thread(new Runnable() { public void run() { lib(MainActivity.this); } }).start();
        } else fill();
        ad.notifyDataSetChanged();
    }

    void fill() {
        Library l = lib(this);
        if (mode.equals("artist") || mode.equals("songs")) rows.add(new String[] {"Shuffle"}); // top row
        if (mode.equals("artists")) for (String a : l.artists()) rows.add(new String[] {a});
        else if (mode.equals("albums")) for (String[] a : l.allAlbums()) rows.add(new String[] {a[1] + " - " + a[0], a[0], a[1]});
        else if (mode.equals("artist")) for (String a : l.albums(artist)) rows.add(new String[] {a, artist, a});
        else for (Library.Song s : songList = l.songs(artist, album))
            rows.add(new String[] {(s.track > 0 ? String.format("%02d  ", s.track) : "") + s.title, s.path});
    }

    void go(String m, String a, String b) {
        startActivity(new Intent(this, MainActivity.class).putExtra("m", m).putExtra("a", a).putExtra("b", b));
    }

    void select() {
        if (rows.isEmpty()) return;
        String[] r = rows.get(sel);
        if (mode.equals("home")) {
            if (sel == 0) go("artists", null, null);
            else if (sel == 1) go("albums", null, null);
            else if (sel == 2) { PlayerService.play(this, lib(this).songs(null, null), 0, true); startActivity(new Intent(this, NowPlayingActivity.class)); }
            else if (sel == 3) startActivity(new Intent(this, NowPlayingActivity.class));
            else Toast.makeText(this, r[0] + ": later", Toast.LENGTH_SHORT).show();
        } else if (mode.equals("artists")) go("artist", r[0], null);
        else if (sel == 0 && (mode.equals("artist") || mode.equals("songs"))) { // Shuffle row of an artist / album list
            PlayerService.play(this, lib(this).songs(artist, album), 0, true);
            startActivity(new Intent(this, NowPlayingActivity.class));
        }
        else if (mode.equals("albums") || mode.equals("artist")) go("songs", r[1], r[2]);
        else { // queue = the whole list, starting at the selection (row 0 is the Shuffle row)
            PlayerService.play(this, songList, sel - 1, false);
            startActivity(new Intent(this, NowPlayingActivity.class));
        }
    }

    void move(int d) {
        int n = sel + d;
        if (n < 0 || n >= rows.size()) return;
        sel = n;
        ad.notifyDataSetChanged();
        lv.setSelectionFromTop(sel, Math.max(0, lv.getHeight() / 2 - 28)); // keep selection mid-screen
    }

    // Wheel: clockwise = DPAD_RIGHT (down), counter-clockwise = DPAD_LEFT (up); center = ENTER.
    @Override public boolean onKeyDown(int k, KeyEvent e) {
        if (k == KeyEvent.KEYCODE_DPAD_RIGHT || k == KeyEvent.KEYCODE_DPAD_DOWN) { move(1); return true; }
        if (k == KeyEvent.KEYCODE_DPAD_LEFT || k == KeyEvent.KEYCODE_DPAD_UP) { move(-1); return true; }
        if (k == KeyEvent.KEYCODE_ENTER || k == KeyEvent.KEYCODE_DPAD_CENTER) { select(); return true; }
        String a = MediaButtonReceiver.actionFor(k);
        if (a != null) { PlayerService.send(this, a); return true; }
        return super.onKeyDown(k, e);
    }
}
