package com.y1.music;

import android.media.MediaMetadataRetriever;
import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** All songs in one sorted list. Artist/album lists are built once per load/rescan (see Snap), not per screen. */
public class Library {
    public static class Song {
        public String path, title, artist, album;
        public int track;
        public long mtime; // file date; lets rescan skip unchanged files

        String line() { return path + "\t" + title + "\t" + artist + "\t" + album + "\t" + track + "\t" + mtime + "\n"; }
    }

    /** One line of library.dat / queue.dat. Null if too short; a bad number throws (cache is corrupt). */
    static Song parse(String line) {
        String[] p = line.split("\t", -1);
        if (p.length < 5) return null;
        Song g = new Song();
        g.path = p[0]; g.title = p[1]; g.artist = p[2].intern(); g.album = p[3].intern(); g.track = Integer.parseInt(p[4]);
        if (p.length > 5) try { g.mtime = Long.parseLong(p[5]); } catch (Exception e) {} // old queue.dat has no date
        return g;
    }

    /** Write to dst.tmp, fsync, then rename: a kill or power loss mid-write leaves the old file intact. */
    static void writeAtomic(File dst, List<Song> l) throws IOException {
        File tmp = new File(dst.getPath() + ".tmp");
        FileOutputStream fo = new FileOutputStream(tmp);
        try {
            Writer w = new BufferedWriter(new OutputStreamWriter(fo, "UTF-8"));
            for (Song s : l) w.write(s.line());
            w.flush();
            fo.getFD().sync();
        } finally { fo.close(); }
        if (!tmp.renameTo(dst)) throw new IOException("rename failed: " + dst);
    }

    /** Immutable view of one library state. Swapped in whole, so UI threads never see a half-built rescan. */
    static class Snap {
        final List<Song> songs; // sorted by ORDER
        final List<String> artists = new ArrayList<String>();
        final Map<String, List<String>> albumsOf = new HashMap<String, List<String>>();
        final List<String[]> allAlbums = new ArrayList<String[]>(); // {artist, album}, A-Z by album
        final Map<String, List<Song>> subsets = new ConcurrentHashMap<String, List<Song>>(); // filled on first request

        Snap(List<Song> l) {
            songs = l;
            Map<String, Set<String>> by = new LinkedHashMap<String, Set<String>>(); // exact-name keys, sorted order kept
            for (Song s : l) {
                Set<String> a = by.get(s.artist);
                if (a == null) by.put(s.artist, a = new LinkedHashSet<String>());
                a.add(s.album);
            }
            artists.addAll(by.keySet());
            for (Map.Entry<String, Set<String>> e : by.entrySet()) {
                List<String> al = new ArrayList<String>(e.getValue());
                albumsOf.put(e.getKey(), al);
                for (String b : al) allAlbums.add(new String[] {e.getKey(), b});
            }
            Collections.sort(allAlbums, new Comparator<String[]>() {
                public int compare(String[] a, String[] b) { return a[1].compareToIgnoreCase(b[1]); }
            });
        }
    }

    public static final String ROOT = "/storage/sdcard0/Music";
    volatile Snap snap = new Snap(new ArrayList<Song>());
    final File cache;

    public Library(File filesDir) { cache = new File(filesDir, "library.dat"); }

    static final Comparator<Song> ORDER = new Comparator<Song>() {
        public int compare(Song a, Song b) {
            int c = a.artist.compareToIgnoreCase(b.artist);
            if (c == 0) c = a.album.compareToIgnoreCase(b.album);
            if (c == 0) c = a.track - b.track;
            if (c == 0) c = a.path.compareTo(b.path);
            return c;
        }
    };

    public boolean load() {
        if (!cache.exists()) return false;
        try {
            BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(cache), "UTF-8"));
            List<Song> l = new ArrayList<Song>();
            String s;
            while ((s = r.readLine()) != null) {
                Song g = parse(s);
                if (g != null) l.add(g);
            }
            r.close();
            snap = new Snap(l); // already sorted when saved
            return true;
        } catch (Exception e) { return false; }
    }

    /** Walks ROOT, reads tags, writes the cache. Slow (minutes); run off the UI thread. Synchronized: two at once would share one .tmp file. */
    public synchronized int rescan() throws IOException {
        List<File> files = new ArrayList<File>();
        walk(new File(ROOT), files);
        Map<String, Song> old = new HashMap<String, Song>();
        for (Song o : snap.songs) old.put(o.path, o);
        List<Song> l = new ArrayList<Song>();
        MediaMetadataRetriever m = new MediaMetadataRetriever();
        for (File f : files) {
            Song o = old.get(f.getPath());
            if (o != null && o.mtime != 0 && o.mtime == f.lastModified()) { l.add(o); continue; } // unchanged: keep cached tags
            Song s = new Song();
            s.path = f.getPath();
            s.mtime = f.lastModified();
            String title = null, artist = null, album = null, tr = null;
            try {
                m.setDataSource(s.path);
                title = m.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
                artist = m.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST); // album artist keeps "feat." tracks together
                if (artist == null || artist.trim().length() == 0) artist = m.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
                album = m.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM);
                tr = m.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER);
            } catch (Exception e) { /* untagged or unreadable: fall through to defaults */ }
            s.title = clean(title, f.getName());
            s.artist = clean(artist, "Unknown Artist");
            s.album = clean(album, "Unknown Album");
            s.track = 0;
            if (tr != null) try { s.track = Integer.parseInt(tr.split("/")[0].trim()); } catch (Exception e) {}
            l.add(s);
        }
        m.release();
        Collections.sort(l, ORDER);
        snap = new Snap(l);
        writeAtomic(cache, l);
        return l.size();
    }

    static String clean(String s, String dflt) {
        if (s == null) return dflt;
        s = s.replace('\t', ' ').replace('\n', ' ').trim();
        return s.length() == 0 ? dflt : s;
    }

    static void walk(File d, List<File> out) {
        File[] a = d.listFiles();
        if (a == null) return;
        for (File f : a) {
            if (f.isDirectory()) walk(f, out);
            else {
                String n = f.getName().toLowerCase();
                if (n.endsWith(".flac") || n.endsWith(".mp3") || n.endsWith(".m4a")) out.add(f);
            }
        }
    }

    // The getters return shared lists: callers must not modify them.

    public List<String> artists() { return snap.artists; }

    /** Albums of one artist, in order. */
    public List<String> albums(String artist) {
        List<String> l = snap.albumsOf.get(artist);
        return l == null ? Collections.<String>emptyList() : l;
    }

    /** Every (artist, album) pair as {artist, album}, sorted A-Z by album name. */
    public List<String[]> allAlbums() { return snap.allAlbums; }

    /** Songs of an artist and/or album (null = any). Filtered once, then served from the snapshot's cache. */
    public List<Song> songs(String artist, String album) {
        Snap s = snap;
        if (artist == null && album == null) return s.songs;
        String k = (artist == null ? "" : artist) + "\t" + (album == null ? "" : album); // names never hold tabs or are empty (clean())
        List<Song> l = s.subsets.get(k);
        if (l == null) {
            l = new ArrayList<Song>();
            for (Song g : s.songs) if ((album == null || g.album.equals(album)) && (artist == null || g.artist.equals(artist))) l.add(g);
            s.subsets.put(k, l);
        }
        return l;
    }
}
