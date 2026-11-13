package com.y1.music;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.util.LruCache;
import java.io.*;

/** Album art. One cover per artist+album: a few in memory, and 240 px JPEGs in cacheDir/covers so a song change
 *  doesn't re-open the audio file. A 0-byte file means "this album has no art". Rescan clears it all.
 *  get() blocks on disk: call it from one background thread (NowPlayingActivity does), never the UI thread. */
public class Covers {
    static final int SIZE = 240; // matches the 240x240 ImageView
    static final LruCache<String, Bitmap> mem = new LruCache<String, Bitmap>(3);

    static File dir(Context c) { return new File(c.getCacheDir(), "covers"); }

    /** The cover, or null if the album has none (or it couldn't be read right now). */
    static Bitmap get(Context c, Library.Song s) {
        String key = s.artist + "\t" + s.album;
        Bitmap bm = mem.get(key);
        if (bm != null) return bm;
        File f = new File(dir(c), Integer.toHexString(key.hashCode()) + ".jpg");
        if (f.exists()) {
            if (f.length() == 0) return null;
            bm = BitmapFactory.decodeFile(f.getPath());
            if (bm != null) { mem.put(key, bm); return bm; }
            // unreadable cache file: rebuild below
        }
        try {
            byte[] d = artBytes(s);
            if (d != null) bm = decode(d);
        } catch (Throwable e) { // card missing, OOM...: show nothing now, and don't remember it as "no art"
            android.util.Log.w("Y1COVER", "no cover " + s.path, e);
            return null;
        }
        save(f, bm);
        if (bm != null) mem.put(key, bm);
        return bm;
    }

    /** Embedded picture, else cover.jpg / folder.jpg next to the song, else null. */
    static byte[] artBytes(Library.Song s) throws IOException {
        MediaMetadataRetriever m = new MediaMetadataRetriever();
        byte[] d;
        try {
            m.setDataSource(s.path);
            d = m.getEmbeddedPicture();
        } finally { m.release(); }
        if (d != null) return d;
        for (String n : new String[] {"cover.jpg", "folder.jpg"}) {
            File f = new File(new File(s.path).getParentFile(), n);
            if (!f.exists()) continue;
            d = new byte[(int) f.length()];
            DataInputStream in = new DataInputStream(new FileInputStream(f));
            try { in.readFully(d); } finally { in.close(); }
            return d;
        }
        return null;
    }

    /** Decode small (sampled down), then scale so the short side is SIZE. */
    static Bitmap decode(byte[] d) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(d, 0, d.length, o);
        int n = 1;
        while (o.outWidth / (n * 2) >= SIZE) n *= 2;
        o = new BitmapFactory.Options();
        o.inSampleSize = n;
        Bitmap bm = BitmapFactory.decodeByteArray(d, 0, d.length, o);
        if (bm == null) return null;
        int w = bm.getWidth(), h = bm.getHeight(), s = Math.min(w, h);
        if (s > SIZE) {
            float k = (float) SIZE / s;
            Bitmap t = Bitmap.createScaledBitmap(bm, Math.max(SIZE, Math.round(w * k)), Math.max(SIZE, Math.round(h * k)), true);
            if (t != bm) bm.recycle();
            bm = t;
        }
        return bm;
    }

    /** bm == null writes the empty "no art" marker. Failures are ignored: it is only a cache. */
    static void save(File f, Bitmap bm) {
        File tmp = new File(f.getPath() + ".tmp");
        try {
            f.getParentFile().mkdirs();
            if (bm == null) { f.createNewFile(); return; }
            FileOutputStream o = new FileOutputStream(tmp);
            boolean ok;
            try { ok = bm.compress(Bitmap.CompressFormat.JPEG, 85, o); } finally { o.close(); }
            if (!ok || !tmp.renameTo(f)) tmp.delete();
        } catch (Exception e) { tmp.delete(); }
    }

    /** After a rescan: tags and folder art may have changed. */
    static void clear(Context c) {
        mem.evictAll();
        File[] a = dir(c).listFiles();
        if (a != null) for (File f : a) f.delete();
    }
}
