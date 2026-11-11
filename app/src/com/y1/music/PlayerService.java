package com.y1.music;

import android.app.Notification;
import android.app.Service;
import android.content.*;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.IBinder;
import android.widget.Toast;
import android.os.Handler;
import java.io.*;
import java.util.*;

/** Owns the MediaPlayer and the queue. UI and hardware keys talk to it with action intents. */
public class PlayerService extends Service implements MediaPlayer.OnCompletionListener,
        MediaPlayer.OnErrorListener, MediaPlayer.OnPreparedListener, AudioManager.OnAudioFocusChangeListener {
    static List<Library.Song> queue = new ArrayList<Library.Song>();
    static int idx;
    static boolean playing, shuffle;
    static int pendingPos; // restored position, applied by the next load()
    static final String QFILE = "queue.dat";

    MediaPlayer mp;
    AudioManager am;
    ComponentName btn;
    int fails;
    boolean lostFocus, ready; // ready = prepared, so position/pause/start are legal

    static void send(Context c, String action) {
        c.startService(new Intent(c, PlayerService.class).setAction(action));
    }

    /** Plays q in order from i, or a shuffled copy (each song once) when shuffle is true. */
    static void play(Context c, List<Library.Song> q, int i, boolean shuf) {
        shuffle = shuf;
        if (shuf) { q = new ArrayList<Library.Song>(q); Collections.shuffle(q); i = 0; }
        queue = q;
        idx = i;
        pendingPos = 0;
        saveQueue(c);
        send(c, "play");
    }

    // --- resume: queue in files/queue.dat (same line format as library.dat), idx/pos/shuffle in prefs "p" ---
    static void saveQueue(Context c) {
        try {
            Writer w = new BufferedWriter(new OutputStreamWriter(c.openFileOutput(QFILE, 0), "UTF-8"));
            for (Library.Song s : queue) w.write(s.path + "\t" + s.title + "\t" + s.artist + "\t" + s.album + "\t" + s.track + "\n");
            w.close();
        } catch (Exception e) { log("saveQueue " + e); }
    }

    /** Reload the last queue, paused at the saved position. Returns false if there is nothing to restore. */
    static boolean restore(Context c) {
        if (!queue.isEmpty()) return true;
        android.content.SharedPreferences p = c.getSharedPreferences("p", 0);
        try {
            BufferedReader r = new BufferedReader(new InputStreamReader(c.openFileInput(QFILE), "UTF-8"));
            List<Library.Song> l = new ArrayList<Library.Song>();
            String line;
            while ((line = r.readLine()) != null) {
                String[] f = line.split("\t", -1);
                if (f.length < 5) continue;
                Library.Song s = new Library.Song();
                s.path = f[0]; s.title = f[1]; s.artist = f[2]; s.album = f[3]; s.track = Integer.parseInt(f[4]);
                l.add(s);
            }
            r.close();
            if (l.isEmpty()) return false;
            queue = l;
            idx = Math.min(p.getInt("idx", 0), l.size() - 1);
            pendingPos = p.getInt("pos", 0);
            shuffle = p.getBoolean("shuf", false);
            playing = false;
            return true;
        } catch (Exception e) { return false; }
    }

    void saveState() {
        int pos = mp != null && ready ? mp.getCurrentPosition() : pendingPos;
        getSharedPreferences("p", 0).edit().putInt("idx", idx).putInt("pos", pos).putBoolean("shuf", shuffle).commit();
    }

    final Handler saver = new Handler();
    final Runnable saveTick = new Runnable() {
        public void run() { if (playing) saveState(); saver.postDelayed(this, 5000); } // survives a kill within 5 s
    };

    static Library.Song cur() { return idx >= 0 && idx < queue.size() ? queue.get(idx) : null; }

    static void log(String s) {
        try { FileWriter w = new FileWriter("/storage/sdcard0/y1-music.log", true); w.write(new Date() + " " + s + "\n"); w.close(); }
        catch (Exception e) { /* card missing: nothing to log to */ }
    }

    final BroadcastReceiver noisy = new BroadcastReceiver() {
        public void onReceive(Context c, Intent i) { pause(); }
    };

    @Override public void onCreate() {
        am = (AudioManager) getSystemService(AUDIO_SERVICE);
        btn = new ComponentName(this, MediaButtonReceiver.class);
        am.registerMediaButtonEventReceiver(btn);
        registerReceiver(noisy, new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY));
        saveTick.run();
    }

    @Override public void onDestroy() {
        saveState();
        saver.removeCallbacks(saveTick);
        unregisterReceiver(noisy);
        am.unregisterMediaButtonEventReceiver(btn);
        am.abandonAudioFocus(this);
        if (mp != null) mp.release();
        playing = false;
    }

    @Override public IBinder onBind(Intent i) { return null; }

    @Override public int onStartCommand(Intent i, int flags, int id) {
        String a = i == null ? null : i.getAction();
        if ("play".equals(a)) { fails = 0; load(); }
        else if ("toggle".equals(a)) { if (playing) pause(); else resume(); }
        else if ("next".equals(a)) next();
        else if ("prev".equals(a)) prev();
        return START_STICKY;
    }

    void load() {
        Library.Song s = cur();
        if (s == null) return;
        if (mp != null) mp.release();
        mp = null;
        ready = false;
        try {
            am.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
            mp = new MediaPlayer();
            mp.setWakeMode(this, android.os.PowerManager.PARTIAL_WAKE_LOCK); // music survives screen off
            mp.setOnCompletionListener(this);
            mp.setOnErrorListener(this);
            mp.setOnPreparedListener(this);
            mp.setDataSource(s.path);
            mp.prepareAsync(); // main thread stays free for the wheel
            playing = true;
            Notification n = new Notification.Builder(this).setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle(s.title).setContentText(s.artist).build();
            startForeground(1, n);
            saveState();
        } catch (Exception e) { fail(e); }
    }

    public void onPrepared(MediaPlayer m) {
        if (m != mp) return;
        ready = true;
        fails = 0;
        if (pendingPos > 0) { m.seekTo(pendingPos); pendingPos = 0; }
        if (playing) m.start(); // user may have paused while loading
    }

    void fail(Exception e) {
        Library.Song s = cur();
        playing = false;
        log("can't play " + (s == null ? "?" : s.path) + ": " + e);
        Toast.makeText(this, "can't play " + (s == null ? "" : s.title), Toast.LENGTH_SHORT).show();
        if (++fails < queue.size() && idx + 1 < queue.size()) { idx++; load(); }
    }

    void pause() {
        if (mp != null && playing) { if (ready) mp.pause(); playing = false; saveState(); }
    }

    void resume() {
        if (mp == null) { load(); return; }
        am.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
        playing = true;
        if (ready) mp.start();
    }

    void next() {
        if (idx + 1 < queue.size()) { idx++; pendingPos = 0; load(); }
    }

    void prev() {
        if (mp != null && ready && mp.getCurrentPosition() > 3000) { mp.seekTo(0); return; }
        if (idx > 0) idx--;
        pendingPos = 0;
        load();
    }

    public void onCompletion(MediaPlayer m) {
        if (idx + 1 < queue.size()) next(); else playing = false;
    }

    public boolean onError(MediaPlayer m, int what, int extra) {
        fail(new Exception("MediaPlayer error " + what + "/" + extra));
        return true;
    }

    public void onAudioFocusChange(int change) {
        if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
            lostFocus = playing;
            pause();
        } else if (change == AudioManager.AUDIOFOCUS_GAIN && lostFocus) {
            lostFocus = false;
            resume();
        }
    }
}
