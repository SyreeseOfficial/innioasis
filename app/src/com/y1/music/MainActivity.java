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
public class MainActivity extends Base {
    static volatile Library LIB; // null until the first load/scan finishes
    static boolean restoredOnce;

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
    boolean inert; // rows are a placeholder ("Loading..." / "Empty"): Enter does nothing
    ListView lv;
    TextView dog; // home only
    BaseAdapter ad;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Intent i = getIntent();
        mode = i.getStringExtra("m");
        if (mode == null) mode = "home";
        if (mode.equals("home")) {
            final android.content.SharedPreferences sp = getSharedPreferences("p", 0);
            if (sp.getInt("crashes", 0) >= 2) { // 2 crashes in a row: hand over to the stock player, retry next time
                sp.edit().putInt("crashes", 0).commit();
                PlayerService.log("crash guard: falling back to stock player");
                startActivity(new Intent().setClassName("com.innioasis.y1", "com.innioasis.y1.activity.SplashActivity"));
                finish();
                return;
            }
            idleH.postDelayed(new Runnable() { public void run() { sp.edit().putInt("crashes", 0).commit(); } }, 10000);
        }
        artist = i.getStringExtra("a");
        album = i.getStringExtra("b");
        lv = new ListView(this);
        lv.setBackgroundColor(bg());
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
                t.setTextColor(on ? bg() : fg());
                t.setBackgroundColor(on ? accent() : bg());
                return t;
            }
        };
        lv.setAdapter(ad);
        if (mode.equals("home")) {
            dog = new TextView(this);
            dog.setText("U\u30FB\u03C9\u30FBU"); // ponytail: font may lack these glyphs; U+1D25 (nose) is missing from the device font, so omega
            dog.setTextSize(32);
            dog.setPadding(0, 0, 0, 12);
            android.widget.FrameLayout f = new android.widget.FrameLayout(this);
            f.addView(lv);
            f.addView(dog, new android.widget.FrameLayout.LayoutParams(-2, -2, android.view.Gravity.BOTTOM | android.view.Gravity.CENTER_HORIZONTAL));
            setContentView(f);
        } else setContentView(lv);
        if (mode.equals("home")) {
            for (String s : new String[] {"Artists", "Albums", "Shuffle All", "Now Playing", "Settings"}) rows.add(new String[] {s});
            if (PlayerService.restore(this) && !restoredOnce) { // fresh process: show the last song, paused
                restoredOnce = true;
                startActivity(new Intent(this, NowPlayingActivity.class));
            }
            // warm the library (cache load, or first-time scan) without blocking the menu
            new Thread(new Runnable() { public void run() { lib(MainActivity.this); } }).start();
        } else if (mode.equals("settings")) settingsRows();
        else load();
        ad.notifyDataSetChanged();
    }

    /** Rows are built on a worker thread: the library may still be loading (or scanning, minutes on first run). */
    void load() {
        inert = true;
        rows.add(new String[] {"Loading..."});
        new Thread(new Runnable() { public void run() {
            final List<String[]> r = new ArrayList<String[]>();
            final List<Library.Song> sl = build(lib(MainActivity.this), r);
            runOnUiThread(new Runnable() { public void run() {
                if (isFinishing()) return;
                inert = r.isEmpty();
                if (inert) r.add(new String[] {"Empty"});
                rows = r;
                songList = sl;
                sel = 0;
                ad.notifyDataSetChanged();
            } });
        } }).start();
    }

    /** Fills r for this screen. Returns the song list for a songs screen, else null. */
    List<Library.Song> build(Library l, List<String[]> r) {
        if (mode.equals("artist") || mode.equals("songs")) r.add(new String[] {"Shuffle"}); // top row
        if (mode.equals("artists")) for (String a : l.artists()) r.add(new String[] {a});
        else if (mode.equals("albums")) for (String[] a : l.allAlbums()) r.add(new String[] {a[1] + " - " + a[0], a[0], a[1]});
        else if (mode.equals("artist")) for (String a : l.albums(artist)) r.add(new String[] {a, artist, a});
        else {
            List<Library.Song> sl = l.songs(artist, album);
            for (Library.Song s : sl) r.add(new String[] {(s.track > 0 ? String.format("%02d  ", s.track) : "") + s.title, s.path});
            return sl;
        }
        return null;
    }

    // ---- settings ----
    static final int[] BRIGHT = {30, 60, 100, 150, 200, 255};
    volatile String scanMsg = "";

    int brightness() {
        try { return android.provider.Settings.System.getInt(getContentResolver(), android.provider.Settings.System.SCREEN_BRIGHTNESS); }
        catch (Exception e) { return 128; }
    }

    void settingsRows() {
        rows.clear();
        rows.add(new String[] {"Bluetooth"});
        rows.add(new String[] {"Rescan library" + scanMsg});
        rows.add(new String[] {"Brightness: " + brightness() * 100 / 255 + "%"});
        rows.add(new String[] {"Launch Android"});
        rows.add(new String[] {"Theme: " + NAMES[themeIdx()]});
        rows.add(new String[] {"Sleep"});
        rows.add(new String[] {"Power off"});
        rows.add(new String[] {"USB storage: " + (usb(null) ? "On" : "Off")});
        rows.add(new String[] {"Made by Syreese"}); // sel == 8 has no action
    }

    /** USB mass storage on/off via StorageManager (we run as uid system). The stock dialog needs left/right keys the wheel lacks. */
    boolean usb(Boolean set) {
        try {
            Object sm = getSystemService("storage");
            Class<?> c = sm.getClass();
            if (set != null) c.getMethod(set ? "enableUsbMassStorage" : "disableUsbMassStorage").invoke(sm);
            return (Boolean) c.getMethod("isUsbMassStorageEnabled").invoke(sm);
        } catch (Exception e) { PlayerService.log("usb failed: " + e); return false; }
    }

    void settingsAction() {
        if (sel == 0) startActivity(new Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS));
        else if (sel == 1) {
            scanMsg = " (scanning...)";
            new Thread(new Runnable() { public void run() {
                String m;
                try { m = " (" + lib(MainActivity.this).rescan() + " songs)"; Covers.clear(MainActivity.this); } catch (Exception e) { m = " (failed)"; }
                scanMsg = m;
                runOnUiThread(new Runnable() { public void run() { settingsRows(); ad.notifyDataSetChanged(); } });
            } }).start();
        } else if (sel == 2) {
            int b = brightness(), n = BRIGHT[0];
            for (int v : BRIGHT) if (v > b) { n = v; break; } // next step up, wrapping to the lowest
            android.provider.Settings.System.putInt(getContentResolver(), android.provider.Settings.System.SCREEN_BRIGHTNESS_MODE, 0);
            android.provider.Settings.System.putInt(getContentResolver(), android.provider.Settings.System.SCREEN_BRIGHTNESS, n);
            setBrightness(-1f); // re-apply the system value now
        } else if (sel == 3) { // the stock "home" is the Y1 player, so the plain Android UI is Settings
            startActivity(new Intent().setClassName("com.android.settings", "com.android.settings.Settings").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } else if (sel == 4) {
            getSharedPreferences("p", 0).edit().putInt("theme", (themeIdx() + 1) % NAMES.length).commit();
            loadTheme(); onTheme();
        } else if (sel == 5) { // pause, then screen off; the power button wakes it
            PlayerService.send(this, "pause");
            try { android.os.PowerManager.class.getMethod("goToSleep", long.class).invoke(getSystemService(POWER_SERVICE), android.os.SystemClock.uptimeMillis()); }
            catch (Exception e) { PlayerService.log("sleep failed: " + e); }
        } else if (sel == 6) { // system shows its own "Power off?" confirm
            PlayerService.send(this, "pause");
            startActivity(new Intent("android.intent.action.ACTION_REQUEST_SHUTDOWN").putExtra("android.intent.extra.KEY_CONFIRM", true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } else if (sel == 7) usb(!usb(null));
        settingsRows();
        ad.notifyDataSetChanged();
    }

    @Override void onTheme() {
        if (lv == null) return;
        lv.setBackgroundColor(bg());
        if (dog != null) dog.setTextColor(accent());
        ad.notifyDataSetChanged();
    }

    void go(String m, String a, String b) {
        startActivity(new Intent(this, MainActivity.class).putExtra("m", m).putExtra("a", a).putExtra("b", b));
    }

    /** The library may still be loading, so fetch it off the UI thread. */
    void shuffleAll() {
        if (LIB == null) Toast.makeText(this, "Loading library...", Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() { public void run() {
            final List<Library.Song> all = lib(MainActivity.this).songs(null, null);
            runOnUiThread(new Runnable() { public void run() {
                PlayerService.play(MainActivity.this, all, 0, true);
                startActivity(new Intent(MainActivity.this, NowPlayingActivity.class));
            } });
        } }).start();
    }

    void select() {
        if (inert || rows.isEmpty()) return;
        String[] r = rows.get(sel);
        if (mode.equals("home")) {
            if (sel == 0) go("artists", null, null);
            else if (sel == 1) go("albums", null, null);
            else if (sel == 2) shuffleAll();
            else if (sel == 3) startActivity(new Intent(this, NowPlayingActivity.class));
            else go("settings", null, null);
        } else if (mode.equals("settings")) settingsAction();
        else if (mode.equals("artists")) go("artist", r[0], null);
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
