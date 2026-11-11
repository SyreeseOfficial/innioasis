package com.y1.music;

import android.media.MediaMetadataRetriever;
import java.io.*;
import java.util.*;

/** All songs in one sorted list; artist/album views are filtered on demand (3k items, instant). */
public class Library {
    public static class Song {
        public String path, title, artist, album;
        public int track;
    }

    public static final String ROOT = "/storage/sdcard0/Music";
    public List<Song> songs = new ArrayList<Song>();
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
                String[] p = s.split("\t", -1);
                if (p.length < 5) continue;
                Song g = new Song();
                g.path = p[0]; g.title = p[1]; g.artist = p[2]; g.album = p[3]; g.track = Integer.parseInt(p[4]);
                l.add(g);
            }
            r.close();
            songs = l; // already sorted when saved
            return true;
        } catch (Exception e) { return false; }
    }

    /** Walks ROOT, reads tags, writes the cache. Slow (minutes); run off the UI thread. */
    public int rescan() throws IOException {
        List<File> files = new ArrayList<File>();
        walk(new File(ROOT), files);
        List<Song> l = new ArrayList<Song>();
        MediaMetadataRetriever m = new MediaMetadataRetriever();
        for (File f : files) {
            Song s = new Song();
            s.path = f.getPath();
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
        songs = l;
        Writer w = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(cache), "UTF-8"));
        for (Song s : l) w.write(s.path + "\t" + s.title + "\t" + s.artist + "\t" + s.album + "\t" + s.track + "\n");
        w.close();
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

    public List<String> artists() {
        List<String> l = new ArrayList<String>();
        for (Song s : songs) if (l.isEmpty() || !l.get(l.size() - 1).equals(s.artist)) l.add(s.artist);
        return l; // songs are sorted by artist, so duplicates are adjacent (case-insensitive ties could split; ok)
    }

    /** Albums of one artist, or all albums as "Artist\tAlbum"-free names when artist is null. */
    public List<String> albums(String artist) {
        List<String> l = new ArrayList<String>();
        for (Song s : songs) {
            if (artist != null && !s.artist.equals(artist)) continue;
            if (l.isEmpty() || !l.get(l.size() - 1).equals(s.album)) l.add(s.album);
        }
        return l;
    }

    /** Every (artist, album) pair as {artist, album}, sorted A-Z by album name. */
    public List<String[]> allAlbums() {
        List<String[]> l = new ArrayList<String[]>();
        for (Song s : songs) {
            String[] last = l.isEmpty() ? null : l.get(l.size() - 1);
            if (last == null || !last[0].equals(s.artist) || !last[1].equals(s.album)) l.add(new String[] {s.artist, s.album});
        }
        Collections.sort(l, new Comparator<String[]>() {
            public int compare(String[] a, String[] b) { return a[1].compareToIgnoreCase(b[1]); }
        });
        return l;
    }

    public List<Song> songs(String artist, String album) {
        List<Song> l = new ArrayList<Song>();
        for (Song s : songs) if (s.album.equals(album) && (artist == null || s.artist.equals(artist))) l.add(s);
        return l;
    }
}
