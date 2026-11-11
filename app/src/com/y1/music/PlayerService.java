package com.y1.music;

import android.app.Notification;
import android.app.Service;
import android.content.*;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.IBinder;
import android.widget.Toast;
import java.io.FileWriter;
import java.util.*;

/** Owns the MediaPlayer and the queue. UI and hardware keys talk to it with action intents. */
public class PlayerService extends Service implements MediaPlayer.OnCompletionListener,
        MediaPlayer.OnErrorListener, AudioManager.OnAudioFocusChangeListener {
    static List<Library.Song> queue = new ArrayList<Library.Song>();
    static int idx;
    static boolean playing;

    MediaPlayer mp;
    AudioManager am;
    ComponentName btn;
    int fails;
    boolean lostFocus;

    static void send(Context c, String action) {
        c.startService(new Intent(c, PlayerService.class).setAction(action));
    }

    static void play(Context c, List<Library.Song> q, int i) {
        queue = q;
        idx = i;
        send(c, "play");
    }

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
    }

    @Override public void onDestroy() {
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
        try {
            am.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
            mp = new MediaPlayer();
            mp.setWakeMode(this, android.os.PowerManager.PARTIAL_WAKE_LOCK); // music survives screen off
            mp.setOnCompletionListener(this);
            mp.setOnErrorListener(this);
            mp.setDataSource(s.path);
            mp.prepare();
            mp.start();
            playing = true;
            fails = 0;
            Notification n = new Notification.Builder(this).setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle(s.title).setContentText(s.artist).build();
            startForeground(1, n);
        } catch (Exception e) { fail(e); }
    }

    void fail(Exception e) {
        Library.Song s = cur();
        playing = false;
        log("can't play " + (s == null ? "?" : s.path) + ": " + e);
        Toast.makeText(this, "can't play " + (s == null ? "" : s.title), Toast.LENGTH_SHORT).show();
        if (++fails < queue.size() && idx + 1 < queue.size()) { idx++; load(); }
    }

    void pause() {
        if (mp != null && playing) { mp.pause(); playing = false; }
    }

    void resume() {
        if (mp == null) { load(); return; }
        am.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
        mp.start();
        playing = true;
    }

    void next() {
        if (idx + 1 < queue.size()) { idx++; load(); }
    }

    void prev() {
        if (mp != null && mp.getCurrentPosition() > 3000) { mp.seekTo(0); return; }
        if (idx > 0) idx--;
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
